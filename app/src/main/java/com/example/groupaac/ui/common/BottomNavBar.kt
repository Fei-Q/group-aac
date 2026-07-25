package com.example.groupaac.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.groupaac.R
import com.example.groupaac.ui.navigation.FacilitatorInsideRoutes
import com.example.groupaac.ui.navigation.FacilitatorOutsideRoutes
import com.example.groupaac.ui.navigation.ParticipantInsideRoutes
import com.example.groupaac.ui.navigation.ParticipantOutsideRoutes

interface BottomNavItem {
    val route: String
    val label: String

    @get:DrawableRes
    val iconRes: Int
}

sealed class ParticipantOutsideNavItem(
    override val route: String,
    override val label: String,
    @DrawableRes override val iconRes: Int
) : BottomNavItem {
    data object Join : ParticipantOutsideNavItem(
        route = ParticipantOutsideRoutes.Join,
        label = "Join",
        iconRes = R.drawable.ic_enter_code
    )

    data object Social : ParticipantOutsideNavItem(
        route = ParticipantOutsideRoutes.Social,
        label = "Social",
        iconRes = R.drawable.ic_group
    )

    data object Settings : ParticipantOutsideNavItem(
        route = ParticipantOutsideRoutes.Settings,
        label = "Settings",
        iconRes = R.drawable.ic_nav_settings
    )

    data object Debug : ParticipantOutsideNavItem(
        route = ParticipantOutsideRoutes.Debug,
        label = "Debug",
        iconRes = R.drawable.ic_action_edit
    )
}

sealed class ParticipantInsideNavItem(
    override val route: String,
    override val label: String,
    @DrawableRes override val iconRes: Int
) : BottomNavItem {
    data object Share : ParticipantInsideNavItem(
        route = ParticipantInsideRoutes.Share,
        label = "Share",
        iconRes = R.drawable.ic_nav_share
    )

    data object Signal : ParticipantInsideNavItem(
        route = ParticipantInsideRoutes.Signal,
        label = "Signal",
        iconRes = R.drawable.ic_nav_signal
    )

    data object Debug : ParticipantInsideNavItem(
        route = ParticipantInsideRoutes.Debug,
        label = "Debug",
        iconRes = R.drawable.ic_action_edit
    )
}

sealed class FacilitatorOutsideNavItem(
    override val route: String,
    override val label: String,
    @DrawableRes override val iconRes: Int
) : BottomNavItem {
    data object Sessions : FacilitatorOutsideNavItem(
        route = FacilitatorOutsideRoutes.Sessions,
        label = "Sessions",
        iconRes = R.drawable.ic_nav_session_log
    )

    data object Settings : FacilitatorOutsideNavItem(
        route = FacilitatorOutsideRoutes.Settings,
        label = "Settings",
        iconRes = R.drawable.ic_nav_settings
    )

    data object Debug : FacilitatorOutsideNavItem(
        route = FacilitatorOutsideRoutes.Debug,
        label = "Debug",
        iconRes = R.drawable.ic_action_edit
    )
}

sealed class FacilitatorInsideNavItem(
    override val route: String,
    override val label: String,
    @DrawableRes override val iconRes: Int
) : BottomNavItem {
    data object Participants : FacilitatorInsideNavItem(
        route = FacilitatorInsideRoutes.Participants,
        label = "Participants",
        iconRes = R.drawable.ic_nav_participants
    )

    data object SessionLog : FacilitatorInsideNavItem(
        route = FacilitatorInsideRoutes.SessionLog,
        label = "Session Log",
        iconRes = R.drawable.ic_nav_session_log
    )

    data object Summary : FacilitatorInsideNavItem(
        route = FacilitatorInsideRoutes.Summary,
        label = "Summary",
        iconRes = R.drawable.ic_nav_summary
    )

    data object Debug : FacilitatorInsideNavItem(
        route = FacilitatorInsideRoutes.Debug,
        label = "Debug",
        iconRes = R.drawable.ic_action_edit
    )
}

/*
 * Legacy item types are retained temporarily so the old
 * ParticipantHomeScreen and FacilitatorHomeScreen files still compile.
 * They can be deleted after those obsolete host screens are removed.
 */
sealed class ParticipantNavItem(
    override val route: String,
    override val label: String,
    @DrawableRes override val iconRes: Int
) : BottomNavItem {
    data object Share : ParticipantNavItem(
        "legacy/participant/share",
        "Share",
        R.drawable.ic_nav_share
    )

    data object Signal : ParticipantNavItem(
        "legacy/participant/signal",
        "Signal",
        R.drawable.ic_nav_signal
    )

    data object Social : ParticipantNavItem(
        "legacy/participant/social",
        "Social",
        R.drawable.ic_group
    )

    data object Settings : ParticipantNavItem(
        "legacy/participant/settings",
        "Settings",
        R.drawable.ic_nav_settings
    )

    data object Debug : ParticipantNavItem(
        "legacy/participant/debug",
        "Debug",
        R.drawable.ic_action_edit
    )
}

sealed class FacilitatorNavItem(
    override val route: String,
    override val label: String,
    @DrawableRes override val iconRes: Int
) : BottomNavItem {
    data object Participants : FacilitatorNavItem(
        "legacy/facilitator/participants",
        "Participants",
        R.drawable.ic_nav_participants
    )

    data object SessionLog : FacilitatorNavItem(
        "legacy/facilitator/session_log",
        "Session Log",
        R.drawable.ic_nav_session_log
    )

    data object Summary : FacilitatorNavItem(
        "legacy/facilitator/summary",
        "Summary",
        R.drawable.ic_nav_summary
    )

    data object Settings : FacilitatorNavItem(
        "legacy/facilitator/settings",
        "Settings",
        R.drawable.ic_nav_settings
    )

    data object Debug : FacilitatorNavItem(
        "legacy/facilitator/debug",
        "Debug",
        R.drawable.ic_action_edit
    )
}

@Composable
fun <T : BottomNavItem> AppBottomNavBar(
    items: List<T>,
    currentRoute: String?,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.height(80.dp)
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    onSelected(item)
                },
                icon = {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(item.label)
                }
            )
        }
    }
}

@Composable
fun <T : BottomNavItem> AppBottomNavBar(
    items: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    AppBottomNavBar(
        items = items,
        currentRoute = selected.route,
        onSelected = onSelected,
        modifier = modifier
    )
}
