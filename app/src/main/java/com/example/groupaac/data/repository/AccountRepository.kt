package com.example.groupaac.data.repository

import com.example.groupaac.data.dao.UserDao
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.entity.UserSettingsEntity
import com.example.groupaac.data.prefs.AppPreferences
import com.example.groupaac.model.UserRole
import com.example.groupaac.util.IdUtils
import com.example.groupaac.util.TimeUtils
import kotlinx.coroutines.flow.Flow

class AccountRepository(
    private val userDao: UserDao,
    private val preferences: AppPreferences
) {
    val users: Flow<List<UserEntity>> = userDao.observeUsers()
    val activeUserId: Flow<String?> = preferences.activeUserId

    fun observeUser(userId: String): Flow<UserEntity?> = userDao.observeUser(userId)

    suspend fun createLocalUser(displayName: String, role: UserRole): String {
        val id = IdUtils.newId()
        val now = TimeUtils.now()
        userDao.createUserWithSettings(
            UserEntity(id = id, displayName = displayName.trim(), role = role, createdAt = now, lastLoginAt = now),
            UserSettingsEntity(userId = id)
        )
        preferences.setActiveUser(id)
        preferences.setLastRole(role)
        return id
    }

    suspend fun switchUser(userId: String) {
        userDao.updateLastLogin(userId, TimeUtils.now())
        preferences.setActiveUser(userId)
        userDao.getUser(userId)?.let { preferences.setLastRole(it.role) }
    }

    suspend fun signOut() {
        preferences.setActiveUser(null)
    }

    suspend fun updateUser(userId: String, displayName: String, role: UserRole) {
        userDao.getUser(userId)?.let { user ->
            userDao.upsertUser(user.copy(displayName = displayName.trim(), role = role))
            preferences.setLastRole(role)
        }
    }
}
