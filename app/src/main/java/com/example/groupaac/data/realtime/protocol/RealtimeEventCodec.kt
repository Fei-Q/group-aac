package com.example.groupaac.data.realtime.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object RealtimeEventCodec {
    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    }

    fun encode(event: RealtimeEvent): String {
        return json.encodeToString(JsonObject.serializer(), event.toJsonObject())
    }

    fun decode(serialized: String): RealtimeEvent {
        return fromJsonObject(json.parseToJsonElement(serialized).jsonObject)
    }

    internal fun fromJsonObject(objectValue: JsonObject): RealtimeEvent {
        return RealtimeEvent(
            eventId = objectValue.requiredString("eventId"),
            type = objectValue.requiredString("type"),
            sessionId = objectValue.requiredString("sessionId"),
            actorUserId = objectValue.optionalString("actorUserId"),
            occurredAt = objectValue.requiredLong("occurredAt"),
            inReplyToEventId = objectValue.optionalString("inReplyToEventId"),
            expiresAt = objectValue.optionalLong("expiresAt"),
            payload = objectValue["payload"]?.jsonObject ?: JsonObject(emptyMap())
        )
    }

    internal fun RealtimeEvent.toJsonObject(): JsonObject {
        return buildJsonObject {
            put("eventId", JsonPrimitive(eventId))
            put("type", JsonPrimitive(type))
            put("sessionId", JsonPrimitive(sessionId))
            actorUserId?.let {
                put("actorUserId", JsonPrimitive(it))
            }
            put("occurredAt", JsonPrimitive(occurredAt))
            inReplyToEventId?.let {
                put("inReplyToEventId", JsonPrimitive(it))
            }
            expiresAt?.let {
                put("expiresAt", JsonPrimitive(it))
            }
            put("payload", payload)
        }
    }

    private fun JsonObject.requiredString(key: String): String {
        return this[key]?.jsonPrimitive?.content
            ?: error("Missing required string field: $key")
    }

    private fun JsonObject.optionalString(key: String): String? {
        val value = this[key] ?: return null
        return if (value is JsonNull) null else value.jsonPrimitive.content
    }

    private fun JsonObject.requiredLong(key: String): Long {
        return this[key]?.jsonPrimitive?.content?.toLongOrNull()
            ?: error("Missing required long field: $key")
    }

    private fun JsonObject.optionalLong(key: String): Long? {
        val value: JsonElement = this[key] ?: return null
        return if (value is JsonNull) null else value.jsonPrimitive.content.toLongOrNull()
    }
}
