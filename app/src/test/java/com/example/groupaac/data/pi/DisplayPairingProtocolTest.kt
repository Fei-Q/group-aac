package com.example.groupaac.data.pi

import com.example.groupaac.data.realtime.protocol.RealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeChannels
import com.example.groupaac.data.realtime.protocol.RealtimeEventTypes
import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.SessionStatus
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
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

    @Test
    fun invitationValidationNormalizesJoinCodeAndTrimsFields() {
        val invitation =
            SessionInvitationPayload(
                sessionId = " session-1 ",
                joinCode = "12345678",
                sessionName = " Friday Group ",
                hostUserId = " host-1 ",
                displayId = " pi-1 ",
                status = SessionStatus.LIVE,
                displayMode = DisplayMode.AUTO_LATEST,
                actualStartedAt = 1_000L,
                expiresAt = Long.MAX_VALUE
            )

        val validated =
            invitation.validatedForJoin(
                nowProvider = { 2L }
            )

        assertEquals("session-1", validated.sessionId)
        assertEquals("1234-5678", validated.joinCode)
        assertEquals("Friday Group", validated.sessionName)
        assertEquals("host-1", validated.hostUserId)
        assertEquals("pi-1", validated.displayId)
    }

    @Test
    fun invitationValidationRejectsWrongType() {
        try {
            SessionInvitationPayload(
                type = "wrong-type",
                sessionId = "session-1",
                joinCode = "1234-5678",
                sessionName = "Friday Group",
                hostUserId = "host-1",
                displayId = "pi-1",
                status = SessionStatus.LIVE,
                displayMode = DisplayMode.AUTO_LATEST,
                actualStartedAt = 1_000L,
                expiresAt = Long.MAX_VALUE
            ).validatedForJoin(
                nowProvider = { 2L }
            )

            fail("Expected wrong invitation type to fail validation.")
        } catch (expected: IllegalArgumentException) {
            assertEquals(
                "QR code is not a Group AAC session invitation.",
                expected.message
            )
        }
    }

    @Test
    fun sharedFixtureChannelsMatchAndroidBuilders() {
        val channels =
            sharedFixturesRoot()["channels"]!!
                .jsonObject

        assertEquals(
            "display.{displayId}.control",
            channels.requiredString("displayControl")
        )
        assertEquals(
            "display.{displayId}.events",
            channels.requiredString(
                "displayDeviceEvents"
            )
        )
        assertEquals(
            "session.{sessionId}.display",
            channels.requiredString("sessionDisplay")
        )
        assertEquals(
            "session.{sessionId}.display.events",
            channels.requiredString(
                "sessionDisplayEvents"
            )
        )

        assertEquals(
            "display.pi-lab-01.control",
            RealtimeChannels.displayControl("pi-lab-01")
        )
        assertEquals(
            "display.pi-lab-01.events",
            RealtimeChannels.displayDeviceEvents("pi-lab-01")
        )
        assertEquals(
            "session.session-123.display",
            RealtimeChannels.display("session-123")
        )
        assertEquals(
            "session.session-123.display.events",
            RealtimeChannels.displayEvents("session-123")
        )
    }

    @Test
    fun sharedIdlePairingFixtureDecodes() {
        val pairing =
            DisplayPairingPayloadCodec.decode(
                sharedFixtureJson("idlePairing")
            )

        assertEquals("pi-lab-01", pairing.displayId)
        assertEquals(
            "Therapy Room Display",
            pairing.displayName
        )
        assertEquals(
            DISPLAY_PAIRING_TYPE,
            pairing.type
        )
        assertEquals(
            DISPLAY_PROTOCOL_VERSION,
            pairing.protocolVersion
        )
    }

    @Test
    fun sharedInvitationFixtureValidatesAndNormalizes() {
        val invitation =
            SessionInvitationPayloadCodec.decode(
                sharedFixtureJson(
                    "activeSessionInvitation"
                )
            )

        val validated =
            invitation.validatedForJoin {
                1_785_427_200_000L
            }

        assertEquals(
            "1234-5678",
            validated.joinCode
        )
        assertEquals(
            "session-123",
            validated.sessionId
        )
        assertEquals(
            "host-123",
            validated.hostUserId
        )
    }

    @Test
    fun sharedMalformedInvitationFixtureIsRejected() {
        val invitation =
            SessionInvitationPayloadCodec.decode(
                sharedFixtureJson(
                    "malformedInvitation"
                )
            )

        try {
            invitation.validatedForJoin {
                1_785_427_200_000L
            }
            fail("Expected malformed invitation to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertEquals(
                "QR code is not a Group AAC session invitation.",
                expected.message
            )
        }
    }

    @Test
    fun sharedExpiredInvitationFixtureIsRejected() {
        val invitation =
            SessionInvitationPayloadCodec.decode(
                sharedFixtureJson("expiredInvitation")
            )

        try {
            invitation.validatedForJoin {
                1_785_427_200_000L
            }
            fail("Expected expired invitation to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertEquals(
                "This session invitation has expired.",
                expected.message
            )
        }
    }

    @Test
    fun sharedBindCommandFixtureMatchesAndroidBuilder() {
        val pairing =
            DisplayPairingPayloadCodec.decode(
                sharedFixtureJson("idlePairing")
            )
        val invitation =
            SessionInvitationPayloadCodec.decode(
                sharedFixtureJson(
                    "activeSessionInvitation"
                )
            )
        val expected =
            sharedFixtureEvent("bindCommand")

        val event =
            buildDisplayBindSessionEvent(
                eventId = "bind-001",
                requestedByUserId = "host-123",
                pairing = pairing,
                invitation = invitation,
                occurredAt = 1_785_427_200_000L
            )

        assertEquals(expected.eventId, event.eventId)
        assertEquals(expected.type, event.type)
        assertEquals(expected.sessionId, event.sessionId)
        assertEquals(expected.actorUserId, event.actorUserId)
        assertEquals(expected.occurredAt, event.occurredAt)
        assertEquals(expected.inReplyToEventId, event.inReplyToEventId)
        assertEquals(expected.expiresAt, event.expiresAt)
        assertEquals(expected.payload, event.payload)
    }

    @Test
    fun sharedReplyFixturesMatchAndroidReplyParser() {
        val boundReply =
            sharedFixtureEvent("boundReply")
                .toDisplayBindingReplyOrNull(
                    expectedCommandEventId = "bind-001",
                    expectedDisplayId = "pi-lab-01",
                    expectedSessionId = "session-123"
                )

        assertNotNull(boundReply)
        assertEquals(
            RealtimeEventTypes.DISPLAY_BOUND,
            boundReply?.eventType
        )

        val failedReply =
            sharedFixtureEvent("bindFailure")
                .toDisplayBindingReplyOrNull(
                    expectedCommandEventId = "bind-001",
                    expectedDisplayId = "pi-lab-01",
                    expectedSessionId = "session-123"
                )

        assertNotNull(failedReply)
        assertEquals(
            RealtimeEventTypes.DISPLAY_BIND_FAILED,
            failedReply?.eventType
        )
        assertEquals(
            "pairing_nonce_mismatch",
            failedReply?.reason
        )

        val unboundReply =
            sharedFixtureEvent("unboundReply")
                .toDisplayBindingReplyOrNull(
                    expectedCommandEventId = "unbind-001",
                    expectedDisplayId = "pi-lab-01",
                    expectedSessionId = "session-123"
                )

        assertNotNull(unboundReply)
        assertEquals(
            RealtimeEventTypes.DISPLAY_UNBOUND,
            unboundReply?.eventType
        )
    }

    @Test
    fun sharedUnbindCommandFixtureMatchesAndroidBuilder() {
        val expected =
            sharedFixtureEvent("unbindCommand")

        val event =
            buildDisplayUnbindSessionEvent(
                eventId = "unbind-001",
                requestedByUserId = "host-123",
                displayId = "pi-lab-01",
                sessionId = "session-123",
                occurredAt = 1_785_429_000_000L
            )

        assertEquals(expected.eventId, event.eventId)
        assertEquals(expected.type, event.type)
        assertEquals(expected.sessionId, event.sessionId)
        assertEquals(expected.actorUserId, event.actorUserId)
        assertEquals(expected.occurredAt, event.occurredAt)
        assertEquals(expected.inReplyToEventId, event.inReplyToEventId)
        assertEquals(expected.expiresAt, event.expiresAt)
        assertEquals(expected.payload, event.payload)
    }

    private fun sharedFixturesRoot(): JsonObject {
        val file =
            locateRepositoryFile(
                "docs",
                "pi-display-protocol-fixtures.json"
            )

        return Json.parseToJsonElement(
            file.readText()
        ).jsonObject
    }

    private fun sharedFixtureJson(
        name: String
    ): String {
        val fixture =
            sharedFixturesRoot()
                .requiredObject("fixtures")
                .requiredObject(name)

        return Json.encodeToString(
            JsonObject.serializer(),
            fixture
        )
    }

    private fun sharedFixtureEvent(
        name: String
    ): RealtimeEvent {
        val objectValue =
            sharedFixturesRoot()
                .requiredObject("fixtures")
                .requiredObject(name)

        return RealtimeEvent(
            eventId =
                objectValue.requiredString("eventId"),
            type = objectValue.requiredString("type"),
            sessionId =
                objectValue.requiredString("sessionId"),
            actorUserId =
                objectValue["actorUserId"]
                    ?.jsonPrimitive
                    ?.content,
            occurredAt =
                objectValue.requiredLong("occurredAt"),
            inReplyToEventId =
                objectValue.nullableString(
                    "inReplyToEventId"
                ),
            expiresAt =
                objectValue.nullableString(
                    "expiresAt"
                )?.toLong(),
            payload =
                objectValue.requiredObject("payload")
        )
    }

    private fun JsonObject.requiredObject(
        key: String
    ): JsonObject =
        this[key]?.jsonObject
            ?: error("Missing object field: $key")

    private fun JsonObject.requiredString(
        key: String
    ): String =
        this[key]?.jsonPrimitive?.content
            ?: error("Missing string field: $key")

    private fun JsonObject.requiredLong(
        key: String
    ): Long =
        requiredString(key).toLong()

    private fun JsonObject.nullableString(
        key: String
    ): String? {
        val value = this[key] ?: return null
        if (value is JsonNull) {
            return null
        }

        return value.jsonPrimitive.content
    }

    private fun locateRepositoryFile(
        vararg segments: String
    ): File {
        var current =
            File(System.getProperty("user.dir"))
                .absoluteFile

        while (true) {
            val candidate =
                segments.fold(current) { file, segment ->
                    File(file, segment)
                }

            if (candidate.exists()) {
                return candidate
            }

            current =
                current.parentFile
                    ?: error(
                        "Unable to locate ${segments.joinToString("/")}"
                    )
        }
    }
}
