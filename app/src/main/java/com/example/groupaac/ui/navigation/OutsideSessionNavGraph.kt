package com.example.groupaac.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.groupaac.LocalAppContainer
import com.example.groupaac.R
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.data.entity.UserSettingsEntity
import com.example.groupaac.model.HomeExperience
import com.example.groupaac.model.SessionConnectionState
import com.example.groupaac.model.SessionRole
import com.example.groupaac.ui.common.AdvancedOutsideNavItem
import com.example.groupaac.ui.common.AppBottomNavBar
import com.example.groupaac.ui.common.AppCard
import com.example.groupaac.ui.common.CompactActionButton
import com.example.groupaac.ui.common.PrimaryButton
import com.example.groupaac.ui.common.SecondaryButton
import com.example.groupaac.ui.common.SimpleOutsideNavItem
import com.example.groupaac.ui.facilitator.FacilitatorSettingsScreen
import com.example.groupaac.ui.participant.ParticipantSettingsScreen
import com.example.groupaac.ui.participant.SocialScreen
import com.example.groupaac.ui.profile.ProfileViewModel
import com.example.groupaac.ui.profile.ProfileViewModelFactory
import com.example.groupaac.ui.session.AwaitingApprovalScreen
import com.example.groupaac.ui.session.JoinSessionScreen
import com.example.groupaac.ui.session.SessionCoordinatorUiState
import com.example.groupaac.ui.theme.AacBackground
import com.example.groupaac.ui.theme.AacTextSecondary
import com.example.groupaac.util.TimeUtils
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private val ScheduleDurationOptions = listOf(30, 45, 60, 90, 120)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutsideSessionNavGraph(
    currentUser: UserEntity,
    homeExperience: HomeExperience,
    sessionUiState: SessionCoordinatorUiState,
    onCreateSessionNow: (
        sessionName: String,
        displayName: String
    ) -> Unit,
    onJoinSession: (
        code: String,
        displayName: String,
        sessionRole: SessionRole,
        rememberProfile: Boolean
    ) -> Unit,
    onCancelFacilitatorRequest: () -> Unit,
    onClearLocalHistory: () -> Unit = {},
    onExportSummary: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()

    val profileViewModel: ProfileViewModel = viewModel(
        key = "outside-profile-${currentUser.id}",
        factory = ProfileViewModelFactory(
            userId = currentUser.id,
            accountRepository = container.accountRepository,
            settingsRepository = container.settingsRepository
        )
    )

    val profileUiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val settings = profileUiState.settings ?: UserSettingsEntity(userId = currentUser.id)
    val userForActions = profileUiState.user ?: currentUser

    val upcomingSessionsFlow = remember(homeExperience, currentUser.id) {
        if (homeExperience == HomeExperience.ADVANCED) {
            container.sessionRepository.observeUpcomingHostedSessions(currentUser.id)
        } else {
            flowOf(emptyList())
        }
    }
    val liveSessionsFlow = remember(homeExperience, currentUser.id) {
        if (homeExperience == HomeExperience.ADVANCED) {
            container.sessionRepository.observeLiveHostedSessions(currentUser.id)
        } else {
            flowOf(emptyList())
        }
    }
    val pastSessionsFlow = remember(homeExperience, currentUser.id) {
        if (homeExperience == HomeExperience.ADVANCED) {
            container.sessionRepository.observePastHostedSessions(currentUser.id)
        } else {
            flowOf(emptyList())
        }
    }

    val upcomingSessions by upcomingSessionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val liveSessions by liveSessionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val pastSessions by pastSessionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var managementError by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var editingSessionId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    val simpleNavItems = remember {
        listOf(
            SimpleOutsideNavItem.Home,
            SimpleOutsideNavItem.Groups,
            SimpleOutsideNavItem.Settings
        )
    }
    val advancedNavItems = remember {
        listOf(
            AdvancedOutsideNavItem.Home,
            AdvancedOutsideNavItem.Sessions,
            AdvancedOutsideNavItem.Groups,
            AdvancedOutsideNavItem.Tools,
            AdvancedOutsideNavItem.Settings
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("outside_session"),
        bottomBar = {
            when (homeExperience) {
                HomeExperience.SIMPLE -> {
                    AppBottomNavBar(
                        items = simpleNavItems,
                        currentRoute = currentRoute ?: OutsideRoutes.Home,
                        onSelected = { item ->
                            navController.navigateToOutsideDestination(item.route)
                        }
                    )
                }

                HomeExperience.ADVANCED -> {
                    AppBottomNavBar(
                        items = advancedNavItems,
                        currentRoute = currentRoute ?: OutsideRoutes.Home,
                        onSelected = { item ->
                            navController.navigateToOutsideDestination(item.route)
                        }
                    )
                }
            }
        }
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = OutsideRoutes.Home,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            composable(OutsideRoutes.Home) {
                if (sessionUiState.connectionState is SessionConnectionState.AwaitingApproval) {
                    val awaiting =
                        sessionUiState.connectionState as SessionConnectionState.AwaitingApproval
                    AwaitingApprovalScreen(
                        sessionName = awaiting.sessionName,
                        onCancelRequest = onCancelFacilitatorRequest,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    when (homeExperience) {
                        HomeExperience.SIMPLE -> {
                            JoinSessionScreen(
                                currentUser = userForActions,
                                isJoining = sessionUiState.connectionState
                                    is SessionConnectionState.Joining,
                                errorMessage = sessionUiState.errorMessage,
                                onJoin = onJoinSession,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        HomeExperience.ADVANCED -> {
                            AdvancedHomeScreen(
                                upcomingSessions = upcomingSessions.take(3),
                                managementError = managementError,
                                onCreateNow = {
                                    onCreateSessionNow(
                                        settings.defaultSessionName,
                                        userForActions.displayName
                                    )
                                },
                                onJoinSession = {
                                    navController.navigate(OutsideRoutes.Join)
                                },
                                onManageSessions = {
                                    navController.navigateToOutsideDestination(OutsideRoutes.Sessions)
                                },
                                onScheduleSession = {
                                    editingSessionId = null
                                    navController.navigate(OutsideRoutes.Schedule)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            composable(OutsideRoutes.Join) {
                if (sessionUiState.connectionState is SessionConnectionState.AwaitingApproval) {
                    val awaiting =
                        sessionUiState.connectionState as SessionConnectionState.AwaitingApproval
                    AwaitingApprovalScreen(
                        sessionName = awaiting.sessionName,
                        onCancelRequest = onCancelFacilitatorRequest,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    JoinSessionScreen(
                        currentUser = userForActions,
                        isJoining = sessionUiState.connectionState
                            is SessionConnectionState.Joining,
                        errorMessage = sessionUiState.errorMessage,
                        onJoin = onJoinSession,
                        modifier = Modifier.fillMaxSize(),
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }

            composable(OutsideRoutes.Sessions) {
                SessionsBrowserScreen(
                    upcomingSessions = upcomingSessions,
                    liveSessions = liveSessions,
                    pastSessions = pastSessions,
                    errorMessage = managementError,
                    onScheduleSession = {
                        editingSessionId = null
                        navController.navigate(OutsideRoutes.Schedule)
                    },
                    onStartSession = { session ->
                        coroutineScope.launch {
                            managementError = null
                            runCatching {
                                container.sessionRepository.startScheduledSession(
                                    sessionId = session.id,
                                    ownerUserId = currentUser.id
                                )
                            }.onFailure { error ->
                                managementError =
                                    error.message ?: "Unable to start session."
                            }
                        }
                    },
                    onEditSession = { session ->
                        editingSessionId = session.id
                        navController.navigate(OutsideRoutes.Schedule)
                    },
                    onCancelSession = { session ->
                        coroutineScope.launch {
                            managementError = null
                            runCatching {
                                container.sessionRepository.cancelScheduledSession(
                                    sessionId = session.id,
                                    ownerUserId = currentUser.id
                                )
                            }.onFailure { error ->
                                managementError =
                                    error.message ?: "Unable to cancel session."
                            }
                        }
                    },
                    onOpenSession = { session ->
                        coroutineScope.launch {
                            managementError = null
                            runCatching {
                                container.sessionRepository.openHostedSession(
                                    sessionId = session.id,
                                    ownerUserId = currentUser.id
                                )
                            }.onFailure { error ->
                                managementError =
                                    error.message ?: "Unable to open session."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            composable(OutsideRoutes.Schedule) {
                ScheduleSessionScreen(
                    initialSession = editingSessionId?.let { sessionId ->
                        upcomingSessions.firstOrNull { session ->
                            session.id == sessionId
                        }
                    },
                    defaultSessionName = settings.defaultSessionName,
                    errorMessage = managementError,
                    onBack = {
                        navController.popBackStack()
                    },
                    onSubmit = { sessionId, sessionName, scheduledStartAt, durationMinutes, onFinished ->
                        coroutineScope.launch {
                            managementError = null
                            val result = runCatching {
                                if (sessionId == null) {
                                    container.sessionRepository.scheduleSession(
                                        name = sessionName,
                                        ownerUserId = currentUser.id,
                                        scheduledStartAt = scheduledStartAt,
                                        scheduledDurationMinutes = durationMinutes
                                    )
                                } else {
                                    container.sessionRepository.updateScheduledSession(
                                        sessionId = sessionId,
                                        ownerUserId = currentUser.id,
                                        name = sessionName,
                                        scheduledStartAt = scheduledStartAt,
                                        scheduledDurationMinutes = durationMinutes
                                    )
                                }
                            }

                            result.onSuccess {
                                editingSessionId = null
                                onFinished(null)
                                navController.popBackStack()
                            }.onFailure { error ->
                                val message =
                                    error.message ?: "Unable to save session."
                                managementError = message
                                onFinished(message)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            composable(OutsideRoutes.Groups) {
                SocialScreen(
                    user = userForActions,
                    modifier = Modifier.fillMaxSize()
                )
            }

            composable(OutsideRoutes.Tools) {
                ToolsPlaceholderScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }

            composable(OutsideRoutes.Settings) {
                if (profileUiState.settings == null) {
                    LoadingDestination()
                } else {
                    when (homeExperience) {
                        HomeExperience.SIMPLE -> {
                            ParticipantSettingsScreen(
                                user = userForActions,
                                settings = settings,
                                onUpdateSettings = profileViewModel::updateSettings,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        HomeExperience.ADVANCED -> {
                            FacilitatorSettingsScreen(
                                settings = settings,
                                onSettingsChange = profileViewModel::updateSettings,
                                onClearLocalHistory = onClearLocalHistory,
                                onExportSummary = onExportSummary,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun NavHostController.navigateToOutsideDestination(
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
private fun AdvancedHomeScreen(
    upcomingSessions: List<SessionEntity>,
    managementError: String?,
    onCreateNow: () -> Unit,
    onScheduleSession: () -> Unit,
    onJoinSession: () -> Unit,
    onManageSessions: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showCreateDialog) {
        CreateSessionChoiceDialog(
            onCreateNow = {
                showCreateDialog = false
                onCreateNow()
            },
            onSchedule = {
                showCreateDialog = false
                onScheduleSession()
            },
            onDismiss = {
                showCreateDialog = false
            }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .background(AacBackground)
            .padding(24.dp)
    ) {
        val isTablet = maxWidth >= 700.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isTablet) {
                        Modifier.widthIn(max = 1080.dp)
                    } else {
                        Modifier
                    }
                )
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Home",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.secondary
            )

            managementError
                ?.takeIf { it.isNotBlank() }
                ?.let { message ->
                    ErrorCard(message = message)
                }

            if (isTablet) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HomeActionCard(
                        title = "Create Session",
                        subtitle = "Start a session now.",
                        iconRes = R.drawable.ic_role_facilitator,
                        onClick = {
                            showCreateDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    )
                    HomeActionCard(
                        title = "Join Session",
                        subtitle = "Enter a session code to connect.",
                        iconRes = R.drawable.ic_enter_code,
                        onClick = onJoinSession,
                        modifier = Modifier.weight(1f)
                    )
                    HomeActionCard(
                        title = "Manage Sessions",
                        subtitle = "Review upcoming, live, and past sessions.",
                        iconRes = R.drawable.ic_nav_session_log,
                        onClick = onManageSessions,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        HomeActionCard(
                            title = "Create Session",
                            subtitle = "Start a session now.",
                            iconRes = R.drawable.ic_role_facilitator,
                            onClick = {
                                showCreateDialog = true
                            },
                            modifier = Modifier.weight(1f)
                        )
                        HomeActionCard(
                            title = "Join Session",
                            subtitle = "Enter a session code to connect.",
                            iconRes = R.drawable.ic_enter_code,
                            onClick = onJoinSession,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HomeActionCard(
                        title = "Manage Sessions",
                        subtitle = "Review upcoming, live, and past sessions.",
                        iconRes = R.drawable.ic_nav_session_log,
                        onClick = onManageSessions,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            AppCard {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Upcoming sessions",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    if (upcomingSessions.isEmpty()) {
                        Text(
                            text = "No upcoming sessions.",
                            color = AacTextSecondary
                        )
                    } else {
                        upcomingSessions.forEach { session ->
                            SessionSummaryRow(session = session)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeActionCard(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 104.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = AacTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun CreateSessionChoiceDialog(
    onCreateNow: () -> Unit,
    onSchedule: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create Session")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DialogOptionCard(
                    title = "Now",
                    description = "Start a session now.",
                    onClick = onCreateNow
                )
                DialogOptionCard(
                    title = "Schedule",
                    description = "Plan a session for later.",
                    onClick = onSchedule
                )
            }
        },
        confirmButton = {
            SecondaryButton(
                text = "Cancel",
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun DialogOptionCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = description,
                color = AacTextSecondary
            )
        }
    }
}

private enum class SessionsTab(
    val label: String
) {
    Upcoming("Upcoming"),
    Live("Live"),
    Past("Past")
}

@Composable
private fun SessionsBrowserScreen(
    upcomingSessions: List<SessionEntity>,
    liveSessions: List<SessionEntity>,
    pastSessions: List<SessionEntity>,
    errorMessage: String?,
    onScheduleSession: () -> Unit,
    onStartSession: (SessionEntity) -> Unit,
    onEditSession: (SessionEntity) -> Unit,
    onCancelSession: (SessionEntity) -> Unit,
    onOpenSession: (SessionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable {
        mutableStateOf(SessionsTab.Upcoming)
    }

    val visibleSessions = when (selectedTab) {
        SessionsTab.Upcoming -> upcomingSessions
        SessionsTab.Live -> liveSessions
        SessionsTab.Past -> pastSessions
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AacBackground)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sessions",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            SecondaryButton(
                text = "Schedule session",
                onClick = onScheduleSession
            )
        }

        errorMessage
            ?.takeIf { it.isNotBlank() }
            ?.let { message ->
                ErrorCard(message = message)
            }

        TabRow(selectedTabIndex = selectedTab.ordinal) {
            SessionsTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.label) }
                )
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (visibleSessions.isEmpty()) {
                item {
                    AppCard {
                        Text(
                            text = when (selectedTab) {
                                SessionsTab.Upcoming -> "No upcoming sessions."
                                SessionsTab.Live -> "No live sessions."
                                SessionsTab.Past -> "No past sessions."
                            },
                            color = AacTextSecondary
                        )
                    }
                }
            } else {
                items(
                    items = visibleSessions,
                    key = { it.id }
                ) { session ->
                    SessionListCard(
                        session = session,
                        tab = selectedTab,
                        onStartSession = onStartSession,
                        onEditSession = onEditSession,
                        onCancelSession = onCancelSession,
                        onOpenSession = onOpenSession
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionListCard(
    session: SessionEntity,
    tab: SessionsTab,
    onStartSession: (SessionEntity) -> Unit,
    onEditSession: (SessionEntity) -> Unit,
    onCancelSession: (SessionEntity) -> Unit,
    onOpenSession: (SessionEntity) -> Unit
) {
    AppCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Code ${session.joinCode}",
                    color = AacTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = when (tab) {
                        SessionsTab.Upcoming ->
                            session.scheduledStartAt?.let(::sessionDateTimeLabel)
                                ?: "Schedule details not set yet."

                        SessionsTab.Live ->
                            "Started ${session.actualStartedAt?.let(TimeUtils::clockTime) ?: "now"}"

                        SessionsTab.Past -> {
                            val endedAt = session.actualEndedAt
                                ?: session.scheduledStartAt
                                ?: session.createdAt
                            "Ended ${sessionDateTimeLabel(endedAt)}"
                        }
                    },
                    color = AacTextSecondary
                )
                session.scheduledDurationMinutes?.let { durationMinutes ->
                    Text(
                        text = "Duration $durationMinutes min",
                        color = AacTextSecondary
                    )
                }
            }

            when (tab) {
                SessionsTab.Upcoming -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompactActionButton(
                            text = "Start",
                            onClick = {
                                onStartSession(session)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        CompactActionButton(
                            text = "Edit",
                            onClick = {
                                onEditSession(session)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        CompactActionButton(
                            text = "Cancel",
                            onClick = {
                                onCancelSession(session)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                SessionsTab.Live -> {
                    PrimaryButton(
                        text = "Open",
                        onClick = {
                            onOpenSession(session)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SessionsTab.Past -> {
                    SecondaryButton(
                        text = "View summary",
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Not available yet.",
                        color = AacTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSessionScreen(
    initialSession: SessionEntity?,
    defaultSessionName: String,
    errorMessage: String?,
    onBack: () -> Unit,
    onSubmit: (
        sessionId: String?,
        sessionName: String,
        scheduledStartAt: Long,
        durationMinutes: Int,
        onFinished: (String?) -> Unit
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val now = remember {
        System.currentTimeMillis()
    }
    val defaultScheduledAt = remember(initialSession?.id) {
        initialSession?.scheduledStartAt ?: nextHalfHour(now)
    }
    val defaultDuration = remember(initialSession?.id) {
        initialSession?.scheduledDurationMinutes ?: 60
    }

    var sessionName by rememberSaveable(initialSession?.id) {
        mutableStateOf(initialSession?.name ?: defaultSessionName)
    }
    var scheduledAt by rememberSaveable(initialSession?.id) {
        mutableStateOf(defaultScheduledAt)
    }
    var durationMinutes by rememberSaveable(initialSession?.id) {
        mutableStateOf(defaultDuration)
    }
    var validationError by rememberSaveable(initialSession?.id) {
        mutableStateOf<String?>(null)
    }
    var isSaving by rememberSaveable {
        mutableStateOf(false)
    }
    var showDatePicker by rememberSaveable {
        mutableStateOf(false)
    }
    var showTimePicker by rememberSaveable {
        mutableStateOf(false)
    }
    var durationExpanded by rememberSaveable {
        mutableStateOf(false)
    }

    LaunchedEffect(initialSession?.id) {
        if (initialSession != null) {
            sessionName = initialSession.name
            scheduledAt = initialSession.scheduledStartAt ?: defaultScheduledAt
            durationMinutes = initialSession.scheduledDurationMinutes ?: defaultDuration
            validationError = null
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startOfDayMillis(scheduledAt)
        )
        DatePickerDialog(
            onDismissRequest = {
                showDatePicker = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedDateMillis =
                            datePickerState.selectedDateMillis
                                ?: startOfDayMillis(scheduledAt)
                        scheduledAt = combineDateAndTime(
                            dateMillis = selectedDateMillis,
                            hour = scheduledAt.hourOfDay(),
                            minute = scheduledAt.minuteOfHour()
                        )
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = scheduledAt.hourOfDay(),
            initialMinute = scheduledAt.minuteOfHour(),
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = {
                showTimePicker = false
            },
            title = {
                Text("Time")
            },
            text = {
                TimeInput(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scheduledAt = combineDateAndTime(
                            dateMillis = startOfDayMillis(scheduledAt),
                            hour = timePickerState.hour,
                            minute = timePickerState.minute
                        )
                        showTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTimePicker = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AacBackground)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Back",
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.clickable(onClick = onBack)
        )

        Text(
            text = "Schedule session",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.secondary
        )

        if (initialSession == null && errorMessage != null && errorMessage.contains("edit", ignoreCase = true)) {
            ErrorCard(message = errorMessage)
        }

        AppCard {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = sessionName,
                    onValueChange = {
                        sessionName = it
                        validationError = null
                    },
                    label = {
                        Text("Session name")
                    },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = TimeUtils.dateLabel(scheduledAt),
                    onValueChange = {},
                    readOnly = true,
                    enabled = !isSaving,
                    label = {
                        Text("Date")
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = false)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showDatePicker = true
                        }
                )

                OutlinedTextField(
                    value = TimeUtils.clockTime(scheduledAt),
                    onValueChange = {},
                    readOnly = true,
                    enabled = !isSaving,
                    label = {
                        Text("Time")
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = false)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showTimePicker = true
                        }
                )

                ExposedDropdownMenuBox(
                    expanded = durationExpanded,
                    onExpandedChange = { expanded ->
                        if (!isSaving) {
                            durationExpanded = expanded
                        }
                    }
                ) {
                    OutlinedTextField(
                        value = "$durationMinutes minutes",
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isSaving,
                        label = {
                            Text("Duration")
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = durationExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = durationExpanded,
                        onDismissRequest = {
                            durationExpanded = false
                        }
                    ) {
                        ScheduleDurationOptions.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text("$option minutes")
                                },
                                onClick = {
                                    durationMinutes = option
                                    durationExpanded = false
                                    validationError = null
                                }
                            )
                        }
                    }
                }

                (validationError ?: errorMessage)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { message ->
                        ErrorCard(message = message)
                    }

                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                PrimaryButton(
                    text = if (isSaving) {
                        "Scheduling…"
                    } else {
                        "Schedule session"
                    },
                    enabled = !isSaving,
                    onClick = {
                        val cleanSessionName = sessionName.trim()
                        val minimumStart = System.currentTimeMillis() - 60_000L
                        validationError = when {
                            cleanSessionName.isBlank() ->
                                "Enter a session name."

                            scheduledAt < minimumStart ->
                                "Choose a future date and time."

                            durationMinutes <= 0 ->
                                "Choose a duration."

                            else -> null
                        }

                        if (validationError == null) {
                            isSaving = true
                            onSubmit(
                                initialSession?.id,
                                cleanSessionName,
                                scheduledAt,
                                durationMinutes
                            ) { submitError ->
                                isSaving = false
                                validationError = submitError
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SessionSummaryRow(
    session: SessionEntity
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = session.name,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = session.scheduledStartAt?.let(::sessionDateTimeLabel)
                ?: "Schedule details not set yet.",
            color = AacTextSecondary
        )
    }
}

@Composable
private fun ToolsPlaceholderScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AacBackground)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Tools",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        AppCard {
            Text(
                text = "Session tools will be added here.",
                color = AacTextSecondary
            )
        }
    }
}

@Composable
private fun ErrorCard(
    message: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

private fun sessionDateTimeLabel(timestamp: Long): String {
    return "${TimeUtils.dateLabel(timestamp)} at ${TimeUtils.clockTime(timestamp)}"
}

private fun nextHalfHour(now: Long): Long {
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(now)
        .atZone(zone)
        .toLocalDateTime()
    val roundedMinute = if (dateTime.minute < 30) 30 else 0
    val roundedHour = if (dateTime.minute < 30) dateTime.hour else dateTime.hour + 1
    val adjusted = LocalDateTime.of(
        dateTime.toLocalDate(),
        LocalTime.of(roundedHour % 24, roundedMinute)
    ).let { candidate ->
        if (roundedHour >= 24) {
            candidate.plusDays(1)
        } else {
            candidate
        }
    }
    return adjusted.atZone(zone).toInstant().toEpochMilli()
}

private fun startOfDayMillis(timestamp: Long): Long {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(timestamp)
        .atZone(zone)
        .toLocalDate()
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
}

private fun combineDateAndTime(
    dateMillis: Long,
    hour: Int,
    minute: Int
): Long {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(dateMillis)
        .atZone(zone)
        .toLocalDate()
    return LocalDateTime.of(date, LocalTime.of(hour, minute))
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}

private fun Long.hourOfDay(): Int {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .hour
}

private fun Long.minuteOfHour(): Int {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .minute
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

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AdvancedHomeScreenPhonePreview() {
    AdvancedHomeScreen(
        upcomingSessions = emptyList(),
        managementError = null,
        onCreateNow = {},
        onScheduleSession = {},
        onJoinSession = {},
        onManageSessions = {}
    )
}

@Preview(showBackground = true, widthDp = 1024, heightDp = 800)
@Composable
private fun SessionsBrowserScreenPreview() {
    SessionsBrowserScreen(
        upcomingSessions = listOf(
            SessionEntity(
                id = "a",
                name = "Tomorrow Planning",
                joinCode = "1111-2222",
                hostUserId = "1",
                createdAt = 0,
                scheduledStartAt = System.currentTimeMillis() + 86_400_000L,
                scheduledDurationMinutes = 60
            )
        ),
        liveSessions = emptyList(),
        pastSessions = emptyList(),
        errorMessage = null,
        onScheduleSession = {},
        onStartSession = {},
        onEditSession = {},
        onCancelSession = {},
        onOpenSession = {}
    )
}
