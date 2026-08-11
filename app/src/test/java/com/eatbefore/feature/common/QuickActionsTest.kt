package com.eatbefore.feature.common

import com.eatbefore.R
import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.usecase.AddBatchUseCase
import com.eatbefore.domain.usecase.AddToShoppingListUseCase
import com.eatbefore.domain.usecase.CalculateExpirationAfterOpeningUseCase
import com.eatbefore.domain.usecase.ChangeQuantityUseCase
import com.eatbefore.domain.usecase.MarkBatchStatusUseCase
import com.eatbefore.domain.usecase.OpenBatchUseCase
import com.eatbefore.domain.usecase.RestoreBatchUseCase
import com.eatbefore.domain.usecase.UndoLastActionUseCase
import com.eatbefore.testutil.FakeAppClock
import com.eatbefore.testutil.FakeHistoryRepository
import com.eatbefore.testutil.FakeInventoryRepository
import com.eatbefore.testutil.FakeProductRepository
import com.eatbefore.testutil.FakeShoppingListRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Quick actions change stock straight from a list row, with no product card in between to
 * check first. That makes two things worth proving: the change is the one the user asked
 * for, and it can be taken back.
 */
class QuickActionsTest {

    private val clock = FakeAppClock()
    private lateinit var inventory: FakeInventoryRepository
    private lateinit var history: FakeHistoryRepository
    private lateinit var products: FakeProductRepository
    private lateinit var shopping: FakeShoppingListRepository
    private lateinit var quickActions: QuickActions

    private val batchId = 1L
    private val productId = 7L

    @Before
    fun setUp() {
        products = FakeProductRepository(
            mutableMapOf(productId to Product(id = productId, name = "Молоко")),
        )
        inventory = FakeInventoryRepository(
            batches = mutableMapOf(
                batchId to InventoryBatch(
                    id = batchId,
                    productId = productId,
                    storageLocationId = 2L,
                    quantity = 2.0,
                    initialQuantity = 2.0,
                    measurementUnit = MeasurementUnit.PIECE,
                    expirationDate = LocalDate.of(2026, 8, 1),
                ),
            ),
        )
        history = FakeHistoryRepository(inventory)
        shopping = FakeShoppingListRepository()

        quickActions = QuickActions(
            inventoryRepository = inventory,
            openBatch = OpenBatchUseCase(inventory, CalculateExpirationAfterOpeningUseCase(), clock),
            changeQuantity = ChangeQuantityUseCase(inventory, clock),
            addToShoppingList = AddToShoppingListUseCase(shopping, history, clock),
            addBatch = AddBatchUseCase(products, inventory, clock),
            markStatus = MarkBatchStatusUseCase(inventory, clock),
            restoreBatch = RestoreBatchUseCase(inventory, clock),
            undoLastAction = UndoLastActionUseCase(history, inventory, clock),
        )
    }

    private fun batch() = inventory.batches.getValue(batchId)

    @Test
    fun `decrement takes exactly one off the batch`() = runTest {
        quickActions.perform(QuickAction.DECREMENT, batchId)

        assertEquals(1.0, batch().quantity, 0.0)
    }

    @Test
    fun `finished writes the batch off`() = runTest {
        quickActions.perform(QuickAction.FINISHED, batchId)

        assertEquals(0.0, batch().quantity, 0.0)
        assertEquals(BatchStatus.CONSUMED, batch().status)
    }

    @Test
    fun `open stamps the batch as opened`() = runTest {
        quickActions.perform(QuickAction.OPEN, batchId)

        assertNotNull(batch().openedAt)
        assertEquals(BatchStatus.OPENED, batch().status)
    }

    @Test
    fun `to shopping adds the product to the list`() = runTest {
        quickActions.perform(QuickAction.TO_SHOPPING, batchId)

        assertEquals(listOf(productId), shopping.items.values.map { it.productId })
    }

    @Test
    fun `repeat adds a second batch of the same product in the same place`() = runTest {
        val expiry = LocalDate.of(2026, 9, 1)

        quickActions.perform(QuickAction.REPEAT, batchId, expirationDate = expiry)

        val added = inventory.batches.values.single { it.id != batchId }
        assertEquals(productId, added.productId)
        assertEquals(2L, added.storageLocationId)
        assertEquals(expiry, added.expirationDate)
    }

