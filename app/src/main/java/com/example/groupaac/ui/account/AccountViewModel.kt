package com.example.groupaac.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.groupaac.data.repository.AccountRepository
import com.example.groupaac.model.UserRole
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
        repository.activeUserId.flatMapLatest { id -> if (id == null) flowOf(null) else repository.observeUser(id) }
    ) { users, active ->
        AccountUiState(users = users, activeUser = active, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    fun createUser(displayName: String, role: UserRole) {
        viewModelScope.launch { repository.createLocalUser(displayName, role) }
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
