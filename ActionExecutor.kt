package com.maherma.app.device

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import com.maherma.app.domain.model.Channel
import com.maherma.app.domain.model.ConnectivityPanel
import com.maherma.app.domain.model.PhoneAction
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface Outcome {
    data class Success(val message: String) : Outcome
    data class Failure(val message: String) : Outcome
    data class NeedsPermission(val permission: String, val rationale: String) : Outcome
    data class Ambiguous(val question: String, val options: List<String>) : Outcome
}

@Singleton
class ActionExecutor @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val contacts: ContactResolver,
    private val apps: AppResolver,
) {
    suspend fun execute(a: PhoneAction): Outcome = try {
        when (a) {
            is PhoneAction.OpenApp -> openApp(a)
            is PhoneAction.Dial -> withContact(a.contact) { m ->
                if (launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(m.number)))))
                    Outcome.Success("Dialer opened for ${m.name}. Press call to connect.")
                else Outcome.Failure("This device has no dialer app.")
            }
            is PhoneAction.SetTimer -> {
                val i = Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, a.seconds)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, a.label)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                if (launch(i)) Outcome.Success("Timer started.") else Outcome.Failure("No clock app can handle timers on this device.")
            }
            is PhoneAction.SetAlarm -> {
                val i = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, a.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, a.minute)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, a.label)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                if (launch(i)) Outcome.Success("Alarm set for %02d:%02d.".format(a.hour, a.minute))
                else Outcome.Failure("No clock app can handle alarms on this device.")
            }
            is PhoneAction.AddEvent -> addEvent(a)
            is PhoneAction.LookupContact -> lookup(a)
            is PhoneAction.Flashlight -> flashlight(a.on)
            is PhoneAction.SetVolume -> {
                val am = ctx.getSystemService(AudioManager::class.java)
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, a.percent * max / 100, AudioManager.FLAG_SHOW_UI)
                Outcome.Success("Media volume set to ${a.percent}%.")
            }
            is PhoneAction.OpenPanel -> panel(a.panel)
            is PhoneAction.SendMessage -> sendMessage(a)
        }
    } catch (e: SecurityException) {
        Outcome.Failure("Android or your phone maker blocked this action.")
    } catch (e: Exception) {
        Outcome.Failure("Couldn't complete that: ${e.localizedMessage ?: "unknown error"}")
    }

    // ---- individual actions ----
    private fun openApp(a: PhoneAction.OpenApp): Outcome = when (val r = apps.resolve(a.appName)) {
        is AppResolver.Result.Found -> {
            val i = ctx.packageManager.getLaunchIntentForPackage(r.app.pkg)
            if (i != null && launch(i)) Outcome.Success("Opened ${r.app.label}.")
            else Outcome.Failure("Couldn't open ${r.app.label}.")
        }
        is AppResolver.Result.Multiple -> Outcome.Ambiguous("Which app did you mean?", r.apps.map { "Open ${it.label}" })
        AppResolver.Result.None -> Outcome.Failure("I couldn't find an app called “${a.appName}”.")
    }

    private suspend fun withContact(query: String, onFound: (ContactMatch) -> Outcome): Outcome =
        when (val r = contacts.resolve(query)) {
            is ContactResult.Found -> onFound(r.match)
            is ContactResult.Multiple -> Outcome.Ambiguous(
                "I found more than one match for “$query”. Which one?",
                r.matches.map { "${it.name} – ${it.number}" },
            )
            ContactResult.NotFound -> Outcome.Failure("No contact matches “$query”.")
            ContactResult.NoPermission -> Outcome.NeedsPermission(
                Manifest.permission.READ_CONTACTS,
                "Halcyn looks names up in your contacts so you can say “call Ayesha”. Contacts are read on this phone only when you ask for a name.",
            )
        }

    private suspend fun lookup(a: PhoneAction.LookupContact): Outcome = when (val r = contacts.resolve(a.query)) {
        is ContactResult.Found -> Outcome.Success("${r.match.name}: ${r.match.number}")
        is ContactResult.Multiple -> Outcome.Success(r.matches.joinToString("\n") { "${it.name}: ${it.number}" })
        ContactResult.NotFound -> Outcome.Failure("No contact matches “${a.query}”.")
        ContactResult.NoPermission -> Outcome.NeedsPermission(
            Manifest.permission.READ_CONTACTS, "Halcyn needs Contacts access to look up names. It stays on this phone."
        )
    }

    private fun addEvent(a: PhoneAction.AddEvent): Outcome {
        val i = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, a.title)
        a.startIso?.let { iso ->
            runCatching {
                val begin = LocalDateTime.parse(iso).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 60 * 60 * 1000)
            }
        }
        return if (launch(i)) Outcome.Success("Calendar opened with “${a.title}”. Save it there to confirm.")
        else Outcome.Failure("No calendar app found.")
    }

    private fun flashlight(on: Boolean): Outcome {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return Outcome.Failure("This device has no flashlight.")
        cm.setTorchMode(id, on)
        return Outcome.Success(if (on) "Flashlight on." else "Flashlight off.")
    }

    private fun panel(p: ConnectivityPanel): Outcome {
        val q = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val action = when (p) {
            ConnectivityPanel.WIFI -> if (q) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIFI_SETTINGS
            ConnectivityPanel.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
            ConnectivityPanel.VOLUME -> if (q) Settings.Panel.ACTION_VOLUME else Settings.ACTION_SOUND_SETTINGS
        }
        return if (launch(Intent(action))) Outcome.Success("Opened. Android needs you to flip the switch yourself.")
        else Outcome.Failure("This device can't open that screen.")
    }

    private suspend fun sendMessage(a: PhoneAction.SendMessage): Outcome = withContact(a.recipient) { m ->
        val number = toE164(m.number)
        when (a.channel) {
            Channel.SMS -> {
                val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)))
                    .putExtra("sms_body", a.body)
                if (launch(i)) Outcome.Success("Draft ready for ${m.name} in your messaging app. Tap Send there.")
                else Outcome.Failure("No SMS app found.")
            }
            Channel.WHATSAPP -> {
                val digits = number.filter { it.isDigit() }
                val uri = Uri.parse("https://wa.me/$digits?text=" + Uri.encode(a.body))
                val ok = launch(Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp")) ||
                    launch(Intent(Intent.ACTION_VIEW, uri))
                if (ok) Outcome.Success("Draft ready for ${m.name} in WhatsApp. Tap Send there.")
                else Outcome.Failure("WhatsApp isn't installed and no browser could open the link.")
            }
        }
    }

    // ---- helpers ----
    private fun launch(i: Intent): Boolean = try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    private fun toE164(raw: String): String {
        val tm = ctx.getSystemService(TelephonyManager::class.java)
        val iso = listOf(tm?.networkCountryIso, tm?.simCountryIso, Locale.getDefault().country)
            .firstOrNull { !it.isNullOrBlank() }.orEmpty().uppercase()
        return PhoneNumberUtils.formatNumberToE164(raw, iso) ?: PhoneNumberUtils.normalizeNumber(raw)
    }
}
