package com.example.groupaac.ui.participant

import androidx.compose.foundation.layout.padding
import android.net.Uri
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.example.groupaac.LocalAppContainer
import com.example.groupaac.data.entity.UserSettingsEntity
import com.example.groupaac.model.MessageTarget
import com.example.groupaac.model.SignalType
import com.example.groupaac.ui.common.AppBottomNavBar
import com.example.groupaac.ui.common.ParticipantNavItem
import com.example.groupaac.ui.debug.DebugScreen
import com.example.groupaac.ui.debug.DebugViewModel
import com.example.groupaac.ui.debug.DebugViewModelFactory
import com.example.groupaac.ui.theme.GroupAacTheme

@Composable
fun ParticipantHomeScreen(viewModel: ParticipantViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val container = LocalAppContainer.current
    val debugViewModel: DebugViewModel = composeViewModel(
        factory = DebugViewModelFactory(container.debugRepository)
    )

    ParticipantHomeScreenContent(
        uiState = uiState,

        onMessageChange = viewModel::updateShareComposerText,
        onSaveCurrentDraft = viewModel::saveCurrentDraft,
        onClearComposer = viewModel::clearShareComposer,
        onUploadAttachment = viewModel::addSelectedAttachments,
        onSelectAttachment = viewModel::selectShareAttachment,
        onDismissAttachmentPreview = viewModel::dismissShareAttachmentPreview,
        onRemoveAttachment = viewModel::removeShareAttachment,
        onEditAttachment = viewModel::editSelectedShareAttachment,
        onTargetChange = viewModel::setShareTarget,
        onSendShare = viewModel::sendCurrentShare,
        onEditDraft = viewModel::editDraft,
        onDeleteDraft = viewModel::deleteDraft,

        onSendSignal = viewModel::sendSignal,
        onClearSignal = viewModel::clearCurrentSignal,
        onUpdateSettings = viewModel::updateSettings,
        debugViewModel = debugViewModel
    )
}

@Composable
fun ParticipantHomeScreenContent(
    uiState: ParticipantUiState,

    onMessageChange: (String) -> Unit,
    onSaveCurrentDraft: () -> Unit,
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

    onSendSignal: (SignalType) -> Unit,
    onClearSignal: () -> Unit,
    onUpdateSettings: (UserSettingsEntity) -> Unit,
    debugViewModel: DebugViewModel? = null
) {
    var selected by remember {
        mutableStateOf<ParticipantNavItem>(ParticipantNavItem.Share)
    }

    val items = listOf(
        ParticipantNavItem.Share,
        ParticipantNavItem.Signal,
        ParticipantNavItem.Social,
        ParticipantNavItem.Settings,
        ParticipantNavItem.Debug
    )

    Scaffold(
        bottomBar = {
            AppBottomNavBar(
                items = items,
                selected = selected,
                onSelected = { selected = it }
            )
        }
    ) { padding ->
        when (selected) {
            ParticipantNavItem.Share -> ShareScreen(
                uiState = uiState,
                onMessageChange = onMessageChange,
                onSaveDraft = onSaveCurrentDraft,
                onClearComposer = onClearComposer,
                onUploadAttachment = onUploadAttachment,
                onSelectAttachment = onSelectAttachment,
                onDismissAttachmentPreview = onDismissAttachmentPreview,
                onRemoveAttachment = onRemoveAttachment,
                onEditAttachment = onEditAttachment,
                onTargetChange = onTargetChange,
                onSendShare = onSendShare,
                onEditDraft = onEditDraft,
                onDeleteDraft = onDeleteDraft,
                modifier = Modifier.padding(padding)
            )

            ParticipantNavItem.Signal -> SignalScreen(
                uiState = uiState,
                onSignal = onSendSignal,
                onClearSignal = onClearSignal,
                modifier = Modifier.padding(padding)
            )

            ParticipantNavItem.Social -> SocialScreen(
                uiState = uiState,
                modifier = Modifier.padding(padding)
            )

            ParticipantNavItem.Settings -> ParticipantSettingsScreen(
                uiState = uiState,
                onUpdateSettings = onUpdateSettings,
                modifier = Modifier.padding(padding)
            )

            ParticipantNavItem.Debug -> DebugScreen(
                viewModel = debugViewModel ?: error("DebugViewModel missing"),
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun ParticipantHomeScreenPhonePreview() {
    GroupAacTheme {
        ParticipantHomeScreenContent(
            uiState = ParticipantUiState(),

            onMessageChange = {},
            onSaveCurrentDraft = {},
            onClearComposer = {},
            onUploadAttachment = {},
            onSelectAttachment = {},
            onDismissAttachmentPreview = {},
            onRemoveAttachment = {},
            onEditAttachment = {},
            onTargetChange = {},
            onSendShare = {},
            onEditDraft = {},
            onDeleteDraft = {},

            onSendSignal = {},
            onClearSignal = {},
            onUpdateSettings = {}
        )
    }
}
