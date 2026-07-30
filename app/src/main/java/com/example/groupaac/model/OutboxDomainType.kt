package com.example.groupaac.model

enum class OutboxDomainType {
    MESSAGE,
    SESSION,
    MEMBER,
    FACILITATOR_REQUEST,
    SIGNAL,
    DISPLAY;

    companion object {
        fun fromName(name: String?): OutboxDomainType {
            return entries.firstOrNull { it.name == name } ?: MESSAGE
        }
    }
}
