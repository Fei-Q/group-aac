package com.example.groupaac.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.groupaac.LocalAppContainer
import com.example.groupaac.model.UserRole
import com.example.groupaac.ui.account.AccountViewModel
import com.example.groupaac.ui.account.AccountViewModelFactory
import com.example.groupaac.ui.account.CreateAccountScreen
import com.example.groupaac.ui.account.LoginScreen
import com.example.groupaac.ui.facilitator.FacilitatorHomeScreen
import com.example.groupaac.ui.facilitator.FacilitatorViewModel
import com.example.groupaac.ui.facilitator.FacilitatorViewModelFactory
import com.example.groupaac.ui.participant.ParticipantHomeScreen
import com.example.groupaac.ui.participant.ParticipantViewModel
import com.example.groupaac.ui.participant.ParticipantViewModelFactory
import com.example.groupaac.ui.session.JoinSessionScreen
import com.example.groupaac.ui.session.SessionViewModel
import com.example.groupaac.ui.session.SessionViewModelFactory

object Routes {
    const val Login = "login"
    const val CreateAccount = "create_account"
    const val JoinSession = "join_session"
    const val Participant = "participant"
    const val Facilitator = "facilitator"
}

@Composable
fun AppNavGraph(navController: NavHostController = rememberNavController()) {
    val container = LocalAppContainer.current
    val accountViewModel: AccountViewModel = viewModel(factory = AccountViewModelFactory(container.accountRepository))
    val accountState by accountViewModel.uiState.collectAsState()

    NavHost(navController = navController, startDestination = Routes.Login) {
        composable(Routes.Login) {
            LoginScreen(
                uiState = accountState,
                onUserSelected = { user ->
                    accountViewModel.switchUser(user.id)
                    navController.navigate(Routes.JoinSession)
                },
                onCreateAccount = { navController.navigate(Routes.CreateAccount) }
            )
        }
        composable(Routes.CreateAccount) {
            CreateAccountScreen(
                onBack = { navController.popBackStack() },
                onCreate = { name, role ->
                    accountViewModel.createUser(name, role)
                    navController.navigate(Routes.JoinSession) {
                        popUpTo(Routes.Login) { inclusive = false }
                    }
                }
            )
        }
        composable(Routes.JoinSession) {
            val sessionViewModel: SessionViewModel = viewModel(
                factory = SessionViewModelFactory(container.sessionRepository, container.accountRepository)
            )
            val sessionState by sessionViewModel.uiState.collectAsState()
            JoinSessionScreen(
                currentUser = accountState.activeUser,
                onJoin = { code, displayName, role, rememberSettings ->
                    sessionViewModel.join(code, displayName, role, rememberSettings) { resultRole ->
                        when (resultRole) {
                            UserRole.PARTICIPANT -> navController.navigate(Routes.Participant)
                            UserRole.FACILITATOR -> navController.navigate(Routes.Facilitator)
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.Participant) {
            val participantViewModel: ParticipantViewModel = viewModel(
                factory = ParticipantViewModelFactory(
                    container.accountRepository,
                    container.sessionRepository,
                    container.messageRepository,
                    container.signalRepository,
                    container.settingsRepository,
                    container.attachmentRepository
                )
            )
            ParticipantHomeScreen(viewModel = participantViewModel)
        }
        composable(Routes.Facilitator) {
            val facilitatorViewModel: FacilitatorViewModel = viewModel(
                factory = FacilitatorViewModelFactory(
                    accountRepository = container.accountRepository,
                    settingsRepository = container.settingsRepository,
                    sessionRepository = container.sessionRepository,
                    messageRepository = container.messageRepository,
                    signalRepository = container.signalRepository,
                    facilitatorRepository = container.facilitatorRepository
                )
            )
            FacilitatorHomeScreen(viewModel = facilitatorViewModel)
        }
    }
}
