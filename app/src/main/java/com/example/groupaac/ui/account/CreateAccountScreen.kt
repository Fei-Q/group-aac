package com.example.groupaac.ui.account

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.groupaac.model.UserRole
import com.example.groupaac.ui.common.AppCard
import com.example.groupaac.ui.common.PrimaryButton
import com.example.groupaac.ui.theme.AacBackground
import com.example.groupaac.ui.theme.AacTextSecondary
import com.example.groupaac.ui.theme.GroupAacTheme
import com.example.groupaac.ui.common.RoleSelectionButton
import com.example.groupaac.ui.common.RoleSelectionButtonLayout
import com.example.groupaac.ui.common.RoleSelectionButtonStyle

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun CreateAccountScreen(
    onBack: () -> Unit,
    onCreate: (String, UserRole) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.PARTICIPANT) }

    BoxWithConstraints(Modifier.fillMaxSize().background(AacBackground).padding(24.dp)) {
        val isTablet = maxWidth >= 700.dp
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
                    Text("Username", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Role", style = MaterialTheme.typography.titleLarge)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RoleSelectionButton(
                            role = UserRole.PARTICIPANT,
                            selected = role == UserRole.PARTICIPANT,
                            onClick = { role = UserRole.PARTICIPANT },
                            modifier = Modifier.weight(1f),
                            layout = RoleSelectionButtonLayout.Vertical,
                            selectedStyle = RoleSelectionButtonStyle.Solid,
                            iconSize = 48.dp
                        )

                        RoleSelectionButton(
                            role = UserRole.FACILITATOR,
                            selected = role == UserRole.FACILITATOR,
                            onClick = { role = UserRole.FACILITATOR },
                            modifier = Modifier.weight(1f),
                            layout = RoleSelectionButtonLayout.Vertical,
                            selectedStyle = RoleSelectionButtonStyle.Solid,
                            iconSize = 48.dp
                        )
                    }
                    Text(role.description, color = AacTextSecondary)
                    PrimaryButton(
                        text = "Create account",
                        onClick = { onCreate(name.trim(), role) },
                        enabled = name.trim().isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
