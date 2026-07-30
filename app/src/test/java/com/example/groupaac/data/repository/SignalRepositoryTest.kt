package com.example.groupaac.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.groupaac.data.AppDatabase
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.realtime.reliability.NoOpOutboxDispatcher
import com.example.groupaac.data.repository.ImmediateTransactionRunner
import com.example.groupaac.data.realtime.sync.NoOpSessionRealtimeSync
import com.example.groupaac.model.SessionRole
import com.example.groupaac.model.SignalState
import com.example.groupaac.model.SignalType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
class SignalRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: SignalRepository
    private lateinit var sync: RecordingSignalRealtimeSync

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        sync = RecordingSignalRealtimeSync()
        repository = SignalRepository(
            transactionRunner = ImmediateTransactionRunner,
            signalDao = database.statusSignalDao(),
            sessionDao = database.sessionDao(),
            userDao = database.userDao(),
            outboxDispatcher = NoOpOutboxDispatcher,
            sessionRealtimeSync = sync
        )

        database.userDao().upsertUser(
            UserEntity(
                uid = "participant1",
                displayName = "Participant One",
                createdAt = 1L
            )
        )
        database.userDao().upsertUser(
            UserEntity(
                uid = "facilitator1",
                displayName = "Facilitator One",
                createdAt = 1L
            )
        )
        database.userDao().upsertUser(
            UserEntity(
                uid = "facilitator2",
                displayName = "Facilitator Two",
                createdAt = 1L
            )
        )
        database.sessionDao().upsertSession(
            SessionEntity(
                id = "session1",
                name = "Group",
                joinCode = "1111-2222",
                hostUserId = "facilitator1",
                createdAt = 1L
            )
        )
        database.sessionDao().upsertMember(
            SessionMemberEntity(
                sessionId = "session1",
                userId = "participant1",
                displayName = "Participant One",
                role = SessionRole.PARTICIPANT,
                joinedAt = 1L
            )
        )
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun snoozeIsFacilitatorSpecific() = runTest {
        val signalId = repository.sendSignal(
            sessionId = "session1",
            userId = "participant1",
            type = SignalType.HELP
        )

        repository.snoozeSignal(signalId, "facilitator1")

        val facilitatorOneSignals = repository.observeActiveSignals(
            sessionId = "session1",
            facilitatorUserId = "facilitator1"
        ).first()
        val facilitatorTwoSignals = repository.observeActiveSignals(
            sessionId = "session1",
            facilitatorUserId = "facilitator2"
        ).first()

        assertEquals(SignalState.SNOOZED, facilitatorOneSignals.single().state)
        assertEquals(SignalState.CURRENT, facilitatorTwoSignals.single().state)
    }

    @Test
    fun clearingSignalRemovesSnoozes() = runTest {
        val signalId = repository.sendSignal(
            sessionId = "session1",
            userId = "participant1",
            type = SignalType.HELP
        )

        repository.snoozeSignal(signalId, "facilitator1")
        repository.clearSignal(signalId)

        val facilitatorOneSignals = repository.observeActiveSignals(
            sessionId = "session1",
            facilitatorUserId = "facilitator1"
        ).first()

        assertTrue(facilitatorOneSignals.isEmpty())
    }

    @Test
    fun facilitatorSnoozeDoesNotClearSignalOrPublishCleared() = runTest {
        val signalId = repository.sendSignal(
            sessionId = "session1",
            userId = "participant1",
            type = SignalType.HELP
        )

        repository.snoozeSignal(signalId, "facilitator1")

        val active = repository.observeActiveSignals(
            sessionId = "session1",
            facilitatorUserId = "facilitator1"
        ).first().single()

        assertEquals(SignalState.SNOOZED, active.state)
        assertEquals(1, sync.snoozedSignals.size)
        assertTrue(sync.clearedSignals.isEmpty())
        assertNotNull(database.statusSignalDao().getSignal(signalId))
    }

    @Test
    fun participantSelfClearStillClearsAndPublishesNormally() = runTest {
        val signalId = repository.sendSignal(
            sessionId = "session1",
            userId = "participant1",
            type = SignalType.REPEAT
        )

        repository.clearCurrentSignal(
            sessionId = "session1",
            userId = "participant1"
        )

        val current = repository.observeCurrentSignal(
            sessionId = "session1",
            userId = "participant1"
        ).first()

        assertEquals(null, current)
        assertEquals(1, sync.clearedSignals.size)
        assertEquals("participant1", sync.clearedSignals.single().first)
        assertEquals(signalId, sync.clearedSignals.single().second.id)
        assertFalse(sync.snoozedSignals.any { it.id == signalId })
    }
}

private class RecordingSignalRealtimeSync :
    com.example.groupaac.data.realtime.sync.SessionRealtimeSync by NoOpSessionRealtimeSync {

    val snoozedSignals = mutableListOf<com.example.groupaac.data.entity.StatusSignalEntity>()
    val clearedSignals =
        mutableListOf<Pair<String, com.example.groupaac.data.entity.StatusSignalEntity>>()

    override suspend fun publishSignalSnoozed(
        signal: com.example.groupaac.data.entity.StatusSignalEntity,
        facilitatorUserId: String
    ) {
        snoozedSignals += signal
    }

    override suspend fun publishSignalCleared(
        signal: com.example.groupaac.data.entity.StatusSignalEntity,
        actorUserId: String
    ) {
        clearedSignals += actorUserId to signal
    }
}
