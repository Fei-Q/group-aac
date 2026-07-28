package com.example.groupaac.data.realtime

import com.example.groupaac.data.pi.DisplayCommand
import com.example.groupaac.data.pi.PiJoinRequest
import com.example.groupaac.data.pi.PiMessagePayload
import com.example.groupaac.data.pi.PiSessionEvent
import com.example.groupaac.data.pi.PiSignalPayload
import com.example.groupaac.data.realtime.protocol.ReceivedRealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

class RecordingRealtimeClient : SessionRealtimeClient {
    var closed = false
    private val published = MutableSharedFlow<ReceivedRealtimeEvent>(
        extraBufferCapacity = 8
    )
    private var nextTimetoken = 1L

    override suspend fun joinSession(request: PiJoinRequest) = Unit

    override suspend fun sendMessage(payload: PiMessagePayload) = Unit

    override suspend fun sendSignal(payload: PiSignalPayload) = Unit

    override suspend fun sendDisplayCommand(command: DisplayCommand) = Unit

    override suspend fun publish(channel: String, event: RealtimeEvent): Long {
        val timetoken = nextTimetoken++
        published.tryEmit(
            ReceivedRealtimeEvent(
                channel = channel,
                timetoken = timetoken,
                publisherUserId = event.actorUserId,
                event = event
            )
        )
        return timetoken
    }

    override fun observeChannel(channel: String): Flow<ReceivedRealtimeEvent> {
        return emptyFlow()
    }

    override fun observeSessionEvents(sessionId: String): Flow<PiSessionEvent> {
        return flowOf(PiSessionEvent.Connected)
    }

    override suspend fun close() {
        closed = true
    }
}
