package com.truesummit.android.data.dao

import androidx.room.*
import com.truesummit.android.data.entity.TransactionAttachmentEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface TransactionAttachmentDao {
    @Query("SELECT * FROM transaction_attachments WHERE transactionId = :transactionId ORDER BY createdAt ASC")
    fun getForTransaction(transactionId: UUID): Flow<List<TransactionAttachmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: TransactionAttachmentEntity)

    @Query("DELETE FROM transaction_attachments WHERE id = :id")
    suspend fun deleteById(id: UUID)

    @Query("SELECT COUNT(*) FROM transaction_attachments WHERE transactionId = :transactionId")
    suspend fun countForTransaction(transactionId: UUID): Int
}
