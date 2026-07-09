package com.example.groupaac.data.repository

import com.example.groupaac.data.dao.SignalWithUser
import com.example.groupaac.data.dao.SessionDao
import com.example.groupaac.data.dao.StatusSignalDao
import com.example.groupaac.data.dao.UserDao
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
    fun observeActiveSignals(sessionId: String): Flow<List<SignalWithUser>> {
        return signalDao.observeActiveSignals(sessionId)
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
        signalDao.clearCurrentSignalsForUser(
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
        signalDao.clearCurrentSignalsForUser(
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
    }

    suspend fun resolveSignal(signalId: String) {
        signalDao.resolveSignal(
            signalId = signalId,
            resolvedAt = TimeUtils.now()
        )
    }

    suspend fun snoozeSignal(signalId: String) {
        signalDao.snoozeSignal(signalId)
    }

    suspend fun unsnoozeSignal(signalId: String) {
        signalDao.unsnoozeSignal(signalId)
    }

    suspend fun resolveSignalsForUser(
        sessionId: String,
        userId: String
    ) {
        signalDao.resolveSignalsForUser(
            sessionId = sessionId,
            userId = userId,
            resolvedAt = TimeUtils.now()
        )
    }
}
