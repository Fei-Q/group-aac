package com.example.groupaac.data.realtime

import com.example.groupaac.data.realtime.protocol.RealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeEventCodec
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PubNubSessionRealtimeClientTest {
    @Test
    fun eventSerializationIsPassedToTransport() = runTest {
        val transport = FakePubNubTransport()
        val client = PubNubSessionRealtimeClient("alice", transport)
        val event = sampleEvent()

        client.publish("session.demo.public", event)

        assertEquals(1, transport.published.size)
        assertEquals("session.demo.public", transport.published.single().channel)
        assertEquals(
            RealtimeEventCodec.encode(event),
            transport.published.single().payload
        )
    }

    @Test
    fun acceptedTimetokenIsReturnedFromPublish() = runTest {
        val transport = FakePubNubTransport(nextTimetoken = 42L)
        val client = PubNubSessionRealtimeClient("alice", transport)

        val timetoken = client.publish("session.demo.public", sampleEvent())

        assertEquals(42L, timetoken)
    }

    @Test
    fun incomingEventIsParsedIntoReceivedRealtimeEvent() = runTest {
        val transport = FakePubNubTransport()
        val client = PubNubSessionRealtimeClient("alice", transport)
        val event = sampleEvent()
        val subscription = client.openSubscription("session.demo.public")
        val deferred = async {
            subscription.events.first()
        }
        runCurrent()

        transport.emitIncoming(
            channel = "session.demo.public",
            payload = RealtimeEventCodec.encode(event),
            publisherUserId = "bob",
            timetoken = 99L
        )

        val received = deferred.await()
        assertEquals("session.demo.public", received.channel)
        assertEquals(99L, received.timetoken)
        assertEquals("bob", received.publisherUserId)
        assertEquals(event, received.event)
        subscription.close()
    }

    @Test
    fun malformedInputIsHandledWithoutEmittingEvent() = runTest {
        val transport = FakePubNubTransport()
        val client = PubNubSessionRealtimeClient("alice", transport)

        val subscription = client.openSubscription("session.demo.public")
        transport.emitIncoming(
            channel = "session.demo.public",
            payload = "{not-json",
            publisherUserId = "bob",
            timetoken = 100L
        )

        val event = withTimeoutOrNull(100) {
            subscription.events.first()
        }

        assertNull(event)
        assertEquals(
            RealtimeConnectionState.Connecting,
            client.observeConnectionState().value
        )
        assertEquals(100L, client.lastMalformedEventDiagnostics.value?.timetoken)
        subscription.close()
    }

    @Test
    fun statusEventsUpdateConnectionState() = runTest {
        val transport = FakePubNubTransport()
        val client = PubNubSessionRealtimeClient("alice", transport)

        transport.emitStatus(PubNubTransportState.Connected)
        assertEquals(
            RealtimeConnectionState.Connected,
            client.observeConnectionState().value
        )

        transport.emitStatus(PubNubTransportState.Reconnecting)
        assertEquals(
            RealtimeConnectionState.Reconnecting,
            client.observeConnectionState().value
        )

        transport.emitStatus(PubNubTransportState.Disconnected)
        assertEquals(
            RealtimeConnectionState.Disconnected,
            client.observeConnectionState().value
        )
    }

    @Test
    fun closeUnsubscribesTransportAndMarksDisconnected() = runTest {
        val transport = FakePubNubTransport()
        val client = PubNubSessionRealtimeClient("alice", transport)

        val subscription = client.openSubscription("session.demo.public")
        client.close()

        assertTrue(transport.closed)
        assertEquals(
            RealtimeConnectionState.Disconnected,
            client.observeConnectionState().value
        )
        subscription.close()
    }

    @Test
    fun closingLastSubscriptionUnsubscribesTransportChannel() = runTest {
        val transport = FakePubNubTransport()
        val client = PubNubSessionRealtimeClient("alice", transport)

        val first = client.openSubscription("session.demo.public")
        val second = client.openSubscription("session.demo.public")

        first.close()
        assertTrue("session.demo.public" in transport.subscribers.keys)

        second.close()
        assertTrue("session.demo.public" in transport.unsubscribedChannels)
    }

    @Test
    fun noCursorUsesLatestSessionPublicHistory() = runTest {
        val transport = FakePubNubTransport().apply {
            enqueueHistoryPage(
                channel = "session.demo.public",
                page = PubNubHistoryPage(
                    messages = listOf(
                        historyMessage(sampleEvent(eventId = "evt-1"), timetoken = 10L),
                        historyMessage(sampleEvent(eventId = "evt-2"), timetoken = 20L)
                    ),
                    nextPage = null
                )
            )
        }
        val client = PubNubSessionRealtimeClient("alice", transport)

        val history = client.fetchHistory("session.demo.public", null, limit = 10)

        assertEquals(listOf(10L, 20L), history.map { it.timetoken })
        assertEquals(
            PubNubHistoryCursor(end = null, limit = 10),
            transport.requestedPages.single()
        )
        assertNull(client.lastHistoryDiagnostics.value)
    }

    @Test
    fun cursorOverlapSkipsInclusiveFacilitatorBoundary() = runTest {
        val transport = FakePubNubTransport().apply {
            enqueueHistoryPage(
                channel = "session.demo.facilitator",
                page = PubNubHistoryPage(
                    messages = listOf(
                        historyMessage(
                            sampleEvent(eventId = "evt-old"),
                            channel = "session.demo.facilitator",
                            timetoken = 50L
                        ),
                        historyMessage(
                            sampleEvent(eventId = "evt-new"),
                            channel = "session.demo.facilitator",
                            timetoken = 60L
                        )
                    ),
                    nextPage = null
                )
            )
        }
        val client = PubNubSessionRealtimeClient("alice", transport)

        val history = client.fetchHistory(
            channel = "session.demo.facilitator",
            afterTimetoken = 50L,
            limit = 10
        )

        assertEquals(listOf(60L), history.map { it.timetoken })
    }

    @Test
    fun onePagePrivateUserHistoryPreservesPublisherUserId() = runTest {
        val transport = FakePubNubTransport().apply {
            enqueueHistoryPage(
                channel = "session.demo.user.alice",
                page = PubNubHistoryPage(
                    messages = listOf(
                        historyMessage(
                            sampleEvent(eventId = "evt-private"),
                            channel = "session.demo.user.alice",
                            publisherUserId = "host-1",
                            timetoken = 70L
                        )
                    ),
                    nextPage = null
                )
            )
        }
        val client = PubNubSessionRealtimeClient("alice", transport)

        val history = client.fetchHistory(
            channel = "session.demo.user.alice",
            afterTimetoken = 60L,
            limit = 1
        )

        assertEquals(1, history.size)
        assertEquals("host-1", history.single().publisherUserId)
        assertEquals("evt-private", history.single().event.eventId)
    }

    @Test
    fun multiplePagesContinueUntilRequestedLimit() = runTest {
        val transport = FakePubNubTransport().apply {
            enqueueHistoryPage(
                channel = "session.demo.display.events",
                page = PubNubHistoryPage(
                    messages = listOf(
                        historyMessage(
                            sampleEvent(eventId = "evt-1"),
                            channel = "session.demo.display.events",
                            timetoken = 100L
                        ),
                        historyMessage(
                            sampleEvent(eventId = "evt-2"),
                            channel = "session.demo.display.events",
                            timetoken = 110L
                        )
                    ),
                    nextPage = PubNubHistoryCursor(start = 110L, end = 90L, limit = 2)
                )
            )
            enqueueHistoryPage(
                channel = "session.demo.display.events",
                page = PubNubHistoryPage(
                    messages = listOf(
                        historyMessage(
                            sampleEvent(eventId = "evt-3"),
                            channel = "session.demo.display.events",
                            timetoken = 120L
                        ),
                        historyMessage(
                            sampleEvent(eventId = "evt-4"),
                            channel = "session.demo.display.events",
                            timetoken = 130L
                        )
                    ),
                    nextPage = null
                )
            )
        }
        val client = PubNubSessionRealtimeClient("alice", transport)

        val history = client.fetchHistory(
            channel = "session.demo.display.events",
            afterTimetoken = 90L,
            limit = 3
        )

        assertEquals(listOf(100L, 110L, 120L), history.map { it.timetoken })
        assertEquals(2, transport.requestedPages.size)
        assertEquals(
            PubNubHistoryCursor(end = 90L, limit = 3),
            transport.requestedPages[0]
        )
        assertEquals(
            PubNubHistoryCursor(start = 110L, end = 90L, limit = 1),
            transport.requestedPages[1]
        )
    }

    @Test
    fun orderingIsNormalizedBeforeReturn() = runTest {
        val transport = FakePubNubTransport().apply {
            enqueueHistoryPage(
                channel = "display.pi-1.events",
                page = PubNubHistoryPage(
                    messages = listOf(
                        historyMessage(
                            sampleEvent(eventId = "evt-3"),
                            channel = "display.pi-1.events",
                            timetoken = 300L
                        ),
                        historyMessage(
                            sampleEvent(eventId = "evt-1"),
                            channel = "display.pi-1.events",
                            timetoken = 100L
                        ),
                        historyMessage(
                            sampleEvent(eventId = "evt-2"),
                            channel = "display.pi-1.events",
                            timetoken = 200L
                        )
                    ),
                    nextPage = null
                )
            )
        }
        val client = PubNubSessionRealtimeClient("alice", transport)

        val history = client.fetchHistory(
            channel = "display.pi-1.events",
            afterTimetoken = null,
            limit = 10
        )

        assertEquals(listOf(100L, 200L, 300L), history.map { it.timetoken })
    }

    @Test
    fun duplicateBoundaryAcrossPagesIsDeduplicated() = runTest {
        val shared = historyMessage(
            event = sampleEvent(eventId = "evt-shared"),
            channel = "session.demo.public",
            timetoken = 210L
        )
        val transport = FakePubNubTransport().apply {
            enqueueHistoryPage(
                channel = "session.demo.public",
                page = PubNubHistoryPage(
                    messages = listOf(
                        historyMessage(
                            sampleEvent(eventId = "evt-1"),
                            channel = "session.demo.public",
                            timetoken = 200L
                        ),
                        shared
                    ),
                    nextPage = PubNubHistoryCursor(start = 210L, end = 190L, limit = 2)
                )
            )
            enqueueHistoryPage(
                channel = "session.demo.public",
                page = PubNubHistoryPage(
                    messages = listOf(
                        shared,
                        historyMessage(sampleEvent(eventId = "evt-3"), timetoken = 220L)
                    ),
                    nextPage = null
                )
            )
        }
        val client = PubNubSessionRealtimeClient("alice", transport)

        val history = client.fetchHistory("session.demo.public", 190L, limit = 10)

        assertEquals(listOf(200L, 210L, 220L), history.map { it.timetoken })
    }

    @Test
    fun malformedPayloadIsQuarantinedWhileValidMessagesContinue() = runTest {
        val transport = FakePubNubTransport().apply {
            enqueueHistoryPage(
                channel = "session.demo.display.events",
                page = PubNubHistoryPage(
                    messages = listOf(
                        historyMessage(
                            sampleEvent(eventId = "evt-1"),
                            channel = "session.demo.display.events",
                            timetoken = 10L
                        ),
                        PubNubIncomingMessage(
                            channel = "session.demo.display.events",
                            payload = "{bad-json",
                            publisherUserId = "display-1",
                            timetoken = 11L
                        ),
                        historyMessage(
                            sampleEvent(eventId = "evt-2"),
                            channel = "session.demo.display.events",
                            timetoken = 12L
                        )
                    ),
                    nextPage = null
                )
            )
        }
        val client = PubNubSessionRealtimeClient("alice", transport)

        val history = client.fetchHistory(
            channel = "session.demo.display.events",
            afterTimetoken = null,
            limit = 10
        )

        assertEquals(listOf(10L, 12L), history.map { it.timetoken })
        assertEquals(
            listOf(11L),
            client.lastHistoryDiagnostics.value
                ?.quarantinedMessages
                ?.map { it.timetoken }
        )
    }

    @Test
    fun expiredEventRemainsAvailableForDownstreamFiltering() = runTest {
        val transport = FakePubNubTransport().apply {
            enqueueHistoryPage(
                channel = "display.pi-1.events",
                page = PubNubHistoryPage(
                    messages = listOf(
                        historyMessage(
                            sampleEvent(eventId = "evt-expired", expiresAt = 1L),
                            channel = "display.pi-1.events",
                            timetoken = 400L
                        )
                    ),
                    nextPage = null
                )
            )
        }
        val client = PubNubSessionRealtimeClient("alice", transport)

        val history = client.fetchHistory("display.pi-1.events", null, limit = 10)

        assertEquals(listOf("evt-expired"), history.map { it.event.eventId })
        assertEquals(listOf(1L), history.map { it.event.expiresAt })
    }

    @Test
    fun cancellationFromHistoryTransportPropagates() = runTest {
        val transport = FakePubNubTransport().apply {
            historyThrowable = CancellationException("cancel history")
        }
        val client = PubNubSessionRealtimeClient("alice", transport)

        try {
            client.fetchHistory("session.demo.public", null, limit = 10)
            fail("Expected cancellation to propagate.")
        } catch (error: CancellationException) {
            assertEquals("cancel history", error.message)
        }
    }

    @Test
    fun networkFailureFromHistoryTransportPropagates() = runTest {
        val transport = FakePubNubTransport().apply {
            historyThrowable = IOException("offline")
        }
        val client = PubNubSessionRealtimeClient("alice", transport)

        try {
            client.fetchHistory("session.demo.public", null, limit = 10)
            fail("Expected network failure to propagate.")
        } catch (error: IOException) {
            assertEquals("offline", error.message)
        }
    }
}

