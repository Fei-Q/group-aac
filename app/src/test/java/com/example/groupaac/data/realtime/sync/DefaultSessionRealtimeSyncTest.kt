package com.example.groupaac.data.realtime.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.groupaac.data.AppDatabase
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.realtime.protocol.RealtimeEventCodec
import com.example.groupaac.data.repository.ImmediateTransactionRunner
import com.example.groupaac.data.repository.SessionRepository
import com.example.groupaac.data.realtime.protocol.RealtimeChannels
import com.example.groupaac.data.realtime.protocol.RealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeEventTypes
import com.example.groupaac.data.realtime.protocol.ReceivedRealtimeEvent
import com.example.groupaac.data.realtime.reliability.RealtimeReliabilityStore
import com.example.groupaac.data.entity.MessageEntity
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionJoinRequestEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.data.realtime.reliability.NoOpOutboxDispatcher
import com.example.groupaac.data.session.ActiveSessionStore
import com.example.groupaac.data.sessiondirectory.FakeSessionDirectory
import com.example.groupaac.model.JoinRequestStatus
import com.example.groupaac.model.MessageStatus
import com.example.groupaac.model.OutboxDomainType
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.ActiveSession
import com.example.groupaac.model.SessionRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DefaultSessionRealtimeSyncTest {
    private lateinit var database: AppDatabase
    private lateinit var sync: DefaultSessionRealtimeSync

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        sync = DefaultSessionRealtimeSync(
            transactionRunner = ImmediateTransactionRunner,
            sessionDao = database.sessionDao(),
            sessionJoinRequestDao = database.sessionJoinRequestDao(),
            messageDao = database.messageDao(),
            reliabilityDao = database.reliabilityDao(),
            reliabilityStore = RealtimeReliabilityStore(
                database = database,
                reliabilityDao = database.reliabilityDao()
            )
        )
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun publishMessageCreatedWritesOutbox() = runTest {
        val message = MessageEntity(
            id = "msg-1",
            sessionId = "session-1",
            senderUserId = "alice",
            target = MessageTarget.GROUP,
            text = "Hello",
            createdAt = 100L,
            status = MessageStatus.ACTIVE
        )

        sync.publishMessageCreated(
            message = message,
            senderName = "Alice",
            target = MessageTarget.GROUP
        )

        val stored = database.reliabilityDao().getRetryableOutboxEvents(
            now = Long.MAX_VALUE,
            limit = 1
        ).single()
        assertNotNull(stored)
        assertEquals(RealtimeChannels.public("session-1"), stored?.channel)
        assertEquals(OutboxDomainType.MESSAGE, stored?.domainType)
        assertEquals("msg-1", stored?.domainId)
    }

    @Test
    fun applySnapshotUpsertsRows() = runTest {
        val snapshot = SessionSnapshotPayload(
            session = SessionEntity(
                id = "session-1",
                name = "Planning",
                joinCode = "1234-5678",
                hostUserId = "host",
                createdAt = 100L
            ).toRealtimePayload(),
            members = listOf(
                SessionMemberEntity(
                    sessionId = "session-1",
                    userId = "alice",
                    displayName = "Alice",
                    role = SessionRole.PARTICIPANT,
                    joinedAt = 100L
                ).toRealtimePayload()
            ),
            requests = listOf(
                SessionJoinRequestEntity(
                    id = "req-1",
                    sessionId = "session-1",
                    userId = "facilitator",
                    displayName = "Facilitator",
                    requestedRole = SessionRole.FACILITATOR,
                    status = JoinRequestStatus.PENDING,
                    requestedAt = 101L
                ).toRealtimePayload()
            ),
            messages = listOf(
                MessageEntity(
                    id = "msg-1",
                    sessionId = "session-1",
                    senderUserId = "alice",
                    target = MessageTarget.GROUP,
                    text = "Hello",
                    createdAt = 102L
                ).toRealtimePayload("Alice")
            )
        )
        val payload = buildJsonObject {
            put(
                "snapshot",
                Json.encodeToJsonElement(
                    SessionSnapshotPayload.serializer(),
                    snapshot
                )
            )
        }
        val received = ReceivedRealtimeEvent(
            channel = RealtimeChannels.privateUser("session-1", "host"),
            timetoken = 10L,
            publisherUserId = "host",
            event = RealtimeEvent(
                eventId = "evt-1",
                type = RealtimeEventTypes.SESSION_SNAPSHOT,
                sessionId = "session-1",
                actorUserId = "host",
                occurredAt = 105L,
                payload = payload
            )
        )

        assertTrue(sync.applyIncoming(received))
        assertNotNull(database.sessionDao().getSession("session-1"))
        assertNotNull(database.sessionDao().getMember("session-1", "alice"))
        assertNotNull(database.sessionJoinRequestDao().getRequestById("req-1"))
        assertNotNull(database.messageDao().getMessage("msg-1"))
        assertEquals(
            10L,
            database.reliabilityDao()
                .getChannelCursor(received.channel)
                ?.lastProcessedTimetoken
        )
    }

    @Test
    fun displayAcknowledgementUpdatesDisplayState() = runTest {
        val payload = buildJsonObject {
            put(
                "displayState",
                Json.encodeToJsonElement(
                    DisplayStatePayload.serializer(),
                    DisplayStatePayload(
                        sessionId = "session-1",
                        currentMessageId = "msg-1",
                        isPinned = true,
                        displayMode = "AUTO_LATEST"
                    )
                )
            )
        }
        val received = ReceivedRealtimeEvent(
            channel = RealtimeChannels.displayEvents("session-1"),
            timetoken = 25L,
            publisherUserId = "display-1",
            event = RealtimeEvent(
                eventId = "evt-display-1",
                type = RealtimeEventTypes.DISPLAY_PINNED,
                sessionId = "session-1",
                actorUserId = "display-1",
                occurredAt = 200L,
                inReplyToEventId = "cmd-1",
                payload = payload
            )
        )

        assertTrue(sync.applyIncoming(received))
        val displayState = database.reliabilityDao().getDisplayState("session-1")
        assertNotNull(displayState)
        assertEquals("msg-1", displayState?.currentMessageId)
        assertEquals(true, displayState?.isPinned)
        assertEquals("cmd-1", displayState?.lastAppliedCommandEventId)
    }

    @Test
    fun duplicateHistoryAndLiveEventsAreDeduplicatedByEventId() = runTest {
        val payload = buildJsonObject {
            put(
                "message",
                Json.encodeToJsonElement(
                    MessagePayload.serializer(),
                    MessageEntity(
                        id = "msg-dup",
                        sessionId = "session-1",
                        senderUserId = "alice",
                        target = MessageTarget.GROUP,
                        text = "Hello",
                        createdAt = 102L
                    ).toRealtimePayload("Alice")
                )
            )
        }
        val received = ReceivedRealtimeEvent(
            channel = RealtimeChannels.public("session-1"),
            timetoken = 10L,
            publisherUserId = "alice",
            event = RealtimeEvent(
                eventId = "evt-dup",
                type = RealtimeEventTypes.MESSAGE_CREATED,
                sessionId = "session-1",
                actorUserId = "alice",
                occurredAt = 105L,
                payload = payload
            )
        )

        assertTrue(sync.applyIncoming(received))
        assertEquals(false, sync.applyIncoming(received))
        assertEquals(1, database.messageDao().observeMessages("session-1").first().size)
    }

    @Test
    fun facilitatorApprovalPayloadIsSelfContained() = runTest {
        val session = seededSession("session-1")
        val request = SessionJoinRequestEntity(
            id = "req-1",
            sessionId = session.id,
            userId = "facilitator",
            displayName = "Facilitator",
            requestedRole = SessionRole.FACILITATOR,
            status = JoinRequestStatus.APPROVED,
            requestedAt = 101L,
            decidedAt = 110L,
            decidedByUserId = "host"
        )
        val member = SessionMemberEntity(
            sessionId = session.id,
            userId = "facilitator",
            displayName = "Facilitator",
            role = SessionRole.FACILITATOR,
            joinedAt = 101L
        )

        sync.publishFacilitatorApproved(
            request = request,
            member = member,
            session = session,
            actorUserId = "host"
        )

        val stored = database.reliabilityDao().getRetryableOutboxEvents(
            now = Long.MAX_VALUE,
            limit = 10
        ).single { it.eventId.isNotBlank() && it.channel == RealtimeChannels.privateUser("session-1", "facilitator") }
        val event = RealtimeEventCodec.decode(stored.serializedEvent)
        val approval = Json.decodeFromJsonElement(
            FacilitatorApprovalPayload.serializer(),
            event.payload.getValue("approval")
        )

        assertEquals(RealtimeEventTypes.FACILITATOR_APPROVED, event.type)
        assertEquals("req-1", approval.request.id)
        assertEquals("facilitator", approval.member.userId)
        assertEquals("session-1", approval.session.id)
    }

    @Test
    fun privateApprovalAppliesAtomicallyAndActivatesWithoutPublicEvent() = runTest {
        val hostDb = inMemoryDatabase()
        val requesterDb = inMemoryDatabase()
        try {
            val hostSync = syncFor(hostDb)
            val requesterSync = syncFor(requesterDb)
            val session = seededSession("session-approval")
            hostDb.sessionDao().upsertSession(session)
            requesterDb.userDao().upsertUser(
                UserEntity(uid = "facilitator", displayName = "Facilitator", createdAt = 1L)
            )
            val request = SessionJoinRequestEntity(
                id = "req-approval",
                sessionId = session.id,
                userId = "facilitator",
                displayName = "Facilitator",
                requestedRole = SessionRole.FACILITATOR,
                status = JoinRequestStatus.APPROVED,
                requestedAt = 101L,
                decidedAt = 110L,
                decidedByUserId = "host"
            )
            val member = SessionMemberEntity(
                sessionId = session.id,
                userId = "facilitator",
                displayName = "Facilitator",
                role = SessionRole.FACILITATOR,
                joinedAt = 101L
            )

            hostSync.publishFacilitatorApproved(
                request = request,
                member = member,
                session = session,
                actorUserId = "host"
            )

            val privateEvent = hostDb.reliabilityDao().getRetryableOutboxEvents(
                now = Long.MAX_VALUE,
                limit = 10
            ).single { it.channel == RealtimeChannels.privateUser(session.id, "facilitator") }
            val received = ReceivedRealtimeEvent(
                channel = privateEvent.channel,
                timetoken = 200L,
                publisherUserId = "host",
                event = RealtimeEventCodec.decode(privateEvent.serializedEvent)
            )

            assertTrue(requesterSync.applyIncoming(received))
            assertNotNull(requesterDb.sessionDao().getSession(session.id))
            assertNotNull(requesterDb.sessionDao().getMember(session.id, "facilitator"))
            assertEquals(
                JoinRequestStatus.APPROVED,
                requesterDb.sessionJoinRequestDao().getRequestById(request.id)?.status
            )
            assertEquals(
                200L,
                requesterDb.reliabilityDao().getChannelCursor(received.channel)?.lastProcessedTimetoken
            )

            val activeStore = FakeActiveSessionStore()
            val requesterRepository = SessionRepository(
                transactionRunner = ImmediateTransactionRunner,
                sessionDao = requesterDb.sessionDao(),
                sessionJoinRequestDao = requesterDb.sessionJoinRequestDao(),
                userDao = requesterDb.userDao(),
                activeSessionStore = activeStore,
                sessionDirectory = FakeSessionDirectory(),
                outboxDispatcher = NoOpOutboxDispatcher,
                sessionRealtimeSync = NoOpSessionRealtimeSync
            )

            val activeSession = requesterRepository.activateApprovedFacilitatorRequest(
                requestId = request.id,
                userId = "facilitator"
            )

            assertNotNull(activeSession)
            assertEquals(SessionRole.FACILITATOR, activeSession?.role)
            assertEquals(session.id, activeStore.activeSessions["facilitator"])
        } finally {
            hostDb.close()
            requesterDb.close()
        }
    }

    @Test
    fun publicMemberJoinedOnlyUpdatesRosterForOtherClients() = runTest {
        val hostDb = inMemoryDatabase()
        val participantDb = inMemoryDatabase()
        try {
            val hostSync = syncFor(hostDb)
            val participantSync = syncFor(participantDb)
            val session = seededSession("session-roster")
            val member = SessionMemberEntity(
                sessionId = session.id,
                userId = "facilitator",
                displayName = "Facilitator",
                role = SessionRole.FACILITATOR,
                joinedAt = 101L
            )

            hostSync.publishMemberJoined(session, member)

            val rosterEvent = hostDb.reliabilityDao().getRetryableOutboxEvents(
                now = Long.MAX_VALUE,
                limit = 10
            ).single { it.channel == RealtimeChannels.public(session.id) }
            val received = ReceivedRealtimeEvent(
                channel = rosterEvent.channel,
                timetoken = 300L,
                publisherUserId = "host",
                event = RealtimeEventCodec.decode(rosterEvent.serializedEvent)
            )

            assertTrue(participantSync.applyIncoming(received))
            val rosterMember = participantDb.sessionDao().getMember(session.id, "facilitator")
            assertNotNull(rosterMember)
            assertEquals(SessionRole.FACILITATOR, rosterMember?.role)
        } finally {
            hostDb.close()
            participantDb.close()
        }
    }

    @Test
    fun duplicatePrivateApprovalDeliveryIsHarmless() = runTest {
        val requesterDb = inMemoryDatabase()
        try {
            val requesterSync = syncFor(requesterDb)
            val received = facilitatorApprovalEvent(
                sessionId = "session-dup",
                requestId = "req-dup",
                userId = "facilitator",
                timetoken = 400L
            )

            assertTrue(requesterSync.applyIncoming(received))
            assertEquals(false, requesterSync.applyIncoming(received))
            assertNotNull(requesterDb.sessionDao().getMember("session-dup", "facilitator"))
            assertEquals(
                400L,
                requesterDb.reliabilityDao().getChannelCursor(received.channel)?.lastProcessedTimetoken
            )
        } finally {
            requesterDb.close()
        }
    }

    @Test
    fun privateDeclineUpdatesRequesterState() = runTest {
        val requesterDb = inMemoryDatabase()
        try {
            val requesterSync = syncFor(requesterDb)
            val session = seededSession("session-decline")
            val payload = buildJsonObject {
                put(
                    "decline",
                    Json.encodeToJsonElement(
                        FacilitatorDeclinePayload.serializer(),
                        FacilitatorDeclinePayload(
                            request = SessionJoinRequestEntity(
                                id = "req-decline",
                                sessionId = session.id,
                                userId = "facilitator",
                                displayName = "Facilitator",
                                requestedRole = SessionRole.FACILITATOR,
                                status = JoinRequestStatus.DECLINED,
                                requestedAt = 101L,
                                decidedAt = 111L,
                                decidedByUserId = "host"
                            ).toRealtimePayload(),
                            session = session.toRealtimePayload()
                        )
                    )
                )
            }
            val received = ReceivedRealtimeEvent(
                channel = RealtimeChannels.privateUser(session.id, "facilitator"),
                timetoken = 500L,
                publisherUserId = "host",
                event = RealtimeEvent(
                    eventId = "evt-decline",
                    type = RealtimeEventTypes.FACILITATOR_DECLINED,
                    sessionId = session.id,
                    actorUserId = "host",
                    occurredAt = 200L,
                    payload = payload
                )
            )

            assertTrue(requesterSync.applyIncoming(received))
            assertEquals(
                JoinRequestStatus.DECLINED,
                requesterDb.sessionJoinRequestDao().getRequestById("req-decline")?.status
            )
            assertNotNull(requesterDb.sessionDao().getSession(session.id))
        } finally {
            requesterDb.close()
        }
    }

    private fun inMemoryDatabase(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    private fun syncFor(database: AppDatabase): DefaultSessionRealtimeSync =
        DefaultSessionRealtimeSync(
            transactionRunner = ImmediateTransactionRunner,
            sessionDao = database.sessionDao(),
            sessionJoinRequestDao = database.sessionJoinRequestDao(),
            messageDao = database.messageDao(),
            reliabilityDao = database.reliabilityDao(),
            reliabilityStore = RealtimeReliabilityStore(
                database = database,
                reliabilityDao = database.reliabilityDao()
            )
        )

    private fun seededSession(id: String): SessionEntity = SessionEntity(
        id = id,
        name = "Planning",
        joinCode = "1234-5678",
        hostUserId = "host",
        displayMode = DisplayMode.AUTO_LATEST,
        createdAt = 100L,
        actualStartedAt = 100L
    )

    private fun facilitatorApprovalEvent(
        sessionId: String,
        requestId: String,
        userId: String,
        timetoken: Long
    ): ReceivedRealtimeEvent {
        val session = seededSession(sessionId)
        val payload = buildJsonObject {
            put(
                "approval",
                Json.encodeToJsonElement(
                    FacilitatorApprovalPayload.serializer(),
                    FacilitatorApprovalPayload(
                        request = SessionJoinRequestEntity(
                            id = requestId,
                            sessionId = sessionId,
                            userId = userId,
                            displayName = "Facilitator",
                            requestedRole = SessionRole.FACILITATOR,
                            status = JoinRequestStatus.APPROVED,
                            requestedAt = 101L,
                            decidedAt = 110L,
                            decidedByUserId = "host"
                        ).toRealtimePayload(),
                        member = SessionMemberEntity(
                            sessionId = sessionId,
                            userId = userId,
                            displayName = "Facilitator",
                            role = SessionRole.FACILITATOR,
                            joinedAt = 101L
                        ).toRealtimePayload(),
                        session = session.toRealtimePayload()
                    )
                )
            )
        }
        return ReceivedRealtimeEvent(
            channel = RealtimeChannels.privateUser(sessionId, userId),
            timetoken = timetoken,
            publisherUserId = "host",
            event = RealtimeEvent(
                eventId = "evt-$requestId",
                type = RealtimeEventTypes.FACILITATOR_APPROVED,
                sessionId = sessionId,
                actorUserId = "host",
                occurredAt = 200L,
                payload = payload
            )
        )
    }
}

private class FakeActiveSessionStore : ActiveSessionStore {
    val activeSessions = linkedMapOf<String, String?>()

    override fun observeActiveSessionId(userId: String): Flow<String?> =
        flowOf(activeSessions[userId])

    override suspend fun setActiveSession(userId: String, sessionId: String) {
        activeSessions[userId] = sessionId
    }

    override suspend fun clearActiveSession(userId: String) {
        activeSessions[userId] = null
    }
}
