package com.truesummit.android.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.truesummit.android.data.model.LiabilityKind
import java.math.BigDecimal
import java.util.Date
import java.util.UUID

@Entity(
    tableName = "liabilities",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId"), Index("plaidAccountId", unique = true)]
)
data class LiabilityEntity(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val plaidAccountId: String,
    val kind: LiabilityKind,
    val lastStatementBalance: BigDecimal?,
    val lastStatementIssueDate: Date?,
    val minimumPayment: BigDecimal?,
    val nextPaymentDueDate: Date?,
    val lastPaymentAmount: BigDecimal?,
    val lastPaymentDate: Date?,
    val interestRatePercentage: BigDecimal?,
    val originationPrincipal: BigDecimal?,
    val originationDate: Date?,
    val maturityDate: Date?,
    val loanName: String?,
    val rawJSON: String?,
    val updatedAt: Date,
    val accountId: UUID?
)
