package com.example.groupaac.data.repository

import com.example.groupaac.data.dao.UserDao
import com.example.groupaac.data.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(
    private val userDao: UserDao
) {
    fun observeSettings(userId: String): Flow<UserSettingsEntity> {
        return userDao.observeSettings(userId)
            .map { settings ->
                settings ?: UserSettingsEntity(userId = userId)
            }
    }

    suspend fun ensureSettingsExist(userId: String) {
        val existing = userDao.getSettings(userId)
        if (existing == null) {
            userDao.upsertSettings(UserSettingsEntity(userId = userId))
        }
    }

    suspend fun updateSettings(settings: UserSettingsEntity) {
        userDao.upsertSettings(
            settings.copy(updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun updateLowParticipationAlerts(userId: String, enabled: Boolean) {
        userDao.updateLowParticipationAlerts(userId, enabled)
    }

    suspend fun updateMonitorManualApproval(userId: String, required: Boolean) {
        userDao.updateMonitorManualApproval(userId, required)
    }
}
