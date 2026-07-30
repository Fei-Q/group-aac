package com.example.groupaac.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.groupaac.model.OutboxDomainType
import com.example.groupaac.model.OutboxEventState

@Entity(
    tableName = "outbox_events",
    indices = [
        Index(value = ["state", "nextAttemptAt"]),
        Index(value = ["sessionId"])
    ]
)
data class OutboxEventEntity(
    @PrimaryKey val eventId: String,
    val domainType: OutboxDomainType,
    val domainId: String,
    val actorUserId: String?,
    val sessionId: String,
    val channel: String,
    val serializedEvent: String,
    val attemptCount: Int = 0,
    val nextAttemptAt: Long,
    val state: OutboxEventState = OutboxEventState.PENDING,
    val createdAt: Long,
    val acceptedTimetoken: Long? = null,
    val expiresAt: Long? = null
)
