package com.truesummit.android.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.util.UUID

@Entity(tableName = "budget_months")
data class BudgetMonthEntity(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val year: Int,
    val month: Int,
    val carryover: BigDecimal
)
