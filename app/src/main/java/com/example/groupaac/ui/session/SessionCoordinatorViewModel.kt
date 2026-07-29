package com.example.groupaac.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.pi.DisplayPairingPayloadCodec
import com.example.groupaac.data.pi.LaunchSessionResult
import com.example.groupaac.data.pi.SessionInvitationPayload
import com.example.groupaac.data.pi.SessionInvitationPayloadCodec
import com.example.groupaac.data.pi.validatedForJoin
import com.example.groupaac.data.realtime.RealtimeConnectionState
import com.example.groupaac.data.realtime.SessionSubscriptionCoordinator
import com.example.groupaac.data.repository.AccountRepository
import com.example.groupaac.data.repository.InvitationLookupResult
import com.example.groupaac.data.repository.SessionRepository
import com.example.groupaac.model.ActiveSession
import com.example.groupaac.model.JoinRequestStatus
import com.example.groupaac.model.JoinSessionResult
import com.example.groupaac.model.SessionConnectionState
import com.example.groupaac.model.SessionRole
import com.example.groupaac.util.TimeUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

sealed interface DisplayLaunchUiState {

    data object Idle : DisplayLaunchUiState

    data class AwaitingScan(
        val sessionId: String,
        val sessionName: String
    ) : DisplayLaunchUiState

    data class Connecting(
        val sessionId: String,
        val sessionName: String,
        val displayName: String
    ) : DisplayLaunchUiState

    data class Error(
        val sessionId: String,
        val sessionName: String,
        val message: String
    ) : DisplayLaunchUiState
}

sealed interface ParticipantLookupUiState {

    data object Idle : ParticipantLookupUiState

    data class Resolving(
        val codeDigits: String
    ) : ParticipantLookupUiState

    data class Preview(
        val preview: ParticipantSessionPreview
    ) : ParticipantLookupUiState

    data class NotFound(
        val codeDigits: String
    ) : ParticipantLookupUiState

    data class Expired(
        val message: String
    ) : ParticipantLookupUiState

    data class Invalid(
        val message: String
    ) : ParticipantLookupUiState

    data class Failure(
        val message: String
    ) : ParticipantLookupUiState

    data class Joining(
        val preview: ParticipantSessionPreview
    ) : ParticipantLookupUiState
}

data class ParticipantSessionPreview(
    val invitation: SessionInvitationPayload,
    val sessionName: String,
    val formattedCode: String,
    val startLabel: String?,
    val displayIdentity: String,
    val expiryWarning: String?
)

data class SessionCoordinatorUiState(
    val activeUser: UserEntity? = null,
    val connectionState: SessionConnectionState =
        SessionConnectionState.Restoring,
    val participantLookupState: ParticipantLookupUiState =
        ParticipantLookupUiState.Idle,
    val displayLaunchState: DisplayLaunchUiState =
        DisplayLaunchUiState.Idle,
    val errorMessage: String? = null
)

