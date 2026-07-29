package com.example.groupaac.data.repository

import com.example.groupaac.data.dao.SessionDao
import com.example.groupaac.data.dao.SessionJoinRequestDao
import com.example.groupaac.data.dao.SessionParticipantRow
import com.example.groupaac.data.dao.UserDao
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionJoinRequestEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.data.pi.DisplayBindingCoordinator
import com.example.groupaac.data.pi.DisplayBindingResult
import com.example.groupaac.data.pi.DisplayPairingPayload
import com.example.groupaac.data.pi.LaunchSessionResult
import com.example.groupaac.data.pi.NoOpDisplayBindingCoordinator
import com.example.groupaac.data.pi.SessionInvitationPayload
import com.example.groupaac.data.realtime.reliability.OutboxDispatching
import com.example.groupaac.data.realtime.sync.NoOpSessionRealtimeSync
import com.example.groupaac.data.realtime.sync.SessionRealtimeSync
import com.example.groupaac.data.session.ActiveSessionStore
import com.example.groupaac.data.sessiondirectory.RegisterSessionResult
import com.example.groupaac.data.sessiondirectory.ResolveJoinCodeResult
import com.example.groupaac.data.sessiondirectory.SessionDirectory
import com.example.groupaac.data.sessiondirectory.SessionDirectoryEntry
import com.example.groupaac.data.sessiondirectory.formatJoinCode
import com.example.groupaac.model.ActiveSession
import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.JoinRequestStatus
import com.example.groupaac.model.JoinSessionResult
import com.example.groupaac.model.SessionRole
import com.example.groupaac.model.SessionStatus
import com.example.groupaac.util.IdUtils
import com.example.groupaac.util.TimeUtils
import java.security.SecureRandom
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

