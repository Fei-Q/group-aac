package com.example.groupaac.data.realtime.reliability

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import com.example.groupaac.data.AppDatabase
import com.example.groupaac.data.entity.MessageEntity
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.repository.ImmediateTransactionRunner
import com.example.groupaac.data.repository.MessageRepository
import com.example.groupaac.data.realtime.ActiveRealtimeAccount
import com.example.groupaac.data.realtime.RealtimeClientManager
import com.example.groupaac.data.realtime.RealtimeConnectionState
import com.example.groupaac.data.realtime.RealtimeSubscription
import com.example.groupaac.data.realtime.SessionRealtimeClient
import com.example.groupaac.data.realtime.protocol.ReceivedRealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeChannels
import com.example.groupaac.data.realtime.protocol.RealtimeEvent
import com.example.groupaac.data.realtime.sync.DefaultSessionRealtimeSync
import com.example.groupaac.model.MessageStatus
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.model.MessageTransportStatus
import com.example.groupaac.model.OutboxDomainType
import com.example.groupaac.model.OutboxEventState
import com.example.groupaac.model.SessionRole
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OutboxDispatcherTest {
    private lateinit var database: AppDatabase
    private lateinit var store: RealtimeReliabilityStore
    private lateinit var sync: DefaultSessionRealtimeSync
    private lateinit var aliceClient: ControllableRealtimeClient
    private lateinit var bobClient: ControllableRealtimeClient
    private lateinit var manager: TestRealtimeClientManager
    private lateinit var scheduledDelays: MutableList<Long>
    private var clock = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        store = RealtimeReliabilityStore(database, database.reliabilityDao())
        sync = DefaultSessionRealtimeSync(
            transactionRunner = ImmediateTransactionRunner,
            sessionDao = database.sessionDao(),
            sessionJoinRequestDao = database.sessionJoinRequestDao(),
            messageDao = database.messageDao(),
            statusSignalDao = database.statusSignalDao(),
            reliabilityDao = database.reliabilityDao(),
            reliabilityStore = store
        )
        aliceClient = ControllableRealtimeClient()
        bobClient = ControllableRealtimeClient()
        manager = TestRealtimeClientManager(
            clients = mapOf(
                "alice" to aliceClient,
                "bob" to bobClient
            ),
            activeUserId = "alice"
        )
        scheduledDelays = mutableListOf()

        runBlocking {
            database.userDao().upsertUser(
                UserEntity(uid = "host1", displayName = "Host", createdAt = 1L)
            )
            database.userDao().upsertUser(
                UserEntity(uid = "participant1", displayName = "Participant", createdAt = 1L)
            )
            database.sessionDao().upsertSession(
                SessionEntity(
                    id = "session1",
                    name = "Group",
                    joinCode = "1234-5678",
                    hostUserId = "host1",
                    createdAt = 1L
                )
            )
            database.sessionDao().upsertMember(
                SessionMemberEntity(
                    sessionId = "session1",
                    userId = "participant1",
                    displayName = "Participant",
                    role = SessionRole.PARTICIPANT,
                    joinedAt = 1L
                )
            )
        }
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun messageSendIsStoredWithOutboxAndPendingTransportState() = runTest {
        val repository = messageRepository()

        val messageId = repository.sendText(
            sessionId = "session1",
            senderUserId = "participant1",
            target = MessageTarget.GROUP,
            text = "Hello group"
        )

        val message = database.messageDao().getMessage(messageId)
        val outbox = database.reliabilityDao().getRetryableOutboxEvents(
            actorUserId = "participant1",
            now = Long.MAX_VALUE,
            maxAttempts = RealtimeReliabilityStore.MAX_ATTEMPTS,
            limit = 10
        ).single { it.domainType == OutboxDomainType.MESSAGE }

        assertNotNull(message)
        assertEquals(MessageStatus.ACTIVE, message?.status)
        assertEquals(MessageTransportStatus.PENDING, message?.transportStatus)
        assertEquals(messageId, outbox.domainId)
        assertEquals(OutboxDomainType.MESSAGE, outbox.domainType)
    }

    @Test
    fun foregroundAndBackgroundDispatchersRaceForOneRowAndPublishOnce() = runTest {
        enqueueMessageOutbox(
            messageId = "msg-race",
            eventId = "evt-race",
            actorUserId = "alice"
        )
        val gate = CompletableDeferred<Unit>()
        aliceClient.beforePublish = { gate.await() }
        val foreground = dispatcher(this)
        val background = dispatcher(this)

        val first = async { foreground.dispatchDueEvents() }
        val second = async { background.dispatchDueEvents() }
        gate.complete(Unit)
        first.await()
        second.await()

        assertEquals(1, aliceClient.publishAttempts)
        assertEquals(
            OutboxEventState.SENT,
            database.reliabilityDao().getOutboxEvent("evt-race")?.state
        )
    }

    @Test
    fun secondClaimantReceivesZeroClaimedRows() = runTest {
        enqueueMessageOutbox(
            messageId = "msg-claim",
            eventId = "evt-claim",
            actorUserId = "alice"
        )

        assertTrue(
            store.claimSending(
                eventId = "evt-claim",
                actorUserId = "alice",
                attemptCount = 1,
                now = clock
            )
        )
        assertFalse(
            store.claimSending(
                eventId = "evt-claim",
                actorUserId = "alice",
                attemptCount = 1,
                now = clock
            )
        )
    }

    @Test
    fun alicesQueuedEventIsNotPublishedThroughBobsActiveClient() = runTest {
        enqueueMessageOutbox(
            messageId = "msg-alice",
            eventId = "evt-alice",
            actorUserId = "alice"
        )
        manager.activeUserId = "bob"

        dispatcher(this).dispatchDueEvents()

        assertEquals(0, bobClient.publishAttempts)
        assertEquals(0, aliceClient.publishAttempts)
        assertEquals(
            OutboxEventState.PENDING,
            database.reliabilityDao().getOutboxEvent("evt-alice")?.state
        )
    }

    @Test
    fun alicesRowRemainsQueuedAfterSwitchingToBob() = runTest {
        enqueueMessageOutbox(
            messageId = "msg-queued",
            eventId = "evt-queued",
            actorUserId = "alice"
        )
        manager.activeUserId = "bob"

        dispatcher(this).requestImmediateDispatch()

        assertEquals(
            OutboxEventState.PENDING,
            database.reliabilityDao().getOutboxEvent("evt-queued")?.state
        )
    }

    @Test
    fun switchAliceToBobAfterClaimBeforePublishNeverPublishesThroughBobAndKeepsRowRetryable() = runTest {
        enqueueMessageOutbox(
            messageId = "msg-switch",
            eventId = "evt-switch",
            actorUserId = "alice"
        )
        manager.switchToUserOnCurrentAccountCall = 2 to "bob"

        dispatcher(this).dispatchDueEvents()

        val outbox = database.reliabilityDao().getOutboxEvent("evt-switch")
        assertEquals(0, aliceClient.publishAttempts)
        assertEquals(0, bobClient.publishAttempts)
        assertEquals(OutboxEventState.PENDING, outbox?.state)
        assertEquals(0, outbox?.attemptCount)
        assertEquals(clock, outbox?.nextAttemptAt)
    }

    @Test
    fun switchingBackToAliceDispatchesHerRow() = runTest {
        enqueueMessageOutbox(
            messageId = "msg-return",
            eventId = "evt-return",
            actorUserId = "alice"
        )
        manager.activeUserId = "bob"
        dispatcher(this).dispatchDueEvents()

        manager.activeUserId = "alice"
        dispatcher(this).dispatchDueEvents()

        assertEquals(1, aliceClient.publishAttempts)
        assertEquals(
            OutboxEventState.SENT,
            database.reliabilityDao().getOutboxEvent("evt-return")?.state
        )
    }

    @Test
    fun switchingBackToAlicePublishesClaimReleasedRowSuccessfully() = runTest {
        enqueueMessageOutbox(
            messageId = "msg-retryable",
            eventId = "evt-retryable",
            actorUserId = "alice"
        )
        manager.switchToUserOnCurrentAccountCall = 2 to "bob"

        dispatcher(this).dispatchDueEvents()
        assertEquals(
            OutboxEventState.PENDING,
            database.reliabilityDao().getOutboxEvent("evt-retryable")?.state
        )

        manager.activeUserId = "alice"
        manager.switchToUserOnCurrentAccountCall = null
        dispatcher(this).dispatchDueEvents()

        assertEquals(1, aliceClient.publishAttempts)
        assertEquals(0, bobClient.publishAttempts)
        assertEquals(
            OutboxEventState.SENT,
            database.reliabilityDao().getOutboxEvent("evt-retryable")?.state
        )
    }

    @Test
    fun nullOrMismatchedActorEventsAreRejected() = runTest {
        try {
            store.enqueueOutboxEvent(
                domainType = OutboxDomainType.MESSAGE,
                domainId = "msg-null",
                channel = RealtimeChannels.public("session1"),
                event = RealtimeEvent(
                    eventId = "evt-null",
                    type = "message.created",
                    sessionId = "session1",
                    actorUserId = null,
                    occurredAt = clock,
                    payload = JsonObject(mapOf("messageId" to JsonPrimitive("msg-null")))
                ),
                now = clock
            )
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message?.contains("actorUserId") == true)
        }

        enqueueMessageOutbox(
            messageId = "msg-mismatch",
            eventId = "evt-mismatch",
            actorUserId = "alice",
            eventActorUserId = "bob"
        )

        dispatcher(this).dispatchDueEvents()

        assertEquals(0, aliceClient.publishAttempts)
        assertEquals(
            OutboxEventState.FAILED,
            database.reliabilityDao().getOutboxEvent("evt-mismatch")?.state
        )
    }

    @Test
    fun failedPublicationRecordsFutureNextAttemptAt() = runTest {
        enqueueMessageOutbox(
            messageId = "msg-fail",
            eventId = "evt-fail",
            actorUserId = "alice"
        )
        aliceClient.failuresRemaining = 1

        dispatcher(this).dispatchDueEvents()

        val outbox = database.reliabilityDao().getOutboxEvent("evt-fail")
        assertEquals(OutboxEventState.FAILED, outbox?.state)
        assertEquals(2_000L, outbox?.nextAttemptAt)
    }

    @Test
    fun nextWorkerIsScheduledWithCorrectDelay() = runTest {
        enqueueMessageOutbox(
            messageId = "msg-delay",
            eventId = "evt-delay",
            actorUserId = "alice",
            now = 5_000L
        )
        clock = 1_000L

        dispatcher(this).dispatchDueEvents()

        assertEquals(listOf(4_000L), scheduledDelays)
    }

    @Test
    fun workerUsesNetworkConstraint() {
        val request = OutboxDispatcher.buildWorkRequest(delayMillis = 123L)

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun staleSendingRowsAreReclaimableAfterLeaseExpires() = runTest {
        enqueueMessageOutbox(
            messageId = "msg-stale",
            eventId = "evt-stale",
            actorUserId = "alice"
        )
        assertTrue(
            store.claimSending(
                eventId = "evt-stale",
                actorUserId = "alice",
                attemptCount = 1,
                now = clock
            )
        )
        clock += RealtimeReliabilityStore.SEND_LEASE_MILLIS

        dispatcher(this).dispatchDueEvents()

        assertEquals(1, aliceClient.publishAttempts)
        assertEquals(
            OutboxEventState.SENT,
            database.reliabilityDao().getOutboxEvent("evt-stale")?.state
        )
    }

    @Test
    fun manualRetryResetsMaxAttemptFailureAndPublishesAgain() = runTest {
        enqueueMessageOutbox(
            messageId = "msg-retry",
            eventId = "evt-retry",
            actorUserId = "alice"
        )
        aliceClient.failuresRemaining = RealtimeReliabilityStore.MAX_ATTEMPTS
        val dispatcher = dispatcher(this)

        repeat(RealtimeReliabilityStore.MAX_ATTEMPTS) {
            dispatcher.dispatchDueEvents()
            clock =
                database.reliabilityDao()
                    .getOutboxEvent("evt-retry")
                    ?.nextAttemptAt
                    ?: clock
        }

        dispatcher.dispatchDueEvents()
        assertEquals(RealtimeReliabilityStore.MAX_ATTEMPTS, aliceClient.publishAttempts)
        assertEquals(
            OutboxEventState.FAILED,
            database.reliabilityDao().getOutboxEvent("evt-retry")?.state
        )

        aliceClient.failuresRemaining = 0
        dispatcher.retryEvent("evt-retry")

        val outbox = database.reliabilityDao().getOutboxEvent("evt-retry")
        val message = database.messageDao().getMessage("msg-retry")
        assertEquals(RealtimeReliabilityStore.MAX_ATTEMPTS + 1, aliceClient.publishAttempts)
        assertEquals(OutboxEventState.SENT, outbox?.state)
        assertEquals(1, outbox?.attemptCount)
        assertEquals(MessageTransportStatus.SENT, message?.transportStatus)
    }

    private fun dispatcher(scope: CoroutineScope): OutboxDispatcher {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return OutboxDispatcher(
            context = context,
            database = database,
            reliabilityStore = store,
            realtimeClientManager = manager,
            scope = scope,
            clock = { clock },
            workScheduler = { delayMillis ->
                scheduledDelays += delayMillis
            }
        )
    }

    private suspend fun messageRepository(): MessageRepository {
        return MessageRepository(
            transactionRunner = ImmediateTransactionRunner,
            messageDao = database.messageDao(),
            sessionDao = database.sessionDao(),
            userDao = database.userDao(),
            reliabilityDao = database.reliabilityDao(),
            reliabilityStore = store,
            outboxDispatcher = NoOpOutboxDispatcher,
            sessionRealtimeSync = sync
        )
    }

    private suspend fun enqueueMessageOutbox(
        messageId: String,
        eventId: String,
        actorUserId: String,
        eventActorUserId: String = actorUserId,
        now: Long = clock
    ) {
        database.messageDao().upsertMessage(
            MessageEntity(
                id = messageId,
                sessionId = "session1",
                senderUserId = "participant1",
                target = MessageTarget.GROUP,
                text = "hello",
                createdAt = 1L,
                status = MessageStatus.ACTIVE,
                transportStatus = MessageTransportStatus.PENDING
            )
        )
        store.enqueueOutboxEvent(
            domainType = OutboxDomainType.MESSAGE,
            domainId = messageId,
            channel = RealtimeChannels.public("session1"),
            event = RealtimeEvent(
                eventId = eventId,
                type = "message.created",
                sessionId = "session1",
                actorUserId = eventActorUserId,
                occurredAt = now,
                payload = JsonObject(
                    mapOf("messageId" to JsonPrimitive(messageId))
                )
            ),
            now = now
        )
        if (actorUserId != eventActorUserId) {
            database.reliabilityDao().upsertOutboxEvent(
                requireNotNull(database.reliabilityDao().getOutboxEvent(eventId)).copy(
                    actorUserId = actorUserId
                )
            )
        }
    }
}

