package com.example.groupaac.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.groupaac.LocalAppContainer
import com.example.groupaac.model.ActiveSession
import com.example.groupaac.model.SessionConnectionState
import com.example.groupaac.ui.common.AppBottomNavBar
import com.example.groupaac.ui.common.ParticipantInsideNavItem
import com.example.groupaac.ui.debug.DebugScreen
import com.example.groupaac.ui.debug.DebugViewModel
import com.example.groupaac.ui.debug.DebugViewModelFactory
import com.example.groupaac.ui.participant.ParticipantViewModel
import com.example.groupaac.ui.participant.ParticipantViewModelFactory
import com.example.groupaac.ui.participant.ShareScreen
import com.example.groupaac.ui.participant.SignalScreen
import com.example.groupaac.ui.session.ActiveSessionHeader

@Composable
fun ParticipantInSessionNavGraph(
    activeSession: ActiveSession,
    connectionState: SessionConnectionState,
    onLeaveSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val navController = rememberNavController()

    val participantViewModel: ParticipantViewModel = viewModel(
        key = "participant-session-${activeSession.sessionId}",
        factory = ParticipantViewModelFactory(
            sessionId = activeSession.sessionId,
            accountRepository = container.accountRepository,
            messageRepository = container.messageRepository,
            signalRepository = container.signalRepository,
            settingsRepository = container.settingsRepository,
            attachmentRepository = container.attachmentRepository
        )
    )

    val uiState by participantViewModel.uiState
        .collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val navItems = remember {
        listOf(
            ParticipantInsideNavItem.Share,
            ParticipantInsideNavItem.Signal,
            ParticipantInsideNavItem.Debug
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("participant_inside_session"),
        topBar = {
            ActiveSessionHeader(
                activeSession = activeSession,
                connectionState = connectionState,
                isFacilitator = false,
                onLeaveSession = onLeaveSession,
                onEndSession = {} // Participants cannot end sessions
            )
        },
        bottomBar = {
            AppBottomNavBar(
                items = navItems,
                currentRoute = currentRoute,
                onSelected = { item ->
                    navController.navigateToBottomDestination(item.route)
                }
            )
        }
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = ParticipantInsideRoutes.Share,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            composable(ParticipantInsideRoutes.Share) {
                ShareScreen(
                    uiState = uiState,
                    onMessageChange = participantViewModel::updateShareComposerText,
                    onSaveDraft = participantViewModel::saveCurrentDraft,
                    onClearComposer = participantViewModel::clearShareComposer,
                    onUploadAttachment = participantViewModel::addSelectedAttachments,
                    onSelectAttachment = participantViewModel::selectShareAttachment,
                    onDismissAttachmentPreview = participantViewModel::dismissShareAttachmentPreview,
                    onRemoveAttachment = participantViewModel::removeShareAttachment,
                    onEditAttachment = participantViewModel::editSelectedShareAttachment,
                    onTargetChange = participantViewModel::setShareTarget,
                    onSendShare = participantViewModel::sendCurrentShare,
                    onEditDraft = participantViewModel::editDraft,
                    onDeleteDraft = participantViewModel::deleteDraft,
                    modifier = Modifier.fillMaxSize()
                )
            }

            composable(ParticipantInsideRoutes.Signal) {
                SignalScreen(
                    uiState = uiState,
                    onSignal = participantViewModel::sendSignal,
                    onClearSignal = participantViewModel::clearCurrentSignal,
                    modifier = Modifier.fillMaxSize()
                )
            }

            composable(ParticipantInsideRoutes.Debug) {
                val debugViewModel: DebugViewModel = viewModel(
                    factory = DebugViewModelFactory(
                        debugRepository = container.debugRepository,
                        sessionId = activeSession.sessionId
                    )
                )

                DebugScreen(
                    viewModel = debugViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun NavHostController.navigateToBottomDestination(
    route: String
) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }

        launchSingleTop = true
        restoreState = true
    }
}
