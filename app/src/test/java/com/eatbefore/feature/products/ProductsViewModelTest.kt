package com.eatbefore.feature.products

import app.cash.turbine.test
import com.eatbefore.R
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.usecase.DeleteProductUseCase
import com.eatbefore.testutil.FakeInventoryRepository
import com.eatbefore.testutil.FakeProductRepository
import com.eatbefore.testutil.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The catalogue screen. What it owes the user is a truthful list and a delete button that
 * is only offered when deleting is actually allowed — the refusal has to be visible before
 * the tap, not explained after it.
 */
class ProductsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val products = FakeProductRepository(
        mutableMapOf(
            1L to Product(id = 1, name = "Молоко", brand = "Простоквашино"),
            2L to Product(id = 2, name = "Хлеб", barcode = "4620017700531"),
        ),
    )
    private val inventory = FakeInventoryRepository()

    private fun viewModel() = ProductsViewModel(
        productRepository = products,
        deleteProduct = DeleteProductUseCase(products, inventory),
    )

    @Test
    fun `the catalogue lists what can still be chosen`() = runTest {
        viewModel().uiState.test {
            val state = awaitItemWhere { !it.isLoading }
            assertEquals(listOf("Молоко", "Хлеб"), state.rows.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search matches the name, the brand and the barcode alike`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItemWhere { it.rows.size == 2 }

            vm.setQuery("простоквашино")
            assertEquals(listOf("Молоко"), awaitItemWhere { it.rows.size == 1 }.rows.map { it.name })

            vm.setQuery("46200177")
            assertEquals(listOf("Хлеб"), awaitItemWhere { it.rows.singleOrNull()?.name == "Хлеб" }.rows.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Nothing found is not the same as nothing there, and the screen says so differently. */
    @Test
    fun `an empty catalogue and an empty search read differently`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItemWhere { it.rows.size == 2 }

            vm.setQuery("зубная паста")

            val filtered = awaitItemWhere { it.rows.isEmpty() }
            assertFalse("the catalogue itself is not empty", filtered.isEmpty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the count of packages at home reaches the row`() = runTest {
        products.presentCounts.value = mapOf(1L to 3)

        viewModel().uiState.test {
            val row = awaitItemWhere { it.rows.isNotEmpty() }.rows.first { it.id == 1L }
            assertEquals(3, row.presentBatches)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The button is disabled rather than the tap being refused after the fact. */
    @Test
    fun `deleting is not offered while packages are at home`() = runTest {
        val row = ProductRowUi(id = 1, name = "Молоко", brand = null, barcode = null, presentBatches = 2)

        assertFalse(viewModel().canDelete(row))
        assertTrue(viewModel().canDelete(row.copy(presentBatches = 0)))
    }

    @Test
    fun `a struck-off card leaves the list and can be brought back`() = runTest {
        val vm = viewModel()
        val row = ProductRowUi(id = 1, name = "Молоко", brand = null, barcode = null, presentBatches = 0)

        vm.uiState.test {
            awaitItemWhere { it.rows.size == 2 }

            vm.delete(row)

            // The list is what the user is looking at, so that is where the deletion has
            // to show — the message alone would be a claim without a consequence.
            assertEquals(listOf("Хлеб"), awaitItemWhere { it.rows.size == 1 }.rows.map { it.name })
            assertEquals(R.string.products_deleted, vm.message.value?.textRes)
            assertEquals("undo has to be offered", 1L, vm.message.value?.undoProductId)

            vm.undoDelete(1)

            assertEquals(listOf("Молоко", "Хлеб"), awaitItemWhere { it.rows.size == 2 }.rows.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(vm.message.value)
        assertNull(products.getById(1)?.deletedAt)
    }

    @Test
    fun `a refusal is reported with the number of packages`() = runTest {
        products.presentCounts.value = mapOf(1L to 2)
        inventory.productBatches.value = List(2) { index ->
            com.eatbefore.domain.model.InventoryItem(
                batch = com.eatbefore.domain.model.InventoryBatch(
                    id = index + 1L,
                    productId = 1,
                    storageLocationId = 1,
                    quantity = 1.0,
                    initialQuantity = 1.0,
                ),
                product = Product(id = 1, name = "Молоко"),
                location = com.eatbefore.domain.model.StorageLocation(id = 1, name = "Холодильник"),
            )
        }
        val vm = viewModel()

        vm.delete(ProductRowUi(id = 1, name = "Молоко", brand = null, barcode = null, presentBatches = 2))
        advanceUntilIdle()

        assertEquals(R.string.products_delete_in_stock, vm.message.value?.textRes)
        assertEquals(2, vm.message.value?.count)
        assertNull("a refusal is not undoable — nothing happened", vm.message.value?.undoProductId)
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<ProductsUiState>.awaitItemWhere(
        predicate: (ProductsUiState) -> Boolean,
    ): ProductsUiState {
        repeat(MAX_EMISSIONS) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
        error("No matching state within $MAX_EMISSIONS emissions")
    }

    private companion object {
        const val MAX_EMISSIONS = 20
    }
}
