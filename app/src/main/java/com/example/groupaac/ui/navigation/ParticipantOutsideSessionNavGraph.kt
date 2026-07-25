package com.example.groupaac.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.model.SessionRole
import com.example.groupaac.model.SessionConnectionState
import com.example.groupaac.ui.common.AppBottomNavBar
import com.example.groupaac.ui.common.ParticipantOutsideNavItem
import com.example.groupaac.ui.participant.ParticipantSettingsScreen
import com.example.groupaac.ui.participant.SocialScreen
import com.example.groupaac.ui.profile.ProfileViewModel
import com.example.groupaac.ui.profile.ProfileViewModelFactory
import com.example.groupaac.ui.session.AwaitingApprovalScreen
import com.example.groupaac.ui.session.JoinSessionScreen
import com.example.groupaac.ui.session.SessionCoordinatorUiState

@Composable
fun ParticipantOutsideSessionNavGraph(
    currentUser: UserEntity,
    sessionUiState: SessionCoordinatorUiState,
    onJoinSession: (
        code: String,
        displayName: String,
        sessionRole: SessionRole,
        rememberProfile: Boolean
    ) -> Unit,
    onCancelFacilitatorRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val navController = rememberNavController()

    val profileViewModel: ProfileViewModel = viewModel(
        key = "participant-profile-${currentUser.id}",
        factory = ProfileViewModelFactory(
            userId = currentUser.id,
            accountRepository = container.accountRepository,
            settingsRepository = container.settingsRepository
        )
    )

    val profileUiState by profileViewModel.uiState
        .collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val navItems = remember {
        listOf(
            ParticipantOutsideNavItem.Join,
            ParticipantOutsideNavItem.Social,
            ParticipantOutsideNavItem.Settings
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("participant_outside_session"),
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
            startDestination = ParticipantOutsideRoutes.Join,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            composable(ParticipantOutsideRoutes.Join) {
                when (
                    val state = sessionUiState.connectionState
                ) {
                    is SessionConnectionState.AwaitingApproval -> {
                        AwaitingApprovalScreen(
                            sessionName = state.sessionName,
                            onCancelRequest =
                                onCancelFacilitatorRequest,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    else -> {
                        JoinSessionScreen(
                            currentUser = currentUser,
                            isJoining = sessionUiState.connectionState
                                    is SessionConnectionState.Joining,
                            errorMessage = sessionUiState.errorMessage,
                            onJoin = onJoinSession,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            composable(ParticipantOutsideRoutes.Social) {
                SocialScreen(
                    user = profileUiState.user ?: currentUser,
                    modifier = Modifier.fillMaxSize()
                )
            }

            composable(ParticipantOutsideRoutes.Settings) {
                val settings = profileUiState.settings

                if (settings == null) {
                    LoadingDestination()
                } else {
                    ParticipantSettingsScreen(
                        user = profileUiState.user ?: currentUser,
                        settings = settings,
                        onUpdateSettings =
                            profileViewModel::updateSettings,
                        modifier = Modifier.fillMaxSize()
                    )
                }
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

@Composable
private fun LoadingDestination(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}
