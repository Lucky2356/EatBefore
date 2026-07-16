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

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): ProductEntity?

    /** Case-insensitive lookup used when merging duplicate manual entries. */
    @Query(
        "SELECT * FROM products WHERE barcode IS NULL " +
            "AND LOWER(name) = LOWER(:name) " +
            "AND (:brand IS NULL AND brand IS NULL OR LOWER(brand) = LOWER(:brand)) LIMIT 1",
    )
    suspend fun findUserProductByNameAndBrand(name: String, brand: String?): ProductEntity?

    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ProductEntity>>

    /**
     * Products the household buys most often, by how many times they were added.
     * Backs one-tap repeat purchases, so it counts history rather than current stock.
     */
    @Query(
        "SELECT p.* FROM products p " +
            "JOIN inventory_events e ON e.product_id = p.id AND e.event_type = 'ADDED' " +
            "GROUP BY p.id HAVING COUNT(e.id) >= :minTimes " +
            "ORDER BY COUNT(e.id) DESC, MAX(e.created_at) DESC LIMIT :limit",
    )
    fun observeFrequent(limit: Int, minTimes: Int): Flow<List<ProductEntity>>

    // Backup/export support.
    @Query("SELECT * FROM products")
    suspend fun getAll(): List<ProductEntity>

    @Insert
    suspend fun insertAll(items: List<ProductEntity>)

    @Query("DELETE FROM products")
    suspend fun deleteAll()
}
