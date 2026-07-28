package com.example.groupaac.data.realtime

import com.example.groupaac.data.pi.DisplayCommand
import com.example.groupaac.data.pi.PiClient
import com.example.groupaac.data.pi.PiJoinRequest
import com.example.groupaac.data.pi.PiMessagePayload
import com.example.groupaac.data.pi.PiSessionEvent
import com.example.groupaac.data.pi.PiSignalPayload
import kotlinx.coroutines.flow.Flow

class DelegatingPiClient(
    private val realtimeClientManager: RealtimeClientManager
) : PiClient {
    override suspend fun joinSession(request: PiJoinRequest) {
        realtimeClientManager.requireClient().joinSession(request)
    }

    override suspend fun sendMessage(payload: PiMessagePayload) {
        realtimeClientManager.requireClient().sendMessage(payload)
    }

    override suspend fun sendSignal(payload: PiSignalPayload) {
        realtimeClientManager.requireClient().sendSignal(payload)
    }

    override suspend fun sendDisplayCommand(command: DisplayCommand) {
        realtimeClientManager.requireClient().sendDisplayCommand(command)
    }

    override fun observeSessionEvents(sessionId: String): Flow<PiSessionEvent> {
        return realtimeClientManager.requireClient().observeSessionEvents(sessionId)
    }
}
