package com.example.groupaac.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.groupaac.data.entity.FacilitatorNoteEntity
import com.example.groupaac.data.entity.QuickLogEntity
import kotlinx.coroutines.flow.Flow

data class NoteWithParticipant(
    val id: String,
    val sessionId: String,
    val participantUserId: String?,
    val participantName: String?,
    val facilitatorUserId: String,
    val text: String,
    val createdAt: Long
)

data class QuickLogWithParticipant(
    val id: String,
    val sessionId: String,
    val participantUserId: String,
    val participantName: String,
    val facilitatorUserId: String,
    val label: String,
    val createdAt: Long
)

data class ParticipantStatsRow(
    val userId: String,
    val displayName: String,
    val messageCount: Int,
    val supportRequests: Int,
    val lastMessageAt: Long?,
    val lastSignalAt: Long?
)

@Dao
interface FacilitatorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: FacilitatorNoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuickLog(log: QuickLogEntity)

    @Query("""
        SELECT facilitator_notes.id, facilitator_notes.sessionId, facilitator_notes.participantUserId,
               COALESCE(users.displayName, session_members.displayName, 'Session note') AS participantName, facilitator_notes.facilitatorUserId,
               facilitator_notes.text, facilitator_notes.createdAt
        FROM facilitator_notes
        LEFT JOIN users ON users.uid = facilitator_notes.participantUserId
        LEFT JOIN session_members ON session_members.sessionId = facilitator_notes.sessionId AND session_members.userId = facilitator_notes.participantUserId
        WHERE facilitator_notes.sessionId = :sessionId
        ORDER BY facilitator_notes.createdAt DESC
    """)
    fun observeNotes(sessionId: String): Flow<List<NoteWithParticipant>>

    @Query("""
        SELECT quick_logs.id, quick_logs.sessionId, quick_logs.participantUserId,
               COALESCE(users.displayName, session_members.displayName, 'Unknown') AS participantName, quick_logs.facilitatorUserId,
               quick_logs.label, quick_logs.createdAt
        FROM quick_logs
        LEFT JOIN users ON users.uid = quick_logs.participantUserId
        LEFT JOIN session_members ON session_members.sessionId = quick_logs.sessionId AND session_members.userId = quick_logs.participantUserId
        WHERE quick_logs.sessionId = :sessionId
        ORDER BY quick_logs.createdAt DESC
    """)
    fun observeQuickLogs(sessionId: String): Flow<List<QuickLogWithParticipant>>

    @Query("""
        SELECT session_members.userId AS userId,
               COALESCE(users.displayName, session_members.displayName) AS displayName,
               COUNT(DISTINCT messages.id) AS messageCount,
               COUNT(DISTINCT CASE WHEN status_signals.type IN ('HELP', 'REPEAT') THEN status_signals.id END) AS supportRequests,
               MAX(messages.createdAt) AS lastMessageAt,
               MAX(status_signals.createdAt) AS lastSignalAt
        FROM session_members
        LEFT JOIN users ON users.uid = session_members.userId
        LEFT JOIN messages ON messages.senderUserId = session_members.userId AND messages.sessionId = :sessionId AND messages.status != 'DELETED'
        LEFT JOIN status_signals ON status_signals.userId = session_members.userId AND status_signals.sessionId = :sessionId
        WHERE session_members.sessionId = :sessionId AND session_members.role = 'PARTICIPANT'
        GROUP BY session_members.userId, COALESCE(users.displayName, session_members.displayName)
        ORDER BY COALESCE(users.displayName, session_members.displayName) ASC
    """)
    fun observeParticipantStats(sessionId: String): Flow<List<ParticipantStatsRow>>

    @Query("SELECT COUNT(*) FROM session_members WHERE sessionId = :sessionId AND role = 'PARTICIPANT'")
    fun observeParticipantCount(sessionId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sessionId AND status != 'DELETED'")
    fun observeSharedItemCount(sessionId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM status_signals WHERE sessionId = :sessionId AND type IN ('HELP', 'REPEAT')")
    fun observeSupportRequestCount(sessionId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sessionId AND saved = 1")
    fun observeSavedItemCount(sessionId: String): Flow<Int>
}
