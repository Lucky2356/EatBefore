package com.eatbefore.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.domain.repository.HistoryRepository
import com.eatbefore.domain.repository.ProductRepository
import com.eatbefore.domain.usecase.AnalyticsSummary
import com.eatbefore.domain.usecase.BuildAnalyticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

enum class AnalyticsPeriod(val days: Long?) {
    WEEK(7),
    MONTH(30),
    YEAR(365),
    ALL(null),
}

data class AnalyticsUiState(
    val isLoading: Boolean = true,
    val period: AnalyticsPeriod = AnalyticsPeriod.MONTH,
    val summary: AnalyticsSummary? = null,
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    historyRepository: HistoryRepository,
    productRepository: ProductRepository,
    private val buildAnalytics: BuildAnalyticsUseCase,
    private val clock: AppClock,
) : ViewModel() {

    private val period = MutableStateFlow(AnalyticsPeriod.MONTH)

    val uiState: StateFlow<AnalyticsUiState> = combine(
        historyRepository.observeAll(),
        productRepository.observeAll(),
        period,
    ) { events, products, activePeriod ->
        val from = activePeriod.days
            ?.let { clock.now().minus(it, ChronoUnit.DAYS) }
            ?: Instant.EPOCH
        AnalyticsUiState(
            isLoading = false,
            period = activePeriod,
            summary = buildAnalytics(events, products.associateBy { it.id }, from),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AnalyticsUiState(),
    )

    fun setPeriod(value: AnalyticsPeriod) {
        period.value = value
    }
}
