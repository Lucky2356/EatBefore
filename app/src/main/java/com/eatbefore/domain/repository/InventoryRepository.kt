package com.eatbefore.domain.repository

import com.eatbefore.domain.model.BatchPrice
import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.model.InventoryItem
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for stock batches. Mutations are paired with a history [InventoryEvent] and
 * applied atomically so the audit trail can never diverge from the data (prompt: "all
 * stock-changing operations create history records").
 */
interface InventoryRepository {

    fun observePresentByExpiry(): Flow<List<InventoryItem>>
    fun observePresentByLocation(locationId: Long): Flow<List<InventoryItem>>
    fun observeExpiringBefore(thresholdEpochDay: Long): Flow<List<InventoryItem>>
    fun observeAllForProduct(productId: Long): Flow<List<InventoryItem>>
    fun observeRecent(limit: Int): Flow<List<InventoryItem>>
    fun observePresentCount(): Flow<Int>

    /** What each batch cost, for batches that have a price at all. Keyed by batch id. */
    fun observePrices(): Flow<Map<Long, BatchPrice>>
    fun observeItem(batchId: Long): Flow<InventoryItem?>

    suspend fun getBatch(id: Long): InventoryBatch?
    suspend fun getPresentForProduct(productId: Long): List<InventoryBatch>

    /**
     * Inserts [batch] and, in the same transaction, the event produced by [buildEvent]
     * (which receives the generated batch id). Returns the new batch id.
     */
    suspend fun addBatchWithEvent(
        batch: InventoryBatch,
        buildEvent: (batchId: Long) -> InventoryEvent,
    ): Long

    /** Updates [batch] and appends [event] atomically. */
    suspend fun updateBatchWithEvent(batch: InventoryBatch, event: InventoryEvent)
}
