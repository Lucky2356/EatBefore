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

    /** The unchanged form as the screen would submit it; each test copies what it changes. */
    private val unchanged = UpdateItemDetailsUseCase.Params(
        batchId = 1,
        name = "Milk",
        brand = "Old",
        category = "Dairy",
        expirationDate = originalExpiry,
        note = null,
    )

    private fun params(
        name: String = "Milk",
        brand: String? = "Old",
        category: String? = "Dairy",
        expiry: LocalDate? = originalExpiry,
        note: String? = null,
    ) = unchanged.copy(
        name = name,
        brand = brand,
        category = category,
        expirationDate = expiry,
        note = note,
    )

    @Test
    fun `a price typed on the card is stored with its currency`() = runTest {
        useCase(unchanged.copy(price = 89.0, currency = "RUB"))

        val batch = inventory.getBatch(1)!!
        assertEquals(89.0, batch.price!!, 0.001)
        assertEquals("RUB", batch.currency)
    }

    /** Clearing the field is a real answer; a currency with nothing to price is not. */
    @Test
    fun `clearing the price clears the currency with it`() = runTest {
        useCase(unchanged.copy(price = 89.0, currency = "RUB"))

        useCase(unchanged.copy(price = null, currency = "RUB"))

        val batch = inventory.getBatch(1)!!
        assertNull(batch.price)
        assertNull(batch.currency)
    }

    /**
     * A jar bought on holiday stays priced in euros: the caller passes the phone's current
     * currency, and it must not silently re-denominate what is already there.
     */
    @Test
    fun `an existing currency is not overwritten by the phone's default`() = runTest {
        inventory.batches[1] = inventory.getBatch(1)!!.copy(price = 5.0, currency = "EUR")

        useCase(unchanged.copy(price = 6.0, currency = "RUB"))

        assertEquals("EUR", inventory.getBatch(1)!!.currency)
    }

    @Test
    fun `the purchase date can be corrected`() = runTest {
        val bought = LocalDate.parse("2026-07-28")

        useCase(unchanged.copy(purchaseDate = bought))

        assertEquals(bought, inventory.getBatch(1)!!.purchaseDate)
    }

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
