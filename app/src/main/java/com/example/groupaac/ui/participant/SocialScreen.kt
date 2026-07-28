package com.example.groupaac.ui.participant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.model.UserRole
import com.example.groupaac.ui.common.AppCard
import com.example.groupaac.ui.common.AppWindowSize
import com.example.groupaac.ui.common.SecondaryButton
import com.example.groupaac.ui.common.UserAvatar
import com.example.groupaac.ui.common.rememberAppWindowSize
import com.example.groupaac.ui.theme.AacBackground
import com.example.groupaac.ui.theme.AacTextSecondary
import com.example.groupaac.ui.theme.GroupAacTheme

@Composable
fun SocialScreen(
    user: UserEntity?,
    modifier: Modifier = Modifier
) {
    when (rememberAppWindowSize()) {
        AppWindowSize.Phone -> SocialScreenPhone(
            user = user,
            modifier = modifier
        )

        AppWindowSize.Tablet,
        AppWindowSize.Desktop -> SocialScreenTablet(
            user = user,
            modifier = modifier
        )
    }
}

/**
 * Temporary compatibility overload for the obsolete ParticipantHomeScreen.
 * Remove it after that old host screen is deleted.
 */
@Composable
fun SocialScreen(
    uiState: ParticipantUiState,
    modifier: Modifier = Modifier
) {
    SocialScreen(
        user = uiState.user,
        modifier = modifier
    )
}

@Composable
private fun SocialScreenPhone(
    user: UserEntity?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AacBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SocialHeader(user)

        SocialListCard(
            title = "Groups",
            rows = listOf(
                "Aphasia group" to "8 members",
                "Family" to "4 members"
            )
        )

        SocialListCard(
            title = "Contacts",
            rows = listOf(
                "Dr. Lee" to "Facilitator",
                "Mary" to "Participant",
                "Bob" to "Participant"
            )
        )
    }
}

@Composable
private fun SocialScreenTablet(
    user: UserEntity?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AacBackground)
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        SocialHeader(user)

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                SocialListCard(
                    title = "Groups",
                    rows = listOf(
                        "Aphasia group" to "8 members",
                        "Family" to "4 members"
                    )
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                SocialListCard(
                    title = "Contacts",
                    rows = listOf(
                        "Dr. Lee" to "Facilitator",
                        "Mary" to "Participant",
                        "Bob" to "Participant"
                    )
                )
            }
        }
    }
}

@Composable
private fun SocialHeader(user: UserEntity?) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Groups",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.secondary
        )

        Text(
            text = if (user == null) {
                "My saved groups and contacts."
            } else {
                "${user.displayName}'s saved groups and contacts."
            },
            color = AacTextSecondary
        )
    }
}

@Composable
private fun SocialListCard(
    title: String,
    rows: List<Pair<String, String>>
) {
    AppCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )

            rows.forEach { (name, subtitle) ->
                SocialRow(
                    name = name,
                    subtitle = subtitle
                )
            }
        }
    }
}

@Composable
private fun SocialRow(
    name: String,
    subtitle: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        UserAvatar(name)

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = subtitle,
                color = AacTextSecondary
            )
        }

        SecondaryButton(
            text = "Open",
            onClick = {}
        )
    }
}

private fun previewUser() = UserEntity(
    id = "u1",
    displayName = "Alice",
    role = UserRole.PARTICIPANT,
    createdAt = 0
)

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun SocialScreenPhonePreview() {
    GroupAacTheme {
        SocialScreen(user = previewUser())
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Composable
private fun SocialScreenTabletPortraitPreview() {
    GroupAacTheme {
        SocialScreen(user = previewUser())
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
private fun SocialScreenTabletLandscapePreview() {
    GroupAacTheme {
        SocialScreen(user = previewUser())
    }
}
