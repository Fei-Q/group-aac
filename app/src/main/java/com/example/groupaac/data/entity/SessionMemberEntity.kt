package com.example.groupaac.data.entity

import androidx.room.Entity
import com.example.groupaac.model.SessionRole

@Entity(tableName = "session_members", primaryKeys = ["sessionId", "userId"])
data class SessionMemberEntity(
    val sessionId: String,
    val userId: String,
    val displayName: String,
    val role: SessionRole,
    val joinedAt: Long
)
