package com.truesummit.android.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.util.*

@Entity(
    tableName = "plaid_account_links",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountModelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountModelId"), Index("plaidAccountId", unique = true)]
)
data class PlaidAccountLinkEntity(
    @PrimaryKey val plaidAccountId: String,
    val plaidItemId: String,
    val accountModelId: UUID,
    val lastBalance: BigDecimal,
    val updatedAt: Date = Date()
)

@Entity(
    tableName = "plaid_transaction_links",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionModelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("transactionModelId"), Index("plaidTransactionId", unique = true)]
)
data class PlaidTransactionLinkEntity(
    @PrimaryKey val plaidTransactionId: String,
    val transactionModelId: UUID,
    val plaidAccountId: String,
    val pending: Boolean
)
