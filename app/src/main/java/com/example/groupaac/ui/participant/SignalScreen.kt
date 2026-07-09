package com.example.groupaac.ui.participant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.groupaac.data.entity.StatusSignalEntity
import com.example.groupaac.model.SignalState
import com.example.groupaac.model.SignalType
import com.example.groupaac.ui.common.AppCard
import com.example.groupaac.ui.common.AppWindowSize
import com.example.groupaac.ui.common.PrimaryButton
import com.example.groupaac.ui.common.SecondaryButton
import com.example.groupaac.ui.common.rememberAppWindowSize
import com.example.groupaac.ui.theme.AacBackground
import com.example.groupaac.ui.theme.AacBlue
import com.example.groupaac.ui.theme.AacBorder
import com.example.groupaac.ui.theme.AacTextSecondary
import com.example.groupaac.ui.theme.GroupAacTheme

private val SignalTextColor = Color(0xFF0B1B4D)

@Composable
fun SignalScreen(
    uiState: ParticipantUiState,
    onSignal: (SignalType) -> Unit,
    onClearSignal: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (rememberAppWindowSize()) {
        AppWindowSize.Phone -> SignalScreenPhone(
            uiState = uiState,
            onSignal = onSignal,
            onClearSignal = onClearSignal,
            modifier = modifier
        )

        AppWindowSize.Tablet,
        AppWindowSize.Desktop -> SignalScreenTablet(
            uiState = uiState,
            onSignal = onSignal,
            onClearSignal = onClearSignal,
            modifier = modifier
        )
    }
}

@Composable
private fun SignalScreenPhone(
    uiState: ParticipantUiState,
    onSignal: (SignalType) -> Unit,
    onClearSignal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AacBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SignalHeader()

        CurrentSignalBanner(
            currentSignal = uiState.currentSignal,
            onClearSignal = onClearSignal
        )

        StanceSignalCard(onSignal = onSignal)
        TurnManagementSignalCard(onSignal = onSignal)
        AssistanceSignalCard(onSignal = onSignal)
    }
}

@Composable
private fun SignalScreenTablet(
    uiState: ParticipantUiState,
    onSignal: (SignalType) -> Unit,
    onClearSignal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AacBackground)
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        SignalHeader()

        CurrentSignalBanner(
            currentSignal = uiState.currentSignal,
            onClearSignal = onClearSignal
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                StanceSignalCard(onSignal = onSignal)
                TurnManagementSignalCard(onSignal = onSignal)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                AssistanceSignalCard(onSignal = onSignal)
            }
        }
    }
}

@Composable
private fun SignalHeader() {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Signal",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.secondary
        )

        Text(
            text = "Quick cues for status and intent.",
            color = AacTextSecondary
        )
    }
}

@Composable
private fun StanceSignalCard(
    onSignal: (SignalType) -> Unit
) {
    AppCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SignalButtonRow(
                signals = listOf(
                    SignalType.YES_AGREE,
                    SignalType.NO_DISAGREE,
                    SignalType.OKAY
                ),
                onSignal = onSignal
            )
        }
    }
}

@Composable
private fun TurnManagementSignalCard(
    onSignal: (SignalType) -> Unit
) {
    AppCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SignalLargeButton(
                signal = SignalType.WANT_TO_SHARE,
                onSignal = onSignal
            )

            SignalButtonRow(
                signals = listOf(
                    SignalType.READY,
                    SignalType.WAIT
                ),
                onSignal = onSignal
            )

            SignalButtonRow(
                signals = listOf(
                    SignalType.COMMENT,
                    SignalType.QUESTION
                ),
                onSignal = onSignal
            )
        }
    }
}

@Composable
private fun AssistanceSignalCard(
    onSignal: (SignalType) -> Unit
) {
    AppCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SignalLargeButton(
                signal = SignalType.HELP,
                onSignal = onSignal
            )

            SignalButtonRow(
                signals = listOf(
                    SignalType.REPEAT,
                    SignalType.FIND_WORD
                ),
                onSignal = onSignal
            )

            SignalButtonRow(
                signals = listOf(
                    SignalType.SPELL_WORD,
                    SignalType.SAY_WORD
                ),
                onSignal = onSignal
            )
        }
    }
}

@Composable
private fun CurrentSignalBanner(
    currentSignal: StatusSignalEntity?,
    onClearSignal: () -> Unit
) {
    if (currentSignal == null) return

    val signal = currentSignal.type

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, AacBlue, RoundedCornerShape(18.dp))
            .background(AacBlue.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(AacBlue, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = signal.emoji,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Current signal",
                style = MaterialTheme.typography.labelMedium,
                color = AacTextSecondary
            )

            Text(
                text = signal.label,
                style = MaterialTheme.typography.bodyLarge,
                color = SignalTextColor,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )
        }

        TextButton(
            onClick = onClearSignal
        ) {
            Text(
                text = "✕",
                style = MaterialTheme.typography.titleLarge,
                color = SignalTextColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SignalLargeButton(
    signal: SignalType,
    onSignal: (SignalType) -> Unit
) {
    PrimaryButton(
        text = signal.buttonLabel,
        leadingEmoji = signal.emoji,
        onClick = {
            onSignal(signal)
        },
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        minHeight = 72.dp,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        textStyle = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 2
    )
}

@Composable
private fun SignalButtonRow(
    signals: List<SignalType>,
    onSignal: (SignalType) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        signals.forEach { signal ->
            SignalSmallButton(
                signal = signal,
                onSignal = onSignal,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SignalSmallButton(
    signal: SignalType,
    onSignal: (SignalType) -> Unit,
    modifier: Modifier = Modifier
) {
    SecondaryButton(
        text = signal.buttonLabel,
        leadingEmoji = signal.emoji,
        onClick = {
            onSignal(signal)
        },
        modifier = modifier,
        containerColor = Color.White,
        contentColor = SignalTextColor,
        borderColor = AacBorder,
        shape = RoundedCornerShape(14.dp),
        minHeight = 68.dp,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 2
    )
}

private fun previewSignalUiState(): ParticipantUiState {
    return ParticipantUiState(
        currentSignal = StatusSignalEntity(
            id = "signal-1",
            sessionId = "session-1",
            userId = "user-1",
            type = SignalType.READY,
            state = SignalState.CURRENT,
            createdAt = System.currentTimeMillis()
        ),
        lastSignal = SignalType.READY
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun SignalScreenPhonePreview() {
    GroupAacTheme {
        SignalScreen(
            uiState = previewSignalUiState(),
            onSignal = {},
            onClearSignal = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Composable
fun SignalScreenTabletPortraitPreview() {
    GroupAacTheme {
        SignalScreen(
            uiState = previewSignalUiState(),
            onSignal = {},
            onClearSignal = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
fun SignalScreenTabletLandscapePreview() {
    GroupAacTheme {
        SignalScreen(
            uiState = previewSignalUiState(),
            onSignal = {},
            onClearSignal = {}
        )
    }
}