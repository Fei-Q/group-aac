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
import com.example.groupaac.model.SessionConnectionState
import com.example.groupaac.ui.common.AppBottomNavBar
import com.example.groupaac.ui.common.FacilitatorOutsideNavItem
import com.example.groupaac.ui.facilitator.FacilitatorSettingsScreen
import com.example.groupaac.ui.profile.ProfileViewModel
import com.example.groupaac.ui.profile.ProfileViewModelFactory
import com.example.groupaac.ui.session.FacilitatorSessionsScreen
import com.example.groupaac.ui.session.SessionCoordinatorUiState

@Composable
fun FacilitatorOutsideSessionNavGraph(
    currentUser: UserEntity,
    sessionUiState: SessionCoordinatorUiState,
    onCreateSession: (
        sessionName: String,
        displayName: String
    ) -> Unit,
    onJoinSession: (
        code: String,
        displayName: String,
        rememberProfile: Boolean
    ) -> Unit,
    onClearLocalHistory: () -> Unit = {},
    onExportSummary: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val navController = rememberNavController()

    val profileViewModel: ProfileViewModel = viewModel(
        key = "facilitator-profile-${currentUser.id}",
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
            FacilitatorOutsideNavItem.Sessions,
            FacilitatorOutsideNavItem.Settings
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("facilitator_outside_session"),
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
            startDestination = FacilitatorOutsideRoutes.Sessions,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            composable(FacilitatorOutsideRoutes.Sessions) {
                FacilitatorSessionsScreen(
                    currentUser = profileUiState.user ?: currentUser,
                    isWorking = sessionUiState.connectionState
                            is SessionConnectionState.Joining,
                    errorMessage = sessionUiState.errorMessage,
                    onCreateSession = onCreateSession,
                    onJoinSession = { code, displayName ->
                        onJoinSession(
                            code,
                            displayName,
                            false
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            composable(FacilitatorOutsideRoutes.Settings) {
                val settings = profileUiState.settings

                if (settings == null) {
                    LoadingDestination()
                } else {
                    FacilitatorSettingsScreen(
                        settings = settings,
                        onSettingsChange =
                            profileViewModel::updateSettings,
                        onClearLocalHistory = onClearLocalHistory,
                        onExportSummary = onExportSummary,
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