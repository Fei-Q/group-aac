package com.example.groupaac.data.repository

import com.example.groupaac.data.dao.SessionDao
import com.example.groupaac.data.dao.SessionJoinRequestDao
import com.example.groupaac.data.dao.SessionParticipantRow
import com.example.groupaac.data.dao.UserDao
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionJoinRequestEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.entity.UserSettingsEntity
import com.example.groupaac.data.pi.DisplayCommand
import com.example.groupaac.data.pi.PiClient
import com.example.groupaac.data.pi.PiJoinRequest
import com.example.groupaac.data.pi.PiMessagePayload
import com.example.groupaac.data.pi.PiSessionEvent
import com.example.groupaac.data.pi.PiSignalPayload
import com.example.groupaac.data.session.ActiveSessionStore
import com.example.groupaac.model.JoinRequestStatus
import com.example.groupaac.model.JoinSessionResult
import com.example.groupaac.model.SessionRole
import com.example.groupaac.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRepositoryTest {
    @Test
    fun participantImmediateJoin() = runTest {
        val fixture = sessionFixture()

        val result = fixture.repository.joinSession(
            joinCode = fixture.session.joinCode,
            userId = fixture.participant.id,
            displayName = fixture.participant.displayName,
            requestedRole = SessionRole.PARTICIPANT
        )

        assertTrue(result is JoinSessionResult.Joined)
        val joined = result as JoinSessionResult.Joined
        assertEquals(SessionRole.PARTICIPANT, joined.activeSession.role)
        assertEquals(
            fixture.session.id,
            fixture.activeSessionStore.activeSessions[fixture.participant.id]
        )
        assertNotNull(
            fixture.sessionDao.getMember(
                fixture.session.id,
                fixture.participant.id
            )
        )
        assertTrue(fixture.joinRequestDao.requests.isEmpty())
    }

    @Test
    fun facilitatorPendingRequest() = runTest {
        val fixture = sessionFixture()

        val first = fixture.repository.joinSession(
            joinCode = fixture.session.joinCode,
            userId = fixture.facilitator.id,
            displayName = fixture.facilitator.displayName,
            requestedRole = SessionRole.FACILITATOR
        )
        val second = fixture.repository.joinSession(
            joinCode = fixture.session.joinCode,
            userId = fixture.facilitator.id,
            displayName = fixture.facilitator.displayName,
            requestedRole = SessionRole.FACILITATOR
        )

        assertTrue(first is JoinSessionResult.AwaitingApproval)
        assertTrue(second is JoinSessionResult.AwaitingApproval)
        val firstRequest = (first as JoinSessionResult.AwaitingApproval).request
        val secondRequest = (second as JoinSessionResult.AwaitingApproval).request
        assertEquals(firstRequest.id, secondRequest.id)
        assertEquals(JoinRequestStatus.PENDING, firstRequest.status)
        assertNull(
            fixture.sessionDao.getMember(
                fixture.session.id,
                fixture.facilitator.id
            )
        )
        assertNull(
            fixture.activeSessionStore.activeSessions[fixture.facilitator.id]
        )
    }

    @Test
    fun onlyHostCanApprove() = runTest {
        val fixture = sessionFixture()
        val pending = fixture.repository.joinSession(
            joinCode = fixture.session.joinCode,
            userId = fixture.facilitator.id,
            displayName = fixture.facilitator.displayName,
            requestedRole = SessionRole.FACILITATOR
        ) as JoinSessionResult.AwaitingApproval

        var threw = false
        try {
            fixture.repository.approveJoinRequest(
                requestId = pending.request.id,
                decidedByUserId = fixture.participant.id
            )
        } catch (_: IllegalStateException) {
            threw = true
        }

        assertTrue(threw)

        assertNull(
            fixture.sessionDao.getMember(
                fixture.session.id,
                fixture.facilitator.id
            )
        )
    }

    @Test
    fun approvalCreatesFacilitatorMembership() = runTest {
        val fixture = sessionFixture()
        val pending = fixture.repository.joinSession(
            joinCode = fixture.session.joinCode,
            userId = fixture.facilitator.id,
            displayName = fixture.facilitator.displayName,
            requestedRole = SessionRole.FACILITATOR
        ) as JoinSessionResult.AwaitingApproval

        val approved = fixture.repository.approveJoinRequest(
            requestId = pending.request.id,
            decidedByUserId = fixture.host.id
        )

        assertTrue(approved)
        val member = fixture.sessionDao.getMember(
            fixture.session.id,
            fixture.facilitator.id
        )
        assertNotNull(member)
        assertEquals(SessionRole.FACILITATOR, member?.role)
        assertEquals(
            JoinRequestStatus.APPROVED,
            fixture.joinRequestDao.getRequestById(pending.request.id)?.status
        )
    }

    @Test
    fun declineCreatesNoMembership() = runTest {
        val fixture = sessionFixture()
        val pending = fixture.repository.joinSession(
            joinCode = fixture.session.joinCode,
            userId = fixture.facilitator.id,
            displayName = fixture.facilitator.displayName,
            requestedRole = SessionRole.FACILITATOR
        ) as JoinSessionResult.AwaitingApproval

        val declined = fixture.repository.declineJoinRequest(
            requestId = pending.request.id,
            decidedByUserId = fixture.host.id
        )

        assertTrue(declined)
        assertNull(
            fixture.sessionDao.getMember(
                fixture.session.id,
                fixture.facilitator.id
            )
        )
        assertEquals(
            JoinRequestStatus.DECLINED,
            fixture.joinRequestDao.getRequestById(pending.request.id)?.status
        )
    }

    private fun sessionFixture(): SessionFixture {
        val sessionDao = FakeSessionDao()
        val joinRequestDao = FakeSessionJoinRequestDao()
        val userDao = FakeUserDao()
        val activeSessionStore = FakeActiveSessionStore()
        val piClient = RecordingPiClient()

        val host = UserEntity(
            id = "host-1",
            displayName = "Host",
            role = UserRole.PARTICIPANT,
            createdAt = 1L
        )
        val participant = UserEntity(
            id = "participant-1",
            displayName = "Participant",
            role = UserRole.PARTICIPANT,
            createdAt = 2L
        )
        val facilitator = UserEntity(
            id = "facilitator-1",
            displayName = "Facilitator",
            role = UserRole.PARTICIPANT,
            createdAt = 3L
        )
        userDao.seed(host, participant, facilitator)

        val session = SessionEntity(
            id = "session-1",
            name = "Friday Group",
            joinCode = "1234-5678",
            hostUserId = host.id,
            createdAt = 10L,
            actualStartedAt = 10L
        )
        sessionDao.seedSession(session)
        sessionDao.seedMember(
            SessionMemberEntity(
                sessionId = session.id,
                userId = host.id,
                displayName = host.displayName,
                role = SessionRole.HOST,
                joinedAt = 10L
            )
        )

        val repository = SessionRepository(
            sessionDao = sessionDao,
            sessionJoinRequestDao = joinRequestDao,
            userDao = userDao,
            activeSessionStore = activeSessionStore,
            piClient = piClient
        )

        return SessionFixture(
            repository = repository,
            sessionDao = sessionDao,
            joinRequestDao = joinRequestDao,
            activeSessionStore = activeSessionStore,
            session = session,
            host = host,
            participant = participant,
            facilitator = facilitator
        )
    }
}

