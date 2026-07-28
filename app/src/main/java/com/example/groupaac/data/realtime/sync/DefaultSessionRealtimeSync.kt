package com.example.groupaac.data.realtime.sync

import com.example.groupaac.data.dao.MessageDao
import com.example.groupaac.data.dao.ReliabilityDao
import com.example.groupaac.data.dao.SessionDao
import com.example.groupaac.data.dao.SessionJoinRequestDao
import com.example.groupaac.data.entity.MessageEntity
import com.example.groupaac.data.entity.StatusSignalEntity
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionJoinRequestEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.data.realtime.protocol.RealtimeChannels
import com.example.groupaac.data.realtime.protocol.RealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeEventTypes
import com.example.groupaac.data.realtime.reliability.RealtimeReliabilityStore
import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.model.OutboxDomainType
import com.example.groupaac.util.IdUtils
import kotlinx.serialization.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DefaultSessionRealtimeSync(
    private val sessionDao: SessionDao,
    private val sessionJoinRequestDao: SessionJoinRequestDao,
    private val messageDao: MessageDao,
    private val reliabilityDao: ReliabilityDao,
    private val reliabilityStore: RealtimeReliabilityStore
) : SessionRealtimeSync {
    private val json = Json { ignoreUnknownKeys = true }

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
        publish(
            domainType = OutboxDomainType.SESSION,
            domainId = session.id,
            channel = RealtimeChannels.public(session.id),
            event = event(
                type = RealtimeEventTypes.SESSION_UPDATED,
                sessionId = session.id,
                actorUserId = actorUserId,
                payload = payload(
                    "session" to json.encodeToJsonElement(SessionPayload.serializer(), session.toRealtimePayload())
                )
            )
        )
    }

    override suspend fun publishSessionEnded(
        session: SessionEntity,
        actorUserId: String
    ) {
        publish(
            domainType = OutboxDomainType.SESSION,
            domainId = session.id,
            channel = RealtimeChannels.public(session.id),
            event = event(
                type = RealtimeEventTypes.SESSION_ENDED,
                sessionId = session.id,
                actorUserId = actorUserId,
                payload = payload(
                    "session" to json.encodeToJsonElement(SessionPayload.serializer(), session.toRealtimePayload())
                )
            )
        )
    }

    override suspend fun publishSessionCancelled(
        session: SessionEntity,
        actorUserId: String
    ) {
        publish(
            domainType = OutboxDomainType.SESSION,
            domainId = session.id,
            channel = RealtimeChannels.public(session.id),
            event = event(
                type = RealtimeEventTypes.SESSION_CANCELLED,
                sessionId = session.id,
                actorUserId = actorUserId,
                payload = payload(
                    "session" to json.encodeToJsonElement(SessionPayload.serializer(), session.toRealtimePayload())
                )
            )
        )
    }

    override suspend fun publishMemberJoined(
        session: SessionEntity,
        member: SessionMemberEntity
    ) {
        publish(
            domainType = OutboxDomainType.MEMBER,
            domainId = "${member.sessionId}:${member.userId}",
            channel = RealtimeChannels.public(session.id),
            event = event(
                type = RealtimeEventTypes.MEMBER_JOINED,
                sessionId = session.id,
                actorUserId = member.userId,
                payload = payload(
                    "member" to json.encodeToJsonElement(
                        SessionMemberPayload.serializer(),
                        member.toRealtimePayload()
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
                    "request" to json.encodeToJsonElement(
                        SessionJoinRequestPayload.serializer(),
                        request.toRealtimePayload()
                    )
                )
            )
        )
    }

    override suspend fun publishFacilitatorDeclined(
        request: SessionJoinRequestEntity,
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
                    "request" to json.encodeToJsonElement(
                        SessionJoinRequestPayload.serializer(),
                        request.toRealtimePayload()
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
        val channel = when (target) {
            MessageTarget.GROUP -> RealtimeChannels.public(message.sessionId)
            MessageTarget.FACILITATOR,
            MessageTarget.PRIVATE -> RealtimeChannels.facilitator(message.sessionId)
        }
        publish(
            domainType = OutboxDomainType.MESSAGE,
            domainId = message.id,
            channel = channel,
            event = event(
                type = RealtimeEventTypes.MESSAGE_CREATED,
                sessionId = message.sessionId,
                actorUserId = message.senderUserId,
                payload = payload(
                    "message" to json.encodeToJsonElement(
                        MessagePayload.serializer(),
                        message.toRealtimePayload(senderName)
                    )
                )
            )
        )
    }

    override suspend fun publishSignalCreated(
        signal: StatusSignalEntity,
        displayName: String
    ) {
        publish(
            domainType = OutboxDomainType.SIGNAL,
            domainId = signal.id,
            channel = RealtimeChannels.public(signal.sessionId),
            event = event(
                type = RealtimeEventTypes.AAC_SIGNAL_CREATED,
                sessionId = signal.sessionId,
                actorUserId = signal.userId,
                payload = payload(
                    "signalId" to JsonPrimitive(signal.id),
                    "userId" to JsonPrimitive(signal.userId),
                    "displayName" to JsonPrimitive(displayName),
                    "type" to JsonPrimitive(signal.type.name),
                    "createdAt" to JsonPrimitive(signal.createdAt)
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
        restore: Boolean
    ) {
        val payload = DisplayMessagePayload(
            sessionId = session.id,
            message = message.toRealtimePayload(senderName),
            displayMode = session.displayMode.name,
            isPinned = false
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
        pinned: Boolean
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
                            displayMode = DisplayMode.AUTO_LATEST.name
                        )
                    )
                )
            )
        )
    }

    override suspend fun publishDisplayClear(
        sessionId: String,
        actorUserId: String
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
                            displayMode = DisplayMode.AUTO_LATEST.name
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

        when (received.event.type) {
            RealtimeEventTypes.SESSION_STARTED,
            RealtimeEventTypes.SESSION_UPDATED,
            RealtimeEventTypes.SESSION_ENDED,
            RealtimeEventTypes.SESSION_CANCELLED -> {
                payload<SessionPayload>(received.event.payload, "session")?.let {
                    sessionDao.upsertSession(it.toEntity())
                } ?: return false
            }

            RealtimeEventTypes.MEMBER_JOINED -> {
                payload<SessionMemberPayload>(received.event.payload, "member")?.let {
                    sessionDao.upsertMember(it.toEntity())
                } ?: return false
            }

            RealtimeEventTypes.FACILITATOR_REQUESTED,
            RealtimeEventTypes.FACILITATOR_APPROVED,
            RealtimeEventTypes.FACILITATOR_DECLINED,
            RealtimeEventTypes.FACILITATOR_CANCELLED -> {
                payload<SessionJoinRequestPayload>(received.event.payload, "request")?.let {
                    sessionJoinRequestDao.upsertRequest(it.toEntity())
                } ?: return false
            }

            RealtimeEventTypes.MESSAGE_CREATED,
            RealtimeEventTypes.ANNOUNCEMENT_CREATED -> {
                payload<MessagePayload>(received.event.payload, "message")?.let {
                    messageDao.upsertMessage(it.toEntity())
                } ?: return false
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
                ) ?: return false
                messageDao.clearDisplayedMessages(state.sessionId)
                state.currentMessageId?.let { messageDao.markDisplayed(it) }
                reliabilityStore.applyDisplayStateIfNewer(
                    sessionId = state.sessionId,
                    eventId = received.event.inReplyToEventId ?: received.event.eventId,
                    currentMessageId = state.currentMessageId,
                    isPinned = state.isPinned,
                    displayMode = state.mode(),
                    commandTimetoken = received.timetoken,
                    now = System.currentTimeMillis()
                )
            }

            RealtimeEventTypes.SESSION_SNAPSHOT -> {
                val snapshot = payload<SessionSnapshotPayload>(
                    received.event.payload,
                    "snapshot"
                ) ?: return false
                sessionDao.upsertSession(snapshot.session.toEntity())
                snapshot.members.forEach { sessionDao.upsertMember(it.toEntity()) }
                snapshot.requests.forEach { sessionJoinRequestDao.upsertRequest(it.toEntity()) }
                snapshot.messages.forEach { messageDao.upsertMessage(it.toEntity()) }
            }

            else -> return false
        }

        return reliabilityStore.recordProcessedEvent(
            received = received,
            now = System.currentTimeMillis()
        )
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

    private fun payload(vararg entries: Pair<String, JsonElement>): JsonObject {
        return buildJsonObject {
            entries.forEach { (key, value) ->
                put(key, value)
            }
        }
    }
}
