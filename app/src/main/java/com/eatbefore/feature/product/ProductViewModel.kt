package com.eatbefore.feature.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.datastore.UserPreferencesRepository
import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.ExpiryStatus
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.model.InventoryItem
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.repository.HistoryRepository
import com.eatbefore.domain.repository.InventoryRepository
import com.eatbefore.domain.repository.StorageLocationRepository
import com.eatbefore.domain.usecase.ChangeQuantityUseCase
import com.eatbefore.domain.usecase.DetermineExpiryStatusUseCase
import com.eatbefore.domain.usecase.MarkBatchStatusUseCase
import com.eatbefore.domain.usecase.MoveBatchUseCase
import com.eatbefore.domain.usecase.OpenBatchUseCase
import com.eatbefore.domain.usecase.UndoLastActionUseCase
import com.eatbefore.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class ProductUiState(
    val isLoading: Boolean = true,
    val item: InventoryItem? = null,
    val expiryStatus: ExpiryStatus = ExpiryStatus.NO_DATE,
    val remainingDays: Long? = null,
    val history: List<InventoryEvent> = emptyList(),
    val locations: List<StorageLocation> = emptyList(),
    /** Timestamp of the last undoable action; drives a one-shot Undo snackbar. */
    val undoableActionAt: Long? = null,
    /** True once the batch no longer exists as present (e.g. add was undone). */
    val closed: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProductViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val inventoryRepository: InventoryRepository,
    historyRepository: HistoryRepository,
    storageLocationRepository: StorageLocationRepository,
    private val determineExpiryStatus: DetermineExpiryStatusUseCase,
    preferences: UserPreferencesRepository,
    private val openBatch: OpenBatchUseCase,
    private val changeQuantity: ChangeQuantityUseCase,
    private val markStatus: MarkBatchStatusUseCase,
    private val moveBatch: MoveBatchUseCase,
    private val undoLastAction: UndoLastActionUseCase,
    private val clock: AppClock,
) : ViewModel() {

    private val batchId: Long = checkNotNull(savedStateHandle[Routes.PRODUCT_BATCH_ARG]) {
        "Missing ${Routes.PRODUCT_BATCH_ARG}"
    }
    private val undoSignal = MutableStateFlow<Long?>(null)

    private val itemFlow: Flow<InventoryItem?> = inventoryRepository.observeItem(batchId)

    private val historyFlow: Flow<List<InventoryEvent>> = itemFlow.flatMapLatest { item ->
        if (item == null) flowOf(emptyList()) else historyRepository.observeForProduct(item.product.id)
    }

    val uiState: StateFlow<ProductUiState> = combine(
        itemFlow,
        historyFlow,
        storageLocationRepository.observeActive(),
        preferences.preferences,
        undoSignal,
    ) { item, history, locations, prefs, undoAt ->
        if (item == null) {
            ProductUiState(
                isLoading = false,
                locations = locations,
                closed = undoAt != null,
            )
        } else {
            val effective = item.batch.effectiveExpirationDate
            ProductUiState(
                isLoading = false,
                item = item,
                expiryStatus = determineExpiryStatus.forDate(effective, clock.today(), prefs.soonThresholdDays),
                remainingDays = effective?.let { ChronoUnit.DAYS.between(clock.today(), it) },
                history = history,
                locations = locations,
                undoableActionAt = undoAt,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProductUiState(),
    )

    fun open() = runAction { openBatch(batchId) }

    fun decrement() = runAction {
        val batch = inventoryRepository.getBatch(batchId) ?: return@runAction
        changeQuantity(batchId, batch.quantity - 1)
    }

    fun markFinished() = runAction { changeQuantity(batchId, 0.0) }
    fun discard() = runAction { markStatus(batchId, BatchStatus.DISCARDED) }
    fun markExpired() = runAction { markStatus(batchId, BatchStatus.EXPIRED) }
    fun moveTo(locationId: Long) = runAction { moveBatch(batchId, locationId) }

    fun undo() {
        viewModelScope.launch {
            runCatching { undoLastAction() }
            undoSignal.value = null
        }
    }

    fun consumeUndoSignal() {
        undoSignal.value = null
    }

    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { undoSignal.value = clock.now().toEpochMilli() }
        }
    }
}
