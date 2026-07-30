package com.example.groupaac.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.groupaac.model.SignalState
import com.example.groupaac.model.SignalType

@Entity(tableName = "status_signals")
data class StatusSignalEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val userId: String,
    val type: SignalType,
    val state: SignalState = SignalState.CURRENT,
    val createdAt: Long,
    val clearedAt: Long? = null
)
