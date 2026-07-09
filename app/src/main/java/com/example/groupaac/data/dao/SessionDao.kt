package com.example.groupaac.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.model.UserRole
import kotlinx.coroutines.flow.Flow

data class SessionParticipantRow(
    val sessionId: String,
    val userId: String,
    val displayName: String,
    val role: UserRole,
    val joinedAt: Long
)

@Dao
interface SessionDao {
    @Query("""
        SELECT * FROM sessions
        ORDER BY COALESCE(actualStartedAt, scheduledStartAt, createdAt) DESC
    """)
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    fun observeSession(id: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE joinCode = :joinCode LIMIT 1")
    suspend fun getSessionByCode(joinCode: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getSession(id: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: SessionEntity)

    @Query("""
        UPDATE sessions
        SET actualStartedAt = :startedAt
        WHERE id = :sessionId
          AND actualStartedAt IS NULL
    """)
    suspend fun markSessionStartedIfNeeded(
        sessionId: String,
        startedAt: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE sessions
        SET actualEndedAt = :endedAt
        WHERE id = :sessionId
    """)
    suspend fun markSessionEnded(
        sessionId: String,
        endedAt: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE sessions
        SET scheduledStartAt = :scheduledStartAt,
            scheduledDurationMinutes = :scheduledDurationMinutes
        WHERE id = :sessionId
    """)
    suspend fun updateSchedule(
        sessionId: String,
        scheduledStartAt: Long?,
        scheduledDurationMinutes: Int?
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: SessionMemberEntity)

    @Query("SELECT userId FROM session_members WHERE sessionId = :sessionId")
    fun observeMemberIds(sessionId: String): Flow<List<String>>

    @Query("""
        SELECT session_members.sessionId,
               session_members.userId,
               COALESCE(users.displayName, session_members.displayName) AS displayName,
               session_members.role,
               session_members.joinedAt
        FROM session_members
        LEFT JOIN users ON users.id = session_members.userId
        WHERE session_members.sessionId = :sessionId
        ORDER BY session_members.joinedAt ASC
    """)
    fun observeMembers(sessionId: String): Flow<List<SessionParticipantRow>>

    @Query("""
        SELECT session_members.sessionId,
               session_members.userId,
               COALESCE(users.displayName, session_members.displayName) AS displayName,
               session_members.role,
               session_members.joinedAt
        FROM session_members
        LEFT JOIN users ON users.id = session_members.userId
        WHERE session_members.sessionId = :sessionId AND session_members.userId = :userId
        LIMIT 1
    """)
    suspend fun getMember(sessionId: String, userId: String): SessionParticipantRow?

    @Transaction
    suspend fun createOrJoinSession(session: SessionEntity, member: SessionMemberEntity) {
        upsertSession(session)
        upsertMember(member)
    }
}