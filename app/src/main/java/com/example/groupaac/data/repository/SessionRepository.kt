package com.example.groupaac.data.repository

import com.example.groupaac.data.dao.SessionDao
import com.example.groupaac.data.dao.SessionJoinRequestDao
import com.example.groupaac.data.dao.SessionParticipantRow
import com.example.groupaac.data.dao.UserDao
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionJoinRequestEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.data.realtime.sync.NoOpSessionRealtimeSync
import com.example.groupaac.data.realtime.sync.SessionRealtimeSync
import com.example.groupaac.data.session.ActiveSessionStore
import com.example.groupaac.data.sessiondirectory.CancelRemoteSessionResult
import com.example.groupaac.data.sessiondirectory.CloseRemoteSessionRequest
import com.example.groupaac.data.sessiondirectory.CreateRemoteSessionRequest
import com.example.groupaac.data.sessiondirectory.CreateRemoteSessionResult
import com.example.groupaac.data.sessiondirectory.EndRemoteSessionResult
import com.example.groupaac.data.sessiondirectory.RemoteSessionRecord
import com.example.groupaac.data.sessiondirectory.RemoteSessionStatus
import com.example.groupaac.data.sessiondirectory.ResolveJoinCodeResult
import com.example.groupaac.data.sessiondirectory.SessionDirectory
import com.example.groupaac.data.sessiondirectory.UpdateRemoteSessionRequest
import com.example.groupaac.data.sessiondirectory.UpdateRemoteSessionResult
import com.example.groupaac.model.ActiveSession
import com.example.groupaac.model.JoinRequestStatus
import com.example.groupaac.model.JoinSessionResult
import com.example.groupaac.model.SessionRole
import com.example.groupaac.util.IdUtils
import com.example.groupaac.util.TimeUtils
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class SessionRepository(
    private val sessionDao: SessionDao,
    private val sessionJoinRequestDao: SessionJoinRequestDao,
    private val userDao: UserDao,
    private val activeSessionStore: ActiveSessionStore,
    private val sessionDirectory: SessionDirectory,
    private val sessionRealtimeSync: SessionRealtimeSync = NoOpSessionRealtimeSync
) {
    fun observeSession(
        sessionId: String
    ): Flow<SessionEntity?> = sessionDao.observeSession(sessionId)

    fun observeSessions(): Flow<List<SessionEntity>> = sessionDao.observeSessions()

    fun observeUpcomingHostedSessions(
        hostUserId: String
    ): Flow<List<SessionEntity>> = sessionDao.observeUpcomingHostedSessions(
        hostUserId = hostUserId,
        dayStartMillis = startOfTodayMillis()
    )

    fun observeLiveHostedSessions(
        hostUserId: String
    ): Flow<List<SessionEntity>> = sessionDao.observeLiveHostedSessions(hostUserId)

    fun observePastHostedSessions(
        hostUserId: String
    ): Flow<List<SessionEntity>> = sessionDao.observePastHostedSessions(
        hostUserId = hostUserId,
        dayStartMillis = startOfTodayMillis()
    )

    suspend fun getSession(
        sessionId: String
    ): SessionEntity? = sessionDao.getSession(sessionId)

    fun observeMembers(
        sessionId: String
    ): Flow<List<SessionParticipantRow>> =
        sessionDao.observeMembers(sessionId)

    fun observePendingJoinRequests(
        sessionId: String
    ): Flow<List<SessionJoinRequestEntity>> =
        sessionJoinRequestDao.observePendingBySession(sessionId)

    fun observeJoinRequest(
        requestId: String
    ): Flow<SessionJoinRequestEntity?> =
        sessionJoinRequestDao.observeRequestById(requestId)

    fun observeActiveSession(
        userId: String
    ): Flow<ActiveSession?> {
        return activeSessionStore
            .observeActiveSessionId(userId)
            .flatMapLatest { sessionId ->
                if (sessionId == null) {
                    flowOf(null)
                } else {
                    combine(
                        sessionDao.observeSession(sessionId),
                        sessionDao.observeMember(
                            sessionId = sessionId,
                            userId = userId
                        )
                    ) { session, member ->
                        if (
                            session == null ||
                            member == null ||
                            session.actualEndedAt != null
                        ) {
                            null
                        } else {
                            ActiveSession(
                                sessionId = session.id,
                                joinCode = session.joinCode,
                                sessionName = session.name,
                                userId = member.userId,
                                role = member.role,
                                joinedAt = member.joinedAt,
                                scheduledStartAt = session.scheduledStartAt,
                                scheduledDurationMinutes =
                                    session.scheduledDurationMinutes,
                                actualStartedAt = session.actualStartedAt
                            )
                        }
                    }
                }
            }
    }

    suspend fun createSessionNow(
        name: String,
        ownerUserId: String,
        displayName: String
    ): ActiveSession {
        val owner = userDao.getUser(ownerUserId)
            ?: error("User not found.")
        val cleanDisplayName = displayName
            .trim()
            .ifBlank { owner.displayName }
        val session = when (
            val result = sessionDirectory.createSession(
                CreateRemoteSessionRequest(
                    hostUid = ownerUserId,
                    name = name,
                    status = RemoteSessionStatus.LIVE,
                    scheduledStartAt = null,
                    scheduledDurationMinutes = null
                )
            )
        ) {
            is CreateRemoteSessionResult.Created -> result.session.toSessionEntity()
            is CreateRemoteSessionResult.Failure -> error(result.message)
        }
        val now = session.actualStartedAt ?: TimeUtils.now()
        val member = hostMembership(
            sessionId = session.id,
            ownerUserId = ownerUserId,
            displayName = cleanDisplayName,
            joinedAt = now
        )

        sessionDao.createOrJoinSession(
            session = session,
            member = member
        )

        activeSessionStore.setActiveSession(
            userId = ownerUserId,
            sessionId = session.id
        )

        sessionRealtimeSync.publishSessionStarted(session, ownerUserId)
        sessionRealtimeSync.publishMemberJoined(session, member)

        return ActiveSession(
            sessionId = session.id,
            joinCode = session.joinCode,
            sessionName = session.name,
            userId = ownerUserId,
            role = SessionRole.HOST,
            joinedAt = now,
            scheduledStartAt = session.scheduledStartAt,
            scheduledDurationMinutes = session.scheduledDurationMinutes,
            actualStartedAt = session.actualStartedAt
        )
    }

    suspend fun scheduleSession(
        name: String,
        ownerUserId: String,
        scheduledStartAt: Long,
        scheduledDurationMinutes: Int
    ): SessionEntity {
        val owner = userDao.getUser(ownerUserId)
            ?: error("User not found.")
        val session = when (
            val result = sessionDirectory.createSession(
                CreateRemoteSessionRequest(
                    hostUid = ownerUserId,
                    name = name,
                    status = RemoteSessionStatus.SCHEDULED,
                    scheduledStartAt = scheduledStartAt,
                    scheduledDurationMinutes = scheduledDurationMinutes
                )
            )
        ) {
            is CreateRemoteSessionResult.Created -> result.session.toSessionEntity()
            is CreateRemoteSessionResult.Failure -> error(result.message)
        }
        val now = TimeUtils.now()
        val member = hostMembership(
            sessionId = session.id,
            ownerUserId = ownerUserId,
            displayName = owner.displayName,
            joinedAt = now
        )

        sessionDao.createOrJoinSession(
            session = session,
            member = member
        )
        sessionRealtimeSync.publishSessionUpdated(session, ownerUserId)

        return session
    }

    suspend fun updateScheduledSession(
        sessionId: String,
        ownerUserId: String,
        name: String,
        scheduledStartAt: Long,
        scheduledDurationMinutes: Int
    ): SessionEntity {
        val session = requireHostedSession(
            sessionId = sessionId,
            ownerUserId = ownerUserId
        )
        check(session.actualStartedAt == null && session.actualEndedAt == null) {
            "Only upcoming sessions can be edited."
        }

        val updated = when (
            val result = sessionDirectory.updateSession(
                UpdateRemoteSessionRequest(
                    sessionId = sessionId,
                    hostUid = ownerUserId,
                    name = name,
                    status = RemoteSessionStatus.SCHEDULED,
                    scheduledStartAt = scheduledStartAt,
                    scheduledDurationMinutes = scheduledDurationMinutes
                )
            )
        ) {
            is UpdateRemoteSessionResult.Updated -> result.session.toSessionEntity(
                existing = session
            )
            UpdateRemoteSessionResult.NotFound -> error("Session not found.")
            UpdateRemoteSessionResult.Cancelled -> error("This session has been cancelled.")
            UpdateRemoteSessionResult.Ended -> error("This session has already ended.")
            is UpdateRemoteSessionResult.Failure -> error(result.message)
        }
        sessionDao.upsertSession(updated)
        sessionRealtimeSync.publishSessionUpdated(updated, ownerUserId)
        return updated
    }

    suspend fun startScheduledSession(
        sessionId: String,
        ownerUserId: String
    ): ActiveSession {
        val session = requireHostedSession(
            sessionId = sessionId,
            ownerUserId = ownerUserId
        )
        check(session.actualEndedAt == null) {
            "This session has already ended."
        }

        val updatedSession = when (
            val result = sessionDirectory.updateSession(
                UpdateRemoteSessionRequest(
                    sessionId = sessionId,
                    hostUid = ownerUserId,
                    name = session.name,
                    status = RemoteSessionStatus.LIVE,
                    scheduledStartAt = session.scheduledStartAt,
                    scheduledDurationMinutes = session.scheduledDurationMinutes
                )
            )
        ) {
            is UpdateRemoteSessionResult.Updated -> result.session.toSessionEntity(
                existing = session
            )
            UpdateRemoteSessionResult.NotFound -> error("Session not found.")
            UpdateRemoteSessionResult.Cancelled -> error("This session has been cancelled.")
            UpdateRemoteSessionResult.Ended -> error("This session has already ended.")
            is UpdateRemoteSessionResult.Failure -> error(result.message)
        }
        sessionDao.upsertSession(updatedSession)

        val owner = userDao.getUser(ownerUserId)
            ?: error("User not found.")
        val member = sessionDao.getMember(sessionId, ownerUserId)
        val joinedAt = member?.joinedAt ?: updatedSession.createdAt

        sessionDao.upsertMember(
            hostMembership(
                sessionId = sessionId,
                ownerUserId = ownerUserId,
                displayName = owner.displayName,
                joinedAt = joinedAt
            )
        )

        return activateHostedSession(
            sessionId = sessionId,
            ownerUserId = ownerUserId,
            displayName = owner.displayName,
            joinedAt = joinedAt
        )
    }

    suspend fun openHostedSession(
        sessionId: String,
        ownerUserId: String
    ): ActiveSession {
        val session = requireHostedSession(
            sessionId = sessionId,
            ownerUserId = ownerUserId
        )
        check(session.actualStartedAt != null && session.actualEndedAt == null) {
            "Only live sessions can be opened."
        }

        val owner = userDao.getUser(ownerUserId)
            ?: error("User not found.")
        val member = sessionDao.getMember(sessionId, ownerUserId)
        val joinedAt = member?.joinedAt ?: session.createdAt

        sessionDao.upsertMember(
            hostMembership(
                sessionId = sessionId,
                ownerUserId = ownerUserId,
                displayName = owner.displayName,
                joinedAt = joinedAt
            )
        )

        return activateHostedSession(
            sessionId = sessionId,
            ownerUserId = ownerUserId,
            displayName = owner.displayName,
            joinedAt = joinedAt
        )
    }

    suspend fun cancelScheduledSession(
        sessionId: String,
        ownerUserId: String
    ): Boolean {
        val session = requireHostedSession(
            sessionId = sessionId,
            ownerUserId = ownerUserId
        )
        check(session.actualStartedAt == null && session.actualEndedAt == null) {
            "Only upcoming sessions can be cancelled."
        }

        return when (
            val result = sessionDirectory.cancelSession(
                CloseRemoteSessionRequest(
                    sessionId = sessionId,
                    hostUid = ownerUserId
                )
            )
        ) {
            is CancelRemoteSessionResult.Cancelled -> {
                sessionJoinRequestDao.deleteRequestsForSession(sessionId)
                sessionDao.deleteMembersForSession(sessionId)
                val deleted = sessionDao.deleteHostedSession(sessionId, ownerUserId) > 0
                if (deleted) {
                    sessionRealtimeSync.publishSessionCancelled(
                        result.session.toSessionEntity(existing = session),
                        ownerUserId
                    )
                }
                deleted
            }
            CancelRemoteSessionResult.NotFound -> error("Session not found.")
            CancelRemoteSessionResult.Ended -> error("This session has already ended.")
            CancelRemoteSessionResult.AlreadyCancelled -> error("This session has already been cancelled.")
            is CancelRemoteSessionResult.Failure -> error(result.message)
        }
    }

    suspend fun joinSession(
        joinCode: String,
        userId: String,
        displayName: String,
        requestedRole: SessionRole = SessionRole.PARTICIPANT
    ): JoinSessionResult {
        val user = userDao.getUser(userId)
            ?: error("User not found.")
        val cleanCode = normalizeJoinCode(joinCode)
        val session = when (
            val resolved = sessionDirectory.resolveJoinCode(
                joinCode = cleanCode,
                requesterUid = userId
            )
        ) {
            is ResolveJoinCodeResult.Found -> {
                val shell = resolved.session.toSessionEntity(
                    existing = sessionDao.getSession(resolved.session.sessionId)
                )
                sessionDao.upsertSession(shell)
                shell
            }
            ResolveJoinCodeResult.NotFound -> error("No session found for this code.")
            ResolveJoinCodeResult.Expired -> error("This session code has expired.")
            ResolveJoinCodeResult.Cancelled -> error("This session has been cancelled.")
            ResolveJoinCodeResult.Ended -> error("This session has already ended.")
            is ResolveJoinCodeResult.Failure -> error(resolved.message)
        }
        require(requestedRole != SessionRole.HOST) {
            "Host access cannot be requested from the join screen."
        }

        check(session.actualEndedAt == null) {
            "This session has already ended."
        }

        val now = TimeUtils.now()
        val cleanDisplayName = displayName
            .trim()
            .ifBlank { user.displayName }

        return when (requestedRole) {
            SessionRole.PARTICIPANT -> {
                val member = SessionMemberEntity(
                    sessionId = session.id,
                    userId = userId,
                    displayName = cleanDisplayName,
                    role = SessionRole.PARTICIPANT,
                    joinedAt = now
                )

                sessionDao.upsertMember(member)

                activeSessionStore.setActiveSession(
                    userId = userId,
                    sessionId = session.id
                )

                sessionRealtimeSync.publishMemberJoined(session, member)

                JoinSessionResult.Joined(
                    activeSession = ActiveSession(
                        sessionId = session.id,
                        joinCode = session.joinCode,
                        sessionName = session.name,
                        userId = userId,
                        role = SessionRole.PARTICIPANT,
                        joinedAt = now,
                        scheduledStartAt = session.scheduledStartAt,
                        scheduledDurationMinutes =
                            session.scheduledDurationMinutes,
                        actualStartedAt = session.actualStartedAt
                    )
                )
            }

            SessionRole.FACILITATOR -> {
                val pendingRequest =
                    sessionJoinRequestDao.getPendingRequest(
                        sessionId = session.id,
                        userId = userId,
                        requestedRole = SessionRole.FACILITATOR
                    ) ?: createJoinRequest(
                        sessionId = session.id,
                        userId = userId,
                        displayName = cleanDisplayName,
                        requestedRole = SessionRole.FACILITATOR
                    )

                JoinSessionResult.AwaitingApproval(
                    request = pendingRequest
                )
            }

            SessionRole.HOST -> error(
                "Host access cannot be requested from the join screen."
            )
        }
    }

    suspend fun leaveSession(userId: String) {
        activeSessionStore.clearActiveSession(userId)
    }

    suspend fun getJoinRequest(
        requestId: String
    ): SessionJoinRequestEntity? =
        sessionJoinRequestDao.getRequestById(requestId)

    suspend fun createJoinRequest(
        sessionId: String,
        userId: String,
        displayName: String,
        requestedRole: SessionRole
    ): SessionJoinRequestEntity {
        require(requestedRole != SessionRole.HOST) {
            "Host access cannot be requested."
        }

        val user = userDao.getUser(userId)
            ?: error("User not found.")
        val now = TimeUtils.now()
        val request = SessionJoinRequestEntity(
            id = IdUtils.newId(),
            sessionId = sessionId,
            userId = userId,
            displayName = displayName.trim().ifBlank { user.displayName },
            requestedRole = requestedRole,
            status = JoinRequestStatus.PENDING,
            requestedAt = now
        )
        sessionJoinRequestDao.upsertRequest(request)
        sessionRealtimeSync.publishFacilitatorRequested(request, userId)
        return request
    }

    suspend fun requestFacilitatorAccess(
        sessionId: String,
        userId: String,
        displayName: String
    ): SessionJoinRequestEntity =
        createJoinRequest(
            sessionId = sessionId,
            userId = userId,
            displayName = displayName,
            requestedRole = SessionRole.FACILITATOR
        )

    suspend fun approveJoinRequest(
        requestId: String,
        decidedByUserId: String
    ): Boolean {
        val request = sessionJoinRequestDao.getRequestById(requestId)
            ?: return false
        val session = sessionDao.getSession(request.sessionId)
            ?: return false
        check(session.hostUserId == decidedByUserId) {
            "Only the session host may approve facilitator requests."
        }
        val updated = sessionJoinRequestDao.approveRequest(
            requestId = requestId,
            decidedByUserId = decidedByUserId,
            decidedAt = TimeUtils.now()
        )
        if (updated == 0) {
            return false
        }

        val existingMember = sessionDao.getMember(
            sessionId = request.sessionId,
            userId = request.userId
        )
        sessionDao.upsertMember(
            SessionMemberEntity(
                sessionId = request.sessionId,
                userId = request.userId,
                displayName = request.displayName,
                role = request.requestedRole,
                joinedAt = existingMember?.joinedAt ?: request.requestedAt
            )
        )
        val updatedRequest = sessionJoinRequestDao.getRequestById(requestId)
            ?: request.copy(
                status = JoinRequestStatus.APPROVED,
                decidedAt = TimeUtils.now(),
                decidedByUserId = decidedByUserId
            )
        sessionRealtimeSync.publishFacilitatorApproved(updatedRequest, decidedByUserId)
        return true
    }

    suspend fun declineJoinRequest(
        requestId: String,
        decidedByUserId: String
    ): Boolean {
        val request = sessionJoinRequestDao.getRequestById(requestId)
            ?: return false
        val session = sessionDao.getSession(request.sessionId)
            ?: return false
        check(session.hostUserId == decidedByUserId) {
            "Only the session host may decline facilitator requests."
        }
        val declined = sessionJoinRequestDao.declineRequest(
            requestId = requestId,
            decidedByUserId = decidedByUserId,
            decidedAt = TimeUtils.now()
        ) > 0
        if (declined) {
            val updatedRequest = sessionJoinRequestDao.getRequestById(requestId)
                ?: request.copy(
                    status = JoinRequestStatus.DECLINED,
                    decidedAt = TimeUtils.now(),
                    decidedByUserId = decidedByUserId
                )
            sessionRealtimeSync.publishFacilitatorDeclined(updatedRequest, decidedByUserId)
        }
        return declined
    }

    suspend fun cancelJoinRequest(requestId: String): Boolean {
        val request = sessionJoinRequestDao.getRequestById(requestId)
            ?: return false
        val cancelled = sessionJoinRequestDao.cancelRequest(
            requestId = requestId,
            decidedAt = TimeUtils.now()
        ) > 0
        if (cancelled) {
            val updatedRequest = sessionJoinRequestDao.getRequestById(requestId)
                ?: request.copy(
                    status = JoinRequestStatus.CANCELLED,
                    decidedAt = TimeUtils.now()
                )
            sessionRealtimeSync.publishFacilitatorCancelled(updatedRequest, request.userId)
        }
        return cancelled
    }

    suspend fun activateApprovedFacilitatorRequest(
        requestId: String,
        userId: String
    ): ActiveSession? {
        val request = sessionJoinRequestDao.getRequestById(requestId)
            ?: return null
        if (
            request.userId != userId ||
            request.status != JoinRequestStatus.APPROVED
        ) {
            return null
        }

        val session = sessionDao.getSession(request.sessionId)
            ?: return null
        val member = sessionDao.getMember(
            sessionId = request.sessionId,
            userId = userId
        ) ?: return null

        activeSessionStore.setActiveSession(
            userId = userId,
            sessionId = session.id
        )

        return ActiveSession(
            sessionId = session.id,
            joinCode = session.joinCode,
            sessionName = session.name,
            userId = userId,
            role = member.role,
            joinedAt = member.joinedAt,
            scheduledStartAt = session.scheduledStartAt,
            scheduledDurationMinutes = session.scheduledDurationMinutes,
            actualStartedAt = session.actualStartedAt
        )
    }

    suspend fun endSession(sessionId: String) {
        val session = sessionDao.getSession(sessionId) ?: return
        val actorUserId = session.hostUserId ?: return
        when (
            val result = sessionDirectory.endSession(
                CloseRemoteSessionRequest(
                    sessionId = sessionId,
                    hostUid = actorUserId
                )
            )
        ) {
            is EndRemoteSessionResult.Ended -> {
                val updated = result.session.toSessionEntity(existing = session)
                sessionDao.upsertSession(updated)
                sessionRealtimeSync.publishSessionEnded(updated, actorUserId)
            }
            EndRemoteSessionResult.NotFound -> error("Session not found.")
            EndRemoteSessionResult.Cancelled -> error("This session has been cancelled.")
            EndRemoteSessionResult.AlreadyEnded -> Unit
            is EndRemoteSessionResult.Failure -> error(result.message)
        }
    }

    suspend fun seedDemoParticipants(sessionId: String) {
        val now = TimeUtils.now()
        val demoMembers = listOf(
            SessionMemberEntity(
                sessionId,
                "demo-alice",
                "Alice",
                SessionRole.PARTICIPANT,
                now - 100_000
            ),
            SessionMemberEntity(
                sessionId,
                "demo-bob",
                "Bob",
                SessionRole.PARTICIPANT,
                now - 90_000
            ),
            SessionMemberEntity(
                sessionId,
                "demo-eve",
                "Eve",
                SessionRole.PARTICIPANT,
                now - 80_000
            ),
            SessionMemberEntity(
                sessionId,
                "demo-mary",
                "Mary",
                SessionRole.PARTICIPANT,
                now - 70_000
            )
        )

        for (member in demoMembers) {
            sessionDao.upsertMember(member)
        }
    }

    private fun normalizeJoinCode(raw: String): String {
        val digits = raw.filter(Char::isDigit)

        require(digits.length == 8) {
            "Session code must contain eight digits."
        }

        return "${digits.take(4)}-${digits.drop(4)}"
    }

    private suspend fun requireHostedSession(
        sessionId: String,
        ownerUserId: String
    ): SessionEntity {
        val session = sessionDao.getSession(sessionId)
            ?: error("Session not found.")
        check(session.hostUserId == ownerUserId) {
            "Only the session host may manage this session."
        }
        return session
    }

    private suspend fun activateHostedSession(
        sessionId: String,
        ownerUserId: String,
        displayName: String,
        joinedAt: Long
    ): ActiveSession {
        val session = sessionDao.getSession(sessionId)
            ?: error("Session not found.")

        activeSessionStore.setActiveSession(
            userId = ownerUserId,
            sessionId = sessionId
        )

        return ActiveSession(
            sessionId = session.id,
            joinCode = session.joinCode,
            sessionName = session.name,
            userId = ownerUserId,
            role = SessionRole.HOST,
            joinedAt = joinedAt,
            scheduledStartAt = session.scheduledStartAt,
            scheduledDurationMinutes = session.scheduledDurationMinutes,
            actualStartedAt = session.actualStartedAt
        )
    }

    private fun hostMembership(
        sessionId: String,
        ownerUserId: String,
        displayName: String,
        joinedAt: Long
    ): SessionMemberEntity = SessionMemberEntity(
        sessionId = sessionId,
        userId = ownerUserId,
        displayName = displayName,
        role = SessionRole.HOST,
        joinedAt = joinedAt
    )

    private fun startOfTodayMillis(): Long {
        val zone = ZoneId.systemDefault()
        return LocalDate.now(zone)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    }

    private fun RemoteSessionRecord.toSessionEntity(
        existing: SessionEntity? = null
    ): SessionEntity {
        return SessionEntity(
            id = sessionId,
            name = sessionName,
            joinCode = joinCode,
            hostUserId = hostUid,
            displayMode = existing?.displayMode ?: com.example.groupaac.model.DisplayMode.AUTO_LATEST,
            createdAt = existing?.createdAt ?: actualStartedAt ?: scheduledStartAt ?: TimeUtils.now(),
            scheduledStartAt = scheduledStartAt,
            scheduledDurationMinutes = scheduledDurationMinutes,
            actualStartedAt = actualStartedAt,
            actualEndedAt = actualEndedAt,
            updatedAt = TimeUtils.now()
        )
    }
}
