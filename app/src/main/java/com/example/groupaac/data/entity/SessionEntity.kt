package com.example.groupaac.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val joinCode: String,
    val hostUserId: String? = null,

    // When the session row/code was created in local storage.
    val createdAt: Long,

    // Planned session metadata. Null for ad hoc sessions.
    val scheduledStartAt: Long? = null,
    val scheduledDurationMinutes: Int? = null,

    // Actual runtime metadata. Set when facilitator launches/ends the session.
    val actualStartedAt: Long? = null,
    val actualEndedAt: Long? = null
)