private class FakePubNubTransport(
    private var nextTimetoken: Long = 1L
) : PubNubTransport {
    data class PublishCall(val channel: String, val payload: String)

    val published = mutableListOf<PublishCall>()
    val requestedPages = mutableListOf<PubNubHistoryCursor>()
    val unsubscribedChannels = mutableListOf<String>()
    var closed = false
    var historyThrowable: Throwable? = null
    val subscribers =
        linkedMapOf<String, (PubNubIncomingMessage) -> Unit>()
    private val historyPages =
        linkedMapOf<String, ArrayDeque<PubNubHistoryPage>>()
    private var statusListener: ((PubNubTransportState) -> Unit)? = null

    override suspend fun publish(channel: String, payload: String): Long {
        published += PublishCall(channel, payload)
        return nextTimetoken++
    }

    override fun subscribe(
        channel: String,
        onMessage: (PubNubIncomingMessage) -> Unit
    ) {
        subscribers[channel] = onMessage
    }

    override fun setStatusListener(listener: (PubNubTransportState) -> Unit) {
        statusListener = listener
    }

    override fun unsubscribe(channel: String) {
        unsubscribedChannels += channel
        subscribers.remove(channel)
    }

    override suspend fun fetchHistoryPage(
        channel: String,
        page: PubNubHistoryCursor
    ): PubNubHistoryPage {
        historyThrowable?.let { throw it }
        requestedPages += page
        return historyPages[channel]?.removeFirstOrNull()
            ?: PubNubHistoryPage(emptyList(), nextPage = null)
    }

    override suspend fun close() {
        closed = true
        subscribers.clear()
    }

    fun enqueueHistoryPage(
        channel: String,
        page: PubNubHistoryPage
    ) {
        historyPages.getOrPut(channel) { ArrayDeque() }
            .addLast(page)
    }

    fun emitIncoming(
        channel: String,
        payload: String,
        publisherUserId: String?,
        timetoken: Long
    ) {
        subscribers[channel]?.invoke(
            PubNubIncomingMessage(
                channel = channel,
                payload = payload,
                publisherUserId = publisherUserId,
                timetoken = timetoken
            )
        )
    }

    fun emitStatus(state: PubNubTransportState) {
        statusListener?.invoke(state)
    }
}

private fun sampleEvent(): RealtimeEvent = RealtimeEvent(
    eventId = "evt-1",
    type = "message.created",
    sessionId = "session.demo",
    actorUserId = "alice",
    occurredAt = 1234L,
    payload = JsonObject(
        mapOf("text" to JsonPrimitive("hello"))
    )
)

private fun sampleEvent(
    eventId: String,
    expiresAt: Long? = null
): RealtimeEvent = RealtimeEvent(
    eventId = eventId,
    type = "message.created",
    sessionId = "session.demo",
    actorUserId = "alice",
    occurredAt = 1234L,
    expiresAt = expiresAt,
    payload = JsonObject(
        mapOf("text" to JsonPrimitive(eventId))
    )
)

private fun historyMessage(
    event: RealtimeEvent,
    channel: String = "session.demo.public",
    publisherUserId: String? = event.actorUserId,
    timetoken: Long
): PubNubIncomingMessage = PubNubIncomingMessage(
    channel = channel,
    payload = RealtimeEventCodec.encode(event),
    publisherUserId = publisherUserId,
    timetoken = timetoken
)
