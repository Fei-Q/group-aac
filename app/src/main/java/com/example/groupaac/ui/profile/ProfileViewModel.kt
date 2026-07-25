package com.example.groupaac.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.entity.UserSettingsEntity
import com.example.groupaac.data.repository.AccountRepository
import com.example.groupaac.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: UserEntity? = null,
    val settings: UserSettingsEntity? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

class ProfileViewModel(
    private val userId: String,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ProfileUiState()
    )

    val uiState: StateFlow<ProfileUiState> =
        _uiState.asStateFlow()

    init {
        observeProfile()
    }

    private fun observeProfile() {
        viewModelScope.launch {
            combine(
                accountRepository.observeUser(userId),
                settingsRepository.observeSettings(userId)
            ) { user, settings ->
                user to settings
            }.collect { (user, settings) ->
                _uiState.update { current ->
                    current.copy(
                        user = user,
                        settings = settings,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun updateSettings(
        settings: UserSettingsEntity
    ) {
        val settingsForCurrentUser = settings.copy(
            userId = userId
        )

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    errorMessage = null
                )
            }

            runCatching {
                settingsRepository.updateSettings(
                    settingsForCurrentUser
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSaving = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage =
                            error.message
                                ?: "Unable to save settings."
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update {
            it.copy(errorMessage = null)
        }
    }
}

class ProfileViewModelFactory(
    private val userId: String,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (
            modelClass.isAssignableFrom(
                ProfileViewModel::class.java
            )
        ) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(
                userId = userId,
                accountRepository = accountRepository,
                settingsRepository = settingsRepository
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}