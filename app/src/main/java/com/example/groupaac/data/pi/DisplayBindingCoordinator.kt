package com.example.groupaac.data.pi

import com.example.groupaac.data.realtime.SessionRealtimeClient
import com.example.groupaac.data.realtime.protocol.RealtimeChannels
import com.example.groupaac.data.realtime.protocol.RealtimeEventTypes
import com.example.groupaac.util.IdUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull

sealed interface DisplayBindingResult {

    data class Bound(
        val commandEventId: String
    ) : DisplayBindingResult

    data object PairingExpired : DisplayBindingResult

    data class Rejected(
        val reason: String?
    ) : DisplayBindingResult

    data object TimedOut : DisplayBindingResult

    data class Failure(
        val message: String
    ) : DisplayBindingResult
}

sealed interface DisplayUnbindResult {

    data object Unbound : DisplayUnbindResult

    data object TimedOut : DisplayUnbindResult

    data class Failure(
        val message: String
    ) : DisplayUnbindResult
}

interface DisplayBindingCoordinator {

    suspend fun bind(
        pairing: DisplayPairingPayload,
        invitation: SessionInvitationPayload,
        requestedByUserId: String
    ): DisplayBindingResult

    suspend fun unbind(
        displayId: String,
        sessionId: String,
        requestedByUserId: String
    ): DisplayUnbindResult
}

object NoOpDisplayBindingCoordinator :
    DisplayBindingCoordinator {

    override suspend fun bind(
        pairing: DisplayPairingPayload,
        invitation: SessionInvitationPayload,
        requestedByUserId: String
    ): DisplayBindingResult {
        return DisplayBindingResult.Failure(
            "Display binding is not configured."
        )
    }

    override suspend fun unbind(
        displayId: String,
        sessionId: String,
        requestedByUserId: String
    ): DisplayUnbindResult {
        return DisplayUnbindResult.Failure(
            "Display binding is not configured."
        )
    }
}

