package com.example.groupaac.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.groupaac.data.AppDatabase
import com.example.groupaac.data.entity.MessageEntity
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.realtime.reliability.NoOpOutboxDispatcher
import com.example.groupaac.data.realtime.reliability.RealtimeReliabilityStore
import com.example.groupaac.data.realtime.sync.NoOpSessionRealtimeSync
import com.example.groupaac.data.realtime.sync.SessionRealtimeSync
import com.example.groupaac.data.repository.ImmediateTransactionRunner
import com.example.groupaac.model.DisplayCommandOrigin
import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.model.SessionRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MessageRepositoryDisplayTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: MessageRepository
    private lateinit var sync: RecordingDisplayRealtimeSync

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

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
                createdAt = 1L
            )
        )
        database.sessionDao().upsertSession(
            SessionEntity(
                id = "session1",
                name = "Group",
                joinCode = "1234-5678",
                hostUserId = "host1",
                displayMode = DisplayMode.AUTO_LATEST,
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

        sync = RecordingDisplayRealtimeSync()
        repository = MessageRepository(
            transactionRunner = ImmediateTransactionRunner,
            messageDao = database.messageDao(),
            sessionDao = database.sessionDao(),
            userDao = database.userDao(),
            reliabilityDao = database.reliabilityDao(),
            reliabilityStore = RealtimeReliabilityStore(
                database = database,
                reliabilityDao = database.reliabilityDao()
            ),
            outboxDispatcher = NoOpOutboxDispatcher,
            sessionRealtimeSync = sync
        )
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun autoLatestDisplaysEligibleGroupMessagesOnly() = runTest {
        val groupMessageId = repository.sendText(
            sessionId = "session1",
            senderUserId = "participant1",
            target = MessageTarget.GROUP,
            text = "Hello group"
        )
        val facilitatorMessageId = repository.sendText(
            sessionId = "session1",
            senderUserId = "participant1",
            target = MessageTarget.FACILITATOR,
            text = "Private help"
        )

        val displayState = repository.observeDisplayState("session1").first()
        val displayedMessage = repository.observeDisplayedMessage("session1").first()

        assertEquals(groupMessageId, displayState?.currentMessageId)
        assertEquals(groupMessageId, displayedMessage?.id)
        assertFalse(facilitatorMessageId == displayState?.currentMessageId)
    }

    @Test
    fun clearDisplayAlsoClearsPinnedState() = runTest {
        val messageId = repository.sendText(
            sessionId = "session1",
            senderUserId = "participant1",
            target = MessageTarget.GROUP,
            text = "Show me"
        )

        repository.pinDisplayedMessage("session1")
        repository.clearDisplay("session1")

        val displayState = repository.observeDisplayState("session1").first()
        val displayedMessage = repository.observeDisplayedMessage("session1").first()

        assertNull(displayedMessage)
        assertEquals(null, displayState?.currentMessageId)
        assertEquals(false, displayState?.isPinned)
        assertTrue(database.messageDao().getMessage(messageId) != null)
    }

    @Test
    fun pinnedAutomaticReplacementIsRejected() = runTest {
        val first = repository.sendText(
            sessionId = "session1",
            senderUserId = "participant1",
            target = MessageTarget.GROUP,
            text = "First"
        )
        repository.pinDisplayedMessage("session1")

        repository.sendText(
            sessionId = "session1",
            senderUserId = "participant1",
            target = MessageTarget.GROUP,
            text = "Second"
        )

        val displayState = repository.observeDisplayState("session1").first()
        assertEquals(first, displayState?.currentMessageId)
        assertEquals(true, displayState?.isPinned)
    }

    @Test
    fun pinnedManualReplacementPreservesPinAndOrigin() = runTest {
        repository.sendText(
            sessionId = "session1",
            senderUserId = "participant1",
            target = MessageTarget.GROUP,
            text = "First"
        )
        val second = repository.sendText(
            sessionId = "session1",
            senderUserId = "participant1",
            target = MessageTarget.GROUP,
            text = "Second"
        )
        repository.pinDisplayedMessage("session1")

        repository.displayMessage("session1", second)

        val displayState = repository.observeDisplayState("session1").first()
        assertEquals(second, displayState?.currentMessageId)
        assertEquals(true, displayState?.isPinned)
        assertEquals(DisplayCommandOrigin.MANUAL_SHOW, displayState?.commandOrigin)
    }

    @Test
    fun unpinKeepsCurrentContentVisible() = runTest {
        val messageId = repository.sendText(
            sessionId = "session1",
            senderUserId = "participant1",
            target = MessageTarget.GROUP,
            text = "Visible"
        )
        repository.pinDisplayedMessage("session1")

        repository.unpinDisplayedMessage("session1")

        val displayState = repository.observeDisplayState("session1").first()
        val displayedMessage = repository.observeDisplayedMessage("session1").first()
        assertEquals(messageId, displayState?.currentMessageId)
        assertEquals(false, displayState?.isPinned)
        assertEquals(messageId, displayedMessage?.id)
    }

    @Test
    fun publishedDisplayCommandsCarryCurrentDisplayMode() = runTest {
        database.sessionDao().upsertSession(
            SessionEntity(
                id = "session1",
                name = "Group",
                joinCode = "1234-5678",
                hostUserId = "host1",
                displayMode = DisplayMode.APPROVAL_REQUIRED,
                createdAt = 1L
            )
        )
        val messageId = repository.sendText(
            sessionId = "session1",
            senderUserId = "participant1",
            target = MessageTarget.GROUP,
            text = "Mode test"
        )
        repository.displayMessage("session1", messageId)
        repository.pinDisplayedMessage("session1")
        repository.unpinDisplayedMessage("session1")
        repository.clearDisplay("session1")

        assertTrue(sync.showCalls.all { it.displayMode == DisplayMode.APPROVAL_REQUIRED })
        assertTrue(sync.pinCalls.all { it.displayMode == DisplayMode.APPROVAL_REQUIRED })
        assertEquals(DisplayMode.APPROVAL_REQUIRED, sync.clearMode)
    }
}

private class RecordingDisplayRealtimeSync : SessionRealtimeSync by NoOpSessionRealtimeSync {
    data class ShowCall(
        val displayMode: DisplayMode,
        val isPinned: Boolean,
        val origin: DisplayCommandOrigin
    )

    data class PinCall(
        val displayMode: DisplayMode,
        val pinned: Boolean,
        val origin: DisplayCommandOrigin?
    )

    val showCalls = mutableListOf<ShowCall>()
    val pinCalls = mutableListOf<PinCall>()
    var clearMode: DisplayMode? = null

    override suspend fun publishDisplayShowMessage(
        session: SessionEntity,
        message: MessageEntity,
        senderName: String,
        actorUserId: String,
        restore: Boolean,
        isPinned: Boolean,
        origin: DisplayCommandOrigin
    ) {
        showCalls += ShowCall(
            displayMode = session.displayMode,
            isPinned = isPinned,
            origin = origin
        )
    }

    override suspend fun publishDisplayPinState(
        sessionId: String,
        messageId: String,
        actorUserId: String,
        pinned: Boolean,
        displayMode: DisplayMode,
        origin: DisplayCommandOrigin?
    ) {
        pinCalls += PinCall(displayMode, pinned, origin)
    }

    override suspend fun publishDisplayClear(
        sessionId: String,
        actorUserId: String,
        displayMode: DisplayMode,
        origin: DisplayCommandOrigin?
    ) {
        clearMode = displayMode
    }
}
