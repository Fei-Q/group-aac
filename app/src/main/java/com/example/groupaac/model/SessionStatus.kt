package com.example.groupaac.model

enum class SessionStatus {
    DRAFT,
    SCHEDULED,
    LIVE,
    ENDED,
    CANCELLED;

    companion object {
        fun fromName(name: String?): SessionStatus =
            entries.firstOrNull { it.name == name } ?: DRAFT
    }
}