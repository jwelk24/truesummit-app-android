package com.truesummit.android.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.truesummit.android.data.model.ScheduledKind
import java.math.BigDecimal
import java.util.Date
import java.util.UUID

@Entity(
    tableName = "scheduled_items",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL
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
data class ScheduledItemEntity(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val kind: ScheduledKind,
    val name: String,
    val amount: BigDecimal,
    val nextDate: Date,
    val intervalDays: Int,
    val accountId: UUID?,
    val categoryId: UUID?
)
