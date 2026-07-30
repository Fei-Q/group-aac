package com.example.groupaac.ui.session

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.pi.SessionInvitationPayload
import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.SessionRole
import com.example.groupaac.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JoinSessionScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun initialState() {
        composeRule.setContent {
            JoinSessionScreen(
                currentUser = testUser(),
                participantLookupState = ParticipantLookupUiState.Idle,
                errorMessage = null,
                onLookupCodeChanged = {},
                onConfirmJoin = { _, _, _ -> }
            )
        }

        composeRule.onNodeWithTag("join_role_participant")
            .assertIsSelected()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(
                "join_session_preview_card"
            ).fetchSemanticsNodes().size
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(
                "join_session_lookup_resolving"
            ).fetchSemanticsNodes().size
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(
                "join_session_lookup_message"
            ).fetchSemanticsNodes().size
        )
    }

    @Test
    fun incompleteCode() {
        var latestCode = ""

        composeRule.setContent {
            JoinSessionScreen(
                currentUser = testUser(),
                participantLookupState = ParticipantLookupUiState.Idle,
                errorMessage = null,
                onLookupCodeChanged = {
                    latestCode = it
                },
                onConfirmJoin = { _, _, _ -> }
            )
        }

        composeRule.onNodeWithTag("join_session_code")
            .performTextInput("1234")

        assertEquals("1234", latestCode)
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(
                "join_session_lookup_resolving"
            ).fetchSemanticsNodes().size
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(
                "join_session_preview_card"
            ).fetchSemanticsNodes().size
        )
    }

    @Test
    fun resolving() {
        composeRule.setContent {
            JoinSessionScreen(
                currentUser = testUser(),
                participantLookupState =
                    ParticipantLookupUiState.Resolving(
                        codeDigits = "12345678"
                    ),
                errorMessage = null,
                onLookupCodeChanged = {},
                onConfirmJoin = { _, _, _ -> }
            )
        }

        composeRule.onNodeWithTag(
            "join_session_lookup_resolving"
        ).fetchSemanticsNode()
        composeRule.onNodeWithText(
            "Looking up session…"
        ).fetchSemanticsNode()
    }

    @Test
    fun successfulPreview() {
        composeRule.setContent {
            JoinSessionScreen(
                currentUser = testUser(),
                participantLookupState =
                    ParticipantLookupUiState.Preview(
                        preview = preview()
                    ),
                errorMessage = null,
                onLookupCodeChanged = {},
                onConfirmJoin = { _, _, _ -> }
            )
        }

        composeRule.onNodeWithTag(
            "join_session_preview_card"
        ).fetchSemanticsNode()
        composeRule.onNodeWithText(
            "Friday Group"
        ).fetchSemanticsNode()
        composeRule.onNodeWithText(
            "1234-5678"
        ).fetchSemanticsNode()
        composeRule.onNodeWithText(
            "Display pi-1"
        ).fetchSemanticsNode()
    }

    @Test
    fun notFound() {
        composeRule.setContent {
            JoinSessionScreen(
                currentUser = testUser(),
                participantLookupState =
                    ParticipantLookupUiState.NotFound(
                        codeDigits = "12345678"
                    ),
                errorMessage = null,
                onLookupCodeChanged = {},
                onConfirmJoin = { _, _, _ -> }
            )
        }

        composeRule.onNodeWithTag(
            "join_session_lookup_message"
        ).fetchSemanticsNode()
        composeRule.onNodeWithText(
            "No session found for code 1234-5678."
        ).fetchSemanticsNode()
    }

    @Test
    fun expired() {
        composeRule.setContent {
            JoinSessionScreen(
                currentUser = testUser(),
                participantLookupState =
                    ParticipantLookupUiState.Expired(
                        "This session invitation has expired."
                    ),
                errorMessage = null,
                onLookupCodeChanged = {},
                onConfirmJoin = { _, _, _ -> }
            )
        }

        composeRule.onNodeWithText(
            "This session invitation has expired."
        ).fetchSemanticsNode()
    }
}

private fun testUser() =
    UserEntity(
        uid = "user_1",
        displayName = "Alice",
        createdAt = 0L
    )

private fun preview(): ParticipantSessionPreview =
    ParticipantSessionPreview(
        invitation =
            SessionInvitationPayload(
                sessionId = "session-1",
                joinCode = "1234-5678",
                sessionName = "Friday Group",
                hostUserId = "host-1",
                displayId = "pi-1",
                status = SessionStatus.LIVE,
                displayMode = DisplayMode.AUTO_LATEST,
                actualStartedAt = 1L,
                expiresAt = Long.MAX_VALUE
            ),
        sessionName = "Friday Group",
        formattedCode = "1234-5678",
        startLabel = "Started 1:00 PM",
        displayIdentity = "Display pi-1",
        expiryWarning = null
    )
