package com.example.groupaac.data.pi

import com.example.groupaac.model.SignalType
import com.example.groupaac.model.SessionRole

data class PiJoinRequest(
    val sessionCode: String,
    val userId: String,
    val displayName: String,
    val role: SessionRole
)

data class PiMessagePayload(
    val id: String,
    val sessionId: String,
    val senderUserId: String,
    val senderName: String,
    val text: String?,
    val attachmentId: String?,
    val target: String,
    val createdAt: Long
)

data class PiSignalPayload(
    val id: String,
    val sessionId: String,
    val userId: String,
    val displayName: String,
    val type: SignalType,
    val createdAt: Long
)

sealed interface PiSessionEvent {
    data object Connected : PiSessionEvent
    data object Disconnected : PiSessionEvent
    data class Error(val message: String) : PiSessionEvent
}
