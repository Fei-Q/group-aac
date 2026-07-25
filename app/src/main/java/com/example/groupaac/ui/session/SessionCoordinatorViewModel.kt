package com.example.groupaac.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.groupaac.data.entity.UserEntity
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
    private val sessionRepository: SessionRepository
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
                        observeActiveSession(user.id)
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
                            SessionConnectionState.Connected(
                                activeSession
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
                        userId = user.id,
                        displayName = displayName
                    )
                }

                sessionRepository.joinSession(
                    joinCode = code,
                    userId = user.id,
                    displayName = displayName,
                    requestedRole = sessionRole
                )
            }.onSuccess { result ->
                when (result) {
                    is JoinSessionResult.Joined -> {
                        joinRequestJob?.cancel()
                        _uiState.value = _uiState.value.copy(
                            connectionState =
                                SessionConnectionState.Connected(
                                    result.activeSession
                                )
                        )
                    }

                    is JoinSessionResult.AwaitingApproval -> {
                        val request = result.request
                        val sessionName = sessionRepository
                            .getSession(request.sessionId)
                            ?.name
                            ?: "Group Meeting"
                        observeJoinRequest(
                            userId = user.id,
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
                    ownerUserId = user.id,
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
                sessionRepository.leaveSession(user.id)
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
                sessionRepository.endSession(session.sessionId)
                sessionRepository.leaveSession(session.userId)
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

    fun onRealtimeDisconnected(reason: String? = null) {
        val session = currentActiveSession() ?: return

        _uiState.value = _uiState.value.copy(
            connectionState =
                SessionConnectionState.Reconnecting(
                    session = session,
                    reason = reason
                )
        )
    }

    fun onRealtimeConnected() {
        val session = currentActiveSession() ?: return

        _uiState.value = _uiState.value.copy(
            connectionState =
                SessionConnectionState.Connected(session)
        )
    }

    fun onRemoteSessionEnded(sessionId: String) {
        val session = currentActiveSession() ?: return

        if (session.sessionId != sessionId) {
            return
        }

        viewModelScope.launch {
            sessionRepository.leaveSession(session.userId)
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
}

class SessionCoordinatorViewModelFactory(
    private val accountRepository: AccountRepository,
    private val sessionRepository: SessionRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        return SessionCoordinatorViewModel(
            accountRepository = accountRepository,
            sessionRepository = sessionRepository
        ) as T
    }
}
