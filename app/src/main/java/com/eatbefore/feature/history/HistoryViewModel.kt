package com.eatbefore.feature.history

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.R
import com.eatbefore.core.datastore.UserPreferencesRepository
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.repository.HistoryRepository
import com.eatbefore.domain.usecase.RestoreBatchUseCase
import com.eatbefore.domain.usecase.UndoLastActionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val events: List<InventoryEvent> = emptyList(),
    val filter: EventType? = null,
    /** True while more rows exist beyond the current page. */
    val canLoadMore: Boolean = false,
    /** Names of the other household devices, so events can be signed. */
    val peerNames: Map<String, String> = emptyMap(),
)

/**
 * Something worth saying out loud after a restore or an undo.
 *
 * [id] increments on every message so two identical ones in a row still re-trigger the
 * snackbar, the same way [com.eatbefore.feature.shopping.ShoppingMessage] does.
 */
data class HistoryMessage(@StringRes val textRes: Int, val id: Long)

/** Event types that removed stock and can therefore be restored from the list. */
private val RESTORABLE = setOf(EventType.CONSUMED, EventType.DISCARDED, EventType.EXPIRED)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val restoreBatch: RestoreBatchUseCase,
    private val undoLastAction: UndoLastActionUseCase,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    private val filter = MutableStateFlow<EventType?>(null)
    private val limit = MutableStateFlow(PAGE_SIZE)

    private val _message = MutableStateFlow<HistoryMessage?>(null)
    val message: StateFlow<HistoryMessage?> = _message.asStateFlow()

    private var messageCounter = 0L

    private val query = combine(filter, limit) { activeFilter, activeLimit ->
        activeFilter to activeLimit
    }

    private val peerNames = preferences.preferences.map { it.peerNames }

    val uiState: StateFlow<HistoryUiState> = query
        .flatMapLatest { (activeFilter, activeLimit) ->
            // Fetch one extra row to learn whether another page exists.
            historyRepository.observeRecent(activeLimit + 1, activeFilter)
                .let { flow ->
                    combine(flow, filter, peerNames) { events, currentFilter, names ->
                        HistoryUiState(
                            events = events.take(activeLimit),
                            filter = currentFilter,
                            canLoadMore = events.size > activeLimit,
                            peerNames = names,
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
        viewModelScope.launch {
            // Success is deliberately silent: restoring writes a RESTORED event, which
            // appears as the first row of the very list being looked at. A snackbar saying
            // the same thing on top of it is noise.
            runCatching { restoreBatch(event.inventoryBatchId) }
                .onFailure { announce(R.string.history_restore_failed) }
        }
    }

    fun undoLast() {
        viewModelScope.launch {
            runCatching { undoLastAction() }
                // false means there was nothing left to undo — not a failure, but not
                // nothing either: in silence it is indistinguishable from a dead button.
                .onSuccess { undone -> if (!undone) announce(R.string.history_undo_nothing) }
                .onFailure { announce(R.string.history_undo_failed) }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun announce(@StringRes textRes: Int) {
        _message.value = HistoryMessage(textRes = textRes, id = ++messageCounter)
    }

    private companion object {
        const val PAGE_SIZE = 100
    }
}
