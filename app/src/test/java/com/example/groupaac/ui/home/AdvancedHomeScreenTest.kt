package com.example.groupaac.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.model.CalendarViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class AdvancedHomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun liveSessionIsPrioritizedInHero() {
        composeRule.setContent {
            AdvancedHomeScreen(
                liveSessions = listOf(
                    session(
                        id = "live-1",
                        name = "Live Support Circle",
                        actualStartedAt = timeOf(2026, 7, 25, 13, 0),
                        scheduledStartAt = timeOf(2026, 7, 25, 13, 0),
                        scheduledDurationMinutes = 45
                    )
                ),
                upcomingSessions = listOf(
                    session(
                        id = "upcoming-1",
                        name = "Sunday Planning",
                        scheduledStartAt = timeOf(2026, 7, 26, 9, 0)
                    )
                ),
                calendarViewMode = CalendarViewMode.WEEK,
                managementError = null,
                onCreateSession = {},
                onJoinSession = {},
                onManageSessions = {},
                onOpenLiveSession = {},
                onStartScheduledSession = {}
            )
        }

        assertTrue(
            composeRule.onAllNodesWithText("Live now")
                .fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText("Live Support Circle")
                .fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun earliestUpcomingSessionShownWhenNoneAreLive() {
        composeRule.setContent {
            AdvancedHomeScreen(
                liveSessions = emptyList(),
                upcomingSessions = listOf(
                    session(
                        id = "later",
                        name = "Monday Debrief",
                        scheduledStartAt = timeOf(2026, 8, 3, 16, 0)
                    ),
                    session(
                        id = "earlier",
                        name = "Sunday Planning",
                        scheduledStartAt = timeOf(2026, 8, 2, 9, 0)
                    )
                ),
                calendarViewMode = CalendarViewMode.WEEK,
                managementError = null,
                onCreateSession = {},
                onJoinSession = {},
                onManageSessions = {},
                onOpenLiveSession = {},
                onStartScheduledSession = {}
            )
        }

        assertTrue(
            composeRule.onAllNodesWithText("Next session")
                .fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText("Sunday Planning")
                .fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun emptyHeroStateShownWhenThereAreNoSessions() {
        composeRule.setContent {
            AdvancedHomeScreen(
                liveSessions = emptyList(),
                upcomingSessions = emptyList(),
                calendarViewMode = CalendarViewMode.WEEK,
                managementError = null,
                onCreateSession = {},
                onJoinSession = {},
                onManageSessions = {},
                onOpenLiveSession = {},
                onStartScheduledSession = {}
            )
        }

        assertTrue(
            composeRule.onAllNodesWithText("No upcoming sessions")
                .fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun selectingDateFiltersVisibleSessionList() {
        composeRule.setContent {
            AdvancedHomeScreen(
                liveSessions = emptyList(),
                upcomingSessions = listOf(
                    session(
                        id = "today",
                        name = "Tuesday Check-in",
                        scheduledStartAt = timeOf(2026, 7, 28, 10, 0)
                    ),
                    session(
                        id = "future",
                        name = "Friday Planning",
                        scheduledStartAt = timeOf(2026, 7, 31, 11, 0)
                    )
                ),
                calendarViewMode = CalendarViewMode.MONTH,
                managementError = null,
                onCreateSession = {},
                onJoinSession = {},
                onManageSessions = {},
                onOpenLiveSession = {},
                onStartScheduledSession = {}
            )
        }

        assertTrue(
            composeRule.onAllNodesWithText("Tuesday Check-in")
                .fetchSemanticsNodes().isNotEmpty()
        )

            composeRule.onNodeWithTag("advanced_home_list")
            .performScrollToNode(hasTestTag("month_date_2026-07-31"))

        composeRule.onNodeWithTag("month_date_2026-07-31")
            .performClick()

        composeRule.onNodeWithTag("advanced_home_list")
            .performScrollToNode(hasText("Friday Planning"))

        assertEquals(
            0,
            composeRule.onAllNodesWithText("Tuesday Check-in")
                .fetchSemanticsNodes().size
        )
        assertTrue(
            composeRule.onAllNodesWithText("Friday Planning")
                .fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun monthDatesWithSessionsExposeAccessibleCountDescription() {
        composeRule.setContent {
            AdvancedHomeScreen(
                liveSessions = emptyList(),
                upcomingSessions = listOf(
                    session(
                        id = "future",
                        name = "Tuesday Planning",
                        scheduledStartAt = timeOf(2026, 7, 28, 11, 0)
                    ),
                    session(
                        id = "future-2",
                        name = "Tuesday Follow-up",
                        scheduledStartAt = timeOf(2026, 7, 28, 14, 0)
                    )
                ),
                calendarViewMode = CalendarViewMode.MONTH,
                managementError = null,
                onCreateSession = {},
                onJoinSession = {},
                onManageSessions = {},
                onOpenLiveSession = {},
                onStartScheduledSession = {}
            )
        }

        composeRule.onNodeWithTag("advanced_home_list")
            .performScrollToNode(hasTestTag("month_date_2026-07-28"))

        val node = composeRule.onNodeWithTag("month_date_2026-07-28")
            .fetchSemanticsNode()
        val descriptions = node.config[SemanticsProperties.ContentDescription]
        assertTrue(
            descriptions.any { description ->
                description.contains("2 sessions")
            }
        )
    }

    @Test
    fun pastSessionsAreNotShownOnHome() {
        composeRule.setContent {
            AdvancedHomeScreen(
                liveSessions = emptyList(),
                upcomingSessions = listOf(
                    session(
                        id = "past",
                        name = "Friday Review",
                        scheduledStartAt = timeOf(2026, 7, 27, 9, 0)
                    ),
                    session(
                        id = "future",
                        name = "Sunday Planning",
                        scheduledStartAt = timeOf(2026, 8, 2, 9, 0)
                    )
                ),
                calendarViewMode = CalendarViewMode.WEEK,
                managementError = null,
                onCreateSession = {},
                onJoinSession = {},
                onManageSessions = {},
                onOpenLiveSession = {},
                onStartScheduledSession = {}
            )
        }

        assertEquals(
            0,
            composeRule.onAllNodesWithText("Friday Review")
                .fetchSemanticsNodes().size
        )
        assertTrue(
            composeRule.onAllNodesWithText("Sunday Planning")
                .fetchSemanticsNodes().isNotEmpty()
        )
    }

    private fun session(
        id: String,
        name: String,
        scheduledStartAt: Long? = null,
        actualStartedAt: Long? = null,
        scheduledDurationMinutes: Int? = null
    ) = SessionEntity(
        id = id,
        name = name,
        joinCode = "1234-5678",
        hostUserId = "host-1",
        createdAt = timeOf(2026, 7, 25, 8, 0),
        scheduledStartAt = scheduledStartAt,
        scheduledDurationMinutes = scheduledDurationMinutes,
        actualStartedAt = actualStartedAt
    )

    private fun timeOf(
        year: Int,
        month: Int,
        dayOfMonth: Int,
        hour: Int,
        minute: Int
    ): Long {
        return LocalDateTime.of(year, month, dayOfMonth, hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
