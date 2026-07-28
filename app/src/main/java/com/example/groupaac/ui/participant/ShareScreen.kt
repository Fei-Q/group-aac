package com.example.groupaac.ui.participant

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.saveable.rememberSaveable
import coil.compose.AsyncImage
import com.example.groupaac.R
import com.example.groupaac.data.dao.MessageWithSender
import com.example.groupaac.data.dao.MessageWithSenderAndAttachments
import com.example.groupaac.data.entity.AttachmentEntity
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.model.MessageStatus
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.model.UserRole
import com.example.groupaac.ui.common.AppCard
import com.example.groupaac.ui.common.AppWindowSize
import com.example.groupaac.ui.common.CompactActionButton
import com.example.groupaac.ui.common.PrimaryButton
import com.example.groupaac.ui.common.SecondaryButton
import com.example.groupaac.ui.common.TextToSpeechHelper
import com.example.groupaac.ui.common.rememberAppWindowSize
import com.example.groupaac.ui.theme.AacBackground
import com.example.groupaac.ui.theme.AacTextSecondary
import com.example.groupaac.ui.theme.GroupAacTheme
import androidx.core.net.toUri

@Composable
fun ShareScreen(
    uiState: ParticipantUiState,
    onMessageChange: (String) -> Unit,
    onSaveDraft: () -> Unit,
    onClearComposer: () -> Unit,
    onUploadAttachment: (List<Uri>) -> Unit,
    onSelectAttachment: (String) -> Unit,
    onDismissAttachmentPreview: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onEditAttachment: () -> Unit,
    onTargetChange: (MessageTarget) -> Unit,
    onSendShare: () -> Unit,
    onEditDraft: (String) -> Unit,
    onDeleteDraft: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val tts = remember(context, isPreview) {
        if (isPreview) null else TextToSpeechHelper(context)
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 3)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onUploadAttachment(uris)
        }
    }

    val launchMediaPicker = {
        mediaPickerLauncher.launch(
            PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageAndVideo
            )
        )
    }

    val windowSize = rememberAppWindowSize()
    val configuration = LocalConfiguration.current
    val isCompactHeight = configuration.screenHeightDp < 480
    val useWideLayout = windowSize != AppWindowSize.Phone && !isCompactHeight

    val sharedMessages = remember(uiState.messages, uiState.user?.uid) {
        uiState.messages.filter {
            it.message.senderUserId == uiState.user?.uid &&
                    it.message.status != MessageStatus.DRAFT &&
                    it.message.status != MessageStatus.DELETED
        }
    }

    DisposableEffect(tts) {
        onDispose {
            tts?.shutdown()
        }
    }

    uiState.selectedShareAttachment?.let { selectedAttachment ->
        AttachmentPreviewDialog(
            attachment = selectedAttachment,
            onDismiss = onDismissAttachmentPreview,
            onEdit = onEditAttachment,
            onRemove = {
                onRemoveAttachment(selectedAttachment.id)
            }
        )
    }

    if (useWideLayout) {
        ShareScreenWide(
            uiState = uiState,
            drafts = uiState.drafts,
            sharedMessages = sharedMessages,
            onMessageChange = onMessageChange,
            onReadAloud = { tts?.speak(uiState.shareComposerText) },
            onSaveDraft = onSaveDraft,
            onClearComposer = onClearComposer,
            onUploadAttachment = launchMediaPicker,
            onSelectAttachment = onSelectAttachment,
            onTargetChange = onTargetChange,
            onSendShare = onSendShare,
            onEditDraft = onEditDraft,
            onDeleteDraft = onDeleteDraft,
            modifier = modifier
        )
    } else {
        ShareScreenCompact(
            uiState = uiState,
            drafts = uiState.drafts,
            sharedMessages = sharedMessages,
            isCompactHeight = isCompactHeight,
            onMessageChange = onMessageChange,
            onReadAloud = { tts?.speak(uiState.shareComposerText) },
            onSaveDraft = onSaveDraft,
            onClearComposer = onClearComposer,
            onUploadAttachment = launchMediaPicker,
            onSelectAttachment = onSelectAttachment,
            onTargetChange = onTargetChange,
            onSendShare = onSendShare,
            onEditDraft = onEditDraft,
            onDeleteDraft = onDeleteDraft,
            modifier = modifier
        )
    }
}

