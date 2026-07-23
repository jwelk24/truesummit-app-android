package com.truesummit.android.data.dao

import androidx.room.*
import com.truesummit.android.data.entity.GoalEntity
import kotlinx.coroutines.flow.Flow
import java.util.*

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE categoryId = :categoryId")
    suspend fun getGoalForCategory(categoryId: UUID): GoalEntity?

    @Query("SELECT * FROM goals")
    fun getAllGoals(): Flow<List<GoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: GoalEntity)

    @Update
    suspend fun update(goal: GoalEntity)

    @Delete
    suspend fun delete(goal: GoalEntity)
}
