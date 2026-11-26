package com.maherma.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maherma.app.domain.model.ActionStatus
import com.maherma.app.domain.model.ChatItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp), modifier = Modifier.widthIn(max = 320.dp),
        ) { Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
fun AssistantBubble(text: String, chips: List<String>, onChip: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp), modifier = Modifier.widthIn(max = 340.dp),
            ) { Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge) }
        }
        if (chips.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(chips.size) { i -> SuggestionChip(onClick = { onChip(chips[i]) }, label = { Text(chips[i]) }) }
        }
    }
}

@Composable
fun ErrorCard(text: String, canRetry: Boolean, onRetry: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
        if (canRetry) TextButton(onClick = onRetry) { Text("Retry", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun ActionCard(item: ChatItem, onConfirm: () -> Unit, onCancel: () -> Unit, onGrant: (String) -> Unit) {
    val status = item.status ?: ActionStatus.DONE
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(statusIcon(status), contentDescription = null, tint = statusTint(status))
            Text(item.text, style = MaterialTheme.typography.titleMedium)
        }
        Text(statusLabel(status), style = MaterialTheme.typography.labelLarge, color = statusTint(status))
        when (status) {
            ActionStatus.NEEDS_PERMISSION -> {
                val parts = (item.detail ?: "").split("|", limit = 2)
                Text(parts.getOrNull(1).orEmpty(), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCancel) { Text("Not now") }
                    Button(onClick = { onGrant(parts[0]) }) { Text("Allow") }
                }
            }
            ActionStatus.AWAITING_CONFIRM -> {
                item.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    Button(onClick = onConfirm) { Text("Confirm") }
                }
            }
            else -> item.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** Rich card created by the ui_show_card tool (lists, steps, plans, drafts, links). */
@Composable
fun InfoCard(item: ChatItem) {
    val root = remember(item.payload) { runCatching { Json.parseToJsonElement(item.payload.orEmpty()).jsonObject }.getOrNull() }
    val type = root?.get("type")?.jsonPrimitive?.contentOrNull ?: "SUMMARY"
    val rows = root?.get("items")?.jsonArray?.mapNotNull { it as? JsonObject }.orEmpty()
    val uri = LocalUriHandler.current
    GlassCard(Modifier.fillMaxWidth()) {
        Text(item.text, style = MaterialTheme.typography.titleMedium)
        rows.forEachIndexed { i, r ->
            val title = r["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val sub = r["subtitle"]?.jsonPrimitive?.contentOrNull
            val url = r["url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("https://") }
            Row(
                Modifier.fillMaxWidth().then(if (url != null) Modifier.clickable { runCatching { uri.openUri(url) } } else Modifier),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(if (type == "STEPS") "${i + 1}." else "•", fontWeight = FontWeight.Bold)
                Column {
                    Text(title, style = MaterialTheme.typography.bodyLarge,
                        color = if (url != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Returns a function that launches a runtime-permission request and reports (cardId, granted) back. */
@Composable
fun rememberPermissionGrant(onResult: (Long, Boolean) -> Unit): (Long, String) -> Unit {
    var cardId by remember { mutableLongStateOf(-1L) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult(cardId, it) }
    return { id, perm -> cardId = id; launcher.launch(perm) }
}

fun openAppSettings(ctx: Context) {
    ctx.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun statusIcon(s: ActionStatus): ImageVector = when (s) {
    ActionStatus.AWAITING_CONFIRM, ActionStatus.NEEDS_INPUT -> Icons.Rounded.TouchApp
    ActionStatus.RUNNING -> Icons.Rounded.HourglassTop
    ActionStatus.NEEDS_PERMISSION -> Icons.Rounded.Lock
    ActionStatus.DONE -> Icons.Rounded.CheckCircle
    ActionStatus.FAILED -> Icons.Rounded.ErrorOutline
    ActionStatus.CANCELLED -> Icons.Rounded.Block
}

private fun statusLabel(s: ActionStatus) = when (s) {
    ActionStatus.AWAITING_CONFIRM -> "Needs your confirmation"
    ActionStatus.RUNNING -> "Running…"
    ActionStatus.NEEDS_PERMISSION -> "Permission needed"
    ActionStatus.NEEDS_INPUT -> "Needs your choice"
    ActionStatus.DONE -> "Done"
    ActionStatus.FAILED -> "Didn't work"
    ActionStatus.CANCELLED -> "Cancelled"
}

@Composable
private fun statusTint(s: ActionStatus) = when (s) {
    ActionStatus.DONE -> MaterialTheme.colorScheme.primary
    ActionStatus.FAILED -> MaterialTheme.colorScheme.error
    ActionStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.secondary
}
