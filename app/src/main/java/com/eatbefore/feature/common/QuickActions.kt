package com.eatbefore.feature.common

import androidx.annotation.StringRes
import com.eatbefore.R
import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.repository.InventoryRepository
import com.eatbefore.domain.usecase.AddBatchUseCase
import com.eatbefore.domain.usecase.AddToShoppingListUseCase
import com.eatbefore.domain.usecase.ChangeQuantityUseCase
import com.eatbefore.domain.usecase.MarkBatchStatusUseCase
import com.eatbefore.domain.usecase.OpenBatchUseCase
import com.eatbefore.domain.usecase.RestoreBatchUseCase
import com.eatbefore.domain.usecase.UndoLastActionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject

/**
 * What a long press on a stock row can do without opening the product.
 *
 * [SELECT] is the odd one out: it changes nothing in the inventory, it turns the screen's
 * selection mode on. It rides along here so the menu has one callback instead of two, and
 * the screen intercepts it before it ever reaches [QuickActions].
 */
enum class QuickAction { OPEN, DECREMENT, FINISHED, DISCARD, TO_SHOPPING, REPEAT, SELECT }

/**
 * A finished quick action, waiting to be shown as a snackbar.
 *
 * [id] increments on every action so two identical ones in a row still re-trigger the
 * snackbar. [undoable] is false where [UndoLastActionUseCase] has nothing to reverse —
 * offering an Undo that quietly does nothing would be worse than not offering one.
 */
data class QuickActionSignal(@StringRes val messageRes: Int, val undoable: Boolean, val id: Long)

/**
 * Stock changes reachable straight from a list row, shared by the home and inventory
 * screens.
 *
 * It lives outside both ViewModels so the two screens cannot drift apart: an action that
 * was undoable on one screen but not the other would be a trap. Not a singleton — each
 * screen keeps its own signal, so a snackbar belongs to the screen the user is looking at.
 */
class QuickActions @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val openBatch: OpenBatchUseCase,
    private val changeQuantity: ChangeQuantityUseCase,
    private val addToShoppingList: AddToShoppingListUseCase,
    private val addBatch: AddBatchUseCase,
    private val markStatus: MarkBatchStatusUseCase,
    private val restoreBatch: RestoreBatchUseCase,
    private val undoLastAction: UndoLastActionUseCase,
) {

    private val _signal = MutableStateFlow<QuickActionSignal?>(null)
    val signal: StateFlow<QuickActionSignal?> = _signal.asStateFlow()

    private var counter = 0L

    /** Batches written off by the last bulk action, kept until its undo is answered. */
    private var bulkBatchIds: List<Long>? = null

    /**
     * Runs [action] against [batchId]. [expirationDate] is only read by
     * [QuickAction.REPEAT], which is the one action that needs an answer from the user.
     */
    suspend fun perform(action: QuickAction, batchId: Long, expirationDate: LocalDate? = null) {
        when (action) {
            QuickAction.OPEN -> record(R.string.event_opened) {
                openBatch(batchId)
                true
            }

            QuickAction.DECREMENT -> record(R.string.event_quantity_changed) {
                val batch = inventoryRepository.getBatch(batchId) ?: return@record false
                changeQuantity(batchId, batch.quantity - 1)
                true
            }

            QuickAction.FINISHED -> record(R.string.event_consumed) {
                changeQuantity(batchId, 0.0)
                true
            }

            QuickAction.DISCARD -> record(R.string.event_discarded) {
                markStatus(batchId, BatchStatus.DISCARDED)
                true
            }

            // A shopping-list entry is not a stock event, so there is nothing for the
            // undo chain to roll back — the snackbar says so by omitting the action.
            QuickAction.TO_SHOPPING -> record(R.string.shopping_added, undoable = false) {
                val batch = inventoryRepository.getBatch(batchId) ?: return@record false
                addToShoppingList(
                    AddToShoppingListUseCase.Params(
                        productId = batch.productId,
                        sourceInventoryBatchId = batchId,
                    ),
                )
                true
            }

            QuickAction.REPEAT -> record(R.string.event_added) {
                val batch = inventoryRepository.getBatch(batchId) ?: return@record false
                addBatch(
                    AddBatchUseCase.Params(
                        productId = batch.productId,
                        storageLocationId = batch.storageLocationId,
                        quantity = 1.0,
                        measurementUnit = batch.measurementUnit,
                        expirationDate = expirationDate,
                    ),
                )
                true
            }

            // Intercepted by the screen (see rememberQuickActionHandler): it is a mode,
            // not a change to any batch.
            QuickAction.SELECT -> Unit
        }
    }

    /**
     * Writes off several batches at once.
     *
     * The ids are remembered so [undo] can put back *all* of them: the undo chain reverses
     * one event, which after a bulk write-off would restore one item out of four and leave
     * the user worse off than with no undo at all.
     */
    suspend fun performBulk(action: QuickAction, batchIds: List<Long>) {
        require(action == QuickAction.FINISHED || action == QuickAction.DISCARD) {
            "Only writing off makes sense in bulk, not $action"
        }
        val done = batchIds.filter { batchId ->
            runCatching {
                when (action) {
                    QuickAction.DISCARD -> markStatus(batchId, BatchStatus.DISCARDED)
                    else -> changeQuantity(batchId, 0.0)
                }
            }.isSuccess
        }
        if (done.isEmpty()) return

        bulkBatchIds = done
        _signal.value = QuickActionSignal(
            messageRes = if (action == QuickAction.DISCARD) {
                R.string.event_discarded
            } else {
                R.string.event_consumed
            },
            undoable = true,
            id = ++counter,
        )
    }

    suspend fun undo() {
        val bulk = bulkBatchIds
        if (bulk != null) {
            // Restoring by id rather than replaying the undo chain: the chain only knows
            // about the last event, and here there were several.
            bulkBatchIds = null
            bulk.forEach { batchId -> runCatching { restoreBatch(batchId) } }
        } else {
            runCatching { undoLastAction() }
        }
        _signal.value = null
    }

    fun consumeSignal() {
        bulkBatchIds = null
        _signal.value = null
    }

    /**
     * Signals only after the change actually landed — hence the Boolean: a batch that is
     * already gone (the other household member wrote it off while the menu was open) is
     * not a failure, but it is not a change either, and a snackbar claiming otherwise
     * would offer an undo that rolls back something else entirely.
     */
    private suspend fun record(
        @StringRes messageRes: Int,
        undoable: Boolean = true,
        block: suspend () -> Boolean,
    ) {
        runCatching { block() }.onSuccess { changed ->
            if (!changed) return
            // A single action supersedes any pending bulk one: undo must reverse what the
            // snackbar on screen is actually offering to reverse.
            bulkBatchIds = null
            _signal.value = QuickActionSignal(
                messageRes = messageRes,
                undoable = undoable,
                id = ++counter,
            )
        }
    }
}
