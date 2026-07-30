package com.example.groupaac.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.entity.UserSettingsEntity
import com.example.groupaac.model.CalendarViewMode
import com.example.groupaac.model.HomeExperience
import com.example.groupaac.ui.common.AppCard
import com.example.groupaac.ui.common.PrimaryButton
import com.example.groupaac.ui.common.SecondaryButton
import com.example.groupaac.ui.common.SettingsDivider
import com.example.groupaac.ui.common.SettingsNumberRow
import com.example.groupaac.ui.common.SettingsSection
import com.example.groupaac.ui.common.SettingsSliderRow
import com.example.groupaac.ui.common.SettingsSwitchRow
import com.example.groupaac.ui.theme.AacBackground
import com.example.groupaac.ui.theme.AacTextSecondary
import com.example.groupaac.ui.theme.GroupAacTheme

internal val userSettingsSectionTitles = listOf(
    "Profile",
    "Accessibility features",
    "Advanced settings"
)

@Composable
fun UserSettingsScreen(
    user: UserEntity?,
    settings: UserSettingsEntity,
    onUpdateSettings: (UserSettingsEntity) -> Unit,
    onClearLocalHistory: () -> Unit,
    onExportSummary: () -> Unit,
    modifier: Modifier = Modifier
) {
    var textScaleDraft by remember(settings.textScale) {
        mutableFloatStateOf(settings.textScale)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AacBackground)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 880.dp)
                .padding(horizontal = 24.dp, vertical = 24.dp),
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

        ProfileSection(
            user = user,
            modifier = Modifier
                .testTag("settings_profile")
                .fillMaxWidth()
                .widthIn(max = 880.dp)
                .padding(horizontal = 24.dp)
        )

        AccessibilitySection(
            settings = settings,
            textScaleDraft = textScaleDraft,
            onTextScaleDraftChange = {
                textScaleDraft = it
            },
            onTextScaleCommit = {
                onUpdateSettings(settings.copy(textScale = textScaleDraft))
            },
            onUpdateSettings = onUpdateSettings,
            modifier = Modifier
                .testTag("settings_accessibility")
                .fillMaxWidth()
                .widthIn(max = 880.dp)
                .padding(horizontal = 24.dp)
        )

        AdvancedSettingsSection(
            settings = settings,
            onUpdateSettings = onUpdateSettings,
            onClearLocalHistory = onClearLocalHistory,
            onExportSummary = onExportSummary,
            modifier = Modifier
                .testTag("settings_advanced")
                .fillMaxWidth()
                .widthIn(max = 880.dp)
                .padding(horizontal = 24.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 24.dp)
        )
    }
}

@Composable
private fun ProfileSection(
    user: UserEntity?,
    modifier: Modifier = Modifier
) {
    SettingsSection(
        title = "Profile",
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialsAvatar(
                displayName = user?.displayName ?: "Unknown"
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = user?.displayName ?: "Unknown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Account profile",
                    color = AacTextSecondary
                )
            }
        }
    }
}

@Composable
private fun InitialsAvatar(
    displayName: String
) {
    val initials = remember(displayName) {
        displayName
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "?" }
    }

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Color(0xFFDCE6F2))
            .semantics {
                contentDescription = "Profile avatar for $displayName"
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color(0xFF1E3A5F),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AccessibilitySection(
    settings: UserSettingsEntity,
    textScaleDraft: Float,
    onTextScaleDraftChange: (Float) -> Unit,
    onTextScaleCommit: () -> Unit,
    onUpdateSettings: (UserSettingsEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSection(
        title = "Accessibility features",
        modifier = modifier
    ) {
        SettingsSliderRow(
            title = "Text size",
            subtitle = "Adjust text throughout the app.",
            value = textScaleDraft,
            onValueChange = onTextScaleDraftChange,
            onValueChangeFinished = onTextScaleCommit,
            valueRange = 0.85f..1.5f,
            startLabel = "Smaller",
            endLabel = "Larger"
        )

        AppCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
        ) {
            Text(
                text = "Sample text preview",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * textScaleDraft
                ),
                textAlign = TextAlign.Start
            )
        }

        SettingsDivider()

        SettingsSwitchRow(
            title = "Sound",
            subtitle = "Play audio feedback where available.",
            checked = settings.soundEnabled,
            onCheckedChange = {
                onUpdateSettings(settings.copy(soundEnabled = it))
            }
        )

        SettingsDivider()

        SettingsSwitchRow(
            title = "High contrast",
            subtitle = "Increase visual contrast for text and controls.",
            checked = settings.highContrastEnabled,
            onCheckedChange = {
                onUpdateSettings(settings.copy(highContrastEnabled = it))
            }
        )

        SettingsDivider()

        SettingsSwitchRow(
            title = "Reduce motion",
            subtitle = "Limit motion-heavy transitions and effects.",
            checked = settings.reduceMotionEnabled,
            onCheckedChange = {
                onUpdateSettings(settings.copy(reduceMotionEnabled = it))
            }
        )

        SettingsDivider()

        SettingsSwitchRow(
            title = "Keep screen awake",
            subtitle = "Prevent the screen from sleeping during use.",
            checked = settings.keepScreenAwake,
            onCheckedChange = {
                onUpdateSettings(settings.copy(keepScreenAwake = it))
            }
        )
    }
}

