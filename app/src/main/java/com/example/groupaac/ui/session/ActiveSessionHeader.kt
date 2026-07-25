package com.example.groupaac.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.groupaac.model.ActiveSession
import com.example.groupaac.model.SessionConnectionState
import com.example.groupaac.ui.common.AppWindowSize
import com.example.groupaac.ui.common.rememberAppWindowSize
import com.example.groupaac.ui.theme.AacBlueLight
import com.example.groupaac.ui.theme.AacBorder
import com.example.groupaac.ui.theme.AacGreen
import com.example.groupaac.ui.theme.AacRed
import com.example.groupaac.ui.theme.AacTextSecondary
import com.example.groupaac.ui.theme.AacYellow

private enum class PendingSessionAction {
    Leave,
    End
}

private data class ConnectionPresentation(
    val label: String,
    val backgroundColor: Color,
    val contentColor: Color
)

@Composable
fun ActiveSessionHeader(
    activeSession: ActiveSession,
    connectionState: SessionConnectionState,
    isFacilitator: Boolean,
    onLeaveSession: () -> Unit,
    onEndSession: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var pendingAction by remember {
        mutableStateOf<PendingSessionAction?>(null)
    }

    val connectionPresentation =
        connectionPresentation(connectionState)

    val isLeaving =
        connectionState is SessionConnectionState.Leaving

    val canEndSession =
        connectionState is SessionConnectionState.Connected

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AacBlueLight,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        when (rememberAppWindowSize()) {
            AppWindowSize.Phone -> {
                PhoneSessionHeader(
                    activeSession = activeSession,
                    connectionPresentation =
                        connectionPresentation,
                    isFacilitator = isFacilitator,
                    leaveEnabled = !isLeaving,
                    endEnabled = canEndSession,
                    showEndAction =
                        isFacilitator && onEndSession != null,
                    onLeaveClick = {
                        pendingAction =
                            PendingSessionAction.Leave
                    },
                    onEndClick = {
                        pendingAction =
                            PendingSessionAction.End
                    }
                )
            }

            AppWindowSize.Tablet,
            AppWindowSize.Desktop -> {
                WideSessionHeader(
                    activeSession = activeSession,
                    connectionPresentation =
                        connectionPresentation,
                    isFacilitator = isFacilitator,
                    leaveEnabled = !isLeaving,
                    endEnabled = canEndSession,
                    showEndAction =
                        isFacilitator && onEndSession != null,
                    onLeaveClick = {
                        pendingAction =
                            PendingSessionAction.Leave
                    },
                    onEndClick = {
                        pendingAction =
                            PendingSessionAction.End
                    }
                )
            }
        }
    }

    when (pendingAction) {
        PendingSessionAction.Leave -> {
            AlertDialog(
                onDismissRequest = {
                    pendingAction = null
                },
                title = {
                    Text("Leave this session?")
                },
                text = {
                    Text(
                        "You will return to the sessions screen. " +
                                "The session will continue for everyone else."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingAction = null
                            onLeaveSession()
                        }
                    ) {
                        Text("Leave")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            pendingAction = null
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        PendingSessionAction.End -> {
            AlertDialog(
                onDismissRequest = {
                    pendingAction = null
                },
                title = {
                    Text("End this session?")
                },
                text = {
                    Text(
                        "This will end the session for all " +
                                "participants. This action cannot be undone."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingAction = null
                            onEndSession?.invoke()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AacRed,
                            contentColor = Color.White
                        )
                    ) {
                        Text("End session")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            pendingAction = null
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        null -> Unit
    }
}

@Composable
private fun PhoneSessionHeader(
    activeSession: ActiveSession,
    connectionPresentation: ConnectionPresentation,
    isFacilitator: Boolean,
    leaveEnabled: Boolean,
    endEnabled: Boolean,
    showEndAction: Boolean,
    onLeaveClick: () -> Unit,
    onEndClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = 12.dp
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SessionInformation(
                activeSession = activeSession,
                isFacilitator = isFacilitator,
                modifier = Modifier.weight(1f)
            )

            ConnectionStatusPill(
                presentation = connectionPresentation
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onLeaveClick,
                enabled = leaveEnabled,
                shape = RoundedCornerShape(999.dp),
                contentPadding = PaddingValues(
                    horizontal = 18.dp,
                    vertical = 8.dp
                )
            ) {
                Text("Leave")
            }

            if (showEndAction) {
                Spacer(Modifier.width(10.dp))

                Button(
                    onClick = onEndClick,
                    enabled = endEnabled,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AacRed,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(
                        horizontal = 18.dp,
                        vertical = 8.dp
                    )
                ) {
                    Text("End")
                }
            }
        }
    }
}

@Composable
private fun WideSessionHeader(
    activeSession: ActiveSession,
    connectionPresentation: ConnectionPresentation,
    isFacilitator: Boolean,
    leaveEnabled: Boolean,
    endEnabled: Boolean,
    showEndAction: Boolean,
    onLeaveClick: () -> Unit,
    onEndClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 24.dp,
                vertical = 12.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SessionInformation(
            activeSession = activeSession,
            isFacilitator = isFacilitator,
            modifier = Modifier.weight(1f)
        )

        ConnectionStatusPill(
            presentation = connectionPresentation
        )

        OutlinedButton(
            onClick = onLeaveClick,
            enabled = leaveEnabled,
            shape = RoundedCornerShape(999.dp)
        ) {
            Text("Leave session")
        }

        if (showEndAction) {
            Button(
                onClick = onEndClick,
                enabled = endEnabled,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AacRed,
                    contentColor = Color.White
                )
            ) {
                Text("End session")
            }
        }
    }
}

@Composable
private fun SessionInformation(
    activeSession: ActiveSession,
    isFacilitator: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = activeSession.sessionName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = buildString {
                append(
                    if (isFacilitator) {
                        "Facilitator session"
                    } else {
                        "Participant session"
                    }
                )

                if (activeSession.joinCode.isNotBlank()) {
                    append(" • Code ")
                    append(activeSession.joinCode)
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = AacTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ConnectionStatusPill(
    presentation: ConnectionPresentation
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = presentation.backgroundColor
    ) {
        Text(
            text = presentation.label,
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 7.dp
            ),
            color = presentation.contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun connectionPresentation(
    state: SessionConnectionState
): ConnectionPresentation {
    return when (state) {
        SessionConnectionState.Restoring -> {
            ConnectionPresentation(
                label = "Restoring…",
                backgroundColor = AacBorder.copy(alpha = 0.7f),
                contentColor = AacTextSecondary
            )
        }

        SessionConnectionState.NotInSession -> {
            ConnectionPresentation(
                label = "Not connected",
                backgroundColor = AacBorder.copy(alpha = 0.7f),
                contentColor = AacTextSecondary
            )
        }

        is SessionConnectionState.Joining -> {
            ConnectionPresentation(
                label = "Joining…",
                backgroundColor = AacYellow.copy(alpha = 0.2f),
                contentColor = Color(0xFF6D4C00)
            )
        }

        is SessionConnectionState.Connected -> {
            ConnectionPresentation(
                label = "Connected",
                backgroundColor = AacGreen.copy(alpha = 0.16f),
                contentColor = AacGreen
            )
        }

        is SessionConnectionState.Reconnecting -> {
            ConnectionPresentation(
                label = "Reconnecting…",
                backgroundColor = AacYellow.copy(alpha = 0.2f),
                contentColor = Color(0xFF6D4C00)
            )
        }

        is SessionConnectionState.Leaving -> {
            ConnectionPresentation(
                label = "Leaving…",
                backgroundColor = AacBorder.copy(alpha = 0.7f),
                contentColor = AacTextSecondary
            )
        }
    }
}