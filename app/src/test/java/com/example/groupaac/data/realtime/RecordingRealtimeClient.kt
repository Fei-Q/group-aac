package com.example.groupaac.data.realtime

import com.example.groupaac.data.pi.DisplayCommand
import com.example.groupaac.data.pi.PiJoinRequest
import com.example.groupaac.data.pi.PiMessagePayload
import com.example.groupaac.data.pi.PiSessionEvent
import com.example.groupaac.data.pi.PiSignalPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class RecordingRealtimeClient : SessionRealtimeClient {
    var closed = false

    override suspend fun joinSession(request: PiJoinRequest) = Unit

    override suspend fun sendMessage(payload: PiMessagePayload) = Unit

    override suspend fun sendSignal(payload: PiSignalPayload) = Unit

    override suspend fun sendDisplayCommand(command: DisplayCommand) = Unit

    override fun observeSessionEvents(sessionId: String): Flow<PiSessionEvent> {
        return flowOf(PiSessionEvent.Connected)
    }

    override suspend fun close() {
        closed = true
    }
}
