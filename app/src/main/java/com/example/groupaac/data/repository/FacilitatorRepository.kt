package com.example.groupaac.data.repository

import com.example.groupaac.data.dao.FacilitatorDao
import com.example.groupaac.data.dao.NoteWithParticipant
import com.example.groupaac.data.dao.ParticipantStatsRow
import com.example.groupaac.data.dao.QuickLogWithParticipant
import com.example.groupaac.data.entity.FacilitatorNoteEntity
import com.example.groupaac.data.entity.QuickLogEntity
import com.example.groupaac.util.IdUtils
import com.example.groupaac.util.TimeUtils
import kotlinx.coroutines.flow.Flow

class FacilitatorRepository(private val facilitatorDao: FacilitatorDao) {
    fun observeParticipantStats(sessionId: String): Flow<List<ParticipantStatsRow>> = facilitatorDao.observeParticipantStats(sessionId)
    fun observeNotes(sessionId: String): Flow<List<NoteWithParticipant>> = facilitatorDao.observeNotes(sessionId)
    fun observeQuickLogs(sessionId: String): Flow<List<QuickLogWithParticipant>> = facilitatorDao.observeQuickLogs(sessionId)
    fun observeParticipantCount(sessionId: String): Flow<Int> = facilitatorDao.observeParticipantCount(sessionId)
    fun observeSharedItemCount(sessionId: String): Flow<Int> = facilitatorDao.observeSharedItemCount(sessionId)
    fun observeSupportRequestCount(sessionId: String): Flow<Int> = facilitatorDao.observeSupportRequestCount(sessionId)
    fun observeSavedItemCount(sessionId: String): Flow<Int> = facilitatorDao.observeSavedItemCount(sessionId)

    suspend fun addNote(sessionId: String, participantUserId: String?, facilitatorUserId: String, text: String) {
        if (text.isBlank()) return
        facilitatorDao.insertNote(
            FacilitatorNoteEntity(
                id = IdUtils.newId(),
                sessionId = sessionId,
                participantUserId = participantUserId,
                facilitatorUserId = facilitatorUserId,
                text = text.trim(),
                createdAt = TimeUtils.now()
            )
        )
    }

    suspend fun quickLog(sessionId: String, participantUserId: String, facilitatorUserId: String, label: String) {
        facilitatorDao.insertQuickLog(
            QuickLogEntity(
                id = IdUtils.newId(),
                sessionId = sessionId,
                participantUserId = participantUserId,
                facilitatorUserId = facilitatorUserId,
                label = label,
                createdAt = TimeUtils.now()
            )
        )
    }
}
