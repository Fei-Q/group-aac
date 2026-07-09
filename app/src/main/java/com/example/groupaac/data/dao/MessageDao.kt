package com.example.groupaac.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.example.groupaac.data.entity.AttachmentEntity
import com.example.groupaac.data.entity.MessageEntity
import com.example.groupaac.model.MessageStatus
import com.example.groupaac.model.MessageTarget
import kotlinx.coroutines.flow.Flow

data class MessageWithSenderAndAttachments(
    @Embedded val message: MessageWithSender,

    @Relation(
        parentColumn = "id",
        entityColumn = "messageId"
    )
    val attachments: List<AttachmentEntity>
)

data class MessageWithSender(
    val id: String,
    val sessionId: String,
    val senderUserId: String,
    val senderName: String,
    val target: MessageTarget,
    val text: String?,
    val attachmentId: String?,
    val createdAt: Long,
    val status: MessageStatus,
    val saved: Boolean,
    val displayedOnMonitor: Boolean
)

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttachment(attachment: AttachmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttachments(attachments: List<AttachmentEntity>)

    @Transaction
    suspend fun upsertMessageWithAttachments(
        message: MessageEntity,
        attachments: List<AttachmentEntity>
    ) {
        upsertMessage(message)

        if (attachments.isNotEmpty()) {
            upsertAttachments(attachments)
        }
    }

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessage(messageId: String): MessageEntity?

    @Query("""
        SELECT messages.*, COALESCE(users.displayName, session_members.displayName, 'Unknown') AS senderName
        FROM messages
        LEFT JOIN users ON users.id = messages.senderUserId
        LEFT JOIN session_members ON session_members.sessionId = messages.sessionId AND session_members.userId = messages.senderUserId
        WHERE messages.sessionId = :sessionId AND messages.status != 'DELETED'
        ORDER BY messages.createdAt DESC
    """)
    fun observeMessages(sessionId: String): Flow<List<MessageWithSender>>

    @Transaction
    @Query("""
        SELECT messages.*, COALESCE(users.displayName, session_members.displayName, 'Unknown') AS senderName
        FROM messages
        LEFT JOIN users ON users.id = messages.senderUserId
        LEFT JOIN session_members ON session_members.sessionId = messages.sessionId AND session_members.userId = messages.senderUserId
        WHERE messages.sessionId = :sessionId AND messages.status != 'DELETED'
        ORDER BY messages.createdAt DESC
    """)
    fun observeMessagesWithAttachments(
        sessionId: String
    ): Flow<List<MessageWithSenderAndAttachments>>

    @Query("""
        SELECT messages.*, COALESCE(users.displayName, session_members.displayName, 'Unknown') AS senderName
        FROM messages
        LEFT JOIN users ON users.id = messages.senderUserId
        LEFT JOIN session_members ON session_members.sessionId = messages.sessionId AND session_members.userId = messages.senderUserId
        WHERE messages.sessionId = :sessionId AND messages.displayedOnMonitor = 1 AND messages.status != 'DELETED'
        ORDER BY messages.createdAt DESC LIMIT 1
    """)
    fun observeDisplayedMessage(sessionId: String): Flow<MessageWithSender?>

    @Transaction
    @Query("""
        SELECT messages.*, COALESCE(users.displayName, session_members.displayName, 'Unknown') AS senderName
        FROM messages
        LEFT JOIN users ON users.id = messages.senderUserId
        LEFT JOIN session_members ON session_members.sessionId = messages.sessionId AND session_members.userId = messages.senderUserId
        WHERE messages.sessionId = :sessionId AND messages.displayedOnMonitor = 1 AND messages.status != 'DELETED'
        ORDER BY messages.createdAt DESC LIMIT 1
    """)
    fun observeDisplayedMessageWithAttachments(
        sessionId: String
    ): Flow<MessageWithSenderAndAttachments?>

    @Query("DELETE FROM attachments WHERE messageId = :messageId")
    suspend fun deleteAttachmentsForMessage(messageId: String)

    @Query("""
        UPDATE messages
        SET text = :text,
            target = :target,
            createdAt = :updatedAt,
            status = 'DRAFT',
            saved = 0,
            displayedOnMonitor = 0
        WHERE id = :messageId
          AND sessionId = :sessionId
          AND senderUserId = :senderUserId
          AND status = 'DRAFT'
    """)
    suspend fun updateDraft(
        messageId: String,
        sessionId: String,
        senderUserId: String,
        target: MessageTarget,
        text: String,
        updatedAt: Long
    )

    @Query("""
        UPDATE messages
        SET target = :target,
            text = :text,
            createdAt = :sentAt,
            status = 'SENT',
            saved = 0,
            displayedOnMonitor = 0
        WHERE id = :messageId
          AND sessionId = :sessionId
          AND senderUserId = :senderUserId
          AND status = 'DRAFT'
    """)
    suspend fun markDraftAsSent(
        messageId: String,
        sessionId: String,
        senderUserId: String,
        target: MessageTarget,
        text: String,
        sentAt: Long
    )

    @Query("UPDATE messages SET saved = 1, status = 'SAVED' WHERE id = :messageId")
    suspend fun saveMessage(messageId: String)

    @Query("UPDATE messages SET displayedOnMonitor = 0 WHERE sessionId = :sessionId")
    suspend fun clearDisplayedMessages(sessionId: String)

    @Query("UPDATE messages SET displayedOnMonitor = 1, status = 'DISPLAYED' WHERE id = :messageId")
    suspend fun markDisplayed(messageId: String)

    @Query("UPDATE messages SET status = 'DELETED' WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)
}
