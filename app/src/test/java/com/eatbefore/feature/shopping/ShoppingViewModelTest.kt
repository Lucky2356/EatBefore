package com.eatbefore.feature.shopping

import app.cash.turbine.test
import com.eatbefore.R
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.ShoppingListItem
import com.eatbefore.domain.usecase.AddToShoppingListUseCase
import com.eatbefore.domain.usecase.DeleteShoppingItemUseCase
import com.eatbefore.domain.usecase.MoveShoppingItemToInventoryUseCase
import com.eatbefore.domain.usecase.ToggleShoppingItemUseCase
import com.eatbefore.testutil.FakeAppClock
import com.eatbefore.testutil.FakeHistoryRepository
import com.eatbefore.testutil.FakeInventoryRepository
import com.eatbefore.testutil.FakeProductRepository
import com.eatbefore.testutil.FakeShoppingListRepository
import com.eatbefore.testutil.MainDispatcherRule
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The shopping list is where the user moves fastest, and delete sits one finger-width from
 * "bought it". These tests are mostly about that: a wrong tap has to be recoverable.
 */
class ShoppingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeAppClock()
    private lateinit var shopping: FakeShoppingListRepository
    private lateinit var products: FakeProductRepository
    private lateinit var inventory: FakeInventoryRepository
    private lateinit var history: FakeHistoryRepository

    private val itemId = 1L

    @Before
    fun setUp() {
        products = FakeProductRepository(mutableMapOf(5L to Product(id = 5, name = "Молоко")))
        inventory = FakeInventoryRepository()
        history = FakeHistoryRepository(inventory)
        shopping = FakeShoppingListRepository(
            mutableMapOf(
                itemId to ShoppingListItem(
                    id = itemId,
                    customName = "Хлеб",
                    quantity = 2.0,
                    measurementUnit = MeasurementUnit.PIECE,
                    addedAt = clock.now(),
                ),
            ),
        )
    }

    private fun viewModel() = ShoppingViewModel(
        shoppingListRepository = shopping,
        productRepository = products,
        addToShoppingList = AddToShoppingListUseCase(shopping, history, clock),
        toggleItem = ToggleShoppingItemUseCase(shopping, clock),
        deleteItem = DeleteShoppingItemUseCase(shopping),
        moveToInventory = mockk<MoveShoppingItemToInventoryUseCase>(relaxed = true),
    )

    private fun advance() = mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

    @Test
    fun `deleting removes the row`() = runTest {
        val vm = viewModel()

        vm.delete(itemId)
        advance()

        assertTrue(shopping.items.isEmpty())
    }

    @Test
    fun `deleting offers to undo`() = runTest {
        val vm = viewModel()

        vm.message.test {
            assertNull(awaitItem())
            vm.delete(itemId)
            advance()

            val message = awaitItem()
            assertNotNull(message)
            assertEquals(R.string.shopping_deleted, message!!.textRes)
            assertTrue(message.undoable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Not "an item like it" — the same row, with the amount the user typed. */
    @Test
    fun `undo puts the deleted row back as it was`() = runTest {
        val vm = viewModel()
        val original = shopping.items.getValue(itemId)

        vm.delete(itemId)
        advance()
        vm.undoDelete()
        advance()

        assertEquals(original, shopping.items[itemId])
    }

    /** Undo must not resurrect the same row twice if the button is somehow hit again. */
    @Test
    fun `undo works only once`() = runTest {
        val vm = viewModel()

        vm.delete(itemId)
        advance()
        vm.undoDelete()
        advance()
        shopping.items.clear()
        vm.undoDelete()
        advance()

        assertTrue(shopping.items.isEmpty())
    }

    @Test
    fun `clearing bought items leaves what is still to buy`() = runTest {
        shopping.items[2L] = ShoppingListItem(
            id = 2L,
            customName = "Сыр",
            isCompleted = true,
            addedAt = clock.now(),
        )
        val vm = viewModel()

        vm.clearCompleted()
        advance()

        assertEquals(listOf("Хлеб"), shopping.items.values.map { it.customName })
    }

    /** Undoing half a bulk removal would be worse than not offering undo at all. */
    @Test
    fun `undo restores every row that clearing removed`() = runTest {
        shopping.items[2L] = ShoppingListItem(id = 2L, customName = "Сыр", isCompleted = true, addedAt = clock.now())
        shopping.items[3L] = ShoppingListItem(id = 3L, customName = "Соль", isCompleted = true, addedAt = clock.now())
        val vm = viewModel()

        vm.clearCompleted()
        advance()
        vm.undoDelete()
        advance()

        assertEquals(3, shopping.items.size)
    }

    @Test
    fun `editing a free-typed row changes its name, amount and unit`() = runTest {
        val vm = viewModel()

        vm.updateItem(itemId, name = "Батон", quantity = 3.0, unit = MeasurementUnit.KILOGRAM)
        advance()

        val edited = shopping.items.getValue(itemId)
        assertEquals("Батон", edited.customName)
        assertEquals(3.0, edited.quantity, 0.0)
        assertEquals(MeasurementUnit.KILOGRAM, edited.measurementUnit)
    }

    /**
     * A row backed by a product shows that product's name. Renaming it here would rename
     * the product in the inventory and in every past purchase along with it.
     */
    @Test
    fun `editing a product-backed row leaves the product name alone`() = runTest {
        shopping.items[2L] = ShoppingListItem(id = 2L, productId = 5L, quantity = 1.0, addedAt = clock.now())
        val vm = viewModel()

        vm.updateItem(2L, name = "Что-то другое", quantity = 2.0, unit = MeasurementUnit.LITER)
        advance()

        val edited = shopping.items.getValue(2L)
        assertNull(edited.customName)
        assertEquals(2.0, edited.quantity, 0.0)
    }

    /** What gets sent to whoever is going to the shop. */
    @Test
    fun `the shared text lists what is left to buy, with amounts`() = runTest {
        shopping.items[2L] = ShoppingListItem(
            id = 2L,
            customName = "Картошка",
            quantity = 2.0,
            measurementUnit = MeasurementUnit.KILOGRAM,
            addedAt = clock.now(),
        )
        val vm = viewModel()
        vm.uiState.test {
            awaitItemWhere { !it.isLoading }

            val text = vm.buildShareText("Купить:") { unit -> unit.name.lowercase() }

            assertEquals(
                listOf("Купить:", "— Картошка, 2 kilogram", "— Хлеб, 2 piece"),
                text.lines(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Already bought is not "to buy" — sending it would send the wrong errand. */
    @Test
    fun `bought items are left out of the shared text`() = runTest {
        shopping.items[2L] = ShoppingListItem(
            id = 2L,
            customName = "Сыр",
            isCompleted = true,
            addedAt = clock.now(),
        )
        val vm = viewModel()
        vm.uiState.test {
            awaitItemWhere { !it.isLoading }

            val text = vm.buildShareText("Купить:") { "шт" }

            assertFalse(text.contains("Сыр"))
            assertTrue(text.contains("Хлеб"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `adding a regular purchase reports it without an undo`() = runTest {
        val vm = viewModel()

        vm.message.test {
            assertNull(awaitItem())
            vm.addFrequent(productId = 5L)
            advance()

            val message = awaitItem()
            assertEquals(R.string.shopping_added, message!!.textRes)
            assertFalse(message.undoable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Two deletions in a row are two separate messages, not one silently swallowed. */
    @Test
    fun `each message is distinct even when the text repeats`() = runTest {
        shopping.items[2L] = ShoppingListItem(id = 2L, customName = "Сыр", addedAt = clock.now())
        val vm = viewModel()

        vm.delete(itemId)
        advance()
        val first = vm.message.value!!.id
        vm.consumeMessage()
        vm.delete(2L)
        advance()

        assertEquals(first + 1, vm.message.value!!.id)
    }
}

/** State flows conflate and replay, so filter for the interesting item rather than index. */
private suspend fun <T> app.cash.turbine.ReceiveTurbine<T>.awaitItemWhere(predicate: (T) -> Boolean): T {
    repeat(20) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
    error("No matching item")
}
