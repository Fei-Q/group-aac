package com.example.groupaac.ui.common

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TextToSpeechHelper(context: Context) {
    private var tts: TextToSpeech? = try {
        TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
    } catch (_: Throwable) {
        null
    }

    fun speak(text: String) {
        if (text.isNotBlank()) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "group-aac-read-aloud")
        }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }
}