private class TestRealtimeClientManager(
    private val clients: Map<String, ControllableRealtimeClient>,
    override var activeUserId: String?
) : RealtimeClientManager {
    var currentAccountCalls: Int = 0
    var switchToUserOnCurrentAccountCall: Pair<Int, String>? = null

    override fun currentAccount(): ActiveRealtimeAccount? {
        currentAccountCalls += 1
        switchToUserOnCurrentAccountCall?.let { (callIndex, nextUser) ->
            if (currentAccountCalls == callIndex) {
                activeUserId = nextUser
            }
        }
        val userId = activeUserId ?: return null
        val client = clients[userId]
            ?: error("No active client for $userId")
        return ActiveRealtimeAccount(
            userId = userId,
            client = client
        )
    }

    override suspend fun activateUser(uid: String) {
        activeUserId = uid
    }

    override suspend fun deactivateUser() {
        activeUserId = null
    }

    override fun requireClient(): SessionRealtimeClient =
        clients[activeUserId]
            ?: error("No active client for $activeUserId")
}

private class ControllableRealtimeClient : SessionRealtimeClient {
    var failuresRemaining: Int = 0
    var publishAttempts: Int = 0
    var beforePublish: (suspend () -> Unit)? = null
    private val connectionState = MutableStateFlow<RealtimeConnectionState>(
        RealtimeConnectionState.Connected
    )

