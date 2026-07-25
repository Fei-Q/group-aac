package com.example.groupaac.data.session

import com.example.groupaac.data.prefs.AppPreferences
import kotlinx.coroutines.flow.Flow

interface ActiveSessionStore {
    fun observeActiveSessionId(userId: String): Flow<String?>

    suspend fun setActiveSession(
        userId: String,
        sessionId: String
    )

    suspend fun clearActiveSession(userId: String)
}

class DataStoreActiveSessionStore(
    private val preferences: AppPreferences
) : ActiveSessionStore {

    override fun observeActiveSessionId(userId: String): Flow<String?> =
        preferences.observeActiveSessionId(userId)

    override suspend fun setActiveSession(
        userId: String,
        sessionId: String
    ) {
        preferences.setActiveSessionId(
            userId = userId,
            sessionId = sessionId
        )
    }

    override suspend fun clearActiveSession(userId: String) {
        preferences.setActiveSessionId(
            userId = userId,
            sessionId = null
        )
    }
}