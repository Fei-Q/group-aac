package com.example.groupaac.data.repository

import com.example.groupaac.data.dao.SignalWithUser
import com.example.groupaac.data.dao.SessionDao
import com.example.groupaac.data.dao.StatusSignalDao
import com.example.groupaac.data.dao.UserDao
import com.example.groupaac.data.entity.SignalSnoozeEntity
import com.example.groupaac.data.entity.StatusSignalEntity
import com.example.groupaac.data.pi.PiClient
import com.example.groupaac.data.pi.PiSignalPayload
import com.example.groupaac.model.SignalState
import com.example.groupaac.model.SignalType
import com.example.groupaac.util.IdUtils
import com.example.groupaac.util.TimeUtils
import kotlinx.coroutines.flow.Flow

class SignalRepository(
    private val signalDao: StatusSignalDao,
    private val sessionDao: SessionDao,
    private val userDao: UserDao,
    private val piClient: PiClient
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

        // One current signal per participant per session.
        // Tapping a new signal clears the previous one.
        signalDao.clearCurrentSignalsAndSnoozesForUser(
            sessionId = sessionId,
            userId = userId,
            clearedAt = now
        )

        val id = IdUtils.newId()

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

        piClient.sendSignal(
            PiSignalPayload(
                id = id,
                sessionId = sessionId,
                userId = userId,
                displayName = displayName,
                type = type,
                createdAt = now
            )
        )

        return id
    }

    suspend fun clearCurrentSignal(
        sessionId: String,
        userId: String
    ) {
        signalDao.clearCurrentSignalsAndSnoozesForUser(
            sessionId = sessionId,
            userId = userId,
            clearedAt = TimeUtils.now()
        )
    }

    suspend fun clearSignal(signalId: String) {
        signalDao.clearSignal(
            signalId = signalId,
            clearedAt = TimeUtils.now()
        )
        signalDao.deleteSnoozesForSignal(signalId)
    }

    suspend fun snoozeSignal(
        signalId: String,
        facilitatorUserId: String
    ) {
        signalDao.upsertSnooze(
            SignalSnoozeEntity(
                signalId = signalId,
                facilitatorUserId = facilitatorUserId,
                createdAt = TimeUtils.now()
            )
        )
    }

    suspend fun unsnoozeSignal(
        signalId: String,
        facilitatorUserId: String
    ) {
        signalDao.deleteSnooze(signalId, facilitatorUserId)
    }
}
