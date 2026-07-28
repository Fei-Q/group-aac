package com.example.groupaac

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.groupaac.data.dao.MessageWithSenderAndAttachments
import com.example.groupaac.data.entity.DisplayStateEntity
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionJoinRequestEntity
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.model.ActiveSession
import com.example.groupaac.model.CalendarViewMode
import com.example.groupaac.model.DisplayCommandOrigin
import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.HomeExperience
import com.example.groupaac.model.JoinRequestStatus
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.model.ParticipantOverview
import com.example.groupaac.model.SessionConnectionState
import com.example.groupaac.model.SessionRole
import com.example.groupaac.model.SignalState
import com.example.groupaac.model.SignalType
import com.example.groupaac.ui.account.CreateAccountScreen
import com.example.groupaac.ui.facilitator.FacilitatorUiState
import com.example.groupaac.ui.facilitator.ParticipantsScreen
import com.example.groupaac.ui.facilitator.SessionLogScreen
import com.example.groupaac.ui.home.AdvancedHomeScreen
import com.example.groupaac.ui.session.ActiveSessionHeader
import com.example.groupaac.ui.session.JoinSessionScreen
import com.example.groupaac.ui.theme.GroupAacTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Ignore(
    "Blocked on the current Android 17 preview emulator: Compose test rule queries fail " +
        "before execution because android.hardware.input.InputManager.getInstance is missing."
)
class RealtimeComposeFlowsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun accountCreationSubmitsTypedCallbackForValidUid() {
        val created = mutableListOf<Triple<String, String, HomeExperience>>()

        composeRule.setContent {
            GroupAacTheme {
                CreateAccountScreen(
                    onBack = {},
                    onCreate = { uid, displayName, homeExperience ->
                        created += Triple(uid, displayName, homeExperience)
                    }
                )
            }
        }

        composeRule.onNodeWithTag("create_account_uid")
            .performTextInput("Alice_12345678901234567890")
        composeRule.onNodeWithTag("create_account_display_name")
            .performTextInput("Alice")
        composeRule.onNodeWithText("Manage sessions").performClick()
        composeRule.onNodeWithTag("create_account_submit")
            .assertIsEnabled()
            .performClick()

