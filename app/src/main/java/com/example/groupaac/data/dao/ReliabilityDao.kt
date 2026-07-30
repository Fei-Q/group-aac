package com.example.groupaac.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.groupaac.data.entity.ChannelCursorEntity
import com.example.groupaac.data.entity.DisplayStateEntity
import com.example.groupaac.data.entity.OutboxEventEntity
import com.example.groupaac.data.entity.ProcessedEventEntity
import com.example.groupaac.model.OutboxEventState
import kotlinx.coroutines.flow.Flow

@Dao
interface ReliabilityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOutboxEvent(event: OutboxEventEntity)

    @Query("SELECT * FROM outbox_events WHERE eventId = :eventId LIMIT 1")
    suspend fun getOutboxEvent(eventId: String): OutboxEventEntity?

    @Query(
        """
        SELECT * FROM outbox_events
        WHERE state IN ('PENDING', 'FAILED')
          AND actorUserId = :actorUserId
          AND nextAttemptAt <= :now
          AND attemptCount < :maxAttempts
        ORDER BY nextAttemptAt ASC, createdAt ASC
        LIMIT :limit
        """
    )
    suspend fun getRetryableOutboxEvents(
        actorUserId: String,
        now: Long,
        maxAttempts: Int,
        limit: Int
    ): List<OutboxEventEntity>

    @Query(
        """
        SELECT * FROM outbox_events
        WHERE state = 'SENDING'
          AND actorUserId = :actorUserId
          AND nextAttemptAt <= :now
        ORDER BY nextAttemptAt ASC, createdAt ASC
        """
    )
    suspend fun getStaleSendingOutboxEvents(
        actorUserId: String,
        now: Long
    ): List<OutboxEventEntity>

    @Query(
        """
        UPDATE outbox_events
        SET state = 'SENDING',
            attemptCount = :attemptCount,
            nextAttemptAt = :leaseUntil
        WHERE eventId = :eventId
          AND actorUserId = :actorUserId
          AND state IN ('PENDING', 'FAILED')
          AND nextAttemptAt <= :now
        """
    )
    suspend fun claimOutboxEvent(
        eventId: String,
        actorUserId: String,
        attemptCount: Int,
        now: Long,
        leaseUntil: Long
    ): Int

    @Query(
        """
        UPDATE outbox_events
        SET state = 'PENDING',
            attemptCount = :attemptCount,
            nextAttemptAt = :now
        WHERE eventId = :eventId
          AND actorUserId = :actorUserId
          AND state = 'SENDING'
        """
    )
    suspend fun releaseClaimedOutboxEvent(
        eventId: String,
        actorUserId: String,
        attemptCount: Int,
        now: Long
    ): Int

    @Query(
        """
        UPDATE outbox_events
        SET state = :state,
            attemptCount = :attemptCount,
            nextAttemptAt = :nextAttemptAt
        WHERE eventId = :eventId
        """
    )
    suspend fun updateOutboxAttempt(
        eventId: String,
        state: OutboxEventState,
        attemptCount: Int,
        nextAttemptAt: Long
    )

    @Query(
        """
        UPDATE outbox_events
        SET state = 'SENT',
            acceptedTimetoken = :acceptedTimetoken
        WHERE eventId = :eventId
        """
    )
    suspend fun markOutboxSent(
        eventId: String,
        acceptedTimetoken: Long?
    )

    @Query(
        """
        UPDATE outbox_events
        SET state = 'PENDING',
            attemptCount = 0,
            acceptedTimetoken = NULL,
            nextAttemptAt = :now
        WHERE eventId = :eventId
        """
    )
    suspend fun retryOutboxEvent(
        eventId: String,
        now: Long
    )

    @Query(
        """
        SELECT MIN(nextAttemptAt) FROM outbox_events
        WHERE actorUserId = :actorUserId
          AND state IN ('PENDING', 'FAILED')
          AND nextAttemptAt > :now
          AND attemptCount < :maxAttempts
        """
    )
    suspend fun getEarliestFutureRetryTime(
        actorUserId: String,
        now: Long,
        maxAttempts: Int
    ): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProcessedEvent(event: ProcessedEventEntity): Long

    @Query("SELECT * FROM processed_events WHERE eventId = :eventId LIMIT 1")
    suspend fun getProcessedEvent(eventId: String): ProcessedEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannelCursor(cursor: ChannelCursorEntity)

    @Query("SELECT * FROM channel_cursors WHERE channel = :channel LIMIT 1")
    suspend fun getChannelCursor(channel: String): ChannelCursorEntity?

    @Query(
        """
        UPDATE channel_cursors
        SET lastProcessedTimetoken = CASE
                WHEN lastProcessedTimetoken > :timetoken
                    THEN lastProcessedTimetoken
                ELSE :timetoken
            END,
            updatedAt = :updatedAt
        WHERE channel = :channel
        """
    )
    suspend fun advanceExistingChannelCursor(
        channel: String,
        timetoken: Long,
        updatedAt: Long
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDisplayState(state: DisplayStateEntity)

    @Query("SELECT * FROM display_state WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getDisplayState(sessionId: String): DisplayStateEntity?

    @Query("SELECT * FROM display_state WHERE sessionId = :sessionId LIMIT 1")
    fun observeDisplayState(sessionId: String): Flow<DisplayStateEntity?>

    @Transaction
    suspend fun recordProcessedEventAndCursor(
        processed: ProcessedEventEntity,
        cursor: ChannelCursorEntity
    ): Boolean {
        val inserted = insertProcessedEvent(processed)
        if (inserted == -1L) {
            return false
        }
        if (
            advanceExistingChannelCursor(
                channel = cursor.channel,
                timetoken = cursor.lastProcessedTimetoken,
                updatedAt = cursor.updatedAt
            ) == 0
        ) {
            upsertChannelCursor(cursor)
        }
        return true
    }
}
