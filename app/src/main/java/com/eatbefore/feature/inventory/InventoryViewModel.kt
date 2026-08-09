package com.eatbefore.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.datastore.UserPreferencesRepository
import com.eatbefore.domain.model.ExpiryStatus
import com.eatbefore.domain.model.InventoryItem
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.repository.InventoryRepository
import com.eatbefore.domain.repository.StorageLocationRepository
import com.eatbefore.domain.usecase.DetermineExpiryStatusUseCase
import com.eatbefore.feature.common.InventoryRowUi
import com.eatbefore.feature.common.QuickAction
import com.eatbefore.feature.common.QuickActionSignal
import com.eatbefore.feature.common.QuickActions
import com.eatbefore.feature.common.toRowUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class InventorySort { EXPIRY, NAME, ADDED }

/** Narrows the list to what the user came here to deal with. */
enum class InventoryStatusFilter { ALL, EXPIRED, SOON, OPENED }

data class InventoryUiState(
    val isLoading: Boolean = true,
    val rows: List<InventoryRowUi> = emptyList(),
    val locations: List<StorageLocation> = emptyList(),
    val selectedLocationId: Long? = null,
    val sort: InventorySort = InventorySort.EXPIRY,
    val statusFilter: InventoryStatusFilter = InventoryStatusFilter.ALL,
)

/** Sort order and status filter travel together so the state combine stays typed. */
private data class ViewOptions(
    val sort: InventorySort = InventorySort.EXPIRY,
    val status: InventoryStatusFilter = InventoryStatusFilter.ALL,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    storageLocationRepository: StorageLocationRepository,
    preferences: UserPreferencesRepository,
    private val determineExpiryStatus: DetermineExpiryStatusUseCase,
    private val quickActions: QuickActions,
    private val filterRequest: InventoryFilterRequest,
    private val clock: AppClock,
) : ViewModel() {

    /** Last finished quick action, driving the undo snackbar. */
    val quickActionSignal: StateFlow<QuickActionSignal?> = quickActions.signal

    /** Today per the app clock, for date presets offered by quick actions. */
    val today: LocalDate get() = clock.today()

    private val selectedLocationId = MutableStateFlow<Long?>(null)
    private val query = MutableStateFlow("")
    private val viewOptions = MutableStateFlow(ViewOptions())

    init {
        // Arriving from the "expired" tile should land on the expired items; asking for a
        // filter is a one-shot, so it is cleared as soon as it has been applied.
        viewModelScope.launch {
            filterRequest.pending.collect { requested ->
                if (requested != null) {
                    viewOptions.update { it.copy(status = requested) }
                    filterRequest.consume()
                }
            }
        }
    }

    /**
     * What the search field shows. It must reflect every keystroke immediately — driving
     * the field from the debounced flow instead would blank out characters as they are
     * typed. Only the filtering below is debounced.
     */
    val queryText: StateFlow<String> = query.asStateFlow()

    private val itemsFlow = selectedLocationId.flatMapLatest { locationId ->
        if (locationId == null) {
            inventoryRepository.observePresentByExpiry()
        } else {
            inventoryRepository.observePresentByLocation(locationId)
        }
    }

    val uiState: StateFlow<InventoryUiState> = combine(
        itemsFlow,
        storageLocationRepository.observeActive(),
        preferences.preferences,
        query.debounce(250),
        viewOptions,
    ) { items, locations, prefs, q, options ->
        val today = clock.today()
        val rows = items
            .filter { it.matches(q) }
            .sortedWith(sortComparator(options.sort))
            .map { it.toRowUi(today, prefs.soonThresholdDays, determineExpiryStatus) }
        InventoryUiState(
            isLoading = false,
            // Filtering after the mapping, because the status is derived there and would
            // otherwise have to be computed twice with two chances to disagree.
            rows = rows.filter { it.matches(options.status) },
            locations = locations,
            selectedLocationId = selectedLocationId.value,
            sort = options.sort,
            statusFilter = options.status,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InventoryUiState(),
    )

    fun setQuery(value: String) {
        query.value = value
    }
    fun setLocation(id: Long?) {
        selectedLocationId.value = id
    }
    fun setSort(value: InventorySort) {
        viewOptions.update { it.copy(sort = value) }
    }

    fun setStatusFilter(value: InventoryStatusFilter) {
        viewOptions.update { it.copy(status = value) }
    }

    fun quickAction(action: QuickAction, batchId: Long, expirationDate: LocalDate? = null) {
        viewModelScope.launch { quickActions.perform(action, batchId, expirationDate) }
    }

    fun undoQuickAction() {
        viewModelScope.launch { quickActions.undo() }
    }

    fun consumeQuickActionSignal() = quickActions.consumeSignal()

    private fun InventoryRowUi.matches(filter: InventoryStatusFilter): Boolean = when (filter) {
        InventoryStatusFilter.ALL -> true
        InventoryStatusFilter.EXPIRED -> expiryStatus == ExpiryStatus.EXPIRED
        // "Soon" is what the reminder would warn about today: the last day counts.
        InventoryStatusFilter.SOON ->
            expiryStatus == ExpiryStatus.EXPIRING_SOON || expiryStatus == ExpiryStatus.EXPIRES_TODAY
        InventoryStatusFilter.OPENED -> isOpened
    }

    private fun InventoryItem.matches(q: String): Boolean {
        if (q.isBlank()) return true
        val needle = q.trim().lowercase()
        return product.name.lowercase().contains(needle) ||
            product.brand?.lowercase()?.contains(needle) == true ||
            // The note is the only field holding the user's own words ("для пирога"),
            // which makes it the most likely thing they search for.
            batch.note?.lowercase()?.contains(needle) == true ||
            product.barcode?.contains(needle) == true
    }

    private fun sortComparator(sort: InventorySort): Comparator<InventoryItem> = when (sort) {
        InventorySort.EXPIRY -> compareBy(nullsLast()) { it.batch.effectiveExpirationDate }
        InventorySort.NAME -> compareBy { it.product.name.lowercase() }
        InventorySort.ADDED -> compareByDescending { it.batch.addedAt }
    }
}
