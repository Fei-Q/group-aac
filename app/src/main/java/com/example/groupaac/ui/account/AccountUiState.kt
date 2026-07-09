package com.example.groupaac.ui.account

import com.example.groupaac.data.entity.UserEntity

data class AccountUiState(
    val users: List<UserEntity> = emptyList(),
    val activeUser: UserEntity? = null,
    val isLoading: Boolean = true
)
