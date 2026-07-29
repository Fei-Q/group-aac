package com.example.groupaac.data.pi

import com.example.groupaac.data.pi.DisplayCommand
import com.example.groupaac.data.pi.PiJoinRequest
import com.example.groupaac.data.pi.PiMessagePayload
import com.example.groupaac.data.pi.PiSessionEvent
import com.example.groupaac.data.pi.PiSignalPayload
import com.example.groupaac.data.realtime.RealtimeConnectionState
import com.example.groupaac.data.realtime.SessionRealtimeClient
import com.example.groupaac.data.realtime.protocol.ReceivedRealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeChannels
import com.example.groupaac.data.realtime.protocol.RealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeEventTypes
import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayBindingCoordinatorTest {

    @Test
    fun matchingPiAcknowledgementCompletesBinding() =
        runTest {

            val client =
                AutoReplyRealtimeClient()

            val coordinator =
                PubNubDisplayBindingCoordinator(
                    clientProvider = { client },
                    nowProvider = { 1_000L },
                    acknowledgementTimeoutMillis =
                        1_000L
                )

            val result =
                coordinator.bind(
                    pairing =
                        DisplayPairingPayload(
                            displayId = "pi-1",
                            displayName =
                                "Room Display",
                            pairingNonce =
                                "nonce-1",
                            pairingExpiresAt =
                                10_000L
                        ),
                    invitation =
                        SessionInvitationPayload(
                            sessionId =
                                "session-1",
                            joinCode =
                                "1234-5678",
                            sessionName =
                                "Friday Group",
                            hostUserId =
                                "host-1",
                            displayId =
                                "pi-1",
                            status =
                                SessionStatus.LIVE,
                            displayMode =
                                DisplayMode.AUTO_LATEST,
                            actualStartedAt =
                                1_000L,
                            expiresAt =
                                100_000L
                        ),
                    requestedByUserId =
                        "host-1"
                )

            assertTrue(
                result is
                        DisplayBindingResult.Bound
            )
        }

    @Test
    fun expiredPairingIsRejectedBeforePublish() =
        runTest {

            val client =
                AutoReplyRealtimeClient()

            val coordinator =
                PubNubDisplayBindingCoordinator(
                    clientProvider = { client },
                    nowProvider = { 20_000L }
                )

            val result =
                coordinator.bind(
                    pairing =
                        DisplayPairingPayload(
                            displayId = "pi-1",
                            displayName =
                                "Room Display",
                            pairingNonce =
                                "nonce-1",
                            pairingExpiresAt =
                                10_000L
                        ),
                    invitation =
                        SessionInvitationPayload(
                            sessionId =
                                "session-1",
                            joinCode =
                                "1234-5678",
                            sessionName =
                                "Friday Group",
                            hostUserId =
                                "host-1",
                            displayId =
                                "pi-1",
                            status =
                                SessionStatus.LIVE,
                            displayMode =
                                DisplayMode.AUTO_LATEST,
                            actualStartedAt =
                                20_000L,
                            expiresAt =
                                100_000L
                        ),
                    requestedByUserId =
                        "host-1"
                )

            assertTrue(
                result is
                        DisplayBindingResult
                        .PairingExpired
            )

            assertTrue(
                client.publishedEvents.isEmpty()
            )
        }

    @Test
    fun bindPropagatesCancellation() = runTest {
        val client =
            object : AutoReplyRealtimeClient() {
                override suspend fun publish(
                    channel: String,
                    event: RealtimeEvent
                ): Long {
                    throw CancellationException(
                        "cancel bind"
                    )
                }
            }

        val coordinator =
            PubNubDisplayBindingCoordinator(
                clientProvider = { client },
                nowProvider = { 1_000L }
            )

        try {
            coordinator.bind(
                pairing =
                    DisplayPairingPayload(
                        displayId = "pi-1",
                        displayName = "Room Display",
                        pairingNonce = "nonce-1",
                        pairingExpiresAt = 10_000L
                    ),
                invitation =
                    SessionInvitationPayload(
                        sessionId = "session-1",
                        joinCode = "1234-5678",
                        sessionName = "Friday Group",
                        hostUserId = "host-1",
                        displayId = "pi-1",
                        status = SessionStatus.LIVE,
                        displayMode = DisplayMode.AUTO_LATEST,
                        actualStartedAt = 1_000L,
                        expiresAt = 100_000L
                    ),
                requestedByUserId = "host-1"
            )
        } catch (expected: CancellationException) {
            assertEquals("cancel bind", expected.message)
            return@runTest
        }

        throw AssertionError("Expected cancellation to propagate.")
    }
}

private open class AutoReplyRealtimeClient :
    SessionRealtimeClient {

    val publishedEvents =
        mutableListOf<ReceivedRealtimeEvent>()

    private val events =
        MutableSharedFlow<ReceivedRealtimeEvent>(
            extraBufferCapacity = 16
        )

    private val connectionState =
        MutableStateFlow<RealtimeConnectionState>(
            RealtimeConnectionState.Connected
        )

    private var nextTimetoken =
        1_000L

    override suspend fun publish(
        channel: String,
        event: RealtimeEvent
    ): Long {
        val timetoken =
            nextTimetoken++

        publishedEvents +=
            ReceivedRealtimeEvent(
                channel = channel,
                timetoken = timetoken,
                publisherUserId =
                    event.actorUserId,
                event = event
            )

        if (
            event.type ==
            RealtimeEventTypes
                .DISPLAY_BIND_SESSION
        ) {
            val displayId =
                event.payload["displayId"]
                    ?.jsonPrimitive
                    ?.content
                    ?: error(
                        "Missing display ID."
                    )

            val reply =
                RealtimeEvent(
                    eventId =
                        "ack-${event.eventId}",
                    type =
                        RealtimeEventTypes
                            .DISPLAY_BOUND,
                    sessionId =
                        event.sessionId,
                    actorUserId =
                        displayId,
                    occurredAt =
                        nextTimetoken,
                    inReplyToEventId =
                        event.eventId,
                    payload =
                        buildJsonObject {
                            put(
                                "displayId",
                                JsonPrimitive(
                                    displayId
                                )
                            )
                        }
                )

            events.emit(
                ReceivedRealtimeEvent(
                    channel =
                        RealtimeChannels
                            .displayDeviceEvents(
                                displayId
                            ),
                    timetoken =
                        nextTimetoken++,
                    publisherUserId =
                        displayId,
                    event = reply
                )
            )
        }

        return timetoken
    }

    override fun observeChannel(
        channel: String
    ): Flow<ReceivedRealtimeEvent> {
        return events.filter {
            it.channel == channel
        }
    }

    override suspend fun fetchHistory(
        channel: String,
        afterTimetoken: Long?,
        limit: Int
    ): List<ReceivedRealtimeEvent> =
        emptyList()

    override fun observeConnectionState():
            StateFlow<RealtimeConnectionState> =
        connectionState

    override suspend fun joinSession(
        request: PiJoinRequest
    ) = Unit

    override suspend fun sendMessage(
        payload: PiMessagePayload
    ) = Unit

    override suspend fun sendSignal(
        payload: PiSignalPayload
    ) = Unit

    override suspend fun sendDisplayCommand(
        command: DisplayCommand
    ) = Unit

    override fun observeSessionEvents(
        sessionId: String
    ): Flow<PiSessionEvent> =
        flowOf(PiSessionEvent.Connected)

    override suspend fun close() = Unit
}
