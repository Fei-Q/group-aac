package com.example.groupaac.data.sessiondirectory

class FakeSessionDirectory(
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) : SessionDirectory {
    private val sessionsById = linkedMapOf<String, RemoteSessionRecord>()
    private val sessionIdsByJoinCode = linkedMapOf<String, String>()
    private var nextSessionNumber = 1
    private var nextJoinNumber = 1111_1111

    fun seedSession(session: RemoteSessionRecord) {
        sessionsById[session.sessionId] = session
        sessionIdsByJoinCode[session.joinCode.filter(Char::isDigit)] = session.sessionId
    }

    override suspend fun createSession(
        request: CreateRemoteSessionRequest
    ): CreateRemoteSessionResult {
        val sessionId = "session-${nextSessionNumber++}"
        val joinDigits = nextJoinNumber++.toString().padStart(8, '0')
        val joinCode = "${joinDigits.take(4)}-${joinDigits.drop(4)}"
        val now = nowProvider()
        val record = RemoteSessionRecord(
            sessionId = sessionId,
            joinCode = joinCode,
            sessionName = request.name.trim().ifBlank { "Group Meeting" },
            hostUid = request.hostUid,
            status = request.status,
            scheduledStartAt = request.scheduledStartAt,
            scheduledDurationMinutes = request.scheduledDurationMinutes,
            actualStartedAt = if (request.status == RemoteSessionStatus.LIVE) now else null,
            actualEndedAt = null,
            expiresAt = request.scheduledStartAt?.let { scheduled ->
                scheduled + (request.scheduledDurationMinutes ?: 60) * 60_000L
            } ?: now + 24 * 60 * 60_000L
        )
        seedSession(record)
        return CreateRemoteSessionResult.Created(record)
    }

    override suspend fun resolveJoinCode(
        joinCode: String,
        requesterUid: String
    ): ResolveJoinCodeResult {
        val sessionId = sessionIdsByJoinCode[joinCode.filter(Char::isDigit)]
            ?: return ResolveJoinCodeResult.NotFound
        val session = sessionsById[sessionId]
            ?: return ResolveJoinCodeResult.NotFound

        if (session.expiresAt != null && session.expiresAt <= nowProvider()) {
            return ResolveJoinCodeResult.Expired
        }

        return when (session.status) {
            RemoteSessionStatus.CANCELLED -> ResolveJoinCodeResult.Cancelled
            RemoteSessionStatus.ENDED -> ResolveJoinCodeResult.Ended
            else -> ResolveJoinCodeResult.Found(session)
        }
    }

    override suspend fun updateSession(
        request: UpdateRemoteSessionRequest
    ): UpdateRemoteSessionResult {
        val existing = sessionsById[request.sessionId]
            ?: return UpdateRemoteSessionResult.NotFound
        if (existing.hostUid != request.hostUid) {
            return UpdateRemoteSessionResult.Failure("Only the host may update this session.")
        }

        return when (existing.status) {
            RemoteSessionStatus.CANCELLED -> UpdateRemoteSessionResult.Cancelled
            RemoteSessionStatus.ENDED -> UpdateRemoteSessionResult.Ended
            else -> {
                val now = nowProvider()
                val updated = existing.copy(
                    sessionName = request.name.trim().ifBlank { existing.sessionName },
                    status = request.status,
                    scheduledStartAt = request.scheduledStartAt,
                    scheduledDurationMinutes = request.scheduledDurationMinutes,
                    actualStartedAt = if (
                        request.status == RemoteSessionStatus.LIVE &&
                            existing.actualStartedAt == null
                    ) now else existing.actualStartedAt,
                    expiresAt = request.scheduledStartAt?.let { scheduled ->
                        scheduled + (request.scheduledDurationMinutes ?: 60) * 60_000L
                    } ?: existing.expiresAt
                )
                seedSession(updated)
                UpdateRemoteSessionResult.Updated(updated)
            }
        }
    }

    override suspend fun endSession(
        request: CloseRemoteSessionRequest
    ): EndRemoteSessionResult {
        val existing = sessionsById[request.sessionId]
            ?: return EndRemoteSessionResult.NotFound
        if (existing.hostUid != request.hostUid) {
            return EndRemoteSessionResult.Failure("Only the host may end this session.")
        }

        return when (existing.status) {
            RemoteSessionStatus.CANCELLED -> EndRemoteSessionResult.Cancelled
            RemoteSessionStatus.ENDED -> EndRemoteSessionResult.AlreadyEnded
            else -> {
                val updated = existing.copy(
                    status = RemoteSessionStatus.ENDED,
                    actualEndedAt = nowProvider()
                )
                seedSession(updated)
                EndRemoteSessionResult.Ended(updated)
            }
        }
    }

    override suspend fun cancelSession(
        request: CloseRemoteSessionRequest
    ): CancelRemoteSessionResult {
        val existing = sessionsById[request.sessionId]
            ?: return CancelRemoteSessionResult.NotFound
        if (existing.hostUid != request.hostUid) {
            return CancelRemoteSessionResult.Failure("Only the host may cancel this session.")
        }

        return when (existing.status) {
            RemoteSessionStatus.ENDED -> CancelRemoteSessionResult.Ended
            RemoteSessionStatus.CANCELLED -> CancelRemoteSessionResult.AlreadyCancelled
            else -> {
                val updated = existing.copy(status = RemoteSessionStatus.CANCELLED)
                seedSession(updated)
                CancelRemoteSessionResult.Cancelled(updated)
            }
        }
    }
}
