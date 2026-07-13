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
}
