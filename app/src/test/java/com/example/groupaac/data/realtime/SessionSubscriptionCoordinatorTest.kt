package com.example.groupaac.data.realtime

import com.example.groupaac.data.entity.MessageEntity
import com.example.groupaac.data.entity.AttachmentEntity
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionJoinRequestEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.data.entity.StatusSignalEntity
import com.example.groupaac.data.realtime.protocol.ReceivedRealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeChannels
import com.example.groupaac.data.realtime.protocol.RealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeEventTypes
import com.example.groupaac.data.realtime.sync.SessionRealtimeSync
import com.example.groupaac.model.ActiveSession
import com.example.groupaac.model.DisplayCommandOrigin
import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.model.SessionRole
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
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
                    RealtimeChannels.displayEvents("session-1"),
                    RealtimeChannels.displayDeviceEvents("display-1")
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
                    RealtimeChannels.displayEvents("session-1"),
                    RealtimeChannels.displayDeviceEvents("display-1")
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
            coordinator.start()
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

    @Test
    fun startupReplaysHistoryAfterStoredCursorBeforeLiveCollection() = runTest {
        val fixture = coordinatorFixture(
            cursorByChannel = mapOf(
                RealtimeChannels.public("session-1") to 50L
            )
        )
        try {
            val replayed = receivedEvent(
                channel = RealtimeChannels.public("session-1"),
                sessionId = "session-1",
                eventId = "evt-history",
                timetoken = 75L
            )
            fixture.client.history[RealtimeChannels.public("session-1")] = listOf(replayed)
            fixture.activeUserId.value = "participant_1"
            fixture.activeSessions["participant_1"]?.value = activeSession(
                userId = "participant_1",
                role = SessionRole.PARTICIPANT
            )

            advanceUntilIdle()

            assertEquals(listOf(replayed), fixture.sync.applied)
            assertEquals(
                50L,
                fixture.client.fetchedAfterTimetokens[RealtimeChannels.public("session-1")]
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun facilitatorApprovalRecoveryReplaysPrivateHistoryWhileOffline() = runTest {
        val fixture = coordinatorFixture(
            cursorByChannel = mapOf(
                RealtimeChannels.privateUser("session-1", "facilitator_1") to 25L
            )
        )
        try {
            val approval = receivedEvent(
                channel = RealtimeChannels.privateUser("session-1", "facilitator_1"),
                sessionId = "session-1",
                eventId = "evt-approved",
                type = RealtimeEventTypes.FACILITATOR_APPROVED,
                timetoken = 30L
            )
            fixture.client.history[
                RealtimeChannels.privateUser("session-1", "facilitator_1")
            ] = listOf(approval)
            fixture.activeUserId.value = "facilitator_1"
            fixture.coordinator.trackFacilitatorRequest(
                sessionId = "session-1",
                userId = "facilitator_1"
            )

            advanceUntilIdle()

            assertEquals(listOf(approval), fixture.sync.applied)
            assertEquals(
                25L,
                fixture.client.fetchedAfterTimetokens[
                    RealtimeChannels.privateUser("session-1", "facilitator_1")
                ]
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun historyCancellationPropagates() = runTest {
        val fixture = coordinatorFixture()
        try {
            fixture.client.historyThrowable =
                kotlinx.coroutines.CancellationException(
                    "cancel history"
                )
            fixture.activeUserId.value = "participant_1"
            fixture.activeSessions["participant_1"]?.value = activeSession(
                userId = "participant_1",
                role = SessionRole.PARTICIPANT
            )

            advanceUntilIdle()

            assertTrue(
                fixture.client.activeChannels.isEmpty()
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun eventArrivesDuringHistoryReplay() = runTest {
        val fixture = coordinatorFixture(
            cursorByChannel = mapOf(
                RealtimeChannels.public("session-1") to 50L
            )
        )
        try {
            val historyEvent = receivedEvent(
                channel = RealtimeChannels.public("session-1"),
                sessionId = "session-1",
                eventId = "evt-history",
                timetoken = 60L
            )
            val liveEvent = receivedEvent(
                channel = RealtimeChannels.public("session-1"),
                sessionId = "session-1",
                eventId = "evt-live",
                timetoken = 70L
            )
            fixture.client.history[
                RealtimeChannels.public("session-1")
            ] = listOf(historyEvent)
            fixture.client.onFetchHistory = { channel, _, _ ->
                if (channel == RealtimeChannels.public("session-1")) {
                    emit(liveEvent)
                }
            }
            fixture.activeUserId.value = "participant_1"
            fixture.activeSessions["participant_1"]?.value = activeSession(
                userId = "participant_1",
                role = SessionRole.PARTICIPANT
            )

            advanceUntilIdle()

            assertEquals(
                listOf("evt-history", "evt-live"),
                fixture.sync.applied.map { it.event.eventId }
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun eventInHistoryAndLiveBufferIsDeduplicated() = runTest {
        val fixture = coordinatorFixture(
            cursorByChannel = mapOf(
                RealtimeChannels.public("session-1") to 50L
            )
        )
        try {
            val shared = receivedEvent(
                channel = RealtimeChannels.public("session-1"),
                sessionId = "session-1",
                eventId = "evt-shared",
                timetoken = 60L
            )
            fixture.client.history[
                RealtimeChannels.public("session-1")
            ] = listOf(shared)
            fixture.client.onFetchHistory = { channel, _, _ ->
                if (channel == RealtimeChannels.public("session-1")) {
                    emit(shared)
                }
            }
            fixture.activeUserId.value = "participant_1"
            fixture.activeSessions["participant_1"]?.value = activeSession(
                userId = "participant_1",
                role = SessionRole.PARTICIPANT
            )

            advanceUntilIdle()

            assertEquals(
                listOf("evt-shared"),
                fixture.sync.applied.map { it.event.eventId }
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun oldSessionStopsDeliveringAfterLeave() = runTest {
        val fixture = coordinatorFixture()
        try {
            fixture.activeUserId.value = "participant_1"
            fixture.activeSessions["participant_1"]?.value = activeSession(
                userId = "participant_1",
                role = SessionRole.PARTICIPANT
            )
            advanceUntilIdle()

            fixture.client.emit(
                receivedEvent(
                    channel = RealtimeChannels.public("session-1"),
                    sessionId = "session-1",
                    eventId = "evt-before-leave",
                    timetoken = 100L
                )
            )
            advanceUntilIdle()

            fixture.activeSessions["participant_1"]?.value = null
            advanceUntilIdle()

            fixture.client.emit(
                receivedEvent(
                    channel = RealtimeChannels.public("session-1"),
                    sessionId = "session-1",
                    eventId = "evt-after-leave",
                    timetoken = 101L
                )
            )
            advanceUntilIdle()

            assertEquals(
                listOf("evt-before-leave"),
                fixture.sync.applied.map { it.event.eventId }
            )
            assertTrue(fixture.client.activeChannels.isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun approvalReplacesRequesterChannels() = runTest {
        val fixture = coordinatorFixture()
        try {
            fixture.activeUserId.value = "facilitator_1"
            fixture.coordinator.trackFacilitatorRequest(
                sessionId = "session-1",
                userId = "facilitator_1"
            )
            advanceUntilIdle()

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
                    RealtimeChannels.displayEvents("session-1"),
                    RealtimeChannels.displayDeviceEvents("display-1")
                ),
                fixture.client.activeChannels
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun repeatedStartStopLeaksNoSubscriptions() = runTest {
        val fixture = coordinatorFixture()
        try {
            repeat(3) {
                fixture.activeUserId.value = "participant_1"
                fixture.activeSessions["participant_1"]?.value = activeSession(
                    userId = "participant_1",
                    role = SessionRole.PARTICIPANT
                )
                advanceUntilIdle()

                fixture.activeSessions["participant_1"]?.value = null
                advanceUntilIdle()
            }

            fixture.coordinator.close()
            advanceUntilIdle()

            assertTrue(fixture.client.activeChannels.isEmpty())
            assertEquals(
                fixture.client.openedSubscriptions,
                fixture.client.closedSubscriptions
            )
        } finally {
            fixture.scope.cancel()
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

private fun kotlinx.coroutines.test.TestScope.coordinatorFixture(
    cursorByChannel: Map<String, Long> = emptyMap()
): CoordinatorFixture {
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
        channelCursorProvider = { channel -> cursorByChannel[channel] },
        scope = scope
    )
    coordinator.start()
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
    override val activeUserId: String? = "test-user"
    override fun currentAccount(): ActiveRealtimeAccount? =
        ActiveRealtimeAccount(
            userId = requireNotNull(activeUserId),
            client = client
        )
    override suspend fun activateUser(uid: String) = Unit

    override suspend fun deactivateUser() = Unit

    override fun requireClient(): SessionRealtimeClient = client
}

private class TrackingRealtimeClient : SessionRealtimeClient {
    val activeChannels = linkedSetOf<String>()
    val history = linkedMapOf<String, List<ReceivedRealtimeEvent>>()
    val fetchedAfterTimetokens = linkedMapOf<String, Long?>()
    var openedSubscriptions: Int = 0
    var closedSubscriptions: Int = 0
    var historyThrowable: Throwable? = null
    var onFetchHistory:
        suspend TrackingRealtimeClient.(String, Long?, Int) -> Unit =
            { _, _, _ -> }
    val connectionState = MutableStateFlow<RealtimeConnectionState>(
        RealtimeConnectionState.Connected
    )
    private val subscriptions =
        linkedMapOf<String, LinkedHashMap<Int, Channel<ReceivedRealtimeEvent>>>()
    private var nextOwnerId: Int = 1

    override suspend fun joinSession(request: com.example.groupaac.data.pi.PiJoinRequest) = Unit

    override suspend fun sendMessage(payload: com.example.groupaac.data.pi.PiMessagePayload) = Unit

    override suspend fun sendSignal(payload: com.example.groupaac.data.pi.PiSignalPayload) = Unit

    override suspend fun sendDisplayCommand(command: com.example.groupaac.data.pi.DisplayCommand) = Unit

    override suspend fun publish(
        channel: String,
        event: RealtimeEvent
    ): Long = 1L

    override fun openSubscription(channel: String): RealtimeSubscription {
        val ownerId = nextOwnerId++
        val ownerChannel = Channel<ReceivedRealtimeEvent>(Channel.UNLIMITED)
        val channelOwners =
            subscriptions.getOrPut(channel) { linkedMapOf() }
        channelOwners[ownerId] = ownerChannel
        activeChannels += channel
        openedSubscriptions += 1

        return object : RealtimeSubscription {
            override val events: Flow<ReceivedRealtimeEvent> =
                ownerChannel.receiveAsFlow()
            private var closed = false

            override fun close() {
                if (closed) {
                    return
                }
                closed = true
                subscriptions[channel]?.remove(ownerId)
                if (subscriptions[channel].isNullOrEmpty()) {
                    subscriptions.remove(channel)
                    activeChannels.remove(channel)
                }
                closedSubscriptions += 1
                ownerChannel.close()
            }
        }
    }

    override fun observeConnectionState(): StateFlow<RealtimeConnectionState> =
        connectionState.asStateFlow()

    override fun observeSessionEvents(sessionId: String) = emptyFlow<com.example.groupaac.data.pi.PiSessionEvent>()

    override suspend fun fetchHistory(
        channel: String,
        afterTimetoken: Long?,
        limit: Int
    ): List<ReceivedRealtimeEvent> {
        historyThrowable?.let { throw it }
        fetchedAfterTimetokens[channel] = afterTimetoken
        onFetchHistory(channel, afterTimetoken, limit)
        return history[channel].orEmpty()
    }

    override suspend fun close() = Unit

    suspend fun emit(received: ReceivedRealtimeEvent) {
        subscriptions[received.channel]
            ?.values
            ?.toList()
            .orEmpty()
            .forEach { ownerChannel ->
                ownerChannel.send(received)
            }
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

    override suspend fun publishSessionSettingsChanged(
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
        member: SessionMemberEntity,
        actorUserId: String
    ) = Unit

    override suspend fun publishMemberLeft(
        session: SessionEntity,
        member: SessionMemberEntity,
        actorUserId: String
    ) = Unit

    override suspend fun publishMemberRemoved(
        session: SessionEntity,
        member: SessionMemberEntity,
        actorUserId: String
    ) = Unit

    override suspend fun publishMemberDisplayNameChanged(
        session: SessionEntity,
        member: SessionMemberEntity,
        actorUserId: String
    ) = Unit

    override suspend fun publishMemberRoleChanged(
        session: SessionEntity,
        member: SessionMemberEntity,
        actorUserId: String
    ) = Unit

    override suspend fun publishHostTransferred(
        session: SessionEntity,
        newHostMember: SessionMemberEntity,
        previousHostUserId: String,
        actorUserId: String
    ) = Unit

    override suspend fun publishFacilitatorRequested(
        request: SessionJoinRequestEntity,
        actorUserId: String
    ) = Unit

    override suspend fun publishFacilitatorApproved(
        request: SessionJoinRequestEntity,
        member: SessionMemberEntity,
        session: SessionEntity,
        actorUserId: String
    ) = Unit

    override suspend fun publishFacilitatorDeclined(
        request: SessionJoinRequestEntity,
        session: SessionEntity,
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

    override suspend fun publishMessageDeleted(
        message: MessageEntity,
        actorUserId: String
    ) = Unit

    override suspend fun publishAnnouncementCreated(
        message: MessageEntity,
        senderName: String,
        actorUserId: String
    ) = Unit

    override suspend fun publishAttachmentAvailable(
        message: MessageEntity,
        attachment: AttachmentEntity,
        actorUserId: String
    ) = Unit

    override suspend fun publishAttachmentFailed(
        message: MessageEntity,
        attachment: AttachmentEntity,
        actorUserId: String,
        errorMessage: String?
    ) = Unit

    override suspend fun publishSignalCreated(
        signal: StatusSignalEntity,
        displayName: String
    ) = Unit

    override suspend fun publishSignalSnoozed(
        signal: StatusSignalEntity,
        facilitatorUserId: String
    ) = Unit

    override suspend fun publishSignalCleared(
        signal: StatusSignalEntity,
        actorUserId: String
    ) = Unit

    override suspend fun publishSnapshotRequested(
        sessionId: String,
        requesterUserId: String,
        actorUserId: String
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
        eventId: String,
        session: SessionEntity,
        message: MessageEntity,
        senderName: String,
        actorUserId: String,
        restore: Boolean,
        isPinned: Boolean,
        origin: DisplayCommandOrigin
    ) = Unit

    override suspend fun publishDisplayPinState(
        eventId: String,
        sessionId: String,
        messageId: String,
        actorUserId: String,
        pinned: Boolean,
        displayMode: DisplayMode,
        origin: DisplayCommandOrigin?
    ) = Unit

    override suspend fun publishDisplayClear(
        eventId: String,
        sessionId: String,
        actorUserId: String,
        displayMode: DisplayMode,
        origin: DisplayCommandOrigin?
    ) = Unit

    override suspend fun publishDisplayModeChanged(
        eventId: String,
        sessionId: String,
        actorUserId: String,
        displayMode: DisplayMode,
        currentMessageId: String?,
        isPinned: Boolean,
        origin: DisplayCommandOrigin?
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
    displayId = "display-1",
    userId = userId,
    role = role,
    joinedAt = 10L,
    scheduledStartAt = null,
    scheduledDurationMinutes = null,
    actualStartedAt = 10L
)

private fun receivedEvent(
    channel: String,
    sessionId: String,
    eventId: String = "evt-$sessionId",
    type: String = "message.created",
    timetoken: Long = 99L
): ReceivedRealtimeEvent = ReceivedRealtimeEvent(
    channel = channel,
    timetoken = timetoken,
    publisherUserId = "publisher",
    event = RealtimeEvent(
        eventId = eventId,
        type = type,
        sessionId = sessionId,
        actorUserId = "publisher",
        occurredAt = 10L,
        payload = JsonObject(mapOf("text" to JsonPrimitive("hello")))
    )
)
