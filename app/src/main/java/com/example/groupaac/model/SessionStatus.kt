package com.example.groupaac.model

enum class SessionStatus {
    SCHEDULED,
    LIVE,
    ENDED,
    CANCELLED;

    companion object {
        fun fromName(name: String?): SessionStatus =
            entries.firstOrNull { it.name == name } ?: LIVE
    }
}
