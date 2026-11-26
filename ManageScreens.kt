package com.maherma.app.ui.manage

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maherma.app.ui.components.GlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenFrame(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title, style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            actions = { actions() },
        )
    }) { pad -> Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) { content() } }
}

@Composable
private fun AddRow(hint: String, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    OutlinedTextField(
        value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        placeholder = { Text(hint) }, singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onAdd(text); text = "" }),
        trailingIcon = { IconButton(onClick = { onAdd(text); text = "" }, enabled = text.isNotBlank()) { Icon(Icons.Rounded.Add, contentDescription = "Add") } },
    )
}

@Composable
fun TasksScreen(onBack: () -> Unit, vm: ManageViewModel = hiltViewModel()) {
    val tasks by vm.openTasks.collectAsStateWithLifecycle()
    ScreenFrame("Tasks & reminders", onBack) {
        AddRow("Add a task (say “remind me…” for timed ones)") { vm.addTask(it) }
        if (tasks.isEmpty()) Text("Nothing open.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(tasks, key = { it.id }) { t ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = false, onCheckedChange = { vm.completeTask(t.id) })
                    Column(Modifier.weight(1f)) {
                        Text(t.title)
                        t.dueAt?.let {
                            Text(
                                DateUtils.formatDateTime(null, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL) +
                                    if (t.repeat != "NONE") " · ${t.repeat.lowercase()}" else "",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { vm.deleteTask(t.id) }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete task") }
                }
            }
        }
    }
}

@Composable
fun NotesScreen(onBack: () -> Unit, vm: ManageViewModel = hiltViewModel()) {
    val notes by vm.noteList.collectAsStateWithLifecycle()
    ScreenFrame("Notes", onBack) {
        AddRow("New note") { vm.addNote(it) }
        if (notes.isEmpty()) Text("No notes yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(notes, key = { it.id }) { n ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(n.title, style = MaterialTheme.typography.titleMedium)
                            if (n.body != n.title) Text(n.body, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { vm.deleteNote(n.id) }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete note") }
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryScreen(onBack: () -> Unit, vm: ManageViewModel = hiltViewModel()) {
    val list by vm.memories.collectAsStateWithLifecycle()
    val cfg by vm.settings.collectAsStateWithLifecycle()
    var confirmAll by remember { mutableStateOf(false) }
    ScreenFrame("Memory", onBack, actions = { TextButton(onClick = { confirmAll = true }, enabled = list.isNotEmpty()) { Text("Forget all") } }) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Use memory", style = MaterialTheme.typography.titleMedium)
                Text("When off, nothing is read or sent to the AI.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = cfg.memoryEnabled, onCheckedChange = { vm.setMemoryMaster(it) })
        }
        AddRow("Add something to remember") { vm.addMemory(it) }
        if (list.isEmpty()) Text("Nothing saved. Say “remember that…”. Sensitive items always ask first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.id }) { m ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(m.text, style = MaterialTheme.typography.bodyLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${m.type.lowercase()} · ${m.source}", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.setMemoryPinned(m, !m.pinned) }) {
                            Icon(Icons.Rounded.PushPin, contentDescription = if (m.pinned) "Unpin" else "Pin",
                                tint = if (m.pinned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = m.enabled, onCheckedChange = { vm.setMemoryEnabled(m, it) })
                        IconButton(onClick = { vm.deleteMemory(m.id) }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete memory") }
                    }
                }
            }
        }
    }
    if (confirmAll) AlertDialog(
        onDismissRequest = { confirmAll = false },
        title = { Text("Forget everything?") },
        text = { Text("This deletes all saved memories from this phone. It can't be undone.") },
        confirmButton = { TextButton(onClick = { vm.forgetAllMemory(); confirmAll = false }) { Text("Forget") } },
        dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Keep") } },
    )
}

@Composable
fun ActivityScreen(onBack: () -> Unit, vm: ManageViewModel = hiltViewModel()) {
    val log by vm.auditLog.collectAsStateWithLifecycle()
    ScreenFrame("Activity", onBack, actions = { TextButton(onClick = { vm.clearAudit() }, enabled = log.isNotEmpty()) { Text("Clear") } }) {
        Text("Every action, and what was sent to the AI, is listed here.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(log, key = { it.id }) { a ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(a.title.lines().first(), style = MaterialTheme.typography.titleMedium)
                    Text(a.detail, style = MaterialTheme.typography.bodyMedium)
                    Text("${a.kind} · ${a.status} · ${DateUtils.getRelativeTimeSpanString(a.ts)}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
