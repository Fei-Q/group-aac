package com.example.groupaac.model

data class ParticipantOverview(
    val userId: String,
    val displayName: String,
    val activeSignal: SignalType?,
    val signalState: SignalState?,
    val messageCount: Int,
    val supportRequests: Int,
    val lastActivityLabel: String,
    val elapsedLabel: String,
    val isLowParticipation: Boolean = false
)

data class SessionSummaryUi(
    val participantCount: Int = 0,
    val sharedItemCount: Int = 0,
    val supportRequestCount: Int = 0,
    val savedItemCount: Int = 0,
    val participantRows: List<ParticipantOverview> = emptyList()
)