    override suspend fun joinSession(request: com.example.groupaac.data.pi.PiJoinRequest) = Unit

    override suspend fun sendMessage(payload: com.example.groupaac.data.pi.PiMessagePayload) = Unit

    override suspend fun sendSignal(payload: com.example.groupaac.data.pi.PiSignalPayload) = Unit

    override suspend fun sendDisplayCommand(command: com.example.groupaac.data.pi.DisplayCommand) = Unit

    override suspend fun publish(channel: String, event: RealtimeEvent): Long {
        beforePublish?.invoke()
        publishAttempts += 1
        if (failuresRemaining > 0) {
            failuresRemaining -= 1
            error("publish failed")
        }
        return 9_999L
    }

    override fun openSubscription(channel: String): RealtimeSubscription =
        object : RealtimeSubscription {
            override val events: Flow<ReceivedRealtimeEvent> = emptyFlow()

            override fun close() = Unit
        }

    override fun observeConnectionState(): StateFlow<RealtimeConnectionState> =
        connectionState

    override fun observeSessionEvents(sessionId: String): Flow<com.example.groupaac.data.pi.PiSessionEvent> =
        emptyFlow()

    override suspend fun fetchHistory(
        channel: String,
        afterTimetoken: Long?,
        limit: Int
    ): List<ReceivedRealtimeEvent> = emptyList()

    override suspend fun close() = Unit
}
