package com.example.groupaac.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.groupaac.ui.common.AppCard
import com.example.groupaac.ui.common.PrimaryButton
import com.example.groupaac.ui.theme.AacBackground
import com.example.groupaac.ui.theme.AacTextSecondary
import com.example.groupaac.ui.theme.GroupAacTheme

@Composable
fun CreateSessionScreen(
    initialDisplayName: String,
    isCreating: Boolean,
    errorMessage: String?,
    onCreate: (
        sessionName: String,
        displayName: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var sessionName by rememberSaveable {
        mutableStateOf("Group AAC Session")
    }
    var displayName by rememberSaveable(initialDisplayName) {
        mutableStateOf(initialDisplayName)
    }
    var validationError by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    fun submit() {
        val cleanSessionName = sessionName.trim()
        val cleanDisplayName = displayName.trim()

        validationError = when {
            cleanSessionName.isBlank() ->
                "Enter a session name."

            cleanDisplayName.isBlank() ->
                "Enter your display name."

            else -> null
        }

        if (validationError == null) {
            onCreate(cleanSessionName, cleanDisplayName)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AacBackground)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Create a Session",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Create a group and enter the live facilitator view.",
            color = AacTextSecondary
        )

        AppCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = sessionName,
                    onValueChange = {
                        sessionName = it
                        validationError = null
                    },
                    enabled = !isCreating,
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
                    onValueChange = {
                        displayName = it
                        validationError = null
                    },
                    enabled = !isCreating,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Your display name")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    )
                )

                (validationError ?: errorMessage)
                    ?.takeIf(String::isNotBlank)
                    ?.let { message ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
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

                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(
                            Alignment.CenterHorizontally
                        )
                    )
                }

                PrimaryButton(
                    text = if (isCreating) {
                        "Creating…"
                    } else {
                        "Create and start"
                    },
                    onClick = ::submit,
                    enabled = !isCreating,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 800)
@Composable
private fun CreateSessionScreenPreview() {
    GroupAacTheme {
        CreateSessionScreen(
            initialDisplayName = "Dr. Lee",
            isCreating = false,
            errorMessage = null,
            onCreate = { _, _ -> }
        )
    }
}
