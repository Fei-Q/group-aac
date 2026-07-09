package com.example.groupaac.data.pi

sealed interface DisplayCommand {
    data class ShowMessage(val sessionId: String, val messageId: String) : DisplayCommand
    data class Clear(val sessionId: String) : DisplayCommand
}