class SessionCoordinatorViewModel(
    private val accountRepository: AccountRepository,
    private val sessionRepository: SessionRepository,
    private val sessionSubscriptionCoordinator: SessionSubscriptionCoordinator
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SessionCoordinatorUiState()
    )
    val uiState: StateFlow<SessionCoordinatorUiState> =
        _uiState.asStateFlow()

    private val participantLookupCoordinator =
        ParticipantLookupCoordinator(
            scope = viewModelScope,
            lookupInvitationByCode =
                sessionRepository::lookupInvitation,
            decodeInvitation = {
                SessionInvitationPayloadCodec
                    .decode(it)
            },
            validateInvitation = { invitation ->
                invitation.validatedForJoin(
                    nowProvider = TimeUtils::now
                )
            },
            joinInvitation = {
                    invitation,
                    userId,
                    displayName,
                    role ->

                sessionRepository.joinInvitation(
                    invitation = invitation,
                    userId = userId,
                    displayName = displayName,
                    requestedRole = role
                )
            },
            updateDisplayName =
                accountRepository::updateDisplayName,
            loadLocalSession =
                sessionRepository::getSession,
            onStateChanged = { state ->
                _uiState.value =
                    _uiState.value.copy(
                        participantLookupState = state
                    )
            },
            onJoinStarted = { preview ->
                _uiState.value =
                    _uiState.value.copy(
                        connectionState =
                            SessionConnectionState.Joining(
                                preview.formattedCode
                            ),
                        errorMessage = null
                    )
            },
            onJoinSuccess = {
                    userId,
                    result ->

                when (result) {
                    is JoinSessionResult.Joined -> {
                        joinRequestJob?.cancel()
                        sessionSubscriptionCoordinator
                            .clearFacilitatorRequest(
                                userId = userId
                            )
                        _uiState.value =
                            _uiState.value.copy(
                                connectionState =
                                    SessionConnectionState
                                        .Connected(
                                            result.activeSession
                                        ),
                                errorMessage = null
                            )
                    }

                    is JoinSessionResult
                    .AwaitingApproval -> {
                        val request = result.request
                        sessionSubscriptionCoordinator
                            .trackFacilitatorRequest(
                                sessionId =
                                    request.sessionId,
                                userId = userId
                            )
                        val sessionName =
                            sessionRepository
                                .getSession(
                                    request.sessionId
                                )
                                ?.name
                                ?: "Group Meeting"
                        observeJoinRequest(
                            userId = userId,
                            requestId = request.id
                        )
                        _uiState.value =
                            _uiState.value.copy(
                                connectionState =
                                    SessionConnectionState
                                        .AwaitingApproval(
                                            requestId =
                                                request.id,
                                            sessionId =
                                                request.sessionId,
                                            sessionName =
                                                sessionName,
                                            requestedAt =
                                                request.requestedAt
                                        ),
                                errorMessage = null
                            )
                    }
                }
            },
            onJoinFailure = { userId, message ->
                sessionSubscriptionCoordinator
                    .clearFacilitatorRequest(
                        userId = userId
                    )
                _uiState.value =
                    _uiState.value.copy(
                        connectionState =
                            SessionConnectionState
                                .NotInSession,
                        errorMessage = message
                    )
            },
            nowProvider = TimeUtils::now
        )

    private var activeSessionJob: Job? = null
    private var joinRequestJob: Job? = null

    init {
        observeActiveUser()
        observeRealtimeConnection()
    }

    private fun observeActiveUser() {
        viewModelScope.launch {
            accountRepository.activeUserId
                .flatMapLatest { userId ->
                    if (userId == null) {
                        flowOf(null)
                    } else {
                        accountRepository.observeUser(userId)
                    }
                }
                .collect { user ->
                    activeSessionJob?.cancel()
                    joinRequestJob?.cancel()

                    _uiState.value = _uiState.value.copy(
                        activeUser = user,
                        connectionState = if (user == null) {
                            SessionConnectionState.NotInSession
                        } else {
                            SessionConnectionState.Restoring
                        },
                        participantLookupState =
                            ParticipantLookupUiState.Idle,
                        displayLaunchState =
                            DisplayLaunchUiState.Idle,
                        errorMessage = null
                    )

                    if (user != null) {
                        observeActiveSession(user.uid)
                    }
                }
        }
    }

    private fun observeActiveSession(userId: String) {
        activeSessionJob = viewModelScope.launch {
            sessionRepository
                .observeActiveSession(userId)
                .collect { activeSession ->
                    val currentState =
                        _uiState.value.connectionState

                    if (currentState is SessionConnectionState.Joining ||
                        currentState is SessionConnectionState.Leaving ||
                        currentState is SessionConnectionState.AwaitingApproval
                    ) {
                        return@collect
                    }

                    _uiState.value = _uiState.value.copy(
                        connectionState = if (activeSession == null) {
                            SessionConnectionState.NotInSession
                        } else {
                            connectionStateFor(
                                session = activeSession,
                                realtimeState =
                                    sessionSubscriptionCoordinator
                                        .connectionState
                                        .value
                            )
                        }
                    )
                }
        }
    }

    fun updateParticipantLookupCode(
        code: String
    ) {
        participantLookupCoordinator
            .onCodeChanged(code)
    }

    fun previewParticipantInvitation(
        scannedValue: String
    ) {
        participantLookupCoordinator
            .onQrScanned(scannedValue)
    }

    fun onParticipantQrScanCancelled() {
        participantLookupCoordinator
            .onQrScanCancelled()
    }

    fun onParticipantQrScanFailed(
        message: String
    ) {
        participantLookupCoordinator
            .onScannerFailure(message)
    }

    fun confirmParticipantJoin(
        displayName: String,
        sessionRole: SessionRole,
        rememberProfile: Boolean
    ) {
        val user = _uiState.value.activeUser ?: return

        participantLookupCoordinator
            .confirmJoin(
                user = user,
                displayName = displayName,
                sessionRole = sessionRole,
                rememberProfile = rememberProfile
            )
    }

    private fun observeJoinRequest(
        userId: String,
        requestId: String
    ) {
        joinRequestJob?.cancel()
        joinRequestJob = viewModelScope.launch {
            sessionRepository.observeJoinRequest(requestId)
                .collect { request ->
                    when (request?.status) {
                        JoinRequestStatus.PENDING -> {
                            val sessionName = sessionRepository
                                .getSession(request.sessionId)
                                ?.name
                                ?: "Group Meeting"
                            _uiState.value = _uiState.value.copy(
                                connectionState =
                                    SessionConnectionState.AwaitingApproval(
                                        requestId = request.id,
                                        sessionId = request.sessionId,
                                        sessionName = sessionName,
                                        requestedAt = request.requestedAt
                                    )
                            )
                        }

                        JoinRequestStatus.APPROVED -> {
                            val activeSession =
                                sessionRepository
                                    .activateApprovedFacilitatorRequest(
                                        requestId = requestId,
                                        userId = userId
                                    ) ?: return@collect
                            joinRequestJob?.cancel()
                            sessionSubscriptionCoordinator.clearFacilitatorRequest(
                                userId = userId,
                                sessionId = request.sessionId
                            )
                            _uiState.value = _uiState.value.copy(
                                connectionState =
                                    SessionConnectionState.Connected(
                                        activeSession
                                    ),
                                errorMessage = null
                            )
                        }

                        JoinRequestStatus.DECLINED -> {
                            joinRequestJob?.cancel()
                            sessionSubscriptionCoordinator.clearFacilitatorRequest(
                                userId = userId,
                                sessionId = request.sessionId
                            )
                            _uiState.value = _uiState.value.copy(
                                connectionState =
                                    SessionConnectionState.NotInSession,
                                participantLookupState =
                                    ParticipantLookupUiState.Idle,
                                errorMessage =
                                    "Facilitator request declined."
                            )
                        }

                        JoinRequestStatus.CANCELLED,
                        null -> {
                            joinRequestJob?.cancel()
                            sessionSubscriptionCoordinator.clearFacilitatorRequest(
                                userId = userId,
                                sessionId = request?.sessionId
                            )
                            _uiState.value = _uiState.value.copy(
                                connectionState =
                                    SessionConnectionState.NotInSession,
                                participantLookupState =
                                    ParticipantLookupUiState.Idle,
                                errorMessage = null
                            )
                        }
                    }
                }
        }
    }

    fun cancelFacilitatorRequest() {
        val state = _uiState.value.connectionState
        if (state !is SessionConnectionState.AwaitingApproval) {
            return
        }

        viewModelScope.launch {
            try {
                sessionRepository.cancelJoinRequest(state.requestId)

                joinRequestJob?.cancel()
                sessionSubscriptionCoordinator.clearFacilitatorRequest(
                    userId = _uiState.value.activeUser?.uid,
                    sessionId = state.sessionId
                )
                _uiState.value = _uiState.value.copy(
                    connectionState = SessionConnectionState.NotInSession,
                    participantLookupState =
                        ParticipantLookupUiState.Idle,
                    errorMessage = null
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage =
                        error.message
                            ?: "Unable to cancel facilitator request."
                )
            }
        }
    }

    fun createSession(
        name: String,
        displayName: String
    ) {
        val user =
            _uiState.value.activeUser
                ?: return

        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    connectionState =
                        SessionConnectionState.Joining(
                            null
                        ),
                    displayLaunchState =
                        DisplayLaunchUiState.Idle,
                    errorMessage = null
                )

            try {
                val draftSession =
                    sessionRepository.createSessionNow(
                    name = name,
                    ownerUserId = user.uid,
                    displayName = displayName
                )

                _uiState.value =
                    _uiState.value.copy(
                        connectionState =
                            SessionConnectionState
                                .NotInSession,
                        displayLaunchState =
                            DisplayLaunchUiState
                                .AwaitingScan(
                                    sessionId =
                                        draftSession.sessionId,
                                    sessionName =
                                        draftSession.sessionName
                                ),
                        errorMessage = null
                    )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value =
                    _uiState.value.copy(
                        connectionState =
                            SessionConnectionState
                                .NotInSession,
                        displayLaunchState =
                            DisplayLaunchUiState.Idle,
                        errorMessage =
                            error.message
                                ?: "Unable to create session."
                    )
            }
        }
    }

    fun requestDisplayLaunch(
        sessionId: String,
        sessionName: String
    ) {
        if (_uiState.value.activeUser == null) {
            return
        }

        _uiState.value =
            _uiState.value.copy(
                displayLaunchState =
                    DisplayLaunchUiState.AwaitingScan(
                        sessionId = sessionId,
                        sessionName = sessionName
                    ),
                errorMessage = null
            )
    }

    fun cancelDisplayLaunch() {
        val state =
            _uiState.value.displayLaunchState

        if (
            state is
                    DisplayLaunchUiState.Connecting
        ) {
            return
        }

        _uiState.value =
            _uiState.value.copy(
                displayLaunchState =
                    DisplayLaunchUiState.Idle
            )
    }

    fun launchSessionOnDisplay(
        scannedValue: String
    ) {
        val user =
            _uiState.value.activeUser
                ?: return

        val pendingLaunch =
            when (
                val state =
                    _uiState.value
                        .displayLaunchState
            ) {
                is DisplayLaunchUiState
                .AwaitingScan -> {
                    state.sessionId to
                        state.sessionName
                }

                is DisplayLaunchUiState.Error -> {
                    state.sessionId to
                        state.sessionName
                }

                DisplayLaunchUiState.Idle,
                is DisplayLaunchUiState
                .Connecting -> {
                    return
                }
            }

        val sessionId =
            pendingLaunch.first
        val sessionName =
            pendingLaunch.second

        val pairing =
            try {
                DisplayPairingPayloadCodec.decode(
                    scannedValue
                )
            } catch (error: Exception) {
                _uiState.value =
                    _uiState.value.copy(
                        displayLaunchState =
                            DisplayLaunchUiState.Error(
                                sessionId = sessionId,
                                sessionName = sessionName,
                                message =
                                    error.message
                                        ?: "This is not a valid display pairing QR code."
                            )
                    )

                return
            }

        _uiState.value =
            _uiState.value.copy(
                displayLaunchState =
                    DisplayLaunchUiState.Connecting(
                        sessionId = sessionId,
                        sessionName = sessionName,
                        displayName =
                            pairing.displayName
                    ),
                errorMessage = null
            )

        viewModelScope.launch {
            val result =
                try {
                    sessionRepository
                        .launchSessionOnDisplay(
                            sessionId = sessionId,
                            ownerUserId = user.uid,
                            pairing = pairing
                        )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    LaunchSessionResult.Failure(
                        error.message
                            ?: "Unable to launch the session."
                    )
                }

            when (result) {
                is LaunchSessionResult.Launched -> {
                    _uiState.value =
                        _uiState.value.copy(
                            connectionState =
                                SessionConnectionState
                                    .Connected(
                                        result.activeSession
                                    ),
                            displayLaunchState =
                                DisplayLaunchUiState.Idle,
                            errorMessage = null
                        )
                }

                LaunchSessionResult.PairingExpired -> {
                    setDisplayLaunchError(
                        sessionId = sessionId,
                        sessionName = sessionName,
                        message =
                            "This display QR code has expired. " +
                                "Scan the refreshed QR code shown by the display."
                    )
                }

                LaunchSessionResult.DisplayTimedOut -> {
                    setDisplayLaunchError(
                        sessionId = sessionId,
                        sessionName = sessionName,
                        message =
                            "The display did not respond. " +
                                "Check that it is online, then scan its refreshed QR code."
                    )
                }

                is LaunchSessionResult
                .DisplayRejected -> {
                    setDisplayLaunchError(
                        sessionId = sessionId,
                        sessionName = sessionName,
                        message =
                            displayRejectionMessage(
                                result.reason
                            )
                    )
                }

                LaunchSessionResult
                    .DirectoryCollision -> {
                    setDisplayLaunchError(
                        sessionId = sessionId,
                        sessionName = sessionName,
                        message =
                            "The session code was taken by another session. " +
                                "Scan the display's refreshed QR code to try again."
                    )
                }

                is LaunchSessionResult
                .DirectoryFailure -> {
                    setDisplayLaunchError(
                        sessionId = sessionId,
                        sessionName = sessionName,
                        message = result.message
                    )
                }

                is LaunchSessionResult.Failure -> {
                    setDisplayLaunchError(
                        sessionId = sessionId,
                        sessionName = sessionName,
                        message = result.message
                    )
                }
            }
        }
    }

    fun leaveSession() {
        val user = _uiState.value.activeUser ?: return
        val session = currentActiveSession() ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                connectionState =
                    SessionConnectionState.Leaving(
                        session
                    )
            )

            try {
                sessionRepository.leaveSession(
                    userId = user.uid,
                    sessionId = session.sessionId
                )
                _uiState.value = _uiState.value.copy(
                    connectionState =
                        SessionConnectionState.NotInSession,
                    participantLookupState =
                        ParticipantLookupUiState.Idle
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    connectionState =
                        SessionConnectionState.Connected(
                            session
                        ),
                    errorMessage =
                        error.message
                            ?: "Unable to leave session."
                )
            }
        }
    }

    fun endSession() {
        val session = currentActiveSession() ?: return

        viewModelScope.launch {
            try {
                sessionRepository.endSession(
                    sessionId = session.sessionId,
                    actorUserId = session.userId
                )
                sessionRepository.leaveSession(
                    userId = session.userId,
                    sessionId = session.sessionId
                )
                _uiState.value = _uiState.value.copy(
                    connectionState =
                        SessionConnectionState.NotInSession,
                    participantLookupState =
                        ParticipantLookupUiState.Idle
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage =
                        error.message
                            ?: "Unable to end session."
                )
            }
        }
    }

    fun onRemoteSessionEnded(sessionId: String) {
        val session = currentActiveSession() ?: return

        if (session.sessionId != sessionId) {
            return
        }

        viewModelScope.launch {
            sessionRepository.leaveSession(
                userId = session.userId,
                sessionId = session.sessionId
            )
            _uiState.value = _uiState.value.copy(
                connectionState =
                    SessionConnectionState.NotInSession,
                participantLookupState =
                    ParticipantLookupUiState.Idle
            )
        }
    }

    private fun setDisplayLaunchError(
        sessionId: String,
        sessionName: String,
        message: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                connectionState =
                    SessionConnectionState.NotInSession,
                displayLaunchState =
                    DisplayLaunchUiState.Error(
                        sessionId = sessionId,
                        sessionName = sessionName,
                        message = message
                    )
            )
    }

    private fun displayRejectionMessage(
        reason: String?
    ): String {
        return when (reason) {
            "display_already_bound" -> {
                "This display is already connected to another session."
            }

            "pairing_expired" -> {
                "The display pairing QR code has expired. Scan the refreshed code."
            }

            "pairing_nonce_mismatch",
            "pairing_expiry_mismatch" -> {
                "The display has refreshed its pairing code. Scan the new QR code."
            }

            "display_id_mismatch" -> {
                "The QR code does not match the selected display."
            }

            "session_subscription_failed" -> {
                "The display could not connect to the session channel."
            }

            null,
            "" -> {
                "The display rejected the connection."
            }

            else -> {
                "The display rejected the connection: " +
                    reason.replace(
                        oldChar = '_',
                        newChar = ' '
                    )
            }
        }
    }

    private fun currentActiveSession(): ActiveSession? =
        when (
            val state = _uiState.value.connectionState
        ) {
            is SessionConnectionState.Connected ->
                state.session

            is SessionConnectionState.Reconnecting ->
                state.session

            is SessionConnectionState.Leaving ->
                state.session

            else -> null
        }

    private fun observeRealtimeConnection() {
        viewModelScope.launch {
            sessionSubscriptionCoordinator.connectionState.collect { realtimeState ->
                val currentState = _uiState.value.connectionState
                if (
                    currentState is SessionConnectionState.Joining ||
                    currentState is SessionConnectionState.AwaitingApproval ||
                    currentState is SessionConnectionState.Leaving
                ) {
                    return@collect
                }

                val session = currentActiveSession() ?: return@collect
                _uiState.value = _uiState.value.copy(
                    connectionState = connectionStateFor(
                        session,
                        realtimeState
                    )
                )
            }
        }
    }

    private fun connectionStateFor(
        session: ActiveSession,
        realtimeState: RealtimeConnectionState
    ): SessionConnectionState {
        return when (realtimeState) {
            RealtimeConnectionState.Connected ->
                SessionConnectionState.Connected(
                    session
                )

            RealtimeConnectionState.Connecting,
            RealtimeConnectionState.Reconnecting,
            RealtimeConnectionState.Disconnected ->
                SessionConnectionState.Reconnecting(
                    session
                )

            is RealtimeConnectionState.Failed ->
                SessionConnectionState.Reconnecting(
                    session = session,
                    reason = realtimeState.message
                )
        }
    }
}

