package com.eatbefore.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.datastore.UserPreferencesRepository
import com.eatbefore.domain.model.ExpiryStatus
import com.eatbefore.domain.repository.InventoryRepository
import com.eatbefore.domain.usecase.DetermineExpiryStatusUseCase
import com.eatbefore.feature.common.InventoryRowUi
import com.eatbefore.feature.common.QuickAction
import com.eatbefore.feature.common.QuickActionSignal
import com.eatbefore.feature.common.QuickActions
import com.eatbefore.feature.common.toRowUi
import com.eatbefore.feature.inventory.InventoryFilterRequest
import com.eatbefore.feature.inventory.InventoryStatusFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val totalCount: Int = 0,
    /**
     * The single thing to eat today. The screen could already say what is going off; this
     * says what to do about it, which is a different question and the one asked at six in
     * the evening with the fridge open.
     */
    val eatFirst: InventoryRowUi? = null,
    val expiringSoon: List<InventoryRowUi> = emptyList(),
    val recent: List<InventoryRowUi> = emptyList(),
    /** Already past its date — counted separately, because it needs a different reaction. */
    val expiredCount: Int = 0,
    /**
     * What is waiting to be dealt with right now: already off, plus what runs out today.
     * The one number the home screen leads with — and when it is zero it says nothing at
     * all, because "nothing to do" does not deserve a quarter of the screen.
     */
    val needsAttentionCount: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    preferences: UserPreferencesRepository,
    private val determineExpiryStatus: DetermineExpiryStatusUseCase,
    private val quickActions: QuickActions,
    private val filterRequest: InventoryFilterRequest,
    private val clock: AppClock,
) : ViewModel() {

    /** Today per the app clock, for the header date (display-only). */
    val today: java.time.LocalDate get() = clock.today()

    /** Last finished quick action, driving the undo snackbar. */
    val quickActionSignal: StateFlow<QuickActionSignal?> = quickActions.signal

    private val expiringFlow = preferences.preferences.flatMapLatest { prefs ->
        val threshold = clock.today().plusDays(prefs.soonThresholdDays.toLong()).toEpochDay()
        inventoryRepository.observeExpiringBefore(threshold)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        expiringFlow,
        inventoryRepository.observeRecent(RECENT_LIMIT),
        inventoryRepository.observePresentCount(),
        preferences.preferences,
    ) { expiring, recent, count, prefs ->
        val today = clock.today()
        val expiringRows = expiring.map {
            it.toRowUi(today, prefs.soonThresholdDays, determineExpiryStatus)
        }
        val eatFirst = expiringRows.pickEatFirst()
        HomeUiState(
            isLoading = false,
            totalCount = count,
            eatFirst = eatFirst,
            // Counted over everything, not just the ten shown, or the tile would understate
            // the problem exactly when there is most of it.
            expiredCount = expiringRows.count { it.expiryStatus == ExpiryStatus.EXPIRED },
            needsAttentionCount = expiringRows.count {
                it.expiryStatus == ExpiryStatus.EXPIRED || it.expiryStatus == ExpiryStatus.EXPIRES_TODAY
            },
            // Whatever the card is showing is left out of the list under it: the same row
            // twice, once as an instruction and once as an item, reads as two products.
            expiringSoon = expiringRows.filterNot { it.batchId == eatFirst?.batchId }.take(EXPIRING_LIMIT),
            recent = recent.map { it.toRowUi(today, prefs.soonThresholdDays, determineExpiryStatus) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    /**
     * Asks the inventory tab to open on exactly what the summary counted. A filter that
     * showed more or less than the number just tapped would make the number untrustworthy.
     */
    fun requestAttentionFilter() = filterRequest.request(InventoryStatusFilter.TODAY)

    fun quickAction(action: QuickAction, batchId: Long, expirationDate: LocalDate? = null) {
        viewModelScope.launch { quickActions.perform(action, batchId, expirationDate) }
    }

    fun undoQuickAction() {
        viewModelScope.launch { quickActions.undo() }
    }

    fun consumeQuickActionSignal() = quickActions.consumeSignal()

    /**
     * What to eat first among the things going off.
     *
     * An opened pack wins over a sealed one even if the sealed one expires sooner: opening
     * it started a clock the printed date knows nothing about, and half a carton of milk
     * left open is what actually gets thrown away. Within that, the nearest date. Batches
     * with no date at all cannot be ranked and are never picked — the card would be
     * telling the user to hurry for no stated reason.
     */
    private fun List<InventoryRowUi>.pickEatFirst(): InventoryRowUi? = this
        .filter { it.remainingDays != null }
        .minWithOrNull(
            compareByDescending<InventoryRowUi> { it.isOpened }.thenBy { it.remainingDays },
        )

    private companion object {
        const val RECENT_LIMIT = 5
        const val EXPIRING_LIMIT = 10
    }
}
