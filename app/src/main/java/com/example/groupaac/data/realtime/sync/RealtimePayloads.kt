package com.example.groupaac.data.realtime.sync

import com.example.groupaac.data.entity.MessageEntity
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionJoinRequestEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.model.JoinRequestStatus
import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.MessageDisplayStatus
import com.example.groupaac.model.MessageStatus
import com.example.groupaac.model.MessageTransportStatus
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.model.SessionRole
import kotlinx.serialization.Serializable

@Serializable
data class SessionPayload(
    val id: String,
    val name: String,
    val joinCode: String,
    val hostUserId: String? = null,
    val displayMode: String,
    val createdAt: Long,
    val scheduledStartAt: Long? = null,
    val scheduledDurationMinutes: Int? = null,
    val actualStartedAt: Long? = null,
    val actualEndedAt: Long? = null,
    val updatedAt: Long
)

@Serializable
data class SessionMemberPayload(
    val sessionId: String,
    val userId: String,
    val displayName: String,
    val role: String,
    val joinedAt: Long
)

@Serializable
data class SessionJoinRequestPayload(
    val id: String,
    val sessionId: String,
    val userId: String,
    val displayName: String,
    val requestedRole: String,
    val status: String,
    val requestedAt: Long,
    val decidedAt: Long? = null,
    val decidedByUserId: String? = null
)

@Serializable
data class MessagePayload(
    val id: String,
    val sessionId: String,
    val senderUserId: String,
    val senderName: String,
    val target: String,
    val text: String? = null,
    val attachmentId: String? = null,
    val createdAt: Long,
    val status: String,
    val transportStatus: String,
    val displayStatus: String,
    val saved: Boolean,
    val displayedOnMonitor: Boolean
)

@Serializable
data class SessionSnapshotPayload(
    val session: SessionPayload,
    val members: List<SessionMemberPayload>,
    val requests: List<SessionJoinRequestPayload>,
    val messages: List<MessagePayload>
)

@Serializable
data class DisplayMessagePayload(
    val sessionId: String,
    val message: MessagePayload,
    val displayMode: String,
    val isPinned: Boolean
)

@Serializable
data class DisplayStatePayload(
    val sessionId: String,
    val currentMessageId: String? = null,
    val isPinned: Boolean,
    val displayMode: String
)

fun SessionEntity.toRealtimePayload(): SessionPayload = SessionPayload(
    id = id,
    name = name,
    joinCode = joinCode,
    hostUserId = hostUserId,
    displayMode = displayMode.name,
    createdAt = createdAt,
    scheduledStartAt = scheduledStartAt,
    scheduledDurationMinutes = scheduledDurationMinutes,
    actualStartedAt = actualStartedAt,
    actualEndedAt = actualEndedAt,
    updatedAt = updatedAt
)

fun SessionPayload.toEntity(): SessionEntity = SessionEntity(
    id = id,
    name = name,
    joinCode = joinCode,
    hostUserId = hostUserId,
    displayMode = com.example.groupaac.model.DisplayMode.fromName(displayMode),
    createdAt = createdAt,
    scheduledStartAt = scheduledStartAt,
    scheduledDurationMinutes = scheduledDurationMinutes,
    actualStartedAt = actualStartedAt,
    actualEndedAt = actualEndedAt,
    updatedAt = updatedAt
)

fun SessionMemberEntity.toRealtimePayload(): SessionMemberPayload = SessionMemberPayload(
    sessionId = sessionId,
    userId = userId,
    displayName = displayName,
    role = role.name,
    joinedAt = joinedAt
)

fun SessionMemberPayload.toEntity(): SessionMemberEntity = SessionMemberEntity(
    sessionId = sessionId,
    userId = userId,
    displayName = displayName,
    role = SessionRole.fromName(role),
    joinedAt = joinedAt
)

fun SessionJoinRequestEntity.toRealtimePayload(): SessionJoinRequestPayload = SessionJoinRequestPayload(
    id = id,
    sessionId = sessionId,
    userId = userId,
    displayName = displayName,
    requestedRole = requestedRole.name,
    status = status.name,
    requestedAt = requestedAt,
    decidedAt = decidedAt,
    decidedByUserId = decidedByUserId
)

fun SessionJoinRequestPayload.toEntity(): SessionJoinRequestEntity = SessionJoinRequestEntity(
    id = id,
    sessionId = sessionId,
    userId = userId,
    displayName = displayName,
    requestedRole = SessionRole.fromName(requestedRole),
    status = JoinRequestStatus.fromName(status),
    requestedAt = requestedAt,
    decidedAt = decidedAt,
    decidedByUserId = decidedByUserId
)

fun MessageEntity.toRealtimePayload(senderName: String): MessagePayload = MessagePayload(
    id = id,
    sessionId = sessionId,
    senderUserId = senderUserId,
    senderName = senderName,
    target = target.name,
    text = text,
    attachmentId = attachmentId,
    createdAt = createdAt,
    status = status.name,
    transportStatus = transportStatus.name,
    displayStatus = displayStatus.name,
    saved = saved,
    displayedOnMonitor = displayedOnMonitor
)

fun MessagePayload.toEntity(): MessageEntity = MessageEntity(
    id = id,
    sessionId = sessionId,
    senderUserId = senderUserId,
    target = MessageTarget.entries.firstOrNull { it.name == target }
        ?: MessageTarget.GROUP,
    text = text,
    attachmentId = attachmentId,
    createdAt = createdAt,
    status = MessageStatus.entries.firstOrNull { it.name == status }
        ?: MessageStatus.ACTIVE,
    transportStatus =
        MessageTransportStatus.entries.firstOrNull {
            it.name == transportStatus
        } ?: MessageTransportStatus.SENT,
    displayStatus =
        MessageDisplayStatus.entries.firstOrNull {
            it.name == displayStatus
        } ?: MessageDisplayStatus.HIDDEN,
    saved = saved,
    displayedOnMonitor = displayedOnMonitor
)

fun DisplayStatePayload.mode(): DisplayMode =
    DisplayMode.fromName(displayMode)
