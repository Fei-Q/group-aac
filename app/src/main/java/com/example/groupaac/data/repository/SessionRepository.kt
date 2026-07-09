package com.example.groupaac.data.repository

import com.example.groupaac.data.dao.SessionDao
import com.example.groupaac.data.dao.UserDao
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.BuildConfig
import com.example.groupaac.data.pi.PiClient
import com.example.groupaac.data.pi.PiJoinRequest
import com.example.groupaac.data.prefs.AppPreferences
import com.example.groupaac.model.UserRole
import com.example.groupaac.util.IdUtils
import com.example.groupaac.util.TimeUtils
import kotlinx.coroutines.flow.Flow

class SessionRepository(
    private val sessionDao: SessionDao,
    private val userDao: UserDao,
    private val preferences: AppPreferences,
    private val piClient: PiClient
) {
    val lastSessionId: Flow<String?> = preferences.lastSessionId

    fun observeSession(sessionId: String): Flow<SessionEntity?> = sessionDao.observeSession(sessionId)
    fun observeMembers(sessionId: String): Flow<List<com.example.groupaac.data.dao.SessionParticipantRow>> = sessionDao.observeMembers(sessionId)

    suspend fun createSession(name: String, ownerUserId: String, displayName: String, role: UserRole): String {
        val owner = userDao.getUser(ownerUserId) ?: error("User not found")
        val now = TimeUtils.now()
        val session = SessionEntity(
            id = IdUtils.newId(),
            name = name.ifBlank { "Group Meeting" },
            joinCode = generateJoinCode(),
            createdAt = now
        )
        sessionDao.createOrJoinSession(
            session,
            SessionMemberEntity(session.id, ownerUserId, displayName.ifBlank { owner.displayName }, role, now)
        )
        if (BuildConfig.DEBUG) {
            seedDemoParticipants(session.id)
        }
        preferences.setLastSession(session.id)
        piClient.joinSession(PiJoinRequest(session.joinCode, ownerUserId, displayName.ifBlank { owner.displayName }, role))
        return session.id
    }

    suspend fun joinSession(joinCode: String, userId: String, displayName: String, role: UserRole): String {
        val user = userDao.getUser(userId) ?: error("User not found")
        val cleanCode = joinCode.trim().ifBlank { "1234-5678" }
        val session = sessionDao.getSessionByCode(cleanCode) ?: SessionEntity(
            id = IdUtils.newId(),
            name = "Group AAC Session",
            joinCode = cleanCode,
            createdAt = TimeUtils.now()
        )
        sessionDao.createOrJoinSession(
            session,
            SessionMemberEntity(session.id, userId, displayName.ifBlank { user.displayName }, role, TimeUtils.now())
        )
        preferences.setLastSession(session.id)
        piClient.joinSession(PiJoinRequest(cleanCode, userId, displayName.ifBlank { user.displayName }, role))
        return session.id
    }

    suspend fun seedDemoParticipants(sessionId: String) {
        val now = TimeUtils.now()
        val demo = listOf(
            SessionMemberEntity(sessionId, "demo-alice", "Alice", UserRole.PARTICIPANT, now - 100_000),
            SessionMemberEntity(sessionId, "demo-bob", "Bob", UserRole.PARTICIPANT, now - 90_000),
            SessionMemberEntity(sessionId, "demo-eve", "Eve", UserRole.PARTICIPANT, now - 80_000),
            SessionMemberEntity(sessionId, "demo-mary", "Mary", UserRole.PARTICIPANT, now - 70_000)
        )
        demo.forEach { user ->
            sessionDao.upsertMember(user)
        }
    }

    private fun generateJoinCode(): String {
        val a = (1000..9999).random()
        val b = (1000..9999).random()
        return "$a-$b"
    }
}
