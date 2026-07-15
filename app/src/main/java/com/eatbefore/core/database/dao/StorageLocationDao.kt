package com.eatbefore.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.eatbefore.core.database.entity.StorageLocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StorageLocationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(location: StorageLocationEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(locations: List<StorageLocationEntity>)

    @Update
    suspend fun update(location: StorageLocationEntity)

    @Query("SELECT * FROM storage_locations WHERE id = :id")
    suspend fun getById(id: Long): StorageLocationEntity?

    @Query("SELECT * FROM storage_locations WHERE is_archived = 0 ORDER BY sort_order ASC, name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<StorageLocationEntity>>

    @Query("SELECT * FROM storage_locations ORDER BY sort_order ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<StorageLocationEntity>>

    @Query("SELECT * FROM storage_locations WHERE is_default = 1 ORDER BY sort_order ASC LIMIT 1")
    suspend fun getDefault(): StorageLocationEntity?

    /** Marks exactly one location as the default target for quick adds. */
    @Query("UPDATE storage_locations SET is_default = (id = :id)")
    suspend fun setDefault(id: Long)

    @Query("SELECT COUNT(*) FROM storage_locations")
    suspend fun count(): Int

    // Backup/export support.
    @Query("SELECT * FROM storage_locations")
    suspend fun getAll(): List<StorageLocationEntity>

    @Query("DELETE FROM storage_locations")
    suspend fun deleteAll()
}
