package com.example.groupaac.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.groupaac.data.entity.SignalSnoozeEntity
import com.example.groupaac.data.entity.StatusSignalEntity
import com.example.groupaac.model.SignalState
import com.example.groupaac.model.SignalType
import kotlinx.coroutines.flow.Flow

data class SignalWithUser(
    val id: String,
    val sessionId: String,
    val userId: String,
    val displayName: String,
    val type: SignalType,
    val state: SignalState,
    val createdAt: Long,
    val clearedAt: Long?
)

@Dao
interface StatusSignalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSignal(signal: StatusSignalEntity)

    @Query("SELECT * FROM status_signals WHERE id = :signalId LIMIT 1")
    suspend fun getSignal(signalId: String): StatusSignalEntity?

    @Query(
        """
        SELECT status_signals.id,
               status_signals.sessionId,
               status_signals.userId,
               COALESCE(users.displayName, session_members.displayName, 'Unknown') AS displayName,
               status_signals.type,
               CASE
                   WHEN signal_snoozes.signalId IS NOT NULL THEN 'SNOOZED'
                   ELSE status_signals.state
               END AS state,
               status_signals.createdAt,
               status_signals.clearedAt
        FROM status_signals
        LEFT JOIN users ON users.uid = status_signals.userId
        LEFT JOIN session_members
            ON session_members.sessionId = status_signals.sessionId
            AND session_members.userId = status_signals.userId
        LEFT JOIN signal_snoozes
            ON signal_snoozes.signalId = status_signals.id
            AND signal_snoozes.facilitatorUserId = :facilitatorUserId
        WHERE status_signals.sessionId = :sessionId
            AND status_signals.state = 'CURRENT'
        ORDER BY status_signals.createdAt DESC
        """
    )
    fun observeActiveSignals(
        sessionId: String,
        facilitatorUserId: String?
    ): Flow<List<SignalWithUser>>

    @Query(
        """
        SELECT *
        FROM status_signals
        WHERE sessionId = :sessionId
            AND userId = :userId
            AND state = 'CURRENT'
        ORDER BY createdAt DESC
        LIMIT 1
        """
    )
    fun observeCurrentSignal(
        sessionId: String,
        userId: String
    ): Flow<StatusSignalEntity?>

    @Query(
        """
        SELECT *
        FROM status_signals
        WHERE sessionId = :sessionId
            AND userId = :userId
            AND state = 'CURRENT'
        ORDER BY createdAt DESC
        LIMIT 1
        """
    )
    suspend fun getCurrentSignal(
        sessionId: String,
        userId: String
    ): StatusSignalEntity?

    @Query(
        """
        UPDATE status_signals
        SET state = 'CLEARED', clearedAt = :clearedAt
        WHERE sessionId = :sessionId
            AND userId = :userId
            AND state = 'CURRENT'
        """
    )
    suspend fun clearCurrentSignalsForUser(
        sessionId: String,
        userId: String,
        clearedAt: Long
    )

    @Query(
        """
        UPDATE status_signals
        SET state = 'CLEARED', clearedAt = :clearedAt
        WHERE sessionId = :sessionId
            AND state = 'CURRENT'
        """
    )
    suspend fun clearCurrentSignalsForSession(
        sessionId: String,
        clearedAt: Long
    )

    @Query(
        """
        UPDATE status_signals
        SET state = 'CLEARED', clearedAt = :clearedAt
        WHERE id = :signalId
            AND state = 'CURRENT'
        """
    )
    suspend fun clearSignal(
        signalId: String,
        clearedAt: Long
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnooze(snooze: SignalSnoozeEntity)

    @Query(
        """
        DELETE FROM signal_snoozes
        WHERE signalId = :signalId
            AND facilitatorUserId = :facilitatorUserId
        """
    )
    suspend fun deleteSnooze(
        signalId: String,
        facilitatorUserId: String
    )

    @Query("DELETE FROM signal_snoozes WHERE signalId = :signalId")
    suspend fun deleteSnoozesForSignal(signalId: String)

    @Query(
        """
        DELETE FROM signal_snoozes
        WHERE signalId IN (
            SELECT id FROM status_signals
            WHERE sessionId = :sessionId
              AND userId = :userId
        )
        """
    )
    suspend fun deleteSnoozesForUser(
        sessionId: String,
        userId: String
    )

    @Transaction
    suspend fun clearCurrentSignalsAndSnoozesForUser(
        sessionId: String,
        userId: String,
        clearedAt: Long
    ) {
        clearCurrentSignalsForUser(sessionId, userId, clearedAt)
        deleteSnoozesForUser(sessionId, userId)
    }
}
