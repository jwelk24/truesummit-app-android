package com.truesummit.android.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(tableName = "transaction_attachments")
data class TransactionAttachmentEntity(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val transactionId: UUID,
    val imageData: ByteArray,
    val createdAt: Date = Date()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TransactionAttachmentEntity) return false
        return id == other.id
    }
    override fun hashCode() = id.hashCode()
}