@Composable
private fun AdvancedSettingsSection(
    settings: UserSettingsEntity,
    onUpdateSettings: (UserSettingsEntity) -> Unit,
    onClearLocalHistory: () -> Unit,
    onExportSummary: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSection(
        title = "Advanced settings",
        modifier = modifier
    ) {
        SettingsSwitchRow(
            title = "Show session management tools",
            subtitle = "Adds options to create, schedule, and manage sessions.",
            checked = settings.homeExperience == HomeExperience.ADVANCED,
            onCheckedChange = { enabled ->
                onUpdateSettings(
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

        if (settings.homeExperience == HomeExperience.ADVANCED) {
            SettingsDivider()
            SubsectionHeading("Calendar view")
            Text(
                text = "Choose how the calendar appears on Home.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CalendarViewModeSelector(
                selectedMode = settings.calendarViewMode,
                onModeSelected = { mode ->
                    onUpdateSettings(settings.copy(calendarViewMode = mode))
                }
            )

            SettingsDivider()
            SubsectionHeading("Facilitator alerts")
            SettingsSwitchRow(
                title = "Low participation alerts",
                subtitle = "Highlight participants who have not contributed recently.",
                checked = settings.facilitatorShowLowParticipationAlerts,
                onCheckedChange = {
                    onUpdateSettings(
                        settings.copy(
                            facilitatorShowLowParticipationAlerts = it
                        )
                    )
                }
            )
            SettingsNumberRow(
                title = "Low participation threshold",
                subtitle = "How long before a participant is flagged.",
                valueLabel = "${settings.facilitatorLowParticipationThresholdMinutes} min",
                onMinus = {
                    onUpdateSettings(
                        settings.copy(
                            facilitatorLowParticipationThresholdMinutes =
                                (settings.facilitatorLowParticipationThresholdMinutes - 5)
                                    .coerceAtLeast(5)
                        )
                    )
                },
                onPlus = {
                    onUpdateSettings(
                        settings.copy(
                            facilitatorLowParticipationThresholdMinutes =
                                (settings.facilitatorLowParticipationThresholdMinutes + 5)
                                    .coerceAtMost(60)
                        )
                    )
                }
            )

            SettingsDivider()
            SubsectionHeading("Shared monitor")
            SettingsSwitchRow(
                title = "Require approval before display",
                subtitle = "Messages must be manually displayed by the facilitator.",
                checked = settings.monitorRequireManualApproval,
                onCheckedChange = {
                    onUpdateSettings(
                        settings.copy(monitorRequireManualApproval = it)
                    )
                }
            )
            SettingsSwitchRow(
                title = "Show sender name",
                checked = settings.monitorShowSenderName,
                onCheckedChange = {
                    onUpdateSettings(
                        settings.copy(monitorShowSenderName = it)
                    )
                }
            )
            SettingsSwitchRow(
                title = "Clear monitor when session ends",
                checked = settings.monitorClearOnSessionEnd,
                onCheckedChange = {
                    onUpdateSettings(
                        settings.copy(monitorClearOnSessionEnd = it)
                    )
                }
            )

            SettingsDivider()
            SubsectionHeading("Data")
            SettingsSwitchRow(
                title = "Save session history",
                checked = settings.saveSessionHistory,
                onCheckedChange = {
                    onUpdateSettings(settings.copy(saveSessionHistory = it))
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SecondaryButton(
                    text = "Clear local history",
                    onClick = onClearLocalHistory,
                    modifier = Modifier.weight(1f)
                )
                PrimaryButton(
                    text = "Export summary report",
                    onClick = onExportSummary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SubsectionHeading(
    text: String
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun CalendarViewModeSelector(
    selectedMode: CalendarViewMode,
    onModeSelected: (CalendarViewMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CalendarModeOption(
            title = "Week",
            selected = selectedMode == CalendarViewMode.WEEK,
            testTag = "calendar_mode_week",
            modifier = Modifier.weight(1f),
            onClick = {
                onModeSelected(CalendarViewMode.WEEK)
            }
        )
        CalendarModeOption(
            title = "Month",
            selected = selectedMode == CalendarViewMode.MONTH,
            testTag = "calendar_mode_month",
            modifier = Modifier.weight(1f),
            onClick = {
                onModeSelected(CalendarViewMode.MONTH)
            }
        )
    }
}

@Composable
private fun CalendarModeOption(
    title: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .testTag(testTag),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.outline
            }
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun UserSettingsScreenSimplePreview() {
    GroupAacTheme {
        UserSettingsScreen(
            user = UserEntity(
                uid = "alice",
                displayName = "Alice Baker",
                createdAt = 0L
            ),
            settings = UserSettingsEntity(userId = "alice"),
            onUpdateSettings = {},
            onClearLocalHistory = {},
            onExportSummary = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 900, heightDp = 1000)
@Composable
private fun UserSettingsScreenAdvancedPreview() {
    GroupAacTheme {
        UserSettingsScreen(
            user = UserEntity(
                uid = "alice",
                displayName = "Alice Baker",
                createdAt = 0L
            ),
            settings = UserSettingsEntity(
                userId = "alice",
                homeExperience = HomeExperience.ADVANCED
            ),
            onUpdateSettings = {},
            onClearLocalHistory = {},
            onExportSummary = {}
        )
    }
}