internal class ParticipantLookupCoordinator(
    private val scope: CoroutineScope,
    private val lookupInvitationByCode:
        suspend (String) -> InvitationLookupResult,
    private val decodeInvitation:
        (String) -> SessionInvitationPayload,
    private val validateInvitation:
        (SessionInvitationPayload) -> SessionInvitationPayload,
    private val joinInvitation:
        suspend (
            SessionInvitationPayload,
            String,
            String,
            SessionRole
        ) -> JoinSessionResult,
    private val updateDisplayName:
        suspend (String, String) -> Unit,
    private val loadLocalSession:
        suspend (String) -> SessionEntity?,
    private val onStateChanged:
        (ParticipantLookupUiState) -> Unit,
    private val onJoinStarted:
        (ParticipantSessionPreview) -> Unit,
    private val onJoinSuccess:
        suspend (String, JoinSessionResult) -> Unit,
    private val onJoinFailure:
        (String, String) -> Unit,
    private val nowProvider: () -> Long
) {
    private var state: ParticipantLookupUiState =
        ParticipantLookupUiState.Idle
    private var lookupJob: Job? = null
    private var lastLookupCode: String? = null

    fun onCodeChanged(
        code: String
    ) {
        val digits =
            code.filter(Char::isDigit)
                .take(8)

        if (digits.length < 8) {
            lookupJob?.cancel()
            lastLookupCode = null
            updateState(
                ParticipantLookupUiState.Idle
            )
            return
        }

        if (digits == lastLookupCode) {
            return
        }

        lastLookupCode = digits
        lookupJob?.cancel()
        updateState(
            ParticipantLookupUiState.Resolving(
                codeDigits = digits
            )
        )

        lookupJob = scope.launch {
            when (
                val result =
                    lookupInvitationByCode(
                        digits
                    )
            ) {
                is InvitationLookupResult.Found -> {
                    updateState(
                        ParticipantLookupUiState
                            .Preview(
                                preview =
                                    buildPreview(
                                        invitation =
                                            result
                                                .invitation,
                                        localSession =
                                            loadLocalSession(
                                                result
                                                    .invitation
                                                    .sessionId
                                            ),
                                        nowProvider =
                                            nowProvider
                                    )
                            )
                    )
                }

                InvitationLookupResult.NotFound -> {
                    updateState(
                        ParticipantLookupUiState
                            .NotFound(
                                codeDigits = digits
                            )
                    )
                }

                InvitationLookupResult.Expired -> {
                    updateState(
                        ParticipantLookupUiState
                            .Expired(
                                "This session invitation has expired."
                            )
                    )
                }

                is InvitationLookupResult.Invalid -> {
                    updateState(
                        ParticipantLookupUiState
                            .Invalid(
                                result.message
                            )
                    )
                }

                is InvitationLookupResult.Failure -> {
                    updateState(
                        ParticipantLookupUiState
                            .Failure(
                                result.message
                            )
                    )
                }
            }
        }
    }

    fun onQrScanned(
        scannedValue: String
    ) {
        lookupJob?.cancel()
        lastLookupCode = null

        val invitation =
            try {
                validateInvitation(
                    decodeInvitation(
                        scannedValue
                    )
                )
            } catch (
                error: CancellationException
            ) {
                throw error
            } catch (error: Exception) {
                updateState(
                    ParticipantLookupUiState
                        .Invalid(
                            error.message
                                ?: "This is not a valid session QR code."
                        )
                )
                return
            }

        lookupJob = scope.launch {
            updateState(
                ParticipantLookupUiState
                    .Preview(
                        preview =
                            buildPreview(
                                invitation = invitation,
                                localSession =
                                    loadLocalSession(
                                        invitation
                                            .sessionId
                                    ),
                                nowProvider =
                                    nowProvider
                            )
                    )
            )
        }
    }

    fun onQrScanCancelled() = Unit

    fun onScannerFailure(
        message: String
    ) {
        updateState(
            ParticipantLookupUiState.Failure(
                message
            )
        )
    }

    fun confirmJoin(
        user: UserEntity,
        displayName: String,
        sessionRole: SessionRole,
        rememberProfile: Boolean
    ) {
        val preview =
            when (val currentState = state) {
                is ParticipantLookupUiState
                .Preview -> {
                    currentState.preview
                }

                else -> return
            }

        lookupJob?.cancel()
        updateState(
            ParticipantLookupUiState.Joining(
                preview = preview
            )
        )
        onJoinStarted(preview)

        scope.launch {
            try {
                if (rememberProfile) {
                    updateDisplayName(
                        user.uid,
                        displayName
                    )
                }

                val result =
                    joinInvitation(
                        preview.invitation,
                        user.uid,
                        displayName,
                        sessionRole
                    )

                updateState(
                    ParticipantLookupUiState.Idle
                )
                onJoinSuccess(
                    user.uid,
                    result
                )
            } catch (
                error: CancellationException
            ) {
                throw error
            } catch (error: Exception) {
                val message =
                    error.message
                        ?: "Unable to join session."
                updateState(
                    ParticipantLookupUiState.Failure(
                        message
                    )
                )
                onJoinFailure(
                    user.uid,
                    message
                )
            }
        }
    }

    private fun updateState(
        next: ParticipantLookupUiState
    ) {
        state = next
        onStateChanged(next)
    }
}

