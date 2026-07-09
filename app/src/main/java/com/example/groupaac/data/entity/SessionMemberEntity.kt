package com.example.groupaac.data.entity

import androidx.room.Entity
import com.example.groupaac.model.UserRole

@Entity(tableName = "session_members", primaryKeys = ["sessionId", "userId"])
data class SessionMemberEntity(
    val sessionId: String,
    val userId: String,
    val displayName: String,
    val role: UserRole,
    val joinedAt: Long
)
