package com.example.groupaac.ui.session

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.example.groupaac.model.ActiveSession
import com.example.groupaac.model.SessionConnectionState
import com.example.groupaac.model.SessionRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class ActiveSessionHeaderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun participantHeaderShowsLeaveWithoutConnectedAndUsesDefaultDuration() {
        val session = participantSession(
            scheduledStartAt = null,
            scheduledDurationMinutes = null,
            actualStartedAt = null,
            joinedAt = sampleTimeMillis(2026, 7, 25, 15, 0)
        )
        composeRule.setContent {
            ActiveSessionHeader(
                activeSession = session,
                connectionState = SessionConnectionState.Connected(session),
                isFacilitator = false,
                onLeaveSession = {}
            )
        }

        assertEquals(
            0,
            composeRule.onAllNodesWithText("Connected")
                .fetchSemanticsNodes().size
        )
        assertTrue(
            composeRule.onAllNodesWithText("Leave")
                .fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText(
                "Sat, Jul 25 • 3:00 PM • 60 min"
            ).fetchSemanticsNodes().isNotEmpty()
        )

        val titleBounds = composeRule.onNodeWithText("Friday Group")
            .fetchSemanticsNode().boundsInRoot
        val leaveBounds = composeRule.onNodeWithText("Leave")
            .fetchSemanticsNode().boundsInRoot
        assertIsToRight(leaveBounds, titleBounds)
    }

    @Test
    fun participantHeaderUsesScheduledValuesWhenPresent() {
        val scheduledAt = sampleTimeMillis(2026, 7, 26, 9, 30)
        val session = participantSession(
            scheduledStartAt = scheduledAt,
            scheduledDurationMinutes = 45,
            actualStartedAt = sampleTimeMillis(2026, 7, 26, 9, 0),
            joinedAt = sampleTimeMillis(2026, 7, 26, 8, 45)
        )
        composeRule.setContent {
            ActiveSessionHeader(
                activeSession = session,
                connectionState = SessionConnectionState.Connected(session),
                isFacilitator = false,
                onLeaveSession = {}
            )
        }

        assertTrue(
            composeRule.onAllNodesWithText(
                "Sun, Jul 26 • 9:30 AM • 45 min"
            ).fetchSemanticsNodes().isNotEmpty()
        )
    }

    private fun participantSession(
        scheduledStartAt: Long?,
        scheduledDurationMinutes: Int?,
        actualStartedAt: Long?,
        joinedAt: Long
    ) = ActiveSession(
        sessionId = "session-1",
        joinCode = "1234-5678",
        sessionName = "Friday Group",
        userId = "participant-1",
        role = SessionRole.PARTICIPANT,
        joinedAt = joinedAt,
        scheduledStartAt = scheduledStartAt,
        scheduledDurationMinutes = scheduledDurationMinutes,
        actualStartedAt = actualStartedAt
    )

    private fun sampleTimeMillis(
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

    private fun assertIsToRight(
        left: Rect,
        rightOf: Rect
    ) {
        assertTrue(left.left > rightOf.left)
    }
}
