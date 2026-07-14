package com.eatbefore.data.repository

import com.eatbefore.core.database.dao.InventoryEventDao
import com.eatbefore.data.mapper.toDomain
import com.eatbefore.data.mapper.toEntity
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class HistoryRepositoryImpl @Inject constructor(
    private val eventDao: InventoryEventDao,
) : HistoryRepository {

    override fun observeAll(): Flow<List<InventoryEvent>> =
        eventDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeForProduct(productId: Long): Flow<List<InventoryEvent>> =
        eventDao.observeForProduct(productId).map { list -> list.map { it.toDomain() } }

    override fun observeByType(type: EventType): Flow<List<InventoryEvent>> =
        eventDao.observeByType(type.name).map { list -> list.map { it.toDomain() } }

    override suspend fun getLastEvent(): InventoryEvent? = eventDao.getLast()?.toDomain()

    override suspend fun record(event: InventoryEvent) {
        eventDao.insert(event.toEntity())
    }
}