private data class SessionFixture(
    val repository: SessionRepository,
    val sessionDao: FakeSessionDao,
    val joinRequestDao: FakeSessionJoinRequestDao,
    val activeSessionStore: FakeActiveSessionStore,
    val session: SessionEntity,
    val host: UserEntity,
    val participant: UserEntity,
    val facilitator: UserEntity
)

private class FakeSessionDao : SessionDao {
    private val sessions = linkedMapOf<String, SessionEntity>()
    private val sessionsFlow = MutableStateFlow<List<SessionEntity>>(emptyList())
    private val members =
        linkedMapOf<Pair<String, String>, SessionMemberEntity>()

    fun seedSession(session: SessionEntity) {
        sessions[session.id] = session
        sessionsFlow.value = sessions.values.toList()
    }

    fun seedMember(member: SessionMemberEntity) {
        members[member.sessionId to member.userId] = member
    }

    override fun observeSessions(): Flow<List<SessionEntity>> = sessionsFlow

    override fun observeSession(id: String): Flow<SessionEntity?> =
        flowOf(sessions[id])

    override suspend fun getSessionByCode(joinCode: String): SessionEntity? =
        sessions.values.firstOrNull { it.joinCode == joinCode }

    override suspend fun countSessionsByJoinCode(joinCode: String): Int =
        sessions.values.count { it.joinCode == joinCode }

