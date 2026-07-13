package com.eatbefore.domain.repository

import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryEvent
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun observeAll(): Flow<List<InventoryEvent>>
    fun observeForProduct(productId: Long): Flow<List<InventoryEvent>>
    fun observeByType(type: EventType): Flow<List<InventoryEvent>>
    suspend fun getLastEvent(): InventoryEvent?
}
