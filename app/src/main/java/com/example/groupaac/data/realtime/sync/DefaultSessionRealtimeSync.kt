package com.example.groupaac.data.realtime.sync

import com.example.groupaac.data.dao.MessageDao
import com.example.groupaac.data.dao.ReliabilityDao
import com.example.groupaac.data.dao.SessionDao
import com.example.groupaac.data.dao.SessionJoinRequestDao
import com.example.groupaac.data.dao.StatusSignalDao
import com.example.groupaac.data.entity.AttachmentEntity
import com.example.groupaac.data.entity.DisplayStateEntity
import com.example.groupaac.data.entity.MessageEntity
import com.example.groupaac.data.entity.SignalSnoozeEntity
import com.example.groupaac.data.entity.StatusSignalEntity
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionJoinRequestEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.data.realtime.protocol.RealtimeChannels
import com.example.groupaac.data.realtime.protocol.RealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeEventTypes
import com.example.groupaac.data.realtime.reliability.RealtimeReliabilityStore
import com.example.groupaac.model.DisplayCommandOrigin
import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.model.OutboxDomainType
import com.example.groupaac.data.repository.TransactionRunner
import com.example.groupaac.model.SessionStatus
import com.example.groupaac.util.IdUtils
import kotlinx.serialization.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DefaultSessionRealtimeSync(
    private val transactionRunner: TransactionRunner,
    private val sessionDao: SessionDao,
    private val sessionJoinRequestDao: SessionJoinRequestDao,
    private val messageDao: MessageDao,
    private val statusSignalDao: StatusSignalDao,
    private val reliabilityDao: ReliabilityDao,
    private val reliabilityStore: RealtimeReliabilityStore
) : SessionRealtimeSync {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        val PUBLISHED_EVENT_TYPES = setOf(
            RealtimeEventTypes.SESSION_STARTED,
            RealtimeEventTypes.SESSION_UPDATED,
            RealtimeEventTypes.SESSION_SETTINGS_CHANGED,
            RealtimeEventTypes.SESSION_ENDED,
            RealtimeEventTypes.SESSION_CANCELLED,
            RealtimeEventTypes.SESSION_SNAPSHOT_REQUESTED,
            RealtimeEventTypes.SESSION_SNAPSHOT,
            RealtimeEventTypes.MEMBER_JOINED,
            RealtimeEventTypes.MEMBER_LEFT,
            RealtimeEventTypes.MEMBER_REMOVED,
            RealtimeEventTypes.MEMBER_DISPLAY_NAME_CHANGED,
            RealtimeEventTypes.MEMBER_ROLE_CHANGED,
            RealtimeEventTypes.FACILITATOR_REQUESTED,
            RealtimeEventTypes.FACILITATOR_APPROVED,
            RealtimeEventTypes.FACILITATOR_DECLINED,
            RealtimeEventTypes.FACILITATOR_CANCELLED,
            RealtimeEventTypes.HOST_TRANSFERRED,
            RealtimeEventTypes.MESSAGE_CREATED,
            RealtimeEventTypes.MESSAGE_DELETED,
            RealtimeEventTypes.ATTACHMENT_AVAILABLE,
            RealtimeEventTypes.ATTACHMENT_FAILED,
            RealtimeEventTypes.ANNOUNCEMENT_CREATED,
            RealtimeEventTypes.AAC_SIGNAL_CREATED,
            RealtimeEventTypes.AAC_SIGNAL_SNOOZED,
            RealtimeEventTypes.AAC_SIGNAL_CLEARED,
            RealtimeEventTypes.DISPLAY_SHOW_MESSAGE,
            RealtimeEventTypes.DISPLAY_RESTORE_MESSAGE,
            RealtimeEventTypes.DISPLAY_MODE_CHANGED,
            RealtimeEventTypes.DISPLAY_CLEAR,
            RealtimeEventTypes.DISPLAY_PIN_MESSAGE,
            RealtimeEventTypes.DISPLAY_UNPIN_MESSAGE
        )

        val APPLIED_EVENT_TYPES = setOf(
            RealtimeEventTypes.SESSION_STARTED,
            RealtimeEventTypes.SESSION_UPDATED,
            RealtimeEventTypes.SESSION_SETTINGS_CHANGED,
            RealtimeEventTypes.SESSION_ENDED,
            RealtimeEventTypes.SESSION_CANCELLED,
            RealtimeEventTypes.SESSION_SNAPSHOT_REQUESTED,
            RealtimeEventTypes.SESSION_SNAPSHOT,
            RealtimeEventTypes.MEMBER_JOINED,
            RealtimeEventTypes.MEMBER_LEFT,
            RealtimeEventTypes.MEMBER_REMOVED,
            RealtimeEventTypes.MEMBER_DISPLAY_NAME_CHANGED,
            RealtimeEventTypes.MEMBER_ROLE_CHANGED,
            RealtimeEventTypes.FACILITATOR_REQUESTED,
            RealtimeEventTypes.FACILITATOR_APPROVED,
            RealtimeEventTypes.FACILITATOR_DECLINED,
            RealtimeEventTypes.FACILITATOR_CANCELLED,
            RealtimeEventTypes.HOST_TRANSFERRED,
            RealtimeEventTypes.MESSAGE_CREATED,
            RealtimeEventTypes.MESSAGE_DELETED,
            RealtimeEventTypes.ATTACHMENT_AVAILABLE,
            RealtimeEventTypes.ATTACHMENT_FAILED,
            RealtimeEventTypes.ANNOUNCEMENT_CREATED,
            RealtimeEventTypes.AAC_SIGNAL_CREATED,
            RealtimeEventTypes.AAC_SIGNAL_SNOOZED,
            RealtimeEventTypes.AAC_SIGNAL_CLEARED,
            RealtimeEventTypes.DISPLAY_RENDERED,
            RealtimeEventTypes.DISPLAY_RESTORED,
            RealtimeEventTypes.DISPLAY_CLEARED,
            RealtimeEventTypes.DISPLAY_PINNED,
            RealtimeEventTypes.DISPLAY_UNPINNED,
            RealtimeEventTypes.DISPLAY_STATE
        )

        val RESERVED_EVENT_TYPES = setOf(
            RealtimeEventTypes.DISPLAY_BIND_SESSION,
            RealtimeEventTypes.DISPLAY_UNBIND_SESSION,
            RealtimeEventTypes.DISPLAY_SHOW_ATTACHMENT,
            RealtimeEventTypes.DISPLAY_SHOW_ANNOUNCEMENT,
            RealtimeEventTypes.DISPLAY_PLAY_SOUND,
            RealtimeEventTypes.DISPLAY_SET_PARTICIPANT_LIST,
            RealtimeEventTypes.DISPLAY_SET_THEME,
            RealtimeEventTypes.DISPLAY_CONNECTED,
            RealtimeEventTypes.DISPLAY_DISCONNECTED,
            RealtimeEventTypes.DISPLAY_FAILED,
            RealtimeEventTypes.DISPLAY_CAPABILITIES
        )
    }

    override suspend fun publishSessionStarted(
        session: SessionEntity,
        actorUserId: String
    ) {
        publish(
            domainType = OutboxDomainType.SESSION,
            domainId = session.id,
            channel = RealtimeChannels.public(session.id),
            event = event(
                type = RealtimeEventTypes.SESSION_STARTED,
                sessionId = session.id,
                actorUserId = actorUserId,
                payload = payload(
                    "session" to json.encodeToJsonElement(SessionPayload.serializer(), session.toRealtimePayload())
                )
            )
        )
    }

    override suspend fun publishSessionUpdated(
        session: SessionEntity,
        actorUserId: String
    ) {
        publishSessionEvent(
            type = RealtimeEventTypes.SESSION_UPDATED,
            session = session,
            actorUserId = actorUserId
        )
    }

    override suspend fun publishSessionSettingsChanged(
        session: SessionEntity,
        actorUserId: String
    ) {
        publishSessionEvent(
            type = RealtimeEventTypes.SESSION_SETTINGS_CHANGED,
            session = session,
            actorUserId = actorUserId
        )
    }

    override suspend fun publishSessionEnded(
        session: SessionEntity,
        actorUserId: String
    ) {
        publishSessionEvent(
            type = RealtimeEventTypes.SESSION_ENDED,
            session = session,
            actorUserId = actorUserId
        )
    }

    override suspend fun publishSessionCancelled(
        session: SessionEntity,
        actorUserId: String
    ) {
        publishSessionEvent(
            type = RealtimeEventTypes.SESSION_CANCELLED,
            session = session,
            actorUserId = actorUserId
        )
    }

    override suspend fun publishMemberJoined(
        session: SessionEntity,
        member: SessionMemberEntity
    ) {
        publishMemberEvent(
            type = RealtimeEventTypes.MEMBER_JOINED,
            session = session,
            member = member,
            actorUserId = member.userId
        )
    }

    override suspend fun publishMemberLeft(
        session: SessionEntity,
        member: SessionMemberEntity,
        actorUserId: String
    ) {
        publishMemberEvent(
            type = RealtimeEventTypes.MEMBER_LEFT,
            session = session,
            member = member,
            actorUserId = actorUserId
        )
    }

    override suspend fun publishMemberRemoved(
        session: SessionEntity,
        member: SessionMemberEntity,
        actorUserId: String
    ) {
        publishMemberEvent(
            type = RealtimeEventTypes.MEMBER_REMOVED,
            session = session,
            member = member,
            actorUserId = actorUserId
        )
    }

    override suspend fun publishMemberDisplayNameChanged(
        session: SessionEntity,
        member: SessionMemberEntity,
        actorUserId: String
    ) {
        publishMemberEvent(
            type = RealtimeEventTypes.MEMBER_DISPLAY_NAME_CHANGED,
            session = session,
            member = member,
            actorUserId = actorUserId
        )
    }

    override suspend fun publishMemberRoleChanged(
        session: SessionEntity,
        member: SessionMemberEntity,
        actorUserId: String
    ) {
        publishMemberEvent(
            type = RealtimeEventTypes.MEMBER_ROLE_CHANGED,
            session = session,
            member = member,
            actorUserId = actorUserId
        )
    }

    override suspend fun publishHostTransferred(
        session: SessionEntity,
        newHostMember: SessionMemberEntity,
        previousHostUserId: String,
        actorUserId: String
    ) {
        publish(
            domainType = OutboxDomainType.SESSION,
            domainId = session.id,
            channel = RealtimeChannels.public(session.id),
            event = event(
                type = RealtimeEventTypes.HOST_TRANSFERRED,
                sessionId = session.id,
                actorUserId = actorUserId,
                payload = payload(
                    "hostTransfer" to json.encodeToJsonElement(
                        HostTransferPayload.serializer(),
                        HostTransferPayload(
                            session = session.toRealtimePayload(),
                            newHostMember = newHostMember.toRealtimePayload(),
                            previousHostUserId = previousHostUserId
                        )
                    )
                )
            )
        )
    }

    override suspend fun publishFacilitatorRequested(
        request: SessionJoinRequestEntity,
        actorUserId: String
    ) {
        publish(
            domainType = OutboxDomainType.FACILITATOR_REQUEST,
            domainId = request.id,
            channel = RealtimeChannels.facilitator(request.sessionId),
            event = event(
                type = RealtimeEventTypes.FACILITATOR_REQUESTED,
                sessionId = request.sessionId,
                actorUserId = actorUserId,
                payload = payload(
                    "request" to json.encodeToJsonElement(
                        SessionJoinRequestPayload.serializer(),
                        request.toRealtimePayload()
                    )
                )
            )
        )
    }

    override suspend fun publishFacilitatorApproved(
        request: SessionJoinRequestEntity,
        member: SessionMemberEntity,
        session: SessionEntity,
        actorUserId: String
    ) {
        publish(
            domainType = OutboxDomainType.FACILITATOR_REQUEST,
            domainId = request.id,
            channel = RealtimeChannels.privateUser(request.sessionId, request.userId),
            event = event(
                type = RealtimeEventTypes.FACILITATOR_APPROVED,
                sessionId = request.sessionId,
                actorUserId = actorUserId,
                payload = payload(
                    "approval" to json.encodeToJsonElement(
                        FacilitatorApprovalPayload.serializer(),
                        FacilitatorApprovalPayload(
                            request = request.toRealtimePayload(),
                            member = member.toRealtimePayload(),
                            session = session.toRealtimePayload()
                        )
                    )
                )
            )
        )
    }

    override suspend fun publishFacilitatorDeclined(
        request: SessionJoinRequestEntity,
        session: SessionEntity,
        actorUserId: String
    ) {
        publish(
            domainType = OutboxDomainType.FACILITATOR_REQUEST,
            domainId = request.id,
            channel = RealtimeChannels.privateUser(request.sessionId, request.userId),
            event = event(
                type = RealtimeEventTypes.FACILITATOR_DECLINED,
                sessionId = request.sessionId,
                actorUserId = actorUserId,
                payload = payload(
                    "decline" to json.encodeToJsonElement(
                        FacilitatorDeclinePayload.serializer(),
                        FacilitatorDeclinePayload(
                            request = request.toRealtimePayload(),
                            session = session.toRealtimePayload()
                        )
                    )
                )
            )
        )
    }

    override suspend fun publishFacilitatorCancelled(
        request: SessionJoinRequestEntity,
        actorUserId: String
    ) {
        publish(
            domainType = OutboxDomainType.FACILITATOR_REQUEST,
            domainId = request.id,
            channel = RealtimeChannels.facilitator(request.sessionId),
            event = event(
                type = RealtimeEventTypes.FACILITATOR_CANCELLED,
                sessionId = request.sessionId,
                actorUserId = actorUserId,
                payload = payload(
                    "request" to json.encodeToJsonElement(
                        SessionJoinRequestPayload.serializer(),
                        request.toRealtimePayload()
                    )
                )
            )
        )
    }

    override suspend fun publishMessageCreated(
        message: MessageEntity,
        senderName: String,
        target: MessageTarget
    ) {
        publishMessageEvent(
            type = RealtimeEventTypes.MESSAGE_CREATED,
            message = message,
            senderName = senderName,
            actorUserId = message.senderUserId,
            channel = messageChannel(message.sessionId, target)
        )
    }

    override suspend fun publishMessageDeleted(
        message: MessageEntity,
        actorUserId: String
    ) {
        publish(
            domainType = OutboxDomainType.MESSAGE,
            domainId = message.id,
            channel = messageChannel(message.sessionId, message.target),
            event = event(
                type = RealtimeEventTypes.MESSAGE_DELETED,
                sessionId = message.sessionId,
                actorUserId = actorUserId,
                payload = payload(
                    "messageDeletion" to json.encodeToJsonElement(
                        MessageDeletionPayload.serializer(),
                        message.toDeletionPayload()
                    )
                )
            )
        )
    }

    override suspend fun publishAnnouncementCreated(
        message: MessageEntity,
        senderName: String,
        actorUserId: String
    ) {
        publishMessageEvent(
            type = RealtimeEventTypes.ANNOUNCEMENT_CREATED,
            message = message,
            senderName = senderName,
            actorUserId = actorUserId,
            channel = RealtimeChannels.public(message.sessionId)
        )
    }

    override suspend fun publishAttachmentAvailable(
        message: MessageEntity,
        attachment: AttachmentEntity,
        actorUserId: String
    ) {
        publishAttachmentEvent(
            type = RealtimeEventTypes.ATTACHMENT_AVAILABLE,
            message = message,
            attachment = attachment,
            actorUserId = actorUserId
        )
    }

    override suspend fun publishAttachmentFailed(
        message: MessageEntity,
        attachment: AttachmentEntity,
        actorUserId: String,
        errorMessage: String?
    ) {
        publishAttachmentEvent(
            type = RealtimeEventTypes.ATTACHMENT_FAILED,
            message = message,
            attachment = attachment,
            actorUserId = actorUserId,
            errorMessage = errorMessage
        )
    }

    override suspend fun publishSignalCreated(
        signal: StatusSignalEntity,
        displayName: String
    ) {
        publish(
            domainType = OutboxDomainType.SIGNAL,
            domainId = signal.id,
            channel = RealtimeChannels.facilitator(signal.sessionId),
            event = event(
                type = RealtimeEventTypes.AAC_SIGNAL_CREATED,
                sessionId = signal.sessionId,
                actorUserId = signal.userId,
                payload = payload(
                    "signal" to json.encodeToJsonElement(
                        SignalCreatedPayload.serializer(),
                        signal.toCreatedPayload(displayName)
                    )
                )
            )
        )
    }

    override suspend fun publishSignalSnoozed(
        signal: StatusSignalEntity,
        facilitatorUserId: String
    ) {
        publish(
            domainType = OutboxDomainType.SIGNAL,
            domainId = signal.id,
            channel = RealtimeChannels.privateUser(
                signal.sessionId,
                facilitatorUserId
            ),
            event = event(
                type = RealtimeEventTypes.AAC_SIGNAL_SNOOZED,
                sessionId = signal.sessionId,
                actorUserId = facilitatorUserId,
                payload = payload(
                    "signalState" to json.encodeToJsonElement(
                        SignalStatePayload.serializer(),
                        signal.toStatePayload(
                            facilitatorUserId = facilitatorUserId
                        )
                    )
                )
            )
        )
    }

    override suspend fun publishSignalCleared(
        signal: StatusSignalEntity,
        actorUserId: String
    ) {
        publish(
            domainType = OutboxDomainType.SIGNAL,
            domainId = signal.id,
            channel = RealtimeChannels.facilitator(signal.sessionId),
            event = event(
                type = RealtimeEventTypes.AAC_SIGNAL_CLEARED,
                sessionId = signal.sessionId,
                actorUserId = actorUserId,
                payload = payload(
                    "signalState" to json.encodeToJsonElement(
                        SignalStatePayload.serializer(),
                        signal.toStatePayload(clearedAt = signal.clearedAt)
                    )
                )
            )
        )
    }

    override suspend fun publishSnapshotRequested(
        sessionId: String,
        requesterUserId: String,
        actorUserId: String
    ) {
        publish(
            domainType = OutboxDomainType.SESSION,
            domainId = sessionId,
            channel = RealtimeChannels.facilitator(sessionId),
            event = event(
                type = RealtimeEventTypes.SESSION_SNAPSHOT_REQUESTED,
                sessionId = sessionId,
                actorUserId = actorUserId,
                payload = payload(
                    "snapshotRequest" to json.encodeToJsonElement(
                        SessionSnapshotRequestPayload.serializer(),
                        SessionSnapshotRequestPayload(requesterUserId)
                    )
                )
            )
        )
    }

    override suspend fun publishSnapshot(
        session: SessionEntity,
        members: List<SessionMemberEntity>,
        requests: List<SessionJoinRequestEntity>,
        messages: List<MessageEntity>,
        requesterUserId: String,
        actorUserId: String
    ) {
        val payload = SessionSnapshotPayload(
            session = session.toRealtimePayload(),
            members = members.map { it.toRealtimePayload() },
            requests = requests.map { it.toRealtimePayload() },
            messages = messages.map { it.toRealtimePayload(senderName = "") }
        )
        publish(
            domainType = OutboxDomainType.SESSION,
            domainId = session.id,
            channel = RealtimeChannels.privateUser(session.id, requesterUserId),
            event = event(
                type = RealtimeEventTypes.SESSION_SNAPSHOT,
                sessionId = session.id,
                actorUserId = actorUserId,
                payload = payload(
                    "snapshot" to json.encodeToJsonElement(
                        SessionSnapshotPayload.serializer(),
                        payload
                    )
                )
            )
        )
    }

    override suspend fun publishDisplayShowMessage(
        session: SessionEntity,
        message: MessageEntity,
        senderName: String,
        actorUserId: String,
        restore: Boolean,
        isPinned: Boolean,
        origin: DisplayCommandOrigin
    ) {
        val payload = DisplayMessagePayload(
            sessionId = session.id,
            message = message.toRealtimePayload(senderName),
            displayMode = session.displayMode.name,
            isPinned = isPinned,
            commandOrigin = origin.name
        )
        publish(
            domainType = OutboxDomainType.DISPLAY,
            domainId = message.id,
            channel = RealtimeChannels.display(session.id),
            event = event(
                type = if (restore) {
                    RealtimeEventTypes.DISPLAY_RESTORE_MESSAGE
                } else {
                    RealtimeEventTypes.DISPLAY_SHOW_MESSAGE
                },
                sessionId = session.id,
                actorUserId = actorUserId,
                payload = payload(
                    "display" to json.encodeToJsonElement(
                        DisplayMessagePayload.serializer(),
                        payload
                    )
                )
            )
        )
    }

    override suspend fun publishDisplayPinState(
        sessionId: String,
        messageId: String,
        actorUserId: String,
        pinned: Boolean,
        displayMode: DisplayMode,
        origin: DisplayCommandOrigin?
    ) {
        publish(
            domainType = OutboxDomainType.DISPLAY,
            domainId = messageId,
            channel = RealtimeChannels.display(sessionId),
            event = event(
                type = if (pinned) {
                    RealtimeEventTypes.DISPLAY_PIN_MESSAGE
                } else {
                    RealtimeEventTypes.DISPLAY_UNPIN_MESSAGE
                },
                sessionId = sessionId,
                actorUserId = actorUserId,
                payload = payload(
                    "displayState" to json.encodeToJsonElement(
                        DisplayStatePayload.serializer(),
                        DisplayStatePayload(
                            sessionId = sessionId,
                            currentMessageId = messageId,
                            isPinned = pinned,
                            displayMode = displayMode.name,
                            commandOrigin = origin?.name
                        )
                    )
                )
            )
        )
    }

    override suspend fun publishDisplayClear(
        sessionId: String,
        actorUserId: String,
        displayMode: DisplayMode,
        origin: DisplayCommandOrigin?
    ) {
        publish(
            domainType = OutboxDomainType.DISPLAY,
            domainId = sessionId,
            channel = RealtimeChannels.display(sessionId),
            event = event(
                type = RealtimeEventTypes.DISPLAY_CLEAR,
                sessionId = sessionId,
                actorUserId = actorUserId,
                payload = payload(
                    "displayState" to json.encodeToJsonElement(
                        DisplayStatePayload.serializer(),
                        DisplayStatePayload(
                            sessionId = sessionId,
                            currentMessageId = null,
                            isPinned = false,
                            displayMode = displayMode.name,
                            commandOrigin = origin?.name
                        )
                    )
                )
            )
        )
    }

    override suspend fun publishDisplayModeChanged(
        sessionId: String,
        actorUserId: String,
        displayMode: DisplayMode,
        currentMessageId: String?,
        isPinned: Boolean,
        origin: DisplayCommandOrigin?
    ) {
        publish(
            domainType = OutboxDomainType.DISPLAY,
            domainId = sessionId,
            channel = RealtimeChannels.display(sessionId),
            event = event(
                type = RealtimeEventTypes.DISPLAY_MODE_CHANGED,
                sessionId = sessionId,
                actorUserId = actorUserId,
                payload = payload(
                    "displayState" to json.encodeToJsonElement(
                        DisplayStatePayload.serializer(),
                        DisplayStatePayload(
                            sessionId = sessionId,
                            currentMessageId = currentMessageId,
                            isPinned = isPinned,
                            displayMode = displayMode.name,
                            commandOrigin = origin?.name
                        )
                    )
                )
            )
        )
    }

    override suspend fun applyIncoming(received: com.example.groupaac.data.realtime.protocol.ReceivedRealtimeEvent): Boolean {
        if (reliabilityStore.hasProcessed(received.event.eventId)) {
            return false
        }
        if (RealtimeReliabilityStore.isExpired(received.event, now = System.currentTimeMillis())) {
            return false
        }

        return transactionRunner.inTransaction {
            when (received.event.type) {
                RealtimeEventTypes.SESSION_STARTED,
                RealtimeEventTypes.SESSION_UPDATED,
                RealtimeEventTypes.SESSION_SETTINGS_CHANGED,
                RealtimeEventTypes.SESSION_ENDED,
                RealtimeEventTypes.SESSION_CANCELLED -> {
                    val sessionPayload = payload<SessionPayload>(
                        received.event.payload,
                        "session"
                    ) ?: return@inTransaction false
                    applySessionEvent(
                        type = received.event.type,
                        session = sessionPayload.toEntity()
                    )
                }

                RealtimeEventTypes.SESSION_SNAPSHOT_REQUESTED -> {
                    val request = payload<SessionSnapshotRequestPayload>(
                        received.event.payload,
                        "snapshotRequest"
                    ) ?: return@inTransaction false
                    val session = sessionDao.getSession(received.event.sessionId)
                        ?: return@inTransaction false
                    publishSnapshot(
                        session = session,
                        members = sessionDao.getMembersForSession(session.id),
                        requests = sessionJoinRequestDao.getRequestsForSession(session.id),
                        messages = messageDao.getMessagesForSession(session.id),
                        requesterUserId = request.requesterUserId,
                        actorUserId = received.event.actorUserId ?: session.hostUserId.orEmpty()
                    )
                }

                RealtimeEventTypes.MEMBER_JOINED,
                RealtimeEventTypes.MEMBER_DISPLAY_NAME_CHANGED,
                RealtimeEventTypes.MEMBER_ROLE_CHANGED -> {
                    val memberPayload = payload<SessionMemberPayload>(
                        received.event.payload,
                        "member"
                    ) ?: return@inTransaction false
                    sessionDao.upsertMember(memberPayload.toEntity())
                }

                RealtimeEventTypes.MEMBER_LEFT,
                RealtimeEventTypes.MEMBER_REMOVED -> {
                    val memberPayload = payload<SessionMemberPayload>(
                        received.event.payload,
                        "member"
                    ) ?: return@inTransaction false
                    sessionDao.deleteMember(
                        sessionId = memberPayload.sessionId,
                        userId = memberPayload.userId
                    )
                }

                RealtimeEventTypes.FACILITATOR_REQUESTED,
                RealtimeEventTypes.FACILITATOR_CANCELLED -> {
                    payload<SessionJoinRequestPayload>(received.event.payload, "request")?.let {
                        sessionJoinRequestDao.upsertRequest(it.toEntity())
                    } ?: return@inTransaction false
                }

                RealtimeEventTypes.FACILITATOR_APPROVED -> {
                    val approval = payload<FacilitatorApprovalPayload>(
                        received.event.payload,
                        "approval"
                    ) ?: return@inTransaction false
                    sessionDao.upsertSession(approval.session.toEntity())
                    sessionDao.upsertMember(approval.member.toEntity())
                    sessionJoinRequestDao.upsertRequest(approval.request.toEntity())
                }

                RealtimeEventTypes.FACILITATOR_DECLINED -> {
                    val decline = payload<FacilitatorDeclinePayload>(
                        received.event.payload,
                        "decline"
                    ) ?: return@inTransaction false
                    sessionDao.upsertSession(decline.session.toEntity())
                    sessionJoinRequestDao.upsertRequest(decline.request.toEntity())
                }

                RealtimeEventTypes.HOST_TRANSFERRED -> {
                    val transfer = payload<HostTransferPayload>(
                        received.event.payload,
                        "hostTransfer"
                    ) ?: return@inTransaction false
                    sessionDao.upsertSession(transfer.session.toEntity())
                    sessionDao.upsertMember(transfer.newHostMember.toEntity())
                }

                RealtimeEventTypes.MESSAGE_CREATED,
                RealtimeEventTypes.ANNOUNCEMENT_CREATED -> {
                    payload<MessagePayload>(received.event.payload, "message")?.let {
                        messageDao.upsertMessage(it.toEntity())
                    } ?: return@inTransaction false
                }

                RealtimeEventTypes.MESSAGE_DELETED -> {
                    val deletion = payload<MessageDeletionPayload>(
                        received.event.payload,
                        "messageDeletion"
                    ) ?: return@inTransaction false
                    messageDao.deleteMessage(deletion.id)
                }

                RealtimeEventTypes.ATTACHMENT_AVAILABLE,
                RealtimeEventTypes.ATTACHMENT_FAILED -> {
                    val statusPayload = payload<AttachmentStatusPayload>(
                        received.event.payload,
                        "attachment"
                    ) ?: return@inTransaction false
                    val existingAttachment = messageDao.getAttachment(
                        statusPayload.attachmentId
                    )
                    if (existingAttachment != null) {
                        messageDao.updateAttachmentSyncState(
                            attachmentId = existingAttachment.id,
                            remoteUri = statusPayload.remoteUri,
                            syncStatus = statusPayload.syncStatus
                        )
                    } else {
                        messageDao.upsertAttachment(
                            AttachmentEntity(
                                id = statusPayload.attachmentId,
                                messageId = statusPayload.messageId,
                                localUri = statusPayload.localUri,
                                mimeType = statusPayload.mimeType,
                                originalName = statusPayload.originalName,
                                remoteUri = statusPayload.remoteUri,
                                syncStatus = statusPayload.syncStatus
                            )
                        )
                    }
                }

                RealtimeEventTypes.AAC_SIGNAL_CREATED -> {
                    val signal = payload<SignalCreatedPayload>(
                        received.event.payload,
                        "signal"
                    ) ?: return@inTransaction false
                    statusSignalDao.upsertSignal(signal.toEntity())
                }

                RealtimeEventTypes.AAC_SIGNAL_SNOOZED -> {
                    val signalState = payload<SignalStatePayload>(
                        received.event.payload,
                        "signalState"
                    ) ?: return@inTransaction false
                    val facilitatorUserId = signalState.facilitatorUserId
                        ?: return@inTransaction false
                    statusSignalDao.upsertSnooze(
                        SignalSnoozeEntity(
                            signalId = signalState.signalId,
                            facilitatorUserId = facilitatorUserId,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }

                RealtimeEventTypes.AAC_SIGNAL_CLEARED -> {
                    val signalState = payload<SignalStatePayload>(
                        received.event.payload,
                        "signalState"
                    ) ?: return@inTransaction false
                    val clearedAt = signalState.clearedAt
                        ?: System.currentTimeMillis()
                    statusSignalDao.clearSignal(
                        signalId = signalState.signalId,
                        clearedAt = clearedAt
                    )
                    statusSignalDao.deleteSnoozesForSignal(signalState.signalId)
                }

                RealtimeEventTypes.DISPLAY_RENDERED,
                RealtimeEventTypes.DISPLAY_RESTORED,
                RealtimeEventTypes.DISPLAY_CLEARED,
                RealtimeEventTypes.DISPLAY_PINNED,
                RealtimeEventTypes.DISPLAY_UNPINNED,
                RealtimeEventTypes.DISPLAY_STATE -> {
                    val state = payload<DisplayStatePayload>(
                        received.event.payload,
                        "displayState"
                    ) ?: return@inTransaction false
                    val current = reliabilityDao.getDisplayState(state.sessionId)
                    if (
                        !reliabilityStore.isDisplayAcknowledgementFresh(
                            current = current,
                            inReplyToEventId = received.event.inReplyToEventId,
                            timetoken = received.timetoken
                        )
                    ) {
                        return@inTransaction false
                    }
                    messageDao.clearDisplayedMessages(state.sessionId)
                    state.currentMessageId?.let { messageDao.markDisplayed(it) }
                    reliabilityDao.upsertDisplayState(
                        DisplayStateEntity(
                            sessionId = state.sessionId,
                            currentMessageId = state.currentMessageId,
                            isPinned = state.isPinned,
                            displayMode = state.mode(),
                            commandOrigin = state.origin(),
                            lastIssuedCommandEventId = if (
                                current?.lastIssuedCommandEventId ==
                                    received.event.inReplyToEventId
                            ) {
                                null
                            } else {
                                current?.lastIssuedCommandEventId
                            },
                            lastAppliedCommandTimetoken = received.timetoken,
                            lastAppliedCommandEventId = received.event.inReplyToEventId
                                ?: received.event.eventId,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }

                RealtimeEventTypes.SESSION_SNAPSHOT -> {
                    val snapshot = payload<SessionSnapshotPayload>(
                        received.event.payload,
                        "snapshot"
                    ) ?: return@inTransaction false
                    sessionDao.upsertSession(snapshot.session.toEntity())
                    snapshot.members.forEach { sessionDao.upsertMember(it.toEntity()) }
                    snapshot.requests.forEach { sessionJoinRequestDao.upsertRequest(it.toEntity()) }
                    snapshot.messages.forEach { messageDao.upsertMessage(it.toEntity()) }
                }

                else -> return@inTransaction false
            }

            reliabilityStore.recordProcessedEvent(
                received = received,
                now = System.currentTimeMillis()
            )
        }
    }

    private suspend fun publish(
        domainType: OutboxDomainType,
        domainId: String,
        channel: String,
        event: RealtimeEvent
    ) {
        val now = System.currentTimeMillis()
        reliabilityStore.enqueueOutboxEvent(
            domainType = domainType,
            domainId = domainId,
            channel = channel,
            event = event,
            now = now
        )
    }

    private fun event(
        type: String,
        sessionId: String,
        actorUserId: String?,
        payload: JsonObject
    ): RealtimeEvent = RealtimeEvent(
        eventId = IdUtils.newId(),
        type = type,
        sessionId = sessionId,
        actorUserId = actorUserId,
        occurredAt = System.currentTimeMillis(),
        payload = payload
    )

    private inline fun <reified T> payload(
        payload: JsonObject,
        key: String
    ): T? {
        val element = payload[key] ?: return null
        return json.decodeFromJsonElement(serializer<T>(), element)
    }

    private suspend fun publishSessionEvent(
        type: String,
        session: SessionEntity,
        actorUserId: String
    ) {
        publish(
            domainType = OutboxDomainType.SESSION,
            domainId = session.id,
            channel = RealtimeChannels.public(session.id),
            event = event(
                type = type,
                sessionId = session.id,
                actorUserId = actorUserId,
                payload = payload(
                    "session" to json.encodeToJsonElement(
                        SessionPayload.serializer(),
                        session.toRealtimePayload()
                    )
                )
            )
        )
    }

    private suspend fun publishMemberEvent(
        type: String,
        session: SessionEntity,
        member: SessionMemberEntity,
        actorUserId: String
    ) {
        publish(
            domainType = OutboxDomainType.MEMBER,
            domainId = "${member.sessionId}:${member.userId}",
            channel = RealtimeChannels.public(session.id),
            event = event(
                type = type,
                sessionId = session.id,
                actorUserId = actorUserId,
                payload = payload(
                    "member" to json.encodeToJsonElement(
                        SessionMemberPayload.serializer(),
                        member.toRealtimePayload()
                    )
                )
            )
        )
    }

    private suspend fun publishMessageEvent(
        type: String,
        message: MessageEntity,
        senderName: String,
        actorUserId: String,
        channel: String
    ) {
        publish(
            domainType = OutboxDomainType.MESSAGE,
            domainId = message.id,
            channel = channel,
            event = event(
                type = type,
                sessionId = message.sessionId,
                actorUserId = actorUserId,
                payload = payload(
                    "message" to json.encodeToJsonElement(
                        MessagePayload.serializer(),
                        message.toRealtimePayload(senderName)
                    )
                )
            )
        )
    }

    private suspend fun publishAttachmentEvent(
        type: String,
        message: MessageEntity,
        attachment: AttachmentEntity,
        actorUserId: String,
        errorMessage: String? = null
    ) {
        publish(
            domainType = OutboxDomainType.MESSAGE,
            domainId = attachment.id,
            channel = messageChannel(message.sessionId, message.target),
            event = event(
                type = type,
                sessionId = message.sessionId,
                actorUserId = actorUserId,
                payload = payload(
                    "attachment" to json.encodeToJsonElement(
                        AttachmentStatusPayload.serializer(),
                        attachment.toStatusPayload(errorMessage)
                    )
                )
            )
        )
    }

    private suspend fun applySessionEvent(
        type: String,
        session: SessionEntity
    ) {
        val existing = sessionDao.getSession(session.id)
        val resolved = when (type) {
            RealtimeEventTypes.SESSION_STARTED -> session.copy(
                status = SessionStatus.LIVE,
                actualStartedAt = session.actualStartedAt
                    ?: existing?.actualStartedAt
                    ?: System.currentTimeMillis(),
                actualEndedAt = null
            )
            RealtimeEventTypes.SESSION_ENDED -> session.copy(
                status = SessionStatus.ENDED,
                actualStartedAt = session.actualStartedAt
                    ?: existing?.actualStartedAt,
                actualEndedAt = session.actualEndedAt
                    ?: System.currentTimeMillis()
            )
            RealtimeEventTypes.SESSION_CANCELLED -> session.copy(
                status = SessionStatus.CANCELLED
            )
            else -> session
        }
        sessionDao.upsertSession(resolved)
    }

    private fun messageChannel(
        sessionId: String,
        target: MessageTarget
    ): String = when (target) {
        MessageTarget.GROUP -> RealtimeChannels.public(sessionId)
        MessageTarget.FACILITATOR,
        MessageTarget.PRIVATE -> RealtimeChannels.facilitator(sessionId)
    }

    private fun payload(vararg entries: Pair<String, JsonElement>): JsonObject {
        return buildJsonObject {
            entries.forEach { (key, value) ->
                put(key, value)
            }
        }
    }
}
