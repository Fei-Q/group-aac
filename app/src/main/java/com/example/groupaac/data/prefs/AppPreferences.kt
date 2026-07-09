package com.example.groupaac.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.groupaac.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.groupAacDataStore by preferencesDataStore(name = "group_aac_preferences")

class AppPreferences(private val context: Context) {
    private object Keys {
        val ActiveUserId = stringPreferencesKey("active_user_id")
        val LastSessionId = stringPreferencesKey("last_session_id")
        val LastRole = stringPreferencesKey("last_role")
        val RememberLastUser = booleanPreferencesKey("remember_last_user")
    }

    val activeUserId: Flow<String?> = context.groupAacDataStore.data.map { it[Keys.ActiveUserId] }
    val lastSessionId: Flow<String?> = context.groupAacDataStore.data.map { it[Keys.LastSessionId] }
    val rememberLastUser: Flow<Boolean> = context.groupAacDataStore.data.map { it[Keys.RememberLastUser] ?: true }
    val lastRole: Flow<UserRole> = context.groupAacDataStore.data.map { UserRole.fromName(it[Keys.LastRole]) }

    suspend fun setActiveUser(userId: String?) {
        context.groupAacDataStore.edit { prefs ->
            if (userId == null) prefs.remove(Keys.ActiveUserId) else prefs[Keys.ActiveUserId] = userId
        }
    }

    suspend fun setLastSession(sessionId: String?) {
        context.groupAacDataStore.edit { prefs ->
            if (sessionId == null) prefs.remove(Keys.LastSessionId) else prefs[Keys.LastSessionId] = sessionId
        }
    }

    suspend fun setLastRole(role: UserRole) {
        context.groupAacDataStore.edit { it[Keys.LastRole] = role.name }
    }

    suspend fun setRememberLastUser(enabled: Boolean) {
        context.groupAacDataStore.edit { it[Keys.RememberLastUser] = enabled }
    }
}
