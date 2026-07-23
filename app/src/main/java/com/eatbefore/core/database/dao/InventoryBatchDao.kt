package com.eatbefore.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.eatbefore.core.database.entity.InventoryBatchEntity
import com.eatbefore.core.database.relation.BatchWithProductAndLocation
import kotlinx.coroutines.flow.Flow

/**
 * Access to inventory batches. "Present" means physically at home: not soft-deleted and
 * in a present status (ACTIVE/OPENED/PARTIALLY_USED). Terminal/archived batches are kept
 * for history but excluded from stock views.
 */
@Dao
interface InventoryBatchDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(batch: InventoryBatchEntity): Long

    @Update
    suspend fun update(batch: InventoryBatchEntity)

    @Query("SELECT * FROM inventory_batches WHERE id = :id")
    suspend fun getById(id: Long): InventoryBatchEntity?

    /** Cross-device identity; see ADR-0004. */
    @Query("SELECT * FROM inventory_batches WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): InventoryBatchEntity?

    @Transaction
    @Query("SELECT * FROM inventory_batches WHERE id = :id")
    fun observeWithProduct(id: Long): Flow<BatchWithProductAndLocation?>

    @Transaction
    @Query(
        """
        SELECT * FROM inventory_batches
        WHERE status IN ('ACTIVE','OPENED','PARTIALLY_USED') AND deleted_at IS NULL
        ORDER BY (expiration_date IS NULL), expiration_date ASC, added_at DESC
        """,
    )
    fun observePresentByExpiry(): Flow<List<BatchWithProductAndLocation>>

    @Transaction
    @Query(
        """
        SELECT * FROM inventory_batches
        WHERE status IN ('ACTIVE','OPENED','PARTIALLY_USED') AND deleted_at IS NULL
          AND storage_location_id = :locationId
        ORDER BY (expiration_date IS NULL), expiration_date ASC, added_at DESC
        """,
    )
    fun observePresentByLocation(locationId: Long): Flow<List<BatchWithProductAndLocation>>

    /** Present batches with an expiration on/before [thresholdEpochDay]. */
    @Transaction
    @Query(
        """
        SELECT * FROM inventory_batches
        WHERE status IN ('ACTIVE','OPENED','PARTIALLY_USED') AND deleted_at IS NULL
          AND expiration_date IS NOT NULL AND expiration_date <= :thresholdEpochDay
        ORDER BY expiration_date ASC
        """,
    )
    fun observeExpiringBefore(thresholdEpochDay: Long): Flow<List<BatchWithProductAndLocation>>

    @Transaction
    @Query("SELECT * FROM inventory_batches WHERE product_id = :productId ORDER BY added_at DESC")
    fun observeAllForProduct(productId: Long): Flow<List<BatchWithProductAndLocation>>

    @Transaction
    @Query(
        """
        SELECT * FROM inventory_batches
        WHERE status IN ('ACTIVE','OPENED','PARTIALLY_USED') AND deleted_at IS NULL
        ORDER BY added_at DESC LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<BatchWithProductAndLocation>>

    @Query(
        """
        SELECT COUNT(*) FROM inventory_batches
        WHERE status IN ('ACTIVE','OPENED','PARTIALLY_USED') AND deleted_at IS NULL
        """,
    )
    fun observePresentCount(): Flow<Int>

    /** All present batches for the given product, used when merging duplicates. */
    @Query(
        """
        SELECT * FROM inventory_batches
        WHERE product_id = :productId
          AND status IN ('ACTIVE','OPENED','PARTIALLY_USED') AND deleted_at IS NULL
        """,
    )
    suspend fun getPresentForProduct(productId: Long): List<InventoryBatchEntity>

    // Backup/export support.
    @Query("SELECT * FROM inventory_batches")
    suspend fun getAll(): List<InventoryBatchEntity>

    @Insert
    suspend fun insertAll(items: List<InventoryBatchEntity>)

    @Query("DELETE FROM inventory_batches")
    suspend fun deleteAll()
}
