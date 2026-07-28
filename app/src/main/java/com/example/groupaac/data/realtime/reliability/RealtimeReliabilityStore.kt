package com.example.groupaac.data.realtime.reliability

import androidx.room.withTransaction
import com.example.groupaac.data.AppDatabase
import com.example.groupaac.data.dao.ReliabilityDao
import com.example.groupaac.data.entity.ChannelCursorEntity
import com.example.groupaac.data.entity.DisplayStateEntity
import com.example.groupaac.data.entity.OutboxEventEntity
import com.example.groupaac.data.entity.ProcessedEventEntity
import com.example.groupaac.data.realtime.protocol.ReceivedRealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeEvent
import com.example.groupaac.data.realtime.protocol.RealtimeEventCodec
import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.OutboxEventState

class RealtimeReliabilityStore(
    private val database: AppDatabase,
    private val reliabilityDao: ReliabilityDao
) {
    suspend fun enqueueOutboxEvent(
        channel: String,
        event: RealtimeEvent,
        now: Long
    ) {
        reliabilityDao.upsertOutboxEvent(
            OutboxEventEntity(
                eventId = event.eventId,
                actorUserId = event.actorUserId,
                sessionId = event.sessionId,
                channel = channel,
                serializedEvent = RealtimeEventCodec.encode(event),
                nextAttemptAt = now,
                createdAt = now,
                expiresAt = event.expiresAt
            )
        )
    }

    suspend fun markSending(
        eventId: String,
        attemptCount: Int,
        now: Long
    ) {
        reliabilityDao.updateOutboxAttempt(
            eventId = eventId,
            state = OutboxEventState.SENDING,
            attemptCount = attemptCount,
            nextAttemptAt = now + nextRetryDelayMillis(attemptCount)
        )
    }

    suspend fun markFailed(eventId: String, attemptCount: Int, now: Long) {
        reliabilityDao.updateOutboxAttempt(
            eventId = eventId,
            state = OutboxEventState.FAILED,
            attemptCount = attemptCount,
            nextAttemptAt = now + nextRetryDelayMillis(attemptCount)
        )
    }

    suspend fun markSent(eventId: String, acceptedTimetoken: Long?) {
        reliabilityDao.markOutboxSent(eventId, acceptedTimetoken)
    }

    suspend fun getRetryableEvents(
        now: Long,
        limit: Int
    ): List<OutboxEventEntity> {
        return reliabilityDao.getRetryableOutboxEvents(now, limit)
            .filterNot { event ->
                event.expiresAt != null && event.expiresAt <= now
            }
    }

    suspend fun recordProcessedEvent(
        received: ReceivedRealtimeEvent,
        now: Long
    ): Boolean {
        return reliabilityDao.recordProcessedEventAndCursor(
            processed = ProcessedEventEntity(
                eventId = received.event.eventId,
                channel = received.channel,
                timetoken = received.timetoken,
                processedAt = now
            ),
            cursor = ChannelCursorEntity(
                channel = received.channel,
                lastProcessedTimetoken = received.timetoken,
                updatedAt = now
            )
        )
    }

    suspend fun hasProcessed(eventId: String): Boolean {
        return reliabilityDao.getProcessedEvent(eventId) != null
    }

    suspend fun applyDisplayStateIfNewer(
        sessionId: String,
        eventId: String,
        currentMessageId: String?,
        isPinned: Boolean,
        displayMode: DisplayMode,
        commandTimetoken: Long,
        now: Long
    ): Boolean {
        return database.withTransaction {
            val current = reliabilityDao.getDisplayState(sessionId)
            if (
                current?.lastAppliedCommandTimetoken != null &&
                commandTimetoken <= current.lastAppliedCommandTimetoken
            ) {
                false
            } else {
                reliabilityDao.upsertDisplayState(
                    DisplayStateEntity(
                        sessionId = sessionId,
                        currentMessageId = currentMessageId,
                        isPinned = isPinned,
                        displayMode = displayMode,
                        lastAppliedCommandTimetoken = commandTimetoken,
                        lastAppliedCommandEventId = eventId,
                        updatedAt = now
                    )
                )
                true
            }
        }
    }

    companion object {
        fun nextRetryDelayMillis(attemptCount: Int): Long {
            return when (attemptCount.coerceAtLeast(1)) {
                1 -> 1_000L
                2 -> 2_000L
                3 -> 4_000L
                4 -> 8_000L
                else -> 30_000L
            }
        }

        fun isExpired(event: RealtimeEvent, now: Long): Boolean {
            return event.expiresAt?.let { it <= now } ?: false
        }
    }
}
