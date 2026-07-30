package com.example.groupaac.data.account

sealed interface UserIdValidationResult {
    data object Valid : UserIdValidationResult
    data class Invalid(val message: String) : UserIdValidationResult
}

object UserIdValidator {
    const val MAX_LENGTH = 24
    val pattern = Regex("^[a-z0-9][a-z0-9_]{2,23}$")

    fun normalize(raw: String): String = raw.trim().lowercase()

    fun sanitizeForInput(raw: String): String = buildString {
        raw.lowercase().forEach { char ->
            if (
                (char in 'a'..'z' || char in '0'..'9' || char == '_') &&
                length < MAX_LENGTH
            ) {
                append(char)
            }
        }
    }

    fun validate(uid: String): UserIdValidationResult {
        if (uid.isBlank()) {
            return UserIdValidationResult.Invalid("UID is required.")
        }

        if (!pattern.matches(uid)) {
            return UserIdValidationResult.Invalid(
                "UID must be 3-24 characters using lowercase letters, digits, or underscores."
            )
        }

        return UserIdValidationResult.Valid
    }
}
