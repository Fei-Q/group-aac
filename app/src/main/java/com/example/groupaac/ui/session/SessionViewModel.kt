package com.example.groupaac.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.repository.AccountRepository
import com.example.groupaac.data.repository.SessionRepository
import com.example.groupaac.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionViewModel(
    private val sessionRepository: SessionRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {
    private val internalState = MutableStateFlow(SessionUiState())
    val uiState = internalState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionUiState())

    private var activeUser: UserEntity? = null

    init {
        viewModelScope.launch {
            accountRepository.activeUserId
                .flatMapLatest { id -> if (id == null) flowOf(null) else accountRepository.observeUser(id) }
                .collect { activeUser = it }
        }
    }

    fun join(
        code: String,
        displayName: String,
        role: UserRole,
        rememberSettings: Boolean,
        onComplete: (UserRole) -> Unit
    ) {
        val user = activeUser ?: return
        viewModelScope.launch {
            internalState.value = internalState.value.copy(isJoining = true, error = null)

            if (rememberSettings) {
                accountRepository.updateUser(user.id, displayName, role)
            }

            runCatching { sessionRepository.joinSession(code, user.id, displayName, role) }
                .onSuccess {
                    internalState.value = internalState.value.copy(isJoining = false)
                    onComplete(role)
                }
                .onFailure { internalState.value = internalState.value.copy(isJoining = false, error = it.message) }
        }
    }

    fun createSession(
        displayName: String,
        role: UserRole,
        rememberSettings: Boolean,
        onComplete: (UserRole) -> Unit
    ) {
        val user = activeUser ?: return
        viewModelScope.launch {
            internalState.value = internalState.value.copy(isJoining = true, error = null)

            if (rememberSettings) {
                accountRepository.updateUser(user.id, displayName, role)
            }

            runCatching { sessionRepository.createSession("Group AAC Session", user.id, displayName, role) }
                .onSuccess {
                    internalState.value = internalState.value.copy(isJoining = false)
                    onComplete(role)
                }
                .onFailure { internalState.value = internalState.value.copy(isJoining = false, error = it.message) }
        }
    }
}

class SessionViewModelFactory(
    private val sessionRepository: SessionRepository,
    private val accountRepository: AccountRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = SessionViewModel(sessionRepository, accountRepository) as T
}