class SessionRepository(
    private val transactionRunner: TransactionRunner,
    private val sessionDao: SessionDao,
    private val sessionJoinRequestDao: SessionJoinRequestDao,
    private val userDao: UserDao,
    private val activeSessionStore: ActiveSessionStore,
    private val sessionDirectory: SessionDirectory,
    private val displayBindingCoordinator: DisplayBindingCoordinator =
        NoOpDisplayBindingCoordinator,
    private val outboxDispatcher: OutboxDispatching,
    private val sessionRealtimeSync: SessionRealtimeSync =
        NoOpSessionRealtimeSync,
    private val joinCodeGenerator: () -> String =
        ::generateJoinCode
) {

    fun observeSession(
        sessionId: String
    ): Flow<SessionEntity?> =
        sessionDao.observeSession(sessionId)

    fun observeSessions(): Flow<List<SessionEntity>> =
        sessionDao.observeSessions()

    fun observeUpcomingHostedSessions(
        hostUserId: String
    ): Flow<List<SessionEntity>> =
        sessionDao.observeUpcomingHostedSessions(
            hostUserId = hostUserId,
            dayStartMillis = startOfTodayMillis()
        )

    fun observeLiveHostedSessions(
        hostUserId: String
    ): Flow<List<SessionEntity>> =
        sessionDao.observeLiveHostedSessions(hostUserId)

    fun observePastHostedSessions(
        hostUserId: String
    ): Flow<List<SessionEntity>> =
        sessionDao.observePastHostedSessions(
            hostUserId = hostUserId,
            dayStartMillis = startOfTodayMillis()
        )

    suspend fun getSession(
        sessionId: String
    ): SessionEntity? =
        sessionDao.getSession(sessionId)

    fun observeMembers(
        sessionId: String
    ): Flow<List<SessionParticipantRow>> =
        sessionDao.observeMembers(sessionId)

    fun observePendingJoinRequests(
        sessionId: String
    ): Flow<List<SessionJoinRequestEntity>> =
        sessionJoinRequestDao.observePendingBySession(
            sessionId
        )

    fun observeJoinRequest(
        requestId: String
    ): Flow<SessionJoinRequestEntity?> =
        sessionJoinRequestDao.observeRequestById(
            requestId
        )

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
                        sessionDao.observeSession(
                            sessionId
                        ),
                        sessionDao.observeMember(
                            sessionId = sessionId,
                            userId = userId
                        )
                    ) { session, member ->
                        if (
                            session == null ||
                            member == null ||
                            session.status ==
                            SessionStatus.ENDED ||
                            session.status ==
                            SessionStatus.CANCELLED
                        ) {
                            null
                        } else {
                            ActiveSession(
                                sessionId =
                                    session.id,
                                joinCode =
                                    session.joinCode,
                                sessionName =
                                    session.name,
                                userId =
                                    member.userId,
                                role =
                                    member.role,
                                joinedAt =
                                    member.joinedAt,
                                scheduledStartAt =
                                    session
                                        .scheduledStartAt,
                                scheduledDurationMinutes =
                                    session
                                        .scheduledDurationMinutes,
                                actualStartedAt =
                                    session
                                        .actualStartedAt
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
        val owner =
            userDao.getUser(ownerUserId)
                ?: error("User not found.")

        val now = TimeUtils.now()

        val cleanDisplayName =
            displayName
                .trim()
                .ifBlank {
                    owner.displayName
                }

        val session =
            SessionEntity(
                id = IdUtils.newId(),
                name =
                    name.trim()
                        .ifBlank {
                            "Group Meeting"
                        },
                joinCode =
                    joinCodeGenerator(),
                hostUserId =
                    ownerUserId,
                status =
                    SessionStatus.DRAFT,
                displayMode =
                    defaultDisplayModeForUser(
                        ownerUserId
                    ),
                displayId = null,
                createdAt = now,
                actualStartedAt = null,
                actualEndedAt = null,
                expiresAt = null,
                updatedAt = now
            )

        val member =
            hostMembership(
                sessionId = session.id,
                ownerUserId = ownerUserId,
                displayName =
                    cleanDisplayName,
                joinedAt = now
            )

        transactionRunner.inTransaction {
            sessionDao.createOrJoinSession(
                session = session,
                member = member
            )
        }

        return ActiveSession(
            sessionId = session.id,
            joinCode = session.joinCode,
            sessionName = session.name,
            userId = ownerUserId,
            role = SessionRole.HOST,
            joinedAt = now,
            scheduledStartAt = null,
            scheduledDurationMinutes = null,
            actualStartedAt = null
        )
    }

    suspend fun scheduleSession(
        name: String,
        ownerUserId: String,
        scheduledStartAt: Long,
        scheduledDurationMinutes: Int
    ): SessionEntity {
        val owner =
            userDao.getUser(ownerUserId)
                ?: error("User not found.")

        require(
            scheduledDurationMinutes > 0
        ) {
            "Scheduled duration must be positive."
        }

        val now = TimeUtils.now()

        val session =
            SessionEntity(
                id = IdUtils.newId(),
                name =
                    name.trim()
                        .ifBlank {
                            "Group Meeting"
                        },
                joinCode =
                    joinCodeGenerator(),
                hostUserId =
                    ownerUserId,
                status =
                    SessionStatus.SCHEDULED,
                displayMode =
                    defaultDisplayModeForUser(
                        ownerUserId
                    ),
                displayId = null,
                createdAt = now,
                scheduledStartAt =
                    scheduledStartAt,
                scheduledDurationMinutes =
                    scheduledDurationMinutes,
                actualStartedAt = null,
                actualEndedAt = null,
                expiresAt = null,
                updatedAt = now
            )

        val member =
            hostMembership(
                sessionId = session.id,
                ownerUserId = ownerUserId,
                displayName =
                    owner.displayName,
                joinedAt = now
            )

        transactionRunner.inTransaction {
            sessionDao.createOrJoinSession(
                session = session,
                member = member
            )
        }

        return session
    }

    suspend fun updateScheduledSession(
        sessionId: String,
        ownerUserId: String,
        name: String,
        scheduledStartAt: Long,
        scheduledDurationMinutes: Int
    ): SessionEntity {
        val session =
            requireHostedSession(
                sessionId = sessionId,
                ownerUserId = ownerUserId
            )

        check(
            session.status ==
                    SessionStatus.SCHEDULED &&
                    session.actualStartedAt == null &&
                    session.actualEndedAt == null
        ) {
            "Only upcoming sessions can be edited."
        }

        require(
            scheduledDurationMinutes > 0
        ) {
            "Scheduled duration must be positive."
        }

        val updated =
            session.copy(
                name =
                    name.trim()
                        .ifBlank {
                            session.name
                        },
                scheduledStartAt =
                    scheduledStartAt,
                scheduledDurationMinutes =
                    scheduledDurationMinutes,
                updatedAt = TimeUtils.now()
            )

        sessionDao.upsertSession(updated)

        return updated
    }

    suspend fun startScheduledSession(
        sessionId: String,
        ownerUserId: String
    ): ActiveSession {
        val session =
            requireHostedSession(
                sessionId = sessionId,
                ownerUserId = ownerUserId
            )

        check(
            session.status ==
                    SessionStatus.SCHEDULED ||
                    session.status ==
                    SessionStatus.DRAFT
        ) {
            "Only an unstarted session can be prepared for launch."
        }

        val owner =
            userDao.getUser(ownerUserId)
                ?: error("User not found.")

        val existingMember =
            sessionDao.getMember(
                sessionId = sessionId,
                userId = ownerUserId
            )

        val joinedAt =
            existingMember?.joinedAt
                ?: TimeUtils.now()

        sessionDao.upsertMember(
            hostMembership(
                sessionId = sessionId,
                ownerUserId = ownerUserId,
                displayName =
                    owner.displayName,
                joinedAt = joinedAt
            )
        )

        return activateHostedSession(
            sessionId = sessionId,
            ownerUserId = ownerUserId,
            joinedAt = joinedAt
        )
    }

    suspend fun launchSessionOnDisplay(
        sessionId: String,
        ownerUserId: String,
        pairing: DisplayPairingPayload
    ): LaunchSessionResult {
        val initialSession =
            try {
                requireHostedSession(
                    sessionId = sessionId,
                    ownerUserId = ownerUserId
                )
            } catch (
                error: CancellationException
            ) {
                throw error
            } catch (error: Exception) {
                return LaunchSessionResult.Failure(
                    error.message
                        ?: "Unable to load the session."
                )
            }

        if (
            initialSession.status !=
            SessionStatus.DRAFT &&
            initialSession.status !=
            SessionStatus.SCHEDULED
        ) {
            return LaunchSessionResult.Failure(
                "Only an unstarted session can be launched."
            )
        }

        if (
            pairing.pairingExpiresAt <=
            TimeUtils.now()
        ) {
            return LaunchSessionResult.PairingExpired
        }

        val selectedCodeResult =
            selectAvailableJoinCode(
                initialCode =
                    initialSession.joinCode,
                sessionId =
                    initialSession.id
            )

        val selectedCode =
            when (selectedCodeResult) {
                is AvailableJoinCodeResult.Available -> {
                    selectedCodeResult.joinCode
                }

                is AvailableJoinCodeResult.Failure -> {
                    return LaunchSessionResult
                        .DirectoryFailure(
                            selectedCodeResult.message
                        )
                }
            }

        val workingSession =
            if (
                selectedCode !=
                initialSession.joinCode
            ) {
                initialSession.copy(
                    joinCode = selectedCode,
                    updatedAt = TimeUtils.now()
                ).also { updatedSession ->
                    sessionDao.upsertSession(
                        updatedSession
                    )
                }
            } else {
                initialSession
            }

        val startedAt = TimeUtils.now()

        val durationMinutes =
            workingSession
                .scheduledDurationMinutes
                ?.coerceAtLeast(1)
                ?: 120

        val expiresAt =
            startedAt +
                    durationMinutes * 60_000L

        val liveSession =
            workingSession.copy(
                status = SessionStatus.LIVE,
                displayId = pairing.displayId,
                actualStartedAt = startedAt,
                actualEndedAt = null,
                expiresAt = expiresAt,
                updatedAt = startedAt
            )

        val invitation =
            SessionInvitationPayload(
                sessionId =
                    liveSession.id,
                joinCode =
                    liveSession.joinCode,
                sessionName =
                    liveSession.name,
                hostUserId =
                    ownerUserId,
                displayId =
                    pairing.displayId,
                status =
                    SessionStatus.LIVE,
                displayMode =
                    liveSession.displayMode,
                actualStartedAt =
                    startedAt,
                expiresAt =
                    expiresAt
            )

        val bindingResult =
            displayBindingCoordinator.bind(
                pairing = pairing,
                invitation = invitation,
                requestedByUserId =
                    ownerUserId
            )

        when (bindingResult) {
            DisplayBindingResult.PairingExpired -> {
                return LaunchSessionResult
                    .PairingExpired
            }

            DisplayBindingResult.TimedOut -> {
                return LaunchSessionResult
                    .DisplayTimedOut
            }

            is DisplayBindingResult.Rejected -> {
                return LaunchSessionResult
                    .DisplayRejected(
                        bindingResult.reason
                    )
            }

            is DisplayBindingResult.Failure -> {
                return LaunchSessionResult
                    .Failure(
                        bindingResult.message
                    )
            }

            is DisplayBindingResult.Bound -> {
                // Continue to directory registration.
            }
        }

        val directoryEntry =
            SessionDirectoryEntry(
                joinCode =
                    liveSession.joinCode,
                sessionId =
                    liveSession.id,
                sessionName =
                    liveSession.name,
                hostUserId =
                    ownerUserId,
                displayId =
                    pairing.displayId,
                status =
                    SessionStatus.LIVE,
                displayMode =
                    liveSession.displayMode,
                createdAt =
                    liveSession.createdAt,
                actualStartedAt =
                    startedAt,
                expiresAt =
                    expiresAt
            )

        val registration =
            try {
                sessionDirectory.register(
                    directoryEntry
                )
            } catch (
                error: CancellationException
            ) {
                rollbackBoundLaunch(
                    session = liveSession,
                    ownerUserId = ownerUserId
                )
                throw error
            } catch (error: Exception) {
                rollbackBoundLaunch(
                    session = liveSession,
                    ownerUserId = ownerUserId
                )

                return LaunchSessionResult
                    .DirectoryFailure(
                        error.message
                            ?: "Unable to register the session code."
                    )
            }

        when (registration) {
            RegisterSessionResult.CodeTaken -> {
                rollbackBoundLaunch(
                    session = liveSession,
                    ownerUserId = ownerUserId
                )

                return LaunchSessionResult
                    .DirectoryCollision
            }

            is RegisterSessionResult.Failure -> {
                rollbackBoundLaunch(
                    session = liveSession,
                    ownerUserId = ownerUserId
                )

                return LaunchSessionResult
                    .DirectoryFailure(
                        registration.message
                    )
            }

            is RegisterSessionResult.Registered -> {
                // Continue to local activation.
            }
        }

        val hostMemberRow =
            sessionDao.getMember(
                sessionId = liveSession.id,
                userId = ownerUserId
            )

        if (hostMemberRow == null) {
            rollbackBoundLaunch(
                session = liveSession,
                ownerUserId = ownerUserId
            )

            return LaunchSessionResult.Failure(
                "The session host membership is missing."
            )
        }

        val hostMember =
            SessionMemberEntity(
                sessionId = hostMemberRow.sessionId,
                userId = hostMemberRow.userId,
                displayName = hostMemberRow.displayName,
                role = hostMemberRow.role,
                joinedAt = hostMemberRow.joinedAt
            )

        try {
            transactionRunner.inTransaction {
                sessionDao.upsertSession(
                    liveSession
                )

                sessionRealtimeSync
                    .publishSessionStarted(
                        session =
                            liveSession,
                        actorUserId =
                            ownerUserId
                    )

                sessionRealtimeSync
                    .publishMemberJoined(
                        session =
                            liveSession,
                        member =
                            hostMember
                    )
            }
        } catch (
            error: CancellationException
        ) {
            rollbackBoundLaunch(
                session = liveSession,
                ownerUserId = ownerUserId
            )
            throw error
        } catch (error: Exception) {
            rollbackBoundLaunch(
                session = liveSession,
                ownerUserId = ownerUserId
            )

            return LaunchSessionResult.Failure(
                error.message
                    ?: "Unable to activate the session."
            )
        }

        outboxDispatcher
            .requestImmediateDispatch()

        val activeSession =
            ActiveSession(
                sessionId =
                    liveSession.id,
                joinCode =
                    liveSession.joinCode,
                sessionName =
                    liveSession.name,
                userId =
                    ownerUserId,
                role =
                    SessionRole.HOST,
                joinedAt =
                    hostMember.joinedAt,
                scheduledStartAt =
                    liveSession
                        .scheduledStartAt,
                scheduledDurationMinutes =
                    liveSession
                        .scheduledDurationMinutes,
                actualStartedAt =
                    startedAt
            )

        activeSessionStore.setActiveSession(
            userId = ownerUserId,
            sessionId = liveSession.id
        )

        return LaunchSessionResult.Launched(
            activeSession = activeSession,
            invitation = invitation
        )
    }

    suspend fun openHostedSession(
        sessionId: String,
        ownerUserId: String
    ): ActiveSession {
        val session =
            requireHostedSession(
                sessionId = sessionId,
                ownerUserId = ownerUserId
            )

        check(
            session.status ==
                    SessionStatus.LIVE &&
                    session.actualEndedAt == null
        ) {
            "Only live sessions can be opened."
        }

        val owner =
            userDao.getUser(ownerUserId)
                ?: error("User not found.")

        val member =
            sessionDao.getMember(
                sessionId,
                ownerUserId
            )

        val joinedAt =
            member?.joinedAt
                ?: session.createdAt

        sessionDao.upsertMember(
            hostMembership(
                sessionId = sessionId,
                ownerUserId = ownerUserId,
                displayName =
                    owner.displayName,
                joinedAt = joinedAt
            )
        )

        return activateHostedSession(
            sessionId = sessionId,
            ownerUserId = ownerUserId,
            joinedAt = joinedAt
        )
    }

    suspend fun cancelScheduledSession(
        sessionId: String,
        ownerUserId: String
    ): Boolean {
        val session =
            requireHostedSession(
                sessionId = sessionId,
                ownerUserId = ownerUserId
            )

        check(
            session.actualStartedAt == null &&
                    session.actualEndedAt == null
        ) {
            "Only an unstarted session can be cancelled."
        }

        if (
            session.status ==
            SessionStatus.CANCELLED
        ) {
            return false
        }

        val now = TimeUtils.now()

        val cancelled =
            session.copy(
                status =
                    SessionStatus.CANCELLED,
                actualEndedAt = now,
                updatedAt = now
            )

        transactionRunner.inTransaction {
            sessionJoinRequestDao
                .deleteRequestsForSession(
                    sessionId
                )

            sessionDao.upsertSession(
                cancelled
            )
        }

        return true
    }

    suspend fun joinSession(
        joinCode: String,
        userId: String,
        displayName: String,
        requestedRole: SessionRole =
            SessionRole.PARTICIPANT
    ): JoinSessionResult {
        require(
            requestedRole != SessionRole.HOST
        ) {
            "Host access cannot be requested from the join screen."
        }

        val user =
            userDao.getUser(userId)
                ?: error("User not found.")

        val cleanCode =
            normalizeJoinCode(joinCode)

        val directoryEntry =
            when (
                val result =
                    sessionDirectory.resolve(
                        cleanCode
                    )
            ) {
                is ResolveJoinCodeResult.Found -> {
                    result.entry
                }

                ResolveJoinCodeResult.InvalidCode -> {
                    error(
                        "Session code must contain eight digits."
                    )
                }

                ResolveJoinCodeResult.NotFound -> {
                    error(
                        "No session found for this code."
                    )
                }

                ResolveJoinCodeResult.NotLive -> {
                    error(
                        "This session is not currently open."
                    )
                }

                ResolveJoinCodeResult.Expired -> {
                    error(
                        "This session code has expired."
                    )
                }

                is ResolveJoinCodeResult
                .UnsupportedVersion -> {

                    error(
                        "This session uses an unsupported " +
                                "invitation format. Update the " +
                                "app before joining."
                    )
                }

                is ResolveJoinCodeResult.Failure -> {
                    error(result.message)
                }
            }

        val existingSession =
            sessionDao.getSession(
                directoryEntry.sessionId
            )

        val session =
            directoryEntry.toSessionEntity(
                existing = existingSession
            )

        check(
            session.status ==
                    SessionStatus.LIVE
        ) {
            "This session is not currently open."
        }

        sessionDao.upsertSession(session)

        val now = TimeUtils.now()

        val cleanDisplayName =
            displayName
                .trim()
                .ifBlank {
                    user.displayName
                }

        return when (requestedRole) {
            SessionRole.PARTICIPANT -> {
                val member =
                    SessionMemberEntity(
                        sessionId =
                            session.id,
                        userId =
                            userId,
                        displayName =
                            cleanDisplayName,
                        role =
                            SessionRole
                                .PARTICIPANT,
                        joinedAt =
                            now
                    )

                transactionRunner.inTransaction {
                    sessionDao.upsertMember(
                        member
                    )

                    sessionRealtimeSync
                        .publishMemberJoined(
                            session =
                                session,
                            member =
                                member
                        )
                }

                activeSessionStore
                    .setActiveSession(
                        userId = userId,
                        sessionId =
                            session.id
                    )

                outboxDispatcher
                    .requestImmediateDispatch()

                JoinSessionResult.Joined(
                    activeSession =
                        ActiveSession(
                            sessionId =
                                session.id,
                            joinCode =
                                session.joinCode,
                            sessionName =
                                session.name,
                            userId =
                                userId,
                            role =
                                SessionRole
                                    .PARTICIPANT,
                            joinedAt =
                                now,
                            scheduledStartAt =
                                session
                                    .scheduledStartAt,
                            scheduledDurationMinutes =
                                session
                                    .scheduledDurationMinutes,
                            actualStartedAt =
                                session
                                    .actualStartedAt
                        )
                )
            }

            SessionRole.FACILITATOR -> {
                val pendingRequest =
                    sessionJoinRequestDao
                        .getPendingRequest(
                            sessionId =
                                session.id,
                            userId =
                                userId,
                            requestedRole =
                                SessionRole
                                    .FACILITATOR
                        )
                        ?: createJoinRequest(
                            sessionId =
                                session.id,
                            userId =
                                userId,
                            displayName =
                                cleanDisplayName,
                            requestedRole =
                                SessionRole
                                    .FACILITATOR
                        )

                JoinSessionResult
                    .AwaitingApproval(
                        request =
                            pendingRequest
                    )
            }

            SessionRole.HOST -> {
                error(
                    "Host access cannot be requested from the join screen."
                )
            }
        }
    }

    suspend fun leaveSession(
        userId: String,
        sessionId: String? = null
    ) {
        val leavingSessionId =
            sessionId
                ?: activeSessionStore
                    .observeActiveSessionId(
                        userId
                    )
                    .first()

        if (leavingSessionId != null) {
            val session =
                sessionDao.getSession(
                    leavingSessionId
                )

            val member =
                sessionDao.getMember(
                    leavingSessionId,
                    userId
                )

            if (
                session != null &&
                member != null &&
                member.role !=
                SessionRole.HOST &&
                session.status !=
                SessionStatus.ENDED &&
                session.status !=
                SessionStatus.CANCELLED
            ) {
                transactionRunner.inTransaction {
                    sessionDao.deleteMember(
                        leavingSessionId,
                        userId
                    )

                    sessionRealtimeSync
                        .publishMemberLeft(
                            session =
                                session,
                            member =
                                SessionMemberEntity(
                                    sessionId =
                                        leavingSessionId,
                                    userId =
                                        member.userId,
                                    displayName =
                                        member.displayName,
                                    role =
                                        member.role,
                                    joinedAt =
                                        member.joinedAt
                                ),
                            actorUserId =
                                userId
                        )
                }

                outboxDispatcher
                    .requestImmediateDispatch()
            }
        }

        activeSessionStore
            .clearActiveSession(userId)
    }

    suspend fun getJoinRequest(
        requestId: String
    ): SessionJoinRequestEntity? =
        sessionJoinRequestDao
            .getRequestById(requestId)

    suspend fun createJoinRequest(
        sessionId: String,
        userId: String,
        displayName: String,
        requestedRole: SessionRole
    ): SessionJoinRequestEntity {
        require(
            requestedRole != SessionRole.HOST
        ) {
            "Host access cannot be requested."
        }

        val user =
            userDao.getUser(userId)
                ?: error("User not found.")

        val now = TimeUtils.now()

        val request =
            SessionJoinRequestEntity(
                id = IdUtils.newId(),
                sessionId = sessionId,
                userId = userId,
                displayName =
                    displayName
                        .trim()
                        .ifBlank {
                            user.displayName
                        },
                requestedRole =
                    requestedRole,
                status =
                    JoinRequestStatus.PENDING,
                requestedAt = now
            )

        transactionRunner.inTransaction {
            sessionJoinRequestDao
                .upsertRequest(request)

            sessionRealtimeSync
                .publishFacilitatorRequested(
                    request,
                    userId
                )
        }

        outboxDispatcher
            .requestImmediateDispatch()

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
            requestedRole =
                SessionRole.FACILITATOR
        )

    suspend fun approveJoinRequest(
        requestId: String,
        decidedByUserId: String
    ): Boolean {
        val request =
            sessionJoinRequestDao
                .getRequestById(requestId)
                ?: return false

        val session =
            sessionDao.getSession(
                request.sessionId
            ) ?: return false

        check(
            session.hostUserId ==
                    decidedByUserId
        ) {
            "Only the session host may approve facilitator requests."
        }

        val existingMember =
            sessionDao.getMember(
                sessionId =
                    request.sessionId,
                userId =
                    request.userId
            )

        val now = TimeUtils.now()

        val approved =
            transactionRunner.inTransaction {
                val updated =
                    sessionJoinRequestDao
                        .approveRequest(
                            requestId =
                                requestId,
                            decidedByUserId =
                                decidedByUserId,
                            decidedAt =
                                now
                        )

                if (updated == 0) {
                    false
                } else {
                    val member =
                        SessionMemberEntity(
                            sessionId =
                                request.sessionId,
                            userId =
                                request.userId,
                            displayName =
                                request.displayName,
                            role =
                                request.requestedRole,
                            joinedAt =
                                existingMember
                                    ?.joinedAt
                                    ?: request
                                        .requestedAt
                        )

                    sessionDao.upsertMember(
                        member
                    )

                    val updatedRequest =
                        sessionJoinRequestDao
                            .getRequestById(
                                requestId
                            )
                            ?: request.copy(
                                status =
                                    JoinRequestStatus
                                        .APPROVED,
                                decidedAt =
                                    now,
                                decidedByUserId =
                                    decidedByUserId
                            )

                    val currentSession =
                        sessionDao.getSession(
                            request.sessionId
                        ) ?: session

                    sessionRealtimeSync
                        .publishFacilitatorApproved(
                            request =
                                updatedRequest,
                            member =
                                member,
                            session =
                                currentSession,
                            actorUserId =
                                decidedByUserId
                        )

                    sessionRealtimeSync
                        .publishMemberJoined(
                            session =
                                currentSession,
                            member =
                                member
                        )

                    true
                }
            }

        if (!approved) {
            return false
        }

        outboxDispatcher
            .requestImmediateDispatch()

        return true
    }

    suspend fun declineJoinRequest(
        requestId: String,
        decidedByUserId: String
    ): Boolean {
        val request =
            sessionJoinRequestDao
                .getRequestById(requestId)
                ?: return false

        val session =
            sessionDao.getSession(
                request.sessionId
            ) ?: return false

        check(
            session.hostUserId ==
                    decidedByUserId
        ) {
            "Only the session host may decline facilitator requests."
        }

        val now = TimeUtils.now()

        val declined =
            transactionRunner.inTransaction {
                val updated =
                    sessionJoinRequestDao
                        .declineRequest(
                            requestId =
                                requestId,
                            decidedByUserId =
                                decidedByUserId,
                            decidedAt =
                                now
                        )

                if (updated == 0) {
                    false
                } else {
                    val updatedRequest =
                        sessionJoinRequestDao
                            .getRequestById(
                                requestId
                            )
                            ?: request.copy(
                                status =
                                    JoinRequestStatus
                                        .DECLINED,
                                decidedAt =
                                    now,
                                decidedByUserId =
                                    decidedByUserId
                            )

                    sessionRealtimeSync
                        .publishFacilitatorDeclined(
                            request =
                                updatedRequest,
                            session =
                                session,
                            actorUserId =
                                decidedByUserId
                        )

                    true
                }
            }

        if (declined) {
            outboxDispatcher
                .requestImmediateDispatch()
        }

        return declined
    }

    suspend fun cancelJoinRequest(
        requestId: String
    ): Boolean {
        val request =
            sessionJoinRequestDao
                .getRequestById(requestId)
                ?: return false

        val now = TimeUtils.now()

        val cancelled =
            transactionRunner.inTransaction {
                val updated =
                    sessionJoinRequestDao
                        .cancelRequest(
                            requestId =
                                requestId,
                            decidedAt =
                                now
                        )

                if (updated == 0) {
                    false
                } else {
                    val updatedRequest =
                        sessionJoinRequestDao
                            .getRequestById(
                                requestId
                            )
                            ?: request.copy(
                                status =
                                    JoinRequestStatus
                                        .CANCELLED,
                                decidedAt =
                                    now
                            )

                    sessionRealtimeSync
                        .publishFacilitatorCancelled(
                            updatedRequest,
                            request.userId
                        )

                    true
                }
            }

        if (cancelled) {
            outboxDispatcher
                .requestImmediateDispatch()
        }

        return cancelled
    }

    suspend fun activateApprovedFacilitatorRequest(
        requestId: String,
        userId: String
    ): ActiveSession? {
        val request =
            sessionJoinRequestDao
                .getRequestById(requestId)
                ?: return null

        if (
            request.userId != userId ||
            request.status !=
            JoinRequestStatus.APPROVED
        ) {
            return null
        }

        val session =
            sessionDao.getSession(
                request.sessionId
            ) ?: return null

        val member =
            sessionDao.getMember(
                sessionId =
                    request.sessionId,
                userId =
                    userId
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
            scheduledStartAt =
                session.scheduledStartAt,
            scheduledDurationMinutes =
                session
                    .scheduledDurationMinutes,
            actualStartedAt =
                session.actualStartedAt
        )
    }

    suspend fun endSession(
        sessionId: String,
        actorUserId: String
    ) {
        val session =
            sessionDao.getSession(sessionId)
                ?: return

        check(
            session.hostUserId ==
                    actorUserId
        ) {
            "Only the session host may end the session."
        }

        if (
            session.status ==
            SessionStatus.ENDED
        ) {
            return
        }

        val now = TimeUtils.now()

        val ended =
            session.copy(
                status =
                    SessionStatus.ENDED,
                actualEndedAt = now,
                updatedAt = now
            )

        transactionRunner.inTransaction {
            sessionDao.upsertSession(
                ended
            )

            sessionRealtimeSync
                .publishSessionEnded(
                    ended,
                    actorUserId
                )
        }

        outboxDispatcher
            .requestImmediateDispatch()

        sessionDirectory.remove(
            joinCode = ended.joinCode,
            sessionId = ended.id
        )
    }

    suspend fun updateSessionDisplayMode(
        sessionId: String,
        actorUserId: String,
        displayMode: DisplayMode
    ): SessionEntity {
        val session =
            sessionDao.getSession(sessionId)
                ?: error(
                    "Session not found."
                )

        val member =
            sessionDao.getMember(
                sessionId,
                actorUserId
            ) ?: error(
                "Only session members may update this session."
            )

        check(
            member.role !=
                    SessionRole.PARTICIPANT
        ) {
            "Only facilitators or the host may update display mode."
        }

        val updated =
            session.copy(
                displayMode = displayMode,
                updatedAt = TimeUtils.now()
            )

        transactionRunner.inTransaction {
            sessionDao.upsertSession(
                updated
            )

            sessionRealtimeSync
                .publishSessionSettingsChanged(
                    updated,
                    actorUserId
                )

            sessionRealtimeSync
                .publishDisplayModeChanged(
                    sessionId = sessionId,
                    actorUserId =
                        actorUserId,
                    displayMode =
                        displayMode,
                    currentMessageId = null,
                    isPinned = false,
                    origin = null
                )
        }

        outboxDispatcher
            .requestImmediateDispatch()

        return updated
    }

    suspend fun seedDemoParticipants(
        sessionId: String
    ) {
        val now = TimeUtils.now()

        val demoMembers =
            listOf(
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

    private fun normalizeJoinCode(
        raw: String
    ): String {
        val digits =
            raw.filter(Char::isDigit)

        require(digits.length == 8) {
            "Session code must contain eight digits."
        }

        return formatJoinCode(digits)
    }

    private suspend fun requireHostedSession(
        sessionId: String,
        ownerUserId: String
    ): SessionEntity {
        val session =
            sessionDao.getSession(sessionId)
                ?: error(
                    "Session not found."
                )

        check(
            session.hostUserId ==
                    ownerUserId
        ) {
            "Only the session host may manage this session."
        }

        return session
    }

    private suspend fun activateHostedSession(
        sessionId: String,
        ownerUserId: String,
        joinedAt: Long
    ): ActiveSession {
        val session =
            sessionDao.getSession(sessionId)
                ?: error(
                    "Session not found."
                )

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
            scheduledStartAt =
                session.scheduledStartAt,
            scheduledDurationMinutes =
                session
                    .scheduledDurationMinutes,
            actualStartedAt =
                session.actualStartedAt
        )
    }

    private fun hostMembership(
        sessionId: String,
        ownerUserId: String,
        displayName: String,
        joinedAt: Long
    ): SessionMemberEntity =
        SessionMemberEntity(
            sessionId = sessionId,
            userId = ownerUserId,
            displayName = displayName,
            role = SessionRole.HOST,
            joinedAt = joinedAt
        )

    private fun startOfTodayMillis(): Long {
        val zone =
            ZoneId.systemDefault()

        return LocalDate.now(zone)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    }

    private fun SessionDirectoryEntry
            .toSessionEntity(
        existing: SessionEntity? = null
    ): SessionEntity {

        return SessionEntity(
            id = sessionId,
            name = sessionName,
            joinCode =
                formatJoinCode(joinCode),
            hostUserId = hostUserId,
            status = status,
            displayMode = displayMode,
            displayId = displayId,
            createdAt =
                existing?.createdAt
                    ?: createdAt,
            scheduledStartAt =
                existing?.scheduledStartAt,
            scheduledDurationMinutes =
                existing
                    ?.scheduledDurationMinutes,
            actualStartedAt =
                actualStartedAt,
            actualEndedAt =
                existing?.actualEndedAt,
            expiresAt = expiresAt,
            updatedAt = TimeUtils.now()
        )
    }

    private suspend fun defaultDisplayModeForUser(
        userId: String
    ): DisplayMode {
        val settings =
            userDao.getSettings(userId)

        return if (
            settings
                ?.monitorRequireManualApproval ==
            true
        ) {
            DisplayMode.APPROVAL_REQUIRED
        } else {
            DisplayMode.AUTO_LATEST
        }
    }

    private sealed interface AvailableJoinCodeResult {

        data class Available(
            val joinCode: String
        ) : AvailableJoinCodeResult

        data class Failure(
            val message: String
        ) : AvailableJoinCodeResult
    }

    private suspend fun selectAvailableJoinCode(
        initialCode: String,
        sessionId: String,
        maximumAttempts: Int = 10
    ): AvailableJoinCodeResult {
        var candidate = initialCode

        repeat(maximumAttempts) {
            when (
                val result =
                    sessionDirectory.resolve(
                        candidate
                    )
            ) {
                ResolveJoinCodeResult.NotFound -> {
                    return AvailableJoinCodeResult
                        .Available(candidate)
                }

                is ResolveJoinCodeResult.Found -> {
                    if (
                        result.entry.sessionId ==
                        sessionId
                    ) {
                        sessionDirectory.remove(
                            joinCode = candidate,
                            sessionId = sessionId
                        )

                        return AvailableJoinCodeResult
                            .Available(candidate)
                    }

                    candidate =
                        joinCodeGenerator()
                }

                ResolveJoinCodeResult.InvalidCode,
                ResolveJoinCodeResult.NotLive,
                ResolveJoinCodeResult.Expired,
                is ResolveJoinCodeResult
                .UnsupportedVersion -> {

                    candidate =
                        joinCodeGenerator()
                }

                is ResolveJoinCodeResult.Failure -> {
                    return AvailableJoinCodeResult
                        .Failure(
                            result.message
                        )
                }
            }
        }

        return AvailableJoinCodeResult.Failure(
            "Unable to find an available session code."
        )
    }

    private suspend fun rollbackBoundLaunch(
        session: SessionEntity,
        ownerUserId: String
    ) {
        val displayId =
            session.displayId
                ?: return

        withContext(NonCancellable) {
            try {
                sessionDirectory.remove(
                    joinCode =
                        session.joinCode,
                    sessionId =
                        session.id
                )
            } catch (_: Exception) {
                // Best-effort cleanup.
            }

            try {
                displayBindingCoordinator.unbind(
                    displayId = displayId,
                    sessionId = session.id,
                    requestedByUserId =
                        ownerUserId
                )
            } catch (_: Exception) {
                // Best-effort cleanup.
            }
        }
    }
}

private val joinCodeRandom =
    SecureRandom()

private fun generateJoinCode(): String {
    val number =
        joinCodeRandom.nextInt(
            90_000_000
        ) + 10_000_000

    return formatJoinCode(
        number.toString()
    )
}
