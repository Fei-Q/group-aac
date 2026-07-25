package com.example.groupaac.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.entity.UserSettingsEntity
import com.example.groupaac.model.CalendarViewMode
import com.example.groupaac.model.HomeExperience
import com.example.groupaac.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsShowSectionsInOrderWithoutRoleOrTypingStatus() {
        composeRule.setContent {
            UserSettingsScreen(
                user = sampleUser(),
                settings = UserSettingsEntity(userId = "user-1"),
                onUpdateSettings = {},
                onClearLocalHistory = {},
                onExportSummary = {}
            )
        }

        assertEquals(
            listOf(
                "Profile",
                "Accessibility features",
                "Advanced settings"
            ),
            userSettingsSectionTitles
        )
        assertTrue(
            composeRule.onAllNodesWithText("Profile")
                .fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText("Accessibility features")
                .fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText("Advanced settings")
                .fetchSemanticsNodes().isNotEmpty()
        )

        assertEquals(
            0,
            composeRule.onAllNodesWithText("Role")
                .fetchSemanticsNodes().size
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Show typing status")
                .fetchSemanticsNodes().size
        )
    }

    @Test
    fun advancedManagementControlsAppearOnlyForAdvancedHomeExperience() {
        var homeExperience by mutableStateOf(HomeExperience.SIMPLE)
        composeRule.setContent {
            UserSettingsScreen(
                user = sampleUser(),
                settings = UserSettingsEntity(
                    userId = "user-1",
                    homeExperience = homeExperience
                ),
                onUpdateSettings = {},
                onClearLocalHistory = {},
                onExportSummary = {}
            )
        }

        assertEquals(
            0,
            composeRule.onAllNodesWithText("Facilitator alerts")
                .fetchSemanticsNodes().size
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Shared monitor")
                .fetchSemanticsNodes().size
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Save session history")
                .fetchSemanticsNodes().size
        )

        composeRule.runOnIdle {
            homeExperience = HomeExperience.ADVANCED
        }

        assertTrue(
            composeRule.onAllNodesWithText("Facilitator alerts")
                .fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText("Shared monitor")
                .fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText("Save session history")
                .fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun calendarViewModeDefaultsToWeekAndCanSwitchToMonth() {
        assertEquals(
            CalendarViewMode.WEEK,
            UserSettingsEntity(userId = "user-1").calendarViewMode
        )

        var updatedMode: CalendarViewMode? = null

        composeRule.setContent {
            UserSettingsScreen(
                user = sampleUser(),
                settings = UserSettingsEntity(
                    userId = "user-1",
                    homeExperience = HomeExperience.ADVANCED
                ),
                onUpdateSettings = {
                    updatedMode = it.calendarViewMode
                },
                onClearLocalHistory = {},
                onExportSummary = {}
            )
        }

        composeRule.onNodeWithTag("calendar_mode_month")
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                CalendarViewMode.MONTH,
                updatedMode
            )
        }
    }

    private fun sampleUser() = UserEntity(
        id = "user-1",
        displayName = "Alice Baker",
        role = UserRole.PARTICIPANT,
        createdAt = 0L
    )
}
