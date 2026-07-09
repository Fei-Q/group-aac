package com.example.groupaac.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
    val resolvedAt: Long?
)

@Dao
interface StatusSignalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSignal(signal: StatusSignalEntity)

    @Query("""
        SELECT status_signals.*, COALESCE(users.displayName, session_members.displayName, 'Unknown') AS displayName
        FROM status_signals
        LEFT JOIN users ON users.id = status_signals.userId
        LEFT JOIN session_members 
            ON session_members.sessionId = status_signals.sessionId 
            AND session_members.userId = status_signals.userId
        WHERE status_signals.sessionId = :sessionId 
            AND status_signals.state IN ('CURRENT', 'SNOOZED', 'ACTIVE')
        ORDER BY status_signals.createdAt DESC
    """)
    fun observeActiveSignals(sessionId: String): Flow<List<SignalWithUser>>

    @Query("""
        SELECT *
        FROM status_signals
        WHERE sessionId = :sessionId 
            AND userId = :userId 
            AND state IN ('CURRENT', 'SNOOZED', 'ACTIVE')
        ORDER BY createdAt DESC
        LIMIT 1
    """)
    fun observeCurrentSignal(
        sessionId: String,
        userId: String
    ): Flow<StatusSignalEntity?>

    @Query("UPDATE status_signals SET state = 'RESOLVED', resolvedAt = :resolvedAt WHERE id = :signalId")
    suspend fun resolveSignal(
        signalId: String,
        resolvedAt: Long
    )

    @Query("""
        UPDATE status_signals 
        SET state = 'SNOOZED' 
        WHERE id = :signalId 
            AND state IN ('CURRENT', 'ACTIVE')
    """)
    suspend fun snoozeSignal(signalId: String)

    @Query("""
        UPDATE status_signals 
        SET state = 'RESOLVED', resolvedAt = :resolvedAt 
        WHERE sessionId = :sessionId 
            AND userId = :userId 
            AND state IN ('CURRENT', 'SNOOZED', 'ACTIVE')
    """)
    suspend fun resolveSignalsForUser(
        sessionId: String,
        userId: String,
        resolvedAt: Long
    )

    @Query("""
        UPDATE status_signals 
        SET state = 'CLEARED', resolvedAt = :clearedAt
        WHERE sessionId = :sessionId 
            AND userId = :userId 
            AND state IN ('CURRENT', 'SNOOZED', 'ACTIVE')
    """)
    suspend fun clearCurrentSignalsForUser(
        sessionId: String,
        userId: String,
        clearedAt: Long
    )

    @Query("""
        UPDATE status_signals 
        SET state = 'CLEARED', resolvedAt = :clearedAt
        WHERE sessionId = :sessionId 
            AND state IN ('CURRENT', 'SNOOZED', 'ACTIVE')
    """)
    suspend fun clearCurrentSignalsForSession(
        sessionId: String,
        clearedAt: Long
    )

    @Query("""
        UPDATE status_signals 
        SET state = 'CURRENT', resolvedAt = NULL
        WHERE id = :signalId
            AND state = 'SNOOZED'
    """)
    suspend fun unsnoozeSignal(signalId: String)

    @Query("""
        UPDATE status_signals 
        SET state = 'CLEARED', resolvedAt = :clearedAt
        WHERE id = :signalId
            AND state IN ('CURRENT', 'SNOOZED', 'ACTIVE')
    """)
    suspend fun clearSignal(
        signalId: String,
        clearedAt: Long
    )
}
