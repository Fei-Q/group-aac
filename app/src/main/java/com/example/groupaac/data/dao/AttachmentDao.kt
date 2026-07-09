package com.example.groupaac.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.groupaac.data.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<AttachmentEntity>)

    @Query("""
        SELECT * FROM attachments
        WHERE messageId = :messageId
        ORDER BY sortOrder ASC, createdAt ASC
    """)
    fun observeAttachmentsForMessage(messageId: String): Flow<List<AttachmentEntity>>

    @Query("""
        SELECT * FROM attachments
        WHERE messageId IN (:messageIds)
        ORDER BY sortOrder ASC, createdAt ASC
    """)
    fun observeAttachmentsForMessages(messageIds: List<String>): Flow<List<AttachmentEntity>>

    @Query("DELETE FROM attachments WHERE messageId = :messageId")
    suspend fun deleteAttachmentsForMessage(messageId: String)
}