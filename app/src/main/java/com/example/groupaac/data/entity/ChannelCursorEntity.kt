package com.example.groupaac.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channel_cursors")
data class ChannelCursorEntity(
    @PrimaryKey val channel: String,
    val lastProcessedTimetoken: Long,
    val updatedAt: Long
)