    override suspend fun getSession(id: String): SessionEntity? =
        sessions[id]

    override suspend fun upsertSession(session: SessionEntity) {
        seedSession(session)
    }

    override suspend fun markSessionStartedIfNeeded(
        sessionId: String,
        startedAt: Long
    ) {
        val session = sessions[sessionId] ?: return
        if (session.actualStartedAt == null) {
            seedSession(session.copy(actualStartedAt = startedAt))
        }
    }

    override suspend fun markSessionEnded(
        sessionId: String,
        endedAt: Long
    ) {
        val session = sessions[sessionId] ?: return
        seedSession(session.copy(actualEndedAt = endedAt))
    }

    override suspend fun updateSchedule(
        sessionId: String,
        scheduledStartAt: Long?,
        scheduledDurationMinutes: Int?
    ) {
        val session = sessions[sessionId] ?: return
        seedSession(
            session.copy(
                scheduledStartAt = scheduledStartAt,
                scheduledDurationMinutes = scheduledDurationMinutes
            )
        )
    }

    override suspend fun upsertMember(member: SessionMemberEntity) {
        members[member.sessionId to member.userId] = member
    }

    override fun observeMemberIds(sessionId: String): Flow<List<String>> =
        flowOf(
            members.values
                .filter { it.sessionId == sessionId }
                .map { it.userId }
        )

    override fun observeMembers(sessionId: String): Flow<List<SessionParticipantRow>> =
        flowOf(
            members.values
                .filter { it.sessionId == sessionId }
                .sortedBy { it.joinedAt }
                .map {
                    SessionParticipantRow(
                        sessionId = it.sessionId,
                        userId = it.userId,
                        displayName = it.displayName,
                        role = it.role,
                        joinedAt = it.joinedAt
                    )
                }
        )

    override fun observeMember(
        sessionId: String,
        userId: String
    ): Flow<SessionParticipantRow?> = flowOf(
        getMemberRow(sessionId, userId)
    )

    override suspend fun getMember(
        sessionId: String,
        userId: String
    ): SessionParticipantRow? = getMemberRow(sessionId, userId)

    private fun getMemberRow(
        sessionId: String,
        userId: String
    ): SessionParticipantRow? {
        val member = members[sessionId to userId] ?: return null
        return SessionParticipantRow(
            sessionId = member.sessionId,
            userId = member.userId,
            displayName = member.displayName,
            role = member.role,
            joinedAt = member.joinedAt
        )
    }
}

private class FakeSessionJoinRequestDao : SessionJoinRequestDao {
    val requests = linkedMapOf<String, SessionJoinRequestEntity>()

    override suspend fun upsertRequest(request: SessionJoinRequestEntity) {
        requests[request.id] = request
    }

    override fun observePendingBySession(
        sessionId: String,
        pendingStatus: JoinRequestStatus
    ): Flow<List<SessionJoinRequestEntity>> = flowOf(
        requests.values
            .filter {
                it.sessionId == sessionId &&
                    it.status == pendingStatus
            }
            .sortedBy { it.requestedAt }
    )

    override fun observeRequestById(
        requestId: String
    ): Flow<SessionJoinRequestEntity?> = flowOf(requests[requestId])

    override suspend fun getRequestById(
        requestId: String
    ): SessionJoinRequestEntity? = requests[requestId]

    override suspend fun getPendingRequest(
        sessionId: String,
        userId: String,
        requestedRole: SessionRole,
        pendingStatus: JoinRequestStatus
    ): SessionJoinRequestEntity? =
        requests.values
            .filter {
                it.sessionId == sessionId &&
                    it.userId == userId &&
                    it.requestedRole == requestedRole &&
                    it.status == pendingStatus
            }
            .maxByOrNull { it.requestedAt }

    override suspend fun approveRequest(
        requestId: String,
        decidedByUserId: String,
        decidedAt: Long,
        approvedStatus: JoinRequestStatus,
        pendingStatus: JoinRequestStatus
    ): Int = updateStatus(
        requestId = requestId,
        expectedStatus = pendingStatus,
        newStatus = approvedStatus,
        decidedAt = decidedAt,
        decidedByUserId = decidedByUserId
    )

