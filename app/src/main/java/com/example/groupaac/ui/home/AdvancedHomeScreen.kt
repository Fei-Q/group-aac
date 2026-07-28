package com.example.groupaac.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.groupaac.R
import com.example.groupaac.data.entity.SessionEntity
import com.example.groupaac.model.CalendarViewMode
import com.example.groupaac.ui.common.AppCard
import com.example.groupaac.ui.common.PrimaryButton
import com.example.groupaac.ui.common.SecondaryButton
import com.example.groupaac.ui.theme.AacBackground
import com.example.groupaac.ui.theme.AacBlue
import com.example.groupaac.ui.theme.AacBlueLight
import com.example.groupaac.ui.theme.AacBorder
import com.example.groupaac.ui.theme.AacGreen
import com.example.groupaac.ui.theme.AacTextSecondary
import com.example.groupaac.ui.theme.AacYellow
import com.example.groupaac.util.TimeUtils
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val LocalDateSaver = Saver<LocalDate, String>(
    save = { it.toString() },
    restore = { LocalDate.parse(it) }
)

data class HomeSessionItem(
    val session: SessionEntity,
    val occursAt: Long,
    val isLive: Boolean
)

@Composable
fun AdvancedHomeScreen(
    liveSessions: List<SessionEntity>,
    upcomingSessions: List<SessionEntity>,
    calendarViewMode: CalendarViewMode,
    managementError: String?,
    onCreateSession: () -> Unit,
    onJoinSession: () -> Unit,
    onManageSessions: () -> Unit,
    onOpenLiveSession: (SessionEntity) -> Unit,
    onStartScheduledSession: (SessionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now(ZoneId.systemDefault()) }
    var showCreateDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var selectedDate by rememberSaveable(stateSaver = LocalDateSaver) {
        mutableStateOf(today)
    }
    var displayedAnchorDate by rememberSaveable(stateSaver = LocalDateSaver) {
        mutableStateOf(today)
    }

    LaunchedEffect(calendarViewMode) {
        displayedAnchorDate = selectedDate
    }

    val homeSessions = remember(liveSessions, upcomingSessions) {
        buildHomeSessionItems(
            liveSessions = liveSessions,
            upcomingSessions = upcomingSessions
        )
    }
    val nextHeroSession = remember(homeSessions) {
        homeSessions.firstOrNull { it.isLive }
            ?: homeSessions
                .filterNot { it.isLive }
                .minByOrNull { it.occursAt }
    }
    val sessionCountsByDate = remember(homeSessions) {
        homeSessions.groupingBy { it.localDate() }.eachCount()
    }
    val sessionsByDate = remember(homeSessions) {
        homeSessions.groupBy { it.localDate() }
    }
    val selectedDateSessions = remember(sessionsByDate, selectedDate) {
        sessionsByDate[selectedDate]
            .orEmpty()
            .sortedWith(
                compareBy<HomeSessionItem> { !it.isLive }
                    .thenBy { it.occursAt }
                    .thenBy { it.session.name }
            )
    }

    if (showCreateDialog) {
        CreateSessionChoiceDialog(
            onCreateNow = {
                showCreateDialog = false
                onCreateSession()
            },
            onSchedule = {
                showCreateDialog = false
                onManageSessions()
            },
            onDismiss = {
                showCreateDialog = false
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .background(AacBackground)
            .fillMaxWidth()
            .testTag("advanced_home_list"),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 1080.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Home",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Your sessions at a glance",
                    color = AacTextSecondary
                )
            }
        }

        if (!managementError.isNullOrBlank()) {
            item {
                HomeErrorCard(
                    message = managementError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 1080.dp)
                )
            }
        }

        item {
            NextSessionHeroCard(
                heroSession = nextHeroSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 1080.dp),
                onCreateSession = {
                    showCreateDialog = true
                },
                onManageSessions = onManageSessions,
                onOpenLiveSession = onOpenLiveSession,
                onStartScheduledSession = onStartScheduledSession
            )
        }

        item {
            QuickActionsSection(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 1080.dp),
                onCreateSession = {
                    showCreateDialog = true
                },
                onJoinSession = onJoinSession,
                onManageSessions = onManageSessions
            )
        }

        item {
            CalendarSection(
                sessionCountsByDate = sessionCountsByDate,
                selectedDate = selectedDate,
                displayedAnchorDate = displayedAnchorDate,
                calendarViewMode = calendarViewMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 1080.dp),
                onSelectedDateChange = {
                    selectedDate = it
                },
                onDisplayedAnchorChange = {
                    displayedAnchorDate = it
                }
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 1080.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Sessions on ${fullDateLabel(selectedDate)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (selectedDateSessions.isEmpty()) {
                    AppCard {
                        Text(
                            text = "No sessions on this day.",
                            color = AacTextSecondary
                        )
                    }
                }
            }
        }

        items(
            items = selectedDateSessions,
            key = { "${it.session.id}-${it.occursAt}-${it.isLive}" }
        ) { item ->
            SelectedDateSessionCard(
                sessionItem = item,
                today = today,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 1080.dp)
                    .testTag("home_selected_session_${item.session.id}"),
                onOpenLiveSession = onOpenLiveSession,
                onStartScheduledSession = onStartScheduledSession,
                onManageSessions = onManageSessions
            )
        }
    }
}

