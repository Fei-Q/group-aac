package com.example.groupaac.data.repository

import com.example.groupaac.data.dao.SignalWithUser
import com.example.groupaac.data.dao.SessionDao
import com.example.groupaac.data.dao.StatusSignalDao
import com.example.groupaac.data.dao.UserDao
import com.example.groupaac.data.entity.SignalSnoozeEntity
import com.example.groupaac.data.entity.StatusSignalEntity
import com.example.groupaac.data.realtime.reliability.OutboxDispatching
import com.example.groupaac.data.realtime.sync.NoOpSessionRealtimeSync
import com.example.groupaac.data.realtime.sync.SessionRealtimeSync
import com.example.groupaac.model.SignalState
import com.example.groupaac.model.SignalType
import com.example.groupaac.util.IdUtils
import com.example.groupaac.util.TimeUtils
import kotlinx.coroutines.flow.Flow

class SignalRepository(
    private val transactionRunner: TransactionRunner,
    private val signalDao: StatusSignalDao,
    private val sessionDao: SessionDao,
    private val userDao: UserDao,
    private val outboxDispatcher: OutboxDispatching,
    private val sessionRealtimeSync: SessionRealtimeSync = NoOpSessionRealtimeSync
) {
    fun observeActiveSignals(
        sessionId: String,
        facilitatorUserId: String?
    ): Flow<List<SignalWithUser>> {
        return signalDao.observeActiveSignals(
            sessionId = sessionId,
            facilitatorUserId = facilitatorUserId
        )
    }

    fun observeCurrentSignal(
        sessionId: String,
        userId: String
    ): Flow<StatusSignalEntity?> {
        return signalDao.observeCurrentSignal(
            sessionId = sessionId,
            userId = userId
        )
    }

    suspend fun sendSignal(
        sessionId: String,
        userId: String,
        type: SignalType
    ): String {
        val now = TimeUtils.now()
        val id = IdUtils.newId()
        transactionRunner.inTransaction {
            signalDao.clearCurrentSignalsAndSnoozesForUser(
                sessionId = sessionId,
                userId = userId,
                clearedAt = now
            )

            val signal = StatusSignalEntity(
                id = id,
                sessionId = sessionId,
                userId = userId,
                type = type,
                state = SignalState.CURRENT,
                createdAt = now
            )

            signalDao.upsertSignal(signal)

            val displayName = sessionDao.getMember(sessionId, userId)?.displayName
                ?: userDao.getUser(userId)?.displayName
                ?: "Unknown"
            sessionRealtimeSync.publishSignalCreated(signal, displayName)
        }
        outboxDispatcher.requestImmediateDispatch()
        return id
    }

    suspend fun clearCurrentSignal(
        sessionId: String,
        userId: String
    ) {
        val now = TimeUtils.now()
        transactionRunner.inTransaction {
            val current = signalDao.getCurrentSignal(
                sessionId = sessionId,
                userId = userId
            ) ?: return@inTransaction
            signalDao.clearCurrentSignalsAndSnoozesForUser(
                sessionId = sessionId,
                userId = userId,
                clearedAt = now
            )
            sessionRealtimeSync.publishSignalCleared(
                signal = current.copy(
                    state = SignalState.CLEARED,
                    clearedAt = now
                ),
                actorUserId = userId
            )
        }
        outboxDispatcher.requestImmediateDispatch()
    }

    suspend fun clearSignal(signalId: String) {
        val now = TimeUtils.now()
        transactionRunner.inTransaction {
            val signal = signalDao.getSignal(signalId) ?: return@inTransaction
            signalDao.clearSignal(
                signalId = signalId,
                clearedAt = now
            )
            signalDao.deleteSnoozesForSignal(signalId)
            sessionRealtimeSync.publishSignalCleared(
                signal = signal.copy(
                    state = SignalState.CLEARED,
                    clearedAt = now
                ),
                actorUserId = signal.userId
            )
        }
        outboxDispatcher.requestImmediateDispatch()
    }

    suspend fun snoozeSignal(
        signalId: String,
        facilitatorUserId: String
    ) {
        transactionRunner.inTransaction {
            val signal = signalDao.getSignal(signalId) ?: return@inTransaction
            signalDao.upsertSnooze(
                SignalSnoozeEntity(
                    signalId = signalId,
                    facilitatorUserId = facilitatorUserId,
                    createdAt = TimeUtils.now()
                )
            )
            sessionRealtimeSync.publishSignalSnoozed(
                signal = signal,
                facilitatorUserId = facilitatorUserId
            )
        }
        outboxDispatcher.requestImmediateDispatch()
    }

    suspend fun unsnoozeSignal(
        signalId: String,
        facilitatorUserId: String
    ) {
        signalDao.deleteSnooze(signalId, facilitatorUserId)
    }
}
