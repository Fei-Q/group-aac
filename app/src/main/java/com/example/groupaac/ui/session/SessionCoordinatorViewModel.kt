package com.example.groupaac.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.realtime.RealtimeConnectionState
import com.example.groupaac.data.realtime.SessionSubscriptionCoordinator
import com.example.groupaac.data.repository.AccountRepository
import com.example.groupaac.data.repository.SessionRepository
import com.example.groupaac.model.ActiveSession
import com.example.groupaac.model.JoinRequestStatus
import com.example.groupaac.model.JoinSessionResult
import com.example.groupaac.model.SessionRole
import com.example.groupaac.model.SessionConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

data class SessionCoordinatorUiState(
    val activeUser: UserEntity? = null,
    val connectionState: SessionConnectionState =
        SessionConnectionState.Restoring,
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

    fun joinSession(
        code: String,
        displayName: String,
        sessionRole: SessionRole,
        rememberProfile: Boolean
    ) {
        val user = _uiState.value.activeUser ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                connectionState =
                    SessionConnectionState.Joining(code),
                errorMessage = null
            )

            runCatching {
                if (rememberProfile) {
                    accountRepository.updateDisplayName(
                        userId = user.uid,
                        displayName = displayName
                    )
                }

                sessionRepository.joinSession(
                    joinCode = code,
                    userId = user.uid,
                    displayName = displayName,
                    requestedRole = sessionRole
                )
            }.onSuccess { result ->
                when (result) {
                    is JoinSessionResult.Joined -> {
                        joinRequestJob?.cancel()
                        sessionSubscriptionCoordinator.clearFacilitatorRequest(
                            userId = user.uid
                        )
                        _uiState.value = _uiState.value.copy(
                            connectionState =
                                SessionConnectionState.Connected(
                                    result.activeSession
                                )
                        )
                    }

                    is JoinSessionResult.AwaitingApproval -> {
                        val request = result.request
                        sessionSubscriptionCoordinator.trackFacilitatorRequest(
                            sessionId = request.sessionId,
                            userId = user.uid
                        )
                        val sessionName = sessionRepository
                            .getSession(request.sessionId)
                            ?.name
                            ?: "Group Meeting"
                        observeJoinRequest(
                            userId = user.uid,
                            requestId = request.id
                        )
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
                }
            }.onFailure { error ->
                sessionSubscriptionCoordinator.clearFacilitatorRequest(
                    userId = user.uid
                )
                _uiState.value = _uiState.value.copy(
                    connectionState =
                        SessionConnectionState.NotInSession,
                    errorMessage =
                        error.message ?: "Unable to join session."
                )
            }
        }
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
            runCatching {
                sessionRepository.cancelJoinRequest(state.requestId)
            }.onSuccess {
                joinRequestJob?.cancel()
                sessionSubscriptionCoordinator.clearFacilitatorRequest(
                    userId = _uiState.value.activeUser?.uid,
                    sessionId = state.sessionId
                )
                _uiState.value = _uiState.value.copy(
                    connectionState = SessionConnectionState.NotInSession,
                    errorMessage = null
                )
            }.onFailure { error ->
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
        val user = _uiState.value.activeUser ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                connectionState =
                    SessionConnectionState.Joining(null),
                errorMessage = null
            )

            runCatching {
                sessionRepository.createSessionNow(
                    name = name,
                    ownerUserId = user.uid,
                    displayName = displayName
                )
            }.onSuccess { activeSession ->
                _uiState.value = _uiState.value.copy(
                    connectionState =
                        SessionConnectionState.Connected(
                            activeSession
                        )
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    connectionState =
                        SessionConnectionState.NotInSession,
                    errorMessage =
                        error.message ?: "Unable to create session."
                )
            }
        }
    }

    fun leaveSession() {
        val user = _uiState.value.activeUser ?: return
        val session = currentActiveSession() ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                connectionState =
                    SessionConnectionState.Leaving(session)
            )

            runCatching {
                sessionRepository.leaveSession(
                    userId = user.uid,
                    sessionId = session.sessionId
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    connectionState =
                        SessionConnectionState.NotInSession
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    connectionState =
                        SessionConnectionState.Connected(session),
                    errorMessage =
                        error.message ?: "Unable to leave session."
                )
            }
        }
    }

    fun endSession() {
        val session = currentActiveSession() ?: return

        viewModelScope.launch {
            runCatching {
                sessionRepository.endSession(
                    sessionId = session.sessionId,
                    actorUserId = session.userId
                )
                sessionRepository.leaveSession(
                    userId = session.userId,
                    sessionId = session.sessionId
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    connectionState =
                        SessionConnectionState.NotInSession
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage =
                        error.message ?: "Unable to end session."
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
                    SessionConnectionState.NotInSession
            )
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
                    connectionState = connectionStateFor(session, realtimeState)
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
                SessionConnectionState.Connected(session)

            RealtimeConnectionState.Connecting,
            RealtimeConnectionState.Reconnecting,
            RealtimeConnectionState.Disconnected ->
                SessionConnectionState.Reconnecting(session)

            is RealtimeConnectionState.Failed ->
                SessionConnectionState.Reconnecting(
                    session = session,
                    reason = realtimeState.message
                )
        }
    }
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
