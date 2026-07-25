package com.example.groupaac.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.groupaac.data.entity.UserEntity
import com.example.groupaac.ui.common.AppCard
import com.example.groupaac.ui.common.AppWindowSize
import com.example.groupaac.ui.common.PrimaryButton
import com.example.groupaac.ui.common.rememberAppWindowSize
import com.example.groupaac.ui.theme.AacBackground
import com.example.groupaac.ui.theme.AacTextSecondary

@Composable
fun FacilitatorSessionsScreen(
    currentUser: UserEntity,
    isWorking: Boolean,
    errorMessage: String?,
    onCreateSession: (
        sessionName: String,
        displayName: String
    ) -> Unit,
    onJoinSession: (
        code: String,
        displayName: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var displayName by rememberSaveable(currentUser.id) {
        mutableStateOf(currentUser.displayName)
    }

    var sessionName by rememberSaveable {
        mutableStateOf("Group AAC Session")
    }

    var joinCodeDigits by rememberSaveable {
        mutableStateOf("")
    }

    var createValidationError by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var joinValidationError by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(currentUser.id, currentUser.displayName) {
        displayName = currentUser.displayName
    }

    fun submitCreateSession() {
        val cleanSessionName = sessionName.trim()
        val cleanDisplayName = displayName.trim()

        createValidationError = when {
            cleanSessionName.isBlank() ->
                "Enter a session name."

            cleanDisplayName.isBlank() ->
                "Enter your display name."

            else -> null
        }

        if (createValidationError == null) {
            onCreateSession(
                cleanSessionName,
                cleanDisplayName
            )
        }
    }

    fun submitJoinSession() {
        val cleanDisplayName = displayName.trim()

        joinValidationError = when {
            joinCodeDigits.length != 8 ->
                "Enter the complete eight-digit session code."

            cleanDisplayName.isBlank() ->
                "Enter your display name."

            else -> null
        }

        if (joinValidationError == null) {
            onJoinSession(
                joinCodeDigits,
                cleanDisplayName
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AacBackground)
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = when (rememberAppWindowSize()) {
                    AppWindowSize.Phone -> 20.dp
                    AppWindowSize.Tablet,
                    AppWindowSize.Desktop -> 28.dp
                },
                vertical = 24.dp
            ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SessionsHeader(
            currentUser = currentUser
        )

        if (isWorking) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Connecting to the session…",
                color = AacTextSecondary
            )
        }

        errorMessage
            ?.takeIf { it.isNotBlank() }
            ?.let { message ->
                ErrorMessageCard(message)
            }

        when (rememberAppWindowSize()) {
            AppWindowSize.Phone -> {
                CreateSessionCard(
                    sessionName = sessionName,
                    onSessionNameChange = {
                        sessionName = it
                        createValidationError = null
                    },
                    displayName = displayName,
                    onDisplayNameChange = {
                        displayName = it
                        createValidationError = null
                        joinValidationError = null
                    },
                    validationError = createValidationError,
                    enabled = !isWorking,
                    onCreate = ::submitCreateSession
                )

                JoinExistingSessionCard(
                    codeDigits = joinCodeDigits,
                    onCodeChange = { raw ->
                        joinCodeDigits = raw
                            .filter(Char::isDigit)
                            .take(8)

                        joinValidationError = null
                    },
                    displayName = displayName,
                    onDisplayNameChange = {
                        displayName = it
                        createValidationError = null
                        joinValidationError = null
                    },
                    validationError = joinValidationError,
                    enabled = !isWorking,
                    onJoin = ::submitJoinSession
                )
            }

            AppWindowSize.Tablet,
            AppWindowSize.Desktop -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(22.dp)
                ) {
                    CreateSessionCard(
                        sessionName = sessionName,
                        onSessionNameChange = {
                            sessionName = it
                            createValidationError = null
                        },
                        displayName = displayName,
                        onDisplayNameChange = {
                            displayName = it
                            createValidationError = null
                            joinValidationError = null
                        },
                        validationError = createValidationError,
                        enabled = !isWorking,
                        onCreate = ::submitCreateSession,
                        modifier = Modifier.weight(1f)
                    )

                    JoinExistingSessionCard(
                        codeDigits = joinCodeDigits,
                        onCodeChange = { raw ->
                            joinCodeDigits = raw
                                .filter(Char::isDigit)
                                .take(8)

                            joinValidationError = null
                        },
                        displayName = displayName,
                        onDisplayNameChange = {
                            displayName = it
                            createValidationError = null
                            joinValidationError = null
                        },
                        validationError = joinValidationError,
                        enabled = !isWorking,
                        onJoin = ::submitJoinSession,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionsHeader(
    currentUser: UserEntity
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Sessions",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.secondary
        )

        Text(
            text = "Create a new group session or join an existing session.",
            color = AacTextSecondary
        )

        Text(
            text = "Signed in as ${currentUser.displayName} • " +
                    currentUser.role.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CreateSessionCard(
    sessionName: String,
    onSessionNameChange: (String) -> Unit,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    validationError: String?,
    enabled: Boolean,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Create a session",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Start a new group and enter its live " +
                        "facilitator view.",
                color = AacTextSecondary
            )

            OutlinedTextField(
                value = sessionName,
                onValueChange = onSessionNameChange,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Session name")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                )
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Your display name")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                )
            )

            validationError?.let {
                ValidationMessage(it)
            }

            PrimaryButton(
                text = "Create and start",
                onClick = onCreate,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun JoinExistingSessionCard(
    codeDigits: String,
    onCodeChange: (String) -> Unit,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    validationError: String?,
    enabled: Boolean,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Join an existing session",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Use this option when another facilitator " +
                        "has already created the session.",
                color = AacTextSecondary
            )

            OutlinedTextField(
                value = codeDigits,
                onValueChange = onCodeChange,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Eight-digit session code")
                },
                placeholder = {
                    Text("12345678")
                },
                supportingText = {
                    Text("${codeDigits.length}/8 digits")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Next
                )
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Your display name")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                )
            )

            validationError?.let {
                ValidationMessage(it)
            }

            PrimaryButton(
                text = "Join session",
                onClick = onJoin,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ValidationMessage(
    message: String
) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium
    )
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