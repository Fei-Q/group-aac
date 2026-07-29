package com.example.groupaac.data.realtime

import com.example.groupaac.data.realtime.protocol.ReceivedRealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeChannels
import com.example.groupaac.data.realtime.protocol.RealtimeEventRouter
import com.example.groupaac.data.realtime.protocol.RealtimeRoute
import com.example.groupaac.data.realtime.protocol.RealtimeRouteKind
import com.example.groupaac.data.realtime.sync.SessionRealtimeSync
import com.example.groupaac.model.ActiveSession
import com.example.groupaac.model.SessionRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SessionSubscriptionCoordinator(
    private val activeUserId: Flow<String?>,
    private val activeSessionProvider: (String) -> Flow<ActiveSession?>,
    private val realtimeClientManager: RealtimeClientManager,
    private val sessionRealtimeSync: SessionRealtimeSync,
    private val channelCursorProvider: suspend (String) -> Long? = { null },
    private val scope: CoroutineScope
) {
    private val pendingFacilitatorRequest =
        MutableStateFlow<PendingFacilitatorRequest?>(null)
    private val _connectionState = MutableStateFlow<RealtimeConnectionState>(
        RealtimeConnectionState.Disconnected
    )
    val connectionState: StateFlow<RealtimeConnectionState> =
        _connectionState.asStateFlow()

    private var coordinationJob: Job? = null

    init {
        start()
    }

    fun trackFacilitatorRequest(
        sessionId: String,
        userId: String
    ) {
        pendingFacilitatorRequest.value = PendingFacilitatorRequest(
            sessionId = sessionId,
            userId = userId
        )
    }

    fun clearFacilitatorRequest(
        userId: String? = null,
        sessionId: String? = null
    ) {
        val current = pendingFacilitatorRequest.value ?: return
        if (userId != null && current.userId != userId) {
            return
        }
        if (sessionId != null && current.sessionId != sessionId) {
            return
        }
        pendingFacilitatorRequest.value = null
    }

    fun close() {
        coordinationJob?.cancel()
        coordinationJob = null
        pendingFacilitatorRequest.value = null
        _connectionState.value = RealtimeConnectionState.Disconnected
    }

    private fun start() {
        coordinationJob?.cancel()
        coordinationJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var contextJob: Job? = null
            activeUserId
                .distinctUntilChanged()
                .flatMapLatest { userId ->
                    if (userId == null) {
                        pendingFacilitatorRequest.value = null
                        flowOf(SubscriptionContext())
                    } else {
                        combine(
                            activeSessionProvider(userId),
                            pendingFacilitatorRequest.map { request ->
                                request?.takeIf { it.userId == userId }
                            }
                        ) { activeSession, pendingRequest ->
                            SubscriptionContext(
                                userId = userId,
                                activeSession = activeSession,
                                pendingRequest = if (activeSession == null) {
                                    pendingRequest
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
                .collect { context ->
                    contextJob?.cancelAndJoin()
                    contextJob = launch(start = CoroutineStart.UNDISPATCHED) {
                        coordinate(context)
                    }
                }
        }
    }

    private suspend fun coordinate(
        context: SubscriptionContext
    ) = coroutineScope {
        val userId = context.userId
        if (userId == null) {
            _connectionState.value = RealtimeConnectionState.Disconnected
            return@coroutineScope
        }

        val realtimeClient = realtimeClientManager.requireClient()
        val channels = channelsFor(context)

        launch {
            realtimeClient.observeConnectionState().collect { state ->
                _connectionState.value = state
            }
        }

        channels.forEach { channel ->
            replayHistory(
                realtimeClient = realtimeClient,
                channel = channel,
                context = context
            )
        }

        channels.forEach { channel ->
            launch {
                realtimeClient.observeChannel(channel).collect { received ->
                    if (shouldApplyIncoming(context, received)) {
                        sessionRealtimeSync.applyIncoming(received)
                    }
                }
            }
        }

        awaitCancellation()
    }

    private suspend fun replayHistory(
        realtimeClient: SessionRealtimeClient,
        channel: String,
        context: SubscriptionContext
    ) {
        val afterTimetoken = channelCursorProvider(channel)
        realtimeClient.fetchHistory(
            channel = channel,
            afterTimetoken = afterTimetoken
        ).sortedBy { it.timetoken }
            .forEach { received ->
                if (shouldApplyIncoming(context, received)) {
                    sessionRealtimeSync.applyIncoming(received)
                }
            }
    }

    private fun channelsFor(
        context: SubscriptionContext
    ): List<String> {
        val userId = context.userId ?: return emptyList()
        val activeSession = context.activeSession
        if (activeSession != null) {
            return when (activeSession.role) {
                SessionRole.PARTICIPANT -> listOf(
                    RealtimeChannels.public(activeSession.sessionId),
                    RealtimeChannels.privateUser(
                        activeSession.sessionId,
                        userId
                    )
                )

                SessionRole.FACILITATOR,
                SessionRole.HOST -> listOf(
                    RealtimeChannels.public(activeSession.sessionId),
                    RealtimeChannels.facilitator(activeSession.sessionId),
                    RealtimeChannels.privateUser(
                        activeSession.sessionId,
                        userId
                    ),
                    RealtimeChannels.displayEvents(activeSession.sessionId)
                )
            }
        }

        val pendingRequest = context.pendingRequest ?: return emptyList()
        return listOf(
            RealtimeChannels.public(pendingRequest.sessionId),
            RealtimeChannels.privateUser(pendingRequest.sessionId, userId)
        )
    }

    private fun shouldApplyIncoming(
        context: SubscriptionContext,
        received: ReceivedRealtimeEvent
    ): Boolean {
        val route = runCatching {
            RealtimeEventRouter.route(received.channel)
        }.getOrNull() ?: return false
        val expectedSessionId =
            context.activeSession?.sessionId ?: context.pendingRequest?.sessionId
                ?: return false
        val expectedUserId = context.userId ?: return false

        if (received.event.sessionId != expectedSessionId) {
            return false
        }

        if (!matchesExpectedScope(route, context, expectedSessionId, expectedUserId)) {
            return false
        }

        return true
    }

    private fun matchesExpectedScope(
        route: RealtimeRoute,
        context: SubscriptionContext,
        expectedSessionId: String,
        expectedUserId: String
    ): Boolean {
        if (route.sessionId != expectedSessionId) {
            return false
        }

        return when (route.kind) {
            RealtimeRouteKind.PUBLIC -> true
            RealtimeRouteKind.PRIVATE_USER ->
                route.userId == expectedUserId

            RealtimeRouteKind.FACILITATOR ->
                context.activeSession?.role in setOf(
                    SessionRole.FACILITATOR,
                    SessionRole.HOST
                )

            RealtimeRouteKind.DISPLAY_EVENTS ->
                context.activeSession?.role in setOf(
                    SessionRole.FACILITATOR,
                    SessionRole.HOST
                )

            RealtimeRouteKind.DISPLAY,
            RealtimeRouteKind.DISPLAY_CONTROL,
            RealtimeRouteKind.DISPLAY_DEVICE_EVENTS -> false
        }
    }

    private data class PendingFacilitatorRequest(
        val sessionId: String,
        val userId: String
    )

    private data class SubscriptionContext(
        val userId: String? = null,
        val activeSession: ActiveSession? = null,
        val pendingRequest: PendingFacilitatorRequest? = null
    )
}
