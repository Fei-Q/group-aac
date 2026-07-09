package com.example.groupaac.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.groupaac.model.SignalState
import com.example.groupaac.model.SignalType
import com.example.groupaac.ui.theme.AacBlueLight
import com.example.groupaac.ui.theme.AacGreen
import com.example.groupaac.ui.theme.AacRed
import com.example.groupaac.ui.theme.AacYellow

@Composable
fun SignalBadge(
    type: SignalType?,
    state: SignalState?,
    modifier: Modifier = Modifier
) {
    if (type == null) return

    val backgroundColor = when {
        state == SignalState.SNOOZED -> Color(0xFFE5E7EB)
        type in listOf(
            SignalType.HELP,
            SignalType.REPEAT,
            SignalType.FIND_WORD,
            SignalType.SPELL_WORD,
            SignalType.SAY_WORD
        ) -> AacRed.copy(alpha = 0.14f)

        type in listOf(
            SignalType.WANT_TO_SHARE,
            SignalType.READY,
            SignalType.WAIT,
            SignalType.COMMENT,
            SignalType.QUESTION,
            SignalType.ANSWER,
            SignalType.HOLD_MY_TURN,
            SignalType.MORE_TIME
        ) -> AacYellow.copy(alpha = 0.22f)

        type in listOf(
            SignalType.YES_AGREE,
            SignalType.OKAY,
            SignalType.YES
        ) -> AacGreen.copy(alpha = 0.16f)

        type in listOf(
            SignalType.NO_DISAGREE,
            SignalType.NO
        ) -> AacRed.copy(alpha = 0.12f)

        else -> AacBlueLight
    }

    val baseLabel = "${type.emoji} ${type.label}"

    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = baseLabel,
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF0B1B4D)
        )
    }
}