    @Test
    fun `undo puts back what the last action changed`() = runTest {
        quickActions.perform(QuickAction.FINISHED, batchId)

        quickActions.undo()

        assertEquals(2.0, batch().quantity, 0.0)
        assertEquals(BatchStatus.ACTIVE, batch().status)
    }

    /** Undoing "one more package" must remove that package, not touch the original. */
    @Test
    fun `undo of repeat archives the new batch only`() = runTest {
        quickActions.perform(QuickAction.REPEAT, batchId, expirationDate = null)

        quickActions.undo()

        val added = inventory.batches.values.single { it.id != batchId }
        assertEquals(BatchStatus.ARCHIVED, added.status)
        assertEquals(BatchStatus.ACTIVE, batch().status)
    }

    /**
     * The whole reason bulk exists: sorting out a fridge full of spoiled food. Undoing it
     * has to bring back every item, not the last one — half a rollback is worse than none.
     */
    @Test
    fun `undo after a bulk write-off restores every batch`() = runTest {
        val ids = (2L..4L).map { id ->
            inventory.batches[id] = batch().copy(id = id)
            id
        }

        quickActions.performBulk(QuickAction.DISCARD, ids)
        assertTrue(ids.all { inventory.batches.getValue(it).status == BatchStatus.DISCARDED })

        quickActions.undo()

        assertTrue(ids.all { inventory.batches.getValue(it).status.isPresent })
        assertTrue(ids.all { inventory.batches.getValue(it).deletedAt == null })
    }

    @Test
    fun `a bulk write-off empties every selected batch`() = runTest {
        inventory.batches[2L] = batch().copy(id = 2L)

        quickActions.performBulk(QuickAction.FINISHED, listOf(batchId, 2L))

        assertEquals(0.0, inventory.batches.getValue(batchId).quantity, 0.0)
        assertEquals(0.0, inventory.batches.getValue(2L).quantity, 0.0)
    }

    /** Nothing happened, so there is nothing to announce and nothing to undo. */
    @Test
    fun `a bulk action over batches that are all gone says nothing`() = runTest {
        quickActions.performBulk(QuickAction.DISCARD, listOf(998L, 999L))

        assertNull(quickActions.signal.value)
    }

    /**
     * The snackbar on screen belongs to the newest action. If a single action follows a
     * bulk one, undo must reverse that single action — not resurrect the earlier batch.
     */
    @Test
    fun `a later single action takes over the undo`() = runTest {
        inventory.batches[2L] = batch().copy(id = 2L)
        quickActions.performBulk(QuickAction.DISCARD, listOf(2L))

        quickActions.perform(QuickAction.DECREMENT, batchId)
        quickActions.undo()

        assertEquals(2.0, batch().quantity, 0.0)
        assertEquals(BatchStatus.DISCARDED, inventory.batches.getValue(2L).status)
    }

    @Test
    fun `a finished action signals what happened and offers undo`() = runTest {
        quickActions.perform(QuickAction.DECREMENT, batchId)

        val signal = quickActions.signal.value
        assertNotNull(signal)
        assertEquals(R.string.event_quantity_changed, signal!!.messageRes)
        assertTrue(signal.undoable)
    }

    /**
     * A shopping-list entry is not a stock event, so the undo chain has nothing to reverse.
     * Offering the button anyway would silently roll back whatever came before it.
     */
    @Test
    fun `adding to the shopping list is signalled as not undoable`() = runTest {
        quickActions.perform(QuickAction.TO_SHOPPING, batchId)

        assertFalse(quickActions.signal.value!!.undoable)
    }

    /** Two identical actions in a row must both be announced, not silently coalesced. */
    @Test
    fun `each action gets its own signal id`() = runTest {
        quickActions.perform(QuickAction.DECREMENT, batchId)
        val first = quickActions.signal.value!!.id

        quickActions.perform(QuickAction.DECREMENT, batchId)

        assertEquals(first + 1, quickActions.signal.value!!.id)
    }

    @Test
    fun `an action on a batch that is gone changes nothing and says nothing`() = runTest {
        quickActions.perform(QuickAction.DECREMENT, batchId = 999L)

        assertNull(quickActions.signal.value)
        assertEquals(2.0, batch().quantity, 0.0)
    }

    /** The history stays truthful: a quick action is an ordinary recorded event. */
    @Test
    fun `a quick action is recorded in history`() = runTest {
        quickActions.perform(QuickAction.OPEN, batchId)

        assertEquals(listOf(EventType.OPENED), inventory.eventTypes())
    }
}
