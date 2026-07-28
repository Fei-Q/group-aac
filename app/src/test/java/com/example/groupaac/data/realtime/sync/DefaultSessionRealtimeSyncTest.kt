package com.example.groupaac.data.realtime.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.groupaac.data.AppDatabase
import com.example.groupaac.data.realtime.FakeSessionRealtimeClient
import com.example.groupaac.data.realtime.RealtimeClientManager
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
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.model.SessionRole
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
    private lateinit var client: FakeSessionRealtimeClient
    private lateinit var sync: DefaultSessionRealtimeSync

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        client = FakeSessionRealtimeClient()
        val manager = object : RealtimeClientManager {
            override suspend fun activateUser(uid: String) = Unit
            override suspend fun deactivateUser() = Unit
            override fun requireClient() = client
        }
        sync = DefaultSessionRealtimeSync(
            sessionDao = database.sessionDao(),
            sessionJoinRequestDao = database.sessionJoinRequestDao(),
            messageDao = database.messageDao(),
            reliabilityStore = RealtimeReliabilityStore(
                database = database,
                reliabilityDao = database.reliabilityDao()
            ),
            realtimeClientManager = manager
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
            status = MessageStatus.SENT
        )

        sync.publishMessageCreated(
            message = message,
            senderName = "Alice",
            target = MessageTarget.GROUP
        )

        val published = client.publishedEvents.single()
        val stored = database.reliabilityDao().getOutboxEvent(
            published.event.eventId
        )
        assertNotNull(stored)
        assertEquals(RealtimeChannels.public("session-1"), stored?.channel)
        assertEquals(1_000L, stored?.acceptedTimetoken)
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
}
