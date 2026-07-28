package com.example.groupaac.data.repository

import com.example.groupaac.data.dao.MessageDao
import com.example.groupaac.data.dao.MessageWithSender
import com.example.groupaac.data.dao.MessageWithSenderAndAttachments
import com.example.groupaac.data.dao.ReliabilityDao
import com.example.groupaac.data.dao.SessionDao
import com.example.groupaac.data.dao.UserDao
import com.example.groupaac.data.entity.DisplayStateEntity
import com.example.groupaac.data.entity.MessageEntity
import com.example.groupaac.data.pi.DisplayCommand
import com.example.groupaac.data.pi.PiClient
import com.example.groupaac.data.pi.PiMessagePayload
import com.example.groupaac.data.realtime.reliability.RealtimeReliabilityStore
import com.example.groupaac.data.realtime.sync.NoOpSessionRealtimeSync
import com.example.groupaac.data.realtime.sync.SessionRealtimeSync
import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.MessageStatus
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.util.IdUtils
import com.example.groupaac.util.TimeUtils
import kotlinx.coroutines.flow.Flow

class MessageRepository(
    private val messageDao: MessageDao,
    private val sessionDao: SessionDao,
    private val userDao: UserDao,
    private val piClient: PiClient,
    private val reliabilityDao: ReliabilityDao,
    private val reliabilityStore: RealtimeReliabilityStore,
    private val sessionRealtimeSync: SessionRealtimeSync = NoOpSessionRealtimeSync
) {
    fun observeMessages(sessionId: String): Flow<List<MessageWithSender>> =
        messageDao.observeMessages(sessionId)

    fun observeMessagesWithAttachments(
        sessionId: String
    ): Flow<List<MessageWithSenderAndAttachments>> =
        messageDao.observeMessagesWithAttachments(sessionId)

    fun observeDisplayedMessage(sessionId: String): Flow<MessageWithSender?> =
        messageDao.observeDisplayedMessage(sessionId)

    fun observeDisplayedMessageWithAttachments(
        sessionId: String
    ): Flow<MessageWithSenderAndAttachments?> =
        messageDao.observeDisplayedMessageWithAttachments(sessionId)

    fun observeDisplayState(sessionId: String): Flow<DisplayStateEntity?> =
        reliabilityDao.observeDisplayState(sessionId)

    suspend fun sendText(
        sessionId: String,
        senderUserId: String,
        target: MessageTarget,
        text: String,
        sourceDraftId: String? = null
    ): String {
        val cleanText = text.trim()
        val sentAt = TimeUtils.now()
        val draft = sourceDraftId?.let { messageDao.getMessage(it) }

        val messageId = if (
            draft != null &&
            draft.sessionId == sessionId &&
            draft.senderUserId == senderUserId &&
            draft.status == MessageStatus.DRAFT
        ) {
            messageDao.markDraftAsSent(
                messageId = draft.id,
                sessionId = sessionId,
                senderUserId = senderUserId,
                target = target,
                text = cleanText,
                sentAt = sentAt
            )
            draft.id
        } else {
            val message = MessageEntity(
                id = IdUtils.newId(),
                sessionId = sessionId,
                senderUserId = senderUserId,
                target = target,
                text = cleanText.ifBlank { null },
                createdAt = sentAt,
                status = MessageStatus.SENT
            )
            messageDao.upsertMessage(message)
            message.id
        }

        sendMessageToPi(
            id = messageId,
            sessionId = sessionId,
            senderUserId = senderUserId,
            target = target,
            text = cleanText,
            createdAt = sentAt
        )
        val storedMessage = messageDao.getMessage(messageId)
        if (storedMessage != null) {
            val senderName = sessionDao.getMember(sessionId, senderUserId)?.displayName
                ?: userDao.getUser(senderUserId)?.displayName
                ?: "Unknown"
            sessionRealtimeSync.publishMessageCreated(
                message = storedMessage,
                senderName = senderName,
                target = target
            )
            maybeAutoDisplayMessage(
                message = storedMessage,
                senderName = senderName
            )
        }

        return messageId
    }

    suspend fun saveDraft(
        sessionId: String,
        senderUserId: String,
        text: String,
        target: MessageTarget = MessageTarget.PRIVATE,
        existingDraftId: String? = null
    ): String {
        val cleanText = text.trim()
        val updatedAt = TimeUtils.now()
        val existingDraft = existingDraftId?.let { messageDao.getMessage(it) }

        if (
            existingDraft != null &&
            existingDraft.sessionId == sessionId &&
            existingDraft.senderUserId == senderUserId &&
            existingDraft.status == MessageStatus.DRAFT
        ) {
            messageDao.updateDraft(
                messageId = existingDraft.id,
                sessionId = sessionId,
                senderUserId = senderUserId,
                target = target,
                text = cleanText,
                updatedAt = updatedAt
            )
            return existingDraft.id
        }

        val draftId = IdUtils.newId()

        messageDao.upsertMessage(
            MessageEntity(
                id = draftId,
                sessionId = sessionId,
                senderUserId = senderUserId,
                target = target,
                text = cleanText.ifBlank { null },
                createdAt = updatedAt,
                status = MessageStatus.DRAFT
            )
        )

        return draftId
    }

    suspend fun saveMessage(messageId: String) =
        messageDao.saveMessage(messageId)

    suspend fun displayMessage(sessionId: String, messageId: String) {
        showOrRestoreMessage(
            sessionId = sessionId,
            messageId = messageId,
            restore = false
        )
    }

    suspend fun restoreMessage(sessionId: String, messageId: String) {
        showOrRestoreMessage(
            sessionId = sessionId,
            messageId = messageId,
            restore = true
        )
    }

    suspend fun pinDisplayedMessage(sessionId: String) {
        val current = reliabilityDao.getDisplayState(sessionId)
        val messageId = current?.currentMessageId ?: return
        messageDao.clearDisplayedMessages(sessionId)
        messageDao.markDisplayed(messageId)
        reliabilityStore.applyDisplayStateIfNewer(
            sessionId = sessionId,
            eventId = IdUtils.newId(),
            currentMessageId = messageId,
            isPinned = true,
            displayMode = current.displayMode,
            commandTimetoken = System.currentTimeMillis(),
            now = TimeUtils.now()
        )
        piClient.sendDisplayCommand(DisplayCommand.PinMessage(sessionId, messageId))
        val session = sessionDao.getSession(sessionId) ?: return
        val actorUserId = session.hostUserId ?: messageDao.getMessage(messageId)?.senderUserId ?: return
        sessionRealtimeSync.publishDisplayPinState(
            sessionId = sessionId,
            messageId = messageId,
            actorUserId = actorUserId,
            pinned = true
        )
    }

    suspend fun unpinDisplayedMessage(sessionId: String) {
        val current = reliabilityDao.getDisplayState(sessionId)
        val messageId = current?.currentMessageId ?: return
        messageDao.clearDisplayedMessages(sessionId)
        messageDao.markDisplayed(messageId)
        reliabilityStore.applyDisplayStateIfNewer(
            sessionId = sessionId,
            eventId = IdUtils.newId(),
            currentMessageId = messageId,
            isPinned = false,
            displayMode = current.displayMode,
            commandTimetoken = System.currentTimeMillis(),
            now = TimeUtils.now()
        )
        piClient.sendDisplayCommand(DisplayCommand.UnpinMessage(sessionId, messageId))
        val session = sessionDao.getSession(sessionId) ?: return
        val actorUserId = session.hostUserId ?: messageDao.getMessage(messageId)?.senderUserId ?: return
        sessionRealtimeSync.publishDisplayPinState(
            sessionId = sessionId,
            messageId = messageId,
            actorUserId = actorUserId,
            pinned = false
        )
    }

    suspend fun clearDisplay(sessionId: String) {
        val current = reliabilityDao.getDisplayState(sessionId)
        messageDao.clearDisplayedMessages(sessionId)
        reliabilityStore.applyDisplayStateIfNewer(
            sessionId = sessionId,
            eventId = IdUtils.newId(),
            currentMessageId = null,
            isPinned = false,
            displayMode = current?.displayMode ?: DisplayMode.AUTO_LATEST,
            commandTimetoken = System.currentTimeMillis(),
            now = TimeUtils.now()
        )
        piClient.sendDisplayCommand(DisplayCommand.Clear(sessionId))
        val session = sessionDao.getSession(sessionId) ?: return
        val actorUserId = session.hostUserId ?: return
        sessionRealtimeSync.publishDisplayClear(
            sessionId = sessionId,
            actorUserId = actorUserId
        )
    }

    suspend fun deleteMessage(messageId: String) =
        messageDao.deleteMessage(messageId)

    suspend fun deleteDraft(messageId: String) {
        messageDao.deleteAttachmentsForMessage(messageId)
        messageDao.deleteMessage(messageId)
    }

    private suspend fun sendMessageToPi(
        id: String,
        sessionId: String,
        senderUserId: String,
        target: MessageTarget,
        text: String,
        createdAt: Long
    ) {
        val senderName = sessionDao.getMember(sessionId, senderUserId)?.displayName
            ?: userDao.getUser(senderUserId)?.displayName
            ?: "Unknown"

        piClient.sendMessage(
            PiMessagePayload(
                id = id,
                sessionId = sessionId,
                senderUserId = senderUserId,
                senderName = senderName,
                text = text,
                attachmentId = null,
                target = target.name,
                createdAt = createdAt
            )
        )
    }

    private suspend fun maybeAutoDisplayMessage(
        message: MessageEntity,
        senderName: String
    ) {
        if (message.target != MessageTarget.GROUP) {
            return
        }
        val session = sessionDao.getSession(message.sessionId) ?: return
        if (session.displayMode != DisplayMode.AUTO_LATEST) {
            return
        }
        if (reliabilityDao.getDisplayState(message.sessionId)?.isPinned == true) {
            return
        }
        applyDisplayedMessageState(
            sessionId = message.sessionId,
            messageId = message.id,
            isPinned = false,
            displayMode = session.displayMode
        )
        piClient.sendDisplayCommand(
            DisplayCommand.ShowMessage(message.sessionId, message.id)
        )
        sessionRealtimeSync.publishDisplayShowMessage(
            session = session,
            message = message,
            senderName = senderName,
            actorUserId = message.senderUserId,
            restore = false
        )
    }

    private suspend fun showOrRestoreMessage(
        sessionId: String,
        messageId: String,
        restore: Boolean
    ) {
        val session = sessionDao.getSession(sessionId) ?: return
        val message = messageDao.getMessage(messageId) ?: return
        val senderName = sessionDao.getMember(sessionId, message.senderUserId)?.displayName
            ?: userDao.getUser(message.senderUserId)?.displayName
            ?: "Unknown"
        applyDisplayedMessageState(
            sessionId = sessionId,
            messageId = messageId,
            isPinned = false,
            displayMode = session.displayMode
        )
        piClient.sendDisplayCommand(
            if (restore) {
                DisplayCommand.RestoreMessage(sessionId, messageId)
            } else {
                DisplayCommand.ShowMessage(sessionId, messageId)
            }
        )
        sessionRealtimeSync.publishDisplayShowMessage(
            session = session,
            message = message,
            senderName = senderName,
            actorUserId = session.hostUserId ?: message.senderUserId,
            restore = restore
        )
    }

    private suspend fun applyDisplayedMessageState(
        sessionId: String,
        messageId: String,
        isPinned: Boolean,
        displayMode: DisplayMode
    ) {
        messageDao.clearDisplayedMessages(sessionId)
        messageDao.markDisplayed(messageId)
        reliabilityStore.applyDisplayStateIfNewer(
            sessionId = sessionId,
            eventId = IdUtils.newId(),
            currentMessageId = messageId,
            isPinned = isPinned,
            displayMode = displayMode,
            commandTimetoken = System.currentTimeMillis(),
            now = TimeUtils.now()
        )
    }
}
