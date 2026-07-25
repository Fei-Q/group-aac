package com.example.groupaac.data.repository

import com.example.groupaac.data.dao.SessionDao
import com.example.groupaac.data.dao.SessionJoinRequestDao
import com.example.groupaac.data.dao.SessionParticipantRow
import com.example.groupaac.data.dao.UserDao
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionJoinRequestEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.data.pi.PiClient
import com.example.groupaac.data.pi.PiJoinRequest
import com.example.groupaac.data.session.ActiveSessionStore
import com.example.groupaac.model.ActiveSession
import com.example.groupaac.model.JoinRequestStatus
import com.example.groupaac.model.JoinSessionResult
import com.example.groupaac.model.SessionRole
import com.example.groupaac.util.IdUtils
import com.example.groupaac.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class SessionRepository(
    private val sessionDao: SessionDao,
    private val sessionJoinRequestDao: SessionJoinRequestDao,
    private val userDao: UserDao,
    private val activeSessionStore: ActiveSessionStore,
    private val piClient: PiClient
) {
    fun observeSession(
        sessionId: String
    ): Flow<SessionEntity?> = sessionDao.observeSession(sessionId)

    fun observeSessions(): Flow<List<SessionEntity>> =
        sessionDao.observeSessions()

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
                                joinedAt = member.joinedAt
                            )
                        }
                    }
                }
            }
    }

    suspend fun createSession(
        name: String,
        ownerUserId: String,
        displayName: String
    ): ActiveSession {
        val owner = userDao.getUser(ownerUserId)
            ?: error("User not found.")
        val now = TimeUtils.now()
        val cleanDisplayName = displayName
            .trim()
            .ifBlank { owner.displayName }
        val session = SessionEntity(
            id = IdUtils.newId(),
            name = name.trim().ifBlank { "Group Meeting" },
            joinCode = generateUniqueJoinCode(),
            hostUserId = ownerUserId,
            createdAt = now,
            // This code path is used by the current "Create Now" flow.
            actualStartedAt = now
        )
        val member = SessionMemberEntity(
            sessionId = session.id,
            userId = ownerUserId,
            displayName = cleanDisplayName,
            role = SessionRole.HOST,
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

        piClient.joinSession(
            PiJoinRequest(
                sessionCode = session.joinCode,
                userId = ownerUserId,
                displayName = cleanDisplayName,
                role = SessionRole.HOST
            )
        )

        return ActiveSession(
            sessionId = session.id,
            joinCode = session.joinCode,
            sessionName = session.name,
            userId = ownerUserId,
            role = SessionRole.HOST,
            joinedAt = now
        )
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
        val session = sessionDao.getSessionByCode(cleanCode)
            ?: error("No session found for this code.")
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

                piClient.joinSession(
                    PiJoinRequest(
                        sessionCode = cleanCode,
                        userId = userId,
                        displayName = cleanDisplayName,
                        role = SessionRole.PARTICIPANT
                    )
                )

                JoinSessionResult.Joined(
                    activeSession = ActiveSession(
                        sessionId = session.id,
                        joinCode = session.joinCode,
                        sessionName = session.name,
                        userId = userId,
                        role = SessionRole.PARTICIPANT,
                        joinedAt = now
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
        return sessionJoinRequestDao.declineRequest(
            requestId = requestId,
            decidedByUserId = decidedByUserId,
            decidedAt = TimeUtils.now()
        ) > 0
    }

    suspend fun cancelJoinRequest(requestId: String): Boolean {
        return sessionJoinRequestDao.cancelRequest(
            requestId = requestId,
            decidedAt = TimeUtils.now()
        ) > 0
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

        piClient.joinSession(
            PiJoinRequest(
                sessionCode = session.joinCode,
                userId = userId,
                displayName = member.displayName,
                role = member.role
            )
        )

        return ActiveSession(
            sessionId = session.id,
            joinCode = session.joinCode,
            sessionName = session.name,
            userId = userId,
            role = member.role,
            joinedAt = member.joinedAt
        )
    }

    suspend fun endSession(sessionId: String) {
        sessionDao.markSessionEnded(sessionId)
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

    private suspend fun generateUniqueJoinCode(): String {
        repeat(20) {
            val candidate = generateJoinCode()
            if (sessionDao.countSessionsByJoinCode(candidate) == 0) {
                return candidate
            }
        }

        error("Unable to generate a unique session code.")
    }

    private fun generateJoinCode(): String {
        val firstHalf = (1000..9999).random()
        val secondHalf = (1000..9999).random()
        return "$firstHalf-$secondHalf"
    }

    private fun normalizeJoinCode(raw: String): String {
        val digits = raw.filter(Char::isDigit)

        require(digits.length == 8) {
            "Session code must contain eight digits."
        }

        return "${digits.take(4)}-${digits.drop(4)}"
    }
}
