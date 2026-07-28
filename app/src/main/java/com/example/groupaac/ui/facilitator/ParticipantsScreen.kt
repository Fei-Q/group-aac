package com.example.groupaac.ui.facilitator

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.groupaac.data.entity.SessionJoinRequestEntity
import com.example.groupaac.model.ParticipantOverview
import com.example.groupaac.model.SignalType
import com.example.groupaac.model.SignalState
import com.example.groupaac.ui.common.AppCard
import com.example.groupaac.ui.common.SecondaryButton
import com.example.groupaac.ui.theme.AacBackground
import com.example.groupaac.ui.theme.AacTextSecondary
import com.example.groupaac.ui.theme.GroupAacTheme

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun ParticipantsScreen(
    uiState: FacilitatorUiState,
    onSelect: (String?) -> Unit,
    onApproveJoinRequest: (String) -> Unit,
    onDeclineJoinRequest: (String) -> Unit,
    onSnooze: (String) -> Unit,
    onQuickLog: (String, String) -> Unit,
    onAddNote: (String?, String) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize().background(AacBackground).padding(22.dp)) {
        val selected = uiState.participants.firstOrNull { it.userId == uiState.selectedParticipantId }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.fillMaxSize()) {
            Column(Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Participants", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.secondary)
                Text("Live view of participant statuses.", color = AacTextSecondary)
                if (uiState.isHost) {
                    uiState.pendingJoinRequests.forEach { request ->
                        FacilitatorRequestCard(
                            request = request,
                            onApprove = {
                                onApproveJoinRequest(request.id)
                            },
                            onDecline = {
                                onDeclineJoinRequest(request.id)
                            }
                        )
                    }
                }
                if (uiState.participants.isEmpty()) {
                    AppCard { Text("No participants yet. Join the test session as a participant to populate the roster.", color = AacTextSecondary) }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(uiState.participants, key = { it.userId }) { row ->
                        ParticipantCard(
                            row = row,
                            selected = row.userId == uiState.selectedParticipantId,
                            onClick = { onSelect(row.userId) },
                            onSnooze = { onSnooze(row.userId) }
                        )
                    }
                }
            }
            Column(Modifier.weight(0.8f)) {
                if (selected == null) {
                    GeneralFacilitatorSidebar(uiState)
                } else {
                    ParticipantDetailSidebar(selected, onSnooze, onQuickLog, onAddNote)
                }
            }
        }
    }
}

@Composable
private fun FacilitatorRequestCard(
    request: SessionJoinRequestEntity,
    onApprove: () -> Unit,
    onDecline: () -> Unit
) {
    AppCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Facilitator request",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = "${request.displayName} wants to facilitate.",
                color = AacTextSecondary
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SecondaryButton(
                    text = "Approve",
                    onClick = onApprove,
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Decline",
                    onClick = onDecline,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
fun ParticipantsScreenPreview() {
    val mockParticipants = listOf(
        ParticipantOverview("1", "Alice", SignalType.COMMENT, SignalState.CURRENT, 5, 0, "Typing...", "2m"),
        ParticipantOverview("2", "Bob", SignalType.HELP, SignalState.CURRENT, 2, 1, "Idle", "5m", isLowParticipation = true),
        ParticipantOverview("3", "Charlie", null, null, 12, 0, "Active", "10s")
    )
    GroupAacTheme {
        ParticipantsScreen(
            uiState = FacilitatorUiState(
                participants = mockParticipants,
                selectedParticipantId = "1"
            ),
            onSelect = {},
            onApproveJoinRequest = {},
            onDeclineJoinRequest = {},
            onSnooze = {},
            onQuickLog = { _, _ -> },
            onAddNote = { _, _ -> }
        )
    }
}
