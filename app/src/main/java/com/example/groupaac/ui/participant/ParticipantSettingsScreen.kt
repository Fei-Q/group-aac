package com.example.groupaac.ui.participant

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.entity.UserSettingsEntity
import com.example.groupaac.ui.settings.UserSettingsScreen

@Composable
fun ParticipantSettingsScreen(
    user: UserEntity?,
    settings: UserSettingsEntity,
    onUpdateSettings: (UserSettingsEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    UserSettingsScreen(
        user = user,
        settings = settings,
        onUpdateSettings = onUpdateSettings,
        onClearLocalHistory = {},
        onExportSummary = {},
        modifier = modifier
    )
}

@Composable
fun ParticipantSettingsScreen(
    uiState: ParticipantUiState,
    onUpdateSettings: (UserSettingsEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    ParticipantSettingsScreen(
        user = uiState.user,
        settings = uiState.settings ?: UserSettingsEntity(
            userId = uiState.user?.uid.orEmpty()
        ),
        onUpdateSettings = onUpdateSettings,
        modifier = modifier
    )
}
