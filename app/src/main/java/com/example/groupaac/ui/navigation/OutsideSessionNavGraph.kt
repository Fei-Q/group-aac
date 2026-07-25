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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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
import com.example.groupaac.model.UserRole
import com.example.groupaac.ui.common.AdvancedOutsideNavItem
import com.example.groupaac.ui.common.AppBottomNavBar
import com.example.groupaac.ui.common.AppCard
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
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun OutsideSessionNavGraph(
    currentUser: UserEntity,
    homeExperience: HomeExperience,
    sessionUiState: SessionCoordinatorUiState,
    onCreateSession: (
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

    val profileViewModel: ProfileViewModel = viewModel(
        key = "outside-profile-${currentUser.id}",
        factory = ProfileViewModelFactory(
            userId = currentUser.id,
            accountRepository = container.accountRepository,
            settingsRepository = container.settingsRepository
        )
    )

    val profileUiState by profileViewModel.uiState
        .collectAsStateWithLifecycle()
    val sessions by container.sessionRepository.observeSessions()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val settings = profileUiState.settings ?: UserSettingsEntity(
        userId = currentUser.id
    )

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
                            navController.navigateToOutsideDestination(
                                item.route
                            )
                        }
                    )
                }

                HomeExperience.ADVANCED -> {
                    AppBottomNavBar(
                        items = advancedNavItems,
                        currentRoute = currentRoute ?: OutsideRoutes.Home,
                        onSelected = { item ->
                            navController.navigateToOutsideDestination(
                                item.route
                            )
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
                if (
                    sessionUiState.connectionState
                        is SessionConnectionState.AwaitingApproval
                ) {
                    val awaiting =
                        sessionUiState.connectionState
                            as SessionConnectionState.AwaitingApproval
                    AwaitingApprovalScreen(
                        sessionName = awaiting.sessionName,
                        onCancelRequest = onCancelFacilitatorRequest,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    when (homeExperience) {
                        HomeExperience.SIMPLE -> {
                            JoinSessionScreen(
                                currentUser = profileUiState.user ?: currentUser,
                                isJoining = sessionUiState.connectionState
                                        is SessionConnectionState.Joining,
                                errorMessage = sessionUiState.errorMessage,
                                onJoin = onJoinSession,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        HomeExperience.ADVANCED -> {
                            AdvancedHomeScreen(
                                settings = settings,
                                sessions = sessions,
                                onCreateNow = {
                                    onCreateSession(
                                        settings.defaultSessionName,
                                        (profileUiState.user ?: currentUser).displayName
                                    )
                                },
                                onJoinSession = {
                                    navController.navigate(OutsideRoutes.Join)
                                },
                                onManageSessions = {
                                    navController.navigateToOutsideDestination(
                                        OutsideRoutes.Sessions
                                    )
                                },
                                onScheduleSession = {
                                    navController.navigateToOutsideDestination(
                                        OutsideRoutes.Sessions
                                    )
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            composable(OutsideRoutes.Join) {
                if (
                    sessionUiState.connectionState
                        is SessionConnectionState.AwaitingApproval
                ) {
                    val awaiting =
                        sessionUiState.connectionState
                            as SessionConnectionState.AwaitingApproval
                    AwaitingApprovalScreen(
                        sessionName = awaiting.sessionName,
                        onCancelRequest = onCancelFacilitatorRequest,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    JoinSessionScreen(
                        currentUser = profileUiState.user ?: currentUser,
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
                    sessions = sessions,
                    modifier = Modifier.fillMaxSize()
                )
            }

            composable(OutsideRoutes.Groups) {
                SocialScreen(
                    user = profileUiState.user ?: currentUser,
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
                                user = profileUiState.user ?: currentUser,
                                settings = settings,
                                onUpdateSettings =
                                    profileViewModel::updateSettings,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        HomeExperience.ADVANCED -> {
                            FacilitatorSettingsScreen(
                                settings = settings,
                                onSettingsChange =
                                    profileViewModel::updateSettings,
                                onClearLocalHistory =
                                    onClearLocalHistory,
                                onExportSummary =
                                    onExportSummary,
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
    settings: UserSettingsEntity,
    sessions: List<SessionEntity>,
    onCreateNow: () -> Unit,
    onScheduleSession: () -> Unit,
    onJoinSession: () -> Unit,
    onManageSessions: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by rememberSaveable {
        mutableStateOf(false)
    }
    val upcomingSessions = remember(sessions) {
        sessions
            .filter { it.isUpcomingTodayOrLater() }
            .sortedBy { it.scheduledStartAt ?: Long.MAX_VALUE }
            .take(3)
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

            if (isTablet) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HomeActionCard(
                        title = "Create Session",
                        subtitle = "Start a new session right away.",
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
                            subtitle = "Start a new session right away.",
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
        modifier = modifier
            .clickable(onClick = onClick)
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
    sessions: List<SessionEntity>,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable {
        mutableStateOf(SessionsTab.Upcoming)
    }
    val visibleSessions = remember(sessions, selectedTab) {
        when (selectedTab) {
            SessionsTab.Upcoming ->
                sessions.filter { it.isUpcomingTodayOrLater() }
                    .sortedBy { it.scheduledStartAt ?: Long.MAX_VALUE }
            SessionsTab.Live ->
                sessions.filter { it.isLive() }
                    .sortedByDescending { it.actualStartedAt ?: it.createdAt }
            SessionsTab.Past ->
                sessions.filter { it.isPast() }
                    .sortedByDescending {
                        it.actualEndedAt
                            ?: it.scheduledStartAt
                            ?: it.createdAt
                    }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AacBackground)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Sessions",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.secondary
        )

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
                                SessionsTab.Upcoming ->
                                    "No upcoming sessions."
                                SessionsTab.Live ->
                                    "No live sessions."
                                SessionsTab.Past ->
                                    "No past sessions."
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
                        tab = selectedTab
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionListCard(
    session: SessionEntity,
    tab: SessionsTab
) {
    AppCard {
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

private fun SessionEntity.isLive(): Boolean =
    actualStartedAt != null && actualEndedAt == null

private fun SessionEntity.isPast(now: Long = System.currentTimeMillis()): Boolean =
    actualEndedAt != null ||
        (!isLive() &&
            scheduledStartAt != null &&
            scheduledStartAt < startOfTodayMillis() &&
            scheduledStartAt < now)

private fun SessionEntity.isUpcomingTodayOrLater(): Boolean =
    actualStartedAt == null &&
        actualEndedAt == null &&
        scheduledStartAt != null &&
        scheduledStartAt >= startOfTodayMillis()

private fun startOfTodayMillis(): Long {
    val zone = ZoneId.systemDefault()
    return LocalDate.now(zone)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
}

private fun sessionDateTimeLabel(timestamp: Long): String {
    return "${TimeUtils.dateLabel(timestamp)} at ${TimeUtils.clockTime(timestamp)}"
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
        settings = UserSettingsEntity(
            userId = "1",
            homeExperience = HomeExperience.ADVANCED
        ),
        sessions = emptyList(),
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
        sessions = listOf(
            SessionEntity(
                id = "a",
                name = "Tomorrow Planning",
                joinCode = "1111-2222",
                hostUserId = "1",
                createdAt = 0,
                scheduledStartAt = System.currentTimeMillis() + 86_400_000L
            )
        )
    )
}