    override suspend fun declineRequest(
        requestId: String,
        decidedByUserId: String,
        decidedAt: Long,
        declinedStatus: JoinRequestStatus,
        pendingStatus: JoinRequestStatus
    ): Int = updateStatus(
        requestId = requestId,
        expectedStatus = pendingStatus,
        newStatus = declinedStatus,
        decidedAt = decidedAt,
        decidedByUserId = decidedByUserId
    )

    override suspend fun cancelRequest(
        requestId: String,
        decidedAt: Long,
        cancelledStatus: JoinRequestStatus,
        pendingStatus: JoinRequestStatus
    ): Int = updateStatus(
        requestId = requestId,
        expectedStatus = pendingStatus,
        newStatus = cancelledStatus,
        decidedAt = decidedAt,
        decidedByUserId = null
    )

    private fun updateStatus(
        requestId: String,
        expectedStatus: JoinRequestStatus,
        newStatus: JoinRequestStatus,
        decidedAt: Long,
        decidedByUserId: String?
    ): Int {
        val existing = requests[requestId] ?: return 0
        if (existing.status != expectedStatus) {
            return 0
        }

        requests[requestId] = existing.copy(
            status = newStatus,
            decidedAt = decidedAt,
            decidedByUserId = decidedByUserId
        )
        return 1
    }
}

private class FakeUserDao : UserDao {
    private val users = linkedMapOf<String, UserEntity>()
    private val settings = linkedMapOf<String, UserSettingsEntity>()

    fun seed(vararg seededUsers: UserEntity) {
        seededUsers.forEach { user ->
            users[user.id] = user
        }
    }

    override fun observeUsers(): Flow<List<UserEntity>> =
        flowOf(users.values.toList())

    override fun observeUser(id: String): Flow<UserEntity?> =
        flowOf(users[id])

    override suspend fun getUser(id: String): UserEntity? = users[id]

    override suspend fun upsertUser(user: UserEntity) {
        users[user.id] = user
    }

    override suspend fun updateLastLogin(userId: String, timestamp: Long) {
        val user = users[userId] ?: return
        users[userId] = user.copy(lastLoginAt = timestamp)
    }

    override fun observeSettings(userId: String): Flow<UserSettingsEntity?> =
        flowOf(settings[userId])

    override suspend fun getSettings(userId: String): UserSettingsEntity? =
        settings[userId]

    override suspend fun upsertSettings(settings: UserSettingsEntity) {
        this.settings[settings.userId] = settings
    }

    override suspend fun updateTextScale(
        userId: String,
        textScale: Float,
        updatedAt: Long
    ) = Unit

    override suspend fun updateSoundEnabled(
        userId: String,
        enabled: Boolean,
        updatedAt: Long
    ) = Unit

    override suspend fun updateLowParticipationAlerts(
        userId: String,
        enabled: Boolean,
        updatedAt: Long
    ) = Unit

    override suspend fun updateLowParticipationThreshold(
        userId: String,
        minutes: Int,
        updatedAt: Long
    ) = Unit

    override suspend fun updateMonitorManualApproval(
        userId: String,
        required: Boolean,
        updatedAt: Long
    ) = Unit
}

private class FakeActiveSessionStore : ActiveSessionStore {
    val activeSessions = linkedMapOf<String, String?>()

    override fun observeActiveSessionId(userId: String): Flow<String?> =
        flowOf(activeSessions[userId])

    override suspend fun setActiveSession(
        userId: String,
        sessionId: String
    ) {
        activeSessions[userId] = sessionId
    }

    override suspend fun clearActiveSession(userId: String) {
        activeSessions[userId] = null
    }
}

private class RecordingPiClient : PiClient {
    val joinRequests = mutableListOf<PiJoinRequest>()

    override suspend fun joinSession(request: PiJoinRequest) {
        joinRequests += request
    }

    override suspend fun sendMessage(payload: PiMessagePayload) = Unit

    override suspend fun sendSignal(payload: PiSignalPayload) = Unit

    override suspend fun sendDisplayCommand(command: DisplayCommand) = Unit

    override fun observeSessionEvents(sessionId: String): Flow<PiSessionEvent> =
        flowOf(PiSessionEvent.Connected)
}
