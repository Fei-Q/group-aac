package com.example.groupaac.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.ui.common.AppCard
import com.example.groupaac.ui.common.PrimaryButton
import com.example.groupaac.ui.common.SecondaryButton
import com.example.groupaac.ui.theme.AacTextSecondary
import com.example.groupaac.util.TimeUtils

@Composable
fun DebugScreen(
    viewModel: DebugViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Debug",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.secondary
        )

        Text(
            text = "TEMP DEBUG HARNESS / remove before production.",
            color = AacTextSecondary
        )

        SessionSummaryCard(
            session = uiState.activeSession,
            sessionId = uiState.activeSessionId,
            statusMessage = uiState.statusMessage
        )

        PrimaryButton(
            text = "Setup demo session",
            onClick = viewModel::setupDemoSession,
            modifier = Modifier.fillMaxWidth()
        )

        PrimaryButton(
            text = "Add Alice",
            onClick = viewModel::addAlice,
            modifier = Modifier.fillMaxWidth()
        )

        PrimaryButton(
            text = "Add Bob",
            onClick = viewModel::addBob,
            modifier = Modifier.fillMaxWidth()
        )

        PrimaryButton(
            text = "Alice: Help",
            onClick = viewModel::aliceHelp,
            modifier = Modifier.fillMaxWidth()
        )

        PrimaryButton(
            text = "Bob: Wait",
            onClick = viewModel::bobWait,
            modifier = Modifier.fillMaxWidth()
        )

        SecondaryButton(
            text = "Clear signals",
            onClick = viewModel::clearSignals,
            modifier = Modifier.fillMaxWidth()
        )

        SecondaryButton(
            text = "Seed messages",
            onClick = viewModel::seedMessages,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun SessionSummaryCard(
    session: SessionEntity?,
    sessionId: String?,
    statusMessage: String?
) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Active session",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = sessionId ?: "No active session",
                style = MaterialTheme.typography.bodyLarge
            )

            if (session != null) {
                Text(
                    text = session.name,
                    color = AacTextSecondary
                )

                Text(
                    text = "Started ${TimeUtils.dateLabel(session.actualStartedAt ?: session.createdAt)}",
                    color = AacTextSecondary
                )
            }

            if (!statusMessage.isNullOrBlank()) {
                Text(
                    text = statusMessage,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
