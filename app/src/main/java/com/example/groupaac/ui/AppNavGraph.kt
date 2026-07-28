package com.example.groupaac.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.groupaac.LocalAppContainer
import com.example.groupaac.model.ActiveSession
import com.example.groupaac.model.SessionConnectionState
import com.example.groupaac.ui.account.AccountViewModel
import com.example.groupaac.ui.account.AccountViewModelFactory
import com.example.groupaac.ui.account.CreateAccountScreen
import com.example.groupaac.ui.account.LoginScreen
import com.example.groupaac.ui.navigation.AppShell
import com.example.groupaac.ui.navigation.FacilitatorInSessionNavGraph
import com.example.groupaac.ui.navigation.OutsideSessionNavGraph
import com.example.groupaac.ui.navigation.ParticipantInSessionNavGraph
import com.example.groupaac.ui.navigation.resolveAppShell
import com.example.groupaac.ui.session.SessionCoordinatorViewModel
import com.example.groupaac.ui.session.SessionCoordinatorViewModelFactory

private object AuthRoutes {
    const val Login = "auth/login"
    const val CreateAccount = "auth/create_account"
}

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val container = LocalAppContainer.current

    val accountViewModel: AccountViewModel = viewModel(
        factory = AccountViewModelFactory(
            container.accountRepository
        )
    )
    val accountState by accountViewModel.uiState
        .collectAsStateWithLifecycle()

    val sessionCoordinatorViewModel: SessionCoordinatorViewModel =
        viewModel(
            factory = SessionCoordinatorViewModelFactory(
                accountRepository = container.accountRepository,
                sessionRepository = container.sessionRepository,
                sessionSubscriptionCoordinator =
                    container.sessionSubscriptionCoordinator
            )
        )
    val sessionState by sessionCoordinatorViewModel.uiState
        .collectAsStateWithLifecycle()

    val activeUser = accountState.activeUser

    when {
        accountState.isLoading -> {
            FullScreenLoadingIndicator()
        }

        activeUser == null -> {
            NavHost(
                navController = navController,
                startDestination = AuthRoutes.Login
            ) {
                composable(AuthRoutes.Login) {
                    LoginScreen(
                        uiState = accountState,
                        onUserSelected = { user ->
                            accountViewModel.switchUser(user.uid)
                        },
                        onCreateAccount = {
                            navController.navigate(
                                AuthRoutes.CreateAccount
                            )
                        }
                    )
                }

                composable(AuthRoutes.CreateAccount) {
                    CreateAccountScreen(
                        onBack = {
                            navController.popBackStack()
                        },
                        onCreate = { uid, displayName, homeExperience ->
                            accountViewModel.createUser(
                                uid,
                                displayName,
                                homeExperience
                            )
                        },
                        createAccountResult = accountState.createAccountResult,
                        onConsumeCreateAccountResult =
                            accountViewModel::clearCreateAccountResult
                    )
                }
            }
        }

        else -> {
            when (
                resolveAppShell(
                    homeExperience = accountState.activeHomeExperience,
                    state = sessionState.connectionState
                )
            ) {
                AppShell.Restoring -> {
                    FullScreenLoadingIndicator()
                }

                AppShell.SimpleOutsideSession,
                AppShell.AdvancedOutsideSession -> {
                    OutsideSessionNavGraph(
                        currentUser = activeUser,
                        homeExperience =
                            accountState.activeHomeExperience,
                        sessionUiState = sessionState,
                        onCreateSessionNow =
                            sessionCoordinatorViewModel::createSession,
                        onJoinSession =
                            sessionCoordinatorViewModel::joinSession,
                        onCancelFacilitatorRequest =
                            sessionCoordinatorViewModel::cancelFacilitatorRequest
                    )
                }

                AppShell.ParticipantInSession -> {
                    ParticipantInSessionNavGraph(
                        activeSession =
                            sessionState.connectionState
                                .requireActiveSession(),
                        connectionState =
                            sessionState.connectionState,
                        onLeaveSession =
                            sessionCoordinatorViewModel::leaveSession
                    )
                }

                AppShell.FacilitatorInSession -> {
                    FacilitatorInSessionNavGraph(
                        activeSession =
                            sessionState.connectionState
                                .requireActiveSession(),
                        connectionState =
                            sessionState.connectionState,
                        onLeaveSession =
                            sessionCoordinatorViewModel::leaveSession,
                        onEndSession =
                            sessionCoordinatorViewModel::endSession
                    )
                }
            }
        }
    }
}

@Composable
private fun FullScreenLoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

private fun SessionConnectionState.requireActiveSession(): ActiveSession {
    return when (this) {
        is SessionConnectionState.Connected -> session
        is SessionConnectionState.Reconnecting -> session
        is SessionConnectionState.Leaving -> session
        else -> error(
            "Connection state does not contain an active session."
        )
    }
}
