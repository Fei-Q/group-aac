package com.example.groupaac.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.groupaac.data.AppDatabase
import com.example.groupaac.data.entity.DisplayStateEntity
import com.example.groupaac.data.entity.MessageEntity
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.entity.UserSettingsEntity
import com.example.groupaac.data.realtime.protocol.RealtimeChannels
import com.example.groupaac.data.realtime.protocol.RealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeEventTypes
import com.example.groupaac.data.realtime.protocol.ReceivedRealtimeEvent
import com.example.groupaac.data.realtime.reliability.NoOpOutboxDispatcher
import com.example.groupaac.data.realtime.reliability.RealtimeReliabilityStore
import com.example.groupaac.data.realtime.sync.DefaultSessionRealtimeSync
import com.example.groupaac.data.realtime.sync.DisplayStatePayload
import com.example.groupaac.data.session.ActiveSessionStore
import com.example.groupaac.data.sessiondirectory.FakeSessionDirectory
import com.example.groupaac.model.DisplayCommandOrigin
import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.model.SessionRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DisplayCommandAcknowledgementIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var reliabilityStore: RealtimeReliabilityStore
    private lateinit var sync: DefaultSessionRealtimeSync
    private lateinit var messageRepository: MessageRepository
    private lateinit var sessionRepository: SessionRepository

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        reliabilityStore = RealtimeReliabilityStore(
            database = database,
            reliabilityDao = database.reliabilityDao()
        )
        sync = newSync()
        messageRepository = MessageRepository(
            transactionRunner = RoomTransactionRunner(database),
            messageDao = database.messageDao(),
            sessionDao = database.sessionDao(),
            userDao = database.userDao(),
            reliabilityDao = database.reliabilityDao(),
            reliabilityStore = reliabilityStore,
            outboxDispatcher = NoOpOutboxDispatcher,
            sessionRealtimeSync = sync
        )
        sessionRepository = SessionRepository(
            transactionRunner = RoomTransactionRunner(database),
            sessionDao = database.sessionDao(),
            sessionJoinRequestDao = database.sessionJoinRequestDao(),
            userDao = database.userDao(),
            activeSessionStore = NoOpActiveSessionStore,
            sessionDirectory = FakeSessionDirectory(),
            outboxDispatcher = NoOpOutboxDispatcher,
            sessionRealtimeSync = sync,
            getDisplayState = database.reliabilityDao()::getDisplayState,
            upsertDisplayState = database.reliabilityDao()::upsertDisplayState
        )

        database.userDao().upsertUser(
            UserEntity(
                uid = "host1",
                displayName = "Host",
                createdAt = 1L
            )
        )
        database.userDao().upsertUser(
            UserEntity(
                uid = "participant1",
                displayName = "Participant",
                createdAt = 2L
            )
        )
        database.userDao().upsertUser(
            UserEntity(
                uid = "facilitator1",
                displayName = "Facilitator",
                createdAt = 3L
            )
        )
        database.userDao().upsertSettings(
            UserSettingsEntity(userId = "host1")
        )
        database.sessionDao().upsertSession(
            SessionEntity(
                id = SESSION_ID,
                name = "Group",
                joinCode = "1234-5678",
                hostUserId = "host1",
                displayMode = DisplayMode.APPROVAL_REQUIRED,
                displayId = DISPLAY_ID,
                createdAt = 1L
            )
        )
        database.sessionDao().upsertMember(
            SessionMemberEntity(
                sessionId = SESSION_ID,
                userId = "host1",
                displayName = "Host",
                role = SessionRole.HOST,
                joinedAt = 1L
            )
        )
        database.sessionDao().upsertMember(
            SessionMemberEntity(
                sessionId = SESSION_ID,
                userId = "participant1",
                displayName = "Participant",
                role = SessionRole.PARTICIPANT,
                joinedAt = 2L
            )
        )
        insertMessage("msg-1", "First", 10L)
        insertMessage("msg-2", "Second", 20L)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun showCommandUsesActualOutboxEventIdAndAcceptsFreshAcknowledgement() = runTest {
        messageRepository.displayMessage(SESSION_ID, "msg-1")

        val command = requireDisplayCommand()

        assertEquals(RealtimeEventTypes.DISPLAY_SHOW_MESSAGE, command.event.type)
        assertEquals(command.event.eventId, command.state.lastIssuedCommandEventId)
        assertEquals(1, displayOutboxCount())
        assertEquals("msg-1", command.state.currentMessageId)
        assertEquals(DisplayCommandOrigin.MANUAL_SHOW, command.state.commandOrigin)

        reliabilityStore.markDisplayCommandPublished(SESSION_ID, command.event.eventId, 100L)

        assertTrue(
            sync.applyIncoming(
                acknowledgement(
                    inReplyToEventId = command.event.eventId,
                    type = RealtimeEventTypes.DISPLAY_RENDERED,
                    currentMessageId = "msg-1",
                    isPinned = false,
                    displayMode = DisplayMode.APPROVAL_REQUIRED,
                    origin = DisplayCommandOrigin.MANUAL_SHOW,
                    timetoken = 200L
                )
            )
        )

        val state = database.reliabilityDao().getDisplayState(SESSION_ID)
        assertNull(state?.lastIssuedCommandEventId)
        assertEquals(100L, state?.lastPublishedCommandTimetoken)
        assertEquals(200L, state?.lastPiAppliedCommandTimetoken)
        assertEquals(command.event.eventId, state?.lastAppliedCommandEventId)
        assertTrue(database.messageDao().getMessage("msg-1")?.displayedOnMonitor == true)
    }

    @Test
    fun restoreCommandPreservesPinAndAcknowledgesWithActualEventId() = runTest {
        database.reliabilityDao().upsertDisplayState(
            DisplayStateEntity(
                sessionId = SESSION_ID,
                currentMessageId = "msg-1",
                isPinned = true,
                displayMode = DisplayMode.APPROVAL_REQUIRED,
                commandOrigin = DisplayCommandOrigin.MANUAL_SHOW,
                localOptimisticUpdatedAt = 50L
            )
        )
        database.messageDao().markDisplayed("msg-1")

        messageRepository.restoreMessage(SESSION_ID, "msg-2")

        val command = requireDisplayCommand()
        assertEquals(RealtimeEventTypes.DISPLAY_RESTORE_MESSAGE, command.event.type)
        assertEquals(true, command.state.isPinned)

        reliabilityStore.markDisplayCommandPublished(SESSION_ID, command.event.eventId, 300L)

        assertTrue(
            sync.applyIncoming(
                acknowledgement(
                    inReplyToEventId = command.event.eventId,
                    type = RealtimeEventTypes.DISPLAY_RESTORED,
                    currentMessageId = "msg-2",
                    isPinned = true,
                    displayMode = DisplayMode.APPROVAL_REQUIRED,
                    origin = DisplayCommandOrigin.MANUAL_RESTORE,
                    timetoken = 400L
                )
            )
        )

        val state = database.reliabilityDao().getDisplayState(SESSION_ID)
        assertEquals("msg-2", state?.currentMessageId)
        assertEquals(true, state?.isPinned)
        assertEquals(DisplayCommandOrigin.MANUAL_RESTORE, state?.commandOrigin)
    }

    @Test
    fun pinAndUnpinCommandsUseOutboxEventIdsAndUpdatePiState() = runTest {
        primeDisplayedMessage("msg-1")

        messageRepository.pinDisplayedMessage(SESSION_ID)
        val pinCommand = requireDisplayCommand()
        reliabilityStore.markDisplayCommandPublished(SESSION_ID, pinCommand.event.eventId, 500L)
        assertTrue(
            sync.applyIncoming(
                acknowledgement(
                    inReplyToEventId = pinCommand.event.eventId,
                    type = RealtimeEventTypes.DISPLAY_PINNED,
                    currentMessageId = "msg-1",
                    isPinned = true,
                    displayMode = DisplayMode.APPROVAL_REQUIRED,
                    origin = DisplayCommandOrigin.MANUAL_SHOW,
                    timetoken = 600L
                )
            )
        )

        assertEquals(true, database.reliabilityDao().getDisplayState(SESSION_ID)?.isPinned)

        messageRepository.unpinDisplayedMessage(SESSION_ID)
        val unpinCommand = requireDisplayCommand()
        reliabilityStore.markDisplayCommandPublished(SESSION_ID, unpinCommand.event.eventId, 700L)
        assertTrue(
            sync.applyIncoming(
                acknowledgement(
                    inReplyToEventId = unpinCommand.event.eventId,
                    type = RealtimeEventTypes.DISPLAY_UNPINNED,
                    currentMessageId = "msg-1",
                    isPinned = false,
                    displayMode = DisplayMode.APPROVAL_REQUIRED,
                    origin = DisplayCommandOrigin.MANUAL_SHOW,
                    timetoken = 800L
                )
            )
        )

        val state = database.reliabilityDao().getDisplayState(SESSION_ID)
        assertEquals(false, state?.isPinned)
        assertEquals("msg-1", state?.currentMessageId)
    }

    @Test
    fun clearCommandRemovesContentAndPinAfterAcknowledgement() = runTest {
        primeDisplayedMessage("msg-1", pinned = true)

        messageRepository.clearDisplay(SESSION_ID)

        val command = requireDisplayCommand()
        reliabilityStore.markDisplayCommandPublished(SESSION_ID, command.event.eventId, 900L)

        assertTrue(
            sync.applyIncoming(
                acknowledgement(
                    inReplyToEventId = command.event.eventId,
                    type = RealtimeEventTypes.DISPLAY_CLEARED,
                    currentMessageId = null,
                    isPinned = false,
                    displayMode = DisplayMode.APPROVAL_REQUIRED,
                    origin = null,
                    timetoken = 1_000L
                )
            )
        )

        val state = database.reliabilityDao().getDisplayState(SESSION_ID)
        assertNull(state?.currentMessageId)
        assertEquals(false, state?.isPinned)
    }

    @Test
    fun modeChangePreservesCurrentMessagePinAndOriginAcrossCommandAndAcknowledgement() = runTest {
        primeDisplayedMessage("msg-1", pinned = true)

        val updated = sessionRepository.updateSessionDisplayMode(
            sessionId = SESSION_ID,
            actorUserId = "host1",
            displayMode = DisplayMode.AUTO_LATEST
        )

        assertEquals(DisplayMode.AUTO_LATEST, updated.displayMode)
        val command = requireDisplayCommand()
        assertEquals(RealtimeEventTypes.DISPLAY_MODE_CHANGED, command.event.type)
        assertEquals("msg-1", command.state.currentMessageId)
        assertEquals(true, command.state.isPinned)
        assertEquals(DisplayCommandOrigin.MANUAL_SHOW, command.state.commandOrigin)
        assertEquals(DisplayMode.AUTO_LATEST, command.state.displayMode)

        reliabilityStore.markDisplayCommandPublished(SESSION_ID, command.event.eventId, 1_100L)

        assertTrue(
            sync.applyIncoming(
                acknowledgement(
                    inReplyToEventId = command.event.eventId,
                    type = RealtimeEventTypes.DISPLAY_STATE,
                    currentMessageId = "msg-1",
                    isPinned = true,
                    displayMode = DisplayMode.AUTO_LATEST,
                    origin = DisplayCommandOrigin.MANUAL_SHOW,
                    timetoken = 1_200L
                )
            )
        )

        val state = database.reliabilityDao().getDisplayState(SESSION_ID)
        assertNull(state?.lastIssuedCommandEventId)
        assertEquals(DisplayMode.AUTO_LATEST, state?.displayMode)
        assertEquals("msg-1", state?.currentMessageId)
        assertEquals(true, state?.isPinned)
        assertEquals(DisplayCommandOrigin.MANUAL_SHOW, state?.commandOrigin)
    }

    @Test
    fun staleAcknowledgementIsSideEffectFree() = runTest {
        messageRepository.displayMessage(SESSION_ID, "msg-1")
        val command = requireDisplayCommand()
        reliabilityStore.markDisplayCommandPublished(SESSION_ID, command.event.eventId, 1_300L)
        val before = database.reliabilityDao().getDisplayState(SESSION_ID)

        assertFalse(
            sync.applyIncoming(
                acknowledgement(
                    inReplyToEventId = command.event.eventId,
                    type = RealtimeEventTypes.DISPLAY_RENDERED,
                    currentMessageId = "msg-2",
                    isPinned = false,
                    displayMode = DisplayMode.APPROVAL_REQUIRED,
                    origin = DisplayCommandOrigin.MANUAL_SHOW,
                    timetoken = 1_250L
                )
            )
        )

        assertEquals(before, database.reliabilityDao().getDisplayState(SESSION_ID))
    }

    @Test
    fun wrongDisplayAndMismatchedAcknowledgementsAreSideEffectFree() = runTest {
        messageRepository.displayMessage(SESSION_ID, "msg-1")
        val command = requireDisplayCommand()
        reliabilityStore.markDisplayCommandPublished(SESSION_ID, command.event.eventId, 1_400L)
        val before = database.reliabilityDao().getDisplayState(SESSION_ID)

        assertFalse(
            sync.applyIncoming(
                acknowledgement(
                    inReplyToEventId = command.event.eventId,
                    type = RealtimeEventTypes.DISPLAY_RENDERED,
                    currentMessageId = "msg-2",
                    isPinned = false,
                    displayMode = DisplayMode.APPROVAL_REQUIRED,
                    origin = DisplayCommandOrigin.MANUAL_SHOW,
                    timetoken = 1_500L,
                    actorUserId = "display-2",
                    publisherUserId = "display-2"
                )
            )
        )
        assertFalse(
            sync.applyIncoming(
                acknowledgement(
                    inReplyToEventId = "different-command",
                    type = RealtimeEventTypes.DISPLAY_RENDERED,
                    currentMessageId = "msg-2",
                    isPinned = false,
                    displayMode = DisplayMode.APPROVAL_REQUIRED,
                    origin = DisplayCommandOrigin.MANUAL_SHOW,
                    timetoken = 1_600L
                )
            )
        )

        assertEquals(before, database.reliabilityDao().getDisplayState(SESSION_ID))
    }

    @Test
    fun oneUserActionCreatesOneDisplayCommandEvent() = runTest {
        messageRepository.displayMessage(SESSION_ID, "msg-1")

        val command = requireDisplayCommand()

        assertNotNull(database.reliabilityDao().getOutboxEvent(command.event.eventId))
        assertEquals(1, displayOutboxCount())
        assertEquals(command.event.eventId, command.state.lastIssuedCommandEventId)
    }

    @Test
    fun restartReconciliationAcceptsFreshAcknowledgementForPersistedOutstandingCommand() = runTest {
        messageRepository.displayMessage(SESSION_ID, "msg-1")
        val command = requireDisplayCommand()
        reliabilityStore.markDisplayCommandPublished(SESSION_ID, command.event.eventId, 1_700L)

        val restartedSync = newSync()

        assertTrue(
            restartedSync.applyIncoming(
                acknowledgement(
                    inReplyToEventId = command.event.eventId,
                    type = RealtimeEventTypes.DISPLAY_RENDERED,
                    currentMessageId = "msg-1",
                    isPinned = false,
                    displayMode = DisplayMode.APPROVAL_REQUIRED,
                    origin = DisplayCommandOrigin.MANUAL_SHOW,
                    timetoken = 1_800L
                )
            )
        )

        val state = database.reliabilityDao().getDisplayState(SESSION_ID)
        assertNull(state?.lastIssuedCommandEventId)
        assertEquals(1_800L, state?.lastPiAppliedCommandTimetoken)
    }

    @Test
    fun delegatedFacilitatorDisplayCommandsUseFacilitatorUidAndAreDispatchable() = runTest {
        primeDisplayedMessage("msg-1", pinned = false)

        messageRepository.displayMessage(
            SESSION_ID,
            "msg-1",
            actorUserId = "facilitator1"
        )
        messageRepository.restoreMessage(
            SESSION_ID,
            "msg-2",
            actorUserId = "facilitator1"
        )
        messageRepository.pinDisplayedMessage(
            SESSION_ID,
            actorUserId = "facilitator1"
        )
        messageRepository.unpinDisplayedMessage(
            SESSION_ID,
            actorUserId = "facilitator1"
        )
        messageRepository.clearDisplay(
            SESSION_ID,
            actorUserId = "facilitator1"
        )

        val facilitatorRows = database.reliabilityDao()
            .getRetryableOutboxEvents(
                actorUserId = "facilitator1",
                now = Long.MAX_VALUE,
                maxAttempts = RealtimeReliabilityStore.MAX_ATTEMPTS,
                limit = 50
            )
            .filter { it.channel == RealtimeChannels.display(SESSION_ID) }
            .map { reliabilityStore.decodeOutboxEvent(it) }

        assertEquals(
            setOf(
                RealtimeEventTypes.DISPLAY_SHOW_MESSAGE,
                RealtimeEventTypes.DISPLAY_RESTORE_MESSAGE,
                RealtimeEventTypes.DISPLAY_PIN_MESSAGE,
                RealtimeEventTypes.DISPLAY_UNPIN_MESSAGE,
                RealtimeEventTypes.DISPLAY_CLEAR
            ),
            facilitatorRows.map { it.type }.toSet()
        )
        assertTrue(
            facilitatorRows.all { it.actorUserId == "facilitator1" }
        )
        assertTrue(
            database.reliabilityDao()
                .getRetryableOutboxEvents(
                    actorUserId = "host1",
                    now = Long.MAX_VALUE,
                    maxAttempts = RealtimeReliabilityStore.MAX_ATTEMPTS,
                    limit = 50
                )
                .none { it.channel == RealtimeChannels.display(SESSION_ID) }
        )
    }

    @Test
    fun deletingAnotherUsersMessageUsesDeletingAccountUidAndIsDispatchable() = runTest {
        messageRepository.deleteMessage(
            messageId = "msg-1",
            actorUserId = "facilitator1"
        )

        val facilitatorRows = database.reliabilityDao()
            .getRetryableOutboxEvents(
                actorUserId = "facilitator1",
                now = Long.MAX_VALUE,
                maxAttempts = RealtimeReliabilityStore.MAX_ATTEMPTS,
                limit = 10
            )
        val deletion = facilitatorRows.single()
        val event = reliabilityStore.decodeOutboxEvent(deletion)

        assertEquals(RealtimeEventTypes.MESSAGE_DELETED, event.type)
        assertEquals("facilitator1", event.actorUserId)
        assertTrue(
            database.reliabilityDao()
                .getRetryableOutboxEvents(
                    actorUserId = "participant1",
                    now = Long.MAX_VALUE,
                    maxAttempts = RealtimeReliabilityStore.MAX_ATTEMPTS,
                    limit = 10
                )
                .isEmpty()
        )
    }

    private suspend fun requireDisplayCommand(): DisplayCommandSnapshot {
        val state = requireNotNull(database.reliabilityDao().getDisplayState(SESSION_ID))
        val eventId = requireNotNull(state.lastIssuedCommandEventId)
        val entry = requireNotNull(database.reliabilityDao().getOutboxEvent(eventId))
        return DisplayCommandSnapshot(
            state = state,
            event = reliabilityStore.decodeOutboxEvent(entry)
        )
    }

    private suspend fun displayOutboxCount(): Int =
        database.reliabilityDao()
            .getRetryableOutboxEvents(
                actorUserId = "host1",
                now = Long.MAX_VALUE,
                maxAttempts = RealtimeReliabilityStore.MAX_ATTEMPTS,
                limit = 50
            )
            .count { it.channel == RealtimeChannels.display(SESSION_ID) }

    private fun acknowledgement(
        inReplyToEventId: String,
        type: String,
        currentMessageId: String?,
        isPinned: Boolean,
        displayMode: DisplayMode,
        origin: DisplayCommandOrigin?,
        timetoken: Long,
        actorUserId: String = DISPLAY_ID,
        publisherUserId: String = DISPLAY_ID
    ): ReceivedRealtimeEvent {
        val payload = buildJsonObject {
            put(
                "displayState",
                Json.encodeToJsonElement(
                    DisplayStatePayload.serializer(),
                    DisplayStatePayload(
                        sessionId = SESSION_ID,
                        currentMessageId = currentMessageId,
                        isPinned = isPinned,
                        displayMode = displayMode.name,
                        commandOrigin = origin?.name
                    )
                )
            )
        }
        return ReceivedRealtimeEvent(
            channel = RealtimeChannels.displayEvents(SESSION_ID),
            timetoken = timetoken,
            publisherUserId = publisherUserId,
            event = RealtimeEvent(
                eventId = "ack-$inReplyToEventId-$timetoken",
                type = type,
                sessionId = SESSION_ID,
                actorUserId = actorUserId,
                occurredAt = timetoken,
                inReplyToEventId = inReplyToEventId,
                payload = payload
            )
        )
    }

    private suspend fun primeDisplayedMessage(
        messageId: String,
        pinned: Boolean = false
    ) {
        database.messageDao().hideDisplayedMessages(SESSION_ID)
        database.messageDao().markDisplayed(messageId)
        database.reliabilityDao().upsertDisplayState(
            DisplayStateEntity(
                sessionId = SESSION_ID,
                currentMessageId = messageId,
                isPinned = pinned,
                displayMode = DisplayMode.APPROVAL_REQUIRED,
                commandOrigin = DisplayCommandOrigin.MANUAL_SHOW,
                localOptimisticUpdatedAt = 5L
            )
        )
    }

    private suspend fun insertMessage(
        id: String,
        text: String,
        createdAt: Long
    ) {
        database.messageDao().upsertMessage(
            MessageEntity(
                id = id,
                sessionId = SESSION_ID,
                senderUserId = "participant1",
                target = MessageTarget.GROUP,
                text = text,
                createdAt = createdAt
            )
        )
    }

    private fun newSync(): DefaultSessionRealtimeSync =
        DefaultSessionRealtimeSync(
            transactionRunner = RoomTransactionRunner(database),
            sessionDao = database.sessionDao(),
            sessionJoinRequestDao = database.sessionJoinRequestDao(),
            messageDao = database.messageDao(),
            statusSignalDao = database.statusSignalDao(),
            reliabilityDao = database.reliabilityDao(),
            reliabilityStore = reliabilityStore
        )

    private data class DisplayCommandSnapshot(
        val state: DisplayStateEntity,
        val event: RealtimeEvent
    )

    private object NoOpActiveSessionStore : ActiveSessionStore {
        override fun observeActiveSessionId(userId: String): Flow<String?> = flowOf(null)

        override suspend fun setActiveSession(userId: String, sessionId: String) = Unit

        override suspend fun clearActiveSession(userId: String) = Unit
    }

    private companion object {
        const val SESSION_ID = "session1"
        const val DISPLAY_ID = "display-1"
    }
}
