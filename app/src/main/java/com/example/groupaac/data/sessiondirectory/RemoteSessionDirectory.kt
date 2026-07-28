package com.example.groupaac.data.sessiondirectory

class RemoteSessionDirectory(
    private val api: GroupAacApi
) : SessionDirectory {
    override suspend fun createSession(
        request: CreateRemoteSessionRequest
    ): CreateRemoteSessionResult {
        val response = api.createSession(
            CreateSessionApiRequest(
                hostUid = request.hostUid,
                name = request.name,
                status = request.status.name,
                scheduledStartAt = request.scheduledStartAt,
                scheduledDurationMinutes = request.scheduledDurationMinutes
            )
        )

        return when (response.result) {
            "CREATED" -> response.session?.let {
                CreateRemoteSessionResult.Created(it.toRecord())
            } ?: CreateRemoteSessionResult.Failure("Session directory returned no session.")
            else -> CreateRemoteSessionResult.Failure(
                response.message ?: "Unable to create session."
            )
        }
    }

    override suspend fun resolveJoinCode(
        joinCode: String,
        requesterUid: String
    ): ResolveJoinCodeResult {
        val response = api.resolveJoinCode(
            ResolveCodeApiRequest(
                joinCode = joinCode.filter(Char::isDigit),
                requesterUid = requesterUid
            )
        )

        return when (response.result) {
            "FOUND" -> response.session?.let {
                ResolveJoinCodeResult.Found(it.toRecord())
            } ?: ResolveJoinCodeResult.Failure("Session directory returned no session.")
            "NOT_FOUND" -> ResolveJoinCodeResult.NotFound
            "EXPIRED" -> ResolveJoinCodeResult.Expired
            "CANCELLED" -> ResolveJoinCodeResult.Cancelled
            "ENDED" -> ResolveJoinCodeResult.Ended
            else -> ResolveJoinCodeResult.Failure(
                response.message ?: "Unable to resolve session code."
            )
        }
    }

    override suspend fun updateSession(
        request: UpdateRemoteSessionRequest
    ): UpdateRemoteSessionResult {
        val response = api.updateSession(
            sessionId = request.sessionId,
            request = UpdateSessionApiRequest(
                hostUid = request.hostUid,
                name = request.name,
                status = request.status.name,
                scheduledStartAt = request.scheduledStartAt,
                scheduledDurationMinutes = request.scheduledDurationMinutes
            )
        )

        return when (response.result) {
            "UPDATED" -> response.session?.let {
                UpdateRemoteSessionResult.Updated(it.toRecord())
            } ?: UpdateRemoteSessionResult.Failure("Session directory returned no session.")
            "NOT_FOUND" -> UpdateRemoteSessionResult.NotFound
            "CANCELLED" -> UpdateRemoteSessionResult.Cancelled
            "ENDED" -> UpdateRemoteSessionResult.Ended
            else -> UpdateRemoteSessionResult.Failure(
                response.message ?: "Unable to update session."
            )
        }
    }

    override suspend fun endSession(
        request: CloseRemoteSessionRequest
    ): EndRemoteSessionResult {
        val response = api.endSession(
            sessionId = request.sessionId,
            request = CloseSessionApiRequest(hostUid = request.hostUid)
        )

        return when (response.result) {
            "ENDED" -> response.session?.let {
                EndRemoteSessionResult.Ended(it.toRecord())
            } ?: EndRemoteSessionResult.Failure("Session directory returned no session.")
            "NOT_FOUND" -> EndRemoteSessionResult.NotFound
            "CANCELLED" -> EndRemoteSessionResult.Cancelled
            "ALREADY_ENDED" -> EndRemoteSessionResult.AlreadyEnded
            else -> EndRemoteSessionResult.Failure(
                response.message ?: "Unable to end session."
            )
        }
    }

    override suspend fun cancelSession(
        request: CloseRemoteSessionRequest
    ): CancelRemoteSessionResult {
        val response = api.cancelSession(
            sessionId = request.sessionId,
            request = CloseSessionApiRequest(hostUid = request.hostUid)
        )

        return when (response.result) {
            "CANCELLED" -> response.session?.let {
                CancelRemoteSessionResult.Cancelled(it.toRecord())
            } ?: CancelRemoteSessionResult.Failure("Session directory returned no session.")
            "NOT_FOUND" -> CancelRemoteSessionResult.NotFound
            "ENDED" -> CancelRemoteSessionResult.Ended
            "ALREADY_CANCELLED" -> CancelRemoteSessionResult.AlreadyCancelled
            else -> CancelRemoteSessionResult.Failure(
                response.message ?: "Unable to cancel session."
            )
        }
    }

    private fun SessionApiPayload.toRecord(): RemoteSessionRecord {
        return RemoteSessionRecord(
            sessionId = sessionId,
            joinCode = joinCode,
            sessionName = sessionName,
            hostUid = hostUid,
            status = RemoteSessionStatus.valueOf(status),
            scheduledStartAt = scheduledStartAt,
            scheduledDurationMinutes = scheduledDurationMinutes,
            actualStartedAt = actualStartedAt,
            actualEndedAt = actualEndedAt,
            expiresAt = expiresAt
        )
    }
}
