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

interface BottomNavItem {
    val label: String

    @get:DrawableRes
    val iconRes: Int
}

sealed class ParticipantNavItem(
    override val label: String,
    @DrawableRes override val iconRes: Int
) : BottomNavItem {

    data object Share : ParticipantNavItem(
        label = "Share",
        iconRes = R.drawable.ic_nav_share
    )

    data object Signal : ParticipantNavItem(
        label = "Signal",
        iconRes = R.drawable.ic_nav_signal
    )

    data object Social : ParticipantNavItem(
        label = "Social",
        iconRes = R.drawable.ic_group
    )

    data object Settings : ParticipantNavItem(
        label = "Settings",
        iconRes = R.drawable.ic_nav_settings
    )

    data object Debug : ParticipantNavItem(
        label = "Debug",
        iconRes = R.drawable.ic_action_edit
    )
}

sealed class FacilitatorNavItem(
    override val label: String,
    @DrawableRes override val iconRes: Int
) : BottomNavItem {

    data object Participants : FacilitatorNavItem(
        label = "Participants",
        iconRes = R.drawable.ic_nav_participants
    )

    data object SessionLog : FacilitatorNavItem(
        label = "Session Log",
        iconRes = R.drawable.ic_nav_session_log
    )

    data object Summary : FacilitatorNavItem(
        label = "Summary",
        iconRes = R.drawable.ic_nav_summary
    )

    data object Settings : FacilitatorNavItem(
        label = "Settings",
        iconRes = R.drawable.ic_nav_settings
    )

    data object Debug : FacilitatorNavItem(
        label = "Debug",
        iconRes = R.drawable.ic_action_edit
    )
}

@Composable
fun <T : BottomNavItem> AppBottomNavBar(
    items: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.height(80.dp)
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = selected == item,
                onClick = { onSelected(item) },
                icon = {
                    Icon(
                        painter = painterResource(id = item.iconRes),
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(text = item.label)
                }
            )
        }
    }
}
