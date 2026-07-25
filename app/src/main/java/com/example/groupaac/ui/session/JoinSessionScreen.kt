package com.example.groupaac.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.groupaac.R
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.model.SessionRole
import com.example.groupaac.model.UserRole
import com.example.groupaac.ui.common.AppCard
import com.example.groupaac.ui.common.PrimaryButton
import com.example.groupaac.ui.common.RoleSelectionButton
import com.example.groupaac.ui.common.RoleSelectionButtonLayout
import com.example.groupaac.ui.common.RoleSelectionButtonStyle
import com.example.groupaac.ui.theme.AacBackground
import com.example.groupaac.ui.theme.AacBorder
import com.example.groupaac.ui.theme.AacTextSecondary
import com.example.groupaac.ui.theme.GroupAacTheme

@Composable
fun JoinSessionScreen(
    currentUser: UserEntity,
    isJoining: Boolean,
    errorMessage: String?,
    onJoin: (
        code: String,
        displayName: String,
        sessionRole: SessionRole,
        rememberProfile: Boolean
    ) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onScanQrCode: (() -> Unit)? = null
) {
    val isTablet = LocalConfiguration.current.screenWidthDp >= 700

    var codeDigits by rememberSaveable {
        mutableStateOf("")
    }
    var displayName by rememberSaveable(currentUser.id) {
        mutableStateOf(currentUser.displayName)
    }
    var selectedRole by rememberSaveable {
        mutableStateOf<SessionRole?>(null)
    }
    var rememberProfile by rememberSaveable {
        mutableStateOf(false)
    }
    var localValidationError by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(currentUser.id, currentUser.displayName) {
        displayName = currentUser.displayName
    }

    fun updateCode(raw: String) {
        codeDigits = normalizeSessionDigits(raw)
        localValidationError = null
    }

    fun submitJoin() {
        val cleanDisplayName = displayName.trim()
        val sessionRole = selectedRole

        localValidationError = when {
            codeDigits.length != 8 ->
                "Enter the complete eight-digit session code."

            cleanDisplayName.isBlank() ->
                "Enter your display name."

            sessionRole == null ->
                "Choose whether you want to participate or facilitate."

            else -> null
        }

        if (localValidationError == null && sessionRole != null) {
            onJoin(
                codeDigits,
                cleanDisplayName,
                sessionRole,
                rememberProfile
            )
        }
    }

    val joinEnabled =
        codeDigits.length == 8 &&
            displayName.isNotBlank() &&
            selectedRole != null &&
            !isJoining

    if (isTablet) {
        TabletJoinSession(
            codeDigits = codeDigits,
            onCodeChange = ::updateCode,
            displayName = displayName,
            onDisplayNameChange = {
                displayName = it
                localValidationError = null
            },
            selectedRole = selectedRole,
            onRoleChange = {
                selectedRole = it
                localValidationError = null
            },
            rememberProfile = rememberProfile,
            onRememberProfileChange = {
                rememberProfile = it
            },
            isJoining = isJoining,
            joinEnabled = joinEnabled,
            errorMessage = localValidationError ?: errorMessage,
            onJoin = ::submitJoin,
            onScanQrCode = onScanQrCode,
            onBack = onBack,
            modifier = modifier
        )
    } else {
        PhoneJoinSession(
            codeDigits = codeDigits,
            onCodeChange = ::updateCode,
            displayName = displayName,
            onDisplayNameChange = {
                displayName = it
                localValidationError = null
            },
            selectedRole = selectedRole,
            onRoleChange = {
                selectedRole = it
                localValidationError = null
            },
            rememberProfile = rememberProfile,
            onRememberProfileChange = {
                rememberProfile = it
            },
            isJoining = isJoining,
            joinEnabled = joinEnabled,
            errorMessage = localValidationError ?: errorMessage,
            onJoin = ::submitJoin,
            onScanQrCode = onScanQrCode,
            onBack = onBack,
            modifier = modifier
        )
    }
}

