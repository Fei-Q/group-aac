package com.example.groupaac.ui.session

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.model.UserRole
import com.example.groupaac.ui.common.AppCard
import com.example.groupaac.ui.common.PrimaryButton
import com.example.groupaac.ui.theme.AacBackground
import com.example.groupaac.ui.theme.AacGreen
import com.example.groupaac.ui.theme.AacTextSecondary
import com.example.groupaac.ui.theme.GroupAacTheme
import kotlinx.coroutines.delay
import com.example.groupaac.ui.common.RoleSelectionButton
import com.example.groupaac.ui.common.RoleSelectionButtonLayout
import com.example.groupaac.ui.common.RoleSelectionButtonStyle
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.example.groupaac.R
import com.example.groupaac.ui.theme.AacBorder

private const val MOCK_SCANNED_SESSION_CODE = "12345678"
private val AacErrorRed = Color(0xFFB3261E)

private enum class SessionLookupStatus {
    Idle,
    Loading,
    Found,
    NotFound
}

private data class LocatedSessionPreview(
    val sessionCodeDigits: String,
    val sessionName: String,
    val scheduledTime: String
)

@Composable
fun JoinSessionScreen(
    currentUser: UserEntity?,
    onJoin: (String, String, UserRole, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 700

    var codeDigits by rememberSaveable {
        mutableStateOf("")
    }

    var lookupStatus by rememberSaveable {
        mutableStateOf(SessionLookupStatus.Idle)
    }

    var displayName by rememberSaveable(currentUser?.id) {
        mutableStateOf(currentUser?.displayName.orEmpty())
    }

    var selectedRole by rememberSaveable(currentUser?.id) {
        mutableStateOf(currentUser?.role ?: UserRole.PARTICIPANT)
    }

    var rememberSettings by rememberSaveable {
        mutableStateOf(false)
    }

    LaunchedEffect(codeDigits) {
        lookupStatus = when {
            codeDigits.length < 8 -> SessionLookupStatus.Idle
            codeDigits.length == 8 -> {
                SessionLookupStatus.Loading
            }
            else -> SessionLookupStatus.Idle
        }

        if (codeDigits.length == 8) {
            delay(800)

            lookupStatus = if (mockSessionLookupSucceeds(codeDigits)) {
                SessionLookupStatus.Found
            } else {
                SessionLookupStatus.NotFound
            }
        }
    }

    val locatedSession =
        if (lookupStatus == SessionLookupStatus.Found) {
            LocatedSessionPreview(
                sessionCodeDigits = codeDigits,
                sessionName = "Group AAC Session",
                scheduledTime = "July 4th, 3-4 pm"
            )
        } else {
            null
        }

    val joinEnabled = locatedSession != null && displayName.isNotBlank()

    fun updateCode(raw: String) {
        codeDigits = normalizeSessionDigits(raw)
    }

    fun scanQrCode() {
        // TODO: Replace this with CameraX / ML Kit scanner.
        // Scanner success should call updateCode(scannedCode).
        updateCode(MOCK_SCANNED_SESSION_CODE)
    }

    fun joinLocatedSession() {
        val session = locatedSession ?: return

        if (displayName.isBlank()) {
            return
        }

        onJoin(
            session.sessionCodeDigits,
            displayName.trim(),
            selectedRole,
            rememberSettings
        )
    }

    if (isTablet) {
        TabletJoinSession(
            codeDigits = codeDigits,
            onCodeChange = ::updateCode,
            lookupStatus = lookupStatus,
            locatedSession = locatedSession,
            displayName = displayName,
            onDisplayNameChange = { displayName = it },
            selectedRole = selectedRole,
            onRoleChange = { selectedRole = it },
            rememberSettings = rememberSettings,
            onRememberSettingsChange = { rememberSettings = it },
            onScanClick = ::scanQrCode,
            joinEnabled = joinEnabled,
            onJoin = ::joinLocatedSession,
            onBack = onBack
        )
    } else {
        PhoneJoinSession(
            codeDigits = codeDigits,
            onCodeChange = ::updateCode,
            lookupStatus = lookupStatus,
            locatedSession = locatedSession,
            displayName = displayName,
            onDisplayNameChange = { displayName = it },
            selectedRole = selectedRole,
            onRoleChange = { selectedRole = it },
            rememberSettings = rememberSettings,
            onRememberSettingsChange = { rememberSettings = it },
            onScanClick = ::scanQrCode,
            joinEnabled = joinEnabled,
            onJoin = ::joinLocatedSession,
            onBack = onBack
        )
    }
}

@Composable
private fun PhoneJoinSession(
    codeDigits: String,
    onCodeChange: (String) -> Unit,
    lookupStatus: SessionLookupStatus,
    locatedSession: LocatedSessionPreview?,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    selectedRole: UserRole,
    onRoleChange: (UserRole) -> Unit,
    rememberSettings: Boolean,
    onRememberSettingsChange: (Boolean) -> Unit,
    onScanClick: () -> Unit,
    joinEnabled: Boolean,
    onJoin: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AacBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        JoinScreenHeader(onBack = onBack)

        FindGroupCard(
            codeDigits = codeDigits,
            onCodeChange = onCodeChange,
            lookupStatus = lookupStatus,
            locatedSession = locatedSession,
            onScanClick = onScanClick,
            modifier = Modifier.fillMaxWidth()
        )

        EditProfileCard(
            displayName = displayName,
            onDisplayNameChange = onDisplayNameChange,
            selectedRole = selectedRole,
            onRoleChange = onRoleChange,
            rememberSettings = rememberSettings,
            onRememberSettingsChange = onRememberSettingsChange,
            roleButtonsStacked = true,
            modifier = Modifier.fillMaxWidth()
        )

        PrimaryButton(
            text = "Join",
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
    lookupStatus: SessionLookupStatus,
    locatedSession: LocatedSessionPreview?,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    selectedRole: UserRole,
    onRoleChange: (UserRole) -> Unit,
    rememberSettings: Boolean,
    onRememberSettingsChange: (Boolean) -> Unit,
    onScanClick: () -> Unit,
    joinEnabled: Boolean,
    onJoin: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
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
                FindGroupCard(
                    codeDigits = codeDigits,
                    onCodeChange = onCodeChange,
                    lookupStatus = lookupStatus,
                    locatedSession = locatedSession,
                    onScanClick = onScanClick,
                    modifier = Modifier.fillMaxWidth()
                )

                PrimaryButton(
                    text = "Join",
                    onClick = onJoin,
                    enabled = joinEnabled,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            EditProfileCard(
                displayName = displayName,
                onDisplayNameChange = onDisplayNameChange,
                selectedRole = selectedRole,
                onRoleChange = onRoleChange,
                rememberSettings = rememberSettings,
                onRememberSettingsChange = onRememberSettingsChange,
                roleButtonsStacked = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun JoinScreenHeader(
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Text(
                text = "←",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Text(
            text = "Join a Session!",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun FindGroupCard(
    codeDigits: String,
    onCodeChange: (String) -> Unit,
    lookupStatus: SessionLookupStatus,
    locatedSession: LocatedSessionPreview?,
    onScanClick: () -> Unit,
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
                text = "1. Find my group:",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )

            CenteredCodeInputField(
                codeDigits = codeDigits,
                onCodeChange = onCodeChange,
                modifier = Modifier.fillMaxWidth()
            )

            when (lookupStatus) {
                SessionLookupStatus.Idle -> {
                    Text(
                        text = "or",
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color = AacTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    PrimaryButton(
                        text = "Scan QR Code",
                        onClick = onScanClick,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIconRes = R.drawable.ic_scan_qr_code
                    )

                    Text(
                        text = "Scan session QR code or enter the number code.",
                        color = AacTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                SessionLookupStatus.Loading -> {
                    SessionLookupLoading()
                }

                SessionLookupStatus.Found -> {
                    if (locatedSession != null) {
                        LocatedSessionPreviewBlock(locatedSession)
                    }
                }

                SessionLookupStatus.NotFound -> {
                    SessionLookupError()
                }
            }
        }
    }
}

@Composable
private fun SessionLookupLoading() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
        )

        Text(
            text = "Finding session...",
            color = AacTextSecondary,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun SessionLookupError() {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "No session found.",
            color = AacErrorRed,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Check the number code and try again.",
            color = AacTextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun LocatedSessionPreviewBlock(
    locatedSession: LocatedSessionPreview
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Session found!",
                color = AacGreen,
                style = MaterialTheme.typography.labelLarge
            )

            Spacer(Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(AacGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Group Name: ${locatedSession.sessionName}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Scheduled Time: ${locatedSession.scheduledTime}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CenteredCodeInputField(
    codeDigits: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = formatSessionCode(codeDigits),
        onValueChange = onCodeChange,
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
            .border(1.dp, AacBorder, RoundedCornerShape(4.dp))
            .background(Color.White, RoundedCornerShape(4.dp))
            .padding(horizontal = 14.dp),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_enter_code),
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
                            text = "Enter Number Code",
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
private fun EditProfileCard(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    selectedRole: UserRole,
    onRoleChange: (UserRole) -> Unit,
    rememberSettings: Boolean,
    onRememberSettingsChange: (Boolean) -> Unit,
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
                    RoleSelectionButton(
                        role = UserRole.PARTICIPANT,
                        label = "Participate",
                        selected = selectedRole == UserRole.PARTICIPANT,
                        onClick = { onRoleChange(UserRole.PARTICIPANT) },
                        modifier = Modifier.fillMaxWidth(),
                        layout = RoleSelectionButtonLayout.Horizontal,
                        selectedStyle = RoleSelectionButtonStyle.Soft,
                        iconSize = 24.dp,
                        labelFontSize = 16.sp
                    )

                    RoleSelectionButton(
                        role = UserRole.FACILITATOR,
                        label = "Facilitate",
                        selected = selectedRole == UserRole.FACILITATOR,
                        onClick = { onRoleChange(UserRole.FACILITATOR) },
                        modifier = Modifier.fillMaxWidth(),
                        layout = RoleSelectionButtonLayout.Horizontal,
                        selectedStyle = RoleSelectionButtonStyle.Soft,
                        iconSize = 24.dp,
                        labelFontSize = 16.sp
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RoleSelectionButton(
                        role = UserRole.PARTICIPANT,
                        label = "Participate",
                        selected = selectedRole == UserRole.PARTICIPANT,
                        onClick = { onRoleChange(UserRole.PARTICIPANT) },
                        modifier = Modifier.weight(1f),
                        layout = RoleSelectionButtonLayout.Horizontal,
                        selectedStyle = RoleSelectionButtonStyle.Soft,
                        iconSize = 24.dp,
                        labelFontSize = 15.sp
                    )

                    RoleSelectionButton(
                        role = UserRole.FACILITATOR,
                        label = "Facilitate",
                        selected = selectedRole == UserRole.FACILITATOR,
                        onClick = { onRoleChange(UserRole.FACILITATOR) },
                        modifier = Modifier.weight(1f),
                        layout = RoleSelectionButtonLayout.Horizontal,
                        selectedStyle = RoleSelectionButtonStyle.Soft,
                        iconSize = 24.dp,
                        labelFontSize = 15.sp
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onRememberSettingsChange(!rememberSettings)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = rememberSettings,
                    onCheckedChange = onRememberSettingsChange
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

private fun normalizeSessionDigits(raw: String): String {
    return raw.filter { it.isDigit() }.take(8)
}

private fun formatSessionCode(digits: String): String {
    return when {
        digits.length <= 4 -> digits
        else -> digits.take(4) + "-" + digits.drop(4)
    }
}

private fun mockSessionLookupSucceeds(codeDigits: String): Boolean {
    return codeDigits.length == 8 && codeDigits != "00000000"
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 800)
@Composable
fun JoinSessionScreenTabletPreview() {
    GroupAacTheme {
        JoinSessionScreen(
            currentUser = UserEntity("1", "Alice", UserRole.PARTICIPANT, 0),
            onJoin = { _, _, _, _ -> },
            onBack = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun JoinSessionScreenPhonePreview() {
    GroupAacTheme {
        JoinSessionScreen(
            currentUser = UserEntity("1", "Alice", UserRole.PARTICIPANT, 0),
            onJoin = { _, _, _, _ -> },
            onBack = {}
        )
    }
}