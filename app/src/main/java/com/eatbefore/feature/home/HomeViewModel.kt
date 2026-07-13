package com.eatbefore.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.datastore.UserPreferencesRepository
import com.eatbefore.domain.repository.InventoryRepository
import com.eatbefore.domain.usecase.DetermineExpiryStatusUseCase
import com.eatbefore.feature.common.InventoryRowUi
import com.eatbefore.feature.common.toRowUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val totalCount: Int = 0,
    val expiringSoon: List<InventoryRowUi> = emptyList(),
    val recent: List<InventoryRowUi> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    preferences: UserPreferencesRepository,
    private val determineExpiryStatus: DetermineExpiryStatusUseCase,
    private val clock: AppClock,
) : ViewModel() {

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
        HomeUiState(
            isLoading = false,
            totalCount = count,
            expiringSoon = expiring.take(EXPIRING_LIMIT)
                .map { it.toRowUi(today, prefs.soonThresholdDays, determineExpiryStatus) },
            recent = recent.map { it.toRowUi(today, prefs.soonThresholdDays, determineExpiryStatus) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    private companion object {
        const val RECENT_LIMIT = 5
        const val EXPIRING_LIMIT = 10
    }
}
