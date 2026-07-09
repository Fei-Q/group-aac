package com.example.groupaac.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.groupaac.ui.theme.GroupAacTheme

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes leadingIconRes: Int? = null,
    leadingIconContentDescription: String? = null,
    leadingEmoji: String? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
    shape: Shape = RoundedCornerShape(999.dp),
    minHeight: Dp = 56.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    textStyle: TextStyle? = null,
    iconSize: Dp = 22.dp,
    emojiFontSize: TextUnit = 22.sp,
    spacing: Dp = 10.dp,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val resolvedContainerColor = containerColor ?: MaterialTheme.colorScheme.primary
    val resolvedContentColor = contentColor ?: MaterialTheme.colorScheme.onPrimary

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = minHeight),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = resolvedContainerColor,
            contentColor = resolvedContentColor
        ),
        contentPadding = contentPadding
    ) {
        AppButtonContent(
            text = text,
            leadingIconRes = leadingIconRes,
            leadingIconContentDescription = leadingIconContentDescription,
            leadingEmoji = leadingEmoji,
            textStyle = textStyle ?: MaterialTheme.typography.labelLarge,
            iconSize = iconSize,
            emojiFontSize = emojiFontSize,
            spacing = spacing,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes leadingIconRes: Int? = null,
    leadingIconContentDescription: String? = null,
    leadingEmoji: String? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
    borderColor: Color? = null,
    shape: Shape = RoundedCornerShape(999.dp),
    minHeight: Dp = 56.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    textStyle: TextStyle? = null,
    iconSize: Dp = 22.dp,
    emojiFontSize: TextUnit = 22.sp,
    spacing: Dp = 10.dp,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val resolvedContainerColor = containerColor ?: Color.Transparent
    val resolvedContentColor = contentColor ?: MaterialTheme.colorScheme.secondary
    val resolvedBorderColor = borderColor ?: MaterialTheme.colorScheme.outline

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = minHeight),
        shape = shape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = resolvedContainerColor,
            contentColor = resolvedContentColor
        ),
        border = BorderStroke(1.dp, resolvedBorderColor),
        contentPadding = contentPadding
    ) {
        AppButtonContent(
            text = text,
            leadingIconRes = leadingIconRes,
            leadingIconContentDescription = leadingIconContentDescription,
            leadingEmoji = leadingEmoji,
            textStyle = textStyle ?: MaterialTheme.typography.labelLarge,
            iconSize = iconSize,
            emojiFontSize = emojiFontSize,
            spacing = spacing,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow
        )
    }
}

@Composable
fun CompactActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes leadingIconRes: Int? = null,
    leadingIconContentDescription: String? = null,
    leadingEmoji: String? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
    borderColor: Color? = null
) {
    SecondaryButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        leadingIconRes = leadingIconRes,
        leadingIconContentDescription = leadingIconContentDescription,
        leadingEmoji = leadingEmoji,
        containerColor = containerColor,
        contentColor = contentColor,
        borderColor = borderColor,
        shape = RoundedCornerShape(16.dp),
        minHeight = 64.dp,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        textStyle = MaterialTheme.typography.labelMedium,
        iconSize = 18.dp,
        emojiFontSize = 18.sp,
        spacing = 6.dp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun AppButtonContent(
    text: String,
    @DrawableRes leadingIconRes: Int?,
    leadingIconContentDescription: String?,
    leadingEmoji: String?,
    textStyle: TextStyle,
    iconSize: Dp,
    emojiFontSize: TextUnit,
    spacing: Dp,
    fontWeight: FontWeight?,
    textAlign: TextAlign?,
    maxLines: Int,
    overflow: TextOverflow
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingEmoji != null) {
            Text(
                text = leadingEmoji,
                fontSize = emojiFontSize,
                maxLines = 1
            )
        }

        leadingIconRes?.let { iconRes ->
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = leadingIconContentDescription,
                modifier = Modifier.size(iconSize)
            )
        }

        Text(
            text = text,
            style = textStyle,
            fontWeight = fontWeight ?: textStyle.fontWeight,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AppButtonsPreview() {
    GroupAacTheme {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PrimaryButton(
                text = "Primary Button",
                leadingEmoji = "✋",
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            )

            SecondaryButton(
                text = "Secondary Button",
                leadingEmoji = "✅",
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            )

            CompactActionButton(
                text = "Compact Action",
                leadingEmoji = "💬",
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}