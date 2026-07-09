package com.example.groupaac.ui.session

data class SessionUiState(
    val joinCode: String = "1234-5678",
    val isJoining: Boolean = false,
    val error: String? = null
)
