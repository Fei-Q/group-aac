package com.example.groupaac.ui.participant

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.groupaac.R
import com.example.groupaac.data.dao.MessageWithSenderAndAttachments
import com.example.groupaac.data.entity.AttachmentEntity
import com.example.groupaac.data.entity.UserSettingsEntity
import com.example.groupaac.data.repository.AccountRepository
import com.example.groupaac.data.repository.AttachmentRepository
import com.example.groupaac.data.repository.MessageRepository
import com.example.groupaac.data.repository.SessionRepository
import com.example.groupaac.data.repository.SettingsRepository
import com.example.groupaac.data.repository.SignalRepository
import com.example.groupaac.data.repository.StoredAttachmentDraft
import com.example.groupaac.model.MessageStatus
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.model.SignalType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ParticipantViewModel(
    private val accountRepository: AccountRepository,
    private val sessionRepository: SessionRepository,
    private val messageRepository: MessageRepository,
    private val signalRepository: SignalRepository,
    private val settingsRepository: SettingsRepository,
    private val attachmentRepository: AttachmentRepository
) : ViewModel() {
    val uiState = MutableStateFlow(ParticipantUiState())

    private var settingsJob: Job? = null
    private var messagesJob: Job? = null
    private var currentSignalJob: Job? = null

    private var nextPrototypeAttachmentIndex = 0
    private val pendingAttachmentDrafts = mutableMapOf<String, StoredAttachmentDraft>()
    private var latestMessageRows: List<MessageWithSenderAndAttachments> = emptyList()

    private val prototypeAttachmentLibrary = listOf(
        ShareAttachmentPreview(
            id = "prototype-image-1",
            displayName = "Image 1",
            kind = ShareAttachmentKind.Image,
            thumbnailRes = R.drawable.ic_action_upload_image
        ),
        ShareAttachmentPreview(
            id = "prototype-image-2",
            displayName = "Image 2",
            kind = ShareAttachmentKind.Image,
            thumbnailRes = R.drawable.ic_action_upload_image
        ),
        ShareAttachmentPreview(
            id = "prototype-image-3",
            displayName = "Image 3",
            kind = ShareAttachmentKind.Image,
            thumbnailRes = R.drawable.ic_action_upload_image
        ),
        ShareAttachmentPreview(
            id = "prototype-video-1",
            displayName = "Video 1",
            kind = ShareAttachmentKind.Video,
            thumbnailRes = R.drawable.ic_action_upload_file
        )
    )

    init {
        observeActiveUser()
        observeLastSession()
    }

    private fun observeActiveUser() {
        viewModelScope.launch {
            accountRepository.activeUserId
                .flatMapLatest { id ->
                    if (id == null) {
                        flowOf(null)
                    } else {
                        accountRepository.observeUser(id)
                    }
                }
                .collect { user ->
                    uiState.update { it.copy(user = user) }

                    settingsJob?.cancel()
                    currentSignalJob?.cancel()

                    if (user == null) {
                        clearPendingAttachmentDrafts()

                        uiState.update {
                            it.copy(
                                settings = null,
                                currentSignal = null,
                                shareComposerText = "",
                                shareAttachmentPreviews = emptyList(),
                                selectedShareAttachmentId = null,
                                activeDraftId = null,
                                drafts = emptyList()
                            )
                        }
                    } else {
                        observeSettings(user.id)
                        rebuildDrafts()

                        val sessionId = uiState.value.sessionId
                        if (sessionId != null) {
                            observeCurrentSignal(
                                sessionId = sessionId,
                                userId = user.id
                            )
                        }
                    }
                }
        }
    }

    private fun observeLastSession() {
        viewModelScope.launch {
            sessionRepository.lastSessionId.collect { sessionId ->
                clearPendingAttachmentDrafts()
                latestMessageRows = emptyList()

                uiState.update {
                    it.copy(
                        sessionId = sessionId,
                        shareComposerText = "",
                        shareAttachmentPreviews = emptyList(),
                        selectedShareAttachmentId = null,
                        activeDraftId = null,
                        drafts = emptyList()
                    )
                }

                messagesJob?.cancel()
                currentSignalJob?.cancel()

                if (sessionId == null) {
                    uiState.update {
                        it.copy(
                            messages = emptyList(),
                            currentSignal = null
                        )
                    }
                    return@collect
                }

                observeMessages(sessionId)

                val userId = uiState.value.user?.id
                if (userId != null) {
                    observeCurrentSignal(
                        sessionId = sessionId,
                        userId = userId
                    )
                }
            }
        }
    }

    private fun observeSettings(userId: String) {
        settingsJob = viewModelScope.launch {
            settingsRepository.observeSettings(userId).collect { settings ->
                uiState.update { it.copy(settings = settings) }
            }
        }
    }

    private fun observeMessages(sessionId: String) {
        messagesJob = viewModelScope.launch {
            messageRepository.observeMessagesWithAttachments(sessionId).collect { rows ->
                latestMessageRows = rows
                rebuildMessageState(rows)
            }
        }
    }

    private fun rebuildMessageState(rows: List<MessageWithSenderAndAttachments>) {
        val currentUserId = uiState.value.user?.id
        val drafts = if (currentUserId == null) {
            emptyList()
        } else {
            rows
                .asSequence()
                .filter { row ->
                    row.message.status == MessageStatus.DRAFT &&
                        row.message.senderUserId == currentUserId
                }
                .map { row -> row.toDraftUiItem() }
                .sortedByDescending { draft -> draft.createdAt }
                .toList()
        }

        uiState.update {
            it.copy(
                messages = rows,
                drafts = drafts
            )
        }
    }

    private fun rebuildDrafts() {
        rebuildMessageState(latestMessageRows)
    }

    private fun observeCurrentSignal(
        sessionId: String,
        userId: String
    ) {
        currentSignalJob = viewModelScope.launch {
            signalRepository.observeCurrentSignal(
                sessionId = sessionId,
                userId = userId
            ).collect { signal ->
                uiState.update {
                    it.copy(
                        currentSignal = signal,
                        lastSignal = signal?.type ?: it.lastSignal
                    )
                }
            }
        }
    }

    fun updateShareComposerText(text: String) {
        uiState.update {
            it.copy(shareComposerText = text)
        }
    }

    fun setShareTarget(target: MessageTarget) {
        uiState.update {
            it.copy(shareTarget = target)
        }
    }

    fun addSelectedAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return

        val state = uiState.value
        val selected = state.shareAttachmentPreviews
        val currentKind = selected.firstOrNull()?.kind

        if (currentKind == ShareAttachmentKind.Video) {
            uiState.update { it.copy(statusMessage = "Only 1 video at a time") }
            return
        }

        val remainingSlots = 3 - selected.size
        if (remainingSlots <= 0) {
            uiState.update { it.copy(statusMessage = "Maximum 3 images") }
            return
        }

        viewModelScope.launch {
            val inspected = try {
                uris.take(remainingSlots).map { uri ->
                    attachmentRepository.inspectAttachment(uri)
                }
            } catch (_: Throwable) {
                uiState.update { it.copy(statusMessage = "Could not add media") }
                return@launch
            }

            val incomingKinds = inspected.map { info ->
                if (info.mimeType.startsWith("video/")) {
                    ShareAttachmentKind.Video
                } else {
                    ShareAttachmentKind.Image
                }
            }
            val incomingKind = incomingKinds.firstOrNull()
            val mixedKinds = incomingKinds.distinct().size > 1
            val containsVideo = incomingKinds.any { it == ShareAttachmentKind.Video }

            when {
                mixedKinds -> {
                    uiState.update { it.copy(statusMessage = "Use images or one video, not both") }
                    return@launch
                }

                currentKind != null && incomingKind != currentKind -> {
                    uiState.update { it.copy(statusMessage = "Use images or one video, not both") }
                    return@launch
                }

                containsVideo && (selected.isNotEmpty() || inspected.size > 1) -> {
                    uiState.update { it.copy(statusMessage = "Video must be sent by itself") }
                    return@launch
                }

                incomingKind == ShareAttachmentKind.Image &&
                    selected.size + inspected.size > 3 -> {
                    uiState.update { it.copy(statusMessage = "Maximum 3 images") }
                    return@launch
                }
            }

            val copied = try {
                inspected.map { info ->
                    attachmentRepository.copyToPrivateStorage(info.sourceUri)
                }
            } catch (_: Throwable) {
                uiState.update { it.copy(statusMessage = "Could not add media") }
                return@launch
            }

            copied.forEach { draft ->
                pendingAttachmentDrafts[draft.id] = draft
            }

            val newPreviews = copied.map { draft ->
                ShareAttachmentPreview(
                    id = draft.id,
                    displayName = draft.originalName ?: "Attachment",
                    previewUri = draft.localUri,
                    kind = when {
                        draft.mimeType.startsWith("video/") -> ShareAttachmentKind.Video
                        else -> ShareAttachmentKind.Image
                    },
                    thumbnailRes = when {
                        draft.mimeType.startsWith("video/") -> R.drawable.ic_action_upload_file
                        else -> R.drawable.ic_action_upload_image
                    }
                )
            }

            uiState.update {
                it.copy(
                    shareAttachmentPreviews = selected + newPreviews,
                    selectedShareAttachmentId = null,
                    statusMessage = when {
                        newPreviews.first().kind == ShareAttachmentKind.Video -> "Video added"
                        newPreviews.size == 1 -> "Image added"
                        else -> "${newPreviews.size} images added"
                    }
                )
            }
        }
    }

    fun addPrototypeAttachment() {
        val state = uiState.value
        val selected = state.shareAttachmentPreviews
        val currentKind = selected.firstOrNull()?.kind

        if (currentKind == ShareAttachmentKind.Image && selected.size >= 3) {
            uiState.update { it.copy(statusMessage = "Maximum 3 images") }
            return
        }

        if (currentKind == ShareAttachmentKind.Video) {
            uiState.update { it.copy(statusMessage = "Only 1 video at a time") }
            return
        }

        val candidates = when (currentKind) {
            ShareAttachmentKind.Image -> prototypeAttachmentLibrary
                .filter { it.kind == ShareAttachmentKind.Image }
                .filterNot { candidate ->
                    selected.any { it.id == candidate.id }
                }

            ShareAttachmentKind.Video -> emptyList()

            null -> prototypeAttachmentLibrary
        }

        if (candidates.isEmpty()) {
            uiState.update { it.copy(statusMessage = "No more attachments available") }
            return
        }

        var attachment = candidates[nextPrototypeAttachmentIndex % candidates.size]

        if (currentKind == ShareAttachmentKind.Image && attachment.kind != ShareAttachmentKind.Image) {
            attachment = candidates.first { it.kind == ShareAttachmentKind.Image }
        }

        if (selected.isNotEmpty() && attachment.kind != currentKind) {
            uiState.update { it.copy(statusMessage = "Use images or one video, not both") }
            return
        }

        if (attachment.kind == ShareAttachmentKind.Video && selected.isNotEmpty()) {
            uiState.update { it.copy(statusMessage = "Video must be sent by itself") }
            return
        }

        nextPrototypeAttachmentIndex += 1

        uiState.update {
            it.copy(
                shareAttachmentPreviews = selected + attachment,
                selectedShareAttachmentId = null,
                statusMessage = when (attachment.kind) {
                    ShareAttachmentKind.Image -> "Image added"
                    ShareAttachmentKind.Video -> "Video added"
                }
            )
        }
    }

    fun selectShareAttachment(attachmentId: String) {
        uiState.update {
            it.copy(selectedShareAttachmentId = attachmentId)
        }
    }

    fun dismissShareAttachmentPreview() {
        uiState.update {
            it.copy(selectedShareAttachmentId = null)
        }
    }

    fun removeShareAttachment(attachmentId: String) {
        pendingAttachmentDrafts.remove(attachmentId)?.let { draft ->
            attachmentRepository.deleteDraftAttachment(draft)
        }

        uiState.update {
            it.copy(
                shareAttachmentPreviews = it.shareAttachmentPreviews.filterNot { attachment ->
                    attachment.id == attachmentId
                },
                selectedShareAttachmentId = null,
                statusMessage = "Attachment removed"
            )
        }
    }

    fun editSelectedShareAttachment() {
        uiState.update {
            it.copy(statusMessage = "Media editing will be added later")
        }
    }

    fun clearShareComposer() {
        clearPendingAttachmentDrafts()

        uiState.update {
            it.copy(
                shareComposerText = "",
                shareAttachmentPreviews = emptyList(),
                selectedShareAttachmentId = null,
                activeDraftId = null,
                statusMessage = "Composer cleared"
            )
        }
    }

    fun saveCurrentDraft() {
        val state = uiState.value
        val user = state.user ?: return
        val sessionId = state.sessionId ?: return

        val draftText = state.shareComposerText.trim()
        val attachmentsToSave = state.shareAttachmentPreviews.mapNotNull { preview ->
            pendingAttachmentDrafts[preview.id]
        }

        if (draftText.isBlank() && attachmentsToSave.isEmpty()) return

        viewModelScope.launch {
            val draftId = messageRepository.saveDraft(
                sessionId = sessionId,
                senderUserId = user.id,
                text = draftText,
                target = state.shareTarget,
                existingDraftId = state.activeDraftId
            )

            if (draftId.isNotBlank()) {
                attachmentRepository.saveAttachmentsForMessage(
                    messageId = draftId,
                    drafts = attachmentsToSave
                )
            }

            uiState.update {
                it.copy(
                    activeDraftId = draftId,
                    statusMessage = "Draft saved"
                )
            }
        }
    }

    fun sendCurrentShare() {
        val state = uiState.value
        val user = state.user ?: return
        val sessionId = state.sessionId ?: return

        val textToSend = state.shareComposerText.trim()
        val attachmentsToSend = state.shareAttachmentPreviews.mapNotNull { preview ->
            pendingAttachmentDrafts[preview.id]
        }

        if (textToSend.isBlank() && attachmentsToSend.isEmpty()) return

        viewModelScope.launch {
            val messageId = messageRepository.sendText(
                sessionId = sessionId,
                senderUserId = user.id,
                target = state.shareTarget,
                text = textToSend,
                sourceDraftId = state.activeDraftId
            )

            if (attachmentsToSend.isNotEmpty()) {
                attachmentRepository.saveAttachmentsForMessage(
                    messageId = messageId,
                    drafts = attachmentsToSend
                )
            }

            pendingAttachmentDrafts.clear()

            uiState.update {
                it.copy(
                    shareComposerText = "",
                    shareAttachmentPreviews = emptyList(),
                    selectedShareAttachmentId = null,
                    activeDraftId = null,
                    statusMessage = "Sent to ${state.shareTarget.label.lowercase()}"
                )
            }
        }
    }

    fun sendToGroup(text: String) {
        send(
            text = text,
            target = MessageTarget.GROUP,
            status = "Sent to group"
        )
    }

    fun sendToFacilitator(text: String) {
        send(
            text = text,
            target = MessageTarget.FACILITATOR,
            status = "Sent to facilitator"
        )
    }

    private fun send(
        text: String,
        target: MessageTarget,
        status: String
    ) {
        val state = uiState.value
        val user = state.user ?: return
        val sessionId = state.sessionId ?: return

        if (text.isBlank()) return

        viewModelScope.launch {
            messageRepository.sendText(
                sessionId = sessionId,
                senderUserId = user.id,
                target = target,
                text = text
            )

            uiState.update {
                it.copy(statusMessage = status)
            }
        }
    }

    fun saveDraft(text: String) {
        val state = uiState.value
        val user = state.user ?: return
        val sessionId = state.sessionId ?: return

        viewModelScope.launch {
            messageRepository.saveDraft(
                sessionId = sessionId,
                senderUserId = user.id,
                text = text,
                target = state.shareTarget
            )

            uiState.update {
                it.copy(statusMessage = "Draft saved")
            }
        }
    }

    fun sendSignal(type: SignalType) {
        val state = uiState.value
        val user = state.user ?: return
        val sessionId = state.sessionId ?: return

        viewModelScope.launch {
            signalRepository.sendSignal(
                sessionId = sessionId,
                userId = user.id,
                type = type
            )

            uiState.update {
                it.copy(
                    lastSignal = type,
                    statusMessage = "Signal sent: ${type.label}"
                )
            }
        }
    }

    fun editDraft(draftId: String) {
        val draft = uiState.value.drafts.firstOrNull { it.id == draftId } ?: return

        clearPendingAttachmentDrafts()

        val restoredDrafts = draft.attachments.map { attachment ->
            attachment.toStoredAttachmentDraft()
        }

        restoredDrafts.forEach { restored ->
            pendingAttachmentDrafts[restored.id] = restored
        }

        uiState.update {
            it.copy(
                shareComposerText = draft.text,
                shareTarget = draft.target,
                shareAttachmentPreviews = restoredDrafts.map { restored ->
                    restored.toShareAttachmentPreview()
                },
                selectedShareAttachmentId = null,
                activeDraftId = null,
                statusMessage = "Draft loaded"
            )
        }

        viewModelScope.launch {
            messageRepository.deleteDraft(draftId)
        }
    }

    fun deleteDraft(draftId: String) {
        val attachmentIds = uiState.value.drafts
            .firstOrNull { it.id == draftId }
            ?.attachments
            ?.map { it.id }
            .orEmpty()

        attachmentIds.forEach { attachmentId ->
            pendingAttachmentDrafts.remove(attachmentId)
        }

        viewModelScope.launch {
            messageRepository.deleteDraft(draftId)
        }

        uiState.update {
            it.copy(statusMessage = "Draft deleted")
        }
    }

    fun clearCurrentSignal() {
        val state = uiState.value
        val user = state.user ?: return
        val sessionId = state.sessionId ?: return

        viewModelScope.launch {
            signalRepository.clearCurrentSignal(
                sessionId = sessionId,
                userId = user.id
            )

            uiState.update {
                it.copy(
                    currentSignal = null,
                    statusMessage = "Signal cleared"
                )
            }
        }
    }

    fun updateSettings(settings: UserSettingsEntity) {
        viewModelScope.launch {
            settingsRepository.updateSettings(settings)
        }
    }

    private fun DraftAttachmentUiItem.toStoredAttachmentDraft(): StoredAttachmentDraft {
        return StoredAttachmentDraft(
            id = id,
            localUri = localUri,
            mimeType = mimeType,
            originalName = originalName
        )
    }

    private fun DraftAttachmentUiItem.toShareAttachmentPreview(): ShareAttachmentPreview {
        return ShareAttachmentPreview(
            id = id,
            displayName = originalName ?: "Attachment",
            previewUri = localUri,
            kind = kind,
            thumbnailRes = when (kind) {
                ShareAttachmentKind.Image -> R.drawable.ic_action_upload_image
                ShareAttachmentKind.Video -> R.drawable.ic_action_upload_file
            }
        )
    }

    private fun StoredAttachmentDraft.toShareAttachmentPreview(): ShareAttachmentPreview {
        return ShareAttachmentPreview(
            id = id,
            displayName = originalName ?: "Attachment",
            previewUri = localUri,
            kind = if (mimeType.startsWith("video/")) {
                ShareAttachmentKind.Video
            } else {
                ShareAttachmentKind.Image
            },
            thumbnailRes = if (mimeType.startsWith("video/")) {
                R.drawable.ic_action_upload_file
            } else {
                R.drawable.ic_action_upload_image
            }
        )
    }

    private fun MessageWithSenderAndAttachments.toDraftUiItem(): DraftUiItem {
        return DraftUiItem(
            id = message.id,
            text = message.text.orEmpty(),
            target = message.target,
            createdAt = message.createdAt,
            attachments = attachments
                .sortedBy { it.sortOrder }
                .map { attachment ->
                    attachment.toDraftAttachmentUiItem()
                }
        )
    }

    private fun AttachmentEntity.toDraftAttachmentUiItem(): DraftAttachmentUiItem {
        return DraftAttachmentUiItem(
            id = id,
            localUri = localUri,
            mimeType = mimeType,
            originalName = originalName
        )
    }

    private fun clearPendingAttachmentDrafts() {
        val draftsToDelete = pendingAttachmentDrafts.values.toList()
        if (draftsToDelete.isNotEmpty()) {
            attachmentRepository.deleteDraftAttachments(draftsToDelete)
            pendingAttachmentDrafts.clear()
        } else {
            pendingAttachmentDrafts.clear()
        }
    }
}

class ParticipantViewModelFactory(
    private val accountRepository: AccountRepository,
    private val sessionRepository: SessionRepository,
    private val messageRepository: MessageRepository,
    private val signalRepository: SignalRepository,
    private val settingsRepository: SettingsRepository,
    private val attachmentRepository: AttachmentRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ParticipantViewModel(
            accountRepository = accountRepository,
            sessionRepository = sessionRepository,
            messageRepository = messageRepository,
            signalRepository = signalRepository,
            settingsRepository = settingsRepository,
            attachmentRepository = attachmentRepository
        ) as T
    }
}
