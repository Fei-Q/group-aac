package com.example.groupaac.ui.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.groupaac.R
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.model.UserRole
import com.example.groupaac.ui.common.AppCard
import com.example.groupaac.ui.common.PrimaryButton
import com.example.groupaac.ui.common.SecondaryButton
import com.example.groupaac.ui.common.UserAvatar
import com.example.groupaac.ui.theme.AacBackground
import com.example.groupaac.ui.theme.AacBlue
import com.example.groupaac.ui.theme.AacTextSecondary
import com.example.groupaac.ui.theme.GroupAacTheme

@Composable
fun LoginScreen(
    uiState: AccountUiState,
    onUserSelected: (UserEntity) -> Unit,
    onCreateAccount: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(AacBackground).padding(24.dp)
    ) {
        val isTablet = maxWidth >= 700.dp
        if (isTablet) {
            TabletLoginLayout(uiState, onUserSelected, onCreateAccount)
        } else {
            PhoneLoginLayout(uiState, onUserSelected, onCreateAccount)
        }
    }
}

@Composable
private fun TabletLoginLayout(
    uiState: AccountUiState,
    onUserSelected: (UserEntity) -> Unit,
    onCreateAccount: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Group AAC", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.secondary)
        Text("Choose a saved user or create a local profile.", style = MaterialTheme.typography.bodyLarge, color = AacTextSecondary)
        Spacer(Modifier.height(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp), modifier = Modifier.fillMaxWidth()) {
            AppCard(modifier = Modifier.weight(1f)) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Login, contentDescription = null, tint = AacBlue)
                        Spacer(Modifier.width(12.dp))
                        Text("Existing user", style = MaterialTheme.typography.headlineMedium)
                    }
                    Text("Profiles are stored locally on this device. Select your name to continue to session joining.", color = AacTextSecondary)
                    SavedUserList(uiState.users, onUserSelected)
                }
            }
            AppCard(modifier = Modifier.weight(1f)) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AddCircleOutline, contentDescription = null, tint = AacBlue)
                        Spacer(Modifier.width(12.dp))
                        Text("New user", style = MaterialTheme.typography.headlineMedium)
                    }
                    Text("Create a local profile with your display name, role, and default accessibility preferences.", color = AacTextSecondary)
                    Image(
                        painter = painterResource(R.drawable.monitor_placeholder),
                        contentDescription = "Session display placeholder",
                        modifier = Modifier.align(Alignment.CenterHorizontally).size(width = 180.dp, height = 120.dp)
                    )
                    PrimaryButton("Create account", onClick = onCreateAccount, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun PhoneLoginLayout(
    uiState: AccountUiState,
    onUserSelected: (UserEntity) -> Unit,
    onCreateAccount: () -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text("Group AAC", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.secondary)
            Text("Choose a saved user or create a local profile.", style = MaterialTheme.typography.bodyLarge, color = AacTextSecondary)
        }
        item {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Returning user", style = MaterialTheme.typography.titleLarge)
                    SavedUserList(uiState.users, onUserSelected)
                }
            }
        }
        item {
            PrimaryButton("Create new account", onClick = onCreateAccount, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SavedUserList(users: List<UserEntity>, onUserSelected: (UserEntity) -> Unit) {
    if (users.isEmpty()) {
        Text("No saved users yet.", color = AacTextSecondary)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        users.forEach { user ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onUserSelected(user) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(user.displayName)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(user.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(user.role.label, color = AacTextSecondary)
                }
                SecondaryButton("Continue", onClick = { onUserSelected(user) })
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 800)
@Composable
fun LoginScreenTabletPreview() {
    GroupAacTheme {
        LoginScreen(
            uiState = AccountUiState(
                users = listOf(
                    UserEntity("1", "Alice", UserRole.PARTICIPANT, 0),
                    UserEntity("2", "Bob", UserRole.FACILITATOR, 0)
                ),
                isLoading = false
            ),
            onUserSelected = {},
            onCreateAccount = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun LoginScreenPhonePreview() {
    GroupAacTheme {
        LoginScreen(
            uiState = AccountUiState(
                users = listOf(
                    UserEntity("1", "Alice", UserRole.PARTICIPANT, 0)
                ),
                isLoading = false
            ),
            onUserSelected = {},
            onCreateAccount = {}
        )
    }
}
