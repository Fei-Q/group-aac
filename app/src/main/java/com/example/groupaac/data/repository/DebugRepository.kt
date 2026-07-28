package com.example.groupaac.data.repository

import com.example.groupaac.data.dao.MessageDao
import com.example.groupaac.data.dao.SessionDao
import com.example.groupaac.data.dao.StatusSignalDao
import com.example.groupaac.data.dao.UserDao
import com.example.groupaac.data.entity.MessageEntity
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.data.entity.StatusSignalEntity
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.entity.UserSettingsEntity
import com.example.groupaac.model.MessageStatus
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.model.SessionRole
import com.example.groupaac.model.SignalState
import com.example.groupaac.model.SignalType
import com.example.groupaac.util.TimeUtils
import kotlinx.coroutines.flow.Flow

/**
 * Temporary local debug harness. It seeds the session supplied by the caller and
 * never changes the real active-session preference.
 */
class DebugRepository(
    private val sessionDao: SessionDao,
    private val userDao: UserDao,
    private val signalDao: StatusSignalDao,
    private val messageDao: MessageDao
) {
    fun observeSession(
        sessionId: String
    ): Flow<SessionEntity?> = sessionDao.observeSession(sessionId)

    suspend fun ensureDemoSession(
        sessionId: String = DEBUG_SESSION_ID
    ): String {
        val existing = sessionDao.getSession(sessionId)
        if (existing != null) {
            return existing.id
        }

        val now = TimeUtils.now()
        sessionDao.upsertSession(
            SessionEntity(
                id = sessionId,
                name = DEBUG_SESSION_NAME,
                joinCode = DEBUG_SESSION_CODE,
                createdAt = now,
                actualStartedAt = now
            )
        )

        return sessionId
    }

    suspend fun addDebugParticipantAlice(sessionId: String) {
        ensureDebugParticipant(
            sessionId,
            DEBUG_ALICE_ID,
            DEBUG_ALICE_NAME
        )
    }

    suspend fun addDebugParticipantBob(sessionId: String) {
        ensureDebugParticipant(
            sessionId,
            DEBUG_BOB_ID,
            DEBUG_BOB_NAME
        )
    }

    suspend fun createDebugSignal(
        sessionId: String,
        userId: String,
        type: SignalType
    ) {
        val displayName = debugDisplayNameFor(userId)
        ensureDebugParticipant(sessionId, userId, displayName)

        val now = TimeUtils.now()
        signalDao.clearCurrentSignalsForUser(
            sessionId = sessionId,
            userId = userId,
            clearedAt = now
        )

        signalDao.upsertSignal(
            StatusSignalEntity(
                id = debugSignalIdFor(userId, type),
                sessionId = sessionId,
                userId = userId,
                type = type,
                state = SignalState.CURRENT,
                createdAt = now
            )
        )
    }

    suspend fun clearDebugSignals(sessionId: String) {
        signalDao.clearCurrentSignalsForSession(
            sessionId = sessionId,
            clearedAt = TimeUtils.now()
        )
    }

    suspend fun seedDebugMessages(sessionId: String) {
        ensureDebugParticipant(
            sessionId,
            DEBUG_ALICE_ID,
            DEBUG_ALICE_NAME
        )
        ensureDebugParticipant(
            sessionId,
            DEBUG_BOB_ID,
            DEBUG_BOB_NAME
        )

        val now = TimeUtils.now()
        val messages = listOf(
            MessageEntity(
                id = DEBUG_MESSAGE_ONE_ID,
                sessionId = sessionId,
                senderUserId = DEBUG_ALICE_ID,
                target = MessageTarget.GROUP,
                text = "Can you help me with this?",
                createdAt = now - 3_000,
                status = MessageStatus.SENT
            ),
            MessageEntity(
                id = DEBUG_MESSAGE_TWO_ID,
                sessionId = sessionId,
                senderUserId = DEBUG_BOB_ID,
                target = MessageTarget.FACILITATOR,
                text = "Please wait a moment.",
                createdAt = now - 2_000,
                status = MessageStatus.SENT
            ),
            MessageEntity(
                id = DEBUG_MESSAGE_THREE_ID,
                sessionId = sessionId,
                senderUserId = DEBUG_ALICE_ID,
                target = MessageTarget.GROUP,
                text = "I am ready to share again.",
                createdAt = now - 1_000,
                status = MessageStatus.DISPLAYED,
                displayedOnMonitor = true
            )
        )

        for (message in messages) {
            messageDao.upsertMessage(message)
        }
    }

    private suspend fun ensureDebugParticipant(
        sessionId: String,
        userId: String,
        displayName: String
    ) {
        ensureDemoSession(sessionId)

        val now = TimeUtils.now()
        val existingUser = userDao.getUser(userId)
        val existingMember = sessionDao.getMember(sessionId, userId)

        userDao.upsertUser(
            UserEntity(
                id = userId,
                displayName = displayName,
                createdAt = existingUser?.createdAt ?: now
            )
        )

        userDao.upsertSettings(UserSettingsEntity(userId = userId))

        sessionDao.upsertMember(
            SessionMemberEntity(
                sessionId = sessionId,
                userId = userId,
                displayName = displayName,
                role = SessionRole.PARTICIPANT,
                joinedAt = existingMember?.joinedAt ?: now
            )
        )
    }

    private fun debugDisplayNameFor(userId: String): String {
        return when (userId) {
            DEBUG_ALICE_ID -> DEBUG_ALICE_NAME
            DEBUG_BOB_ID -> DEBUG_BOB_NAME
            else -> userId
        }
    }

    private fun debugSignalIdFor(
        userId: String,
        type: SignalType
    ): String {
        return when (userId) {
            DEBUG_ALICE_ID -> when (type) {
                SignalType.HELP -> DEBUG_ALICE_HELP_SIGNAL_ID
                else -> "debug-alice-${type.name.lowercase()}"
            }

            DEBUG_BOB_ID -> when (type) {
                SignalType.WAIT -> DEBUG_BOB_WAIT_SIGNAL_ID
                else -> "debug-bob-${type.name.lowercase()}"
            }

            else -> "debug-$userId-${type.name.lowercase()}"
        }
    }

    companion object {
        const val DEBUG_SESSION_ID = "debug-session"
        private const val DEBUG_SESSION_NAME = "Debug Session"
        private const val DEBUG_SESSION_CODE = "9999-0001"

        const val DEBUG_ALICE_ID = "debug-alice"
        const val DEBUG_BOB_ID = "debug-bob"
        private const val DEBUG_ALICE_NAME = "Alice"
        private const val DEBUG_BOB_NAME = "Bob"

        private const val DEBUG_ALICE_HELP_SIGNAL_ID =
            "debug-alice-help"
        private const val DEBUG_BOB_WAIT_SIGNAL_ID =
            "debug-bob-wait"

        private const val DEBUG_MESSAGE_ONE_ID =
            "debug-message-1"
        private const val DEBUG_MESSAGE_TWO_ID =
            "debug-message-2"
        private const val DEBUG_MESSAGE_THREE_ID =
            "debug-message-3"
    }
}
