package com.example.groupaac.ui.session

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.model.UserRole
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
    fun joinScreenDefaultsToParticipantAndRemovesObsoleteText() {
        composeRule.setContent {
            JoinSessionScreen(
                currentUser = UserEntity(
                    id = "user-1",
                    displayName = "Alice",
                    role = UserRole.PARTICIPANT,
                    createdAt = 0L
                ),
                isJoining = false,
                errorMessage = null,
                onJoin = { _, _, _, _ -> }
            )
        }

        composeRule.onNodeWithTag("join_role_participant")
            .assertIsSelected()
        assertEquals(
            0,
            composeRule.onAllNodesWithText(
                "The code is checked when you tap Join session."
            ).fetchSemanticsNodes().size
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithText(
                "Remember my settings for this group."
            ).fetchSemanticsNodes().size
        )
    }
}
