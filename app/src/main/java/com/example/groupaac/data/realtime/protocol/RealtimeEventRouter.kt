package com.example.groupaac.data.realtime.protocol

enum class RealtimeRouteKind {
    PUBLIC,
    FACILITATOR,
    PRIVATE_USER,
    DISPLAY,
    DISPLAY_EVENTS,
    DISPLAY_CONTROL
}

data class RealtimeRoute(
    val kind: RealtimeRouteKind,
    val sessionId: String?,
    val userId: String? = null,
    val displayId: String? = null
)

object RealtimeEventRouter {
    fun route(channel: String): RealtimeRoute {
        val parts = channel.split(".")
        return when {
            parts.size == 3 && parts[0] == "session" && parts[2] == "public" -> {
                RealtimeRoute(RealtimeRouteKind.PUBLIC, sessionId = parts[1])
            }

            parts.size == 3 && parts[0] == "session" && parts[2] == "facilitator" -> {
                RealtimeRoute(RealtimeRouteKind.FACILITATOR, sessionId = parts[1])
            }

            parts.size == 3 && parts[0] == "session" && parts[2] == "display" -> {
                RealtimeRoute(RealtimeRouteKind.DISPLAY, sessionId = parts[1])
            }

            parts.size == 4 &&
                parts[0] == "session" &&
                parts[2] == "display" &&
                parts[3] == "events" -> {
                RealtimeRoute(RealtimeRouteKind.DISPLAY_EVENTS, sessionId = parts[1])
            }

            parts.size == 3 && parts[0] == "display" && parts[2] == "control" -> {
                RealtimeRoute(
                    RealtimeRouteKind.DISPLAY_CONTROL,
                    sessionId = null,
                    displayId = parts[1]
                )
            }

            parts.size == 3 && parts[0] == "session" -> {
                RealtimeRoute(
                    RealtimeRouteKind.PRIVATE_USER,
                    sessionId = parts[1],
                    userId = parts[2]
                )
            }

            else -> error("Unsupported realtime channel: $channel")
        }
    }
}
