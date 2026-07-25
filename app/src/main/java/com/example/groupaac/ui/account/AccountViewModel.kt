package com.example.groupaac.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.groupaac.data.repository.AccountRepository
import com.example.groupaac.model.HomeExperience
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountViewModel(
    private val repository: AccountRepository
) : ViewModel() {
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
        }
    ) { users, active, homeExperience ->
        AccountUiState(
            users = users,
            activeUser = active,
            activeHomeExperience = homeExperience,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    fun createUser(displayName: String, homeExperience: HomeExperience) {
        viewModelScope.launch {
            repository.createLocalUser(displayName, homeExperience)
        }
    }

    fun switchUser(userId: String) {
        viewModelScope.launch { repository.switchUser(userId) }
    }
}

class AccountViewModelFactory(
    private val repository: AccountRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AccountViewModel(repository) as T
}
