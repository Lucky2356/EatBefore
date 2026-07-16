package com.eatbefore.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.repository.HistoryRepository
import com.eatbefore.domain.usecase.RestoreBatchUseCase
import com.eatbefore.domain.usecase.UndoLastActionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val events: List<InventoryEvent> = emptyList(),
    val filter: EventType? = null,
    /** True while more rows exist beyond the current page. */
    val canLoadMore: Boolean = false,
)

/** Event types that removed stock and can therefore be restored from the list. */
private val RESTORABLE = setOf(EventType.CONSUMED, EventType.DISCARDED, EventType.EXPIRED)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val restoreBatch: RestoreBatchUseCase,
    private val undoLastAction: UndoLastActionUseCase,
) : ViewModel() {

    private val filter = MutableStateFlow<EventType?>(null)
    private val limit = MutableStateFlow(PAGE_SIZE)

    private val query = combine(filter, limit) { activeFilter, activeLimit ->
        activeFilter to activeLimit
    }

    val uiState: StateFlow<HistoryUiState> = query
        .flatMapLatest { (activeFilter, activeLimit) ->
            // Fetch one extra row to learn whether another page exists.
            historyRepository.observeRecent(activeLimit + 1, activeFilter)
                .let { flow ->
                    combine(flow, filter) { events, currentFilter ->
                        HistoryUiState(
                            events = events.take(activeLimit),
                            filter = currentFilter,
                            canLoadMore = events.size > activeLimit,
                        )
                    }
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState(),
        )

    fun setFilter(type: EventType?) {
        filter.value = type
        limit.value = PAGE_SIZE
    }

    /** Called when the list reaches its end; grows the page by [PAGE_SIZE]. */
    fun loadMore() {
        if (uiState.value.canLoadMore) limit.value += PAGE_SIZE
    }

    fun isRestorable(event: InventoryEvent): Boolean = event.eventType in RESTORABLE

    fun restore(event: InventoryEvent) {
        viewModelScope.launch { runCatching { restoreBatch(event.inventoryBatchId) } }
    }

    fun undoLast() {
        viewModelScope.launch { runCatching { undoLastAction() } }
    }

    private companion object {
        const val PAGE_SIZE = 100
    }
}
