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
import com.example.groupaac.ui.common.FacilitatorInsideNavItem
import com.example.groupaac.ui.facilitator.FacilitatorViewModel
import com.example.groupaac.ui.facilitator.FacilitatorViewModelFactory
import com.example.groupaac.ui.facilitator.ParticipantsScreen
import com.example.groupaac.ui.facilitator.SessionLogScreen
import com.example.groupaac.ui.facilitator.SummaryScreen
import com.example.groupaac.ui.session.ActiveSessionHeader

@Composable
fun FacilitatorInSessionNavGraph(
    activeSession: ActiveSession,
    connectionState: SessionConnectionState,
    onLeaveSession: () -> Unit,
    onEndSession: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val navController = rememberNavController()

    val facilitatorViewModel: FacilitatorViewModel = viewModel(
        key = "facilitator-session-${activeSession.sessionId}",
        factory = FacilitatorViewModelFactory(
            sessionId = activeSession.sessionId,
            accountRepository = container.accountRepository,
            settingsRepository = container.settingsRepository,
            sessionRepository = container.sessionRepository,
            messageRepository = container.messageRepository,
            signalRepository = container.signalRepository,
            facilitatorRepository = container.facilitatorRepository
        )
    )

    val uiState by facilitatorViewModel.uiState
        .collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val navItems = remember {
        listOf(
            FacilitatorInsideNavItem.Participants,
            FacilitatorInsideNavItem.SessionLog,
            FacilitatorInsideNavItem.Summary
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("facilitator_inside_session"),
        topBar = {
            ActiveSessionHeader(
                activeSession = activeSession,
                connectionState = connectionState,
                isFacilitator = true,
                onLeaveSession = onLeaveSession,
                onEndSession = onEndSession
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
            startDestination =
                FacilitatorInsideRoutes.Participants,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            composable(FacilitatorInsideRoutes.Participants) {
                ParticipantsScreen(
                    uiState = uiState,
                    onSelect =
                        facilitatorViewModel::selectParticipant,
                    onApproveJoinRequest =
                        facilitatorViewModel::approveJoinRequest,
                    onDeclineJoinRequest =
                        facilitatorViewModel::declineJoinRequest,
                    onSnooze =
                        facilitatorViewModel::toggleSnoozeParticipant,
                    onQuickLog =
                        facilitatorViewModel::quickLog,
                    onAddNote =
                        facilitatorViewModel::addNote,
                    modifier = Modifier.fillMaxSize()
                )
            }

            composable(FacilitatorInsideRoutes.SessionLog) {
                SessionLogScreen(
                    uiState = uiState,
                    onSave =
                        facilitatorViewModel::saveMessage,
                    onShow =
                        facilitatorViewModel::displayMessage,
                    onRestore =
                        facilitatorViewModel::restoreMessage,
                    onDelete =
                        facilitatorViewModel::deleteMessage,
                    onPinDisplayedMessage =
                        facilitatorViewModel::pinDisplayedMessage,
                    onUnpinDisplayedMessage =
                        facilitatorViewModel::unpinDisplayedMessage,
                    onClearDisplay =
                        facilitatorViewModel::clearDisplay,
                    modifier = Modifier.fillMaxSize()
                )
            }

            composable(FacilitatorInsideRoutes.Summary) {
                SummaryScreen(
                    uiState = uiState,
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