@Composable
private fun ShareScreenCompact(
    uiState: ParticipantUiState,
    drafts: List<DraftUiItem>,
    sharedMessages: List<MessageWithSenderAndAttachments>,
    isCompactHeight: Boolean,
    onMessageChange: (String) -> Unit,
    onReadAloud: () -> Unit,
    onSaveDraft: () -> Unit,
    onClearComposer: () -> Unit,
    onUploadAttachment: () -> Unit,
    onSelectAttachment: (String) -> Unit,
    onTargetChange: (MessageTarget) -> Unit,
    onSendShare: () -> Unit,
    onEditDraft: (String) -> Unit,
    onDeleteDraft: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AacBackground)
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = 20.dp,
                vertical = if (isCompactHeight) 10.dp else 20.dp
            ),
        verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 12.dp else 18.dp)
    ) {
        ShareHeader(compact = isCompactHeight)

        ShareComposerCard(
            uiState = uiState,
            textFieldHeight = if (isCompactHeight) 112.dp else 150.dp,
            previewTileSize = 86.dp,
            useWideInternalLayout = false,
            onMessageChange = onMessageChange,
            onReadAloud = onReadAloud,
            onSaveDraft = onSaveDraft,
            onClearComposer = onClearComposer,
            onUploadAttachment = onUploadAttachment,
            onSelectAttachment = onSelectAttachment,
            onTargetChange = onTargetChange,
            onSendShare = onSendShare
        )

        DraftsCard(
            drafts = drafts,
            onEditDraft = onEditDraft,
            onDeleteDraft = onDeleteDraft
        )

            RecentMessagesCard(
                sharedMessages = sharedMessages,
                currentUserId = uiState.user?.uid
            )
        }
}

@Composable
private fun ShareScreenWide(
    uiState: ParticipantUiState,
    drafts: List<DraftUiItem>,
    sharedMessages: List<MessageWithSenderAndAttachments>,
    onMessageChange: (String) -> Unit,
    onReadAloud: () -> Unit,
    onSaveDraft: () -> Unit,
    onClearComposer: () -> Unit,
    onUploadAttachment: () -> Unit,
    onSelectAttachment: (String) -> Unit,
    onTargetChange: (MessageTarget) -> Unit,
    onSendShare: () -> Unit,
    onEditDraft: (String) -> Unit,
    onDeleteDraft: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AacBackground)
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        ShareHeader(compact = false)

        ShareComposerCard(
            uiState = uiState,
            textFieldHeight = 260.dp,
            previewTileSize = 104.dp,
            useWideInternalLayout = true,
            onMessageChange = onMessageChange,
            onReadAloud = onReadAloud,
            onSaveDraft = onSaveDraft,
            onClearComposer = onClearComposer,
            onUploadAttachment = onUploadAttachment,
            onSelectAttachment = onSelectAttachment,
            onTargetChange = onTargetChange,
            onSendShare = onSendShare
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.Top
        ) {
            DraftsCard(
                drafts = drafts,
                onEditDraft = onEditDraft,
                onDeleteDraft = onDeleteDraft,
                modifier = Modifier.weight(1f)
            )

            RecentMessagesCard(
                sharedMessages = sharedMessages,
                currentUserId = uiState.user?.uid,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ShareHeader(
    compact: Boolean
) {
    if (compact) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Share",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Send a message or file",
                color = AacTextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Share",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.secondary
            )

            Text(
                text = "Send a message or file to the group!",
                color = AacTextSecondary
            )
        }
    }
}

