package com.eatbefore.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.domain.model.InventoryItem
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.core.datastore.UserPreferencesRepository
import com.eatbefore.domain.repository.InventoryRepository
import com.eatbefore.domain.repository.StorageLocationRepository
import com.eatbefore.domain.usecase.DetermineExpiryStatusUseCase
import com.eatbefore.feature.common.InventoryRowUi
import com.eatbefore.feature.common.toRowUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class InventorySort { EXPIRY, NAME, ADDED }

data class InventoryUiState(
    val isLoading: Boolean = true,
    val rows: List<InventoryRowUi> = emptyList(),
    val locations: List<StorageLocation> = emptyList(),
    val selectedLocationId: Long? = null,
    val query: String = "",
    val sort: InventorySort = InventorySort.EXPIRY,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    storageLocationRepository: StorageLocationRepository,
    preferences: UserPreferencesRepository,
    private val determineExpiryStatus: DetermineExpiryStatusUseCase,
    private val clock: AppClock,
) : ViewModel() {

    private val selectedLocationId = MutableStateFlow<Long?>(null)
    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(InventorySort.EXPIRY)

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
        sort,
    ) { items, locations, prefs, q, sortOrder ->
        val today = clock.today()
        val filtered = items.filter { it.matches(q) }
        val sorted = filtered.sortedWith(sortComparator(sortOrder))
        InventoryUiState(
            isLoading = false,
            rows = sorted.map { it.toRowUi(today, prefs.soonThresholdDays, determineExpiryStatus) },
            locations = locations,
            selectedLocationId = selectedLocationId.value,
            query = q,
            sort = sortOrder,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InventoryUiState(),
    )

    fun setQuery(value: String) { query.value = value }
    fun setLocation(id: Long?) { selectedLocationId.value = id }
    fun setSort(value: InventorySort) { sort.value = value }

    private fun InventoryItem.matches(q: String): Boolean {
        if (q.isBlank()) return true
        val needle = q.trim().lowercase()
        return product.name.lowercase().contains(needle) ||
            product.brand?.lowercase()?.contains(needle) == true ||
            product.barcode?.contains(needle) == true
    }

    private fun sortComparator(sort: InventorySort): Comparator<InventoryItem> = when (sort) {
        InventorySort.EXPIRY -> compareBy(nullsLast()) { it.batch.effectiveExpirationDate }
        InventorySort.NAME -> compareBy { it.product.name.lowercase() }
        InventorySort.ADDED -> compareByDescending { it.batch.addedAt }
    }
}
