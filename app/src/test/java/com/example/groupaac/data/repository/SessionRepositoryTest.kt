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
import com.example.groupaac.data.session.ActiveSessionStore
import com.example.groupaac.data.sessiondirectory.FakeSessionDirectory
import com.example.groupaac.data.sessiondirectory.RemoteSessionRecord
import com.example.groupaac.data.sessiondirectory.RemoteSessionStatus
import com.example.groupaac.model.JoinRequestStatus
import com.example.groupaac.model.JoinSessionResult
import com.example.groupaac.model.SessionRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SessionRepositoryTest {
    @Test
    fun participantJoinPersistsResolvedSessionShellBeforeMembership() = runTest {
        val fixture = sessionFixture(seedLocalSession = false)

        val result = fixture.repository.joinSession(
            joinCode = fixture.session.joinCode,
            userId = fixture.participant.uid,
            displayName = fixture.participant.displayName,
            requestedRole = SessionRole.PARTICIPANT
        )

        assertTrue(result is JoinSessionResult.Joined)
        assertNotNull(fixture.sessionDao.getSession(fixture.session.id))
        assertNotNull(
            fixture.sessionDao.getMember(
                fixture.session.id,
                fixture.participant.uid
            )
        )
    }

    @Test
    fun createSessionNowCreatesHostMembershipAndActivates() = runTest {
        val fixture = sessionFixture()

        val activeSession = fixture.repository.createSessionNow(
            name = "Live Planning",
            ownerUserId = fixture.host.uid,
            displayName = fixture.host.displayName
        )

        assertEquals(SessionRole.HOST, activeSession.role)
        assertEquals(
            activeSession.sessionId,
            fixture.activeSessionStore.activeSessions[fixture.host.uid]
        )

        val createdSession = fixture.sessionDao.getSession(activeSession.sessionId)
        assertNotNull(createdSession)
        assertNotNull(createdSession?.actualStartedAt)
        assertEquals(fixture.host.uid, createdSession?.hostUserId)

        val hostMember = fixture.sessionDao.getMember(
            activeSession.sessionId,
            fixture.host.uid
        )
        assertNotNull(hostMember)
        assertEquals(SessionRole.HOST, hostMember?.role)
    }

    @Test
    fun scheduleSessionDoesNotActivate() = runTest {
        val fixture = sessionFixture()

        val scheduledSession = fixture.repository.scheduleSession(
            name = "Tuesday Support Group",
            ownerUserId = fixture.host.uid,
            scheduledStartAt = 5_000L,
            scheduledDurationMinutes = 60
        )

        assertNull(fixture.activeSessionStore.activeSessions[fixture.host.uid])
        assertNull(scheduledSession.actualStartedAt)
        assertEquals(5_000L, scheduledSession.scheduledStartAt)
        assertEquals(60, scheduledSession.scheduledDurationMinutes)
        assertEquals(fixture.host.uid, scheduledSession.hostUserId)
        assertNotNull(
            fixture.sessionDao.getMember(
                scheduledSession.id,
                fixture.host.uid
            )
        )
    }

    @Test
    fun startScheduledSessionIsHostOnly() = runTest {
        val fixture = sessionFixture()
        val scheduledSession = fixture.repository.scheduleSession(
            name = "Wednesday Check-in",
            ownerUserId = fixture.host.uid,
            scheduledStartAt = 7_000L,
            scheduledDurationMinutes = 45
        )

        try {
            fixture.repository.startScheduledSession(
                sessionId = scheduledSession.id,
                ownerUserId = fixture.participant.uid
            )
            fail("Expected only the host to be able to start the session.")
        } catch (_: IllegalStateException) {
            // Expected path.
        }
    }

    @Test
    fun participantImmediateJoin() = runTest {
        val fixture = sessionFixture()

        val result = fixture.repository.joinSession(
            joinCode = fixture.session.joinCode,
            userId = fixture.participant.uid,
            displayName = fixture.participant.displayName,
            requestedRole = SessionRole.PARTICIPANT
        )

        assertTrue(result is JoinSessionResult.Joined)
        val joined = result as JoinSessionResult.Joined
        assertEquals(SessionRole.PARTICIPANT, joined.activeSession.role)
        assertEquals(
            fixture.session.id,
            fixture.activeSessionStore.activeSessions[fixture.participant.uid]
        )
        assertNotNull(
            fixture.sessionDao.getMember(
                fixture.session.id,
                fixture.participant.uid
            )
        )
        assertTrue(fixture.joinRequestDao.requests.isEmpty())
    }

    @Test
    fun facilitatorPendingRequest() = runTest {
        val fixture = sessionFixture()

        val first = fixture.repository.joinSession(
            joinCode = fixture.session.joinCode,
            userId = fixture.facilitator.uid,
            displayName = fixture.facilitator.displayName,
            requestedRole = SessionRole.FACILITATOR
        )
        val second = fixture.repository.joinSession(
            joinCode = fixture.session.joinCode,
            userId = fixture.facilitator.uid,
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
                fixture.facilitator.uid
            )
        )
        assertNull(
            fixture.activeSessionStore.activeSessions[fixture.facilitator.uid]
        )
    }

    @Test
    fun onlyHostCanApprove() = runTest {
        val fixture = sessionFixture()
        val pending = fixture.repository.joinSession(
            joinCode = fixture.session.joinCode,
            userId = fixture.facilitator.uid,
            displayName = fixture.facilitator.displayName,
            requestedRole = SessionRole.FACILITATOR
        ) as JoinSessionResult.AwaitingApproval

        var threw = false
        try {
            fixture.repository.approveJoinRequest(
                requestId = pending.request.id,
                decidedByUserId = fixture.participant.uid
            )
        } catch (_: IllegalStateException) {
            threw = true
        }

        assertTrue(threw)

        assertNull(
            fixture.sessionDao.getMember(
                fixture.session.id,
                fixture.facilitator.uid
            )
        )
    }

    @Test
    fun approvalCreatesFacilitatorMembership() = runTest {
        val fixture = sessionFixture()
        val pending = fixture.repository.joinSession(
            joinCode = fixture.session.joinCode,
            userId = fixture.facilitator.uid,
            displayName = fixture.facilitator.displayName,
            requestedRole = SessionRole.FACILITATOR
        ) as JoinSessionResult.AwaitingApproval

        val approved = fixture.repository.approveJoinRequest(
            requestId = pending.request.id,
            decidedByUserId = fixture.host.uid
        )

        assertTrue(approved)
        val member = fixture.sessionDao.getMember(
            fixture.session.id,
            fixture.facilitator.uid
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
            userId = fixture.facilitator.uid,
            displayName = fixture.facilitator.displayName,
            requestedRole = SessionRole.FACILITATOR
        ) as JoinSessionResult.AwaitingApproval

        val declined = fixture.repository.declineJoinRequest(
            requestId = pending.request.id,
            decidedByUserId = fixture.host.uid
        )

        assertTrue(declined)
        assertNull(
            fixture.sessionDao.getMember(
                fixture.session.id,
                fixture.facilitator.uid
            )
        )
        assertEquals(
            JoinRequestStatus.DECLINED,
            fixture.joinRequestDao.getRequestById(pending.request.id)?.status
        )
    }

    private fun sessionFixture(
        seedLocalSession: Boolean = true
    ): SessionFixture {
        val sessionDao = FakeSessionDao()
        val joinRequestDao = FakeSessionJoinRequestDao()
        val userDao = FakeUserDao()
        val activeSessionStore = FakeActiveSessionStore()
        val sessionDirectory = FakeSessionDirectory(nowProvider = { 10L })

        val host = UserEntity(
            uid = "host_1",
            displayName = "Host",
            createdAt = 1L
        )
        val participant = UserEntity(
            uid = "participant_1",
            displayName = "Participant",
            createdAt = 2L
        )
        val facilitator = UserEntity(
            uid = "facilitator_1",
            displayName = "Facilitator",
            createdAt = 3L
        )
        userDao.seed(host, participant, facilitator)

        val session = SessionEntity(
            id = "session-1",
            name = "Friday Group",
            joinCode = "1234-5678",
            hostUserId = host.uid,
            createdAt = 10L,
            actualStartedAt = 10L
        )
        if (seedLocalSession) {
            sessionDao.seedSession(session)
            sessionDao.seedMember(
                SessionMemberEntity(
                    sessionId = session.id,
                    userId = host.uid,
                    displayName = host.displayName,
                    role = SessionRole.HOST,
                    joinedAt = 10L
                )
            )
        }
        sessionDirectory.seedSession(
            RemoteSessionRecord(
                sessionId = session.id,
                joinCode = session.joinCode,
                sessionName = session.name,
                hostUid = host.uid,
                status = RemoteSessionStatus.LIVE,
                scheduledStartAt = session.scheduledStartAt,
                scheduledDurationMinutes = session.scheduledDurationMinutes,
                actualStartedAt = session.actualStartedAt,
                actualEndedAt = session.actualEndedAt,
                expiresAt = 10L + 86_400_000L
            )
        )

        val repository = SessionRepository(
            sessionDao = sessionDao,
            sessionJoinRequestDao = joinRequestDao,
            userDao = userDao,
            activeSessionStore = activeSessionStore,
            sessionDirectory = sessionDirectory
        )

        return SessionFixture(
            repository = repository,
            sessionDao = sessionDao,
            joinRequestDao = joinRequestDao,
            activeSessionStore = activeSessionStore,
            sessionDirectory = sessionDirectory,
            session = session,
            host = host,
            participant = participant,
            facilitator = facilitator
        )
    }

    @Test
    fun endedResolutionReturnsExplicitMessage() = runTest {
        val fixture = sessionFixture(seedLocalSession = false)
        fixture.sessionDirectory.seedSession(
            RemoteSessionRecord(
                sessionId = fixture.session.id,
                joinCode = fixture.session.joinCode,
                sessionName = fixture.session.name,
                hostUid = fixture.host.uid,
                status = RemoteSessionStatus.ENDED,
                scheduledStartAt = fixture.session.scheduledStartAt,
                scheduledDurationMinutes = fixture.session.scheduledDurationMinutes,
                actualStartedAt = fixture.session.actualStartedAt,
                actualEndedAt = 20L,
                expiresAt = 86_400_000L
            )
        )
        try {
            fixture.repository.joinSession(
                joinCode = fixture.session.joinCode,
                userId = fixture.participant.uid,
                displayName = fixture.participant.displayName,
                requestedRole = SessionRole.PARTICIPANT
            )
            fail("Expected ended sessions to be rejected.")
        } catch (error: IllegalStateException) {
            assertEquals("This session has already ended.", error.message)
        }
    }
}

