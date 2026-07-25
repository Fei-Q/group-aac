package com.example.groupaac.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.groupaac.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.groupAacDataStore by preferencesDataStore(
    name = "group_aac_preferences"
)

class AppPreferences(
    private val context: Context
) {
    private object Keys {
        val ActiveUserId = stringPreferencesKey("active_user_id")
        val LastRole = stringPreferencesKey("last_role")
        val RememberLastUser = booleanPreferencesKey("remember_last_user")
    }

    val activeUserId: Flow<String?> =
        context.groupAacDataStore.data.map { preferences ->
            preferences[Keys.ActiveUserId]
        }

    val rememberLastUser: Flow<Boolean> =
        context.groupAacDataStore.data.map { preferences ->
            preferences[Keys.RememberLastUser] ?: true
        }

    val lastRole: Flow<UserRole> =
        context.groupAacDataStore.data.map { preferences ->
            UserRole.fromName(preferences[Keys.LastRole])
        }

    fun observeActiveSessionId(userId: String): Flow<String?> {
        val key = activeSessionIdKey(userId)

        return context.groupAacDataStore.data.map { preferences ->
            preferences[key]
        }
    }

    suspend fun setActiveUser(userId: String?) {
        context.groupAacDataStore.edit { preferences ->
            if (userId == null) {
                preferences.remove(Keys.ActiveUserId)
            } else {
                preferences[Keys.ActiveUserId] = userId
            }
        }
    }

    suspend fun setActiveSessionId(
        userId: String,
        sessionId: String?
    ) {
        val key = activeSessionIdKey(userId)

        context.groupAacDataStore.edit { preferences ->
            if (sessionId == null) {
                preferences.remove(key)
            } else {
                preferences[key] = sessionId
            }
        }
    }

    suspend fun setLastRole(role: UserRole) {
        context.groupAacDataStore.edit { preferences ->
            preferences[Keys.LastRole] = role.name
        }
    }

    suspend fun setRememberLastUser(enabled: Boolean) {
        context.groupAacDataStore.edit { preferences ->
            preferences[Keys.RememberLastUser] = enabled
        }
    }

    private fun activeSessionIdKey(
        userId: String
    ) = stringPreferencesKey("active_session_id_$userId")
}
