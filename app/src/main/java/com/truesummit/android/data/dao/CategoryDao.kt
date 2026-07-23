package com.truesummit.android.data.dao

import androidx.room.*
import com.truesummit.android.data.entity.CategoryEntity
import com.truesummit.android.data.entity.CategoryGroupEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category_groups ORDER BY sort ASC")
    fun getGroups(): Flow<List<CategoryGroupEntity>>

    @Query("SELECT * FROM categories ORDER BY sort ASC")
    fun getCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: CategoryGroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: UUID): CategoryEntity?

    @Query("SELECT * FROM category_groups ORDER BY sort ASC")
    suspend fun getGroupsList(): List<CategoryGroupEntity>

    @Query("SELECT * FROM categories ORDER BY sort ASC")
    suspend fun getCategoriesList(): List<CategoryEntity>

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteGroup(group: CategoryGroupEntity)
}