class PubNubDisplayBindingCoordinator(
    private val clientProvider:
        () -> SessionRealtimeClient,
    private val nowProvider:
        () -> Long = System::currentTimeMillis,
    private val acknowledgementTimeoutMillis:
    Long = 10_000L
) : DisplayBindingCoordinator {

    override suspend fun bind(
        pairing: DisplayPairingPayload,
        invitation: SessionInvitationPayload,
        requestedByUserId: String
    ): DisplayBindingResult {
        val now = nowProvider()

        if (pairing.pairingExpiresAt <= now) {
            return DisplayBindingResult.PairingExpired
        }

        if (pairing.displayId != invitation.displayId) {
            return DisplayBindingResult.Failure(
                "The selected Pi does not match the session invitation."
            )
        }

        val commandEventId = IdUtils.newId()

        val command = buildDisplayBindSessionEvent(
            eventId = commandEventId,
            requestedByUserId = requestedByUserId,
            pairing = pairing,
            invitation = invitation,
            occurredAt = now
        )

        return try {
            val client = clientProvider()

            val reply = awaitReply(
                client = client,
                eventChannel =
                    RealtimeChannels.displayDeviceEvents(
                        pairing.displayId
                    ),
                commandChannel =
                    RealtimeChannels.displayControl(
                        pairing.displayId
                    ),
                command = command,
                expectedCommandEventId =
                    commandEventId,
                expectedDisplayId =
                    pairing.displayId,
                expectedSessionId =
                    invitation.sessionId,
                allowedReplyTypes = setOf(
                    RealtimeEventTypes.DISPLAY_BOUND,
                    RealtimeEventTypes.DISPLAY_BIND_FAILED
                )
            )

            when (reply?.eventType) {
                RealtimeEventTypes.DISPLAY_BOUND -> {
                    DisplayBindingResult.Bound(
                        commandEventId =
                            commandEventId
                    )
                }

                RealtimeEventTypes.DISPLAY_BIND_FAILED -> {
                    DisplayBindingResult.Rejected(
                        reason = reply.reason
                    )
                }

                null -> {
                    DisplayBindingResult.TimedOut
                }

                else -> {
                    DisplayBindingResult.Failure(
                        "Unexpected Pi binding response."
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            DisplayBindingResult.Failure(
                error.message
                    ?: "Unable to bind the display."
            )
        }
    }

    override suspend fun unbind(
        displayId: String,
        sessionId: String,
        requestedByUserId: String
    ): DisplayUnbindResult {
        val now = nowProvider()
        val commandEventId = IdUtils.newId()

        val command =
            buildDisplayUnbindSessionEvent(
                eventId = commandEventId,
                requestedByUserId =
                    requestedByUserId,
                displayId = displayId,
                sessionId = sessionId,
                occurredAt = now
            )

        return try {
            val client = clientProvider()

            val reply = awaitReply(
                client = client,
                eventChannel =
                    RealtimeChannels
                        .displayDeviceEvents(
                            displayId
                        ),
                commandChannel =
                    RealtimeChannels
                        .displayControl(
                            displayId
                        ),
                command = command,
                expectedCommandEventId =
                    commandEventId,
                expectedDisplayId =
                    displayId,
                expectedSessionId =
                    sessionId,
                allowedReplyTypes = setOf(
                    RealtimeEventTypes.DISPLAY_UNBOUND,
                    RealtimeEventTypes.DISPLAY_BIND_FAILED
                )
            )

            when (reply?.eventType) {
                RealtimeEventTypes.DISPLAY_UNBOUND -> {
                    DisplayUnbindResult.Unbound
                }

                null -> {
                    DisplayUnbindResult.TimedOut
                }

                else -> {
                    DisplayUnbindResult.Failure(
                        reply.reason
                            ?: "The Pi rejected unbinding."
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            DisplayUnbindResult.Failure(
                error.message
                    ?: "Unable to unbind the display."
            )
        }
    }

    private suspend fun awaitReply(
        client: SessionRealtimeClient,
        eventChannel: String,
        commandChannel: String,
        command:
        com.example.groupaac.data.realtime.protocol.RealtimeEvent,
        expectedCommandEventId: String,
        expectedDisplayId: String,
        expectedSessionId: String,
        allowedReplyTypes: Set<String>
    ): DisplayBindingReply? {
        return withTimeoutOrNull(
            acknowledgementTimeoutMillis
        ) {
            coroutineScope {
                /*
                 * Start collecting before publishing so an immediate Pi
                 * acknowledgement cannot be missed.
                 */
                val replyDeferred = async(
                    start =
                        CoroutineStart.UNDISPATCHED
                ) {
                    val subscription =
                        client.openSubscription(
                            eventChannel
                        )
                    try {
                        subscription.events
                            .mapNotNull { received ->
                                received.event
                                    .toDisplayBindingReplyOrNull(
                                        expectedCommandEventId =
                                            expectedCommandEventId,
                                        expectedDisplayId =
                                            expectedDisplayId,
                                        expectedSessionId =
                                            expectedSessionId
                                    )
                                    ?.takeIf {
                                        it.eventType in
                                                allowedReplyTypes
                                    }
                            }
                            .first()
                    } finally {
                        subscription.close()
                    }
                }

                try {
                    client.publish(
                        channel = commandChannel,
                        event = command
                    )

                    replyDeferred.await()
                } finally {
                    replyDeferred.cancel()
                }
            }
        }
    }
}

sealed interface LaunchSessionResult {

    data class Launched(
        val activeSession:
        com.example.groupaac.model.ActiveSession,
        val invitation:
        SessionInvitationPayload
    ) : LaunchSessionResult

    data object PairingExpired :
        LaunchSessionResult

    data object DisplayTimedOut :
        LaunchSessionResult

    data class DisplayRejected(
        val reason: String?
    ) : LaunchSessionResult

    data object DirectoryCollision :
        LaunchSessionResult

    data class DirectoryFailure(
        val message: String
    ) : LaunchSessionResult

    data class Failure(
        val message: String
    ) : LaunchSessionResult
}
