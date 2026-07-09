package com.example.groupaac.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.groupaac.model.UserRole

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val role: UserRole,
    val createdAt: Long,
    val lastLoginAt: Long? = null
)
