package com.example.groupaac.data.realtime.sync

import com.example.groupaac.data.entity.MessageEntity
import com.example.groupaac.data.entity.AttachmentEntity
import com.example.groupaac.data.entity.StatusSignalEntity
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionJoinRequestEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.data.realtime.protocol.ReceivedRealtimeEvent
import com.example.groupaac.model.DisplayCommandOrigin
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

    suspend fun publishSessionSettingsChanged(
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
        member: SessionMemberEntity,
        actorUserId: String = member.userId
    )

    suspend fun publishMemberLeft(
        session: SessionEntity,
        member: SessionMemberEntity,
        actorUserId: String = member.userId
    )

    suspend fun publishMemberRemoved(
        session: SessionEntity,
        member: SessionMemberEntity,
        actorUserId: String
    )

    suspend fun publishMemberDisplayNameChanged(
        session: SessionEntity,
        member: SessionMemberEntity,
        actorUserId: String
    )

    suspend fun publishMemberRoleChanged(
        session: SessionEntity,
        member: SessionMemberEntity,
        actorUserId: String
    )

    suspend fun publishHostTransferred(
        session: SessionEntity,
        newHostMember: SessionMemberEntity,
        previousHostUserId: String,
        actorUserId: String
    )

    suspend fun publishFacilitatorRequested(
        request: SessionJoinRequestEntity,
        actorUserId: String
    )

    suspend fun publishFacilitatorApproved(
        request: SessionJoinRequestEntity,
        member: SessionMemberEntity,
        session: SessionEntity,
        actorUserId: String
    )

    suspend fun publishFacilitatorDeclined(
        request: SessionJoinRequestEntity,
        session: SessionEntity,
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

    suspend fun publishMessageDeleted(
        message: MessageEntity,
        actorUserId: String
    )

    suspend fun publishAnnouncementCreated(
        message: MessageEntity,
        senderName: String,
        actorUserId: String
    )

    suspend fun publishAttachmentAvailable(
        message: MessageEntity,
        attachment: AttachmentEntity,
        actorUserId: String
    )

    suspend fun publishAttachmentFailed(
        message: MessageEntity,
        attachment: AttachmentEntity,
        actorUserId: String,
        errorMessage: String? = null
    )

    suspend fun publishSignalCreated(
        signal: StatusSignalEntity,
        displayName: String
    )

    suspend fun publishSignalSnoozed(
        signal: StatusSignalEntity,
        facilitatorUserId: String
    )

    suspend fun publishSignalCleared(
        signal: StatusSignalEntity,
        actorUserId: String
    )

    suspend fun publishSnapshotRequested(
        sessionId: String,
        requesterUserId: String,
        actorUserId: String
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
        eventId: String,
        session: SessionEntity,
        message: MessageEntity,
        senderName: String,
        actorUserId: String,
        restore: Boolean,
        isPinned: Boolean,
        origin: DisplayCommandOrigin
    )

    suspend fun publishDisplayPinState(
        eventId: String,
        sessionId: String,
        messageId: String,
        actorUserId: String,
        pinned: Boolean,
        displayMode: DisplayMode,
        origin: DisplayCommandOrigin?
    )

    suspend fun publishDisplayClear(
        eventId: String,
        sessionId: String,
        actorUserId: String,
        displayMode: DisplayMode,
        origin: DisplayCommandOrigin?
    )

    suspend fun publishDisplayModeChanged(
        eventId: String,
        sessionId: String,
        actorUserId: String,
        displayMode: DisplayMode,
        currentMessageId: String?,
        isPinned: Boolean,
        origin: DisplayCommandOrigin?
    )

    suspend fun applyIncoming(received: ReceivedRealtimeEvent): Boolean
}

