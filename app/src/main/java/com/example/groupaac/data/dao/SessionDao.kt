package com.example.groupaac.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.SessionMemberEntity
import com.example.groupaac.model.SessionRole
import kotlinx.coroutines.flow.Flow

data class SessionParticipantRow(
    val sessionId: String,
    val userId: String,
    val displayName: String,
    val role: SessionRole,
    val joinedAt: Long
)

@Dao
interface SessionDao {
    @Query(
        """
        SELECT * FROM sessions
        ORDER BY COALESCE(
            actualStartedAt,
            scheduledStartAt,
            createdAt
        ) DESC
        """
    )
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    fun observeSession(id: String): Flow<SessionEntity?>

    @Query(
        """
        SELECT * FROM sessions
        WHERE hostUserId = :hostUserId
          AND status = 'SCHEDULED'
          AND scheduledStartAt IS NOT NULL
          AND scheduledStartAt >= :dayStartMillis
        ORDER BY scheduledStartAt ASC
        """
    )
    fun observeUpcomingHostedSessions(
        hostUserId: String,
        dayStartMillis: Long
    ): Flow<List<SessionEntity>>

    @Query(
        """
        SELECT * FROM sessions
        WHERE hostUserId = :hostUserId
          AND status = 'LIVE'
        ORDER BY actualStartedAt DESC
        """
    )
    fun observeLiveHostedSessions(
        hostUserId: String
    ): Flow<List<SessionEntity>>

    @Query(
        """
        SELECT * FROM sessions
        WHERE hostUserId = :hostUserId
          AND (
              status IN ('ENDED', 'CANCELLED')
              OR (
                  status = 'SCHEDULED'
                  AND scheduledStartAt IS NOT NULL
                  AND scheduledStartAt < :dayStartMillis
              )
          )
        ORDER BY COALESCE(
            actualEndedAt,
            scheduledStartAt,
            createdAt
        ) DESC
        """
    )
    fun observePastHostedSessions(
        hostUserId: String,
        dayStartMillis: Long
    ): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE joinCode = :joinCode LIMIT 1")
    suspend fun getSessionByCode(joinCode: String): SessionEntity?

    @Query("SELECT COUNT(*) FROM sessions WHERE joinCode = :joinCode")
    suspend fun countSessionsByJoinCode(joinCode: String): Int

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getSession(id: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: SessionEntity)

    @Query(
        """
        UPDATE sessions
        SET status = 'LIVE',
            actualStartedAt = :startedAt
        WHERE id = :sessionId
          AND status != 'LIVE'
        """
    )
    suspend fun markSessionStartedIfNeeded(
        sessionId: String,
        startedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE sessions
        SET status = 'ENDED',
            actualEndedAt = :endedAt
        WHERE id = :sessionId
        """
    )
    suspend fun markSessionEnded(
        sessionId: String,
        endedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE sessions
        SET status = 'SCHEDULED',
            scheduledStartAt = :scheduledStartAt,
            scheduledDurationMinutes = :scheduledDurationMinutes
        WHERE id = :sessionId
        """
    )
    suspend fun updateSchedule(
        sessionId: String,
        scheduledStartAt: Long?,
        scheduledDurationMinutes: Int?
    )

    @Query("DELETE FROM session_members WHERE sessionId = :sessionId")
    suspend fun deleteMembersForSession(sessionId: String)

    @Query(
        """
        DELETE FROM sessions
        WHERE id = :sessionId
          AND hostUserId = :hostUserId
        """
    )
    suspend fun deleteHostedSession(
        sessionId: String,
        hostUserId: String
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: SessionMemberEntity)

    @Query(
        """
        SELECT * FROM session_members
        WHERE sessionId = :sessionId
        ORDER BY joinedAt ASC
        """
    )
    suspend fun getMembersForSession(
        sessionId: String
    ): List<SessionMemberEntity>

    @Query(
        """
        DELETE FROM session_members
        WHERE sessionId = :sessionId
          AND userId = :userId
        """
    )
    suspend fun deleteMember(
        sessionId: String,
        userId: String
    )

    @Query("SELECT userId FROM session_members WHERE sessionId = :sessionId")
    fun observeMemberIds(sessionId: String): Flow<List<String>>

    @Query(
        """
        SELECT session_members.sessionId,
               session_members.userId,
               COALESCE(
                   users.displayName,
                   session_members.displayName
               ) AS displayName,
               session_members.role,
               session_members.joinedAt
        FROM session_members
        LEFT JOIN users
            ON users.uid = session_members.userId
        WHERE session_members.sessionId = :sessionId
        ORDER BY session_members.joinedAt ASC
        """
    )
    fun observeMembers(
        sessionId: String
    ): Flow<List<SessionParticipantRow>>

    @Query(
        """
        SELECT session_members.sessionId,
               session_members.userId,
               COALESCE(
                   users.displayName,
                   session_members.displayName
               ) AS displayName,
               session_members.role,
               session_members.joinedAt
        FROM session_members
        LEFT JOIN users
            ON users.uid = session_members.userId
        WHERE session_members.sessionId = :sessionId
          AND session_members.userId = :userId
        LIMIT 1
        """
    )
    fun observeMember(
        sessionId: String,
        userId: String
    ): Flow<SessionParticipantRow?>

    @Query(
        """
        SELECT session_members.sessionId,
               session_members.userId,
               COALESCE(
                   users.displayName,
                   session_members.displayName
               ) AS displayName,
               session_members.role,
               session_members.joinedAt
        FROM session_members
        LEFT JOIN users
            ON users.uid = session_members.userId
        WHERE session_members.sessionId = :sessionId
          AND session_members.userId = :userId
        LIMIT 1
        """
    )
    suspend fun getMember(
        sessionId: String,
        userId: String
    ): SessionParticipantRow?

    @Transaction
    suspend fun createOrJoinSession(
        session: SessionEntity,
        member: SessionMemberEntity
    ) {
        upsertSession(session)
        upsertMember(member)
    }
}
