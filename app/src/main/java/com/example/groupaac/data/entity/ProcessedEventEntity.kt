package com.example.groupaac.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "processed_events")
data class ProcessedEventEntity(
    @PrimaryKey val eventId: String,
    val channel: String,
    val timetoken: Long,
    val processedAt: Long
)
