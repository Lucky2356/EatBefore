package com.eatbefore.domain.usecase

import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.InventoryItem
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.testutil.FakeInventoryRepository
import com.eatbefore.testutil.FakeProductRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Striking a product card off the catalogue — the one action in the app that can make
 * something the user typed disappear from view, so each refusal matters as much as the
 * deletion itself.
 */
class DeleteProductUseCaseTest {

    private lateinit var products: FakeProductRepository
    private lateinit var inventory: FakeInventoryRepository
    private lateinit var deleteProduct: DeleteProductUseCase

    private val location = StorageLocation(id = 1, name = "Холодильник")

    @Before
    fun setUp() {
        products = FakeProductRepository(
            mutableMapOf(1L to Product(id = 1, name = "Молоко"), 2L to Product(id = 2, name = "Хлеб")),
        )
        inventory = FakeInventoryRepository()
        deleteProduct = DeleteProductUseCase(products, inventory)
    }

    private fun stock(productId: Long, status: BatchStatus, deleted: Boolean = false) {
        inventory.productBatches.value = listOf(
            InventoryItem(
                batch = InventoryBatch(
                    id = 10,
                    productId = productId,
                    storageLocationId = 1,
                    quantity = 1.0,
                    initialQuantity = 1.0,
                    status = status,
                    deletedAt = if (deleted) java.time.Instant.EPOCH.plusSeconds(1) else null,
                ),
                product = Product(id = productId, name = "Молоко"),
                location = location,
            ),
        )
    }

    @Test
    fun `a card nothing is left of is struck off`() = runTest {
        val result = deleteProduct(1)

        assertEquals(DeleteProductUseCase.Result.Deleted, result)
        assertNotNull(products.getById(1)?.deletedAt)
    }

    /**
     * The refusal that protects real food: the packages are in the fridge, and dropping
     * the card would take them out of the stock list without anyone eating them.
     */
    @Test
    fun `a card with packages at home is refused, and says how many`() = runTest {
        stock(productId = 1, status = BatchStatus.ACTIVE)

        val result = deleteProduct(1)

        assertEquals(DeleteProductUseCase.Result.StillInStock(1), result)
        assertNull("nothing may be marked when the answer was no", products.getById(1)?.deletedAt)
    }

    /** Written off or thrown away is not "at home": those cards are exactly the clutter. */
    @Test
    fun `packages already used up do not hold the card back`() = runTest {
        stock(productId = 1, status = BatchStatus.CONSUMED)

        assertEquals(DeleteProductUseCase.Result.Deleted, deleteProduct(1))
    }

    @Test
    fun `a card that is not there is reported rather than invented`() = runTest {
        assertEquals(DeleteProductUseCase.Result.NotFound, deleteProduct(404))
    }

    @Test
    fun `bringing a card back clears the mark`() = runTest {
        deleteProduct(1)

        deleteProduct.restore(1)

        assertNull(products.getById(1)?.deletedAt)
    }

    /** The point of it all: a struck-off card stops being offered. */
    @Test
    fun `a struck-off card leaves the catalogue but keeps its history`() = runTest {
        deleteProduct(1)

        val catalogue = products.observeActive().first()
        assertEquals(listOf("Хлеб"), catalogue.map { it.name })
        assertTrue(
            "the card itself must survive, or history rows lose their name",
            products.observeAll().first().any { it.id == 1L },
        )
    }
}
