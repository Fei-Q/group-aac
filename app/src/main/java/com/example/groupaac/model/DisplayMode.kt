package com.example.groupaac.model

enum class DisplayMode {
    AUTO_LATEST,
    APPROVAL_REQUIRED;

    companion object {
        fun fromName(name: String?): DisplayMode {
            return entries.firstOrNull { it.name == name } ?: AUTO_LATEST
        }
    }
}
