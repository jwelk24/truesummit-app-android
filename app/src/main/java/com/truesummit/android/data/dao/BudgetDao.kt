package com.truesummit.android.data.dao

import androidx.room.*
import com.truesummit.android.data.entity.BudgetAllocationEntity
import com.truesummit.android.data.entity.BudgetMonthEntity
import kotlinx.coroutines.flow.Flow
import java.util.*

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budget_months WHERE year = :year AND month = :month")
    suspend fun getMonth(year: Int, month: Int): BudgetMonthEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMonth(month: BudgetMonthEntity)

    @Update
    suspend fun updateMonth(month: BudgetMonthEntity)

    @Delete
    suspend fun deleteMonth(month: BudgetMonthEntity)

    @Query("SELECT * FROM budget_months")
    suspend fun getAllMonths(): List<BudgetMonthEntity>

    @Query("SELECT * FROM budget_allocations WHERE monthId = :monthId")
    fun getAllocationsForMonth(monthId: UUID): Flow<List<BudgetAllocationEntity>>

    @Query("SELECT * FROM budget_allocations WHERE monthId = :monthId AND categoryId = :categoryId")
    suspend fun getAllocation(monthId: UUID, categoryId: UUID): BudgetAllocationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllocation(allocation: BudgetAllocationEntity)

    @Update
    suspend fun updateAllocation(allocation: BudgetAllocationEntity)

    @Query("SELECT * FROM budget_allocations")
    suspend fun getAllAllocations(): List<BudgetAllocationEntity>

    @Query("SELECT * FROM budget_allocations WHERE categoryId = :categoryId")
    suspend fun getAllocationsForCategory(categoryId: UUID): List<BudgetAllocationEntity>

    @Delete
    suspend fun deleteAllocation(allocation: BudgetAllocationEntity)
}
