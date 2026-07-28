package com.example.groupaac.data.realtime

import com.example.groupaac.data.pi.DisplayCommand
import com.example.groupaac.data.pi.PiJoinRequest
import com.example.groupaac.data.pi.PiMessagePayload
import com.example.groupaac.data.pi.PiSessionEvent
import com.example.groupaac.data.realtime.protocol.ReceivedRealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeEvent
import com.example.groupaac.data.pi.PiSignalPayload
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow

sealed interface RealtimeConnectionState {
    data object Connecting : RealtimeConnectionState
    data object Connected : RealtimeConnectionState
    data object Reconnecting : RealtimeConnectionState
    data object Disconnected : RealtimeConnectionState
    data class Failed(val message: String?) : RealtimeConnectionState
}

interface SessionRealtimeClient {
    suspend fun joinSession(request: PiJoinRequest)
    suspend fun sendMessage(payload: PiMessagePayload)
    suspend fun sendSignal(payload: PiSignalPayload)
    suspend fun sendDisplayCommand(command: DisplayCommand)
    suspend fun publish(channel: String, event: RealtimeEvent): Long?
    fun observeChannel(channel: String): Flow<ReceivedRealtimeEvent>
    fun observeConnectionState(): StateFlow<RealtimeConnectionState>
    fun observeSessionEvents(sessionId: String): Flow<PiSessionEvent>
    suspend fun close()
}
