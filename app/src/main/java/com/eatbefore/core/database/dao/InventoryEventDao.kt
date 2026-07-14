package com.eatbefore.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.eatbefore.core.database.entity.InventoryEventEntity
import kotlinx.coroutines.flow.Flow

/** History is append-only; there is intentionally no update/delete here. */
@Dao
interface InventoryEventDao {

    @Insert
    suspend fun insert(event: InventoryEventEntity): Long

    @Query("SELECT * FROM inventory_events ORDER BY created_at DESC, id DESC")
    fun observeAll(): Flow<List<InventoryEventEntity>>

    @Query("SELECT * FROM inventory_events WHERE product_id = :productId ORDER BY created_at DESC, id DESC")
    fun observeForProduct(productId: Long): Flow<List<InventoryEventEntity>>

    @Query("SELECT * FROM inventory_events WHERE inventory_batch_id = :batchId ORDER BY created_at DESC, id DESC")
    fun observeForBatch(batchId: Long): Flow<List<InventoryEventEntity>>

    @Query("SELECT * FROM inventory_events WHERE event_type = :eventType ORDER BY created_at DESC, id DESC")
    fun observeByType(eventType: String): Flow<List<InventoryEventEntity>>

    /** Most recent event overall — backs the global "undo last action" affordance. */
    @Query("SELECT * FROM inventory_events ORDER BY created_at DESC, id DESC LIMIT 1")
    suspend fun getLast(): InventoryEventEntity?

    // Backup/export support (bulk restore only; history stays append-only otherwise).
    @Query("SELECT * FROM inventory_events")
    suspend fun getAll(): List<InventoryEventEntity>

    @Insert
    suspend fun insertAll(items: List<InventoryEventEntity>)

    @Query("DELETE FROM inventory_events")
    suspend fun deleteAll()
}
