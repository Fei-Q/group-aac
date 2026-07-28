package com.example.groupaac.data.account

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.example.groupaac.data.AppDatabase
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.entity.UserSettingsEntity
import com.example.groupaac.model.HomeExperience
import com.example.groupaac.util.TimeUtils

data class CreateAccountRequest(
    val uid: String,
    val displayName: String,
    val homeExperience: HomeExperience
)

sealed interface CreateAccountResult {
    data class Success(val user: UserEntity) : CreateAccountResult
    data object AlreadyTaken : CreateAccountResult
    data class Invalid(val message: String) : CreateAccountResult
    data class Failure(val message: String) : CreateAccountResult
}

interface UserIdRegistry {
    suspend fun createAccount(request: CreateAccountRequest): CreateAccountResult
}

class LocalUserIdRegistry(
    private val database: AppDatabase
) : UserIdRegistry {
    override suspend fun createAccount(
        request: CreateAccountRequest
    ): CreateAccountResult {
        val normalizedUid = request.uid.trim().lowercase()
        val displayName = request.displayName.trim()

        if (!UID_PATTERN.matches(normalizedUid)) {
            return CreateAccountResult.Invalid(
                "UID must be 3-24 characters using lowercase letters, digits, or underscores."
            )
        }

        if (displayName.isBlank()) {
            return CreateAccountResult.Invalid("Display name is required.")
        }

        val now = TimeUtils.now()
        val user = UserEntity(
            id = normalizedUid,
            displayName = displayName,
            createdAt = now
        )

        return try {
            database.withTransaction {
                database.userDao().insertUser(user)
                database.userDao().upsertSettings(
                    UserSettingsEntity(
                        userId = normalizedUid,
                        homeExperience = request.homeExperience
                    )
                )
            }
            CreateAccountResult.Success(user)
        } catch (_: SQLiteConstraintException) {
            CreateAccountResult.AlreadyTaken
        } catch (error: IllegalStateException) {
            CreateAccountResult.Failure(
                error.message ?: "Unable to create account."
            )
        }
    }

    companion object {
        val UID_PATTERN = Regex("^[a-z0-9][a-z0-9_]{2,23}$")
    }
}
