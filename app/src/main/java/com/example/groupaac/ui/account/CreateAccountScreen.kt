package com.example.groupaac.ui.account

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.groupaac.data.account.CreateAccountResult
import com.example.groupaac.model.HomeExperience
import com.example.groupaac.ui.common.AppCard
import com.example.groupaac.ui.common.PrimaryButton
import com.example.groupaac.ui.common.roleIconRes
import com.example.groupaac.ui.theme.AacBackground
import com.example.groupaac.ui.theme.AacBlue
import com.example.groupaac.ui.theme.AacBorder
import com.example.groupaac.ui.theme.AacTextSecondary
import com.example.groupaac.ui.theme.GroupAacTheme
import com.example.groupaac.model.UserRole

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun CreateAccountScreen(
    onBack: () -> Unit,
    onCreate: (String, HomeExperience) -> Unit,
    createAccountResult: CreateAccountResult? = null,
    onConsumeCreateAccountResult: () -> Unit = {}
) {
    var uid by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var homeExperience by remember {
        mutableStateOf(HomeExperience.SIMPLE)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(AacBackground).padding(24.dp)) {
        val isTablet = maxWidth >= 700.dp
        val stackChoices = maxWidth < 520.dp
        Column(
            modifier = Modifier.fillMaxSize().then(if (isTablet) Modifier.padding(horizontal = 96.dp, vertical = 36.dp) else Modifier),
            verticalArrangement = if (isTablet) Arrangement.Center else Arrangement.Top
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            Text("Create account", style = if (isTablet) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.secondary)
//            Text("This creates a local profile for the prototype.", style = MaterialTheme.typography.bodyLarge, color = AacTextSecondary)
            Spacer(Modifier.height(24.dp))
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    Text("Account", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = uid,
                        onValueChange = { input ->
                            uid = input.lowercase().filter { char ->
                                char.isLowerCase() || char.isDigit() || char == '_'
                            }
                        },
                        label = { Text("UID") },
                        supportingText = {
                            Text("3-24 chars, lowercase letters, digits, underscore.")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    when (val result = createAccountResult) {
                        CreateAccountResult.AlreadyTaken -> Text(
                            text = "That UID is already taken on this device.",
                            color = MaterialTheme.colorScheme.error
                        )
                        is CreateAccountResult.Failure -> Text(
                            text = result.message,
                            color = MaterialTheme.colorScheme.error
                        )
                        is CreateAccountResult.Invalid -> Text(
                            text = result.message,
                            color = MaterialTheme.colorScheme.error
                        )
                        is CreateAccountResult.Success -> onConsumeCreateAccountResult()
                        null -> Unit
                    }
                    Text(
                        "How will you use this app?",
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (stackChoices) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            HomeExperienceCard(
                                title = "Join sessions",
                                description = "Join and take part.",
                                iconRole = UserRole.PARTICIPANT,
                                selected = homeExperience == HomeExperience.SIMPLE,
                                onClick = {
                                    homeExperience = HomeExperience.SIMPLE
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            HomeExperienceCard(
                                title = "Manage sessions",
                                description = "Create and organize sessions.",
                                iconRole = UserRole.FACILITATOR,
                                selected = homeExperience == HomeExperience.ADVANCED,
                                onClick = {
                                    homeExperience = HomeExperience.ADVANCED
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            HomeExperienceCard(
                                title = "Join sessions",
                                description = "Join and take part.",
                                iconRole = UserRole.PARTICIPANT,
                                selected = homeExperience == HomeExperience.SIMPLE,
                                onClick = {
                                    homeExperience = HomeExperience.SIMPLE
                                },
                                modifier = Modifier.weight(1f)
                            )
                            HomeExperienceCard(
                                title = "Manage sessions",
                                description = "Create and organize sessions.",
                                iconRole = UserRole.FACILITATOR,
                                selected = homeExperience == HomeExperience.ADVANCED,
                                onClick = {
                                    homeExperience = HomeExperience.ADVANCED
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text(
                        "You can change this later in Settings.",
                        color = AacTextSecondary
                    )
                    PrimaryButton(
                        text = "Create account",
                        onClick = {
                            onCreate(
                                "${uid.trim()}|${displayName.trim()}",
                                homeExperience
                            )
                        },
                        enabled = uid.trim().isNotEmpty() &&
                            displayName.trim().isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeExperienceCard(
    title: String,
    description: String,
    iconRole: UserRole,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 96.dp
) {
    val borderColor = if (selected) AacBlue else AacBorder
    val backgroundColor = if (selected) {
        AacBlue.copy(alpha = 0.12f)
    } else {
        Color.White
    }

    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .border(
                width = 2.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.large
            )
            .background(backgroundColor)
            .height(minHeight)
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(roleIconRes(iconRole)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(44.dp)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    color = AacTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 800)
@Composable
fun CreateAccountScreenTabletPreview() {
    GroupAacTheme {
        CreateAccountScreen(
            onBack = {},
            onCreate = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun CreateAccountScreenPhonePreview() {
    GroupAacTheme {
        CreateAccountScreen(
            onBack = {},
            onCreate = { _, _ -> }
        )
    }
}
