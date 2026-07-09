package com.example.groupaac.ui.facilitator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.groupaac.model.ParticipantOverview
import com.example.groupaac.model.SessionSummaryUi
import com.example.groupaac.ui.common.AppCard
import com.example.groupaac.ui.common.SecondaryButton
import com.example.groupaac.ui.theme.AacBackground
import com.example.groupaac.ui.theme.AacBlue
import com.example.groupaac.ui.theme.AacTextSecondary
import com.example.groupaac.ui.theme.GroupAacTheme

@Composable
fun SummaryScreen(uiState: FacilitatorUiState, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxSize().background(AacBackground).padding(22.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Summary", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.secondary)
            Text("Post-session overview of participation, support requests, and saved items.", color = AacTextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Participants", uiState.summary.participantCount.toString(), Modifier.weight(1f))
                MetricCard("Shared items", uiState.summary.sharedItemCount.toString(), Modifier.weight(1f))
                MetricCard("Support", uiState.summary.supportRequestCount.toString(), Modifier.weight(1f))
                MetricCard("Saved", uiState.summary.savedItemCount.toString(), Modifier.weight(1f))
            }
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Participation", style = MaterialTheme.typography.titleLarge)
                    uiState.participants.forEach { row -> ParticipationBar(row) }
                }
            }
        }
        Column(Modifier.weight(0.75f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Notes and follow-up", style = MaterialTheme.typography.titleLarge)
                    Text("Invite low-participation members earlier in the next session.", color = AacTextSecondary)
                    Text("Review saved shared messages and image-based storytelling items.", color = AacTextSecondary)
                    SecondaryButton("Summary Report", onClick = {}, modifier = Modifier.fillMaxWidth())
                    SecondaryButton("Export", onClick = {}, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineLarge, color = AacBlue)
            Text(label, color = AacTextSecondary)
        }
    }
}

@Composable
private fun ParticipationBar(row: ParticipantOverview) {
    val widthWeight = (row.messageCount.coerceAtLeast(1)).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(row.displayName, style = MaterialTheme.typography.titleMedium)
            Text("${row.messageCount} items", color = AacTextSecondary)
        }
        Box(Modifier.fillMaxWidth().height(14.dp).background(Color(0xFFE3EAF5))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((0.12f * widthWeight).coerceAtMost(1f)).background(AacBlue))
        }
        if (row.isLowParticipation) Text("Low participation flag", color = Color(0xFF7A5AF8), style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
fun SummaryScreenPreview() {
    val mockParticipants = listOf(
        ParticipantOverview("1", "Alice", null, null, 15, 0, "Active", "2m"),
        ParticipantOverview("2", "Bob", null, null, 2, 1, "Idle", "5m", isLowParticipation = true),
        ParticipantOverview("3", "Charlie", null, null, 12, 0, "Active", "10s")
    )
    GroupAacTheme {
        SummaryScreen(
            uiState = FacilitatorUiState(
                participants = mockParticipants,
                summary = SessionSummaryUi(3, 29, 1, 5, mockParticipants)
            )
        )
    }
}
