package com.truesummit.android.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "category_groups")
data class CategoryGroupEntity(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val name: String,
    val sort: Int
)
