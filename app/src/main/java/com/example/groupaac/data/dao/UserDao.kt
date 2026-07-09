package com.example.groupaac.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY lastLoginAt DESC, createdAt DESC")
    fun observeUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    fun observeUser(id: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUser(id: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(user: UserEntity)
    @Query("UPDATE users SET lastLoginAt = :timestamp WHERE id = :userId")
    suspend fun updateLastLogin(userId: String, timestamp: Long)

    @Query("SELECT * FROM user_settings WHERE userId = :userId LIMIT 1")
    fun observeSettings(userId: String): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE userId = :userId LIMIT 1")
    suspend fun getSettings(userId: String): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: UserSettingsEntity)

    @Query("""
        UPDATE user_settings 
        SET textScale = :textScale,
            updatedAt = :updatedAt
        WHERE userId = :userId
    """)
        suspend fun updateTextScale(
            userId: String,
            textScale: Float,
            updatedAt: Long = System.currentTimeMillis()
        )

    @Query("""
        UPDATE user_settings 
        SET soundEnabled = :enabled,
            updatedAt = :updatedAt
        WHERE userId = :userId
    """)
        suspend fun updateSoundEnabled(
            userId: String,
            enabled: Boolean,
            updatedAt: Long = System.currentTimeMillis()
        )

    @Query("""
        UPDATE user_settings 
        SET facilitatorShowLowParticipationAlerts = :enabled,
            updatedAt = :updatedAt
        WHERE userId = :userId
    """)
        suspend fun updateLowParticipationAlerts(
            userId: String,
            enabled: Boolean,
            updatedAt: Long = System.currentTimeMillis()
        )

    @Query("""
        UPDATE user_settings 
        SET facilitatorLowParticipationThresholdMinutes = :minutes,
            updatedAt = :updatedAt
        WHERE userId = :userId
    """)
        suspend fun updateLowParticipationThreshold(
            userId: String,
            minutes: Int,
            updatedAt: Long = System.currentTimeMillis()
        )

    @Query("""
        UPDATE user_settings 
        SET monitorRequireManualApproval = :required,
            updatedAt = :updatedAt
        WHERE userId = :userId
    """)
        suspend fun updateMonitorManualApproval(
            userId: String,
            required: Boolean,
            updatedAt: Long = System.currentTimeMillis()
        )
    @Transaction
    suspend fun createUserWithSettings(user: UserEntity, settings: UserSettingsEntity) {
        upsertUser(user)
        upsertSettings(settings)
    }
}
