package com.example.groupaac.data.realtime.reliability

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.groupaac.GroupAacApplication
import com.example.groupaac.data.AppDatabase
import com.example.groupaac.data.realtime.RealtimeClientManager
import com.example.groupaac.data.realtime.protocol.RealtimeEvent
import com.example.groupaac.data.realtime.sync.DisplayMessagePayload
import com.example.groupaac.model.MessageDisplayStatus
import com.example.groupaac.model.MessageTransportStatus
import com.example.groupaac.model.OutboxDomainType
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.serializer
import kotlinx.serialization.json.Json

interface OutboxDispatching {
    fun requestImmediateDispatch()

    suspend fun retryEvent(eventId: String)
}

object NoOpOutboxDispatcher : OutboxDispatching {
    override fun requestImmediateDispatch() = Unit

    override suspend fun retryEvent(eventId: String) = Unit
}

class OutboxDispatcher(
    private val context: Context,
    private val database: AppDatabase,
    private val reliabilityStore: RealtimeReliabilityStore,
    private val realtimeClientManager: RealtimeClientManager,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val workScheduler: (Long) -> Unit = { delayMillis ->
        enqueueFallback(
            context = context,
            delayMillis = delayMillis
        )
    }
) : OutboxDispatching {
    private val running = AtomicBoolean(false)
    private val json = Json { ignoreUnknownKeys = true }

    override fun requestImmediateDispatch() {
        runCatching { workScheduler(0L) }
        if (!running.compareAndSet(false, true)) {
            return
        }
        scope.launch {
            try {
                dispatchDueEvents()
            } finally {
                running.set(false)
            }
        }
    }

    suspend fun dispatchDueEvents(now: Long = clock()) {
        val activeAccount =
            realtimeClientManager.currentAccount()
                ?: return
        val activeUid = activeAccount.userId
        reliabilityStore.recoverStaleSending(
            actorUserId = activeUid,
            now = now
        )

        while (true) {
            val due = reliabilityStore.getRetryableEvents(
                actorUserId = activeUid,
                now = clock(),
                limit = 25
            )
            if (due.isEmpty()) {
                scheduleNextRetry(activeUid)
                return
            }

            due.forEach { entry ->
                val attemptTime = clock()
                val nextAttempt = entry.attemptCount + 1
                val claimed = reliabilityStore.claimSending(
                    eventId = entry.eventId,
                    actorUserId = activeUid,
                    attemptCount = nextAttempt,
                    now = attemptTime
                )
                if (!claimed) {
                    return@forEach
                }
                try {
                    val event = reliabilityStore.decodeOutboxEvent(entry)
                    val publishAccount =
                        realtimeClientManager.currentAccount()
                    check(entry.actorUserId == activeUid) {
                        "Outbox row actorUserId does not match active realtime UID."
                    }
                    check(event.actorUserId == activeUid) {
                        "Decoded outbox event actorUserId does not match active realtime UID."
                    }
                    check(event.actorUserId == entry.actorUserId) {
                        "Decoded outbox event actorUserId does not match stored row actorUserId."
                    }
                    if (
                        publishAccount == null ||
                        publishAccount.userId != entry.actorUserId
                    ) {
                        reliabilityStore.releaseClaim(
                            eventId = entry.eventId,
                            actorUserId = entry.actorUserId
                                ?: return@forEach,
                            attemptCount = entry.attemptCount,
                            now = clock()
                        )
                        return
                    }
                    val timetoken = publishAccount.client.publish(
                        entry.channel,
                        event
                    )
                    reliabilityStore.markSent(entry.eventId, timetoken)
                    if (entry.domainType == OutboxDomainType.DISPLAY) {
                        reliabilityStore.markDisplayCommandPublished(
                            sessionId = entry.sessionId,
                            eventId = entry.eventId,
                            acceptedTimetoken = timetoken
                        )
                    }
                    applySuccessfulDelivery(entry, event)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    reliabilityStore.markFailed(
                        eventId = entry.eventId,
                        attemptCount = nextAttempt,
                        now = clock()
                    )
                    applyFailedDelivery(entry)
                }
            }
        }
    }

    override suspend fun retryEvent(eventId: String) {
        reliabilityStore.retryNow(eventId, clock())
        dispatchDueEvents()
    }

    private suspend fun scheduleNextRetry(activeUid: String) {
        val nextRetryAt = reliabilityStore.getEarliestFutureRetryTime(
            actorUserId = activeUid,
            now = clock()
        ) ?: return
        val delayMillis = (nextRetryAt - clock()).coerceAtLeast(0L)
        runCatching { workScheduler(delayMillis) }
    }

    private suspend fun applySuccessfulDelivery(
        entry: com.example.groupaac.data.entity.OutboxEventEntity,
        event: RealtimeEvent
    ) {
        when (entry.domainType) {
            OutboxDomainType.MESSAGE -> {
                database.messageDao().updateTransportStatus(
                    messageId = entry.domainId,
                    transportStatus = MessageTransportStatus.SENT
                )
            }

            OutboxDomainType.DISPLAY -> {
                val messageId = when (event.type) {
                    "display.show_message",
                    "display.restore_message" -> {
                        payload<DisplayMessagePayload>(event, "display")
                            ?.message
                            ?.id
                    }
                    else -> null
                }
                if (messageId != null) {
                    database.messageDao().updateDisplayStatus(
                        messageId = messageId,
                        displayStatus = MessageDisplayStatus.PENDING
                    )
                }
            }

            else -> Unit
        }
    }

    private suspend fun applyFailedDelivery(
        entry: com.example.groupaac.data.entity.OutboxEventEntity
    ) {
        when (entry.domainType) {
            OutboxDomainType.MESSAGE -> {
                database.messageDao().updateTransportStatus(
                    messageId = entry.domainId,
                    transportStatus = MessageTransportStatus.FAILED
                )
            }

            OutboxDomainType.DISPLAY -> {
                database.messageDao().getMessage(entry.domainId)?.let {
                    database.messageDao().updateDisplayStatus(
                        messageId = it.id,
                        displayStatus = MessageDisplayStatus.FAILED
                    )
                }
            }

            else -> Unit
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "group-aac-outbox-dispatch"
        internal fun buildWorkRequest(delayMillis: Long) =
            OneTimeWorkRequestBuilder<OutboxDispatchWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()

        private fun enqueueFallback(
            context: Context,
            delayMillis: Long
        ) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                buildWorkRequest(delayMillis)
            )
        }
    }

    private inline fun <reified T> payload(
        event: RealtimeEvent,
        key: String
    ): T? {
        val element = event.payload[key] ?: return null
        return json.decodeFromJsonElement(serializer<T>(), element)
    }
}

class OutboxDispatchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val dispatcher =
            (applicationContext as GroupAacApplication)
                .appContainer
                .outboxDispatcher
        dispatcher.dispatchDueEvents()
        return Result.success()
    }
}
