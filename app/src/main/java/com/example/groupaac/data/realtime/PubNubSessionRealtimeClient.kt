package com.example.groupaac.data.realtime

import com.example.groupaac.data.pi.DisplayCommand
import com.example.groupaac.data.pi.PiJoinRequest
import com.example.groupaac.data.pi.PiMessagePayload
import com.example.groupaac.data.pi.PiSessionEvent
import com.example.groupaac.data.pi.PiSignalPayload
import com.example.groupaac.data.realtime.protocol.ReceivedRealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeChannels
import com.example.groupaac.data.realtime.protocol.RealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeEventCodec
import com.example.groupaac.data.realtime.protocol.RealtimeEventTypes
import com.example.groupaac.util.IdUtils
import com.google.gson.JsonParser
import com.pubnub.api.PubNub
import com.pubnub.api.UserId
import com.pubnub.api.enums.PNStatusCategory
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.PNStatus
import com.pubnub.api.models.consumer.pubsub.PNMessageResult
import com.pubnub.api.v2.PNConfiguration
import com.pubnub.api.v2.callbacks.StatusListener
import com.pubnub.api.v2.subscriptions.Subscription
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PubNubSessionRealtimeClient internal constructor(
    private val userId: String,
    private val transport: PubNubTransport
) : SessionRealtimeClient {
    private val connectionState = MutableStateFlow<RealtimeConnectionState>(
        RealtimeConnectionState.Connecting
    )
    private val channelEvents =
        linkedMapOf<String, MutableSharedFlow<ReceivedRealtimeEvent>>()

    init {
        transport.setStatusListener(::handleTransportState)
    }

    override suspend fun joinSession(request: PiJoinRequest) = Unit

    override suspend fun sendMessage(payload: PiMessagePayload) = Unit

    override suspend fun sendSignal(payload: PiSignalPayload) {
        val event = RealtimeEvent(
            eventId = IdUtils.newId(),
            type = RealtimeEventTypes.AAC_SIGNAL_CREATED,
            sessionId = payload.sessionId,
            actorUserId = payload.userId,
            occurredAt = payload.createdAt,
            payload = buildJsonObject {
                put("signalId", JsonPrimitive(payload.id))
                put("userId", JsonPrimitive(payload.userId))
                put("displayName", JsonPrimitive(payload.displayName))
                put("type", JsonPrimitive(payload.type.name))
                put("createdAt", JsonPrimitive(payload.createdAt))
            }
        )
        publish(RealtimeChannels.public(payload.sessionId), event)
    }

    override suspend fun sendDisplayCommand(command: DisplayCommand) = Unit

    override suspend fun publish(channel: String, event: RealtimeEvent): Long? {
        val encodedEvent = RealtimeEventCodec.encode(event)
        return transport.publish(channel, encodedEvent)
    }

    override suspend fun fetchHistory(
        channel: String,
        afterTimetoken: Long?,
        limit: Int
    ): List<ReceivedRealtimeEvent> {
        return transport.fetchHistory(channel, afterTimetoken, limit)
            .mapNotNull { incoming ->
                runCatching {
                    ReceivedRealtimeEvent(
                        channel = incoming.channel,
                        timetoken = incoming.timetoken,
                        publisherUserId = incoming.publisherUserId,
                        event = RealtimeEventCodec.decode(incoming.payload)
                    )
                }.getOrNull()
            }
            .sortedBy { it.timetoken }
    }

    override fun observeChannel(channel: String): Flow<ReceivedRealtimeEvent> {
        val flow = channelEvents.getOrPut(channel) {
            MutableSharedFlow<ReceivedRealtimeEvent>(
                extraBufferCapacity = 32
            ).also { sharedFlow ->
                transport.subscribe(channel) { incoming ->
                    handleIncoming(sharedFlow, incoming)
                }
            }
        }
        return flow
    }

    override fun observeConnectionState(): StateFlow<RealtimeConnectionState> =
        connectionState.asStateFlow()

    override fun observeSessionEvents(sessionId: String): Flow<PiSessionEvent> {
        return observeConnectionState()
            .map { state ->
                when (state) {
                    RealtimeConnectionState.Connected -> PiSessionEvent.Connected
                    RealtimeConnectionState.Disconnected,
                    RealtimeConnectionState.Reconnecting -> PiSessionEvent.Disconnected
                    is RealtimeConnectionState.Failed -> PiSessionEvent.Error(
                        state.message ?: "Realtime connection failed."
                    )
                    RealtimeConnectionState.Connecting -> null
                }
            }
            .filterNotNull()
            .distinctUntilChanged()
    }

    override suspend fun close() {
        channelEvents.clear()
        transport.close()
        connectionState.value = RealtimeConnectionState.Disconnected
    }

    private fun handleIncoming(
        sink: MutableSharedFlow<ReceivedRealtimeEvent>,
        incoming: PubNubIncomingMessage
    ) {
        try {
            val event = RealtimeEventCodec.decode(incoming.payload)
            sink.tryEmit(
                ReceivedRealtimeEvent(
                    channel = incoming.channel,
                    timetoken = incoming.timetoken,
                    publisherUserId = incoming.publisherUserId,
                    event = event
                )
            )
        } catch (error: Throwable) {
            connectionState.value = RealtimeConnectionState.Failed(
                "Malformed realtime event on ${incoming.channel}: ${error.message}"
            )
        }
    }

    private fun handleTransportState(state: PubNubTransportState) {
        connectionState.value = when (state) {
            PubNubTransportState.Connected -> RealtimeConnectionState.Connected
            PubNubTransportState.Reconnecting -> RealtimeConnectionState.Reconnecting
            PubNubTransportState.Disconnected -> RealtimeConnectionState.Disconnected
            is PubNubTransportState.Failed -> RealtimeConnectionState.Failed(
                state.message
            )
        }
    }
}

