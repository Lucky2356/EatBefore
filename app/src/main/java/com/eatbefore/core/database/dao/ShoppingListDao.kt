package com.eatbefore.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.eatbefore.core.database.entity.ShoppingListItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingListDao {

    @Insert
    suspend fun insert(item: ShoppingListItemEntity): Long

    @Update
    suspend fun update(item: ShoppingListItemEntity)

    @Delete
    suspend fun delete(item: ShoppingListItemEntity)

    @Query("SELECT * FROM shopping_list_items WHERE id = :id")
    suspend fun getById(id: Long): ShoppingListItemEntity?

    /** An open (not yet bought) entry for this product, used to merge instead of duplicate. */
    @Query(
        "SELECT * FROM shopping_list_items " +
            "WHERE product_id = :productId AND is_completed = 0 LIMIT 1",
    )
    suspend fun findOpenForProduct(productId: Long): ShoppingListItemEntity?

    @Query("SELECT * FROM shopping_list_items ORDER BY is_completed ASC, added_at DESC")
    fun observeAll(): Flow<List<ShoppingListItemEntity>>

    @Query("SELECT COUNT(*) FROM shopping_list_items WHERE is_completed = 0")
    fun observeOpenCount(): Flow<Int>
}
