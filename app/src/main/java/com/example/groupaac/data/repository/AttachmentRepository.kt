package com.example.groupaac.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.example.groupaac.data.dao.MessageDao
import com.example.groupaac.data.entity.AttachmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class StoredAttachmentDraft(
    val id: String = UUID.randomUUID().toString(),
    val localUri: String,
    val mimeType: String,
    val originalName: String?
)

data class AttachmentSelectionInfo(
    val sourceUri: Uri,
    val mimeType: String,
    val originalName: String?
)

class AttachmentRepository(
    context: Context,
    private val messageDao: MessageDao
) {
    private val appContext = context.applicationContext

    fun inspectAttachment(sourceUri: Uri): AttachmentSelectionInfo {
        val resolver = appContext.contentResolver
        return AttachmentSelectionInfo(
            sourceUri = sourceUri,
            mimeType = resolver.getType(sourceUri) ?: "application/octet-stream",
            originalName = queryDisplayName(sourceUri)
        )
    }

    suspend fun copyToPrivateStorage(
        sourceUri: Uri
    ): StoredAttachmentDraft = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val mimeType = resolver.getType(sourceUri) ?: "application/octet-stream"
        val originalName = queryDisplayName(sourceUri)

        val outputDir = File(appContext.filesDir, "message_attachments").apply {
            mkdirs()
        }

        val extension = extensionFor(
            mimeType = mimeType,
            originalName = originalName
        )

        val outputFile = File(outputDir, "${UUID.randomUUID()}.$extension")

        resolver.openInputStream(sourceUri).use { input ->
            requireNotNull(input) { "Unable to open selected attachment." }

            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        StoredAttachmentDraft(
            localUri = outputFile.toUri().toString(),
            mimeType = mimeType,
            originalName = originalName
        )
    }

    fun deleteDraftAttachment(draft: StoredAttachmentDraft) {
        deleteLocalUri(draft.localUri)
    }

    fun deleteDraftAttachments(drafts: Collection<StoredAttachmentDraft>) {
        drafts.forEach { draft ->
            deleteLocalUri(draft.localUri)
        }
    }

    suspend fun saveAttachmentsForMessage(
        messageId: String,
        drafts: List<StoredAttachmentDraft>
    ) {
        messageDao.deleteAttachmentsForMessage(messageId)

        if (drafts.isEmpty()) return

        val now = System.currentTimeMillis()

        val attachments = drafts.mapIndexed { index, draft ->
            AttachmentEntity(
                id = draft.id,
                messageId = messageId,
                localUri = draft.localUri,
                mimeType = draft.mimeType,
                originalName = draft.originalName,
                sortOrder = index,
                createdAt = now
            )
        }

        messageDao.upsertAttachments(attachments)
    }

    private fun queryDisplayName(uri: Uri): String? {
        return appContext.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null

                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) null else cursor.getString(index)
            }
    }

    private fun extensionFor(
        mimeType: String,
        originalName: String?
    ): String {
        val originalExtension = originalName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }

        if (originalExtension != null) {
            return originalExtension
        }

        return MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType)
            ?: "bin"
    }

    private fun deleteLocalUri(localUri: String) {
        val file = Uri.parse(localUri).path?.let(::File) ?: return
        runCatching {
            if (file.exists()) {
                file.delete()
            }
        }
    }
}
