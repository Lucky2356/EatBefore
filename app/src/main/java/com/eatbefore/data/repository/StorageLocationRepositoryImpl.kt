package com.eatbefore.data.repository

import com.eatbefore.core.database.dao.StorageLocationDao
import com.eatbefore.data.mapper.toDomain
import com.eatbefore.data.mapper.toEntity
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.repository.StorageLocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class StorageLocationRepositoryImpl @Inject constructor(private val dao: StorageLocationDao) : StorageLocationRepository {

    override fun observeActive(): Flow<List<StorageLocation>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<StorageLocation>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): StorageLocation? = dao.getById(id)?.toDomain()

    override suspend fun getDefault(): StorageLocation? = dao.getDefault()?.toDomain()

    override suspend fun setDefault(id: Long) = dao.setDefault(id)

    override suspend fun upsert(location: StorageLocation): Long = if (location.id == 0L) {
        dao.insert(location.toEntity())
    } else {
        dao.update(location.toEntity())
        location.id
    }
}
