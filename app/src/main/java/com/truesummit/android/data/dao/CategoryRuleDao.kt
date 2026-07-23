package com.truesummit.android.data.dao

import androidx.room.*
import com.truesummit.android.data.entity.CategoryRuleEntity
import kotlinx.coroutines.flow.Flow
import java.util.*

@Dao
interface CategoryRuleDao {
    @Query("SELECT * FROM category_rules ORDER BY priority ASC")
    fun getAll(): Flow<List<CategoryRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: CategoryRuleEntity)

    @Update
    suspend fun update(rule: CategoryRuleEntity)

    @Delete
    suspend fun delete(rule: CategoryRuleEntity)

    @Query("SELECT * FROM category_rules WHERE enabled = 1 ORDER BY priority ASC")
    suspend fun getEnabledRules(): List<CategoryRuleEntity>
}
