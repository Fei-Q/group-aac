package com.example.groupaac.data.realtime

import com.example.groupaac.data.realtime.protocol.RealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeEventCodec
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
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
        val deferred = async {
            client.observeChannel("session.demo.public").first()
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
    }

    @Test
    fun malformedInputIsHandledWithoutEmittingEvent() = runTest {
        val transport = FakePubNubTransport()
        val client = PubNubSessionRealtimeClient("alice", transport)

        client.observeChannel("session.demo.public")
        transport.emitIncoming(
            channel = "session.demo.public",
            payload = "{not-json",
            publisherUserId = "bob",
            timetoken = 100L
        )

        val event = withTimeoutOrNull(100) {
            client.observeChannel("session.demo.public").first()
        }

        assertNull(event)
        assertTrue(
            client.observeConnectionState().value is RealtimeConnectionState.Failed
        )
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
}

private class FakePubNubTransport(
    private var nextTimetoken: Long = 1L
) : PubNubTransport {
    data class PublishCall(val channel: String, val payload: String)

    val published = mutableListOf<PublishCall>()
    var closed = false
    private val subscribers =
        linkedMapOf<String, (PubNubIncomingMessage) -> Unit>()
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

    override suspend fun fetchHistory(
        channel: String,
        afterTimetoken: Long?,
        limit: Int
    ): List<PubNubIncomingMessage> = emptyList()

    override suspend fun close() {
        closed = true
        subscribers.clear()
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
