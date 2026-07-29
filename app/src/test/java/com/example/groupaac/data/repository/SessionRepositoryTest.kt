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
import com.example.groupaac.data.pi.DisplayBindingCoordinator
import com.example.groupaac.data.pi.DisplayBindingResult
import com.example.groupaac.data.pi.DisplayPairingPayload
import com.example.groupaac.data.pi.LaunchSessionResult
import com.example.groupaac.data.pi.SessionInvitationPayload
import com.example.groupaac.data.realtime.reliability.NoOpOutboxDispatcher
import com.example.groupaac.data.realtime.sync.NoOpSessionRealtimeSync
import com.example.groupaac.data.realtime.sync.SessionRealtimeSync
import com.example.groupaac.data.session.ActiveSessionStore
import com.example.groupaac.data.sessiondirectory.FakeSessionDirectory
import com.example.groupaac.data.sessiondirectory.ResolveJoinCodeResult
import com.example.groupaac.data.sessiondirectory.SessionDirectoryEntry
import com.example.groupaac.model.DisplayCommandOrigin
import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.JoinRequestStatus
import com.example.groupaac.model.JoinSessionResult
import com.example.groupaac.model.SessionRole
import com.example.groupaac.model.SessionStatus
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
    fun createSessionNowCreatesDraftHostMembershipWithoutActivation() =
        runTest {
            val fixture = sessionFixture()

            val draftSession =
                fixture.repository.createSessionNow(
                    name = "Live Planning",
                    ownerUserId = fixture.host.uid,
                    displayName = fixture.host.displayName
                )

            assertEquals(
                SessionRole.HOST,
                draftSession.role
            )

            assertNull(
                fixture.activeSessionStore
                    .activeSessions[fixture.host.uid]
            )

            val createdSession =
                fixture.sessionDao.getSession(
                    draftSession.sessionId
                )

            assertNotNull(createdSession)
            assertEquals(
                SessionStatus.DRAFT,
                createdSession?.status
            )
            assertNull(
                createdSession?.actualStartedAt
            )
            assertNull(
                createdSession?.displayId
            )
            assertEquals(
                fixture.host.uid,
                createdSession?.hostUserId
            )

            val hostMember =
                fixture.sessionDao.getMember(
                    draftSession.sessionId,
                    fixture.host.uid
                )

            assertNotNull(hostMember)
            assertEquals(
                SessionRole.HOST,
                hostMember?.role
            )
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
    fun launchSessionOnDisplayActivatesHostAndMarksSessionLive() =
        runTest {
            val sessionDao = FakeSessionDao()
            val joinRequestDao = FakeSessionJoinRequestDao()
            val userDao = FakeUserDao()
            val activeSessionStore = FakeActiveSessionStore()
            val sessionDirectory = FakeSessionDirectory(nowProvider = { 0L })

            val host = UserEntity(
                uid = "host_1",
                displayName = "Host",
                createdAt = 1L
            )

            userDao.seed(host)
            userDao.seedSettings(
                UserSettingsEntity(userId = host.uid)
            )

            val repository = SessionRepository(
                transactionRunner = ImmediateTransactionRunner,
                sessionDao = sessionDao,
                sessionJoinRequestDao = joinRequestDao,
                userDao = userDao,
                activeSessionStore = activeSessionStore,
                sessionDirectory = sessionDirectory,
                displayBindingCoordinator =
                    AlwaysBoundDisplayBindingCoordinator,
                outboxDispatcher = NoOpOutboxDispatcher,
                sessionRealtimeSync = NoOpSessionRealtimeSync,
                joinCodeGenerator = { "1234-5678" }
            )

            val draft =
                repository.createSessionNow(
                    name = "Launch Test",
                    ownerUserId = host.uid,
                    displayName = host.displayName
                )

            val result =
                repository.launchSessionOnDisplay(
                    sessionId = draft.sessionId,
                    ownerUserId = host.uid,
                    pairing =
                        DisplayPairingPayload(
                            displayId = "pi-1",
                            displayName = "Room Display",
                            pairingNonce = "nonce-1",
                            pairingExpiresAt = Long.MAX_VALUE
                        )
                )

            assertTrue(result is LaunchSessionResult.Launched)
            assertEquals(
                draft.sessionId,
                activeSessionStore.activeSessions[host.uid]
            )

            val liveSession =
                sessionDao.getSession(draft.sessionId)

            assertEquals(
                SessionStatus.LIVE,
                liveSession?.status
            )
            assertEquals(
                "pi-1",
                liveSession?.displayId
            )

            val directoryEntry =
                (sessionDirectory.resolve("1234-5678")
                    as ResolveJoinCodeResult.Found)
                    .entry

            assertEquals(
                draft.sessionId,
                directoryEntry.sessionId
            )
            assertEquals(
                "pi-1",
                directoryEntry.displayId
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

    @Test
    fun approvalPublishesPrivateApprovalAndPublicRosterUpdate() = runTest {
        val sync = RecordingSessionRealtimeSync()
        val fixture = sessionFixture(sessionRealtimeSync = sync)
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
        assertNotNull(sync.facilitatorApproved)
        assertNotNull(sync.memberJoined)
        assertEquals(pending.request.id, sync.facilitatorApproved?.request?.id)
        assertEquals(fixture.facilitator.uid, sync.facilitatorApproved?.member?.userId)
        assertEquals(SessionRole.FACILITATOR, sync.facilitatorApproved?.member?.role)
        assertEquals(fixture.session.id, sync.memberJoined?.first?.id)
    }

    @Test
    fun declinePublishesPrivateDecline() = runTest {
        val sync = RecordingSessionRealtimeSync()
        val fixture = sessionFixture(sessionRealtimeSync = sync)
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
        assertNotNull(sync.facilitatorDeclined)
        assertEquals(pending.request.id, sync.facilitatorDeclined?.first?.id)
        assertEquals(fixture.session.id, sync.facilitatorDeclined?.second?.id)
    }

    @Test
    fun explicitLeaveRemovesMembershipAndPublishesMemberLeft() = runTest {
        val sync = RecordingSessionRealtimeSync()
        val fixture = sessionFixture(sessionRealtimeSync = sync)
        fixture.repository.joinSession(
            joinCode = fixture.session.joinCode,
            userId = fixture.participant.uid,
            displayName = fixture.participant.displayName,
            requestedRole = SessionRole.PARTICIPANT
        )

        fixture.repository.leaveSession(
            userId = fixture.participant.uid,
            sessionId = fixture.session.id
        )

        assertNull(
            fixture.sessionDao.getMember(
                fixture.session.id,
                fixture.participant.uid
            )
        )
        assertNotNull(sync.memberLeft)
        assertEquals(fixture.participant.uid, sync.memberLeft?.second?.userId)
    }

    @Test
    fun cancelledUnlaunchedSessionCannotBeJoined() = runTest {
        val fixture = sessionFixture()

        val scheduled = fixture.repository.scheduleSession(
            name = "Thursday Group",
            ownerUserId = fixture.host.uid,
            scheduledStartAt = 20_000L,
            scheduledDurationMinutes = 60
        )

        val cancelled = fixture.repository.cancelScheduledSession(
            sessionId = scheduled.id,
            ownerUserId = fixture.host.uid
        )

        assertTrue(cancelled)

        val storedSession = fixture.sessionDao.getSession(
            scheduled.id
        )

        assertEquals(
            SessionStatus.CANCELLED,
            storedSession?.status
        )

        try {
            fixture.repository.joinSession(
                joinCode = scheduled.joinCode,
                userId = fixture.participant.uid,
                displayName = fixture.participant.displayName
            )

            fail(
                "Expected an unlaunched cancelled session " +
                        "to be unavailable through its code."
            )
        } catch (expected: IllegalStateException) {
            assertEquals(
                "No session found for this code.",
                expected.message
            )
        }
    }

    @Test
    fun hostCanEndSession() = runTest {
        val fixture = sessionFixture()
        val active = fixture.repository.createSessionNow(
            name = "Host End Test",
            ownerUserId = fixture.host.uid,
            displayName = fixture.host.displayName
        )

        fixture.repository.endSession(
            sessionId = active.sessionId,
            actorUserId = fixture.host.uid
        )

        val ended = fixture.sessionDao.getSession(active.sessionId)
        assertEquals(SessionStatus.ENDED, ended?.status)
        assertNotNull(ended?.actualEndedAt)
    }

    @Test
    fun facilitatorCannotEndSession() = runTest {
        val fixture = sessionFixture()
        val active = fixture.repository.createSessionNow(
            name = "Facilitator End Test",
            ownerUserId = fixture.host.uid,
            displayName = fixture.host.displayName
        )
        fixture.sessionDao.upsertMember(
            SessionMemberEntity(
                sessionId = active.sessionId,
                userId = fixture.facilitator.uid,
                displayName = fixture.facilitator.displayName,
                role = SessionRole.FACILITATOR,
                joinedAt = 20L
            )
        )

        try {
            fixture.repository.endSession(
                sessionId = active.sessionId,
                actorUserId = fixture.facilitator.uid
            )
            fail("Expected facilitator end-session attempt to fail.")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message?.contains("host", ignoreCase = true) == true)
        }
    }

    @Test
    fun createSessionUsesAutoLatestWhenAccountDoesNotRequireManualApproval() = runTest {
        val fixture = sessionFixture(
            hostSettings = UserSettingsEntity(
                userId = "host_1",
                monitorRequireManualApproval = false
            )
        )

        val active = fixture.repository.createSessionNow(
            name = "Auto Session",
            ownerUserId = fixture.host.uid,
            displayName = fixture.host.displayName
        )

        assertEquals(
            DisplayMode.AUTO_LATEST,
            fixture.sessionDao.getSession(active.sessionId)?.displayMode
        )
    }

    @Test
    fun createSessionUsesApprovalRequiredWhenAccountRequiresManualApproval() = runTest {
        val fixture = sessionFixture(
            hostSettings = UserSettingsEntity(
                userId = "host_1",
                monitorRequireManualApproval = true
            )
        )

        val active = fixture.repository.createSessionNow(
            name = "Approval Session",
            ownerUserId = fixture.host.uid,
            displayName = fixture.host.displayName
        )

        assertEquals(
            DisplayMode.APPROVAL_REQUIRED,
            fixture.sessionDao.getSession(active.sessionId)?.displayMode
        )
    }

    @Test
    fun liveSessionModeUpdatePersistsAndPublishesBothEvents() = runTest {
        val sync = RecordingSessionRealtimeSync()
        val fixture = sessionFixture(sessionRealtimeSync = sync)

        fixture.repository.updateSessionDisplayMode(
            sessionId = fixture.session.id,
            actorUserId = fixture.host.uid,
            displayMode = DisplayMode.APPROVAL_REQUIRED
        )

        val updated = fixture.sessionDao.getSession(fixture.session.id)
        assertEquals(DisplayMode.APPROVAL_REQUIRED, updated?.displayMode)
        assertEquals(DisplayMode.APPROVAL_REQUIRED, sync.sessionSettingsChanged?.displayMode)
        assertEquals(DisplayMode.APPROVAL_REQUIRED, sync.displayModeChanged?.first)
    }

    private fun sessionFixture(
        seedLocalSession: Boolean = true,
        hostSettings: UserSettingsEntity = UserSettingsEntity(userId = "host_1"),
        sessionRealtimeSync: SessionRealtimeSync = NoOpSessionRealtimeSync
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
        userDao.seedSettings(
            hostSettings,
            UserSettingsEntity(userId = participant.uid),
            UserSettingsEntity(userId = facilitator.uid)
        )

        val session = SessionEntity(
            id = "session-1",
            name = "Friday Group",
            joinCode = "1234-5678",
            hostUserId = host.uid,
            status = SessionStatus.LIVE,
            displayMode = DisplayMode.AUTO_LATEST,
            displayId = "pi-test",
            createdAt = 10L,
            actualStartedAt = 10L,
            expiresAt = 10L + 86_400_000L
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
        sessionDirectory.seed(
            SessionDirectoryEntry(
                joinCode = session.joinCode,
                sessionId = session.id,
                sessionName = session.name,
                hostUserId = host.uid,
                displayId = requireNotNull(session.displayId),
                status = SessionStatus.LIVE,
                displayMode = session.displayMode,
                createdAt = session.createdAt,
                actualStartedAt =
                    requireNotNull(session.actualStartedAt),
                expiresAt = requireNotNull(session.expiresAt)
            )
        )

        val repository = SessionRepository(
            transactionRunner = ImmediateTransactionRunner,
            sessionDao = sessionDao,
            sessionJoinRequestDao = joinRequestDao,
            userDao = userDao,
            activeSessionStore = activeSessionStore,
            sessionDirectory = sessionDirectory,
            outboxDispatcher = NoOpOutboxDispatcher,
            sessionRealtimeSync = sessionRealtimeSync
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
        fixture.sessionDirectory.seed(
            SessionDirectoryEntry(
                joinCode = fixture.session.joinCode,
                sessionId = fixture.session.id,
                sessionName = fixture.session.name,
                hostUserId = fixture.host.uid,
                displayId = requireNotNull(
                    fixture.session.displayId
                ),
                status = SessionStatus.ENDED,
                displayMode = fixture.session.displayMode,
                createdAt = fixture.session.createdAt,
                actualStartedAt = requireNotNull(
                    fixture.session.actualStartedAt
                ),
                expiresAt = requireNotNull(
                    fixture.session.expiresAt
                )
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
            assertEquals("This session is not currently open.", error.message)
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

private class RecordingSessionRealtimeSync : SessionRealtimeSync by NoOpSessionRealtimeSync {
    data class ApprovalCall(
        val request: SessionJoinRequestEntity,
        val member: SessionMemberEntity,
        val session: SessionEntity,
        val actorUserId: String
    )

    var facilitatorApproved: ApprovalCall? = null
    var memberJoined: Pair<SessionEntity, SessionMemberEntity>? = null
    var memberLeft: Pair<SessionEntity, SessionMemberEntity>? = null
    var facilitatorDeclined: Pair<SessionJoinRequestEntity, SessionEntity>? = null
    var sessionSettingsChanged: SessionEntity? = null
    var displayModeChanged: Triple<DisplayMode, String?, DisplayCommandOrigin?>? = null

    override suspend fun publishFacilitatorApproved(
        request: SessionJoinRequestEntity,
        member: SessionMemberEntity,
        session: SessionEntity,
        actorUserId: String
    ) {
        facilitatorApproved = ApprovalCall(request, member, session, actorUserId)
    }

    override suspend fun publishMemberJoined(
        session: SessionEntity,
        member: SessionMemberEntity
    ) {
        memberJoined = session to member
    }

    override suspend fun publishMemberLeft(
        session: SessionEntity,
        member: SessionMemberEntity,
        actorUserId: String
    ) {
        memberLeft = session to member
    }

    override suspend fun publishSessionSettingsChanged(
        session: SessionEntity,
        actorUserId: String
    ) {
        sessionSettingsChanged = session
    }

    override suspend fun publishDisplayModeChanged(
        sessionId: String,
        actorUserId: String,
        displayMode: DisplayMode,
        currentMessageId: String?,
        isPinned: Boolean,
        origin: DisplayCommandOrigin?
    ) {
        displayModeChanged = Triple(displayMode, currentMessageId, origin)
    }

    override suspend fun publishFacilitatorDeclined(
        request: SessionJoinRequestEntity,
        session: SessionEntity,
        actorUserId: String
    ) {
        facilitatorDeclined = request to session
    }
}

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

    override suspend fun updateDisplayMode(
        sessionId: String,
        displayMode: DisplayMode,
        updatedAt: Long
    ) {
        val session = sessions[sessionId] ?: return
        seedSession(
            session.copy(
                displayMode = displayMode,
                updatedAt = updatedAt
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

    override suspend fun getMembersForSession(
        sessionId: String
    ): List<SessionMemberEntity> =
        members.values
            .filter { it.sessionId == sessionId }
            .sortedBy { it.joinedAt }

    override suspend fun deleteMember(
        sessionId: String,
        userId: String
    ) {
        members.remove(sessionId to userId)
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

    override suspend fun getRequestsForSession(
        sessionId: String
    ): List<SessionJoinRequestEntity> =
        requests.values
            .filter { it.sessionId == sessionId }
            .sortedBy { it.requestedAt }

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

    fun seedSettings(vararg seededSettings: UserSettingsEntity) {
        seededSettings.forEach { settings[it.userId] = it }
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

private object AlwaysBoundDisplayBindingCoordinator :
    DisplayBindingCoordinator {

    override suspend fun bind(
        pairing: DisplayPairingPayload,
        invitation: SessionInvitationPayload,
        requestedByUserId: String
    ): DisplayBindingResult =
        DisplayBindingResult.Bound(
            commandEventId = "cmd-1"
        )

    override suspend fun unbind(
        displayId: String,
        sessionId: String,
        requestedByUserId: String
    ) = error("unbind should not be called in this test")
}
