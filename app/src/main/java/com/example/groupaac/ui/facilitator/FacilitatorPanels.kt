package com.example.groupaac.ui.facilitator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.model.ParticipantOverview
import com.example.groupaac.ui.common.AppCard
import com.example.groupaac.ui.common.PrimaryButton
import com.example.groupaac.ui.common.SecondaryButton
import com.example.groupaac.ui.common.SignalBadge
import com.example.groupaac.ui.theme.AacTextSecondary

@Composable
fun GeneralFacilitatorSidebar(uiState: FacilitatorUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Active alerts", style = MaterialTheme.typography.titleLarge)
                if (uiState.activeSignals.isEmpty()) Text("No active alerts.", color = AacTextSecondary)
                uiState.activeSignals.take(4).forEach { signal ->
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(signal.displayName)
                        SignalBadge(signal.type, signal.state)
                    }
                }
            }
        }
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Private inbox", style = MaterialTheme.typography.titleLarge)
                val latest = uiState.messages.firstOrNull { it.message.target == MessageTarget.FACILITATOR }
                Text(latest?.let { "${it.message.senderName}: ${it.message.text}" } ?: "No private messages.", color = AacTextSecondary)
            }
        }
    }
}

@Composable
fun ParticipantDetailSidebar(
    participant: ParticipantOverview,
    onSnooze: (String) -> Unit,
    onQuickLog: (String, String) -> Unit,
    onAddNote: (String?, String) -> Unit
) {
    var note by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(participant.displayName, style = MaterialTheme.typography.headlineMedium)
                SignalBadge(participant.activeSignal, participant.signalState)
                Text(participant.lastActivityLabel, color = AacTextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Snooze", onClick = { onSnooze(participant.userId) }, modifier = Modifier.weight(1f))
                }
            }
        }
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Quick log", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton("Spoke", { onQuickLog(participant.userId, "Spoke") }, Modifier.weight(1f))
                    SecondaryButton("Needs help", { onQuickLog(participant.userId, "Needs help") }, Modifier.weight(1f))
                }
                SecondaryButton("Want to share", { onQuickLog(participant.userId, "Want to share") }, Modifier.fillMaxWidth())
            }
        }
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Note", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Add note") }, modifier = Modifier.fillMaxWidth())
                PrimaryButton("Add note", onClick = { onAddNote(participant.userId, note); note = "" }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
