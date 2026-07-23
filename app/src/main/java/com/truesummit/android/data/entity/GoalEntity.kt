package com.truesummit.android.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.truesummit.android.data.model.GoalType
import java.math.BigDecimal
import java.util.Date
import java.util.UUID

@Entity(
    tableName = "goals",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("categoryId")]
)
data class GoalEntity(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val type: GoalType,
    val targetAmount: BigDecimal,
    val targetDate: Date?,
    val categoryId: UUID?
)
