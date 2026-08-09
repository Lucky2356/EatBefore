package com.eatbefore.testutil

import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.model.InventoryItem
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.ShoppingListItem
import com.eatbefore.domain.repository.HistoryRepository
import com.eatbefore.domain.repository.InventoryRepository
import com.eatbefore.domain.repository.ProductRepository
import com.eatbefore.domain.repository.ShoppingListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * In-memory fakes for use-case tests. Only the methods exercised by tests hold real
 * behavior; observe* streams return static values since these tests assert on state and
 * recorded events, not on reactive emissions.
 */
class FakeProductRepository(private val products: MutableMap<Long, Product> = mutableMapOf()) : ProductRepository {
    private var nextId = 1L

    override suspend fun getById(id: Long): Product? = products[id]
    override fun observeById(id: Long): Flow<Product?> = flowOf(products[id])
    override suspend fun getByBarcode(barcode: String): Product? =
        products.values.firstOrNull { it.barcode == barcode }

    override suspend fun findUserProductByNameAndBrand(name: String, brand: String?): Product? =
        products.values.firstOrNull {
            it.isUserCreated &&
                it.barcode == null &&
                it.name.equals(name, ignoreCase = true) &&
                (it.brand?.equals(brand, ignoreCase = true) ?: (brand == null))
        }

    override suspend fun upsert(product: Product): Long {
        val id = if (product.id == 0L) nextId++ else product.id
        products[id] = product.copy(id = id)
        return id
    }

    override fun observeAll(): Flow<List<Product>> = flowOf(products.values.toList())

    override fun observeFrequent(limit: Int, minTimes: Int): Flow<List<Product>> =
        flowOf(products.values.take(limit))

    fun observeAllCount(): Int = products.size
}

class FakeInventoryRepository(
    val batches: MutableMap<Long, InventoryBatch> = mutableMapOf(),
    val events: MutableList<InventoryEvent> = mutableListOf(),
) : InventoryRepository {
    private var nextEventId = 1L

    /**
     * Stock as the list screens see it. StateFlows rather than one-shot flows: a screen
     * combining several streams only produces state once every one of them has emitted,
     * so an empty flow here would leave a ViewModel under test stuck on "loading".
     */
    val presentItems = MutableStateFlow<List<InventoryItem>>(emptyList())
    val expiringItems = MutableStateFlow<List<InventoryItem>>(emptyList())
    val recentItems = MutableStateFlow<List<InventoryItem>>(emptyList())

    override fun observePresentByExpiry(): Flow<List<InventoryItem>> = presentItems

    override fun observePresentByLocation(locationId: Long): Flow<List<InventoryItem>> =
        presentItems.map { items -> items.filter { it.location.id == locationId } }

    override fun observeExpiringBefore(thresholdEpochDay: Long): Flow<List<InventoryItem>> = expiringItems
    override fun observeAllForProduct(productId: Long): Flow<List<InventoryItem>> = emptyFlow()
    override fun observeRecent(limit: Int): Flow<List<InventoryItem>> = recentItems.map { it.take(limit) }
    override fun observePresentCount(): Flow<Int> = presentItems.map { it.size }

    /**
     * What the product screen is looking at. A StateFlow rather than a plain map: the
     * screen has to react to the item disappearing (the batch was used up, or an add was
     * undone), and a one-shot flow cannot express that.
     */
    val observedItems = MutableStateFlow<Map<Long, InventoryItem>>(emptyMap())

    override fun observeItem(batchId: Long): Flow<InventoryItem?> =
        observedItems.map { it[batchId] }

    fun setObservedItem(batchId: Long, item: InventoryItem?) {
        observedItems.value = observedItems.value.toMutableMap().apply {
            if (item == null) remove(batchId) else put(batchId, item)
        }
    }

    override suspend fun getBatch(id: Long): InventoryBatch? = batches[id]

    override suspend fun getPresentForProduct(productId: Long): List<InventoryBatch> =
        batches.values.filter {
            it.productId == productId && it.status.isPresent && it.deletedAt == null
        }

    override suspend fun addBatchWithEvent(
        batch: InventoryBatch,
        buildEvent: (batchId: Long) -> InventoryEvent,
    ): Long {
        // Derived from what is already there, not from a counter: a test that seeds a
        // batch by id would otherwise have its seed silently overwritten by the first add.
        val id = (batches.keys.maxOrNull() ?: 0L) + 1
        batches[id] = batch.copy(id = id)
        events += buildEvent(id).copy(id = nextEventId++)
        return id
    }

    override suspend fun updateBatchWithEvent(batch: InventoryBatch, event: InventoryEvent) {
        batches[batch.id] = batch
        events += event.copy(id = nextEventId++)
    }

    fun lastEvent(): InventoryEvent = events.last()
    fun eventTypes(): List<EventType> = events.map { it.eventType }
}

class FakeHistoryRepository(private val backing: FakeInventoryRepository) : HistoryRepository {
    override fun observeAll(): Flow<List<InventoryEvent>> = flowOf(backing.events.toList())

    // Newest-first page, mirroring the DAO ordering.
    override fun observeRecent(limit: Int, type: EventType?): Flow<List<InventoryEvent>> = flowOf(
        backing.events
            .filter { type == null || it.eventType == type }
            .asReversed()
            .take(limit),
    )

    override fun observeForProduct(productId: Long): Flow<List<InventoryEvent>> =
        flowOf(backing.events.filter { it.productId == productId })

    override fun observeByType(type: EventType): Flow<List<InventoryEvent>> =
        flowOf(backing.events.filter { it.eventType == type })

    // Mirrors the real DAO: undo's own compensating events are not undo targets.
    override suspend fun getLastEvent(): InventoryEvent? =
        backing.events.lastOrNull { it.reason?.startsWith("undo") != true }

    override suspend fun record(event: InventoryEvent) {
        backing.events += event
    }
}

class FakeShoppingListRepository(val items: MutableMap<Long, ShoppingListItem> = mutableMapOf()) : ShoppingListRepository {
    private var nextId = 1L

    override fun observeAll(): Flow<List<ShoppingListItem>> = flowOf(items.values.toList())

    override fun observeOpenCount(): Flow<Int> = flowOf(items.values.count { !it.isCompleted })

    override suspend fun getById(id: Long): ShoppingListItem? = items[id]

    override suspend fun findOpenForProduct(productId: Long): ShoppingListItem? =
        items.values.firstOrNull { it.productId == productId && !it.isCompleted }

    override suspend fun upsert(item: ShoppingListItem): Long {
        val id = if (item.id == 0L) nextId++ else item.id
        items[id] = item.copy(id = id)
        return id
    }

    override suspend fun delete(item: ShoppingListItem) {
        items.remove(item.id)
    }
}
