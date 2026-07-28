package com.example.groupaac.model

enum class DisplayCommandOrigin {
    AUTO_LATEST,
    MANUAL_SHOW,
    MANUAL_RESTORE;

    companion object {
        fun fromName(name: String?): DisplayCommandOrigin? {
            return entries.firstOrNull { it.name == name }
        }
    }
}
