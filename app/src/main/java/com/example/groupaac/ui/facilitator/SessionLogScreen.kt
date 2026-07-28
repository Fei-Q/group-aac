package com.example.groupaac.ui.facilitator

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.groupaac.data.dao.MessageWithSender
import com.example.groupaac.data.dao.MessageWithSenderAndAttachments
import com.example.groupaac.data.entity.AttachmentEntity
import com.example.groupaac.data.entity.DisplayStateEntity
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.model.MessageStatus
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.ui.common.AppCard
import com.example.groupaac.ui.theme.AacBackground
import com.example.groupaac.ui.theme.AacTextSecondary
import com.example.groupaac.ui.theme.GroupAacTheme
import com.example.groupaac.util.TimeUtils

@Composable
fun SessionLogScreen(
    uiState: FacilitatorUiState,
    onSave: (String) -> Unit,
    onShow: (String) -> Unit,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPinDisplayedMessage: () -> Unit,
    onUnpinDisplayedMessage: () -> Unit,
    onClearDisplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleMessages = remember(uiState.messages) {
        uiState.messages
            .filterNot {
                it.message.status == MessageStatus.DRAFT ||
                    it.message.status == MessageStatus.DELETED
            }
            .sortedBy { it.message.createdAt }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(AacBackground)
            .padding(22.dp)
    ) {
        val useTwoPane = maxWidth >= 900.dp
        val displayedMessageId = uiState.displayedMessage?.message?.id
        val isPinned = uiState.displayState?.isPinned == true

        if (useTwoPane) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1.85f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SessionLogHeader(
                        session = uiState.session,
                        messages = visibleMessages
                    )

                    SessionMessageTable(
                        messages = visibleMessages,
                        currentlyDisplayedMessageId = displayedMessageId,
                        isPinned = isPinned,
                        onSave = onSave,
                        onShow = onShow,
                        onRestore = onRestore,
                        onDelete = onDelete,
                        modifier = Modifier.weight(1f)
                    )
                }

                CurrentlyDisplayedPane(
                    displayedMessage = uiState.displayedMessage,
                    isPinned = isPinned,
                    onPinDisplayedMessage = onPinDisplayedMessage,
                    onUnpinDisplayedMessage = onUnpinDisplayedMessage,
                    onClearDisplay = onClearDisplay,
                    modifier = Modifier.weight(0.75f)
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SessionLogHeader(
                    session = uiState.session,
                    messages = visibleMessages
                )

                CurrentlyDisplayedPane(
                    displayedMessage = uiState.displayedMessage,
                    isPinned = isPinned,
                    onPinDisplayedMessage = onPinDisplayedMessage,
                    onUnpinDisplayedMessage = onUnpinDisplayedMessage,
                    onClearDisplay = onClearDisplay
                )

                SessionMessageTable(
                    messages = visibleMessages,
                    currentlyDisplayedMessageId = displayedMessageId,
                    isPinned = isPinned,
                    onSave = onSave,
                    onShow = onShow,
                    onRestore = onRestore,
                    onDelete = onDelete,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SessionLogHeader(
    session: SessionEntity?,
    messages: List<MessageWithSenderAndAttachments>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = session?.name ?: "Session Log",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.secondary
            )

            Text(
                text = "History of all messages and files shared in the current session.",
                color = AacTextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        SessionStatusChip(
            session = session,
            messageCount = messages.size
        )
    }
}

@Composable
private fun SessionStatusChip(
    session: SessionEntity?,
    messageCount: Int
) {
    val label = when {
        session == null -> "No session loaded"
        session.actualStartedAt != null && session.actualEndedAt == null -> {
            val elapsedMinutes = (
                (System.currentTimeMillis() - session.actualStartedAt)
                    .coerceAtLeast(0L) / 60_000L
                ).coerceAtLeast(1L)
            "Started ${TimeUtils.clockTime(session.actualStartedAt)} · $elapsedMinutes min · $messageCount messages"
        }
        session.actualStartedAt != null && session.actualEndedAt != null -> {
            val durationMinutes = (
                (session.actualEndedAt - session.actualStartedAt)
                    .coerceAtLeast(0L) / 60_000L
                ).coerceAtLeast(1L)
            "Ended ${TimeUtils.clockTime(session.actualEndedAt)} · $durationMinutes min · $messageCount messages"
        }
        session.scheduledStartAt != null -> {
            val durationText = session.scheduledDurationMinutes
                ?.let { " · planned $it min" }
                .orEmpty()
            "Scheduled ${TimeUtils.clockTime(session.scheduledStartAt)}$durationText · $messageCount messages"
        }
        else -> "Not started · $messageCount messages"
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SessionMessageTable(
    messages: List<MessageWithSenderAndAttachments>,
    currentlyDisplayedMessageId: String?,
    isPinned: Boolean,
    onSave: (String) -> Unit,
    onShow: (String) -> Unit,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TableHeaderRow()

        if (messages.isEmpty()) {
            EmptyLogCard()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(messages, key = { it.message.id }) { row ->
                    MessageTableRow(
                        row = row,
                        currentlyDisplayedMessageId = currentlyDisplayedMessageId,
                        isPinned = isPinned,
                        onSave = onSave,
                        onShow = onShow,
                        onRestore = onRestore,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

@Composable
private fun TableHeaderRow() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TableHeaderCell(text = "Time", weight = 0.7f)
            TableHeaderCell(text = "Sender", weight = 0.9f)
            TableHeaderCell(text = "Message", weight = 2.9f)
            TableHeaderCell(text = "Action", weight = 1.85f)
        }
    }
}

@Composable
private fun RowScope.TableHeaderCell(
    text: String,
    weight: Float
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun EmptyLogCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = "No participant messages have been sent yet.",
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = AacTextSecondary
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageTableRow(
    row: MessageWithSenderAndAttachments,
    currentlyDisplayedMessageId: String?,
    isPinned: Boolean,
    onSave: (String) -> Unit,
    onShow: (String) -> Unit,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val message = row.message
    val isSaved = message.status == MessageStatus.SAVED || message.saved
    val isDisplayed = message.id == currentlyDisplayedMessageId ||
        message.displayedOnMonitor
    val canRestore = !isDisplayed && message.status == MessageStatus.DISPLAYED

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = TimeUtils.clockTime(message.createdAt),
                modifier = Modifier.weight(0.7f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1
            )

            PersonCell(
                name = message.senderName,
                modifier = Modifier.weight(0.9f)
            )

            MessageContentCell(
                text = message.text,
                attachments = row.attachments,
                modifier = Modifier
                    .weight(2.9f)
                    .padding(end = 10.dp)
            )

            FlowRow(
                modifier = Modifier.weight(1.85f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallActionButton(
                    label = if (isSaved) "Saved" else "Save",
                    enabled = !isSaved,
                    emphasis = ActionEmphasis.Secondary,
                    onClick = { onSave(message.id) }
                )

                SmallActionButton(
                    label = when {
                        isDisplayed && isPinned -> "Pinned"
                        isDisplayed -> "Showing"
                        canRestore -> "Restore"
                        else -> "Show"
                    },
                    enabled = !isDisplayed,
                    emphasis = ActionEmphasis.Primary,
                    onClick = {
                        if (canRestore) {
                            onRestore(message.id)
                        } else {
                            onShow(message.id)
                        }
                    }
                )

                SmallActionButton(
                    label = "Delete",
                    emphasis = ActionEmphasis.Destructive,
                    onClick = { onDelete(message.id) }
                )
            }
        }
    }
}

@Composable
private fun PersonCell(
    name: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(end = 16.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(10.dp)
            )
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageContentCell(
    text: String?,
    attachments: List<AttachmentEntity>,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!text.isNullOrBlank()) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(min = 120.dp, max = 440.dp)
            )
        }

        attachments.forEach { attachment ->
            AttachmentPreview(attachment = attachment)
        }

        if (text.isNullOrBlank() && attachments.isEmpty()) {
            MediaChip(label = "media/file item")
        }
    }
}

@Composable
private fun AttachmentPreview(
    attachment: AttachmentEntity
) {
    if (attachment.mimeType.startsWith("image/")) {
        AsyncImage(
            model = Uri.parse(attachment.localUri),
            contentDescription = "Attached image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(10.dp)
                )
        )
    } else {
        MediaChip(
            label = when {
                attachment.mimeType.startsWith("video/") -> "video"
                else -> "file"
            }
        )
    }
}

@Composable
private fun MediaChip(
    label: String
) {
    Row(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp)
            )
            .background(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "🖼",
            style = MaterialTheme.typography.bodyLarge
        )

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CurrentlyDisplayedPane(
    displayedMessage: MessageWithSenderAndAttachments?,
    isPinned: Boolean,
    onPinDisplayedMessage: () -> Unit,
    onUnpinDisplayedMessage: () -> Unit,
    onClearDisplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Currently showing:",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.secondary
        )

        AppCard {
            if (displayedMessage == null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "No messages displayed currently.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AacTextSecondary
                    )
                }
            } else {
                val message = displayedMessage.message

                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = TimeUtils.clockTime(message.createdAt),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    MessageContentCell(
                        text = message.text,
                        attachments = displayedMessage.attachments
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SmallActionButton(
                            label = if (isPinned) "Unpin Message" else "Pin Message",
                            enabled = true,
                            emphasis = ActionEmphasis.Secondary,
                            onClick = {
                                if (isPinned) {
                                    onUnpinDisplayedMessage()
                                } else {
                                    onPinDisplayedMessage()
                                }
                            }
                        )
                        SmallActionButton(
                            label = "Clear Screen",
                            emphasis = ActionEmphasis.Secondary,
                            onClick = onClearDisplay
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallActionButton(
    label: String,
    enabled: Boolean = true,
    emphasis: ActionEmphasis,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    val contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)

    when (emphasis) {
        ActionEmphasis.Primary -> {
            Button(
                onClick = onClick,
                enabled = enabled,
                shape = shape,
                contentPadding = contentPadding,
                modifier = Modifier.heightIn(min = 36.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        ActionEmphasis.Secondary -> {
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                shape = shape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                contentPadding = contentPadding,
                modifier = Modifier.heightIn(min = 36.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        ActionEmphasis.Destructive -> {
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                shape = shape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                contentPadding = contentPadding,
                modifier = Modifier.heightIn(min = 36.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private enum class ActionEmphasis {
    Primary,
    Secondary,
    Destructive
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
fun SessionLogScreenPreview() {
    val now = System.currentTimeMillis()

    val mockMessages = listOf(
        MessageWithSenderAndAttachments(
            message = MessageWithSender(
                "1",
                "s1",
                "u1",
                "Alice",
                MessageTarget.GROUP,
                "I went to the farmers market with my sister this weekend.",
                null,
                now - 23 * 60_000,
                MessageStatus.SENT,
                false,
                false
            ),
            attachments = emptyList()
        ),
        MessageWithSenderAndAttachments(
            message = MessageWithSender(
                "2",
                "s1",
                "u2",
                "Dave",
                MessageTarget.GROUP,
                null,
                null,
                now - 13 * 60_000,
                MessageStatus.SENT,
                false,
                false
            ),
            attachments = emptyList()
        ),
        MessageWithSenderAndAttachments(
            message = MessageWithSender(
                "3",
                "s1",
                "u3",
                "Claire",
                MessageTarget.GROUP,
                "I like cooking at home.",
                null,
                now - 10 * 60_000,
                MessageStatus.SENT,
                false,
                false
            ),
            attachments = emptyList()
        ),
        MessageWithSenderAndAttachments(
            message = MessageWithSender(
                "4",
                "s1",
                "u4",
                "Elisa",
                MessageTarget.FACILITATOR,
                "Please show my last message again.",
                null,
                now - 6 * 60_000,
                MessageStatus.SAVED,
                true,
                false
            ),
            attachments = emptyList()
        ),
        MessageWithSenderAndAttachments(
            message = MessageWithSender(
                "5",
                "s1",
                "u5",
                "Mark",
                MessageTarget.GROUP,
                "Gardening on Sunday",
                null,
                now,
                MessageStatus.DISPLAYED,
                false,
                true
            ),
            attachments = emptyList()
        )
    )

    GroupAacTheme {
        SessionLogScreen(
            uiState = FacilitatorUiState(
                messages = mockMessages,
                displayedMessage = mockMessages.last(),
                displayState = DisplayStateEntity(
                    sessionId = "s1",
                    currentMessageId = "5",
                    isPinned = true,
                    updatedAt = now
                )
            ),
            onSave = {},
            onShow = {},
            onRestore = {},
            onDelete = {},
            onPinDisplayedMessage = {},
            onUnpinDisplayedMessage = {},
            onClearDisplay = {}
        )
    }
}
