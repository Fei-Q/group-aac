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
        val normalizedUid = UserIdValidator.normalize(request.uid)
        val displayName = request.displayName.trim()

        when (val validation = UserIdValidator.validate(normalizedUid)) {
            is UserIdValidationResult.Invalid -> {
                return CreateAccountResult.Invalid(validation.message)
            }
            UserIdValidationResult.Valid -> Unit
        }

        if (displayName.isBlank()) {
            return CreateAccountResult.Invalid("Display name is required.")
        }

        val now = TimeUtils.now()
        val user = UserEntity(
            uid = normalizedUid,
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
}
