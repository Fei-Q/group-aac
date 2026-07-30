package com.example.groupaac.data.sessiondirectory

import kotlinx.serialization.Serializable

@Serializable
data class CreateSessionApiRequest(
    val hostUid: String,
    val name: String,
    val status: String,
    val scheduledStartAt: Long? = null,
    val scheduledDurationMinutes: Int? = null
)

@Serializable
data class ResolveCodeApiRequest(
    val joinCode: String,
    val requesterUid: String
)

@Serializable
data class UpdateSessionApiRequest(
    val hostUid: String,
    val name: String,
    val status: String,
    val scheduledStartAt: Long? = null,
    val scheduledDurationMinutes: Int? = null
)

@Serializable
data class CloseSessionApiRequest(
    val hostUid: String
)

@Serializable
data class SessionApiPayload(
    val sessionId: String,
    val joinCode: String,
    val sessionName: String,
    val hostUid: String,
    val status: String,
    val scheduledStartAt: Long? = null,
    val scheduledDurationMinutes: Int? = null,
    val actualStartedAt: Long? = null,
    val actualEndedAt: Long? = null,
    val expiresAt: Long? = null
)

@Serializable
data class SessionApiResponse(
    val result: String,
    val session: SessionApiPayload? = null,
    val message: String? = null
)

interface GroupAacApi {
    suspend fun createSession(
        request: CreateSessionApiRequest
    ): SessionApiResponse

    suspend fun resolveJoinCode(
        request: ResolveCodeApiRequest
    ): SessionApiResponse

    suspend fun updateSession(
        sessionId: String,
        request: UpdateSessionApiRequest
    ): SessionApiResponse

    suspend fun endSession(
        sessionId: String,
        request: CloseSessionApiRequest
    ): SessionApiResponse

    suspend fun cancelSession(
        sessionId: String,
        request: CloseSessionApiRequest
    ): SessionApiResponse
}
