package com.eatbefore.domain.repository

import com.eatbefore.domain.model.ShoppingListItem
import kotlinx.coroutines.flow.Flow

interface ShoppingListRepository {
    fun observeAll(): Flow<List<ShoppingListItem>>
    fun observeOpenCount(): Flow<Int>
    suspend fun getById(id: Long): ShoppingListItem?

    /** An open (not yet bought) entry for this product, if any. */
    suspend fun findOpenForProduct(productId: Long): ShoppingListItem?
    suspend fun upsert(item: ShoppingListItem): Long
    suspend fun delete(item: ShoppingListItem)
}
