package com.example.groupaac.data.realtime

import com.example.groupaac.data.entity.MessageEntity
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionJoinRequestEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.data.realtime.protocol.ReceivedRealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeChannels
import com.example.groupaac.data.realtime.protocol.RealtimeEvent
import com.example.groupaac.data.realtime.sync.SessionRealtimeSync
import com.example.groupaac.model.ActiveSession
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.model.SessionRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionSubscriptionCoordinatorTest {
    @Test
    fun participantSubscribesToPublicAndPrivateChannels() = runTest {
        val fixture = coordinatorFixture()
        try {
            fixture.activeUserId.value = "participant_1"
            fixture.activeSessions["participant_1"]?.value = activeSession(
                userId = "participant_1",
                role = SessionRole.PARTICIPANT
            )

            advanceUntilIdle()

            assertEquals(
                setOf(
                    RealtimeChannels.public("session-1"),
                    RealtimeChannels.privateUser("session-1", "participant_1")
                ),
                fixture.client.activeChannels
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun facilitatorSubscribesToFacilitatorAndDisplayChannels() = runTest {
        val fixture = coordinatorFixture()
        try {
            fixture.activeUserId.value = "facilitator_1"
            fixture.activeSessions["facilitator_1"]?.value = activeSession(
                userId = "facilitator_1",
                role = SessionRole.FACILITATOR
            )

            advanceUntilIdle()

            assertEquals(
                setOf(
                    RealtimeChannels.public("session-1"),
                    RealtimeChannels.facilitator("session-1"),
                    RealtimeChannels.privateUser("session-1", "facilitator_1"),
                    RealtimeChannels.displayEvents("session-1")
                ),
                fixture.client.activeChannels
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun clearingActiveSessionStopsSubscriptions() = runTest {
        val fixture = coordinatorFixture()
        try {
            fixture.activeUserId.value = "participant_1"
            fixture.activeSessions["participant_1"]?.value = activeSession(
                userId = "participant_1",
                role = SessionRole.PARTICIPANT
            )
            advanceUntilIdle()

            fixture.activeSessions["participant_1"]?.value = null
            advanceUntilIdle()

            assertTrue(fixture.client.activeChannels.isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun accountSwitchCleansUpPreviousClient() = runTest {
        val fixture = coordinatorFixture()
        try {
            val firstClient = fixture.client
            fixture.activeUserId.value = "participant_1"
            fixture.activeSessions["participant_1"]?.value = activeSession(
                userId = "participant_1",
                role = SessionRole.PARTICIPANT
            )
            advanceUntilIdle()

            val secondClient = TrackingRealtimeClient()
            fixture.clientManager.client = secondClient
            fixture.activeUserId.value = "host_1"
            fixture.activeSessions["host_1"]?.value = activeSession(
                userId = "host_1",
                role = SessionRole.HOST
            )
            advanceUntilIdle()

            assertTrue(firstClient.activeChannels.isEmpty())
            assertEquals(
                setOf(
                    RealtimeChannels.public("session-1"),
                    RealtimeChannels.facilitator("session-1"),
                    RealtimeChannels.privateUser("session-1", "host_1"),
                    RealtimeChannels.displayEvents("session-1")
                ),
                secondClient.activeChannels
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun activeSessionIsSubscribedWhenRestoredAtStartup() = runTest {
        val activeUserId = MutableStateFlow<String?>("participant_1")
        val client = TrackingRealtimeClient()
        val clientManager = TestRealtimeClientManager(client)
        val activeSessions = mutableMapOf(
            "participant_1" to MutableStateFlow(
                activeSession(
                    userId = "participant_1",
                    role = SessionRole.PARTICIPANT
                )
            )
        )
        val scope = CoroutineScope(
            UnconfinedTestDispatcher(testScheduler) + SupervisorJob()
        )

        val coordinator = SessionSubscriptionCoordinator(
            activeUserId = activeUserId,
            activeSessionProvider = { userId ->
                activeSessions[userId] ?: MutableStateFlow(null)
            },
            realtimeClientManager = clientManager,
            sessionRealtimeSync = RecordingSessionRealtimeSync(),
            scope = scope
        )
        try {
            advanceUntilIdle()

            assertEquals(
                setOf(
                    RealtimeChannels.public("session-1"),
                    RealtimeChannels.privateUser("session-1", "participant_1")
                ),
                client.activeChannels
            )
        } finally {
            coordinator.close()
            scope.cancel()
        }
    }

    @Test
    fun incomingEventReachesApplyIncoming() = runTest {
        val fixture = coordinatorFixture()
        try {
            fixture.activeUserId.value = "participant_1"
            fixture.activeSessions["participant_1"]?.value = activeSession(
                userId = "participant_1",
                role = SessionRole.PARTICIPANT
            )
            advanceUntilIdle()

            val valid = receivedEvent(
                channel = RealtimeChannels.public("session-1"),
                sessionId = "session-1"
            )
            val mismatched = receivedEvent(
                channel = RealtimeChannels.public("session-1"),
                sessionId = "other-session"
            )

            fixture.client.emit(valid)
            fixture.client.emit(mismatched)
            advanceUntilIdle()

            assertEquals(listOf(valid), fixture.sync.applied)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun connectionStatePropagatesFromRealtimeClient() = runTest {
        val fixture = coordinatorFixture()
        try {
            fixture.activeUserId.value = "participant_1"
            fixture.activeSessions["participant_1"]?.value = activeSession(
                userId = "participant_1",
                role = SessionRole.PARTICIPANT
            )
            advanceUntilIdle()

            fixture.client.connectionState.value =
                RealtimeConnectionState.Reconnecting
            advanceUntilIdle()
            assertEquals(
                RealtimeConnectionState.Reconnecting,
                fixture.coordinator.connectionState.value
            )

            fixture.client.connectionState.value =
                RealtimeConnectionState.Failed("network")
            advanceUntilIdle()
            assertEquals(
                RealtimeConnectionState.Failed("network"),
                fixture.coordinator.connectionState.value
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun facilitatorRequestTracksRequesterPrivateChannelBeforeApproval() = runTest {
        val fixture = coordinatorFixture()
        try {
            fixture.activeUserId.value = "facilitator_1"
            fixture.coordinator.trackFacilitatorRequest(
                sessionId = "session-1",
                userId = "facilitator_1"
            )

            advanceUntilIdle()

            assertEquals(
                setOf(
                    RealtimeChannels.public("session-1"),
                    RealtimeChannels.privateUser("session-1", "facilitator_1")
                ),
                fixture.client.activeChannels
            )
        } finally {
            fixture.close()
        }
    }
}

private data class CoordinatorFixture(
    val coordinator: SessionSubscriptionCoordinator,
    val activeUserId: MutableStateFlow<String?>,
    val activeSessions: MutableMap<String, MutableStateFlow<ActiveSession?>>,
    val clientManager: TestRealtimeClientManager,
    val scope: CoroutineScope,
    val client: TrackingRealtimeClient,
    val sync: RecordingSessionRealtimeSync
) {
    fun close() {
        coordinator.close()
        scope.cancel()
    }
}

private fun kotlinx.coroutines.test.TestScope.coordinatorFixture(): CoordinatorFixture {
    val activeUserId = MutableStateFlow<String?>(null)
    val activeSessions = mutableMapOf(
        "participant_1" to MutableStateFlow<ActiveSession?>(null),
        "facilitator_1" to MutableStateFlow<ActiveSession?>(null),
        "host_1" to MutableStateFlow<ActiveSession?>(null)
    )
    val client = TrackingRealtimeClient()
    val sync = RecordingSessionRealtimeSync()
    val clientManager = TestRealtimeClientManager(client)
    val scope = CoroutineScope(
        UnconfinedTestDispatcher(testScheduler) + SupervisorJob()
    )
    val coordinator = SessionSubscriptionCoordinator(
        activeUserId = activeUserId,
        activeSessionProvider = { userId ->
            activeSessions.getOrPut(userId) { MutableStateFlow(null) }
        },
        realtimeClientManager = clientManager,
        sessionRealtimeSync = sync,
        scope = scope
    )
    return CoordinatorFixture(
        coordinator = coordinator,
        activeUserId = activeUserId,
        activeSessions = activeSessions,
        clientManager = clientManager,
        scope = scope,
        client = client,
        sync = sync
    )
}

private class TestRealtimeClientManager(
    var client: TrackingRealtimeClient
) : RealtimeClientManager {
    override suspend fun activateUser(uid: String) = Unit

    override suspend fun deactivateUser() = Unit

    override fun requireClient(): SessionRealtimeClient = client
}

private class TrackingRealtimeClient : SessionRealtimeClient {
    val activeChannels = linkedSetOf<String>()
    val connectionState = MutableStateFlow<RealtimeConnectionState>(
        RealtimeConnectionState.Connected
    )
    private val events = MutableSharedFlow<ReceivedRealtimeEvent>(
        extraBufferCapacity = 16
    )

    override suspend fun joinSession(request: com.example.groupaac.data.pi.PiJoinRequest) = Unit

    override suspend fun sendMessage(payload: com.example.groupaac.data.pi.PiMessagePayload) = Unit

    override suspend fun sendSignal(payload: com.example.groupaac.data.pi.PiSignalPayload) = Unit

    override suspend fun sendDisplayCommand(command: com.example.groupaac.data.pi.DisplayCommand) = Unit

    override suspend fun publish(
        channel: String,
        event: RealtimeEvent
    ): Long = 1L

    override fun observeChannel(channel: String): Flow<ReceivedRealtimeEvent> {
        return callbackFlow {
            activeChannels += channel
            val job = launch {
                events.collect { received ->
                    if (received.channel == channel) {
                        trySend(received)
                    }
                }
            }
            awaitClose {
                activeChannels -= channel
                job.cancel()
            }
        }
    }

    override fun observeConnectionState(): StateFlow<RealtimeConnectionState> =
        connectionState.asStateFlow()

    override fun observeSessionEvents(sessionId: String) = emptyFlow<com.example.groupaac.data.pi.PiSessionEvent>()

    override suspend fun close() = Unit

    suspend fun emit(received: ReceivedRealtimeEvent) {
        events.emit(received)
    }
}

private class RecordingSessionRealtimeSync : SessionRealtimeSync {
    val applied = mutableListOf<ReceivedRealtimeEvent>()

    override suspend fun publishSessionStarted(
        session: SessionEntity,
        actorUserId: String
    ) = Unit

    override suspend fun publishSessionUpdated(
        session: SessionEntity,
        actorUserId: String
    ) = Unit

    override suspend fun publishSessionEnded(
        session: SessionEntity,
        actorUserId: String
    ) = Unit

    override suspend fun publishSessionCancelled(
        session: SessionEntity,
        actorUserId: String
    ) = Unit

    override suspend fun publishMemberJoined(
        session: SessionEntity,
        member: SessionMemberEntity
    ) = Unit

    override suspend fun publishFacilitatorRequested(
        request: SessionJoinRequestEntity,
        actorUserId: String
    ) = Unit

    override suspend fun publishFacilitatorApproved(
        request: SessionJoinRequestEntity,
        actorUserId: String
    ) = Unit

    override suspend fun publishFacilitatorDeclined(
        request: SessionJoinRequestEntity,
        actorUserId: String
    ) = Unit

    override suspend fun publishFacilitatorCancelled(
        request: SessionJoinRequestEntity,
        actorUserId: String
    ) = Unit

    override suspend fun publishMessageCreated(
        message: MessageEntity,
        senderName: String,
        target: MessageTarget
    ) = Unit

    override suspend fun publishSnapshot(
        session: SessionEntity,
        members: List<SessionMemberEntity>,
        requests: List<SessionJoinRequestEntity>,
        messages: List<MessageEntity>,
        requesterUserId: String,
        actorUserId: String
    ) = Unit

    override suspend fun publishDisplayShowMessage(
        session: SessionEntity,
        message: MessageEntity,
        senderName: String,
        actorUserId: String,
        restore: Boolean
    ) = Unit

    override suspend fun publishDisplayPinState(
        sessionId: String,
        messageId: String,
        actorUserId: String,
        pinned: Boolean
    ) = Unit

    override suspend fun publishDisplayClear(
        sessionId: String,
        actorUserId: String
    ) = Unit

    override suspend fun applyIncoming(received: ReceivedRealtimeEvent): Boolean {
        applied += received
        return true
    }
}

private fun activeSession(
    userId: String,
    role: SessionRole
): ActiveSession = ActiveSession(
    sessionId = "session-1",
    joinCode = "1234-5678",
    sessionName = "Friday Group",
    userId = userId,
    role = role,
    joinedAt = 10L,
    scheduledStartAt = null,
    scheduledDurationMinutes = null,
    actualStartedAt = 10L
)

private fun receivedEvent(
    channel: String,
    sessionId: String
): ReceivedRealtimeEvent = ReceivedRealtimeEvent(
    channel = channel,
    timetoken = 99L,
    publisherUserId = "publisher",
    event = RealtimeEvent(
        eventId = "evt-$sessionId",
        type = "message.created",
        sessionId = sessionId,
        actorUserId = "publisher",
        occurredAt = 10L,
        payload = JsonObject(mapOf("text" to JsonPrimitive("hello")))
    )
)
