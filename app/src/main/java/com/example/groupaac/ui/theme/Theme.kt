package com.example.groupaac.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = AacBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = AacBlueDark,
    background = AacBackground,
    surface = AacCard,
    onSurface = AacBlueDark,
    outline = AacBorder
)

@Composable
fun GroupAacTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = GroupAacTypography,
        content = content
    )
}
