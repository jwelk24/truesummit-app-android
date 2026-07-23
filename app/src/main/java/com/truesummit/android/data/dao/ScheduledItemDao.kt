package com.truesummit.android.data.dao

import androidx.room.*
import com.truesummit.android.data.entity.ScheduledItemEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface ScheduledItemDao {
    @Query("SELECT * FROM scheduled_items ORDER BY nextDate ASC")
    fun getAll(): Flow<List<ScheduledItemEntity>>

    @Query("SELECT * FROM scheduled_items WHERE id = :id")
    suspend fun getById(id: UUID): ScheduledItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ScheduledItemEntity)

    @Update
    suspend fun update(item: ScheduledItemEntity)

    @Delete
    suspend fun delete(item: ScheduledItemEntity)
}
