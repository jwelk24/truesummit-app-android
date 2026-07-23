package com.truesummit.android.data.dao

import androidx.room.*
import com.truesummit.android.data.entity.TransactionEntity
import com.truesummit.android.data.entity.TransactionSplitEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: UUID): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("SELECT * FROM transaction_splits WHERE transactionId = :transactionId")
    suspend fun getSplitsForTransaction(transactionId: UUID): List<TransactionSplitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSplits(splits: List<TransactionSplitEntity>)

    @Query("DELETE FROM transaction_splits WHERE transactionId = :transactionId")
    suspend fun deleteSplitsForTransaction(transactionId: UUID)

    @Query("SELECT * FROM transaction_splits")
    suspend fun getAllSplits(): List<TransactionSplitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSplit(split: TransactionSplitEntity)
}
