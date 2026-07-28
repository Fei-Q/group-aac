package com.example.groupaac.util

import com.example.groupaac.model.ParticipantOverview
import com.example.groupaac.model.SignalState
import com.example.groupaac.model.SignalType

object PreviewData {
    val participants = listOf(
        ParticipantOverview("u1", "Alice", SignalType.HOLD_MY_TURN, SignalState.CURRENT, 4, 0, "Sent a message", "2 min"),
        ParticipantOverview("u2", "Bob", null, null, 2, 0, "Shared photo", "8 min", isLowParticipation = true),
        ParticipantOverview("u3", "Eve", SignalType.HELP, SignalState.CURRENT, 1, 2, "Asked for help", "1 min"),
        ParticipantOverview("u4", "Mary", SignalType.COMMENT, SignalState.SNOOZED, 3, 1, "Comment ready", "4 min")
    )
}
