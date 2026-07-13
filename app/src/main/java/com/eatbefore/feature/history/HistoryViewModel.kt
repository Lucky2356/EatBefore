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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val events: List<InventoryEvent> = emptyList(),
    val filter: EventType? = null,
)

/** Event types that removed stock and can therefore be restored from the list. */
private val RESTORABLE = setOf(EventType.CONSUMED, EventType.DISCARDED, EventType.EXPIRED)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    historyRepository: HistoryRepository,
    private val restoreBatch: RestoreBatchUseCase,
    private val undoLastAction: UndoLastActionUseCase,
) : ViewModel() {

    private val filter = MutableStateFlow<EventType?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(
        historyRepository.observeAll(),
        filter,
    ) { events, activeFilter ->
        HistoryUiState(
            events = if (activeFilter == null) events else events.filter { it.eventType == activeFilter },
            filter = activeFilter,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    fun setFilter(type: EventType?) { filter.value = type }

    fun isRestorable(event: InventoryEvent): Boolean = event.eventType in RESTORABLE

    fun restore(event: InventoryEvent) {
        viewModelScope.launch { runCatching { restoreBatch(event.inventoryBatchId) } }
    }

    fun undoLast() {
        viewModelScope.launch { runCatching { undoLastAction() } }
    }
}
