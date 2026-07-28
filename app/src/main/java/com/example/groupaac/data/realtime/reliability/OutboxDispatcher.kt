package com.example.groupaac.data.realtime.reliability

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.groupaac.GroupAacApplication
import com.example.groupaac.data.AppDatabase
import com.example.groupaac.data.realtime.RealtimeClientManager
import com.example.groupaac.data.realtime.protocol.RealtimeEvent
import com.example.groupaac.data.realtime.sync.DisplayMessagePayload
import com.example.groupaac.model.MessageDisplayStatus
import com.example.groupaac.model.MessageTransportStatus
import com.example.groupaac.model.OutboxDomainType
import java.util.concurrent.atomic.AtomicBoolean
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
    private val fallbackScheduler: () -> Unit = {
        enqueueFallback(context)
    }
) : OutboxDispatching {
    private val running = AtomicBoolean(false)
    private val json = Json { ignoreUnknownKeys = true }

    override fun requestImmediateDispatch() {
        runCatching { fallbackScheduler() }
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
        reliabilityStore.recoverStaleSending(now)

        while (true) {
            val due = reliabilityStore.getRetryableEvents(
                now = clock(),
                limit = 25
            ).filter { it.attemptCount < RealtimeReliabilityStore.MAX_ATTEMPTS }
            if (due.isEmpty()) {
                return
            }

            val client = realtimeClientManager.requireClient()
            due.forEach { entry ->
                val nextAttempt = entry.attemptCount + 1
                reliabilityStore.markSending(
                    eventId = entry.eventId,
                    attemptCount = nextAttempt,
                    now = clock()
                )
                try {
                    val event = reliabilityStore.decodeOutboxEvent(entry)
                    val timetoken = client.publish(entry.channel, event)
                    reliabilityStore.markSent(entry.eventId, timetoken)
                    applySuccessfulDelivery(entry, event)
                } catch (_: Throwable) {
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

        private fun enqueueFallback(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<OutboxDispatchWorker>().build()
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
) : Worker(appContext, params) {
    override fun doWork(): Result {
        val dispatcher =
            (applicationContext as GroupAacApplication)
                .appContainer
                .outboxDispatcher
        kotlinx.coroutines.runBlocking {
            dispatcher.dispatchDueEvents()
        }
        return Result.success()
    }
}
