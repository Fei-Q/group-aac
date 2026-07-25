package com.example.groupaac.model

enum class JoinRequestStatus {
    PENDING,
    APPROVED,
    DECLINED,
    CANCELLED;

    companion object {
        fun fromName(name: String?): JoinRequestStatus =
            entries.firstOrNull { it.name == name } ?: PENDING
    }
}
