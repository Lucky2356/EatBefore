package com.eatbefore.domain.usecase

import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.InventoryItem
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.repository.StorageLocationRepository
import com.eatbefore.testutil.FakeAppClock
import com.eatbefore.testutil.FakeInventoryRepository
import com.eatbefore.testutil.FakeProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RepeatPurchaseUseCaseTest {

    private val clock = FakeAppClock(Instant.parse("2026-10-03T10:00:00Z"))
    private val fridge = StorageLocation(id = 1, name = "Fridge", isDefault = true)
    private val pantry = StorageLocation(id = 2, name = "Pantry")
    private val products = FakeProductRepository()
    private val inventory = FakeInventoryRepository()

    private val locations = object : StorageLocationRepository {
        override fun observeActive(): Flow<List<StorageLocation>> = flowOf(listOf(fridge, pantry))
        override fun observeAll(): Flow<List<StorageLocation>> = flowOf(listOf(fridge, pantry))
        override suspend fun getById(id: Long) = listOf(fridge, pantry).firstOrNull { it.id == id }
        override suspend fun getDefault() = fridge
        override suspend fun setDefault(id: Long) = Unit
        override suspend fun upsert(location: StorageLocation) = location.id
    }

    private val repeat = RepeatPurchaseUseCase(inventory, locations, AddBatchUseCase(products, inventory, clock), clock)

    /** A yoghurt that kept 14 days last time gets 14 days from today, in the same place. */
    @Test
    fun `last time's amount, place and shelf span are reused`() = runTest {
        val productId = products.upsert(Product(name = "Йогурт"))
        inventory.productBatches.value = listOf(
            InventoryItem(
                batch = InventoryBatch(
                    id = 5,
                    productId = productId,
                    storageLocationId = pantry.id,
                    quantity = 0.0,
                    initialQuantity = 4.0,
                    measurementUnit = MeasurementUnit.PIECE,
                    purchaseDate = LocalDate.of(2026, 9, 1),
                    expirationDate = LocalDate.of(2026, 9, 15),
                ),
                product = Product(id = productId, name = "Йогурт"),
                location = pantry,
            ),
        )

        val batchId = repeat(productId)!!

        val batch = inventory.getBatch(batchId)!!
        assertEquals(4.0, batch.quantity, 0.0)
        assertEquals(pantry.id, batch.storageLocationId)
        assertEquals(LocalDate.of(2026, 10, 17), batch.expirationDate)
    }

    @Test
    fun `nothing bought before, nothing to repeat`() = runTest {
        val productId = products.upsert(Product(name = "Хлеб"))

        assertNull(repeat(productId))
    }
}
