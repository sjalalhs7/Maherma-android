package com.maherma.app.ui.home

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maherma.app.domain.model.ActionStatus
import com.maherma.app.domain.model.ItemKind
import com.maherma.app.ui.AssistantViewModel
import com.maherma.app.ui.components.ActionCard
import com.maherma.app.ui.components.GlassCard
import com.maherma.app.ui.components.VoiceOrb
import com.maherma.app.ui.components.openAppSettings
import com.maherma.app.ui.components.rememberPermissionGrant
import com.maherma.app.voice.SessionEvent
import com.maherma.app.voice.SessionState
import kotlinx.coroutines.launch
import java.time.LocalTime

private val menu = listOf(
    "Conversation" to "conversation", "Tasks & reminders" to "tasks", "Notes" to "notes",
    "Memory" to "memory", "Activity" to "activity", "Settings" to "settings",
)

@Composable
fun HomeScreen(vm: AssistantViewModel, onNavigate: (String) -> Unit) {
    val session = vm.session
    val state by session.state.collectAsStateWithLifecycle()
    val partial by session.partial.collectAsStateWithLifecycle()
    val live by session.liveReply.collectAsStateWithLifecycle()
    val level by session.level.collectAsStateWithLifecycle()
    val online by session.online.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val tasks by vm.openTasks.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showMicWhy by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) session.startListening()
        else scope.launch {
            if (snackbar.showSnackbar("Microphone is off. You can type instead.", "Settings") == SnackbarResult.ActionPerformed) openAppSettings(ctx)
        }
    }
    val grant = rememberPermissionGrant { id, ok -> session.permissionResult(id, ok) }

    LaunchedEffect(Unit) {
        session.events.collect { e ->
            when (e) {
                SessionEvent.NeedMicPermission -> showMicWhy = true
                is SessionEvent.Notice -> snackbar.showSnackbar(e.text)
            }
        }
    }

    val pending = items.filter {
        it.kind == ItemKind.ACTION && (it.status == ActionStatus.AWAITING_CONFIRM || it.status == ActionStatus.NEEDS_PERMISSION)
    }
    val last = items.lastOrNull()
    val caption = when {
        state == SessionState.LISTENING -> partial.ifBlank { "Listening…" }
        live != null -> live!!
        last?.kind == ItemKind.ASSISTANT -> last.text
        else -> "Tap the orb and say what you need."
    }
    val hour = remember { LocalTime.now().hour }
    val greeting = when { hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(greeting, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                if (!online) Icon(Icons.Rounded.CloudOff, contentDescription = "Offline", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    menu.forEach { (label, route) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { menuOpen = false; onNavigate(route) })
                    }
                }
            }

            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(pending, key = { it.id }) { card ->
                    ActionCard(card,
                        onConfirm = { session.confirm(card.id) },
                        onCancel = { session.cancelAction(card.id) },
                        onGrant = { perm -> grant(card.id, perm) })
                }
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text("Today", style = MaterialTheme.typography.titleMedium)
                        if (tasks.isEmpty()) Text("Nothing open. Say “remind me to…”.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        tasks.take(3).forEach { t ->
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                t.dueAt?.let {
                                    Text(DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                if (recent.isNotEmpty()) item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text("Recent actions", style = MaterialTheme.typography.titleMedium)
                        recent.forEach { a ->
                            Text("${if (a.status == "OK") "✓" else "•"} ${a.title.lines().first()}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            Text(
                caption, style = MaterialTheme.typography.bodyLarge, maxLines = 5, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            val chips = last?.takeIf { it.kind == ItemKind.ASSISTANT }?.chips.orEmpty()
            if (chips.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(chips.size) { i -> SuggestionChip(onClick = { session.sendText(chips[i]) }, label = { Text(chips[i]) }) }
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                VoiceOrb(
                    state = state, level = level, online = online, awaitingConfirm = pending.isNotEmpty(),
                    onPress = { session.orbPress() }, onRelease = { started, ms -> session.orbRelease(started, ms) },
                )
            }
            OutlinedTextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                placeholder = { Text("Type instead…") }, maxLines = 3, shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { session.sendText(input); input = "" }),
                trailingIcon = {
                    IconButton(onClick = { session.sendText(input); input = "" }, enabled = input.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
                    }
                },
            )
        }
    }

    if (showMicWhy) AlertDialog(
        onDismissRequest = { showMicWhy = false },
        title = { Text("Microphone access") },
        text = { Text("Maherma listens only after you tap the orb (or while hands-free is on). Android's speech service turns audio into text, and that text is sent to your chosen AI provider. You can always type instead.") },
        confirmButton = { TextButton(onClick = { showMicWhy = false; micLauncher.launch(android.Manifest.permission.RECORD_AUDIO) }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { showMicWhy = false }) { Text("Not now") } },
    )
}
