package com.example.groupaac.model

data class ActiveSession(
    val sessionId: String,
    val joinCode: String,
    val sessionName: String,
    val userId: String,
    val role: UserRole,
    val joinedAt: Long
)