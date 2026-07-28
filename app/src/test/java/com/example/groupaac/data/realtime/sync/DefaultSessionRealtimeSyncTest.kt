package com.example.groupaac.data.realtime.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.groupaac.data.AppDatabase
import com.example.groupaac.data.realtime.protocol.RealtimeChannels
import com.example.groupaac.data.realtime.protocol.RealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeEventTypes
import com.example.groupaac.data.realtime.protocol.ReceivedRealtimeEvent
import com.example.groupaac.data.realtime.reliability.RealtimeReliabilityStore
import com.example.groupaac.data.entity.MessageEntity
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionJoinRequestEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.model.JoinRequestStatus
import com.example.groupaac.model.MessageStatus
import com.example.groupaac.model.OutboxDomainType
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.model.SessionRole
import kotlinx.coroutines.flow.first
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
}
