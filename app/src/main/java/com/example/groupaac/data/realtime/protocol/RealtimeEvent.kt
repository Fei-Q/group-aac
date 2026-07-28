package com.example.groupaac.data.realtime.protocol

import kotlinx.serialization.json.JsonObject

data class RealtimeEvent(
    val eventId: String,
    val type: String,
    val sessionId: String,
    val actorUserId: String?,
    val occurredAt: Long,
    val inReplyToEventId: String? = null,
    val expiresAt: Long? = null,
    val payload: JsonObject
)

data class ReceivedRealtimeEvent(
    val channel: String,
    val timetoken: Long,
    val publisherUserId: String?,
    val event: RealtimeEvent
)
