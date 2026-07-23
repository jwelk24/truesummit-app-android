package com.truesummit.android.data.dao

import androidx.room.*
import com.truesummit.android.data.entity.LiabilityEntity
import kotlinx.coroutines.flow.Flow
import java.util.*

@Dao
interface LiabilityDao {
    @Query("SELECT * FROM liabilities WHERE accountId = :accountId")
    suspend fun getForAccount(accountId: UUID): LiabilityEntity?

    @Query("SELECT * FROM liabilities")
    fun getAll(): Flow<List<LiabilityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(liability: LiabilityEntity)

    @Update
    suspend fun update(liability: LiabilityEntity)

    @Query("SELECT * FROM liabilities")
    suspend fun getAllList(): List<LiabilityEntity>
}
