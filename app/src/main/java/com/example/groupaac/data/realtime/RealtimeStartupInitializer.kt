package com.example.groupaac.data.realtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class RealtimeStartupInitializer(
    private val activeUserId: Flow<String?>,
    private val realtimeClientManager: RealtimeClientManager
) {
    suspend fun initialize() {
        val persistedUserId = activeUserId.first() ?: return
        realtimeClientManager.activateUser(persistedUserId)
    }
}
