package com.maherma.app.orchestrator

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Tiny deterministic router used when there is no API key or no network. Maps text to a tool call. */
@Singleton
class LocalRouter @Inject constructor() {
    private val open = Regex("^(?:open|launch|start)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val call = Regex("^call\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val timer = Regex("timer\\s+(?:for\\s+)?(\\d+)\\s*(seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h)\\b", RegexOption.IGNORE_CASE)
    private val torch = Regex("(?:turn|switch)\\s+(on|off)\\s+(?:the\\s+)?(?:flashlight|torch)", RegexOption.IGNORE_CASE)
    private val remind = Regex("^remind me (?:to )?(.+?) in (\\d+)\\s*(minutes?|mins?|m|hours?|hrs?|h)\\b", RegexOption.IGNORE_CASE)
    private val note = Regex("^(?:take a note|note|save a note)[:\\s]+(.+)$", RegexOption.IGNORE_CASE)
    private val tasks = Regex("(?:show|list|what are) (?:me )?(?:my )?(?:open )?(?:tasks|reminders)", RegexOption.IGNORE_CASE)

    fun route(text: String): Pair<String, JsonObject>? {
        val t = text.trim()
        remind.find(t)?.let { m ->
            val n = m.groupValues[2].toLong()
            val unit = m.groupValues[3].lowercase()
            val due = LocalDateTime.now().plus(if (unit.startsWith("h")) n * 60 else n, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES)
            return "tasks_create" to buildJsonObject { put("title", m.groupValues[1].trim()); put("due", due.toString()) }
        }
        timer.find(t)?.let { m ->
            val n = m.groupValues[1].toInt()
            val u = m.groupValues[2].lowercase()
            val secs = when { u.startsWith("h") -> n * 3600; u.startsWith("m") -> n * 60; else -> n }
            return "device_set_timer" to buildJsonObject { put("seconds", secs) }
        }
        torch.find(t)?.let { return "device_flashlight" to buildJsonObject { put("on", it.groupValues[1].equals("on", true)) } }
        note.find(t)?.let { return "notes_create" to buildJsonObject { put("body", it.groupValues[1].trim()) } }
        if (tasks.containsMatchIn(t)) return "tasks_list" to buildJsonObject { }
        call.find(t)?.let { return "comms_call" to buildJsonObject { put("contact", it.groupValues[1].trim()) } }
        open.find(t)?.let { return "device_open_app" to buildJsonObject { put("app_name", it.groupValues[1].trim()) } }
        return null
    }
}
