package com.example.groupaac.data.realtime

import com.example.groupaac.data.pi.DisplayCommand
import com.example.groupaac.data.pi.PiJoinRequest
import com.example.groupaac.data.pi.PiMessagePayload
import com.example.groupaac.data.pi.PiSessionEvent
import com.example.groupaac.data.pi.PiSignalPayload
import com.example.groupaac.data.realtime.protocol.ReceivedRealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

class InactiveSessionRealtimeClient(
    private val message: String = "Realtime client is not active for any user."
) : SessionRealtimeClient {
    private val connectionState = MutableStateFlow<RealtimeConnectionState>(
        RealtimeConnectionState.Disconnected
    )

    override suspend fun joinSession(request: PiJoinRequest) = Unit

    override suspend fun sendMessage(payload: PiMessagePayload) = Unit

    override suspend fun sendSignal(payload: PiSignalPayload) = Unit

    override suspend fun sendDisplayCommand(command: DisplayCommand) = Unit

    override suspend fun publish(channel: String, event: RealtimeEvent): Long? {
        throw IllegalStateException(message)
    }

    override suspend fun fetchHistory(
        channel: String,
        afterTimetoken: Long?,
        limit: Int
    ): List<ReceivedRealtimeEvent> = emptyList()

    override fun observeChannel(channel: String): Flow<ReceivedRealtimeEvent> =
        emptyFlow()

    override fun observeConnectionState(): StateFlow<RealtimeConnectionState> =
        connectionState

    override fun observeSessionEvents(sessionId: String): Flow<PiSessionEvent> =
        flowOf(PiSessionEvent.Disconnected)

    override suspend fun close() {
        connectionState.value = RealtimeConnectionState.Disconnected
    }
}
