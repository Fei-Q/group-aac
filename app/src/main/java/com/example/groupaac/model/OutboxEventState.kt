package com.example.groupaac.model

enum class OutboxEventState {
    PENDING,
    SENDING,
    SENT,
    FAILED;

    companion object {
        fun fromName(name: String?): OutboxEventState {
            return entries.firstOrNull { it.name == name } ?: PENDING
        }
    }
}
