package com.example.groupaac.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.groupaac.model.MessageStatus
import com.example.groupaac.model.MessageTarget

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val senderUserId: String,
    val target: MessageTarget,
    val text: String? = null,
    val attachmentId: String? = null,
    val createdAt: Long,
    val status: MessageStatus = MessageStatus.SENT,
    val saved: Boolean = false,
    val displayedOnMonitor: Boolean = false
)
