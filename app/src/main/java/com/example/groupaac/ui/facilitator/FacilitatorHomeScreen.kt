package com.example.groupaac.ui.facilitator

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.example.groupaac.LocalAppContainer
import com.example.groupaac.data.entity.UserSettingsEntity
import com.example.groupaac.ui.common.AppBottomNavBar
import com.example.groupaac.ui.common.FacilitatorNavItem
import com.example.groupaac.ui.debug.DebugScreen
import com.example.groupaac.ui.debug.DebugViewModel
import com.example.groupaac.ui.debug.DebugViewModelFactory
import com.example.groupaac.ui.theme.GroupAacTheme

@Composable
fun FacilitatorHomeScreen(viewModel: FacilitatorViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val container = LocalAppContainer.current
    val debugViewModel: DebugViewModel = composeViewModel(
        factory = DebugViewModelFactory(container.debugRepository)
    )

    FacilitatorHomeScreenContent(
        uiState = uiState,
        onSelectParticipant = viewModel::selectParticipant,
        onSnoozeParticipant = viewModel::toggleSnoozeParticipant,
        onQuickLog = viewModel::quickLog,
        onAddNote = viewModel::addNote,
        onSaveMessage = viewModel::saveMessage,
        onDisplayMessage = viewModel::displayMessage,
        onDeleteMessage = viewModel::deleteMessage,
        onClearDisplay = viewModel::clearDisplay,
        onSettingsChange = viewModel::updateSettings,
        onClearLocalHistory = {
            // TODO: implement repository method for clearing local facilitator/session history.
        },
        onExportSummary = {
            // TODO: implement export/share flow for session summary.
        },
        debugViewModel = debugViewModel
    )
}

@Composable
fun FacilitatorHomeScreenContent(
    uiState: FacilitatorUiState,
    onSelectParticipant: (String?) -> Unit,
    onSnoozeParticipant: (String) -> Unit,
    onQuickLog: (String, String) -> Unit,
    onAddNote: (String?, String) -> Unit,
    onSaveMessage: (String) -> Unit,
    onDisplayMessage: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onClearDisplay: () -> Unit,
    onSettingsChange: (UserSettingsEntity) -> Unit,
    onClearLocalHistory: () -> Unit,
    onExportSummary: () -> Unit,
    debugViewModel: DebugViewModel? = null
) {
    var selected by remember {
        mutableStateOf<FacilitatorNavItem>(FacilitatorNavItem.Participants)
    }

    val items = listOf(
        FacilitatorNavItem.Participants,
        FacilitatorNavItem.SessionLog,
        FacilitatorNavItem.Summary,
        FacilitatorNavItem.Settings,
        FacilitatorNavItem.Debug
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
            FacilitatorNavItem.Participants -> ParticipantsScreen(
                uiState = uiState,
                onSelect = onSelectParticipant,
                onSnooze = onSnoozeParticipant,
                onQuickLog = onQuickLog,
                onAddNote = onAddNote,
                modifier = Modifier.padding(padding)
            )

            FacilitatorNavItem.SessionLog -> SessionLogScreen(
                uiState = uiState,
                onSave = onSaveMessage,
                onDisplay = onDisplayMessage,
                onDelete = onDeleteMessage,
                onClearDisplay = onClearDisplay,
                modifier = Modifier.padding(padding)
            )

            FacilitatorNavItem.Summary -> SummaryScreen(
                uiState = uiState,
                modifier = Modifier.padding(padding)
            )

            FacilitatorNavItem.Settings -> {
                val settings = uiState.settings

                if (settings == null) {
                    Box(
                        modifier = Modifier.padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    FacilitatorSettingsScreen(
                        settings = settings,
                        onSettingsChange = onSettingsChange,
                        onClearLocalHistory = onClearLocalHistory,
                        onExportSummary = onExportSummary,
                        modifier = Modifier.padding(padding)
                    )
                }
            }

            FacilitatorNavItem.Debug -> DebugScreen(
                viewModel = debugViewModel ?: error("DebugViewModel missing"),
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
fun FacilitatorHomeScreenPreview() {
    GroupAacTheme {
        FacilitatorHomeScreenContent(
            uiState = FacilitatorUiState(
            settings = UserSettingsEntity(userId = "preview-facilitator")
            ),
            onSelectParticipant = {},
            onSnoozeParticipant = {},
            onQuickLog = { _, _ -> },
            onAddNote = { _, _ -> },
            onSaveMessage = {},
            onDisplayMessage = {},
            onDeleteMessage = {},
            onClearDisplay = {},
            onSettingsChange = {},
            onClearLocalHistory = {},
            onExportSummary = {}
        )
    }
}
