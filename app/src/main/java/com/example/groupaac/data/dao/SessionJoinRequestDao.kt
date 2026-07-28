package com.example.groupaac.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.groupaac.data.entity.SessionJoinRequestEntity
import com.example.groupaac.model.JoinRequestStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionJoinRequestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRequest(request: SessionJoinRequestEntity)

    @Query(
        """
        SELECT * FROM session_join_requests
        WHERE sessionId = :sessionId
          AND status = :pendingStatus
        ORDER BY requestedAt ASC
        """
    )
    fun observePendingBySession(
        sessionId: String,
        pendingStatus: JoinRequestStatus = JoinRequestStatus.PENDING
    ): Flow<List<SessionJoinRequestEntity>>

    @Query("SELECT * FROM session_join_requests WHERE id = :requestId LIMIT 1")
    fun observeRequestById(requestId: String): Flow<SessionJoinRequestEntity?>

    @Query("SELECT * FROM session_join_requests WHERE id = :requestId LIMIT 1")
    suspend fun getRequestById(requestId: String): SessionJoinRequestEntity?

    @Query(
        """
        SELECT * FROM session_join_requests
        WHERE sessionId = :sessionId
          AND userId = :userId
          AND requestedRole = :requestedRole
          AND status = :pendingStatus
        ORDER BY requestedAt DESC
        LIMIT 1
        """
    )
    suspend fun getPendingRequest(
        sessionId: String,
        userId: String,
        requestedRole: com.example.groupaac.model.SessionRole,
        pendingStatus: JoinRequestStatus = JoinRequestStatus.PENDING
    ): SessionJoinRequestEntity?

    @Query(
        """
        UPDATE session_join_requests
        SET status = :approvedStatus,
            decidedAt = :decidedAt,
            decidedByUserId = :decidedByUserId
        WHERE id = :requestId
          AND status = :pendingStatus
        """
    )
    suspend fun approveRequest(
        requestId: String,
        decidedByUserId: String,
        decidedAt: Long = System.currentTimeMillis(),
        approvedStatus: JoinRequestStatus = JoinRequestStatus.APPROVED,
        pendingStatus: JoinRequestStatus = JoinRequestStatus.PENDING
    ): Int

    @Query(
        """
        UPDATE session_join_requests
        SET status = :declinedStatus,
            decidedAt = :decidedAt,
            decidedByUserId = :decidedByUserId
        WHERE id = :requestId
          AND status = :pendingStatus
        """
    )
    suspend fun declineRequest(
        requestId: String,
        decidedByUserId: String,
        decidedAt: Long = System.currentTimeMillis(),
        declinedStatus: JoinRequestStatus = JoinRequestStatus.DECLINED,
        pendingStatus: JoinRequestStatus = JoinRequestStatus.PENDING
    ): Int

    @Query(
        """
        UPDATE session_join_requests
        SET status = :cancelledStatus,
            decidedAt = :decidedAt,
            decidedByUserId = NULL
        WHERE id = :requestId
          AND status = :pendingStatus
        """
    )
    suspend fun cancelRequest(
        requestId: String,
        decidedAt: Long = System.currentTimeMillis(),
        cancelledStatus: JoinRequestStatus = JoinRequestStatus.CANCELLED,
        pendingStatus: JoinRequestStatus = JoinRequestStatus.PENDING
    ): Int

    @Query("DELETE FROM session_join_requests WHERE sessionId = :sessionId")
    suspend fun deleteRequestsForSession(sessionId: String)
}
