package com.eatbefore.data.repository

import androidx.room.withTransaction
import com.eatbefore.core.database.EatBeforeDatabase
import com.eatbefore.core.database.dao.InventoryBatchDao
import com.eatbefore.core.database.dao.InventoryEventDao
import com.eatbefore.core.database.relation.BatchWithProductAndLocation
import com.eatbefore.core.widget.StockChangeNotifier
import com.eatbefore.data.mapper.toDomain
import com.eatbefore.data.mapper.toEntity
import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.model.InventoryItem
import com.eatbefore.domain.repository.InventoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class InventoryRepositoryImpl @Inject constructor(
    private val db: EatBeforeDatabase,
    private val batchDao: InventoryBatchDao,
    private val eventDao: InventoryEventDao,
    private val stockChangeNotifier: StockChangeNotifier,
) : InventoryRepository {

    private fun BatchWithProductAndLocation.toItem(): InventoryItem = InventoryItem(
        batch = batch.toDomain(),
        product = product.toDomain(),
        location = location.toDomain(),
    )

    override fun observePresentByExpiry(): Flow<List<InventoryItem>> =
        batchDao.observePresentByExpiry().map { list -> list.map { it.toItem() } }

    override fun observePresentByLocation(locationId: Long): Flow<List<InventoryItem>> =
        batchDao.observePresentByLocation(locationId).map { list -> list.map { it.toItem() } }

    override fun observeExpiringBefore(thresholdEpochDay: Long): Flow<List<InventoryItem>> =
        batchDao.observeExpiringBefore(thresholdEpochDay).map { list -> list.map { it.toItem() } }

    override fun observeAllForProduct(productId: Long): Flow<List<InventoryItem>> =
        batchDao.observeAllForProduct(productId).map { list -> list.map { it.toItem() } }

    override fun observeRecent(limit: Int): Flow<List<InventoryItem>> =
        batchDao.observeRecent(limit).map { list -> list.map { it.toItem() } }

    override fun observePresentCount(): Flow<Int> = batchDao.observePresentCount()

    override fun observeItem(batchId: Long): Flow<InventoryItem?> =
        batchDao.observeWithProduct(batchId).map { it?.toItem() }

    override suspend fun getBatch(id: Long): InventoryBatch? = batchDao.getById(id)?.toDomain()

    override suspend fun getPresentForProduct(productId: Long): List<InventoryBatch> =
        batchDao.getPresentForProduct(productId).map { it.toDomain() }

    override suspend fun addBatchWithEvent(
        batch: InventoryBatch,
        buildEvent: (batchId: Long) -> InventoryEvent,
    ): Long {
        val newId = db.withTransaction {
            val id = batchDao.insert(batch.toEntity())
            eventDao.insert(buildEvent(id).toEntity())
            id
        }
        // After the transaction, not inside it: the widget reads the database, and reading
        // it from within the write would either see the old state or deadlock.
        stockChangeNotifier.onStockChanged()
        return newId
    }

    override suspend fun updateBatchWithEvent(batch: InventoryBatch, event: InventoryEvent) {
        db.withTransaction {
            batchDao.update(batch.toEntity())
            eventDao.insert(event.toEntity())
        }
        stockChangeNotifier.onStockChanged()
    }
}
