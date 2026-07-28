package com.example.groupaac.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.groupaac.model.DisplayMode

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val joinCode: String,
    val hostUserId: String? = null,
    val displayMode: DisplayMode = DisplayMode.AUTO_LATEST,
    val createdAt: Long,
    val scheduledStartAt: Long? = null,
    val scheduledDurationMinutes: Int? = null,
    val actualStartedAt: Long? = null,
    val actualEndedAt: Long? = null,
    val updatedAt: Long = createdAt
)
