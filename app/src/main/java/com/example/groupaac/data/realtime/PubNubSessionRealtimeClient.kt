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
import com.pubnub.api.models.consumer.PNBoundedPage
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.PNStatus
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.history.PNFetchMessagesResult
import com.pubnub.api.models.consumer.pubsub.PNMessageResult
import com.pubnub.api.v2.PNConfiguration
import com.pubnub.api.v2.callbacks.StatusListener
import com.pubnub.api.v2.subscriptions.Subscription
import kotlinx.coroutines.CancellationException
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
    internal val lastHistoryDiagnostics =
        MutableStateFlow<HistoryFetchDiagnostics?>(null)
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
        if (limit <= 0) {
            return emptyList()
        }

        val receivedEvents = mutableListOf<ReceivedRealtimeEvent>()
        val quarantinedMessages = mutableListOf<QuarantinedHistoryMessage>()
        val seenMessages = linkedSetOf<HistoryMessageKey>()
        var nextPage = PubNubHistoryCursor(
            end = afterTimetoken,
            limit = limit.coerceAtMost(HISTORY_PAGE_MAX)
        )

        try {
            while (receivedEvents.size < limit) {
                val remaining = (limit - receivedEvents.size)
                    .coerceAtMost(HISTORY_PAGE_MAX)
                val batch = transport.fetchHistoryPage(
                    channel = channel,
                    page = nextPage.copy(limit = remaining)
                )
                if (batch.messages.isEmpty()) {
                    break
                }

                batch.messages.sortedBy { it.timetoken }
                    .forEach { incoming ->
                        if (
                            afterTimetoken != null &&
                            incoming.timetoken <= afterTimetoken
                        ) {
                            return@forEach
                        }
                        val key = HistoryMessageKey(
                            channel = incoming.channel,
                            timetoken = incoming.timetoken,
                            publisherUserId = incoming.publisherUserId,
                            payload = incoming.payload
                        )
                        if (!seenMessages.add(key)) {
                            return@forEach
                        }
                        try {
                            receivedEvents += ReceivedRealtimeEvent(
                                channel = incoming.channel,
                                timetoken = incoming.timetoken,
                                publisherUserId = incoming.publisherUserId,
                                event = RealtimeEventCodec.decode(incoming.payload)
                            )
                        } catch (error: Exception) {
                            quarantinedMessages += QuarantinedHistoryMessage(
                                channel = incoming.channel,
                                timetoken = incoming.timetoken,
                                publisherUserId = incoming.publisherUserId,
                                reason = error.message ?: error::class.java.simpleName,
                                payloadPreview = incoming.payload.take(256)
                            )
                            System.err.println(
                                "$HISTORY_LOG_TAG: Quarantined malformed " +
                                    "PubNub history on ${incoming.channel} " +
                                    "at ${incoming.timetoken}: ${error.message}"
                            )
                        }
                    }

                if (receivedEvents.size >= limit) {
                    break
                }
                val candidatePage = batch.nextPage ?: break
                if (candidatePage == nextPage) {
                    break
                }
                nextPage = candidatePage
            }

            val diagnostics = HistoryFetchDiagnostics(
                channel = channel,
                requestedAfterTimetoken = afterTimetoken,
                quarantinedMessages = quarantinedMessages,
                cursorPolicy = HISTORY_CURSOR_POLICY
            )
            lastHistoryDiagnostics.value = diagnostics.takeIf {
                it.quarantinedMessages.isNotEmpty()
            }

            return receivedEvents
                .sortedBy { it.timetoken }
                .take(limit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw error
        }
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
        } catch (error: Exception) {
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
    suspend fun fetchHistoryPage(
        channel: String,
        page: PubNubHistoryCursor
    ): PubNubHistoryPage
    fun subscribe(channel: String, onMessageReceived: (PubNubIncomingMessage) -> Unit)
    fun setStatusListener(listener: (PubNubTransportState) -> Unit)
    suspend fun close()
}

internal data class PubNubHistoryCursor(
    val start: Long? = null,
    val end: Long? = null,
    val limit: Int? = null
)

internal data class PubNubHistoryPage(
    val messages: List<PubNubIncomingMessage>,
    val nextPage: PubNubHistoryCursor?
)

internal data class QuarantinedHistoryMessage(
    val channel: String,
    val timetoken: Long,
    val publisherUserId: String?,
    val reason: String,
    val payloadPreview: String
)

internal data class HistoryFetchDiagnostics(
    val channel: String,
    val requestedAfterTimetoken: Long?,
    val quarantinedMessages: List<QuarantinedHistoryMessage>,
    val cursorPolicy: String
)

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

    override suspend fun fetchHistoryPage(
        channel: String,
        page: PubNubHistoryCursor
    ): PubNubHistoryPage =
        suspendCancellableCoroutine { continuation ->
            pubNub.fetchMessages(
                channels = listOf(channel),
                page = page.toBoundedPage(),
                includeUUID = true,
                includeMeta = false,
                includeMessageActions = false,
                includeMessageType = true,
                includeCustomMessageType = false
            ).async { result ->
                result.getOrNull()?.let { fetchResult: PNFetchMessagesResult ->
                    if (continuation.isActive) {
                        continuation.resume(
                            PubNubHistoryPage(
                                messages = fetchResult.channels[channel]
                                    .orEmpty()
                                    .map { item ->
                                        item.toIncomingMessage(channel)
                                    },
                                nextPage = fetchResult.page?.toHistoryCursor()
                            )
                        )
                    }
                }
                result.exceptionOrNull()?.let { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(error)
                    }
                }
            }
        }

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

private fun PNFetchMessageItem.toIncomingMessage(
    channel: String
): PubNubIncomingMessage {
    val encodedPayload = if (
        message.isJsonPrimitive &&
        message.asJsonPrimitive.isString
    ) {
        message.asString
    } else {
        message.toString()
    }
    return PubNubIncomingMessage(
        channel = channel,
        payload = encodedPayload,
        publisherUserId = uuid,
        timetoken = timetoken ?: 0L
    )
}

private fun PNBoundedPage.toHistoryCursor(): PubNubHistoryCursor =
    PubNubHistoryCursor(
        start = start,
        end = end,
        limit = limit
    )

private fun PubNubHistoryCursor.toBoundedPage(): PNBoundedPage =
    PNBoundedPage(
        start = start,
        end = end,
        limit = limit
    )

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

private data class HistoryMessageKey(
    val channel: String,
    val timetoken: Long,
    val publisherUserId: String?,
    val payload: String
)

private const val HISTORY_PAGE_MAX = 100
private const val HISTORY_LOG_TAG = "PubNubHistory"
private const val HISTORY_CURSOR_POLICY =
    "Malformed history messages are quarantined locally and skipped. " +
        "Only valid applied events advance the persisted replay cursor."