private fun buildHomeSessionItems(
    liveSessions: List<SessionEntity>,
    upcomingSessions: List<SessionEntity>
): List<HomeSessionItem> {
    val liveItems = liveSessions
        .filter { it.actualStartedAt != null && it.actualEndedAt == null }
        .mapNotNull { session ->
            session.actualStartedAt?.let { startedAt ->
                HomeSessionItem(
                    session = session,
                    occursAt = startedAt,
                    isLive = true
                )
            }
        }
    val liveIds = liveItems.map { it.session.id }.toSet()
    val upcomingItems = upcomingSessions
        .filter { it.id !in liveIds }
        .mapNotNull { session ->
            session.scheduledStartAt?.let { scheduledStartAt ->
                HomeSessionItem(
                    session = session,
                    occursAt = scheduledStartAt,
                    isLive = false
                )
            }
        }
        .filter { item ->
            item.session.actualEndedAt == null &&
                !item.localDate().isBefore(LocalDate.now(ZoneId.systemDefault()))
        }

    return (liveItems + upcomingItems)
        .sortedWith(
            compareBy<HomeSessionItem> { !it.isLive }
                .thenBy { it.occursAt }
                .thenBy { it.session.name }
        )
}

@Composable
private fun NextSessionHeroCard(
    heroSession: HomeSessionItem?,
    onCreateSession: () -> Unit,
    onManageSessions: () -> Unit,
    onOpenLiveSession: (SessionEntity) -> Unit,
    onStartScheduledSession: (SessionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 5.dp,
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = AacBlueLight.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(24.dp)
        ) {
            if (heroSession == null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "No upcoming sessions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Create or schedule a session to get started.",
                        color = AacTextSecondary
                    )
                    PrimaryButton(
                        text = "Create Session",
                        onClick = onCreateSession,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                val session = heroSession.session
                val sessionDate = heroSession.localDate()
                val canStartScheduledSession =
                    !heroSession.isLive && !sessionDate.isAfter(
                        LocalDate.now(ZoneId.systemDefault())
                    )
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HeroStatusLabel(heroSession = heroSession)

                    if (heroSession.isLive) {
                        Text(
                            text = session.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("home_hero_title")
                        )
                        Text(
                            text = "${timeLabel(heroSession.occursAt)} • ${durationLabel(session.scheduledDurationMinutes)}",
                            color = AacTextSecondary
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DateBadge(date = sessionDate)
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = session.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("home_hero_title")
                                )
                                Text(
                                    text = "${sessionDateTimeLabel(heroSession.occursAt)} • ${durationLabel(session.scheduledDurationMinutes)}",
                                    color = AacTextSecondary
                                )
                            }
                        }
                    }

                    SelectionContainer {
                        Text(
                            text = "Code ${session.joinCode}",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    PrimaryButton(
                        text = when {
                            heroSession.isLive -> "Open session"
                            canStartScheduledSession -> "Start session"
                            else -> "View in Sessions"
                        },
                        onClick = {
                            when {
                                heroSession.isLive ->
                                    onOpenLiveSession(session)

                                canStartScheduledSession ->
                                    onStartScheduledSession(session)

                                else -> onManageSessions()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStatusLabel(
    heroSession: HomeSessionItem
) {
    val backgroundColor = if (heroSession.isLive) {
        AacGreen.copy(alpha = 0.18f)
    } else {
        AacYellow.copy(alpha = 0.18f)
    }
    val contentColor = if (heroSession.isLive) {
        AacGreen
    } else {
        MaterialTheme.colorScheme.secondary
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = backgroundColor
    ) {
        Text(
            text = if (heroSession.isLive) {
                "Live now"
            } else {
                "Next session"
            },
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 7.dp)
                .testTag("home_hero_label")
        )
    }
}

@Composable
private fun DateBadge(
    date: LocalDate
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = date.month.name.take(3),
                style = MaterialTheme.typography.labelMedium,
                color = AacTextSecondary
            )
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun QuickActionsSection(
    onCreateSession: () -> Unit,
    onJoinSession: () -> Unit,
    onManageSessions: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val useSingleRow = maxWidth >= 520.dp
        if (useSingleRow) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                QuickActionCard(
                    title = "Create Session",
                    iconRes = R.drawable.ic_role_facilitator,
                    backgroundTint = AacBlueLight,
                    onClick = onCreateSession,
                    modifier = Modifier.weight(1f)
                )
                QuickActionCard(
                    title = "Join Session",
                    iconRes = R.drawable.ic_enter_code,
                    backgroundTint = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = onJoinSession,
                    modifier = Modifier.weight(1f)
                )
                QuickActionCard(
                    title = "Manage Sessions",
                    iconRes = R.drawable.ic_nav_session_log,
                    backgroundTint = AacYellow.copy(alpha = 0.18f),
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
                    QuickActionCard(
                        title = "Create Session",
                        iconRes = R.drawable.ic_role_facilitator,
                        backgroundTint = AacBlueLight,
                        onClick = onCreateSession,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        title = "Join Session",
                        iconRes = R.drawable.ic_enter_code,
                        backgroundTint = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = onJoinSession,
                        modifier = Modifier.weight(1f)
                    )
                }
                QuickActionCard(
                    title = "Manage Sessions",
                    iconRes = R.drawable.ic_nav_session_log,
                    backgroundTint = AacYellow.copy(alpha = 0.18f),
                    onClick = onManageSessions,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    iconRes: Int,
    backgroundTint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = backgroundTint,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CalendarSection(
    sessionCountsByDate: Map<LocalDate, Int>,
    selectedDate: LocalDate,
    displayedAnchorDate: LocalDate,
    calendarViewMode: CalendarViewMode,
    onSelectedDateChange: (LocalDate) -> Unit,
    onDisplayedAnchorChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Calendar",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CalendarNavButton(
                        contentDescription = "Previous ${calendarViewMode.name.lowercase()}",
                        label = "‹",
                        onClick = {
                            onDisplayedAnchorChange(
                                when (calendarViewMode) {
                                    CalendarViewMode.WEEK ->
                                        displayedAnchorDate.minusWeeks(1)

                                    CalendarViewMode.MONTH ->
                                        displayedAnchorDate.minusMonths(1)
                                }
                            )
                        }
                    )
                    Text(
                        text = displayedPeriodLabel(
                            displayedAnchorDate = displayedAnchorDate,
                            mode = calendarViewMode
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    CalendarNavButton(
                        contentDescription = "Next ${calendarViewMode.name.lowercase()}",
                        label = "›",
                        onClick = {
                            onDisplayedAnchorChange(
                                when (calendarViewMode) {
                                    CalendarViewMode.WEEK ->
                                        displayedAnchorDate.plusWeeks(1)

                                    CalendarViewMode.MONTH ->
                                        displayedAnchorDate.plusMonths(1)
                                }
                            )
                        }
                    )
                }
            }

            when (calendarViewMode) {
                CalendarViewMode.WEEK -> {
                    WeekCalendar(
                        sessionCountsByDate = sessionCountsByDate,
                        selectedDate = selectedDate,
                        displayedAnchorDate = displayedAnchorDate,
                        onSelectedDateChange = onSelectedDateChange,
                        onDisplayedAnchorChange = onDisplayedAnchorChange
                    )
                }

                CalendarViewMode.MONTH -> {
                    MonthCalendar(
                        sessionCountsByDate = sessionCountsByDate,
                        selectedDate = selectedDate,
                        displayedAnchorDate = displayedAnchorDate,
                        onSelectedDateChange = onSelectedDateChange,
                        onDisplayedAnchorChange = onDisplayedAnchorChange
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarNavButton(
    contentDescription: String,
    label: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .semantics {
                this.contentDescription = contentDescription
            }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

@Composable
private fun WeekCalendar(
    sessionCountsByDate: Map<LocalDate, Int>,
    selectedDate: LocalDate,
    displayedAnchorDate: LocalDate,
    onSelectedDateChange: (LocalDate) -> Unit,
    onDisplayedAnchorChange: (LocalDate) -> Unit
) {
    val startOfWeek = remember(displayedAnchorDate) {
        displayedAnchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    }
    val days = remember(startOfWeek) {
        List(7) { offset ->
            startOfWeek.plusDays(offset.toLong())
        }
    }
    BoxWithConstraints {
        val minimumCellWidth = 56.dp
        val requiresScroll = (maxWidth / 7) < minimumCellWidth
        val rowModifier = if (requiresScroll) {
            Modifier.horizontalScroll(rememberScrollState())
        } else {
            Modifier.fillMaxWidth()
        }

        Row(
            modifier = rowModifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            days.forEach { day ->
                CalendarDayCell(
                    date = day,
                    sessionCount = sessionCountsByDate[day] ?: 0,
                    selected = day == selectedDate,
                    isToday = day == LocalDate.now(ZoneId.systemDefault()),
                    modifier = if (requiresScroll) {
                        Modifier.width(minimumCellWidth)
                    } else {
                        Modifier.weight(1f)
                    },
                    onClick = {
                        onSelectedDateChange(day)
                        onDisplayedAnchorChange(day)
                    }
                )
            }
        }
    }
}

@Composable
private fun MonthCalendar(
    sessionCountsByDate: Map<LocalDate, Int>,
    selectedDate: LocalDate,
    displayedAnchorDate: LocalDate,
    onSelectedDateChange: (LocalDate) -> Unit,
    onDisplayedAnchorChange: (LocalDate) -> Unit
) {
    val monthStart = remember(displayedAnchorDate) {
        displayedAnchorDate.withDayOfMonth(1)
    }
    val firstGridDate = remember(monthStart) {
        monthStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    }
    val gridDates = remember(firstGridDate) {
        List(42) { index ->
            firstGridDate.plusDays(index.toLong())
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WeekdayHeader()
        gridDates.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                week.forEach { day ->
                    MonthDateCell(
                        date = day,
                        inDisplayedMonth = day.month == monthStart.month,
                        sessionCount = sessionCountsByDate[day] ?: 0,
                        selected = day == selectedDate,
                        isToday = day == LocalDate.now(ZoneId.systemDefault()),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSelectedDateChange(day)
                            onDisplayedAnchorChange(day)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val today = LocalDate.now(ZoneId.systemDefault())
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val labels = remember(today) {
        List(7) { offset ->
            today.plusDays(offset.toLong()).format(
                DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = AacTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    sessionCount: Int,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateDescription = accessibleDateDescription(date, sessionCount)
    Surface(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = dateDescription
            },
        shape = RoundedCornerShape(18.dp),
        color = when {
            selected -> AacBlue.copy(alpha = 0.14f)
            isToday -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (selected) 2.dp else 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) AacBlue else AacBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())),
                style = MaterialTheme.typography.labelMedium,
                color = AacTextSecondary
            )
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (sessionCount > 0) {
                StatusCountBadge(count = sessionCount)
            }
        }
    }
}

@Composable
private fun MonthDateCell(
    date: LocalDate,
    inDisplayedMonth: Boolean,
    sessionCount: Int,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 58.dp)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = accessibleDateDescription(date, sessionCount)
            }
            .testTag("month_date_${date}"),
        shape = RoundedCornerShape(16.dp),
        color = when {
            selected -> AacBlue.copy(alpha = 0.14f)
            isToday -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface
        },
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) AacBlue else AacBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (inDisplayedMonth) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    AacTextSecondary
                },
                fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal
            )
            if (sessionCount > 0) {
                StatusCountBadge(count = sessionCount)
            }
        }
    }
}

@Composable
private fun StatusCountBadge(
    count: Int
) {
    Surface(
        shape = CircleShape,
        color = AacGreen.copy(alpha = 0.16f)
    ) {
        Text(
            text = count.toString(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = AacGreen,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SelectedDateSessionCard(
    sessionItem: HomeSessionItem,
    today: LocalDate,
    onOpenLiveSession: (SessionEntity) -> Unit,
    onStartScheduledSession: (SessionEntity) -> Unit,
    onManageSessions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val session = sessionItem.session
    val statusLabel = if (sessionItem.isLive) "Live" else "Upcoming"
    val canStart = !sessionItem.isLive && !sessionItem.localDate().isAfter(today)
    AppCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = timeLabel(sessionItem.occursAt),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${durationLabel(session.scheduledDurationMinutes)} • $statusLabel",
                    color = AacTextSecondary
                )
            }

            SecondaryButton(
                text = when {
                    sessionItem.isLive -> "Open"
                    canStart -> "Start"
                    else -> "View"
                },
                onClick = {
                    when {
                        sessionItem.isLive -> onOpenLiveSession(session)
                        canStart -> onStartScheduledSession(session)
                        else -> onManageSessions()
                    }
                },
                modifier = Modifier.widthIn(min = 96.dp)
            )
        }
    }
}

@Composable
private fun HomeErrorCard(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
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

@Composable
private fun CreateSessionChoiceDialog(
    onCreateNow: () -> Unit,
    onSchedule: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
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

private fun displayedPeriodLabel(
    displayedAnchorDate: LocalDate,
    mode: CalendarViewMode
): String {
    return when (mode) {
        CalendarViewMode.WEEK -> {
            val start = displayedAnchorDate.with(
                TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)
            )
            val end = start.plusDays(6)
            "${shortDateLabel(start)} - ${shortDateLabel(end)}"
        }

        CalendarViewMode.MONTH ->
            displayedAnchorDate.format(
                DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
            )
    }
}

private fun accessibleDateDescription(
    date: LocalDate,
    sessionCount: Int
): String {
    val fullDate = fullDateLabel(date)
    return if (sessionCount > 0) {
        "$fullDate, $sessionCount sessions"
    } else {
        fullDate
    }
}

private fun shortDateLabel(date: LocalDate): String =
    date.format(
        DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    )

private fun fullDateLabel(date: LocalDate): String =
    date.format(
        DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
    )

private fun sessionDateTimeLabel(timestamp: Long): String =
    "${TimeUtils.dateLabel(timestamp)} at ${TimeUtils.clockTime(timestamp)}"

private fun timeLabel(timestamp: Long): String {
    val formatter = DateTimeFormatter
        .ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(formatter)
}

private fun durationLabel(durationMinutes: Int?): String =
    "${durationMinutes ?: 60} min"

private fun HomeSessionItem.localDate(): LocalDate =
    Instant.ofEpochMilli(occursAt)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()

@Preview(showBackground = true, widthDp = 390, heightDp = 960)
@Composable
private fun AdvancedHomeScreenWeekPreview() {
    AdvancedHomeScreen(
        liveSessions = emptyList(),
        upcomingSessions = listOf(
            SessionEntity(
                id = "session-1",
                name = "Monday Planning",
                joinCode = "1234-5678",
                hostUserId = "host-1",
                createdAt = 0L,
                scheduledStartAt = LocalDateTime.of(
                    2026,
                    7,
                    27,
                    15,
                    0
                ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                scheduledDurationMinutes = 60
            )
        ),
        calendarViewMode = CalendarViewMode.WEEK,
        managementError = null,
        onCreateSession = {},
        onJoinSession = {},
        onManageSessions = {},
        onOpenLiveSession = {},
        onStartScheduledSession = {}
    )
}

@Preview(showBackground = true, widthDp = 1024, heightDp = 900)
@Composable
private fun AdvancedHomeScreenMonthPreview() {
    AdvancedHomeScreen(
        liveSessions = listOf(
            SessionEntity(
                id = "session-live",
                name = "Live Check-in",
                joinCode = "9876-5432",
                hostUserId = "host-1",
                createdAt = 0L,
                scheduledStartAt = LocalDateTime.of(
                    2026,
                    7,
                    25,
                    13,
                    0
                ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                scheduledDurationMinutes = 45,
                actualStartedAt = LocalDateTime.of(
                    2026,
                    7,
                    25,
                    13,
                    0
                ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        ),
        upcomingSessions = emptyList(),
        calendarViewMode = CalendarViewMode.MONTH,
        managementError = null,
        onCreateSession = {},
        onJoinSession = {},
        onManageSessions = {},
        onOpenLiveSession = {},
        onStartScheduledSession = {}
    )
}
