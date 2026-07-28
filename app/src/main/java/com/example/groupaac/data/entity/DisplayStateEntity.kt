package com.example.groupaac.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.groupaac.model.DisplayMode

@Entity(tableName = "display_state")
data class DisplayStateEntity(
    @PrimaryKey val sessionId: String,
    val currentMessageId: String? = null,
    val isPinned: Boolean = false,
    val displayMode: DisplayMode = DisplayMode.AUTO_LATEST,
    val lastAppliedCommandTimetoken: Long? = null,
    val lastAppliedCommandEventId: String? = null,
    val updatedAt: Long
)