private data class SessionFixture(
    val repository: SessionRepository,
    val sessionDao: FakeSessionDao,
    val joinRequestDao: FakeSessionJoinRequestDao,
    val activeSessionStore: FakeActiveSessionStore,
    val sessionDirectory: FakeSessionDirectory,
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

    override fun observeUpcomingHostedSessions(
        hostUserId: String,
        dayStartMillis: Long
    ): Flow<List<SessionEntity>> = flowOf(
        sessions.values
            .filter {
                it.hostUserId == hostUserId &&
                    it.actualStartedAt == null &&
                    it.actualEndedAt == null &&
                    it.scheduledStartAt != null &&
                    it.scheduledStartAt >= dayStartMillis
            }
            .sortedBy { it.scheduledStartAt }
    )

    override fun observeLiveHostedSessions(
        hostUserId: String
    ): Flow<List<SessionEntity>> = flowOf(
        sessions.values
            .filter {
                it.hostUserId == hostUserId &&
                    it.actualStartedAt != null &&
                    it.actualEndedAt == null
            }
            .sortedByDescending { it.actualStartedAt }
    )

    override fun observePastHostedSessions(
        hostUserId: String,
        dayStartMillis: Long
    ): Flow<List<SessionEntity>> = flowOf(
        sessions.values
            .filter {
                it.hostUserId == hostUserId &&
                    (
                        it.actualEndedAt != null ||
                            (
                                it.actualStartedAt == null &&
                                    it.scheduledStartAt != null &&
                                    it.scheduledStartAt < dayStartMillis
                                )
                        )
            }
            .sortedByDescending {
                it.actualEndedAt ?: it.scheduledStartAt ?: it.createdAt
            }
    )

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

    override suspend fun deleteMembersForSession(sessionId: String) {
        val keysToRemove = members.keys
            .filter { (memberSessionId, _) ->
                memberSessionId == sessionId
            }
        keysToRemove.forEach { key ->
            members.remove(key)
        }
    }

    override suspend fun deleteHostedSession(
        sessionId: String,
        hostUserId: String
    ): Int {
        val session = sessions[sessionId] ?: return 0
        if (session.hostUserId != hostUserId) {
            return 0
        }
        sessions.remove(sessionId)
        sessionsFlow.value = sessions.values.toList()
        return 1
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

    override suspend fun deleteRequestsForSession(sessionId: String) {
        val idsToRemove = requests.values
            .filter { it.sessionId == sessionId }
            .map { it.id }
        idsToRemove.forEach { requestId ->
            requests.remove(requestId)
        }
    }

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
            users[user.uid] = user
        }
    }

    override fun observeUsers(): Flow<List<UserEntity>> =
        flowOf(users.values.toList())

    override fun observeUser(uid: String): Flow<UserEntity?> =
        flowOf(users[uid])

    override suspend fun getUser(uid: String): UserEntity? = users[uid]

    override suspend fun upsertUser(user: UserEntity) {
        users[user.uid] = user
    }

    override suspend fun insertUser(user: UserEntity) {
        check(users[user.uid] == null) { "Duplicate user ${user.uid}" }
        users[user.uid] = user
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
