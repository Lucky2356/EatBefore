package com.eatbefore.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.eatbefore.core.database.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(product: ProductEntity): Long

    @Update
    suspend fun update(product: ProductEntity)

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id")
    fun observeById(id: Long): Flow<ProductEntity?>

    /** Cross-device identity; see ADR-0004. */
    @Query("SELECT * FROM products WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): ProductEntity?

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): ProductEntity?

    /** Case-insensitive lookup used when merging duplicate manual entries. */
    @Query(
        "SELECT * FROM products WHERE barcode IS NULL " +
            "AND LOWER(name) = LOWER(:name) " +
            "AND (:brand IS NULL AND brand IS NULL OR LOWER(brand) = LOWER(:brand)) LIMIT 1",
    )
    suspend fun findUserProductByNameAndBrand(name: String, brand: String?): ProductEntity?

    /**
     * Every card, struck-off ones included. Screens that only need a name to show against
     * history — the reports, the shopping list — read this: a deleted card still owns
     * everything ever bought under it, and hiding it here would blank those rows out.
     */
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ProductEntity>>

    /** The catalogue as the user sees it: what can still be chosen and bought again. */
    @Query("SELECT * FROM products WHERE deleted_at IS NULL ORDER BY name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<ProductEntity>>

    /**
     * Products the household buys most often, by how many times they were added.
     * Backs one-tap repeat purchases, so it counts history rather than current stock.
     */
    @Query(
        "SELECT p.* FROM products p " +
            "JOIN inventory_events e ON e.product_id = p.id AND e.event_type = 'ADDED' " +
            "WHERE p.deleted_at IS NULL " +
            "GROUP BY p.id HAVING COUNT(e.id) >= :minTimes " +
            "ORDER BY COUNT(e.id) DESC, MAX(e.created_at) DESC LIMIT :limit",
    )
    fun observeFrequent(limit: Int, minTimes: Int): Flow<List<ProductEntity>>

    /**
     * How many packages of each product are at home right now, so the catalogue can say
     * what is still in use — and refuse to strike off something sitting in the fridge.
     */
    @Query(
        "SELECT product_id AS productId, COUNT(*) AS count FROM inventory_batches " +
            "WHERE status IN ('ACTIVE','OPENED','PARTIALLY_USED') AND deleted_at IS NULL " +
            "GROUP BY product_id",
    )
    fun observePresentCounts(): Flow<List<ProductBatchCount>>

    /**
     * Strikes the card off, or brings it back with [at] = null.
     *
     * `updated_at` moves with it: the exchange settles disagreements by which side wrote
     * last, so a deletion that left the timestamp alone would be quietly overruled by the
     * peer's older copy of the same card.
     */
    @Query("UPDATE products SET deleted_at = :at, updated_at = :now WHERE id = :id")
    suspend fun setDeletedAt(id: Long, at: Long?, now: Long)

    // Backup/export support.
    @Query("SELECT * FROM products")
    suspend fun getAll(): List<ProductEntity>

    @Insert
    suspend fun insertAll(items: List<ProductEntity>)

    @Query("DELETE FROM products")
    suspend fun deleteAll()
}

/** How many packages of one product are at home; see [ProductDao.observePresentCounts]. */
data class ProductBatchCount(val productId: Long, val count: Int)
