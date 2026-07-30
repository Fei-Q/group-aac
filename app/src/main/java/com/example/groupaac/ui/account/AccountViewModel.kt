package com.example.groupaac.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.groupaac.data.account.CreateAccountResult
import com.example.groupaac.data.repository.AccountRepository
import com.example.groupaac.model.HomeExperience
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountViewModel(
    private val repository: AccountRepository
) : ViewModel() {
    private val createAccountResult = MutableStateFlow<CreateAccountResult?>(null)

    val uiState = combine(
        repository.users,
        repository.activeUserId.flatMapLatest { id ->
            if (id == null) {
                flowOf(null)
            } else {
                repository.observeUser(id)
            }
        },
        repository.activeUserId.flatMapLatest { id ->
            if (id == null) {
                flowOf(HomeExperience.SIMPLE)
            } else {
                repository.observeHomeExperience(id)
            }
        },
        createAccountResult
    ) { users, active, homeExperience, result ->
        AccountUiState(
            users = users,
            activeUser = active,
            activeHomeExperience = homeExperience,
            isLoading = false,
            createAccountResult = result
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    fun createUser(uid: String, displayName: String, homeExperience: HomeExperience) {
        viewModelScope.launch {
            createAccountResult.value = repository.createLocalUser(
                uid = uid,
                displayName = displayName,
                homeExperience = homeExperience
            )
        }
    }

    fun switchUser(userId: String) {
        viewModelScope.launch { repository.switchUser(userId) }
    }

    fun clearCreateAccountResult() {
        createAccountResult.update { null }
    }
}

class AccountViewModelFactory(
    private val repository: AccountRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AccountViewModel(repository) as T
}
