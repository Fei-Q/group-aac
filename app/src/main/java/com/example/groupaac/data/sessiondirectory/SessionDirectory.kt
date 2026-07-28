package com.example.groupaac.data.sessiondirectory

enum class RemoteSessionStatus {
    SCHEDULED,
    LIVE,
    ENDED,
    CANCELLED
}

data class RemoteSessionRecord(
    val sessionId: String,
    val joinCode: String,
    val sessionName: String,
    val hostUid: String,
    val status: RemoteSessionStatus,
    val scheduledStartAt: Long?,
    val scheduledDurationMinutes: Int?,
    val actualStartedAt: Long?,
    val actualEndedAt: Long?,
    val expiresAt: Long?
)

data class CreateRemoteSessionRequest(
    val hostUid: String,
    val name: String,
    val status: RemoteSessionStatus,
    val scheduledStartAt: Long?,
    val scheduledDurationMinutes: Int?
)

sealed interface CreateRemoteSessionResult {
    data class Created(val session: RemoteSessionRecord) : CreateRemoteSessionResult
    data class Failure(val message: String) : CreateRemoteSessionResult
}

data class UpdateRemoteSessionRequest(
    val sessionId: String,
    val hostUid: String,
    val name: String,
    val status: RemoteSessionStatus,
    val scheduledStartAt: Long?,
    val scheduledDurationMinutes: Int?
)

sealed interface UpdateRemoteSessionResult {
    data class Updated(val session: RemoteSessionRecord) : UpdateRemoteSessionResult
    data object NotFound : UpdateRemoteSessionResult
    data object Cancelled : UpdateRemoteSessionResult
    data object Ended : UpdateRemoteSessionResult
    data class Failure(val message: String) : UpdateRemoteSessionResult
}

sealed interface ResolveJoinCodeResult {
    data class Found(
        val session: RemoteSessionRecord
    ) : ResolveJoinCodeResult

    data object NotFound : ResolveJoinCodeResult
    data object Expired : ResolveJoinCodeResult
    data object Cancelled : ResolveJoinCodeResult
    data object Ended : ResolveJoinCodeResult
    data class Failure(val message: String) : ResolveJoinCodeResult
}

data class CloseRemoteSessionRequest(
    val sessionId: String,
    val hostUid: String
)

sealed interface EndRemoteSessionResult {
    data class Ended(val session: RemoteSessionRecord) : EndRemoteSessionResult
    data object NotFound : EndRemoteSessionResult
    data object Cancelled : EndRemoteSessionResult
    data object AlreadyEnded : EndRemoteSessionResult
    data class Failure(val message: String) : EndRemoteSessionResult
}

sealed interface CancelRemoteSessionResult {
    data class Cancelled(val session: RemoteSessionRecord) : CancelRemoteSessionResult
    data object NotFound : CancelRemoteSessionResult
    data object Ended : CancelRemoteSessionResult
    data object AlreadyCancelled : CancelRemoteSessionResult
    data class Failure(val message: String) : CancelRemoteSessionResult
}

interface SessionDirectory {
    suspend fun createSession(
        request: CreateRemoteSessionRequest
    ): CreateRemoteSessionResult

    suspend fun resolveJoinCode(
        joinCode: String,
        requesterUid: String
    ): ResolveJoinCodeResult

    suspend fun updateSession(
        request: UpdateRemoteSessionRequest
    ): UpdateRemoteSessionResult

    suspend fun endSession(
        request: CloseRemoteSessionRequest
    ): EndRemoteSessionResult

    suspend fun cancelSession(
        request: CloseRemoteSessionRequest
    ): CancelRemoteSessionResult
}
