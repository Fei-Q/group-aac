package com.example.groupaac.data.realtime.sync

import com.example.groupaac.data.entity.MessageEntity
import com.example.groupaac.data.entity.StatusSignalEntity
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionJoinRequestEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.data.realtime.protocol.ReceivedRealtimeEvent
import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.MessageTarget

interface SessionRealtimeSync {
    suspend fun publishSessionStarted(
        session: SessionEntity,
        actorUserId: String
    )

    suspend fun publishSessionUpdated(
        session: SessionEntity,
        actorUserId: String
    )

    suspend fun publishSessionEnded(
        session: SessionEntity,
        actorUserId: String
    )

    suspend fun publishSessionCancelled(
        session: SessionEntity,
        actorUserId: String
    )

    suspend fun publishMemberJoined(
        session: SessionEntity,
        member: SessionMemberEntity
    )

    suspend fun publishFacilitatorRequested(
        request: SessionJoinRequestEntity,
        actorUserId: String
    )

    suspend fun publishFacilitatorApproved(
        request: SessionJoinRequestEntity,
        actorUserId: String
    )

    suspend fun publishFacilitatorDeclined(
        request: SessionJoinRequestEntity,
        actorUserId: String
    )

    suspend fun publishFacilitatorCancelled(
        request: SessionJoinRequestEntity,
        actorUserId: String
    )

    suspend fun publishMessageCreated(
        message: MessageEntity,
        senderName: String,
        target: MessageTarget
    )

    suspend fun publishSignalCreated(
        signal: StatusSignalEntity,
        displayName: String
    )

    suspend fun publishSnapshot(
        session: SessionEntity,
        members: List<SessionMemberEntity>,
        requests: List<SessionJoinRequestEntity>,
        messages: List<MessageEntity>,
        requesterUserId: String,
        actorUserId: String
    )

    suspend fun publishDisplayShowMessage(
        session: SessionEntity,
        message: MessageEntity,
        senderName: String,
        actorUserId: String,
        restore: Boolean
    )

    suspend fun publishDisplayPinState(
        sessionId: String,
        messageId: String,
        actorUserId: String,
        pinned: Boolean
    )

    suspend fun publishDisplayClear(
        sessionId: String,
        actorUserId: String
    )

    suspend fun applyIncoming(received: ReceivedRealtimeEvent): Boolean
}

object NoOpSessionRealtimeSync : SessionRealtimeSync {
    override suspend fun publishSessionStarted(session: SessionEntity, actorUserId: String) = Unit
    override suspend fun publishSessionUpdated(session: SessionEntity, actorUserId: String) = Unit
    override suspend fun publishSessionEnded(session: SessionEntity, actorUserId: String) = Unit
    override suspend fun publishSessionCancelled(session: SessionEntity, actorUserId: String) = Unit
    override suspend fun publishMemberJoined(session: SessionEntity, member: SessionMemberEntity) = Unit
    override suspend fun publishFacilitatorRequested(request: SessionJoinRequestEntity, actorUserId: String) = Unit
    override suspend fun publishFacilitatorApproved(request: SessionJoinRequestEntity, actorUserId: String) = Unit
    override suspend fun publishFacilitatorDeclined(request: SessionJoinRequestEntity, actorUserId: String) = Unit
    override suspend fun publishFacilitatorCancelled(request: SessionJoinRequestEntity, actorUserId: String) = Unit
    override suspend fun publishMessageCreated(message: MessageEntity, senderName: String, target: MessageTarget) = Unit
    override suspend fun publishSignalCreated(signal: StatusSignalEntity, displayName: String) = Unit
    override suspend fun publishSnapshot(
        session: SessionEntity,
        members: List<SessionMemberEntity>,
        requests: List<SessionJoinRequestEntity>,
        messages: List<MessageEntity>,
        requesterUserId: String,
        actorUserId: String
    ) = Unit
    override suspend fun publishDisplayShowMessage(
        session: SessionEntity,
        message: MessageEntity,
        senderName: String,
        actorUserId: String,
        restore: Boolean
    ) = Unit
    override suspend fun publishDisplayPinState(
        sessionId: String,
        messageId: String,
        actorUserId: String,
        pinned: Boolean
    ) = Unit
    override suspend fun publishDisplayClear(
        sessionId: String,
        actorUserId: String
    ) = Unit

    override suspend fun applyIncoming(received: ReceivedRealtimeEvent): Boolean = false
}
