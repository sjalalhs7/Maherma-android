package com.maherma.app.ui.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maherma.app.domain.model.ItemKind
import com.maherma.app.ui.AssistantViewModel
import com.maherma.app.ui.components.ActionCard
import com.maherma.app.ui.components.AssistantBubble
import com.maherma.app.ui.components.ErrorCard
import com.maherma.app.ui.components.InfoCard
import com.maherma.app.ui.components.UserBubble
import com.maherma.app.ui.components.rememberPermissionGrant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(vm: AssistantViewModel, onBack: () -> Unit) {
    val session = vm.session
    val items by vm.items.collectAsStateWithLifecycle()
    val live by session.liveReply.collectAsStateWithLifecycle()
    val threads by vm.threads.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var showThreads by remember { mutableStateOf(false) }
    val grant = rememberPermissionGrant { id, ok -> session.permissionResult(id, ok) }
    val listState = rememberLazyListState()
    val total = items.size + if (live != null) 1 else 0
    LaunchedEffect(total, live?.length) { if (total > 0) listState.scrollToItem(total - 1) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Conversation", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            actions = {
                IconButton(onClick = { showThreads = true }) { Icon(Icons.Rounded.History, contentDescription = "History") }
                IconButton(onClick = { session.newThread() }) { Icon(Icons.Rounded.Add, contentDescription = "New conversation") }
            },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(
                state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    when (item.kind) {
                        ItemKind.USER -> UserBubble(item.text)
                        ItemKind.ASSISTANT -> AssistantBubble(item.text, if (index == items.lastIndex) item.chips else emptyList()) { session.sendText(it) }
                        ItemKind.ACTION -> ActionCard(item,
                            onConfirm = { session.confirm(item.id) }, onCancel = { session.cancelAction(item.id) },
                            onGrant = { perm -> grant(item.id, perm) })
                        ItemKind.CARD -> InfoCard(item)
                        ItemKind.ERROR -> ErrorCard(item.text, canRetry = index == items.lastIndex) { session.retry(item.id) }
                    }
                }
                live?.let { s -> item { AssistantBubble(s, emptyList()) {} } }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("Message Maherma") }, maxLines = 5, shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { session.sendText(input); input = "" }),
                )
                IconButton(onClick = { session.sendText(input); input = "" }, enabled = input.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
                }
            }
        }
    }

    if (showThreads) AlertDialog(
        onDismissRequest = { showThreads = false },
        title = { Text("Conversations") },
        text = {
            if (threads.isEmpty()) Text("No conversations yet.")
            else LazyColumn {
                itemsIndexed(threads, key = { _, t -> t.id }) { _, t ->
                    ListItem(
                        modifier = Modifier.clickable { session.openThread(t.id); showThreads = false },
                        headlineContent = { Text(t.title, maxLines = 1) },
                        trailingContent = {
                            IconButton(onClick = { session.deleteThread(t.id) }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete") }
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { showThreads = false }) { Text("Close") } },
    )
}
