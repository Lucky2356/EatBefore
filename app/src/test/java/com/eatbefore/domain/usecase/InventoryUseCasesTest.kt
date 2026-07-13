package com.eatbefore.domain.usecase

import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.testutil.FakeAppClock
import com.eatbefore.testutil.FakeHistoryRepository
import com.eatbefore.testutil.FakeInventoryRepository
import com.eatbefore.testutil.FakeProductRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Behavioral tests for the stock-mutation use cases, asserting both the resulting batch
 * state and that the correct history event is recorded (the two must stay in lockstep).
 */
class InventoryUseCasesTest {

    private lateinit var clock: FakeAppClock
    private lateinit var products: FakeProductRepository
    private lateinit var inventory: FakeInventoryRepository
    private lateinit var history: FakeHistoryRepository

    private lateinit var addManual: AddManualProductUseCase
    private lateinit var addBatch: AddBatchUseCase
    private lateinit var changeQuantity: ChangeQuantityUseCase
    private lateinit var openBatch: OpenBatchUseCase
    private lateinit var markStatus: MarkBatchStatusUseCase
    private lateinit var moveBatch: MoveBatchUseCase
    private lateinit var restore: RestoreBatchUseCase
    private lateinit var undo: UndoLastActionUseCase

    @Before
    fun setUp() {
        clock = FakeAppClock()
        products = FakeProductRepository()
        inventory = FakeInventoryRepository()
        history = FakeHistoryRepository(inventory)
        val merge = MergeSameProductUseCase(products)
        addManual = AddManualProductUseCase(products, inventory, merge, clock)
        addBatch = AddBatchUseCase(products, inventory, clock)
        changeQuantity = ChangeQuantityUseCase(inventory, clock)
        openBatch = OpenBatchUseCase(inventory, CalculateExpirationAfterOpeningUseCase(), clock)
        markStatus = MarkBatchStatusUseCase(inventory, clock)
        moveBatch = MoveBatchUseCase(inventory, clock)
        restore = RestoreBatchUseCase(inventory, clock)
        undo = UndoLastActionUseCase(history, inventory, clock)
    }

    @Test
    fun addManual_createsProductBatchAndAddedEvent() = runTest {
        val batchId = addManual(
            AddManualProductUseCase.Params(
                name = "Milk",
                brand = "Farm",
                storageLocationId = 1,
                quantity = 2.0,
                measurementUnit = MeasurementUnit.LITER,
            ),
        )
        val batch = inventory.getBatch(batchId)!!
        assertEquals(2.0, batch.quantity, 0.0)
        assertEquals(BatchStatus.ACTIVE, batch.status)
        assertEquals(EventType.ADDED, inventory.lastEvent().eventType)
        assertEquals(1, products.observeAllCount())
    }

    @Test
    fun addManual_reusesExistingProductCard() = runTest {
        addManual(AddManualProductUseCase.Params(name = "Milk", brand = "Farm", storageLocationId = 1))
        addManual(AddManualProductUseCase.Params(name = "milk", brand = "farm", storageLocationId = 1))
        // Two batches, one shared product card (case-insensitive merge).
        assertEquals(1, products.observeAllCount())
        assertEquals(2, inventory.batches.size)
    }

    @Test
    fun changeQuantity_partial_marksPartiallyUsed() = runTest {
        val id = addManual(AddManualProductUseCase.Params(name = "Rice", storageLocationId = 1, quantity = 5.0))
        changeQuantity(id, 3.0)
        val batch = inventory.getBatch(id)!!
        assertEquals(3.0, batch.quantity, 0.0)
        assertEquals(BatchStatus.PARTIALLY_USED, batch.status)
        assertEquals(EventType.QUANTITY_CHANGED, inventory.lastEvent().eventType)
    }

    @Test
    fun changeQuantity_toZero_consumesAndSoftDeletes() = runTest {
        val id = addManual(AddManualProductUseCase.Params(name = "Rice", storageLocationId = 1, quantity = 5.0))
        changeQuantity(id, 0.0)
        val batch = inventory.getBatch(id)!!
        assertEquals(BatchStatus.CONSUMED, batch.status)
        assertNotNull(batch.deletedAt)
        assertEquals(EventType.CONSUMED, inventory.lastEvent().eventType)
    }

    @Test
    fun openBatch_setsOpenedStateAndEvent() = runTest {
        val id = addManual(AddManualProductUseCase.Params(name = "Juice", storageLocationId = 1))
        openBatch(id)
        val batch = inventory.getBatch(id)!!
        assertEquals(BatchStatus.OPENED, batch.status)
        assertNotNull(batch.openedAt)
        assertEquals(EventType.OPENED, inventory.lastEvent().eventType)
    }

    @Test
    fun markDiscarded_softDeletesWithEvent() = runTest {
        val id = addManual(AddManualProductUseCase.Params(name = "Bread", storageLocationId = 1))
        markStatus(id, BatchStatus.DISCARDED, reason = "moldy")
        val batch = inventory.getBatch(id)!!
        assertEquals(BatchStatus.DISCARDED, batch.status)
        assertNotNull(batch.deletedAt)
        assertEquals(EventType.DISCARDED, inventory.lastEvent().eventType)
    }

    @Test
    fun move_recordsPreviousAndNewLocation() = runTest {
        val id = addManual(AddManualProductUseCase.Params(name = "Cheese", storageLocationId = 1))
        moveBatch(id, newStorageLocationId = 2)
        val batch = inventory.getBatch(id)!!
        assertEquals(2, batch.storageLocationId)
        val event = inventory.lastEvent()
        assertEquals(EventType.MOVED, event.eventType)
        assertEquals(1L, event.previousStorageLocationId)
        assertEquals(2L, event.newStorageLocationId)
    }

    @Test
    fun restore_bringsBackConsumedBatch() = runTest {
        val id = addManual(AddManualProductUseCase.Params(name = "Eggs", storageLocationId = 1, quantity = 6.0))
        changeQuantity(id, 0.0)
        restore(id)
        val batch = inventory.getBatch(id)!!
        assertTrue(batch.status.isPresent)
        assertNull(batch.deletedAt)
        assertEquals(EventType.RESTORED, inventory.lastEvent().eventType)
    }

    @Test
    fun undo_reversesLastQuantityChange() = runTest {
        val id = addManual(AddManualProductUseCase.Params(name = "Yogurt", storageLocationId = 1, quantity = 4.0))
        changeQuantity(id, 1.0)
        val undone = undo()
        assertTrue(undone)
        assertEquals(4.0, inventory.getBatch(id)!!.quantity, 0.0)
    }

    @Test
    fun undo_reversesDiscard() = runTest {
        val id = addManual(AddManualProductUseCase.Params(name = "Ham", storageLocationId = 1))
        markStatus(id, BatchStatus.DISCARDED)
        assertTrue(undo())
        assertTrue(inventory.getBatch(id)!!.status.isPresent)
    }

    @Test
    fun undo_withNoHistory_returnsFalse() = runTest {
        assertFalse(undo())
    }
}
