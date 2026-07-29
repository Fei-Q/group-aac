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
import com.example.groupaac.model.DisplayCommandOrigin
import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.OutboxDomainType
import com.example.groupaac.model.OutboxEventState

class RealtimeReliabilityStore(
    private val database: AppDatabase,
    private val reliabilityDao: ReliabilityDao
) {
    suspend fun enqueueOutboxEvent(
        domainType: OutboxDomainType,
        domainId: String,
        channel: String,
        event: RealtimeEvent,
        now: Long
    ) {
        reliabilityDao.upsertOutboxEvent(
            OutboxEventEntity(
                eventId = event.eventId,
                domainType = domainType,
                domainId = domainId,
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

    suspend fun markDisplayCommandPublished(
        sessionId: String,
        eventId: String,
        acceptedTimetoken: Long?
    ) {
        if (acceptedTimetoken == null) {
            return
        }
        val current = reliabilityDao.getDisplayState(sessionId) ?: return
        if (current.lastIssuedCommandEventId != eventId) {
            return
        }
        reliabilityDao.upsertDisplayState(
            current.copy(
                lastPublishedCommandTimetoken = acceptedTimetoken
            )
        )
    }

    suspend fun recoverStaleSending(now: Long): List<OutboxEventEntity> {
        val stale = reliabilityDao.getStaleSendingOutboxEvents(now)
            .filterNot { event ->
                event.expiresAt != null && event.expiresAt <= now
            }
        stale.forEach { event ->
            reliabilityDao.updateOutboxAttempt(
                eventId = event.eventId,
                state = OutboxEventState.FAILED,
                attemptCount = event.attemptCount,
                nextAttemptAt = now
            )
        }
        return stale
    }

    suspend fun retryNow(eventId: String, now: Long) {
        reliabilityDao.retryOutboxEvent(eventId, now)
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

    suspend fun getChannelCursor(channel: String): ChannelCursorEntity? =
        reliabilityDao.getChannelCursor(channel)

    suspend fun decodeOutboxEvent(
        entry: OutboxEventEntity
    ): RealtimeEvent = RealtimeEventCodec.decode(entry.serializedEvent)

    suspend fun updateLocalDisplayState(
        sessionId: String,
        eventId: String,
        currentMessageId: String?,
        isPinned: Boolean,
        displayMode: DisplayMode,
        commandOrigin: DisplayCommandOrigin?,
        now: Long
    ) {
        database.withTransaction {
            upsertLocalDisplayState(
                sessionId = sessionId,
                eventId = eventId,
                currentMessageId = currentMessageId,
                isPinned = isPinned,
                displayMode = displayMode,
                commandOrigin = commandOrigin,
                now = now
            )
        }
    }

    suspend fun upsertLocalDisplayState(
        sessionId: String,
        eventId: String,
        currentMessageId: String?,
        isPinned: Boolean,
        displayMode: DisplayMode,
        commandOrigin: DisplayCommandOrigin?,
        now: Long
    ) {
        val current = reliabilityDao.getDisplayState(sessionId)
        reliabilityDao.upsertDisplayState(
            DisplayStateEntity(
                sessionId = sessionId,
                currentMessageId = currentMessageId,
                isPinned = isPinned,
                displayMode = displayMode,
                commandOrigin = commandOrigin,
                lastIssuedCommandEventId = eventId,
                lastPublishedCommandTimetoken = null,
                lastPiAppliedCommandTimetoken =
                    current?.lastPiAppliedCommandTimetoken,
                lastAppliedCommandEventId = current?.lastAppliedCommandEventId,
                localOptimisticUpdatedAt = now
            )
        )
    }

    fun isDisplayAcknowledgementFresh(
        current: DisplayStateEntity?,
        inReplyToEventId: String?,
        timetoken: Long
    ): Boolean {
        val issuedEventId = current?.lastIssuedCommandEventId ?: return false
        if (inReplyToEventId != issuedEventId) {
            return false
        }
        if (
            current.lastPublishedCommandTimetoken != null &&
            timetoken <= current.lastPublishedCommandTimetoken
        ) {
            return false
        }
        if (
            current.lastPiAppliedCommandTimetoken != null &&
            timetoken <= current.lastPiAppliedCommandTimetoken
        ) {
            return false
        }
        return true
    }

    companion object {
        const val MAX_ATTEMPTS = 5

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
