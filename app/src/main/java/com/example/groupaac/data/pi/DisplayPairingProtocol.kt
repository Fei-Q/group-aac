package com.example.groupaac.data.pi

import com.example.groupaac.data.realtime.protocol.RealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeEventTypes
import com.example.groupaac.data.sessiondirectory.formatJoinCode
import com.example.groupaac.data.sessiondirectory.normalizeJoinCodeOrNull
import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.SessionStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val DISPLAY_PROTOCOL_VERSION = 1

const val DISPLAY_PAIRING_TYPE =
    "group-aac-display"

const val SESSION_INVITATION_TYPE =
    "group-aac-session"

/**
 * Information shown in the QR code while the Pi is idle.
 */
data class DisplayPairingPayload(
    val type: String = DISPLAY_PAIRING_TYPE,
    val protocolVersion: Int =
        DISPLAY_PROTOCOL_VERSION,
    val displayId: String,
    val displayName: String,
    val pairingNonce: String,
    val pairingExpiresAt: Long
)

/**
 * Session information sent to the Pi during binding.
 *
 * This same model will later become the participant join QR payload.
 */
data class SessionInvitationPayload(
    val type: String = SESSION_INVITATION_TYPE,
    val protocolVersion: Int =
        DISPLAY_PROTOCOL_VERSION,
    val sessionId: String,
    val joinCode: String,
    val sessionName: String,
    val hostUserId: String,
    val displayId: String,
    val status: SessionStatus,
    val displayMode: DisplayMode,
    val actualStartedAt: Long,
    val expiresAt: Long
)

data class DisplayBindingReply(
    val eventType: String,
    val displayId: String,
    val sessionId: String,
    val reason: String? = null
)

fun SessionInvitationPayload.validatedForJoin(
    nowProvider: () -> Long = System::currentTimeMillis
): SessionInvitationPayload {
    require(type == SESSION_INVITATION_TYPE) {
        "QR code is not a Group AAC session invitation."
    }
    require(protocolVersion == DISPLAY_PROTOCOL_VERSION) {
        "Unsupported session invitation version: $protocolVersion"
    }

    val cleanSessionId = sessionId.trim()
    require(cleanSessionId.isNotEmpty()) {
        "Session invitation is missing sessionId."
    }

    val cleanSessionName = sessionName.trim()
    require(cleanSessionName.isNotEmpty()) {
        "Session invitation is missing sessionName."
    }

    val cleanHostUserId = hostUserId.trim()
    require(cleanHostUserId.isNotEmpty()) {
        "Session invitation is missing hostUserId."
    }

    val cleanDisplayId = displayId.trim()
    require(cleanDisplayId.isNotEmpty()) {
        "Session invitation is missing displayId."
    }

    val normalizedJoinCode =
        requireNotNull(
            normalizeJoinCodeOrNull(joinCode)
                ?.let(::formatJoinCode)
        ) {
            "Session invitation has an invalid joinCode."
        }

    require(status == SessionStatus.LIVE) {
        "This session is not currently open."
    }

    val now = nowProvider()

    require(expiresAt > now) {
        "This session invitation has expired."
    }

    require(actualStartedAt > 0L) {
        "Session invitation has an invalid actualStartedAt."
    }

    require(actualStartedAt < expiresAt) {
        "Session invitation has an invalid actualStartedAt."
    }

    return copy(
        sessionId = cleanSessionId,
        joinCode = normalizedJoinCode,
        sessionName = cleanSessionName,
        hostUserId = cleanHostUserId,
        displayId = cleanDisplayId
    )
}

object DisplayPairingPayloadCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encode(
        payload: DisplayPairingPayload
    ): String {
        return json.encodeToString(
            JsonObject.serializer(),
            payload.toJsonObject()
        )
    }

    fun decode(
        serialized: String
    ): DisplayPairingPayload {
        val objectValue =
            json.parseToJsonElement(serialized)
                .jsonObject

        val type =
            objectValue.requiredString("type")

        require(type == DISPLAY_PAIRING_TYPE) {
            "QR code is not a Group AAC display pairing code."
        }

        val version =
            objectValue.requiredInt("protocolVersion")

        require(version == DISPLAY_PROTOCOL_VERSION) {
            "Unsupported display protocol version: $version"
        }

        return DisplayPairingPayload(
            type = type,
            protocolVersion = version,
            displayId =
                objectValue.requiredString("displayId"),
            displayName =
                objectValue.requiredString("displayName"),
            pairingNonce =
                objectValue.requiredString("pairingNonce"),
            pairingExpiresAt =
                objectValue.requiredLong(
                    "pairingExpiresAt"
                )
        )
    }
}

object SessionInvitationPayloadCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encode(
        payload: SessionInvitationPayload
    ): String {
        return json.encodeToString(
            JsonObject.serializer(),
            payload.toJsonObject()
        )
    }

    fun decode(
        serialized: String
    ): SessionInvitationPayload {
        val objectValue =
            json.parseToJsonElement(serialized)
                .jsonObject

        return SessionInvitationPayload(
            type = objectValue.requiredString("type"),
            protocolVersion =
                objectValue.requiredInt(
                    "protocolVersion"
                ),
            sessionId =
                objectValue.requiredString("sessionId"),
            joinCode =
                objectValue.requiredString("joinCode"),
            sessionName =
                objectValue.requiredString("sessionName"),
            hostUserId =
                objectValue.requiredString("hostUserId"),
            displayId =
                objectValue.requiredString("displayId"),
            status =
                SessionStatus.valueOf(
                    objectValue.requiredString(
                        "status"
                    )
                ),
            displayMode =
                DisplayMode.valueOf(
                    objectValue.requiredString(
                        "displayMode"
                    )
                ),
            actualStartedAt =
                objectValue.requiredLong(
                    "actualStartedAt"
                ),
            expiresAt =
                objectValue.requiredLong(
                    "expiresAt"
                )
        )
    }
}

