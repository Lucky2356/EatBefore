package com.eatbefore.feature.common

import androidx.annotation.StringRes
import com.eatbefore.R
import com.eatbefore.domain.repository.InventoryRepository
import com.eatbefore.domain.usecase.AddBatchUseCase
import com.eatbefore.domain.usecase.AddToShoppingListUseCase
import com.eatbefore.domain.usecase.ChangeQuantityUseCase
import com.eatbefore.domain.usecase.OpenBatchUseCase
import com.eatbefore.domain.usecase.UndoLastActionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject

/** What a long press on a stock row can do without opening the product. */
enum class QuickAction { OPEN, DECREMENT, FINISHED, TO_SHOPPING, REPEAT }

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
    private val undoLastAction: UndoLastActionUseCase,
) {

    private val _signal = MutableStateFlow<QuickActionSignal?>(null)
    val signal: StateFlow<QuickActionSignal?> = _signal.asStateFlow()

    private var counter = 0L

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
        }
    }

    suspend fun undo() {
        runCatching { undoLastAction() }
        _signal.value = null
    }

    fun consumeSignal() {
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
            _signal.value = QuickActionSignal(
                messageRes = messageRes,
                undoable = undoable,
                id = ++counter,
            )
        }
    }
}
