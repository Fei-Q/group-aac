package com.example.groupaac.ui.account

import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.account.CreateAccountResult
import com.example.groupaac.model.HomeExperience

data class AccountUiState(
    val users: List<UserEntity> = emptyList(),
    val activeUser: UserEntity? = null,
    val activeHomeExperience: HomeExperience = HomeExperience.SIMPLE,
    val isLoading: Boolean = true,
    val createAccountResult: CreateAccountResult? = null
)
