package com.example.groupaac.data.file

import android.content.Context
import android.net.Uri
import com.example.groupaac.util.IdUtils
import java.io.File

class AttachmentStorage(private val context: Context) {
    private val attachmentDir: File by lazy {
        File(context.filesDir, "attachments").apply { mkdirs() }
    }

    fun placeholderPath(displayName: String): String {
        val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(attachmentDir, "${IdUtils.newId()}_$safeName").absolutePath
    }

    fun savePlaceholderRecordFromUri(uri: Uri): String {
        // First prototype: persist metadata only. Replace with ContentResolver copy when upload is wired.
        return placeholderPath(uri.lastPathSegment ?: "uploaded_file")
    }
}
