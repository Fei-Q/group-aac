package com.example.groupaac.model

enum class UserRole(val label: String, val description: String) {
    PARTICIPANT("Participant", "I’m here to talk and share."),
    FACILITATOR("Facilitator", "I’m here to guide the group.");

    companion object {
        fun fromName(name: String?): UserRole = entries.firstOrNull { it.name == name } ?: PARTICIPANT
    }
}
