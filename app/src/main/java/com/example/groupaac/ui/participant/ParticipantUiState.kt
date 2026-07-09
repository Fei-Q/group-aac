package com.example.groupaac.ui.participant

import androidx.annotation.DrawableRes
import com.example.groupaac.data.dao.MessageWithSenderAndAttachments
import com.example.groupaac.data.entity.StatusSignalEntity
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.entity.UserSettingsEntity
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.model.SignalType

enum class ShareAttachmentKind {
    Image,
    Video
}

data class ShareAttachmentPreview(
    val id: String,
    val displayName: String,
    val previewUri: String? = null,
    val kind: ShareAttachmentKind,
    @param:DrawableRes val thumbnailRes: Int
)

data class PendingAttachmentUiItem(
    val id: String,
    val localUri: String,
    val mimeType: String,
    val originalName: String? = null
)

data class DraftAttachmentUiItem(
    val id: String,
    val localUri: String,
    val mimeType: String,
    val originalName: String? = null
) {
    val kind: ShareAttachmentKind
        get() = if (mimeType.startsWith("video/")) ShareAttachmentKind.Video else ShareAttachmentKind.Image

    fun asPendingAttachment(): PendingAttachmentUiItem = PendingAttachmentUiItem(
        id = id,
        localUri = localUri,
        mimeType = mimeType,
        originalName = originalName
    )
}

data class DraftUiItem(
    val id: String,
    val text: String,
    val target: MessageTarget,
    val createdAt: Long,
    val attachments: List<DraftAttachmentUiItem> = emptyList()
) {
    val hasAttachments: Boolean
        get() = attachments.isNotEmpty()
}

data class ParticipantUiState(
    val user: UserEntity? = null,
    val sessionId: String? = null,
    val messages: List<MessageWithSenderAndAttachments> = emptyList(),
    val settings: UserSettingsEntity? = null,
    val currentSignal: StatusSignalEntity? = null,
    val lastSignal: SignalType? = null,
    val statusMessage: String? = null,
    val pendingAttachments: List<PendingAttachmentUiItem> = emptyList(),
    val shareComposerText: String = "",
    val shareTarget: MessageTarget = MessageTarget.GROUP,
    val shareAttachmentPreviews: List<ShareAttachmentPreview> = emptyList(),
    val selectedShareAttachmentId: String? = null,
    val activeDraftId: String? = null,
    val drafts: List<DraftUiItem> = emptyList()
) {
    val selectedShareAttachment: ShareAttachmentPreview?
        get() = shareAttachmentPreviews.firstOrNull { it.id == selectedShareAttachmentId }
}