@Composable
private fun ShareComposerCard(
    uiState: ParticipantUiState,
    textFieldHeight: Dp,
    previewTileSize: Dp,
    useWideInternalLayout: Boolean,
    onMessageChange: (String) -> Unit,
    onReadAloud: () -> Unit,
    onSaveDraft: () -> Unit,
    onClearComposer: () -> Unit,
    onUploadAttachment: () -> Unit,
    onSelectAttachment: (String) -> Unit,
    onTargetChange: (MessageTarget) -> Unit,
    onSendShare: () -> Unit
) {
    val hasText = uiState.shareComposerText.isNotBlank()
    val hasContent = hasText || uiState.shareAttachmentPreviews.isNotEmpty()

    AppCard {
        if (useWideInternalLayout) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1.25f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        MessageTextField(
                            value = uiState.shareComposerText,
                            onValueChange = onMessageChange,
                            height = textFieldHeight
                        )

                        MessageActionButtons(
                            hasText = hasText,
                            hasContent = hasContent,
                            onReadAloud = onReadAloud,
                            onSaveDraft = onSaveDraft,
                            onClearComposer = onClearComposer
                        )
                    }

                    Column(
                        modifier = Modifier.weight(0.75f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SecondaryButton(
                            text = "Upload File 🌄",
                            onClick = onUploadAttachment,
                            modifier = Modifier.fillMaxWidth(),
                            leadingIconRes = R.drawable.ic_action_upload_file
                        )

                        if (uiState.shareAttachmentPreviews.isNotEmpty()) {
                            AttachmentPreviewGrid(
                                attachments = uiState.shareAttachmentPreviews,
                                previewTileSize = previewTileSize,
                                onSelectAttachment = onSelectAttachment
                            )
                        }

                        AttachmentRulesText()
                    }
                }

                SendTargetButtons(
                    hasContent = hasContent,
                    onTargetChange = onTargetChange,
                    onSendShare = onSendShare
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MessageTextField(
                    value = uiState.shareComposerText,
                    onValueChange = onMessageChange,
                    height = textFieldHeight
                )

                MessageActionButtons(
                    hasText = hasText,
                    hasContent = hasContent,
                    onReadAloud = onReadAloud,
                    onSaveDraft = onSaveDraft,
                    onClearComposer = onClearComposer
                )

                AttachmentSection(
                    attachments = uiState.shareAttachmentPreviews,
                    previewTileSize = previewTileSize,
                    onUploadAttachment = onUploadAttachment,
                    onSelectAttachment = onSelectAttachment
                )

                SendTargetButtons(
                    hasContent = hasContent,
                    onTargetChange = onTargetChange,
                    onSendShare = onSendShare
                )
            }
        }
    }
}

@Composable
private fun MessageTextField(
    value: String,
    onValueChange: (String) -> Unit,
    height: Dp
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        label = { Text("Type your message") },
        placeholder = { Text("I want to say...") }
    )
}

@Composable
private fun MessageActionButtons(
    hasText: Boolean,
    hasContent: Boolean,
    onReadAloud: () -> Unit,
    onSaveDraft: () -> Unit,
    onClearComposer: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CompactActionButton(
            text = "Read aloud",
            onClick = onReadAloud,
            enabled = hasText,
            modifier = Modifier.weight(1f),
            leadingIconRes = R.drawable.ic_action_read_aloud
        )

        CompactActionButton(
            text = "Save draft",
            onClick = onSaveDraft,
            enabled = hasContent,
            modifier = Modifier.weight(1f),
            leadingIconRes = R.drawable.ic_action_save_draft
        )

        CompactActionButton(
            text = "Clear",
            onClick = onClearComposer,
            enabled = hasContent,
            modifier = Modifier.weight(1f),
            leadingIconRes = R.drawable.ic_action_clear_text
        )
    }
}

