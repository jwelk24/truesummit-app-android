package com.truesummit.android.data.dao

import androidx.room.*
import com.truesummit.android.data.entity.SoftDeleteTombstoneEntity

@Dao
interface SoftDeleteTombstoneDao {
    @Query("SELECT * FROM soft_delete_tombstones")
    suspend fun getAll(): List<SoftDeleteTombstoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tombstone: SoftDeleteTombstoneEntity)

    @Delete
    suspend fun delete(tombstone: SoftDeleteTombstoneEntity)
}
