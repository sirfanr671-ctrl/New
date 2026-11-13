package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.CustomRoutineEntity
import com.example.data.local.LocalNoteReminderEntity
import com.example.data.local.VoiceHistoryEntity
import com.example.voice.RoutineAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    history: List<VoiceHistoryEntity>,
    notesReminders: List<LocalNoteReminderEntity>,
    onReplayCommand: (String) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onClearAll: () -> Unit,
    onToggleNoteDone: (LocalNoteReminderEntity) -> Unit,
    onDeleteNote: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showNotesTab by remember { mutableStateOf(false) }

    val filteredHistory = remember(history, searchQuery) {
        if (searchQuery.isBlank()) history
        else history.filter {
            it.transcript.contains(searchQuery, ignoreCase = true) ||
                it.response.contains(searchQuery, ignoreCase = true)
        }
    }

    val dateFormatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("History & Notes", style = MaterialTheme.typography.headlineSmall)
            if (!showNotesTab && history.isNotEmpty()) {
                TextButton(
                    onClick = onClearAll,
                    modifier = Modifier.testTag("clear_all_history_button")
                ) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Wipe History")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { showNotesTab = false },
                modifier = Modifier.weight(1f).testTag("tab_voice_history")
            ) {
                Text("Voice Log (${history.size})")
            }
            OutlinedButton(
                onClick = { showNotesTab = true },
                modifier = Modifier.weight(1f).testTag("tab_notes_reminders")
            ) {
                Text("Notes & Reminders (${notesReminders.size})")
            }
        }

        Spacer(Modifier.height(10.dp))

        if (!showNotesTab) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search command transcripts") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("history_search_input")
            )
            Spacer(Modifier.height(10.dp))

            if (filteredHistory.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("No voice command history yet", style = MaterialTheme.typography.titleMedium)
                    Text("Commands stay stored privately on your device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredHistory, key = { it.id }) { item ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                                        Text(
                                            "${item.executionMode} • ${item.languageCode}",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Text(
                                        dateFormatter.format(Date(item.timestampMillis)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(item.transcript, style = MaterialTheme.typography.titleMedium)
                                Text(item.response, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = { onReplayCommand(item.transcript) }) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Text("Run Again")
                                    }
                                    IconButton(onClick = { onDeleteItem(item.id) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Delete history item")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notesReminders, key = { it.id }) { note ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onToggleNoteDone(note) }) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = "Toggle completed",
                                    tint = if (note.completed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${note.kind.uppercase()} • ${note.title}", style = MaterialTheme.typography.titleMedium)
                                Text(note.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onDeleteNote(note.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete item")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomCommandsScreen(
    routines: List<CustomRoutineEntity>,
    onSaveRoutine: (id: String?, triggerPhrase: String, actions: List<String>, customReply: String) -> Unit,
    onToggleRoutine: (CustomRoutineEntity, Boolean) -> Unit,
    onTestRoutine: (CustomRoutineEntity) -> Unit,
    onDeleteRoutine: (String) -> Unit
) {
    var editingRoutine by remember { mutableStateOf<CustomRoutineEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingRoutine = null
                    showDialog = true
                },
                modifier = Modifier.testTag("add_custom_routine_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Create custom routine")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Text("Custom Voice Commands", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Create multi-step voice routines (e.g. say \"Good morning\" to hear time, weather, reminders, and battery).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 84.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(routines, key = { it.id }) { routine ->
                    val actionKeys = routine.actionsCsv.split(",").filter { it.isNotBlank() }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "\"${routine.triggerPhrase}\"",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Switch(
                                    checked = routine.enabled,
                                    onCheckedChange = { onToggleRoutine(routine, it) }
                                )
                            }
                            if (routine.customReply.isNotBlank()) {
                                Text("Spoken intro: ${routine.customReply}", style = MaterialTheme.typography.bodyMedium)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                actionKeys.forEach { key ->
                                    val label = RoutineAction.entries.firstOrNull { it.key == key }?.label ?: key
                                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onTestRoutine(routine) },
                                    modifier = Modifier.testTag("test_routine_${routine.id}")
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Test")
                                }
                                OutlinedButton(
                                    onClick = {
                                        editingRoutine = routine
                                        showDialog = true
                                    }
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Edit")
                                }
                                IconButton(onClick = { onDeleteRoutine(routine.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete routine")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        RoutineEditorDialog(
            initial = editingRoutine,
            onDismiss = { showDialog = false },
            onSave = { phrase, actions, reply ->
                onSaveRoutine(editingRoutine?.id, phrase, actions, reply)
                showDialog = false
            }
        )
    }
}

@Composable
private fun RoutineEditorDialog(
    initial: CustomRoutineEntity?,
    onDismiss: () -> Unit,
    onSave: (String, List<String>, String) -> Unit
) {
    var phrase by remember { mutableStateOf(initial?.triggerPhrase ?: "") }
    var customReply by remember { mutableStateOf(initial?.customReply ?: "") }
    val selectedActions = remember {
        mutableStateListOf<String>().apply {
            val init = initial?.actionsCsv?.split(",")?.filter { it.isNotBlank() } ?: listOf("READ_TIME", "READ_BATTERY")
            addAll(init)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Custom Command" else "Edit Custom Command") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                    label = { Text("When I say...") },
                    placeholder = { Text("Good morning") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("routine_phrase_input")
                )
                OutlinedTextField(
                    value = customReply,
                    onValueChange = { customReply = it },
                    label = { Text("Spoken greeting (optional)") },
                    placeholder = { Text("Good morning S Irfan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("routine_reply_input")
                )
                Text("Actions to run:", style = MaterialTheme.typography.labelLarge)
                RoutineAction.entries.forEach { action ->
                    val checked = selectedActions.contains(action.key)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                if (isChecked && !checked) selectedActions.add(action.key)
                                else if (!isChecked) selectedActions.remove(action.key)
                            }
                        )
                        Text(action.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(phrase, selectedActions.toList().ifEmpty { listOf("READ_TIME") }, customReply) },
                enabled = phrase.trim().length >= 2,
                modifier = Modifier.testTag("save_routine_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
