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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class FakeSessionRealtimeClient : SessionRealtimeClient {
    val publishedEvents = mutableListOf<ReceivedRealtimeEvent>()
    private val events = MutableSharedFlow<ReceivedRealtimeEvent>(
        extraBufferCapacity = 32
    )
    private val connectionState = MutableStateFlow<RealtimeConnectionState>(
        RealtimeConnectionState.Connected
    )
    private var nextTimetoken = 1_000L

    override suspend fun joinSession(request: PiJoinRequest) = Unit

    override suspend fun sendMessage(payload: PiMessagePayload) = Unit

    override suspend fun sendSignal(payload: PiSignalPayload) = Unit

    override suspend fun sendDisplayCommand(command: DisplayCommand) = Unit

    override suspend fun publish(channel: String, event: RealtimeEvent): Long {
        val timetoken = nextTimetoken++
        val published = ReceivedRealtimeEvent(
            channel = channel,
            timetoken = timetoken,
            publisherUserId = event.actorUserId,
            event = event
        )
        publishedEvents += published
        events.emit(published)
        return timetoken
    }

    override suspend fun fetchHistory(
        channel: String,
        afterTimetoken: Long?,
        limit: Int
    ): List<ReceivedRealtimeEvent> {
        return publishedEvents
            .asSequence()
            .filter { it.channel == channel }
            .filter { afterTimetoken == null || it.timetoken > afterTimetoken }
            .sortedBy { it.timetoken }
            .take(limit)
            .toList()
    }

    override fun observeChannel(channel: String): Flow<ReceivedRealtimeEvent> {
        return events
            .filter { it.channel == channel }
            .map { it }
    }

    override fun observeConnectionState(): StateFlow<RealtimeConnectionState> =
        connectionState

    override fun observeSessionEvents(sessionId: String): Flow<PiSessionEvent> {
        return flowOf(PiSessionEvent.Connected)
    }

    override suspend fun close() {
        connectionState.value = RealtimeConnectionState.Disconnected
    }
}
