package com.example.groupaac.data.entity

import androidx.room.Entity

@Entity(
    tableName = "signal_snoozes",
    primaryKeys = ["signalId", "facilitatorUserId"]
)
data class SignalSnoozeEntity(
    val signalId: String,
    val facilitatorUserId: String,
    val snoozedUntil: Long? = null,
    val createdAt: Long
)
