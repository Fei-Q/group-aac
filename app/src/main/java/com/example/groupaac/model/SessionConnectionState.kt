package com.example.groupaac.model

sealed interface SessionConnectionState {
    data object Restoring : SessionConnectionState

    data object NotInSession : SessionConnectionState

    data class Joining(
        val requestedCode: String?
    ) : SessionConnectionState

    data class AwaitingApproval(
        val requestId: String,
        val sessionId: String,
        val sessionName: String,
        val requestedAt: Long
    ) : SessionConnectionState

    data class Connected(
        val session: ActiveSession
    ) : SessionConnectionState

    data class Reconnecting(
        val session: ActiveSession,
        val reason: String? = null
    ) : SessionConnectionState

    data class Leaving(
        val session: ActiveSession
    ) : SessionConnectionState
}
