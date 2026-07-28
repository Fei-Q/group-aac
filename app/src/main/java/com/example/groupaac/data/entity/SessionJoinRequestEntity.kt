package com.example.groupaac.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.groupaac.model.JoinRequestStatus
import com.example.groupaac.model.SessionRole

@Entity(
    tableName = "session_join_requests",
    indices = [
        Index(value = ["sessionId", "status"]),
        Index(value = ["userId"])
    ]
)
data class SessionJoinRequestEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val userId: String,
    val displayName: String,
    val requestedRole: SessionRole,
    val status: JoinRequestStatus,
    val requestedAt: Long,
    val decidedAt: Long? = null,
    val decidedByUserId: String? = null
)
