package com.example.groupaac.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.groupaac.R
import com.example.groupaac.model.UserRole
import com.example.groupaac.ui.theme.AacBlue
import com.example.groupaac.ui.theme.AacBorder

enum class RoleSelectionButtonLayout {
    Horizontal,
    Vertical
}

enum class RoleSelectionButtonStyle {
    Soft,
    Solid
}

@Composable
fun RoleSelectionButton(
    role: UserRole,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = role.label,
    layout: RoleSelectionButtonLayout = RoleSelectionButtonLayout.Horizontal,
    selectedStyle: RoleSelectionButtonStyle = RoleSelectionButtonStyle.Soft,
    iconSize: Dp = 28.dp,
    labelFontSize: TextUnit = TextUnit.Unspecified
) {
    val background = when {
        selected && selectedStyle == RoleSelectionButtonStyle.Solid -> AacBlue
        selected -> AacBlue.copy(alpha = 0.14f)
        else -> Color.White
    }

    val borderColor = if (selected) AacBlue else AacBorder

    val contentColor =
        if (selected && selectedStyle == RoleSelectionButtonStyle.Solid) {
            Color.White
        } else {
            MaterialTheme.colorScheme.secondary
        }

    Box(
        modifier = modifier
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .background(background, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        if (layout == RoleSelectionButtonLayout.Vertical) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RoleIcon(role = role, iconSize = iconSize, contentColor = contentColor)
                RoleLabel(
                    label = label,
                    selected = selected,
                    contentColor = contentColor,
                    labelFontSize = labelFontSize
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RoleIcon(role = role, iconSize = iconSize, contentColor = contentColor)
                RoleLabel(
                    label = label,
                    selected = selected,
                    contentColor = contentColor,
                    labelFontSize = labelFontSize
                )
            }
        }
    }
}

@Composable
private fun RoleIcon(
    role: UserRole,
    iconSize: Dp,
    contentColor: Color
) {
    Icon(
        painter = painterResource(id = roleIconRes(role)),
        contentDescription = null,
        modifier = Modifier.size(iconSize),
        tint = contentColor
    )
}

@Composable
private fun RoleLabel(
    label: String,
    selected: Boolean,
    contentColor: Color,
    labelFontSize: TextUnit
) {
    val baseStyle = MaterialTheme.typography.bodyLarge
    val resolvedStyle =
        if (labelFontSize == TextUnit.Unspecified) {
            baseStyle
        } else {
            baseStyle.copy(fontSize = labelFontSize)
        }

    Text(
        text = label,
        style = resolvedStyle,
        color = contentColor,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@DrawableRes
fun roleIconRes(role: UserRole): Int {
    return when (role) {
        UserRole.PARTICIPANT -> R.drawable.ic_role_participant
        UserRole.FACILITATOR -> R.drawable.ic_role_facilitator
    }
}