@Composable
private fun PhoneJoinSession(
    codeDigits: String,
    onCodeChange: (String) -> Unit,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    selectedRole: SessionRole?,
    onRoleChange: (SessionRole) -> Unit,
    rememberProfile: Boolean,
    onRememberProfileChange: (Boolean) -> Unit,
    isJoining: Boolean,
    joinEnabled: Boolean,
    errorMessage: String?,
    onJoin: () -> Unit,
    onScanQrCode: (() -> Unit)?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AacBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        JoinScreenHeader(onBack = onBack)

        FindSessionCard(
            codeDigits = codeDigits,
            onCodeChange = onCodeChange,
            onScanQrCode = onScanQrCode,
            enabled = !isJoining,
            modifier = Modifier.fillMaxWidth()
        )

        ProfileCard(
            displayName = displayName,
            onDisplayNameChange = onDisplayNameChange,
            selectedRole = selectedRole,
            onRoleChange = onRoleChange,
            rememberProfile = rememberProfile,
            onRememberProfileChange = onRememberProfileChange,
            enabled = !isJoining,
            roleButtonsStacked = true,
            modifier = Modifier.fillMaxWidth()
        )

        errorMessage
            ?.takeIf(String::isNotBlank)
            ?.let { ErrorMessageCard(it) }

        if (isJoining) {
            JoiningIndicator()
        }

        PrimaryButton(
            text = if (isJoining) "Joining…" else "Join session",
            onClick = onJoin,
            enabled = joinEnabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TabletJoinSession(
    codeDigits: String,
    onCodeChange: (String) -> Unit,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    selectedRole: SessionRole?,
    onRoleChange: (SessionRole) -> Unit,
    rememberProfile: Boolean,
    onRememberProfileChange: (Boolean) -> Unit,
    isJoining: Boolean,
    joinEnabled: Boolean,
    errorMessage: String?,
    onJoin: () -> Unit,
    onScanQrCode: (() -> Unit)?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AacBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 64.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        JoinScreenHeader(onBack = onBack)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 980.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                FindSessionCard(
                    codeDigits = codeDigits,
                    onCodeChange = onCodeChange,
                    onScanQrCode = onScanQrCode,
                    enabled = !isJoining,
                    modifier = Modifier.fillMaxWidth()
                )

                errorMessage
                    ?.takeIf(String::isNotBlank)
                    ?.let { ErrorMessageCard(it) }

                if (isJoining) {
                    JoiningIndicator()
                }

                PrimaryButton(
                    text = if (isJoining) {
                        "Joining…"
                    } else {
                        "Join session"
                    },
                    onClick = onJoin,
                    enabled = joinEnabled,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ProfileCard(
                displayName = displayName,
                onDisplayNameChange = onDisplayNameChange,
                selectedRole = selectedRole,
                onRoleChange = onRoleChange,
                rememberProfile = rememberProfile,
                onRememberProfileChange = onRememberProfileChange,
                enabled = !isJoining,
                roleButtonsStacked = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun JoinScreenHeader(
    onBack: (() -> Unit)?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (onBack != null) {
            Text(
                text = "Back",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onBack() }
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Join a Session",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Enter the code shared by your facilitator.",
                color = AacTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FindSessionCard(
    codeDigits: String,
    onCodeChange: (String) -> Unit,
    onScanQrCode: (() -> Unit)?,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier,
        contentPadding = PaddingValues(18.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "1. Find my session",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )

            CenteredCodeInputField(
                codeDigits = codeDigits,
                onCodeChange = onCodeChange,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "or",
                modifier = Modifier.align(
                    Alignment.CenterHorizontally
                ),
                color = AacTextSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            PrimaryButton(
                text = if (onScanQrCode == null) {
                    "Scan QR code — coming soon"
                } else {
                    "Scan QR code"
                },
                onClick = {
                    onScanQrCode?.invoke()
                },
                enabled = enabled && onScanQrCode != null,
                modifier = Modifier.fillMaxWidth(),
                leadingIconRes = R.drawable.ic_scan_qr_code
            )

            Text(
                text = "The code is checked when you tap Join session.",
                color = AacTextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ProfileCard(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    selectedRole: SessionRole?,
    onRoleChange: (SessionRole) -> Unit,
    rememberProfile: Boolean,
    onRememberProfileChange: (Boolean) -> Unit,
    enabled: Boolean,
    roleButtonsStacked: Boolean,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier,
        contentPadding = PaddingValues(18.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "2. Edit my info:",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "My name is:",
                style = MaterialTheme.typography.bodyLarge
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text("Name")
                }
            )

            Text(
                text = "I'm here to:",
                style = MaterialTheme.typography.bodyLarge
            )

            if (roleButtonsStacked) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SessionRoleSelectionButton(
                        role = SessionRole.PARTICIPANT,
                        label = "Participate",
                        selected = selectedRole == SessionRole.PARTICIPANT,
                        onClick = { onRoleChange(SessionRole.PARTICIPANT) },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                        labelFontSize = 16.sp
                    )

                    SessionRoleSelectionButton(
                        role = SessionRole.FACILITATOR,
                        label = "Facilitate",
                        selected = selectedRole == SessionRole.FACILITATOR,
                        onClick = { onRoleChange(SessionRole.FACILITATOR) },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                        labelFontSize = 16.sp
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SessionRoleSelectionButton(
                        role = SessionRole.PARTICIPANT,
                        label = "Participate",
                        selected = selectedRole == SessionRole.PARTICIPANT,
                        onClick = { onRoleChange(SessionRole.PARTICIPANT) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        labelFontSize = 15.sp
                    )

                    SessionRoleSelectionButton(
                        role = SessionRole.FACILITATOR,
                        label = "Facilitate",
                        selected = selectedRole == SessionRole.FACILITATOR,
                        onClick = { onRoleChange(SessionRole.FACILITATOR) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        labelFontSize = 15.sp
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) {
                        onRememberProfileChange(!rememberProfile)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = rememberProfile,
                    onCheckedChange = onRememberProfileChange,
                    enabled = enabled
                )

                Text(
                    text = "Remember my settings for this group.",
                    color = AacTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun SessionRoleSelectionButton(
    role: SessionRole,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    labelFontSize: androidx.compose.ui.unit.TextUnit
) {
    val mappedRole = when (role) {
        SessionRole.PARTICIPANT -> UserRole.PARTICIPANT
        SessionRole.FACILITATOR,
        SessionRole.HOST -> UserRole.FACILITATOR
    }

    Box(
        modifier = modifier
    ) {
        RoleSelectionButton(
            role = mappedRole,
            label = label,
            selected = selected,
            onClick = {
                if (enabled) {
                    onClick()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            layout = RoleSelectionButtonLayout.Horizontal,
            selectedStyle = RoleSelectionButtonStyle.Soft,
            iconSize = 24.dp,
            labelFontSize = labelFontSize
        )
    }
}

@Composable
private fun CenteredCodeInputField(
    codeDigits: String,
    onCodeChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = formatSessionCode(codeDigits),
        onValueChange = onCodeChange,
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        modifier = modifier
            .heightIn(min = 56.dp)
            .border(
                width = 1.dp,
                color = AacBorder,
                shape = RoundedCornerShape(4.dp)
            )
            .background(
                color = Color.White,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 14.dp),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(
                        id = R.drawable.ic_enter_code
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = AacTextSecondary
                )

                Spacer(Modifier.width(10.dp))

                Box(
                    modifier = Modifier.widthIn(min = 150.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (codeDigits.isBlank()) {
                        Text(
                            text = "Enter number code",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AacTextSecondary
                        )
                    } else {
                        innerTextField()
                    }
                }
            }
        }
    )
}

@Composable
private fun JoiningIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp
        )

        Spacer(Modifier.width(10.dp))

        Text(
            text = "Connecting to the session…",
            color = AacTextSecondary
        )
    }
}

@Composable
private fun ErrorMessageCard(
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
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun normalizeSessionDigits(raw: String): String {
    return raw.filter(Char::isDigit).take(8)
}

private fun formatSessionCode(digits: String): String {
    return if (digits.length <= 4) {
        digits
    } else {
        "${digits.take(4)}-${digits.drop(4)}"
    }
}

@Preview(
    showBackground = true,
    widthDp = 1000,
    heightDp = 800
)
@Composable
private fun JoinSessionScreenTabletPreview() {
    GroupAacTheme {
        JoinSessionScreen(
            currentUser = UserEntity(
                id = "1",
                displayName = "Alice",
                role = UserRole.PARTICIPANT,
                createdAt = 0
            ),
            isJoining = false,
            errorMessage = null,
            onJoin = { _, _, _, _ -> }
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 800
)
@Composable
private fun JoinSessionScreenPhonePreview() {
    GroupAacTheme {
        JoinSessionScreen(
            currentUser = UserEntity(
                id = "1",
                displayName = "Alice",
                role = UserRole.PARTICIPANT,
                createdAt = 0
            ),
            isJoining = false,
            errorMessage = "No session found for this code.",
            onJoin = { _, _, _, _ -> }
        )
    }
}
