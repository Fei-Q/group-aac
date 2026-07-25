package com.example.groupaac.model

import com.example.groupaac.data.entity.SessionJoinRequestEntity

sealed interface JoinSessionResult {
    data class Joined(
        val activeSession: ActiveSession
    ) : JoinSessionResult

    data class AwaitingApproval(
        val request: SessionJoinRequestEntity
    ) : JoinSessionResult
}
