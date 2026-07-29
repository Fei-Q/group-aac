package com.example.groupaac.ui.session

import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.pi.SessionInvitationPayload
import com.example.groupaac.data.pi.SessionInvitationPayloadCodec
import com.example.groupaac.data.pi.validatedForJoin
import com.example.groupaac.data.repository.InvitationLookupResult
import com.example.groupaac.model.ActiveSession
import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.JoinSessionResult
import com.example.groupaac.model.SessionRole
import com.example.groupaac.model.SessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ParticipantLookupCoordinatorTest {

    @Test
    fun malformedQr() = runTest {
        val states = mutableListOf<ParticipantLookupUiState>()
        val coordinator =
            coordinator(
                scope = this,
                stateSink = states
            )

        coordinator.onQrScanned("not-json")
        advanceUntilIdle()

        assertTrue(
            states.last() is ParticipantLookupUiState.Invalid
        )
    }

    @Test
    fun qrPreviewWithoutDirectory() = runTest {
        val states = mutableListOf<ParticipantLookupUiState>()
        var lookupCount = 0
        val coordinator =
            coordinator(
                scope = this,
                stateSink = states,
                lookupInvitationByCode = {
                    lookupCount++
                    InvitationLookupResult.NotFound
                }
            )

        coordinator.onQrScanned(
            SessionInvitationPayloadCodec.encode(
                invitation()
            )
        )
        advanceUntilIdle()

        assertEquals(0, lookupCount)
        assertTrue(
            states.last() is ParticipantLookupUiState.Preview
        )
    }

    @Test
    fun participantConfirmation() = runTest {
        val roles = mutableListOf<SessionRole>()
        var success: JoinSessionResult? = null
        val coordinator =
            coordinator(
                scope = this,
                joinInvitation = { _, _, _, role ->
                    roles += role
                    JoinSessionResult.Joined(
                        ActiveSession(
                            sessionId = "session-1",
                            joinCode = "1234-5678",
                            sessionName = "Friday Group",
                            userId = "user-1",
                            role = role,
                            joinedAt = 1L,
                            actualStartedAt = 1L
                        )
                    )
                },
                onJoinSuccess = { _, result ->
                    success = result
                }
            )

        coordinator.onQrScanned(
            SessionInvitationPayloadCodec.encode(
                invitation()
            )
        )
        advanceUntilIdle()

        coordinator.confirmJoin(
            user = user(),
            displayName = "Alice",
            sessionRole = SessionRole.PARTICIPANT,
            rememberProfile = false
        )
        advanceUntilIdle()

        assertEquals(
            listOf(SessionRole.PARTICIPANT),
            roles
        )
        assertTrue(success is JoinSessionResult.Joined)
    }

    @Test
    fun facilitatorConfirmation() = runTest {
        val roles = mutableListOf<SessionRole>()
        var success: JoinSessionResult? = null
        val coordinator =
            coordinator(
                scope = this,
                joinInvitation = { _, _, _, role ->
                    roles += role
                    JoinSessionResult.AwaitingApproval(
                        request =
                            com.example.groupaac.data.entity.SessionJoinRequestEntity(
                                id = "request-1",
                                sessionId = "session-1",
                                userId = "user-1",
                                displayName = "Alice",
                                requestedRole = role,
                                status = com.example.groupaac.model.JoinRequestStatus.PENDING,
                                requestedAt = 1L
                            )
                    )
                },
                onJoinSuccess = { _, result ->
                    success = result
                }
            )

        coordinator.onQrScanned(
            SessionInvitationPayloadCodec.encode(
                invitation()
            )
        )
        advanceUntilIdle()

        coordinator.confirmJoin(
            user = user(),
            displayName = "Alice",
            sessionRole = SessionRole.FACILITATOR,
            rememberProfile = false
        )
        advanceUntilIdle()

        assertEquals(
            listOf(SessionRole.FACILITATOR),
            roles
        )
        assertTrue(success is JoinSessionResult.AwaitingApproval)
    }

    @Test
    fun scannerCancellation() = runTest {
        val states = mutableListOf<ParticipantLookupUiState>()
        val coordinator =
            coordinator(
                scope = this,
                stateSink = states
            )

        coordinator.onQrScanned(
            SessionInvitationPayloadCodec.encode(
                invitation()
            )
        )
        advanceUntilIdle()
        val beforeCancel = states.last()

        coordinator.onQrScanCancelled()
        advanceUntilIdle()

        assertEquals(beforeCancel, states.last())
    }
}

private fun coordinator(
    scope: kotlinx.coroutines.CoroutineScope,
    stateSink: MutableList<ParticipantLookupUiState> = mutableListOf(),
    lookupInvitationByCode: suspend (String) -> InvitationLookupResult = {
        InvitationLookupResult.Found(
            invitation().validatedForJoin(
                nowProvider = { 1L }
            )
        )
    },
    joinInvitation: suspend (
        SessionInvitationPayload,
        String,
        String,
        SessionRole
    ) -> JoinSessionResult = { _, _, _, role ->
        JoinSessionResult.Joined(
            ActiveSession(
                sessionId = "session-1",
                joinCode = "1234-5678",
                sessionName = "Friday Group",
                userId = "user-1",
                role = role,
                joinedAt = 1L,
                actualStartedAt = 1L
            )
        )
    },
    onJoinSuccess: suspend (String, JoinSessionResult) -> Unit = { _, _ -> }
): ParticipantLookupCoordinator = ParticipantLookupCoordinator(
    scope = scope,
    lookupInvitationByCode = lookupInvitationByCode,
    decodeInvitation = { SessionInvitationPayloadCodec.decode(it) },
    validateInvitation = { it.validatedForJoin(nowProvider = { 1L }) },
    joinInvitation = joinInvitation,
    updateDisplayName = { _, _ -> },
    loadLocalSession = { null },
    onStateChanged = { stateSink += it },
    onJoinStarted = { stateSink += ParticipantLookupUiState.Joining(it) },
    onJoinSuccess = onJoinSuccess,
    onJoinFailure = { _, _ -> },
    nowProvider = { 1L }
)

private fun invitation() =
    SessionInvitationPayload(
        sessionId = "session-1",
        joinCode = "1234-5678",
        sessionName = "Friday Group",
        hostUserId = "host-1",
        displayId = "pi-1",
        status = SessionStatus.LIVE,
        displayMode = DisplayMode.AUTO_LATEST,
        actualStartedAt = 1_000L,
        expiresAt = Long.MAX_VALUE
    )

private fun user() =
    UserEntity(
        uid = "user-1",
        displayName = "Alice",
        createdAt = 0L
    )