class PubNubSessionRealtimeClientFactory internal constructor(
    private val runtimeConfig: PubNubRuntimeConfig,
    private val tokenProvider: PubNubTokenProvider,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val transportFactory: suspend (
        uid: String,
        config: PubNubRuntimeConfig,
        token: String?,
        ioDispatcher: CoroutineDispatcher
    ) -> PubNubTransport = { uid, config, token, dispatcher ->
        createSdkTransport(uid, config, token, dispatcher)
    }
) {
    suspend fun create(uid: String): SessionRealtimeClient {
        val token = tokenProvider.tokenForUser(uid)
        return PubNubSessionRealtimeClient(
            userId = uid,
            transport = transportFactory(uid, runtimeConfig, token, ioDispatcher)
        )
    }
}

internal sealed interface PubNubTransportState {
    data object Connected : PubNubTransportState
    data object Reconnecting : PubNubTransportState
    data object Disconnected : PubNubTransportState
    data class Failed(val message: String?) : PubNubTransportState
}

internal data class PubNubIncomingMessage(
    val channel: String,
    val payload: String,
    val publisherUserId: String?,
    val timetoken: Long
)

internal interface PubNubTransport {
    suspend fun publish(channel: String, payload: String): Long
    suspend fun fetchHistory(
        channel: String,
        afterTimetoken: Long?,
        limit: Int
    ): List<PubNubIncomingMessage>
    fun subscribe(channel: String, onMessageReceived: (PubNubIncomingMessage) -> Unit)
    fun setStatusListener(listener: (PubNubTransportState) -> Unit)
    suspend fun close()
}

private suspend fun createSdkTransport(
    uid: String,
    config: PubNubRuntimeConfig,
    token: String?,
    ioDispatcher: CoroutineDispatcher
): PubNubTransport = withContext(ioDispatcher) {
    val configuration = PNConfiguration.builder(UserId(uid), config.subscribeKey) {
        publishKey = config.publishKey
        secure = true
        authToken = token
    }.build()
    SdkPubNubTransport(
        pubNub = PubNub.create(configuration),
        ioDispatcher = ioDispatcher
    )
}

private class SdkPubNubTransport(
    private val pubNub: PubNub,
    private val ioDispatcher: CoroutineDispatcher
) : PubNubTransport {
    private val subscriptions = linkedMapOf<String, Subscription>()
    private var statusListener: ((PubNubTransportState) -> Unit)? = null
    private val sdkStatusListener = object : StatusListener {
        override fun status(
            pubnub: PubNub,
            status: PNStatus
        ) {
            statusListener?.invoke(status.toTransportState())
        }
    }

    init {
        pubNub.addListener(sdkStatusListener)
    }

    override suspend fun publish(channel: String, payload: String): Long =
        suspendCancellableCoroutine { continuation ->
            pubNub.channel(channel)
                .publish(
                    message = JsonParser.parseString(payload),
                    shouldStore = true
                )
                .async { result ->
                    result.getOrNull()?.let { publishResult: PNPublishResult ->
                        if (continuation.isActive) {
                            continuation.resume(publishResult.timetoken)
                        }
                    }
                    result.exceptionOrNull()?.let { error ->
                        if (continuation.isActive) {
                            continuation.resumeWithException(error)
                        }
                    }
                }
        }

    override suspend fun fetchHistory(
        channel: String,
        afterTimetoken: Long?,
        limit: Int
    ): List<PubNubIncomingMessage> = emptyList()

    override fun subscribe(
        channel: String,
        onMessageReceived: (PubNubIncomingMessage) -> Unit
    ) {
        if (subscriptions.containsKey(channel)) {
            return
        }
        val subscription = pubNub.channel(channel).subscription().apply {
            this.onMessage = { message ->
                onMessageReceived(message.toIncomingMessage())
            }
        }
        subscriptions[channel] = subscription
        subscription.subscribe()
    }

    override fun setStatusListener(listener: (PubNubTransportState) -> Unit) {
        statusListener = listener
    }

    override suspend fun close() = withContext(ioDispatcher) {
        subscriptions.values.forEach { subscription ->
            subscription.close()
        }
        subscriptions.clear()
        pubNub.unsubscribeAll()
        pubNub.disconnect()
        pubNub.destroy()
    }
}

private fun PNMessageResult.toIncomingMessage(): PubNubIncomingMessage {
    val payload = if (message.isJsonPrimitive && message.asJsonPrimitive.isString) {
        message.asString
    } else {
        message.toString()
    }
    return PubNubIncomingMessage(
        channel = channel,
        payload = payload,
        publisherUserId = publisher,
        timetoken = timetoken ?: 0L
    )
}

private fun PNStatus.toTransportState(): PubNubTransportState {
    return when (category) {
        PNStatusCategory.PNConnectedCategory,
        PNStatusCategory.PNSubscriptionChanged -> {
            PubNubTransportState.Connected
        }
        PNStatusCategory.PNUnexpectedDisconnectCategory -> {
            PubNubTransportState.Reconnecting
        }
        PNStatusCategory.PNDisconnectedCategory -> {
            PubNubTransportState.Disconnected
        }
        PNStatusCategory.PNConnectionError,
        PNStatusCategory.PNHeartbeatFailed -> {
            PubNubTransportState.Failed(
                exception?.message ?: category.name
            )
        }
        PNStatusCategory.PNHeartbeatSuccess -> {
            PubNubTransportState.Connected
        }
    }
}