        assertEquals(
            listOf(
                Triple("alice_123456789012345678", "Alice", HomeExperience.ADVANCED)
            ),
            created
        )
    }

    @Test
    fun hostSessionCreationCtaInvokesCallback() {
        var clicked = false

        composeRule.setContent {
            GroupAacTheme {
                AdvancedHomeScreen(
                    liveSessions = emptyList(),
                    upcomingSessions = emptyList(),
                    calendarViewMode = CalendarViewMode.MONTH,
                    managementError = null,
                    onCreateSession = { clicked = true },
                    onJoinSession = {},
                    onManageSessions = {},
                    onOpenLiveSession = {},
                    onStartScheduledSession = {}
                )
            }
        }

        composeRule.onNodeWithText("Create Session").performClick()
        assertTrue(clicked)
    }

    @Test
    fun participantJoinSubmitsEnteredCodeAndDisplayName() {
        val joins = mutableListOf<JoinCall>()

        composeRule.setContent {
            GroupAacTheme {
                JoinSessionScreen(
                    currentUser = sampleUser(uid = "participant_1", displayName = "Pat"),
                    isJoining = false,
                    errorMessage = null,
                    onJoin = { code, displayName, role, rememberProfile ->
                        joins += JoinCall(code, displayName, role, rememberProfile)
                    }
                )
            }
        }

        composeRule.onNodeWithTag("join_session_code")
            .performTextInput("1234-5678")
        composeRule.onNodeWithTag("join_session_display_name")
            .performTextInput(" Tester")
        composeRule.onNodeWithText("Join session").performClick()

        assertEquals(
            listOf(
                JoinCall(
                    code = "12345678",
                    displayName = "Pat Tester",
                    role = SessionRole.PARTICIPANT,
                    rememberProfile = false
                )
            ),
            joins
        )
    }

    @Test
    fun facilitatorApprovalInvokesApproveCallback() {
        val approvals = mutableListOf<String>()
        val request = SessionJoinRequestEntity(
            id = "req-1",
            sessionId = "session-1",
            userId = "facilitator_1",
            displayName = "Facilitator One",
            requestedRole = SessionRole.FACILITATOR,
            status = JoinRequestStatus.PENDING,
            requestedAt = 100L
        )

        composeRule.setContent {
            GroupAacTheme {
                ParticipantsScreen(
                    uiState = FacilitatorUiState(
                        isHost = true,
                        pendingJoinRequests = listOf(request)
                    ),
                    onSelect = {},
                    onApproveJoinRequest = { approvals += it },
                    onDeclineJoinRequest = {},
                    onSnooze = {},
                    onQuickLog = { _, _ -> },
                    onAddNote = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("facilitator_request_approve_req-1")
            .performClick()
        assertEquals(listOf("req-1"), approvals)
    }

    @Test
    fun hostOnlyEndSessionControlIsHiddenForNonHost() {
        val session = sampleActiveSession(role = SessionRole.FACILITATOR)
        composeRule.setContent {
            GroupAacTheme {
                ActiveSessionHeader(
                    activeSession = session,
                    connectionState = SessionConnectionState.Connected(session),
                    isFacilitator = true,
                    onLeaveSession = {},
                    onEndSession = null
                )
            }
        }

        composeRule.onAllNodesWithText("End").assertCountEquals(0)
    }

    @Test
    fun hostEndSessionControlConfirmsAndInvokesCallback() {
        var ended = false
        val session = sampleActiveSession(role = SessionRole.HOST)

        composeRule.setContent {
            GroupAacTheme {
                ActiveSessionHeader(
                    activeSession = session,
                    connectionState = SessionConnectionState.Connected(session),
                    isFacilitator = true,
                    onLeaveSession = {},
                    onEndSession = { ended = true }
                )
            }
        }

        composeRule.onNodeWithText("End").performClick()
        composeRule.onNodeWithText("End session").performClick()
        assertTrue(ended)
    }

    @Test
    fun signalSnoozeButtonsRemainParticipantSpecific() {
        val snoozed = mutableListOf<String>()

        composeRule.setContent {
            GroupAacTheme {
                ParticipantsScreen(
                    uiState = FacilitatorUiState(
                        participants = listOf(
                            ParticipantOverview(
                                userId = "participant_1",
                                displayName = "Alice",
                                activeSignal = SignalType.HELP,
                                signalState = SignalState.CURRENT,
                                messageCount = 1,
                                supportRequests = 1,
                                lastActivityLabel = "Typing",
                                elapsedLabel = "1m"
                            ),
                            ParticipantOverview(
                                userId = "participant_2",
                                displayName = "Bob",
                                activeSignal = SignalType.HELP,
                                signalState = SignalState.SNOOZED,
                                messageCount = 1,
                                supportRequests = 1,
                                lastActivityLabel = "Typing",
                                elapsedLabel = "1m"
                            )
                        )
                    ),
                    onSelect = {},
                    onApproveJoinRequest = {},
                    onDeclineJoinRequest = {},
                    onSnooze = { snoozed += it },
                    onQuickLog = { _, _ -> },
                    onAddNote = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Alice").performClick()
        composeRule.onNodeWithContentDescription("Snooze").performClick()
        assertEquals(listOf("participant_1"), snoozed)
        composeRule.onNodeWithContentDescription("Unsnooze").assertIsEnabled()
    }

    @Test
    fun pinUnpinAndClearControlsInvokeCallbacks() {
        val actions = mutableListOf<String>()
        val displayed = sampleDisplayedRow()

        composeRule.setContent {
            GroupAacTheme {
                SessionLogScreen(
                    uiState = FacilitatorUiState(
                        session = SessionEntity(
                            id = "session-1",
                            name = "Planning",
                            joinCode = "1234-5678",
                            hostUserId = "host_1",
                            createdAt = 1L
                        ),
                        messages = listOf(displayed),
                        displayedMessage = displayed,
                        displayState = DisplayStateEntity(
                            sessionId = "session-1",
                            currentMessageId = "msg-1",
                            isPinned = true,
                            displayMode = DisplayMode.AUTO_LATEST,
                            commandOrigin = DisplayCommandOrigin.MANUAL_SHOW,
                            updatedAt = 100L
                        )
                    ),
                    onSave = {},
                    onShow = {},
                    onRestore = {},
                    onDelete = {},
                    onPinDisplayedMessage = { actions += "pin" },
                    onUnpinDisplayedMessage = { actions += "unpin" },
                    onClearDisplay = { actions += "clear" }
                )
            }
        }

        composeRule.onNodeWithTag("display_pin_toggle").performClick()
        composeRule.onNodeWithTag("display_clear").performClick()

        assertEquals(listOf("unpin", "clear"), actions)
    }
}

private data class JoinCall(
    val code: String,
    val displayName: String,
    val role: SessionRole,
    val rememberProfile: Boolean
)

private fun sampleUser(
    uid: String,
    displayName: String
) = UserEntity(
    uid = uid,
    displayName = displayName,
    createdAt = 1L
)

private fun sampleActiveSession(role: SessionRole) = ActiveSession(
    sessionId = "session-1",
    joinCode = "1234-5678",
    sessionName = "Planning",
    role = role,
    userId = if (role == SessionRole.HOST) "host_1" else "facilitator_1",
    joinedAt = 1L
)

private fun sampleDisplayedRow() = MessageWithSenderAndAttachments(
    message = com.example.groupaac.data.dao.MessageWithSender(
        id = "msg-1",
        sessionId = "session-1",
        senderUserId = "participant_1",
        senderName = "Participant One",
        target = MessageTarget.GROUP,
        text = "Hello group",
        attachmentId = null,
        createdAt = 100L,
        status = com.example.groupaac.model.MessageStatus.ACTIVE,
        transportStatus = com.example.groupaac.model.MessageTransportStatus.SENT,
        displayStatus = com.example.groupaac.model.MessageDisplayStatus.DISPLAYED,
        saved = false,
        displayedOnMonitor = true
    ),
    attachments = emptyList()
)
