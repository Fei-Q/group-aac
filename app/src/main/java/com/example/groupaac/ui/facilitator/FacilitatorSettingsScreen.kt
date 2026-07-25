package com.example.groupaac.ui.facilitator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.groupaac.data.entity.UserSettingsEntity
import com.example.groupaac.model.HomeExperience
import com.example.groupaac.ui.common.SecondaryButton
import com.example.groupaac.ui.common.SettingsNumberRow
import com.example.groupaac.ui.common.SettingsSection
import com.example.groupaac.ui.common.SettingsSwitchRow

@Composable
fun FacilitatorSettingsScreen(
    settings: UserSettingsEntity,
    onSettingsChange: (UserSettingsEntity) -> Unit,
    onClearLocalHistory: () -> Unit,
    onExportSummary: () -> Unit,
    modifier: Modifier
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            SettingsSection(title = "Home") {
                SettingsSwitchRow(
                    title = "Show session management tools",
                    subtitle = "Adds options to create, schedule, and manage sessions.",
                    checked = settings.homeExperience == HomeExperience.ADVANCED,
                    onCheckedChange = { enabled ->
                        onSettingsChange(
                            settings.copy(
                                homeExperience = if (enabled) {
                                    HomeExperience.ADVANCED
                                } else {
                                    HomeExperience.SIMPLE
                                }
                            )
                        )
                    }
                )
            }
        }

        item {
            SettingsSection(title = "Accessibility") {
                SettingsSwitchRow(
                    title = "Sound",
                    subtitle = "Play feedback sounds during the session.",
                    checked = settings.soundEnabled,
                    onCheckedChange = {
                        onSettingsChange(settings.copy(soundEnabled = it))
                    }
                )

                SettingsSwitchRow(
                    title = "Keep screen awake",
                    subtitle = "Prevent the facilitator tablet from sleeping during a session.",
                    checked = settings.keepScreenAwake,
                    onCheckedChange = {
                        onSettingsChange(settings.copy(keepScreenAwake = it))
                    }
                )
            }
        }

        item {
            SettingsSection(title = "Facilitator alerts") {
                SettingsSwitchRow(
                    title = "Low participation alerts",
                    subtitle = "Highlight participants who have not contributed recently.",
                    checked = settings.facilitatorShowLowParticipationAlerts,
                    onCheckedChange = {
                        onSettingsChange(
                            settings.copy(facilitatorShowLowParticipationAlerts = it)
                        )
                    }
                )

                SettingsNumberRow(
                    title = "Low participation threshold",
                    subtitle = "How long before a participant is flagged.",
                    valueLabel = "${settings.facilitatorLowParticipationThresholdMinutes} min",
                    onMinus = {
                        onSettingsChange(
                            settings.copy(
                                facilitatorLowParticipationThresholdMinutes =
                                    (settings.facilitatorLowParticipationThresholdMinutes - 5)
                                        .coerceAtLeast(5)
                            )
                        )
                    },
                    onPlus = {
                        onSettingsChange(
                            settings.copy(
                                facilitatorLowParticipationThresholdMinutes =
                                    (settings.facilitatorLowParticipationThresholdMinutes + 5)
                                        .coerceAtMost(60)
                            )
                        )
                    }
                )
            }
        }

        item {
            SettingsSection(title = "Shared monitor") {
                SettingsSwitchRow(
                    title = "Require approval before display",
                    subtitle = "Messages must be manually displayed by the facilitator.",
                    checked = settings.monitorRequireManualApproval,
                    onCheckedChange = {
                        onSettingsChange(settings.copy(monitorRequireManualApproval = it))
                    }
                )

                SettingsSwitchRow(
                    title = "Show sender name",
                    checked = settings.monitorShowSenderName,
                    onCheckedChange = {
                        onSettingsChange(settings.copy(monitorShowSenderName = it))
                    }
                )

                SettingsSwitchRow(
                    title = "Clear monitor when session ends",
                    checked = settings.monitorClearOnSessionEnd,
                    onCheckedChange = {
                        onSettingsChange(settings.copy(monitorClearOnSessionEnd = it))
                    }
                )
            }
        }

        item {
            SettingsSection(title = "Data") {
                SettingsSwitchRow(
                    title = "Save session history",
                    checked = settings.saveSessionHistory,
                    onCheckedChange = {
                        onSettingsChange(settings.copy(saveSessionHistory = it))
                    }
                )

                SecondaryButton(
                    text = "Clear local history",
                    onClick = onClearLocalHistory,
                    modifier = Modifier.fillMaxWidth()
                )

                SecondaryButton(
                    text = "Export summary report",
                    onClick = onExportSummary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
