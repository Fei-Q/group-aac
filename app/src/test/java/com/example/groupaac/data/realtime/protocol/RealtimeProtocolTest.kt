package com.example.groupaac.data.realtime.protocol

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RealtimeProtocolTest {
    @Test
    fun channelHelpersProduceExpectedNames() {
        assertEquals("session.session123.public", RealtimeChannels.public("session123"))
        assertEquals("session.session123.facilitator", RealtimeChannels.facilitator("session123"))
        assertEquals("session.session123.alice_25", RealtimeChannels.privateUser("session123", "alice_25"))
        assertEquals("session.session123.display", RealtimeChannels.display("session123"))
        assertEquals("session.session123.display.events", RealtimeChannels.displayEvents("session123"))
        assertEquals("display.pi-kiosk.control", RealtimeChannels.displayControl("pi-kiosk"))
        assertEquals("display.pi-kiosk.events", RealtimeChannels.displayDeviceEvents("pi-kiosk"))
    }

    @Test
    fun codecRoundTripsAndOmitsNullFields() {
        val event = RealtimeEvent(
            eventId = "evt-1",
            type = RealtimeEventTypes.MESSAGE_CREATED,
            sessionId = "session123",
            actorUserId = "alice_25",
            occurredAt = 1234L,
            payload = JsonObject(
                mapOf(
                    "messageId" to JsonPrimitive("msg-1"),
                    "text" to JsonPrimitive("hello")
                )
            )
        )

        val encoded = RealtimeEventCodec.encode(event)
        val decoded = RealtimeEventCodec.decode(encoded)

        assertEquals(event, decoded)
        assertFalse(encoded.contains("inReplyToEventId"))
        assertFalse(encoded.contains("expiresAt"))
    }

    @Test
    fun codecKeepsReplyAndExpiryWhenPresent() {
        val event = RealtimeEvent(
            eventId = "evt-2",
            type = RealtimeEventTypes.DISPLAY_RENDERED,
            sessionId = "session123",
            actorUserId = "display-1",
            occurredAt = 2222L,
            inReplyToEventId = "evt-1",
            expiresAt = 3333L,
            payload = JsonObject(mapOf("messageId" to JsonPrimitive("msg-1")))
        )

        val encoded = RealtimeEventCodec.encode(event)
        val decoded = RealtimeEventCodec.decode(encoded)

        assertEquals(event, decoded)
        assertTrue(encoded.contains("inReplyToEventId"))
        assertTrue(encoded.contains("expiresAt"))
    }

    @Test
    fun routerRecognizesSupportedChannels() {
        assertEquals(
            RealtimeRoute(RealtimeRouteKind.PUBLIC, sessionId = "session123"),
            RealtimeEventRouter.route("session.session123.public")
        )
        assertEquals(
            RealtimeRoute(RealtimeRouteKind.FACILITATOR, sessionId = "session123"),
            RealtimeEventRouter.route("session.session123.facilitator")
        )
        assertEquals(
            RealtimeRoute(
                RealtimeRouteKind.PRIVATE_USER,
                sessionId = "session123",
                userId = "alice_25"
            ),
            RealtimeEventRouter.route("session.session123.alice_25")
        )
        assertEquals(
            RealtimeRoute(RealtimeRouteKind.DISPLAY, sessionId = "session123"),
            RealtimeEventRouter.route("session.session123.display")
        )
        assertEquals(
            RealtimeRoute(RealtimeRouteKind.DISPLAY_EVENTS, sessionId = "session123"),
            RealtimeEventRouter.route("session.session123.display.events")
        )
        assertEquals(
            RealtimeRoute(
                RealtimeRouteKind.DISPLAY_CONTROL,
                sessionId = null,
                displayId = "pi-kiosk"
            ),
            RealtimeEventRouter.route("display.pi-kiosk.control")
        )
        assertEquals(
            RealtimeRoute(
                RealtimeRouteKind.DISPLAY_DEVICE_EVENTS,
                sessionId = null,
                displayId = "pi-kiosk"
            ),
            RealtimeEventRouter.route("display.pi-kiosk.events")
        )
    }

    @Test
    fun routerRejectsUnsupportedChannels() {
        try {
            RealtimeEventRouter.route("session.session123.display.control")
            fail("Expected unsupported channel to throw.")
        } catch (_: IllegalStateException) {
            // Expected.
        }
    }
}
