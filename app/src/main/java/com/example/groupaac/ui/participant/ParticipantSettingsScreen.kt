package com.example.groupaac.ui.participant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.entity.UserSettingsEntity
import com.example.groupaac.model.UserRole
import com.example.groupaac.ui.common.AppCard
import com.example.groupaac.ui.common.AppWindowSize
import com.example.groupaac.ui.common.rememberAppWindowSize
import com.example.groupaac.ui.theme.AacBackground
import com.example.groupaac.ui.theme.AacTextSecondary
import com.example.groupaac.ui.theme.GroupAacTheme

@Composable
fun ParticipantSettingsScreen(
    uiState: ParticipantUiState,
    onUpdateSettings: (UserSettingsEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = uiState.settings ?: UserSettingsEntity(
        userId = uiState.user?.id ?: ""
    )

    when (rememberAppWindowSize()) {
        AppWindowSize.Phone -> ParticipantSettingsScreenPhone(
            uiState = uiState,
            settings = settings,
            onUpdateSettings = onUpdateSettings,
            modifier = modifier
        )

        AppWindowSize.Tablet,
        AppWindowSize.Desktop -> ParticipantSettingsScreenTablet(
            uiState = uiState,
            settings = settings,
            onUpdateSettings = onUpdateSettings,
            modifier = modifier
        )
    }
}

@Composable
private fun ParticipantSettingsScreenPhone(
    uiState: ParticipantUiState,
    settings: UserSettingsEntity,
    onUpdateSettings: (UserSettingsEntity) -> Unit,
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
        SettingsHeader()

        ProfileCard(uiState)
        AccessibilitySettingsCard(
            settings = settings,
            onUpdateSettings = onUpdateSettings
        )
    }
}

@Composable
private fun ParticipantSettingsScreenTablet(
    uiState: ParticipantUiState,
    settings: UserSettingsEntity,
    onUpdateSettings: (UserSettingsEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AacBackground)
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        SettingsHeader()

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
                ProfileCard(uiState)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                AccessibilitySettingsCard(
                    settings = settings,
                    onUpdateSettings = onUpdateSettings
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader() {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.secondary
        )

        Text(
            text = "Personalization and accessibility controls.",
            color = AacTextSecondary
        )
    }
}

@Composable
private fun ProfileCard(
    uiState: ParticipantUiState
) {
    AppCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "Profile",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "Username: ${uiState.user?.displayName ?: "Unknown"}"
            )

            Text(
                text = "Role: ${uiState.user?.role?.label ?: "Unknown"}",
                color = AacTextSecondary
            )
        }
    }
}

@Composable
private fun AccessibilitySettingsCard(
    settings: UserSettingsEntity,
    onUpdateSettings: (UserSettingsEntity) -> Unit
) {
    AppCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "Text size",
                style = MaterialTheme.typography.titleLarge
            )

            Slider(
                value = settings.textScale,
                onValueChange = {
                    onUpdateSettings(
                        settings.copy(textScale = it)
                    )
                },
                valueRange = 0.85f..1.5f
            )

            SettingsSwitch(
                label = "Sound",
                checked = settings.soundEnabled,
                onChecked = {
                    onUpdateSettings(
                        settings.copy(soundEnabled = it)
                    )
                }
            )

            SettingsSwitch(
                label = "Show typing status",
                checked = settings.participantShowTypingStatus,
                onChecked = {
                    onUpdateSettings(
                        settings.copy(participantShowTypingStatus = it)
                    )
                }
            )

            SettingsSwitch(
                label = "High contrast",
                checked = settings.highContrastEnabled,
                onChecked = {
                    onUpdateSettings(
                        settings.copy(highContrastEnabled = it)
                    )
                }
            )
        }
    }
}

@Composable
private fun SettingsSwitch(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )

        Switch(
            checked = checked,
            onCheckedChange = onChecked
        )
    }
}

private fun previewParticipantSettingsUiState(): ParticipantUiState {
    return ParticipantUiState(
        user = UserEntity(
            id = "u1",
            displayName = "Alice",
            role = UserRole.PARTICIPANT,
            createdAt = 0
        ),
        settings = UserSettingsEntity(userId = "u1")
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun ParticipantSettingsScreenPhonePreview() {
    GroupAacTheme {
        ParticipantSettingsScreen(
            uiState = previewParticipantSettingsUiState(),
            onUpdateSettings = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Composable
fun ParticipantSettingsScreenTabletPortraitPreview() {
    GroupAacTheme {
        ParticipantSettingsScreen(
            uiState = previewParticipantSettingsUiState(),
            onUpdateSettings = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
fun ParticipantSettingsScreenTabletLandscapePreview() {
    GroupAacTheme {
        ParticipantSettingsScreen(
            uiState = previewParticipantSettingsUiState(),
            onUpdateSettings = {}
        )
    }
}
