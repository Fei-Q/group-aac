package com.example.groupaac.data.pi

import kotlinx.coroutines.flow.Flow

interface PiClient {
    suspend fun joinSession(request: PiJoinRequest)
    suspend fun sendMessage(payload: PiMessagePayload)
    suspend fun sendSignal(payload: PiSignalPayload)
    suspend fun sendDisplayCommand(command: DisplayCommand)
    fun observeSessionEvents(sessionId: String): Flow<PiSessionEvent>
}
