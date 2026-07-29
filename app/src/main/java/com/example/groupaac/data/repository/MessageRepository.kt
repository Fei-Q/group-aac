package com.example.groupaac.data.repository

import com.example.groupaac.data.dao.MessageDao
import com.example.groupaac.data.dao.MessageWithSender
import com.example.groupaac.data.dao.MessageWithSenderAndAttachments
import com.example.groupaac.data.dao.ReliabilityDao
import com.example.groupaac.data.dao.SessionDao
import com.example.groupaac.data.dao.UserDao
import com.example.groupaac.data.entity.DisplayStateEntity
import com.example.groupaac.data.entity.MessageEntity
import com.example.groupaac.data.realtime.reliability.OutboxDispatching
import com.example.groupaac.data.realtime.reliability.RealtimeReliabilityStore
import com.example.groupaac.data.realtime.sync.NoOpSessionRealtimeSync
import com.example.groupaac.data.realtime.sync.SessionRealtimeSync
import com.example.groupaac.model.DisplayCommandOrigin
import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.MessageDisplayStatus
import com.example.groupaac.model.MessageStatus
import com.example.groupaac.model.MessageTransportStatus
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.util.IdUtils
import com.example.groupaac.util.TimeUtils
import kotlinx.coroutines.flow.Flow

