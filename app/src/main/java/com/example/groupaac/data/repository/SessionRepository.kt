package com.example.groupaac.data.repository

import com.example.groupaac.data.dao.SessionDao
import com.example.groupaac.data.dao.SessionParticipantRow
import com.example.groupaac.data.dao.UserDao
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.data.pi.PiClient
import com.example.groupaac.data.pi.PiJoinRequest
import com.example.groupaac.data.session.ActiveSessionStore
import com.example.groupaac.model.ActiveSession
import com.example.groupaac.model.UserRole
import com.example.groupaac.util.IdUtils
import com.example.groupaac.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class SessionRepository(
    private val sessionDao: SessionDao,
    private val userDao: UserDao,
    private val activeSessionStore: ActiveSessionStore,
    private val piClient: PiClient
) {
    fun observeSession(
        sessionId: String
    ): Flow<SessionEntity?> = sessionDao.observeSession(sessionId)

    fun observeMembers(
        sessionId: String
    ): Flow<List<SessionParticipantRow>> =
        sessionDao.observeMembers(sessionId)

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
        displayName: String,
        role: UserRole
    ): ActiveSession {
        require(role == UserRole.FACILITATOR) {
            "Only facilitators can create sessions."
        }

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
            createdAt = now,
            actualStartedAt = now
        )
        val member = SessionMemberEntity(
            sessionId = session.id,
            userId = ownerUserId,
            displayName = cleanDisplayName,
            role = role,
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
                role = role
            )
        )

        return ActiveSession(
            sessionId = session.id,
            joinCode = session.joinCode,
            sessionName = session.name,
            userId = ownerUserId,
            role = role,
            joinedAt = now
        )
    }

    suspend fun joinSession(
        joinCode: String,
        userId: String,
        displayName: String,
        role: UserRole
    ): ActiveSession {
        val user = userDao.getUser(userId)
            ?: error("User not found.")
        val cleanCode = normalizeJoinCode(joinCode)
        val session = sessionDao.getSessionByCode(cleanCode)
            ?: error("No session found for this code.")

        check(session.actualEndedAt == null) {
            "This session has already ended."
        }

        val now = TimeUtils.now()
        val cleanDisplayName = displayName
            .trim()
            .ifBlank { user.displayName }
        val member = SessionMemberEntity(
            sessionId = session.id,
            userId = userId,
            displayName = cleanDisplayName,
            role = role,
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
                role = role
            )
        )

        return ActiveSession(
            sessionId = session.id,
            joinCode = session.joinCode,
            sessionName = session.name,
            userId = userId,
            role = role,
            joinedAt = now
        )
    }

    suspend fun leaveSession(userId: String) {
        activeSessionStore.clearActiveSession(userId)
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
                UserRole.PARTICIPANT,
                now - 100_000
            ),
            SessionMemberEntity(
                sessionId,
                "demo-bob",
                "Bob",
                UserRole.PARTICIPANT,
                now - 90_000
            ),
            SessionMemberEntity(
                sessionId,
                "demo-eve",
                "Eve",
                UserRole.PARTICIPANT,
                now - 80_000
            ),
            SessionMemberEntity(
                sessionId,
                "demo-mary",
                "Mary",
                UserRole.PARTICIPANT,
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
