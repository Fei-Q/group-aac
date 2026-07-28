package com.example.groupaac.data.realtime.reliability

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.groupaac.data.AppDatabase
import com.example.groupaac.data.realtime.protocol.ReceivedRealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeEvent
import com.example.groupaac.model.DisplayMode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RealtimeReliabilityStoreTest {
    private lateinit var database: AppDatabase
    private lateinit var store: RealtimeReliabilityStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        store = RealtimeReliabilityStore(database, database.reliabilityDao())
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun retryDelayCapsAtThirtySeconds() {
        assertEquals(1_000L, RealtimeReliabilityStore.nextRetryDelayMillis(1))
        assertEquals(2_000L, RealtimeReliabilityStore.nextRetryDelayMillis(2))
        assertEquals(4_000L, RealtimeReliabilityStore.nextRetryDelayMillis(3))
        assertEquals(8_000L, RealtimeReliabilityStore.nextRetryDelayMillis(4))
        assertEquals(30_000L, RealtimeReliabilityStore.nextRetryDelayMillis(5))
        assertEquals(30_000L, RealtimeReliabilityStore.nextRetryDelayMillis(99))
    }

    @Test
    fun processedEventsAreDeduplicatedByEventId() = runTest {
        val received = receivedEvent("evt-1", 100L)

        assertTrue(store.recordProcessedEvent(received, now = 200L))
        assertFalse(store.recordProcessedEvent(received, now = 300L))
        assertTrue(store.hasProcessed("evt-1"))
        assertEquals(
            100L,
            database.reliabilityDao()
                .getChannelCursor(received.channel)
                ?.lastProcessedTimetoken
        )
    }

    @Test
    fun staleDisplayCommandsAreRejected() = runTest {
        assertTrue(
            store.applyDisplayStateIfNewer(
                sessionId = "session123",
                eventId = "evt-1",
                currentMessageId = "msg-1",
                isPinned = false,
                displayMode = DisplayMode.AUTO_LATEST,
                commandTimetoken = 100L,
                now = 1_000L
            )
        )

        assertFalse(
            store.applyDisplayStateIfNewer(
                sessionId = "session123",
                eventId = "evt-older",
                currentMessageId = "msg-older",
                isPinned = true,
                displayMode = DisplayMode.APPROVAL_REQUIRED,
                commandTimetoken = 100L,
                now = 1_001L
            )
        )

        assertEquals(
            "msg-1",
            database.reliabilityDao().getDisplayState("session123")?.currentMessageId
        )
    }

    @Test
    fun expiredEventsAreExcludedFromRetryableResults() = runTest {
        store.enqueueOutboxEvent(
            channel = "session.session123.public",
            event = event("evt-live", expiresAt = null),
            now = 1_000L
        )
        store.enqueueOutboxEvent(
            channel = "session.session123.public",
            event = event("evt-expired", expiresAt = 900L),
            now = 1_000L
        )

        val retryable = store.getRetryableEvents(now = 1_000L, limit = 10)

        assertEquals(listOf("evt-live"), retryable.map { it.eventId })
    }

    private fun event(eventId: String, expiresAt: Long?): RealtimeEvent {
        return RealtimeEvent(
            eventId = eventId,
            type = "message.created",
            sessionId = "session123",
            actorUserId = "alice",
            occurredAt = 100L,
            expiresAt = expiresAt,
            payload = JsonObject(mapOf("messageId" to JsonPrimitive("msg-1")))
        )
    }

    private fun receivedEvent(eventId: String, timetoken: Long): ReceivedRealtimeEvent {
        return ReceivedRealtimeEvent(
            channel = "session.session123.public",
            timetoken = timetoken,
            publisherUserId = "alice",
            event = event(eventId, expiresAt = null)
        )
    }
}
