package com.example.groupaac.model

enum class SignalState {
    CURRENT,
    SNOOZED,
    CLEARED,

    @Deprecated("Use CURRENT instead. Kept to avoid crashes from old local Room rows.")
    ACTIVE
}
