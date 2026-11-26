package com.maherma.app.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AcousticEchoCanceler
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maherma.app.ui.components.GlassCard
import com.maherma.app.ui.components.openAppSettings
import com.maherma.app.ui.manage.ManageViewModel
import com.maherma.app.ui.manage.ScreenFrame

private val locales = listOf("" to "System", "en-US" to "English", "ur-PK" to "اردو", "hi-IN" to "हिन्दी")

private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
private fun exactOk(ctx: Context) = Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

@Composable
fun SettingsScreen(onBack: () -> Unit, vm: ManageViewModel = hiltViewModel()) {
    val cfg by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var key by remember(cfg.apiKey) { mutableStateOf(cfg.apiKey) }
    var model by remember(cfg.model) { mutableStateOf(cfg.model) }
    var wipe by remember { mutableStateOf(false) }

    var mic by remember { mutableStateOf(granted(ctx, Manifest.permission.RECORD_AUDIO)) }
    var contacts by remember { mutableStateOf(granted(ctx, Manifest.permission.READ_CONTACTS)) }
    var notif by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS)) }
    var exact by remember { mutableStateOf(exactOk(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        mic = granted(ctx, Manifest.permission.RECORD_AUDIO); contacts = granted(ctx, Manifest.permission.READ_CONTACTS)
        notif = Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS); exact = exactOk(ctx)
    }
    val micL = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { mic = it }
    val conL = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { contacts = it }
    val notL = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notif = it }
    fun launch(i: Intent) { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    ScreenFrame("Settings", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("AI provider", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(key, { key = it }, label = { Text("Gemini API key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("The key is encrypted with the Android Keystore and sent only to Google's Gemini API. Your key is billed for usage.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { vm.saveBrain(key, model) }) { Text("Save") }
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Text("Voice", style = MaterialTheme.typography.titleMedium)
                Text("Language for listening and speaking", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    locales.forEach { (tag, label) -> FilterChip(selected = cfg.voiceLocale == tag, onClick = { vm.setLocale(tag) }, label = { Text(label) }) }
                }
                Text("Roman Urdu: pick English; results depend on the device's recognizer.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                ToggleRow("Speak replies", "Short spoken answer; details stay on screen.", cfg.speakReplies) { vm.setSpeak(it) }
                ToggleRow("Hands-free conversation", "Keep listening after each reply until you go quiet.", cfg.handsFree) { vm.setHandsFree(it) }
                val aec = remember { AcousticEchoCanceler.isAvailable() }
                ToggleRow(
                    "Interrupt by voice",
                    if (aec) "Talk over Maherma to stop it." else "Not available on this device (no echo cancellation). Tap the orb to interrupt.",
                    cfg.voiceBargeIn && aec, enabled = aec,
                ) { vm.setBarge(it) }
                ToggleRow("Prefer on-device recognition", "Used when your phone offers it (Android 13+). Works offline.", cfg.preferOnDevice) { vm.setOnDevice(it) }
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Text("Permissions", style = MaterialTheme.typography.titleMedium)
                PermRow("Microphone", "Only after you tap the orb.", mic, "Allow") {
                    if (Build.VERSION.SDK_INT >= 23) micL.launch(Manifest.permission.RECORD_AUDIO)
                }
                PermRow("Contacts", "To call or message a name. Not needed for numbers.", contacts, "Allow") { conL.launch(Manifest.permission.READ_CONTACTS) }
                PermRow("Notifications", "To show reminders when they're due.", notif, "Allow") {
                    if (Build.VERSION.SDK_INT >= 33) notL.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                PermRow("Alarms & reminders", "Exact reminder times. Without it reminders may be a few minutes late.", exact, "Open") {
                    if (Build.VERSION.SDK_INT >= 31) launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                }
                OutlinedButton(onClick = { openAppSettings(ctx) }) { Text("Open app settings") }
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Text("Background reliability", style = MaterialTheme.typography.titleMedium)
                Text("Reminders survive reboots. Some phone makers stop background work aggressively; if reminders arrive late, exclude Maherma from battery optimization.",
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) { Text("Battery settings") }
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Text("Privacy", style = MaterialTheme.typography.titleMedium)
                Text("Voice is turned into text by Android's speech service. Text, the memories you allow, and tool results go to your AI provider. The Activity screen shows each send. Calls and messages always need your tap.",
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { wipe = true }) { Text("Delete all my data") }
            }
        }
    }

    if (wipe) AlertDialog(
        onDismissRequest = { wipe = false },
        title = { Text("Delete everything?") },
        text = { Text("Conversations, tasks, notes, memory and activity are removed from this phone and reminders are cancelled. It can't be undone.") },
        confirmButton = { TextButton(onClick = { vm.wipeEverything(); wipe = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { wipe = false }) { Text("Keep") } },
    )
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun PermRow(title: String, why: String, ok: Boolean, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (ok) Text("On", color = MaterialTheme.colorScheme.primary) else OutlinedButton(onClick = onAction) { Text(action) }
    }
}
