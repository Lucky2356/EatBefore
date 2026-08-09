package com.eatbefore.feature.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.R
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
import com.eatbefore.domain.usecase.AddBatchUseCase
import com.eatbefore.domain.usecase.AddToShoppingListUseCase
import com.eatbefore.domain.usecase.ChangeQuantityUseCase
import com.eatbefore.domain.usecase.DetermineExpiryStatusUseCase
import com.eatbefore.domain.usecase.MarkBatchStatusUseCase
import com.eatbefore.domain.usecase.MoveBatchUseCase
import com.eatbefore.domain.usecase.OpenBatchUseCase
import com.eatbefore.domain.usecase.UndoLastActionUseCase
import com.eatbefore.domain.usecase.UpdateItemDetailsUseCase
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
import kotlinx.coroutines.flow.update
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
    /** What the last action did, shown in the undo snackbar. */
    val actionMessageRes: Int? = null,
    /** Set after the item is used up: offer to put it on the shopping list. */
    val offerShoppingList: Boolean = false,
    /** True once the batch no longer exists as present (e.g. add was undone). */
    val closed: Boolean = false,
    /** Detailed quantity mode: "decrease" asks for the exact remaining amount. */
    val detailedMode: Boolean = false,
)

/** Transient, screen-local signals kept in one flow so the state combine stays small. */
private data class ProductLocalState(
    val undoableActionAt: Long? = null,
    val actionMessageRes: Int? = null,
    val offerShoppingList: Boolean = false,
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
    private val updateItemDetails: UpdateItemDetailsUseCase,
    private val addToShoppingList: AddToShoppingListUseCase,
    private val addBatch: AddBatchUseCase,
    private val clock: AppClock,
) : ViewModel() {

    private val batchId: Long = checkNotNull(savedStateHandle[Routes.PRODUCT_BATCH_ARG]) {
        "Missing ${Routes.PRODUCT_BATCH_ARG}"
    }
    private val localState = MutableStateFlow(ProductLocalState())

    /** Today per the app clock, for the expiry presets shown in dialogs. */
    val today: java.time.LocalDate get() = clock.today()

    private val itemFlow: Flow<InventoryItem?> = inventoryRepository.observeItem(batchId)

    private val historyFlow: Flow<List<InventoryEvent>> = itemFlow.flatMapLatest { item ->
        if (item == null) flowOf(emptyList()) else historyRepository.observeForProduct(item.product.id)
    }

    val uiState: StateFlow<ProductUiState> = combine(
        itemFlow,
        historyFlow,
        storageLocationRepository.observeActive(),
        preferences.preferences,
        localState,
    ) { item, history, locations, prefs, local ->
        if (item == null) {
            ProductUiState(
                isLoading = false,
                locations = locations,
                // Only pop once an action actually removed the batch, not on first load.
                closed = local.undoableActionAt != null && !local.offerShoppingList,
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
                undoableActionAt = local.undoableActionAt,
                actionMessageRes = local.actionMessageRes,
                offerShoppingList = local.offerShoppingList,
                detailedMode = prefs.detailedQuantityMode,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProductUiState(),
    )

    fun open() = runAction(messageRes = R.string.event_opened) { openBatch(batchId) }

    fun decrement() = runAction(messageRes = R.string.event_quantity_changed) {
        val batch = inventoryRepository.getBatch(batchId) ?: return@runAction
        changeQuantity(batchId, batch.quantity - 1)
    }

    /** Corrects the amount upwards — a second pack of the same batch, or a miscount. */
    fun increment() = runAction(messageRes = R.string.event_quantity_changed) {
        val batch = inventoryRepository.getBatch(batchId) ?: return@runAction
        changeQuantity(batchId, batch.quantity + 1)
    }

    /** Detailed mode: set an exact remaining amount (grams, percent, pieces, …). */
    fun setQuantity(value: Double) = runAction(
        offerShopping = value <= 0.0,
        messageRes = R.string.event_quantity_changed,
    ) {
        changeQuantity(batchId, value)
    }

    /** Using it up offers to put the product straight onto the shopping list. */
    fun markFinished() = runAction(offerShopping = true, messageRes = R.string.event_consumed) {
        changeQuantity(batchId, 0.0)
    }

    fun discard() = runAction(offerShopping = true, messageRes = R.string.event_discarded) {
        markStatus(batchId, BatchStatus.DISCARDED)
    }

    fun markExpired() = runAction(messageRes = R.string.event_expired) {
        markStatus(batchId, BatchStatus.EXPIRED)
    }

    fun moveTo(locationId: Long) = runAction(messageRes = R.string.event_moved) {
        moveBatch(batchId, locationId)
    }

    /** Edits card + batch details (name, brand, category, expiry, note). */
    fun updateDetails(
        name: String,
        brand: String?,
        category: String?,
        expirationDate: java.time.LocalDate?,
        note: String?,
    ) = runAction(messageRes = R.string.event_updated) {
        updateItemDetails(
            UpdateItemDetailsUseCase.Params(
                batchId = batchId,
                name = name,
                brand = brand,
                category = category,
                expirationDate = expirationDate,
                note = note,
            ),
        )
    }

    /**
     * "Bought another one": a second batch of the same product in the same place, so a
     * weekly repurchase does not mean retyping a form that is already on screen. Only the
     * expiry differs between packages, so only the expiry is asked for.
     */
    fun repeatPurchase(expirationDate: java.time.LocalDate?) = runAction(messageRes = R.string.event_added) {
        val batch = inventoryRepository.getBatch(batchId) ?: return@runAction
        addBatch(
            AddBatchUseCase.Params(
                productId = batch.productId,
                storageLocationId = batch.storageLocationId,
                quantity = 1.0,
                measurementUnit = batch.measurementUnit,
                expirationDate = expirationDate,
            ),
        )
    }

    /** Quick action: put this product on the shopping list without writing it off. */
    fun addToShopping() {
        viewModelScope.launch {
            val productId = uiState.value.item?.product?.id ?: return@launch
            runCatching {
                addToShoppingList(
                    AddToShoppingListUseCase.Params(
                        productId = productId,
                        sourceInventoryBatchId = batchId,
                    ),
                )
            }
        }
    }

    /** Accepts the "add to shopping list?" offer shown after the item ran out. */
    fun acceptShoppingOffer() {
        viewModelScope.launch {
            val productId = inventoryRepository.getBatch(batchId)?.productId
            if (productId != null) {
                runCatching {
                    addToShoppingList(
                        AddToShoppingListUseCase.Params(
                            productId = productId,
                            sourceInventoryBatchId = batchId,
                        ),
                    )
                }
            }
            localState.update { it.copy(offerShoppingList = false) }
        }
    }

    fun dismissShoppingOffer() {
        localState.update { it.copy(offerShoppingList = false) }
    }

    fun undo() {
        viewModelScope.launch {
            runCatching { undoLastAction() }
            localState.value = ProductLocalState()
        }
    }

    fun consumeUndoSignal() {
        localState.update { it.copy(undoableActionAt = null) }
    }

    private fun runAction(
        offerShopping: Boolean = false,
        messageRes: Int? = null,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess {
                    localState.update {
                        it.copy(
                            undoableActionAt = clock.now().toEpochMilli(),
                            actionMessageRes = messageRes,
                            offerShoppingList = offerShopping,
                        )
                    }
                }
        }
    }
}
