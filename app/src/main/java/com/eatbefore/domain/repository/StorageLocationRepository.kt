package com.eatbefore.domain.repository

import com.eatbefore.domain.model.StorageLocation
import kotlinx.coroutines.flow.Flow

interface StorageLocationRepository {
    fun observeActive(): Flow<List<StorageLocation>>
    fun observeAll(): Flow<List<StorageLocation>>
    suspend fun getById(id: Long): StorageLocation?
    suspend fun getDefault(): StorageLocation?
    suspend fun setDefault(id: Long)
    suspend fun upsert(location: StorageLocation): Long
}
