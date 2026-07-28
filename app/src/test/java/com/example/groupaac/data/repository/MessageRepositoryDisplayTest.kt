package com.example.groupaac.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.groupaac.data.AppDatabase
import com.example.groupaac.data.entity.MessageEntity
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.pi.MockPiClient
import com.example.groupaac.data.realtime.reliability.RealtimeReliabilityStore
import com.example.groupaac.data.realtime.sync.NoOpSessionRealtimeSync
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

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        database.userDao().upsertUser(
            UserEntity(
                id = "host1",
                displayName = "Host",
                createdAt = 1L
            )
        )
        database.userDao().upsertUser(
            UserEntity(
                id = "participant1",
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

        repository = MessageRepository(
            messageDao = database.messageDao(),
            sessionDao = database.sessionDao(),
            userDao = database.userDao(),
            piClient = MockPiClient(),
            reliabilityDao = database.reliabilityDao(),
            reliabilityStore = RealtimeReliabilityStore(
                database = database,
                reliabilityDao = database.reliabilityDao()
            ),
            sessionRealtimeSync = NoOpSessionRealtimeSync
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
}
