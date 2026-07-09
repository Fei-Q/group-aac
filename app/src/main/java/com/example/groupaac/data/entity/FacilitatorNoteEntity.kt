package com.example.groupaac.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "facilitator_notes")
data class FacilitatorNoteEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val participantUserId: String?,
    val facilitatorUserId: String,
    val text: String,
    val createdAt: Long
)
