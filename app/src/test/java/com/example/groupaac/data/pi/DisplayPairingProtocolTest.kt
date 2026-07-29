package com.example.groupaac.data.pi

import com.example.groupaac.data.realtime.protocol.RealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeEventTypes
import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.SessionStatus
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayPairingProtocolTest {

    @Test
    fun pairingPayloadRoundTrips() {
        val payload =
            DisplayPairingPayload(
                displayId = "pi-1",
                displayName = "Room Display",
                pairingNonce = "nonce-123",
                pairingExpiresAt = 10_000L
            )

        val encoded =
            DisplayPairingPayloadCodec.encode(payload)

        val decoded =
            DisplayPairingPayloadCodec.decode(encoded)

        assertEquals(payload, decoded)
    }

    @Test
    fun bindCommandContainsSessionAndPairingData() {
        val pairing =
            DisplayPairingPayload(
                displayId = "pi-1",
                displayName = "Room Display",
                pairingNonce = "nonce-123",
                pairingExpiresAt = 20_000L
            )

        val invitation =
            SessionInvitationPayload(
                sessionId = "session-1",
                joinCode = "1234-5678",
                sessionName = "Friday Group",
                hostUserId = "host-1",
                displayId = "pi-1",
                status = SessionStatus.LIVE,
                displayMode =
                    DisplayMode.AUTO_LATEST,
                actualStartedAt = 1_000L,
                expiresAt = 50_000L
            )

        val event =
            buildDisplayBindSessionEvent(
                eventId = "bind-1",
                requestedByUserId = "host-1",
                pairing = pairing,
                invitation = invitation,
                occurredAt = 1_000L
            )

        assertEquals(
            RealtimeEventTypes.DISPLAY_BIND_SESSION,
            event.type
        )
        assertEquals(
            "session-1",
            event.sessionId
        )
        assertEquals(
            "host-1",
            event.actorUserId
        )
        assertEquals(
            "pi-1",
            event.payload["displayId"]
                ?.let { it as JsonPrimitive }
                ?.content
        )
    }

    @Test
    fun matchingBoundReplyIsAccepted() {
        val event =
            RealtimeEvent(
                eventId = "ack-1",
                type =
                    RealtimeEventTypes.DISPLAY_BOUND,
                sessionId = "session-1",
                actorUserId = "pi-1",
                occurredAt = 2_000L,
                inReplyToEventId = "bind-1",
                payload = buildJsonObject {
                    put(
                        "displayId",
                        JsonPrimitive("pi-1")
                    )
                }
            )

        val reply =
            event.toDisplayBindingReplyOrNull(
                expectedCommandEventId = "bind-1",
                expectedDisplayId = "pi-1",
                expectedSessionId = "session-1"
            )

        assertNotNull(reply)
        assertEquals(
            RealtimeEventTypes.DISPLAY_BOUND,
            reply?.eventType
        )
    }

    @Test
    fun unrelatedReplyIsIgnored() {
        val event =
            RealtimeEvent(
                eventId = "ack-1",
                type =
                    RealtimeEventTypes.DISPLAY_BOUND,
                sessionId = "session-1",
                actorUserId = "pi-1",
                occurredAt = 2_000L,
                inReplyToEventId = "different-bind",
                payload = buildJsonObject {
                    put(
                        "displayId",
                        JsonPrimitive("pi-1")
                    )
                }
            )

        val reply =
            event.toDisplayBindingReplyOrNull(
                expectedCommandEventId = "bind-1",
                expectedDisplayId = "pi-1",
                expectedSessionId = "session-1"
            )

        assertNull(reply)
    }
}