package com.truesummit.android.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(tableName = "soft_delete_tombstones")
data class SoftDeleteTombstoneEntity(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val table: String,
    val recordID: UUID,
    val createdAt: Date = Date()
)
