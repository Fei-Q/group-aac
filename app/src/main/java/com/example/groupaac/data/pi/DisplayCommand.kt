package com.example.groupaac.data.pi

sealed interface DisplayCommand {
    data class ShowMessage(val sessionId: String, val messageId: String) : DisplayCommand
    data class RestoreMessage(val sessionId: String, val messageId: String) : DisplayCommand
    data class PinMessage(val sessionId: String, val messageId: String) : DisplayCommand
    data class UnpinMessage(val sessionId: String, val messageId: String) : DisplayCommand
    data class Clear(val sessionId: String) : DisplayCommand
}