/**
 * Creates the command sent to:
 *
 * display.<displayId>.control
 */
fun buildDisplayBindSessionEvent(
    eventId: String,
    requestedByUserId: String,
    pairing: DisplayPairingPayload,
    invitation: SessionInvitationPayload,
    occurredAt: Long
): RealtimeEvent {
    require(
        pairing.displayId == invitation.displayId
    ) {
        "Pairing display ID does not match invitation display ID."
    }

    return RealtimeEvent(
        eventId = eventId,
        type =
            RealtimeEventTypes.DISPLAY_BIND_SESSION,
        sessionId = invitation.sessionId,
        actorUserId = requestedByUserId,
        occurredAt = occurredAt,
        inReplyToEventId = null,
        expiresAt = minOf(
            pairing.pairingExpiresAt,
            occurredAt + 30_000L
        ),
        payload = buildJsonObject {
            put(
                "protocolVersion",
                JsonPrimitive(
                    DISPLAY_PROTOCOL_VERSION
                )
            )
            put(
                "displayId",
                JsonPrimitive(pairing.displayId)
            )
            put(
                "pairingNonce",
                JsonPrimitive(pairing.pairingNonce)
            )
            put(
                "pairingExpiresAt",
                JsonPrimitive(
                    pairing.pairingExpiresAt
                )
            )
            put(
                "session",
                invitation.toJsonObject()
            )
        }
    )
}

fun buildDisplayUnbindSessionEvent(
    eventId: String,
    requestedByUserId: String,
    displayId: String,
    sessionId: String,
    occurredAt: Long
): RealtimeEvent {
    return RealtimeEvent(
        eventId = eventId,
        type =
            RealtimeEventTypes.DISPLAY_UNBIND_SESSION,
        sessionId = sessionId,
        actorUserId = requestedByUserId,
        occurredAt = occurredAt,
        expiresAt = occurredAt + 30_000L,
        payload = buildJsonObject {
            put(
                "protocolVersion",
                JsonPrimitive(
                    DISPLAY_PROTOCOL_VERSION
                )
            )
            put(
                "displayId",
                JsonPrimitive(displayId)
            )
        }
    )
}

/**
 * Returns a reply only when it belongs to the exact command,
 * session, and Pi being awaited.
 */
fun RealtimeEvent.toDisplayBindingReplyOrNull(
    expectedCommandEventId: String,
    expectedDisplayId: String,
    expectedSessionId: String
): DisplayBindingReply? {
    if (
        inReplyToEventId !=
        expectedCommandEventId
    ) {
        return null
    }

    if (sessionId != expectedSessionId) {
        return null
    }

    if (
        type != RealtimeEventTypes.DISPLAY_BOUND &&
        type !=
        RealtimeEventTypes.DISPLAY_BIND_FAILED &&
        type != RealtimeEventTypes.DISPLAY_UNBOUND
    ) {
        return null
    }

    val replyDisplayId =
        payload.optionalString("displayId")
            ?: return null

    if (replyDisplayId != expectedDisplayId) {
        return null
    }

    return DisplayBindingReply(
        eventType = type,
        displayId = replyDisplayId,
        sessionId = sessionId,
        reason = payload.optionalString("reason")
    )
}

internal fun SessionInvitationPayload.toJsonObject():
        JsonObject {

    return buildJsonObject {
        put(
            "type",
            JsonPrimitive(type)
        )
        put(
            "protocolVersion",
            JsonPrimitive(protocolVersion)
        )
        put(
            "sessionId",
            JsonPrimitive(sessionId)
        )
        put(
            "joinCode",
            JsonPrimitive(joinCode)
        )
        put(
            "sessionName",
            JsonPrimitive(sessionName)
        )
        put(
            "hostUserId",
            JsonPrimitive(hostUserId)
        )
        put(
            "displayId",
            JsonPrimitive(displayId)
        )
        put(
            "status",
            JsonPrimitive(status.name)
        )
        put(
            "displayMode",
            JsonPrimitive(displayMode.name)
        )
        put(
            "actualStartedAt",
            JsonPrimitive(actualStartedAt)
        )
        put(
            "expiresAt",
            JsonPrimitive(expiresAt)
        )
    }
}

private fun DisplayPairingPayload.toJsonObject():
        JsonObject {

    return buildJsonObject {
        put(
            "type",
            JsonPrimitive(type)
        )
        put(
            "protocolVersion",
            JsonPrimitive(protocolVersion)
        )
        put(
            "displayId",
            JsonPrimitive(displayId)
        )
        put(
            "displayName",
            JsonPrimitive(displayName)
        )
        put(
            "pairingNonce",
            JsonPrimitive(pairingNonce)
        )
        put(
            "pairingExpiresAt",
            JsonPrimitive(pairingExpiresAt)
        )
    }
}

private fun JsonObject.requiredString(
    key: String
): String {
    return this[key]
        ?.jsonPrimitive
        ?.content
        ?: error("Missing required field: $key")
}

private fun JsonObject.requiredInt(
    key: String
): Int {
    return requiredString(key)
        .toIntOrNull()
        ?: error("Invalid integer field: $key")
}

private fun JsonObject.requiredLong(
    key: String
): Long {
    return requiredString(key)
        .toLongOrNull()
        ?: error("Invalid long field: $key")
}

private fun JsonObject.optionalString(
    key: String
): String? {
    return this[key]
        ?.jsonPrimitive
        ?.content
}
