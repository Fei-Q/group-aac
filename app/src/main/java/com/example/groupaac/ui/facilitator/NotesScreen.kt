package com.example.groupaac.ui.facilitator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.groupaac.data.dao.NoteWithParticipant
import com.example.groupaac.ui.common.AppCard
import com.example.groupaac.ui.common.PrimaryButton
import com.example.groupaac.ui.theme.AacBackground
import com.example.groupaac.ui.theme.AacTextSecondary
import com.example.groupaac.ui.theme.GroupAacTheme
import com.example.groupaac.util.TimeUtils

@Composable
fun NotesScreen(
    uiState: FacilitatorUiState,
    onAddNote: (String?, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var note by remember { mutableStateOf("") }
    Row(modifier.fillMaxSize().background(AacBackground).padding(22.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Notes", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.secondary)
            Text("Facilitator notes for follow-up after the group meeting.", color = AacTextSecondary)
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), minLines = 5, label = { Text("Session note") })
                    PrimaryButton("Add note", onClick = { onAddNote(null, note); note = "" }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        LazyColumn(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(uiState.notes, key = { it.id }) { row ->
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(row.participantName ?: "Session note", style = MaterialTheme.typography.titleMedium)
                        Text(TimeUtils.clockTime(row.createdAt), color = AacTextSecondary)
                        Text(row.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
fun NotesScreenPreview() {
    val mockNotes = listOf(
        NoteWithParticipant("1", "s1", "u1", "Alice", "f1", "Alice was very active today.", System.currentTimeMillis() - 600000),
        NoteWithParticipant("2", "s1", null, null, "f1", "Discuss the new communication strategies next time.", System.currentTimeMillis() - 300000)
    )
    GroupAacTheme {
        NotesScreen(
            uiState = FacilitatorUiState(
                notes = mockNotes
            ),
            onAddNote = { _, _ -> }
        )
    }
}
