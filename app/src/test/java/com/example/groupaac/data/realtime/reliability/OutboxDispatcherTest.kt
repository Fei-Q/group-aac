package com.example.groupaac.data.realtime.reliability

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.groupaac.data.AppDatabase
import com.example.groupaac.data.entity.MessageEntity
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.repository.ImmediateTransactionRunner
import com.example.groupaac.data.realtime.RealtimeClientManager
import com.example.groupaac.data.realtime.RealtimeConnectionState
import com.example.groupaac.data.realtime.RealtimeSubscription
import com.example.groupaac.data.realtime.SessionRealtimeClient
import com.example.groupaac.data.realtime.protocol.ReceivedRealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeChannels
import com.example.groupaac.data.realtime.protocol.RealtimeEvent
import com.example.groupaac.data.realtime.reliability.NoOpOutboxDispatcher
import com.example.groupaac.data.realtime.sync.DefaultSessionRealtimeSync
import com.example.groupaac.data.repository.MessageRepository
import com.example.groupaac.model.MessageStatus
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.model.MessageTransportStatus
import com.example.groupaac.model.OutboxDomainType
import com.example.groupaac.model.OutboxEventState
import com.example.groupaac.model.SessionRole
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OutboxDispatcherTest {
    private lateinit var database: AppDatabase
    private lateinit var store: RealtimeReliabilityStore
    private lateinit var sync: DefaultSessionRealtimeSync
    private lateinit var client: ControllableRealtimeClient
    private lateinit var manager: RealtimeClientManager
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
        client = ControllableRealtimeClient()
        manager = object : RealtimeClientManager {
            override suspend fun activateUser(uid: String) = Unit

            override suspend fun deactivateUser() = Unit

            override fun requireClient(): SessionRealtimeClient = client
        }

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
            now = Long.MAX_VALUE,
            limit = 10
        ).single { it.domainType == OutboxDomainType.MESSAGE }

        assertNotNull(message)
        assertEquals(MessageStatus.ACTIVE, message?.status)
        assertEquals(MessageTransportStatus.PENDING, message?.transportStatus)
        assertEquals(messageId, outbox.domainId)
        assertEquals(OutboxDomainType.MESSAGE, outbox.domainType)
    }

    @Test
    fun failedPublishMarksMessageFailedAndRespectsBackoffUntilRetryWindow() = runTest {
        enqueueMessageOutbox("msg-fail", "evt-fail")
        client.failuresRemaining = 1
        val dispatcher = dispatcher(this)

        dispatcher.dispatchDueEvents()

        var outbox = database.reliabilityDao().getOutboxEvent("evt-fail")
        var message = database.messageDao().getMessage("msg-fail")
        assertEquals(OutboxEventState.FAILED, outbox?.state)
        assertEquals(1, outbox?.attemptCount)
        assertEquals(2_000L, outbox?.nextAttemptAt)
        assertEquals(MessageTransportStatus.FAILED, message?.transportStatus)
        assertEquals(1, client.publishAttempts)

        clock = 1_500L
        dispatcher.dispatchDueEvents()
        assertEquals(1, client.publishAttempts)

        clock = 2_000L
        dispatcher.dispatchDueEvents()
        outbox = database.reliabilityDao().getOutboxEvent("evt-fail")
        message = database.messageDao().getMessage("msg-fail")
        assertEquals(OutboxEventState.SENT, outbox?.state)
        assertEquals(MessageTransportStatus.SENT, message?.transportStatus)
        assertEquals(2, client.publishAttempts)
    }

    @Test
    fun staleSendingRowsRecoverAndRetryOnRestart() = runTest {
        enqueueMessageOutbox("msg-recover", "evt-recover")
        store.markSending(
            eventId = "evt-recover",
            attemptCount = 1,
            now = clock
        )
        clock = 2_000L
        val dispatcher = dispatcher(this)

        dispatcher.dispatchDueEvents()

        val outbox = database.reliabilityDao().getOutboxEvent("evt-recover")
        val message = database.messageDao().getMessage("msg-recover")
        assertEquals(OutboxEventState.SENT, outbox?.state)
        assertEquals(MessageTransportStatus.SENT, message?.transportStatus)
        assertEquals(1, client.publishAttempts)
    }

    @Test
    fun manualRetryResetsMaxAttemptFailureAndPublishesAgain() = runTest {
        enqueueMessageOutbox("msg-retry", "evt-retry")
        client.failuresRemaining = RealtimeReliabilityStore.MAX_ATTEMPTS
        val dispatcher = dispatcher(this)

        repeat(RealtimeReliabilityStore.MAX_ATTEMPTS) { attempt ->
            dispatcher.dispatchDueEvents()
            val outbox = database.reliabilityDao().getOutboxEvent("evt-retry")
            clock = outbox?.nextAttemptAt ?: (2_000L + attempt)
        }

        dispatcher.dispatchDueEvents()
        assertEquals(RealtimeReliabilityStore.MAX_ATTEMPTS, client.publishAttempts)
        assertEquals(
            OutboxEventState.FAILED,
            database.reliabilityDao().getOutboxEvent("evt-retry")?.state
        )

        client.failuresRemaining = 0
        dispatcher.retryEvent("evt-retry")

        val outbox = database.reliabilityDao().getOutboxEvent("evt-retry")
        val message = database.messageDao().getMessage("msg-retry")
        assertEquals(RealtimeReliabilityStore.MAX_ATTEMPTS + 1, client.publishAttempts)
        assertEquals(OutboxEventState.SENT, outbox?.state)
        assertEquals(1, outbox?.attemptCount)
        assertEquals(MessageTransportStatus.SENT, message?.transportStatus)
    }

    private fun dispatcher(scope: kotlinx.coroutines.CoroutineScope): OutboxDispatcher {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return OutboxDispatcher(
            context = context,
            database = database,
            reliabilityStore = store,
            realtimeClientManager = manager,
            scope = scope,
            clock = { clock },
            fallbackScheduler = {}
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

    private suspend fun enqueueMessageOutbox(messageId: String, eventId: String) {
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
                actorUserId = "participant1",
                occurredAt = clock,
                payload = JsonObject(
                    mapOf("messageId" to JsonPrimitive(messageId))
                )
            ),
            now = clock
        )
    }
}

private class ControllableRealtimeClient : SessionRealtimeClient {
    var failuresRemaining: Int = 0
    var publishAttempts: Int = 0
    private val connectionState = MutableStateFlow<RealtimeConnectionState>(
        RealtimeConnectionState.Connected
    )

    override suspend fun joinSession(request: com.example.groupaac.data.pi.PiJoinRequest) = Unit

    override suspend fun sendMessage(payload: com.example.groupaac.data.pi.PiMessagePayload) = Unit

    override suspend fun sendSignal(payload: com.example.groupaac.data.pi.PiSignalPayload) = Unit

    override suspend fun sendDisplayCommand(command: com.example.groupaac.data.pi.DisplayCommand) = Unit

    override suspend fun publish(channel: String, event: RealtimeEvent): Long {
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
