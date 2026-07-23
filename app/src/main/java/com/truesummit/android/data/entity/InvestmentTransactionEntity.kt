package com.truesummit.android.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.util.Date
import java.util.UUID

@Entity(
    tableName = "investment_transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId"), Index("plaidInvestmentTransactionId", unique = true)]
)
data class InvestmentTransactionEntity(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val plaidInvestmentTransactionId: String,
    val date: Date,
    val name: String,
    val amount: BigDecimal,
    val fees: BigDecimal?,
    val quantity: BigDecimal?,
    val price: BigDecimal?,
    val type: String,
    val subtype: String?,
    val plaidSecurityId: String?,
    val tickerSymbol: String?,
    val securityName: String?,
    val currencyCode: String,
    val accountId: UUID?
)
