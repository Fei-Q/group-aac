package com.example.groupaac.ui.common

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass

enum class AppWindowSize {
    Phone,
    Tablet,
    Desktop
}

@Composable
fun rememberAppWindowSize(): AppWindowSize {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    return when {
        windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
        ) -> AppWindowSize.Desktop

        windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
        ) -> AppWindowSize.Tablet

        else -> AppWindowSize.Phone
    }
}