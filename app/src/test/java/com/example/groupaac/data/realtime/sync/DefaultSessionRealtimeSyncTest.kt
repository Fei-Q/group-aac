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
import com.example.groupaac.data.entity.StatusSignalEntity
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
import com.example.groupaac.model.DisplayCommandOrigin
import com.example.groupaac.model.SessionRole
import com.example.groupaac.model.SignalState
import com.example.groupaac.model.SignalType
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
            statusSignalDao = database.statusSignalDao(),
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
            actorUserId = "alice",
            now = Long.MAX_VALUE,
            maxAttempts = RealtimeReliabilityStore.MAX_ATTEMPTS,
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
        database.sessionDao().upsertSession(
            SessionEntity(
                id = "session-1",
                name = "Planning",
                joinCode = "1234-5678",
                hostUserId = "host",
                displayMode = DisplayMode.AUTO_LATEST,
                displayId = "display-1",
                createdAt = 100L
            )
        )
        database.reliabilityDao().upsertDisplayState(
            com.example.groupaac.data.entity.DisplayStateEntity(
                sessionId = "session-1",
                currentMessageId = "msg-1",
                isPinned = false,
                displayMode = DisplayMode.AUTO_LATEST,
                commandOrigin = DisplayCommandOrigin.MANUAL_SHOW,
                lastIssuedCommandEventId = "cmd-1",
                lastPublishedCommandTimetoken = 20L,
                localOptimisticUpdatedAt = 150L
            )
        )
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
        assertNull(displayState?.lastIssuedCommandEventId)
        assertEquals(25L, displayState?.lastPiAppliedCommandTimetoken)
        assertEquals("cmd-1", displayState?.lastAppliedCommandEventId)
    }

    @Test
    fun staleAcknowledgementDoesNotChangeDisplayedMessageFlags() = runTest {
        database.messageDao().upsertMessage(
            MessageEntity(
                id = "msg-current",
                sessionId = "session-1",
                senderUserId = "alice",
                target = MessageTarget.GROUP,
                text = "Current",
                createdAt = 100L
            )
        )
        database.messageDao().upsertMessage(
            MessageEntity(
                id = "msg-stale",
                sessionId = "session-1",
                senderUserId = "alice",
                target = MessageTarget.GROUP,
                text = "Stale",
                createdAt = 101L
            )
        )
        database.reliabilityDao().upsertDisplayState(
            com.example.groupaac.data.entity.DisplayStateEntity(
                sessionId = "session-1",
                currentMessageId = "msg-current",
                isPinned = true,
                displayMode = DisplayMode.AUTO_LATEST,
                commandOrigin = DisplayCommandOrigin.MANUAL_SHOW,
                lastIssuedCommandEventId = "cmd-latest",
                lastPublishedCommandTimetoken = 350L,
                lastPiAppliedCommandTimetoken = 300L,
                lastAppliedCommandEventId = "cmd-prev",
                localOptimisticUpdatedAt = 1_000L
            )
        )
        database.messageDao().markDisplayed("msg-current")

        val payload = buildJsonObject {
            put(
                "displayState",
                Json.encodeToJsonElement(
                    DisplayStatePayload.serializer(),
                    DisplayStatePayload(
                        sessionId = "session-1",
                        currentMessageId = "msg-stale",
                        isPinned = false,
                        displayMode = DisplayMode.AUTO_LATEST.name,
                        commandOrigin = DisplayCommandOrigin.AUTO_LATEST.name
                    )
                )
            )
        }
        val received = ReceivedRealtimeEvent(
            channel = RealtimeChannels.displayEvents("session-1"),
            timetoken = 400L,
            publisherUserId = "display-1",
            event = RealtimeEvent(
                eventId = "ack-stale",
                type = RealtimeEventTypes.DISPLAY_RENDERED,
                sessionId = "session-1",
                actorUserId = "display-1",
                occurredAt = 200L,
                inReplyToEventId = "cmd-older",
                payload = payload
            )
        )

        assertFalse(sync.applyIncoming(received))
        assertEquals(
            "msg-current",
            database.reliabilityDao().getDisplayState("session-1")?.currentMessageId
        )
        assertEquals(
            true,
            database.messageDao().getMessage("msg-current")?.displayedOnMonitor
        )
        assertEquals(
            false,
            database.messageDao().getMessage("msg-stale")?.displayedOnMonitor
        )
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
            actorUserId = "host",
            now = Long.MAX_VALUE,
            maxAttempts = RealtimeReliabilityStore.MAX_ATTEMPTS,
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
                actorUserId = "host",
                now = Long.MAX_VALUE,
                maxAttempts = RealtimeReliabilityStore.MAX_ATTEMPTS,
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
                actorUserId = member.userId,
                now = Long.MAX_VALUE,
                maxAttempts = RealtimeReliabilityStore.MAX_ATTEMPTS,
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

    @Test
    fun signalCreatedAndClearedSyncAcrossDatabases() = runTest {
        val senderDb = inMemoryDatabase()
        val receiverDb = inMemoryDatabase()
        try {
            val senderSync = syncFor(senderDb)
            val receiverSync = syncFor(receiverDb)
            seedSignalUsers(receiverDb)

            val signal = StatusSignalEntity(
                id = "signal-1",
                sessionId = "session-signals",
                userId = "participant",
                type = SignalType.HELP,
                state = SignalState.CURRENT,
                createdAt = 100L
            )
            senderSync.publishSignalCreated(signal, "Participant")
            val createdEvent = senderDb.reliabilityDao().getRetryableOutboxEvents(
                actorUserId = "participant",
                now = Long.MAX_VALUE,
                maxAttempts = RealtimeReliabilityStore.MAX_ATTEMPTS,
                limit = 10
            ).single { it.domainId == signal.id }
            assertEquals(
                RealtimeChannels.facilitator(signal.sessionId),
                createdEvent.channel
            )
            assertTrue(
                receiverSync.applyIncoming(
                    ReceivedRealtimeEvent(
                        channel = createdEvent.channel,
                        timetoken = 600L,
                        publisherUserId = "participant",
                        event = RealtimeEventCodec.decode(createdEvent.serializedEvent)
                    )
                )
            )
            assertEquals(
                SignalState.CURRENT,
                receiverDb.statusSignalDao().getSignal(signal.id)?.state
            )

            senderSync.publishSignalCleared(
                signal = signal.copy(
                    state = SignalState.CLEARED,
                    clearedAt = 125L
                ),
                actorUserId = "participant"
            )
            val clearedEvent = senderDb.reliabilityDao().getRetryableOutboxEvents(
                actorUserId = "participant",
                now = Long.MAX_VALUE,
                maxAttempts = RealtimeReliabilityStore.MAX_ATTEMPTS,
                limit = 10
            ).last()
            assertTrue(
                receiverSync.applyIncoming(
                    ReceivedRealtimeEvent(
                        channel = clearedEvent.channel,
                        timetoken = 601L,
                        publisherUserId = "participant",
                        event = RealtimeEventCodec.decode(clearedEvent.serializedEvent)
                    )
                )
            )
            assertEquals(
                SignalState.CLEARED,
                receiverDb.statusSignalDao().getSignal(signal.id)?.state
            )
        } finally {
            senderDb.close()
            receiverDb.close()
        }
    }

    @Test
    fun snoozeDeliveryOnlyAffectsTargetFacilitator() = runTest {
        val senderDb = inMemoryDatabase()
        val receiverDb = inMemoryDatabase()
        try {
            val senderSync = syncFor(senderDb)
            val receiverSync = syncFor(receiverDb)
            seedSignalUsers(receiverDb)
            receiverDb.statusSignalDao().upsertSignal(
                StatusSignalEntity(
                    id = "signal-private",
                    sessionId = "session-signals",
                    userId = "participant",
                    type = SignalType.HELP,
                    state = SignalState.CURRENT,
                    createdAt = 100L
                )
            )

            senderSync.publishSignalSnoozed(
                signal = StatusSignalEntity(
                    id = "signal-private",
                    sessionId = "session-signals",
                    userId = "participant",
                    type = SignalType.HELP,
                    state = SignalState.CURRENT,
                    createdAt = 100L
                ),
                facilitatorUserId = "facilitator-1"
            )
            val snoozeEvent = senderDb.reliabilityDao().getRetryableOutboxEvents(
                actorUserId = "facilitator-1",
                now = Long.MAX_VALUE,
                maxAttempts = RealtimeReliabilityStore.MAX_ATTEMPTS,
                limit = 10
            ).single()
            assertEquals(
                RealtimeChannels.privateUser("session-signals", "facilitator-1"),
                snoozeEvent.channel
            )

            assertTrue(
                receiverSync.applyIncoming(
                    ReceivedRealtimeEvent(
                        channel = snoozeEvent.channel,
                        timetoken = 700L,
                        publisherUserId = "facilitator-1",
                        event = RealtimeEventCodec.decode(snoozeEvent.serializedEvent)
                    )
                )
            )

            val hiddenForOne = receiverDb.statusSignalDao()
                .observeActiveSignals("session-signals", "facilitator-1")
                .first()
                .single()
            val visibleForTwo = receiverDb.statusSignalDao()
                .observeActiveSignals("session-signals", "facilitator-2")
                .first()
                .single()

            assertEquals(SignalState.SNOOZED, hiddenForOne.state)
            assertEquals(SignalState.CURRENT, visibleForTwo.state)
        } finally {
            senderDb.close()
            receiverDb.close()
        }
    }

    @Test
    fun duplicateLifecycleEventsAreIgnoredAfterFirstApply() = runTest {
        val payload = buildJsonObject {
            put(
                "session",
                Json.encodeToJsonElement(
                    SessionPayload.serializer(),
                    seededSession("session-lifecycle").copy(
                        actualEndedAt = 150L
                    ).toRealtimePayload()
                )
            )
        }
        val received = ReceivedRealtimeEvent(
            channel = RealtimeChannels.public("session-lifecycle"),
            timetoken = 800L,
            publisherUserId = "host",
            event = RealtimeEvent(
                eventId = "evt-ended-1",
                type = RealtimeEventTypes.SESSION_ENDED,
                sessionId = "session-lifecycle",
                actorUserId = "host",
                occurredAt = 151L,
                payload = payload
            )
        )

        assertTrue(sync.applyIncoming(received))
        assertFalse(sync.applyIncoming(received))
        assertEquals(
            800L,
            database.reliabilityDao()
                .getChannelCursor(received.channel)
                ?.lastProcessedTimetoken
        )
    }

    @Test
    fun declaredEventTypesArePublishedAppliedOrReserved() {
        val declared = RealtimeEventTypes::class.java.declaredFields
            .filter { it.type == String::class.java }
            .map { it.get(null) as String }
            .toSet()
        val covered = DefaultSessionRealtimeSync.PUBLISHED_EVENT_TYPES +
            DefaultSessionRealtimeSync.APPLIED_EVENT_TYPES +
            DefaultSessionRealtimeSync.RESERVED_EVENT_TYPES

        assertEquals(declared, covered)
    }

    @Test
    fun sessionStartedReplayPreservesDisplayIdAndExpiresAt() = runTest {
        val payload = buildJsonObject {
            put(
                "session",
                Json.encodeToJsonElement(
                    SessionPayload.serializer(),
                    seededSession("session-started").copy(
                        displayId = "display-1",
                        expiresAt = 9_999L
                    ).toRealtimePayload()
                )
            )
        }
        val received = ReceivedRealtimeEvent(
            channel = RealtimeChannels.public("session-started"),
            timetoken = 900L,
            publisherUserId = "host",
            event = RealtimeEvent(
                eventId = "evt-started-1",
                type = RealtimeEventTypes.SESSION_STARTED,
                sessionId = "session-started",
                actorUserId = "host",
                occurredAt = 101L,
                payload = payload
            )
        )

        assertTrue(sync.applyIncoming(received))

        val stored = database.sessionDao().getSession("session-started")
        assertEquals("display-1", stored?.displayId)
        assertEquals(9_999L, stored?.expiresAt)
    }

    @Test
    fun displayAcknowledgementValidationFindsBoundDisplayAfterSessionSelfReplay() = runTest {
        val sessionPayload = seededSession("session-self-replay").copy(
            displayId = "display-1",
            expiresAt = 8_000L
        ).toRealtimePayload()
        val startedPayload = buildJsonObject {
            put(
                "session",
                Json.encodeToJsonElement(
                    SessionPayload.serializer(),
                    sessionPayload
                )
            )
        }
        val started = ReceivedRealtimeEvent(
            channel = RealtimeChannels.public("session-self-replay"),
            timetoken = 910L,
            publisherUserId = "host",
            event = RealtimeEvent(
                eventId = "evt-session-self-replay",
                type = RealtimeEventTypes.SESSION_STARTED,
                sessionId = "session-self-replay",
                actorUserId = "host",
                occurredAt = 102L,
                payload = startedPayload
            )
        )

        assertTrue(sync.applyIncoming(started))

        database.reliabilityDao().upsertDisplayState(
            com.example.groupaac.data.entity.DisplayStateEntity(
                sessionId = "session-self-replay",
                currentMessageId = "msg-1",
                isPinned = false,
                displayMode = DisplayMode.AUTO_LATEST,
                commandOrigin = DisplayCommandOrigin.MANUAL_SHOW,
                lastIssuedCommandEventId = "cmd-1",
                lastPublishedCommandTimetoken = 920L,
                localOptimisticUpdatedAt = 200L
            )
        )

        val ackPayload = buildJsonObject {
            put(
                "displayState",
                Json.encodeToJsonElement(
                    DisplayStatePayload.serializer(),
                    DisplayStatePayload(
                        sessionId = "session-self-replay",
                        currentMessageId = "msg-1",
                        isPinned = false,
                        displayMode = DisplayMode.AUTO_LATEST.name,
                        commandOrigin = DisplayCommandOrigin.MANUAL_SHOW.name
                    )
                )
            )
        }
        val acknowledgement = ReceivedRealtimeEvent(
            channel = RealtimeChannels.displayEvents("session-self-replay"),
            timetoken = 930L,
            publisherUserId = "display-1",
            event = RealtimeEvent(
                eventId = "ack-cmd-1",
                type = RealtimeEventTypes.DISPLAY_RENDERED,
                sessionId = "session-self-replay",
                actorUserId = "display-1",
                occurredAt = 103L,
                inReplyToEventId = "cmd-1",
                payload = ackPayload
            )
        )

        assertTrue(sync.applyIncoming(acknowledgement))
        assertEquals(
            930L,
            database.reliabilityDao()
                .getDisplayState("session-self-replay")
                ?.lastPiAppliedCommandTimetoken
        )
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
            statusSignalDao = database.statusSignalDao(),
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
        displayId = "display-seeded",
        createdAt = 100L,
        actualStartedAt = 100L,
        expiresAt = 5_000L
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

    private suspend fun seedSignalUsers(database: AppDatabase) {
        database.userDao().upsertUser(
            UserEntity(uid = "participant", displayName = "Participant", createdAt = 1L)
        )
        database.userDao().upsertUser(
            UserEntity(uid = "facilitator-1", displayName = "Facilitator One", createdAt = 1L)
        )
        database.userDao().upsertUser(
            UserEntity(uid = "facilitator-2", displayName = "Facilitator Two", createdAt = 1L)
        )
        database.sessionDao().upsertSession(
            seededSession("session-signals")
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