object NoOpSessionRealtimeSync : SessionRealtimeSync {
    override suspend fun publishSessionStarted(session: SessionEntity, actorUserId: String) = Unit
    override suspend fun publishSessionUpdated(session: SessionEntity, actorUserId: String) = Unit
    override suspend fun publishSessionSettingsChanged(session: SessionEntity, actorUserId: String) = Unit
    override suspend fun publishSessionEnded(session: SessionEntity, actorUserId: String) = Unit
    override suspend fun publishSessionCancelled(session: SessionEntity, actorUserId: String) = Unit
    override suspend fun publishMemberJoined(session: SessionEntity, member: SessionMemberEntity, actorUserId: String) = Unit
    override suspend fun publishMemberLeft(session: SessionEntity, member: SessionMemberEntity, actorUserId: String) = Unit
    override suspend fun publishMemberRemoved(session: SessionEntity, member: SessionMemberEntity, actorUserId: String) = Unit
    override suspend fun publishMemberDisplayNameChanged(session: SessionEntity, member: SessionMemberEntity, actorUserId: String) = Unit
    override suspend fun publishMemberRoleChanged(session: SessionEntity, member: SessionMemberEntity, actorUserId: String) = Unit
    override suspend fun publishHostTransferred(session: SessionEntity, newHostMember: SessionMemberEntity, previousHostUserId: String, actorUserId: String) = Unit
    override suspend fun publishFacilitatorRequested(request: SessionJoinRequestEntity, actorUserId: String) = Unit
    override suspend fun publishFacilitatorApproved(
        request: SessionJoinRequestEntity,
        member: SessionMemberEntity,
        session: SessionEntity,
        actorUserId: String
    ) = Unit
    override suspend fun publishFacilitatorDeclined(
        request: SessionJoinRequestEntity,
        session: SessionEntity,
        actorUserId: String
    ) = Unit
    override suspend fun publishFacilitatorCancelled(request: SessionJoinRequestEntity, actorUserId: String) = Unit
    override suspend fun publishMessageCreated(message: MessageEntity, senderName: String, target: MessageTarget) = Unit
    override suspend fun publishMessageDeleted(message: MessageEntity, actorUserId: String) = Unit
    override suspend fun publishAnnouncementCreated(message: MessageEntity, senderName: String, actorUserId: String) = Unit
    override suspend fun publishAttachmentAvailable(message: MessageEntity, attachment: AttachmentEntity, actorUserId: String) = Unit
    override suspend fun publishAttachmentFailed(message: MessageEntity, attachment: AttachmentEntity, actorUserId: String, errorMessage: String?) = Unit
    override suspend fun publishSignalCreated(signal: StatusSignalEntity, displayName: String) = Unit
    override suspend fun publishSignalSnoozed(signal: StatusSignalEntity, facilitatorUserId: String) = Unit
    override suspend fun publishSignalCleared(signal: StatusSignalEntity, actorUserId: String) = Unit
    override suspend fun publishSnapshotRequested(sessionId: String, requesterUserId: String, actorUserId: String) = Unit
    override suspend fun publishSnapshot(
        session: SessionEntity,
        members: List<SessionMemberEntity>,
        requests: List<SessionJoinRequestEntity>,
        messages: List<MessageEntity>,
        requesterUserId: String,
        actorUserId: String
    ) = Unit
    override suspend fun publishDisplayShowMessage(
        eventId: String,
        session: SessionEntity,
        message: MessageEntity,
        senderName: String,
        actorUserId: String,
        restore: Boolean,
        isPinned: Boolean,
        origin: DisplayCommandOrigin
    ) = Unit
    override suspend fun publishDisplayPinState(
        eventId: String,
        sessionId: String,
        messageId: String,
        actorUserId: String,
        pinned: Boolean,
        displayMode: DisplayMode,
        origin: DisplayCommandOrigin?
    ) = Unit
    override suspend fun publishDisplayClear(
        eventId: String,
        sessionId: String,
        actorUserId: String,
        displayMode: DisplayMode,
        origin: DisplayCommandOrigin?
    ) = Unit
    override suspend fun publishDisplayModeChanged(
        eventId: String,
        sessionId: String,
        actorUserId: String,
        displayMode: DisplayMode,
        currentMessageId: String?,
        isPinned: Boolean,
        origin: DisplayCommandOrigin?
    ) = Unit

    override suspend fun applyIncoming(received: ReceivedRealtimeEvent): Boolean = false
}
