package com.example.groupaac.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    @ColumnInfo(name = "uid")
    val id: String,
    val displayName: String,
    val createdAt: Long
)
