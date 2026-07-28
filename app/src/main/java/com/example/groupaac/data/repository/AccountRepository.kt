package com.example.groupaac.data.repository

import com.example.groupaac.data.account.CreateAccountRequest
import com.example.groupaac.data.account.CreateAccountResult
import com.example.groupaac.data.account.UserIdRegistry
import com.example.groupaac.data.dao.UserDao
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.entity.UserSettingsEntity
import com.example.groupaac.data.prefs.AppPreferences
import com.example.groupaac.data.realtime.RealtimeClientManager
import com.example.groupaac.model.HomeExperience
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AccountRepository(
    private val userIdRegistry: UserIdRegistry,
    private val userDao: UserDao,
    private val preferences: AppPreferences,
    private val realtimeClientManager: RealtimeClientManager
) {
    val users: Flow<List<UserEntity>> = userDao.observeUsers()
    val activeUserId: Flow<String?> = preferences.activeUserId

    fun observeUser(userId: String): Flow<UserEntity?> = userDao.observeUser(userId)
    fun observeSettings(userId: String): Flow<UserSettingsEntity?> = userDao.observeSettings(userId)
    fun observeHomeExperience(userId: String): Flow<HomeExperience> =
        userDao.observeSettings(userId).map { it?.homeExperience ?: HomeExperience.SIMPLE }

    suspend fun createLocalUser(
        uid: String,
        displayName: String,
        homeExperience: HomeExperience
    ): CreateAccountResult {
        val result = userIdRegistry.createAccount(
            CreateAccountRequest(
                uid = uid,
                displayName = displayName,
                homeExperience = homeExperience
            )
        )
        if (result is CreateAccountResult.Success) {
            realtimeClientManager.activateUser(result.user.uid)
            preferences.setActiveUser(result.user.uid)
        }
        return result
    }

    suspend fun switchUser(userId: String) {
        realtimeClientManager.activateUser(userId)
        preferences.setActiveUser(userId)
    }

    suspend fun signOut() {
        realtimeClientManager.deactivateUser()
        preferences.setActiveUser(null)
    }

    suspend fun updateDisplayName(userId: String, displayName: String) {
        userDao.getUser(userId)?.let { user ->
            userDao.upsertUser(
                user.copy(displayName = displayName.trim())
            )
        }
    }

    suspend fun updateUser(userId: String, displayName: String) {
        userDao.getUser(userId)?.let { user ->
            userDao.upsertUser(user.copy(displayName = displayName.trim()))
        }
    }
}
