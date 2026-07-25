package com.example.groupaac.model

enum class SessionRole {
    PARTICIPANT,
    FACILITATOR,
    HOST;

    companion object {
        fun fromName(name: String?): SessionRole =
            entries.firstOrNull { it.name == name } ?: PARTICIPANT
    }
}
