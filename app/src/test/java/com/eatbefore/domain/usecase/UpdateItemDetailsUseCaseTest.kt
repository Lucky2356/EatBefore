package com.eatbefore.domain.usecase

import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.Product
import com.eatbefore.testutil.FakeAppClock
import com.eatbefore.testutil.FakeInventoryRepository
import com.eatbefore.testutil.FakeProductRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class UpdateItemDetailsUseCaseTest {

    private lateinit var products: FakeProductRepository
    private lateinit var inventory: FakeInventoryRepository
    private lateinit var useCase: UpdateItemDetailsUseCase
    private val clock = FakeAppClock()

    private val originalExpiry = LocalDate.parse("2026-08-01")

    @Before
    fun setUp() {
        products = FakeProductRepository(
            mutableMapOf(
                1L to Product(id = 1, name = "Milk", brand = "Old", category = "Dairy"),
            ),
        )
        inventory = FakeInventoryRepository(
            batches = mutableMapOf(
                1L to InventoryBatch(
                    id = 1,
                    productId = 1,
                    storageLocationId = 1,
                    quantity = 1.0,
                    initialQuantity = 1.0,
                    expirationDate = originalExpiry,
                ),
            ),
        )
        useCase = UpdateItemDetailsUseCase(products, inventory, clock)
    }

    private fun params(
        name: String = "Milk",
        brand: String? = "Old",
        category: String? = "Dairy",
        expiry: LocalDate? = originalExpiry,
        note: String? = null,
    ) = UpdateItemDetailsUseCase.Params(1, name, brand, category, expiry, note)

    @Test
    fun updatesProductCardFields() = runTest {
        useCase(params(name = "Whole milk", brand = "New", category = "Drinks"))

        val product = products.getById(1)!!
        assertEquals("Whole milk", product.name)
        assertEquals("New", product.brand)
        assertEquals("Drinks", product.category)
    }

    @Test
    fun updatesBatchExpiryAndRecordsUpdatedEvent() = runTest {
        val newExpiry = LocalDate.parse("2026-09-15")
        useCase(params(expiry = newExpiry, note = "  opened box  "))

        val batch = inventory.batches[1]!!
        assertEquals(newExpiry, batch.expirationDate)
        assertEquals("opened box", batch.note)

        val event = inventory.events.last()
        assertEquals(EventType.UPDATED, event.eventType)
        assertEquals(1L, event.inventoryBatchId)
    }

    @Test
    fun clearingExpiryIsAllowed() = runTest {
        useCase(params(expiry = null))
        assertNull(inventory.batches[1]!!.expirationDate)
    }

    @Test
    fun noChanges_writesNoEvent() = runTest {
        useCase(params())
        assertTrue(inventory.events.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankName_isRejected() = runTest {
        useCase(params(name = "   "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownBatch_isRejected() = runTest {
        useCase(
            UpdateItemDetailsUseCase.Params(
                batchId = 99,
                name = "Milk",
                brand = null,
                category = null,
                expirationDate = null,
                note = null,
            ),
        )
    }
}
