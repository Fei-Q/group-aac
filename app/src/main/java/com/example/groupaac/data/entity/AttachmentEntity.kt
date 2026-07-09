package com.example.groupaac.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["messageId"])
    ]
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val localUri: String,
    val mimeType: String,
    val originalName: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),

    // Future-compatible fields for backend/session sync.
    val remoteUri: String? = null,
    val syncStatus: String = "LOCAL_ONLY"
)