class MessageRepository(
    private val transactionRunner: TransactionRunner,
    private val messageDao: MessageDao,
    private val sessionDao: SessionDao,
    private val userDao: UserDao,
    private val reliabilityDao: ReliabilityDao,
    private val reliabilityStore: RealtimeReliabilityStore,
    private val outboxDispatcher: OutboxDispatching,
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
        val messageId = transactionRunner.inTransaction {
            val draft = sourceDraftId?.let { messageDao.getMessage(it) }
            val resolvedMessageId = if (
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
                    status = MessageStatus.ACTIVE,
                    transportStatus = MessageTransportStatus.PENDING,
                    displayStatus = MessageDisplayStatus.HIDDEN
                )
                messageDao.upsertMessage(message)
                message.id
            }

            val storedMessage = messageDao.getMessage(resolvedMessageId)
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
            resolvedMessageId
        }
        outboxDispatcher.requestImmediateDispatch()
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
        transactionRunner.inTransaction {
            showOrRestoreMessage(
                sessionId = sessionId,
                messageId = messageId,
                restore = false,
                origin = DisplayCommandOrigin.MANUAL_SHOW
            )
        }
        outboxDispatcher.requestImmediateDispatch()
    }

    suspend fun restoreMessage(sessionId: String, messageId: String) {
        transactionRunner.inTransaction {
            showOrRestoreMessage(
                sessionId = sessionId,
                messageId = messageId,
                restore = true,
                origin = DisplayCommandOrigin.MANUAL_RESTORE
            )
        }
        outboxDispatcher.requestImmediateDispatch()
    }

    suspend fun pinDisplayedMessage(sessionId: String) {
        transactionRunner.inTransaction {
            val current = reliabilityDao.getDisplayState(sessionId)
            val messageId = current?.currentMessageId ?: return@inTransaction
            val session = sessionDao.getSession(sessionId) ?: return@inTransaction
            val eventId = IdUtils.newId()
            val now = TimeUtils.now()
            messageDao.hideDisplayedMessages(sessionId)
            messageDao.markDisplayed(messageId)
            reliabilityStore.upsertLocalDisplayState(
                sessionId = sessionId,
                eventId = eventId,
                currentMessageId = messageId,
                isPinned = true,
                displayMode = session.displayMode,
                commandOrigin = current.commandOrigin,
                now = now
            )
            val actorUserId = session.hostUserId ?: messageDao.getMessage(messageId)?.senderUserId ?: return@inTransaction
            sessionRealtimeSync.publishDisplayPinState(
                eventId = eventId,
                sessionId = sessionId,
                messageId = messageId,
                actorUserId = actorUserId,
                pinned = true,
                displayMode = session.displayMode,
                origin = current.commandOrigin
            )
        }
        outboxDispatcher.requestImmediateDispatch()
    }

    suspend fun unpinDisplayedMessage(sessionId: String) {
        transactionRunner.inTransaction {
            val current = reliabilityDao.getDisplayState(sessionId)
            val messageId = current?.currentMessageId ?: return@inTransaction
            val session = sessionDao.getSession(sessionId) ?: return@inTransaction
            val eventId = IdUtils.newId()
            val now = TimeUtils.now()
            messageDao.hideDisplayedMessages(sessionId)
            messageDao.markDisplayed(messageId)
            reliabilityStore.upsertLocalDisplayState(
                sessionId = sessionId,
                eventId = eventId,
                currentMessageId = messageId,
                isPinned = false,
                displayMode = session.displayMode,
                commandOrigin = current.commandOrigin,
                now = now
            )
            val actorUserId = session.hostUserId ?: messageDao.getMessage(messageId)?.senderUserId ?: return@inTransaction
            sessionRealtimeSync.publishDisplayPinState(
                eventId = eventId,
                sessionId = sessionId,
                messageId = messageId,
                actorUserId = actorUserId,
                pinned = false,
                displayMode = session.displayMode,
                origin = current.commandOrigin
            )
        }
        outboxDispatcher.requestImmediateDispatch()
    }

    suspend fun clearDisplay(sessionId: String) {
        transactionRunner.inTransaction {
            val current = reliabilityDao.getDisplayState(sessionId)
            val session = sessionDao.getSession(sessionId) ?: return@inTransaction
            val eventId = IdUtils.newId()
            val now = TimeUtils.now()
            messageDao.hideDisplayedMessages(sessionId)
            reliabilityStore.upsertLocalDisplayState(
                sessionId = sessionId,
                eventId = eventId,
                currentMessageId = null,
                isPinned = false,
                displayMode = session.displayMode,
                commandOrigin = null,
                now = now
            )
            val actorUserId = session.hostUserId ?: return@inTransaction
            sessionRealtimeSync.publishDisplayClear(
                eventId = eventId,
                sessionId = sessionId,
                actorUserId = actorUserId,
                displayMode = session.displayMode,
                origin = current?.commandOrigin
            )
        }
        outboxDispatcher.requestImmediateDispatch()
    }

    suspend fun deleteMessage(messageId: String) {
        transactionRunner.inTransaction {
            val message = messageDao.getMessage(messageId)
                ?: return@inTransaction
            messageDao.deleteMessage(messageId)
            sessionRealtimeSync.publishMessageDeleted(
                message = message,
                actorUserId = message.senderUserId
            )
        }
        outboxDispatcher.requestImmediateDispatch()
    }

    suspend fun deleteDraft(messageId: String) {
        messageDao.deleteAttachmentsForMessage(messageId)
        messageDao.deleteMessage(messageId)
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
        val eventId = IdUtils.newId()
        val now = TimeUtils.now()
        applyDisplayedMessageState(
            sessionId = message.sessionId,
            eventId = eventId,
            messageId = message.id,
            isPinned = false,
            displayMode = session.displayMode,
            origin = DisplayCommandOrigin.AUTO_LATEST,
            now = now
        )
        sessionRealtimeSync.publishDisplayShowMessage(
            eventId = eventId,
            session = session,
            message = message,
            senderName = senderName,
            actorUserId = message.senderUserId,
            restore = false,
            isPinned = false,
            origin = DisplayCommandOrigin.AUTO_LATEST
        )
    }

    private suspend fun showOrRestoreMessage(
        sessionId: String,
        messageId: String,
        restore: Boolean,
        origin: DisplayCommandOrigin
    ) {
        val session = sessionDao.getSession(sessionId) ?: return
        val message = messageDao.getMessage(messageId) ?: return
        val current = reliabilityDao.getDisplayState(sessionId)
        val senderName = sessionDao.getMember(sessionId, message.senderUserId)?.displayName
            ?: userDao.getUser(message.senderUserId)?.displayName
            ?: "Unknown"
        val eventId = IdUtils.newId()
        val now = TimeUtils.now()
        applyDisplayedMessageState(
            sessionId = sessionId,
            eventId = eventId,
            messageId = messageId,
            isPinned = current?.isPinned == true,
            displayMode = session.displayMode,
            origin = origin,
            now = now
        )
        sessionRealtimeSync.publishDisplayShowMessage(
            eventId = eventId,
            session = session,
            message = message,
            senderName = senderName,
            actorUserId = session.hostUserId ?: message.senderUserId,
            restore = restore,
            isPinned = current?.isPinned == true,
            origin = origin
        )
    }

    private suspend fun applyDisplayedMessageState(
        sessionId: String,
        eventId: String,
        messageId: String,
        isPinned: Boolean,
        displayMode: DisplayMode,
        origin: DisplayCommandOrigin,
        now: Long
    ) {
        messageDao.hideDisplayedMessages(sessionId)
        messageDao.updateDisplaySelection(
            messageId = messageId,
            displayStatus = MessageDisplayStatus.PENDING
        )
        reliabilityStore.upsertLocalDisplayState(
            sessionId = sessionId,
            eventId = eventId,
            currentMessageId = messageId,
            isPinned = isPinned,
            displayMode = displayMode,
            commandOrigin = origin,
            now = now
        )
    }
}
