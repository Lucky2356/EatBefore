package com.eatbefore.domain.usecase

import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.model.StorageType
import com.eatbefore.domain.repository.StorageLocationRepository
import com.eatbefore.testutil.FakeAppClock
import com.eatbefore.testutil.FakeHistoryRepository
import com.eatbefore.testutil.FakeInventoryRepository
import com.eatbefore.testutil.FakeProductRepository
import com.eatbefore.testutil.FakeShoppingListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ShoppingListUseCasesTest {

    private val clock = FakeAppClock()
    private lateinit var shopping: FakeShoppingListRepository
    private lateinit var products: FakeProductRepository
    private lateinit var inventory: FakeInventoryRepository
    private lateinit var history: FakeHistoryRepository

    private lateinit var addToList: AddToShoppingListUseCase
    private lateinit var toggle: ToggleShoppingItemUseCase
    private lateinit var delete: DeleteShoppingItemUseCase
    private lateinit var moveToStock: MoveShoppingItemToInventoryUseCase

    /** Minimal location repo: a single default "Fridge" with id 1. */
    private class FakeLocations : StorageLocationRepository {
        private val fridge = StorageLocation(id = 1, name = "Fridge", type = StorageType.FRIDGE, isDefault = true)
        override fun observeActive(): Flow<List<StorageLocation>> = flowOf(listOf(fridge))
        override fun observeAll(): Flow<List<StorageLocation>> = flowOf(listOf(fridge))
        override suspend fun getById(id: Long): StorageLocation? = fridge.takeIf { it.id == id }
        override suspend fun getDefault(): StorageLocation = fridge
        override suspend fun upsert(location: StorageLocation): Long = location.id
    }

    @Before
    fun setUp() {
        shopping = FakeShoppingListRepository()
        products = FakeProductRepository()
        inventory = FakeInventoryRepository()
        history = FakeHistoryRepository(inventory)
        val locations = FakeLocations()

        addToList = AddToShoppingListUseCase(shopping, history, clock)
        toggle = ToggleShoppingItemUseCase(shopping, clock)
        delete = DeleteShoppingItemUseCase(shopping)
        moveToStock = MoveShoppingItemToInventoryUseCase(
            shopping,
            locations,
            products,
            AddBatchUseCase(products, inventory, clock),
            AddManualProductUseCase(products, inventory, MergeSameProductUseCase(products), clock),
        )
    }

    @Test
    fun addsCustomItem() = runTest {
        val id = addToList(AddToShoppingListUseCase.Params(customName = "Хлеб", quantity = 2.0))
        val item = shopping.getById(id)!!
        assertEquals("Хлеб", item.customName)
        assertEquals(2.0, item.quantity, 0.0)
        assertFalse(item.isCompleted)
    }

    @Test
    fun addingSameProductTwice_mergesQuantityInsteadOfDuplicating() = runTest {
        val productId = products.upsert(Product(name = "Milk"))
        addToList(AddToShoppingListUseCase.Params(productId = productId, quantity = 1.0))
        addToList(AddToShoppingListUseCase.Params(productId = productId, quantity = 2.0))

        assertEquals(1, shopping.items.size)
        assertEquals(3.0, shopping.items.values.first().quantity, 0.0)
    }

    @Test
    fun addingFromWrittenOffBatch_recordsHistoryEvent() = runTest {
        val productId = products.upsert(Product(name = "Milk"))
        addToList(
            AddToShoppingListUseCase.Params(
                productId = productId,
                sourceInventoryBatchId = 42,
            ),
        )
        assertEquals(EventType.ADDED_TO_SHOPPING_LIST, inventory.lastEvent().eventType)
    }

    @Test
    fun toggle_marksBoughtAndBack() = runTest {
        val id = addToList(AddToShoppingListUseCase.Params(customName = "Соль"))
        toggle(id)
        assertTrue(shopping.getById(id)!!.isCompleted)
        assertNotNull(shopping.getById(id)!!.completedAt)

        toggle(id)
        assertFalse(shopping.getById(id)!!.isCompleted)
        assertNull(shopping.getById(id)!!.completedAt)
    }

    @Test
    fun delete_removesItem() = runTest {
        val id = addToList(AddToShoppingListUseCase.Params(customName = "Сахар"))
        delete(id)
        assertNull(shopping.getById(id))
    }

    @Test
    fun moveToStock_linkedProduct_addsBatchAndRemovesFromList() = runTest {
        val productId = products.upsert(Product(name = "Milk", measurementUnit = MeasurementUnit.LITER))
        val id = addToList(
            AddToShoppingListUseCase.Params(
                productId = productId,
                quantity = 2.0,
                measurementUnit = MeasurementUnit.LITER,
            ),
        )

        val batchId = moveToStock(id)
        assertNotNull(batchId)
        val batch = inventory.getBatch(batchId!!)!!
        assertEquals(productId, batch.productId)
        assertEquals(2.0, batch.quantity, 0.0)
        // Bought items leave the list.
        assertNull(shopping.getById(id))
    }

    @Test
    fun moveToStock_customItem_createsProductCard() = runTest {
        val id = addToList(AddToShoppingListUseCase.Params(customName = "Гречка", quantity = 1.0))
        val batchId = moveToStock(id)
        assertNotNull(batchId)
        assertEquals(1, products.observeAllCount())
        assertNull(shopping.getById(id))
    }
}
