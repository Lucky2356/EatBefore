package com.eatbefore.domain.repository

import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryEvent
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun observeAll(): Flow<List<InventoryEvent>>

    /**
     * Newest-first history capped at [limit], optionally filtered by [type]. The history
     * screen grows this limit as the user scrolls instead of loading the whole table.
     */
    fun observeRecent(limit: Int, type: EventType? = null): Flow<List<InventoryEvent>>

    fun observeForProduct(productId: Long): Flow<List<InventoryEvent>>
    fun observeByType(type: EventType): Flow<List<InventoryEvent>>
    suspend fun getLastEvent(): InventoryEvent?

    /**
     * Appends a standalone event that isn't tied to a batch mutation (e.g. moving a
     * product to the shopping list). Batch mutations must instead go through
     * [InventoryRepository], which writes the event in the same transaction.
     */
    suspend fun record(event: InventoryEvent)
}
