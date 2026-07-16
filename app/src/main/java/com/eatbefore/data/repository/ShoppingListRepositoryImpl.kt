package com.eatbefore.data.repository

import com.eatbefore.core.database.dao.ShoppingListDao
import com.eatbefore.data.mapper.toDomain
import com.eatbefore.data.mapper.toEntity
import com.eatbefore.domain.model.ShoppingListItem
import com.eatbefore.domain.repository.ShoppingListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ShoppingListRepositoryImpl @Inject constructor(private val dao: ShoppingListDao) : ShoppingListRepository {

    override fun observeAll(): Flow<List<ShoppingListItem>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeOpenCount(): Flow<Int> = dao.observeOpenCount()

    override suspend fun getById(id: Long): ShoppingListItem? = dao.getById(id)?.toDomain()

    override suspend fun findOpenForProduct(productId: Long): ShoppingListItem? =
        dao.findOpenForProduct(productId)?.toDomain()

    override suspend fun upsert(item: ShoppingListItem): Long = if (item.id == 0L) {
        dao.insert(item.toEntity())
    } else {
        dao.update(item.toEntity())
        item.id
    }

    override suspend fun delete(item: ShoppingListItem) = dao.delete(item.toEntity())
}
