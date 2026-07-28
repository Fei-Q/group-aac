package com.example.groupaac.ui.facilitator

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.groupaac.data.entity.UserSettingsEntity
import com.example.groupaac.ui.settings.UserSettingsScreen

@Composable
fun FacilitatorSettingsScreen(
    settings: UserSettingsEntity,
    onSettingsChange: (UserSettingsEntity) -> Unit,
    onClearLocalHistory: () -> Unit,
    onExportSummary: () -> Unit,
    modifier: Modifier
) {
    UserSettingsScreen(
        user = null,
        settings = settings,
        onUpdateSettings = onSettingsChange,
        onClearLocalHistory = onClearLocalHistory,
        onExportSummary = onExportSummary,
        modifier = modifier
    )
}
