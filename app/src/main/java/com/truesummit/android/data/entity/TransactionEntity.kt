package com.truesummit.android.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.util.Date
import java.util.UUID

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("accountId"), Index("categoryId")]
)
data class TransactionEntity(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val date: Date,
    val amount: BigDecimal,
    val merchant: String,
    val memo: String?,
    val cleared: Boolean,
    val flagColor: String?,
    val pfcPrimary: String?,
    val accountId: UUID?,
    val categoryId: UUID?,
    val tags: String = "",
    val awaitingRefund: Boolean = false,
    val refundsTransactionId: UUID? = null
) {
    fun tagList(): List<String> = if (tags.isBlank()) emptyList() else tags.split(",").filter { it.isNotBlank() }
    fun withTags(newTags: List<String>): TransactionEntity = copy(tags = newTags.joinToString(","))
}
