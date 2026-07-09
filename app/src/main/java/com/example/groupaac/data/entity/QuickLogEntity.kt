package com.example.groupaac.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quick_logs")
data class QuickLogEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val participantUserId: String,
    val facilitatorUserId: String,
    val label: String,
    val createdAt: Long
)