@Composable
private fun AttachmentSection(
    attachments: List<ShareAttachmentPreview>,
    previewTileSize: Dp,
    onUploadAttachment: () -> Unit,
    onSelectAttachment: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton(
            text = "Upload image/video",
            onClick = onUploadAttachment,
            modifier = Modifier.fillMaxWidth(),
            leadingIconRes = R.drawable.ic_action_upload_file
        )

        if (attachments.isNotEmpty()) {
            AttachmentPreviewGrid(
                attachments = attachments,
                previewTileSize = previewTileSize,
                onSelectAttachment = onSelectAttachment
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttachmentPreviewGrid(
    attachments: List<ShareAttachmentPreview>,
    previewTileSize: Dp,
    onSelectAttachment: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        attachments.forEach { attachment ->
            AttachmentPreviewTile(
                attachment = attachment,
                size = previewTileSize,
                onClick = {
                    onSelectAttachment(attachment.id)
                }
            )
        }
    }
}

@Composable
private fun AttachmentPreviewTile(
    attachment: ShareAttachmentPreview,
    size: Dp,
    onClick: () -> Unit
) {
    val previewUri = attachment.previewUriOrNull()

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (previewUri != null) {
            AsyncImage(
                model = previewUri,
                contentDescription = when (attachment.kind) {
                    ShareAttachmentKind.Image -> "Selected image"
                    ShareAttachmentKind.Video -> "Selected video"
                },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(id = attachment.thumbnailRes),
                contentDescription = when (attachment.kind) {
                    ShareAttachmentKind.Image -> "Selected image"
                    ShareAttachmentKind.Video -> "Selected video"
                },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        if (attachment.kind == ShareAttachmentKind.Video) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                        shape = RoundedCornerShape(50)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "▶",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SendTargetButtons(
    hasContent: Boolean,
    onTargetChange: (MessageTarget) -> Unit,
    onSendShare: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Send to:",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrimaryButton(
                text = "Group",
                onClick = {
                    onTargetChange(MessageTarget.GROUP)
                    onSendShare()
                },
                enabled = hasContent,
                modifier = Modifier.weight(1f),
                leadingIconRes = R.drawable.ic_action_send_group
            )

            SecondaryButton(
                text = "Facilitator",
                onClick = {
                    onTargetChange(MessageTarget.FACILITATOR)
                    onSendShare()
                },
                enabled = hasContent,
                modifier = Modifier.weight(1f),
                leadingIconRes = R.drawable.ic_action_send_person
            )
        }
    }
}

@Composable
private fun AttachmentRulesText() {
    Text(
        text = "Attach up to 3 images or 1 video.",
        color = AacTextSecondary,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun AttachmentPreviewDialog(
    attachment: ShareAttachmentPreview,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val previewUri = attachment.previewUriOrNull()

    Dialog(onDismissRequest = onDismiss) {
        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (attachment.kind) {
                            ShareAttachmentKind.Image -> "Image preview"
                            ShareAttachmentKind.Video -> "Video preview"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )

                    TextButton(onClick = onDismiss) {
                        Text("✕")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 360.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewUri != null) {
                        AsyncImage(
                            model = previewUri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.25f),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Image(
                            painter = painterResource(id = attachment.thumbnailRes),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.25f),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (attachment.kind == ShareAttachmentKind.Video) {
                        Text(
                            text = "▶",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SecondaryButton(
                        text = "Edit",
                        onClick = onEdit,
                        modifier = Modifier.weight(1f)
                    )

                    SecondaryButton(
                        text = "Remove",
                        onClick = onRemove,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = "Editing will support markup, blur, and review after real media picking is added.",
                    color = AacTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun DraftsCard(
    drafts: List<DraftUiItem>,
    onEditDraft: (String) -> Unit,
    onDeleteDraft: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedDraftId by rememberSaveable { mutableStateOf<String?>(null) }

    val orderedDrafts = remember(drafts) {
        drafts.sortedByDescending { draft -> draft.createdAt }
    }
    val previewDrafts = orderedDrafts.take(3)
    val selectedDraft = remember(orderedDrafts, selectedDraftId) {
        orderedDrafts.firstOrNull { draft -> draft.id == selectedDraftId }
    }

    AppCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Drafts",
                    style = MaterialTheme.typography.titleLarge
                )

                TextButton(onClick = { isExpanded = true }) {
                    Text("📂 Open")
                }
            }

            if (orderedDrafts.isEmpty()) {
                Text(
                    text = "No saved drafts yet.",
                    color = AacTextSecondary
                )
            } else {
                previewDrafts.forEach { draft ->
                    DraftPreviewRow(
                        draft = draft,
                        modifier = Modifier.clickable {
                            selectedDraftId = draft.id
                            isExpanded = true
                        }
                    )
                }

                if (orderedDrafts.size > previewDrafts.size) {
                    Text(
                        text = "...",
                        color = AacTextSecondary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }

    if (isExpanded) {
        Dialog(
            onDismissRequest = {
                isExpanded = false
                selectedDraftId = null
            }
        ) {
            AppCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "All drafts",
                            style = MaterialTheme.typography.titleLarge
                        )

                        TextButton(onClick = {
                            isExpanded = false
                            selectedDraftId = null
                        }) {
                            Text("✕")
                        }
                    }

                    Text(
                        text = "Select a message draft to edit or delete.",
                        color = AacTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        orderedDrafts.forEach { draft ->
                            DraftListRow(
                                draft = draft,
                                selected = draft.id == selectedDraftId,
                                onClick = { selectedDraftId = draft.id }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SecondaryButton(
                            leadingIconRes = R.drawable.ic_action_edit3,
                            text = "Edit",
                            onClick = {
                                selectedDraft?.let { draft ->
                                    onEditDraft(draft.id)
                                    isExpanded = false
                                    selectedDraftId = null
                                }
                            },
                            enabled = selectedDraft != null,
                            modifier = Modifier.weight(1f)
                        )

                        SecondaryButton(
                            leadingIconRes = R.drawable.ic_action_delete,
                            text = "Delete",
                            onClick = {
                                selectedDraft?.let { draft ->
                                    onDeleteDraft(draft.id)
                                    selectedDraftId = null
                                }
                            },
                            enabled = selectedDraft != null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftPreviewRow(
    draft: DraftUiItem,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.secondary
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = draft.previewLabel(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        DraftAttachmentIcons(attachments = draft.attachments)
    }
}

@Composable
private fun DraftListRow(
    draft: DraftUiItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                }
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = draft.previewLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            DraftAttachmentIcons(attachments = draft.attachments)
        }

        if (draft.attachments.isNotEmpty()) {
            Text(
                text = draft.attachmentSummary(),
                color = AacTextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun DraftAttachmentIcons(
    attachments: List<DraftAttachmentUiItem>
) {
    if (attachments.isEmpty()) return

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        attachments.forEach { attachment ->
            Image(
                painter = painterResource(
                    id = when (attachment.kind) {
                        ShareAttachmentKind.Image -> R.drawable.ic_action_upload_image
                        ShareAttachmentKind.Video -> R.drawable.ic_action_upload_file
                    }
                ),
                contentDescription = when (attachment.kind) {
                    ShareAttachmentKind.Image -> "Photo attachment"
                    ShareAttachmentKind.Video -> "Video attachment"
                },
                modifier = Modifier.size(20.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun RecentMessagesCard(
    sharedMessages: List<MessageWithSenderAndAttachments>,
    currentUserId: String?,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "My Shared Items 🧾",
                style = MaterialTheme.typography.titleLarge
            )

            val currentUserMessages = remember(sharedMessages, currentUserId) {
                sharedMessages.filter { it.message.senderUserId == currentUserId }
            }

            if (currentUserMessages.isEmpty()) {
                Text(
                    text = "No messages yet.",
                    color = AacTextSecondary
                )
            } else {
                currentUserMessages.take(4).forEach { message ->
                    val targetLabel = when (message.message.target) {
                        MessageTarget.GROUP -> "Group"
                        MessageTarget.FACILITATOR -> "Facilitator"
                        MessageTarget.PRIVATE -> "Private"
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "[to $targetLabel] ${message.message.text ?: "[file]"}",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (message.attachments.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                message.attachments.forEach { attachment ->
                                    RecentMessageAttachmentPreview(attachment = attachment)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentMessageAttachmentPreview(
    attachment: AttachmentEntity
) {
    val previewUri = attachment.localUri.toUri()

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = previewUri,
            contentDescription = when {
                attachment.mimeType.startsWith("video/") -> "Video attachment"
                else -> "Image attachment"
            },
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (attachment.mimeType.startsWith("video/")) {
            Text(
                text = "▶",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun DraftUiItem.previewLabel(): String {
    val cleanText = text.trim()
    if (cleanText.isNotBlank()) return cleanText

    return when {
        attachments.isEmpty() -> "Empty draft"
        attachments.all { it.kind == ShareAttachmentKind.Image } ->
            if (attachments.size == 1) "Photo draft" else "${attachments.size} photo draft"

        attachments.all { it.kind == ShareAttachmentKind.Video } ->
            if (attachments.size == 1) "Video draft" else "${attachments.size} video draft"

        else -> "Mixed media draft"
    }
}

private fun DraftUiItem.attachmentSummary(): String {
    return attachments.joinToString(" • ") { attachment ->
        when (attachment.kind) {
            ShareAttachmentKind.Image -> "Photo"
            ShareAttachmentKind.Video -> "Video"
        }
    }
}

private fun ShareAttachmentPreview.previewUriOrNull(): Uri? {
    return previewUri?.toUri()
}

private fun previewAttachments(): List<ShareAttachmentPreview> {
    return listOf(
        ShareAttachmentPreview(
            id = "preview-image-1",
            displayName = "Image 1",
            previewUri = "file:///preview_attachment_image_1.png",
            kind = ShareAttachmentKind.Image,
            thumbnailRes = R.drawable.ic_action_upload_image
        ),
        ShareAttachmentPreview(
            id = "preview-image-2",
            displayName = "Image 2",
            previewUri = "file:///preview_attachment_image_2.png",
            kind = ShareAttachmentKind.Image,
            thumbnailRes = R.drawable.ic_action_upload_image
        ),
        ShareAttachmentPreview(
            id = "preview-image-3",
            displayName = "Image 3",
            previewUri = "file:///preview_attachment_image_3.png",
            kind = ShareAttachmentKind.Image,
            thumbnailRes = R.drawable.ic_action_upload_image
        )
    )
}

private fun previewDrafts(): List<DraftUiItem> {
    return listOf(
        DraftUiItem(
            id = "draft-3",
            text = "Can we talk about the schedule tomorrow?",
            target = MessageTarget.GROUP,
            createdAt = System.currentTimeMillis() - 100000,
            attachments = listOf(
                DraftAttachmentUiItem(
                    id = "draft-3-img-1",
                    localUri = "file:///preview_attachment_image_1.png",
                    mimeType = "image/png",
                    originalName = "preview_attachment_image_1.png"
                )
            )
        ),
        DraftUiItem(
            id = "draft-2",
            text = "I need help with this idea and I added a video.",
            target = MessageTarget.FACILITATOR,
            createdAt = System.currentTimeMillis() - 200000,
            attachments = listOf(
                DraftAttachmentUiItem(
                    id = "draft-2-video-1",
                    localUri = "file:///preview_attachment_video_1.png",
                    mimeType = "video/mp4",
                    originalName = "preview_attachment_video_1.png"
                )
            )
        ),
        DraftUiItem(
            id = "draft-1",
            text = "",
            target = MessageTarget.GROUP,
            createdAt = System.currentTimeMillis() - 300000,
            attachments = listOf(
                DraftAttachmentUiItem(
                    id = "draft-1-img-1",
                    localUri = "file:///preview_attachment_image_2.png",
                    mimeType = "image/png",
                    originalName = "preview_attachment_image_2.png"
                ),
                DraftAttachmentUiItem(
                    id = "draft-1-img-2",
                    localUri = "file:///preview_attachment_image_3.png",
                    mimeType = "image/png",
                    originalName = "preview_attachment_image_3.png"
                )
            )
        )
    )
}

private fun previewShareUiState(): ParticipantUiState {
    val currentUserId = "u1"

    val mockMessages = listOf(
        MessageWithSenderAndAttachments(
            message = MessageWithSender(
                id = "1",
                sessionId = "s1",
                senderUserId = currentUserId,
                senderName = "Alice",
                target = MessageTarget.GROUP,
                text = "Hello everyone!",
                attachmentId = null,
                createdAt = System.currentTimeMillis() - 600000,
                status = MessageStatus.SENT,
                saved = false,
                displayedOnMonitor = false
            ),
            attachments = emptyList()
        ),
        MessageWithSenderAndAttachments(
            message = MessageWithSender(
                id = "2",
                sessionId = "s1",
                senderUserId = currentUserId,
                senderName = "Alice",
                target = MessageTarget.FACILITATOR,
                text = "I typed this earlier and saved it as a draft.",
                attachmentId = null,
                createdAt = System.currentTimeMillis() - 500000,
                status = MessageStatus.DRAFT,
                saved = false,
                displayedOnMonitor = false
            ),
            attachments = emptyList()
        ),
        MessageWithSenderAndAttachments(
            message = MessageWithSender(
                id = "3",
                sessionId = "s1",
                senderUserId = "u2",
                senderName = "Bob",
                target = MessageTarget.FACILITATOR,
                text = "I have a question.",
                attachmentId = null,
                createdAt = System.currentTimeMillis() - 300000,
                status = MessageStatus.SENT,
                saved = true,
                displayedOnMonitor = false
            ),
            attachments = emptyList()
        ),
        MessageWithSenderAndAttachments(
            message = MessageWithSender(
                id = "4",
                sessionId = "s1",
                senderUserId = currentUserId,
                senderName = "Alice",
                target = MessageTarget.FACILITATOR,
                text = "Can you help me with this?",
                attachmentId = null,
                createdAt = System.currentTimeMillis() - 100000,
                status = MessageStatus.SENT,
                saved = false,
                displayedOnMonitor = false
            ),
            attachments = listOf(
                AttachmentEntity(
                    id = "preview-att-1",
                    messageId = "4",
                    localUri = "file:///preview_attachment_image_1.png",
                    mimeType = "image/png",
                    originalName = "preview_attachment_image_1.png",
                    sortOrder = 0,
                    createdAt = System.currentTimeMillis() - 100000
                )
            )
        )
    )

    return ParticipantUiState(
        user = UserEntity(
            uid = currentUserId,
            displayName = "Alice",
            createdAt = 0
        ),
        messages = mockMessages,
        drafts = previewDrafts(),
        shareComposerText = "I want to share this with the group.",
        shareTarget = MessageTarget.GROUP,
        shareAttachmentPreviews = previewAttachments()
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun ShareScreenPhonePreview() {
    GroupAacTheme {
        ShareScreen(
            uiState = previewShareUiState(),
            onMessageChange = {},
            onSaveDraft = {},
            onClearComposer = {},
            onUploadAttachment = {},
            onSelectAttachment = {},
            onDismissAttachmentPreview = {},
            onRemoveAttachment = {},
            onEditAttachment = {},
            onTargetChange = {},
            onSendShare = {},
            onEditDraft = {},
            onDeleteDraft = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Composable
fun ShareScreenTabletPortraitPreview() {
    GroupAacTheme {
        ShareScreen(
            uiState = previewShareUiState(),
            onMessageChange = {},
            onSaveDraft = {},
            onClearComposer = {},
            onUploadAttachment = {},
            onSelectAttachment = {},
            onDismissAttachmentPreview = {},
            onRemoveAttachment = {},
            onEditAttachment = {},
            onTargetChange = {},
            onSendShare = {},
            onEditDraft = {},
            onDeleteDraft = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
fun ShareScreenTabletLandscapePreview() {
    GroupAacTheme {
        ShareScreen(
            uiState = previewShareUiState(),
            onMessageChange = {},
            onSaveDraft = {},
            onClearComposer = {},
            onUploadAttachment = {},
            onSelectAttachment = {},
            onDismissAttachmentPreview = {},
            onRemoveAttachment = {},
            onEditAttachment = {},
            onTargetChange = {},
            onSendShare = {},
            onEditDraft = {},
            onDeleteDraft = {}
        )
    }
}
