package com.example.groupaac.ui.facilitator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.groupaac.model.ParticipantOverview
import com.example.groupaac.model.SignalState
import com.example.groupaac.model.SignalType
import com.example.groupaac.ui.common.SignalBadge
import com.example.groupaac.ui.common.UserAvatar
import com.example.groupaac.ui.theme.AacBlue
import com.example.groupaac.ui.theme.AacBorder
import com.example.groupaac.ui.theme.AacRed
import com.example.groupaac.ui.theme.AacTextSecondary
import com.example.groupaac.ui.theme.AacYellow
import com.example.groupaac.ui.theme.GroupAacTheme

@Composable
fun ParticipantCard(
    row: ParticipantOverview,
    selected: Boolean,
    onClick: () -> Unit,
    onSnooze: () -> Unit
) {
    val borderColor = when {
        selected -> AacBlue
        row.activeSignal == SignalType.HELP || row.activeSignal == SignalType.REPEAT -> AacRed
        row.activeSignal == SignalType.HOLD_MY_TURN -> AacYellow
        row.isLowParticipation -> Color(0xFF7A5AF8)
        else -> AacBorder
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (selected) 3.dp else 2.dp, if (row.signalState == SignalState.SNOOZED) AacBorder else borderColor),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(row.displayName)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.displayName, style = MaterialTheme.typography.titleLarge)
                Text(row.lastActivityLabel, color = AacTextSecondary, style = MaterialTheme.typography.bodyMedium)
                Text(row.elapsedLabel, color = AacTextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            SignalBadge(row.activeSignal, row.signalState)
            IconButton(onClick = onSnooze, enabled = row.activeSignal != null) {
                Icon(
                    Icons.Outlined.NotificationsOff,
                    contentDescription = if (row.signalState == SignalState.SNOOZED) "Unsnooze" else "Snooze",
                    tint = if (row.signalState == SignalState.SNOOZED) Color(0xFF6B7280) else MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ParticipantCardPreview() {
    val sampleParticipant = ParticipantOverview(
        userId = "1",
        displayName = "Alice",
        activeSignal = SignalType.HELP,
        signalState = SignalState.CURRENT,
        lastActivityLabel = "Typing...",
        elapsedLabel = "2m",
        messageCount = 5,
        supportRequests = 1,
        isLowParticipation = false
    )
    GroupAacTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ParticipantCard(
                row = sampleParticipant,
                selected = false,
                onClick = {},
                onSnooze = {}
            )
            ParticipantCard(
                row = sampleParticipant.copy(displayName = "Bob"),
                selected = true,
                onClick = {},
                onSnooze = {}
            )
        }
    }
}
