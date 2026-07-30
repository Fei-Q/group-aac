package com.example.groupaac.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.SessionStatus

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val joinCode: String,
    val hostUserId: String? = null,
    val status: SessionStatus = SessionStatus.DRAFT,
    val displayMode: DisplayMode = DisplayMode.AUTO_LATEST,
    val displayId: String? = null,
    val createdAt: Long,
    val scheduledStartAt: Long? = null,
    val scheduledDurationMinutes: Int? = null,
    val actualStartedAt: Long? = null,
    val actualEndedAt: Long? = null,
    val expiresAt: Long? = null,
    val updatedAt: Long = createdAt
)