internal fun buildPreview(
    invitation: SessionInvitationPayload,
    localSession: SessionEntity?,
    nowProvider: () -> Long
): ParticipantSessionPreview {
    val startTime =
        invitation.actualStartedAt
            .takeIf { it > 0L }
            ?: localSession?.scheduledStartAt

    val startLabel =
        startTime?.let { millis ->
            val prefix =
                if (invitation.actualStartedAt > 0L) {
                    "Started"
                } else {
                    "Scheduled"
                }
            "$prefix ${formatClockTime(millis)}"
        }

    val expiryWarning =
        if (
            invitation.expiresAt - nowProvider() <=
            10 * 60_000L
        ) {
            "Expires soon"
        } else {
            null
        }

    return ParticipantSessionPreview(
        invitation = invitation,
        sessionName = invitation.sessionName,
        formattedCode = invitation.joinCode,
        startLabel = startLabel,
        displayIdentity =
            "Display ${invitation.displayId}",
        expiryWarning = expiryWarning
    )
}

private fun formatClockTime(
    timeMillis: Long
): String {
    return DateTimeFormatter
        .ofLocalizedTime(
            FormatStyle.SHORT
        )
        .withZone(ZoneId.systemDefault())
        .format(
            Instant.ofEpochMilli(timeMillis)
        )
}

class SessionCoordinatorViewModelFactory(
    private val accountRepository: AccountRepository,
    private val sessionRepository: SessionRepository,
    private val sessionSubscriptionCoordinator: SessionSubscriptionCoordinator
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        return SessionCoordinatorViewModel(
            accountRepository = accountRepository,
            sessionRepository = sessionRepository,
            sessionSubscriptionCoordinator = sessionSubscriptionCoordinator
        ) as T
    }
}
