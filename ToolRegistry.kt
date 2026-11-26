package com.maherma.app.tools

import com.maherma.app.data.repo.ConversationRepository
import com.maherma.app.data.repo.MemoryRepository
import com.maherma.app.data.repo.NoteRepository
import com.maherma.app.data.repo.TaskRepository
import com.maherma.app.device.ActionExecutor
import com.maherma.app.device.Outcome
import com.maherma.app.domain.model.Channel
import com.maherma.app.domain.model.ConnectivityPanel
import com.maherma.app.domain.model.ItemKind
import com.maherma.app.domain.model.PhoneAction
import com.maherma.app.domain.model.summary
import com.maherma.app.llm.ToolDecl
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.addJsonObject
import java.text.DateFormat
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

private val SENSITIVE = Regex(
    "(?i)\\b(password|passcode|pin|otp|ssn|salary|diagnos\\w*|medicat\\w*|prescription|bank|iban|credit card|religio\\w*|politic\\w*)\\b"
)

@Singleton
class ToolRegistry @Inject constructor(
    private val executor: ActionExecutor,
    private val tasks: TaskRepository,
    private val notes: NoteRepository,
    private val memory: MemoryRepository,
    private val conversations: ConversationRepository,
) {
    private val list: List<ToolDef> = buildList {
        // ---- device ----
        add(tool("device_open_app", "Open an installed app by its name.",
            Sch.obj(mapOf("app_name" to Sch.str("App name as the user said it")), listOf("app_name")), Tier.T1,
            { "Open ${it.str("app_name")}" }) { a, _ ->
            val n = a.str("app_name") ?: return@tool bad("app_name")
            exec(PhoneAction.OpenApp(n))
        })
        add(tool("device_set_timer", "Start a countdown timer in the clock app.",
            Sch.obj(mapOf("seconds" to Sch.int("1 to 86400"), "label" to Sch.str("Optional label")), listOf("seconds")), Tier.T1,
            { "Timer for ${it.int("seconds")} s" }) { a, _ ->
            val s = a.int("seconds")?.takeIf { it in 1..86_400 } ?: return@tool bad("seconds (1..86400)")
            exec(PhoneAction.SetTimer(s, a.str("label").orEmpty()))
        })
        add(tool("device_set_alarm", "Set a one-time alarm in the clock app.",
            Sch.obj(mapOf("hour" to Sch.int("0 to 23"), "minute" to Sch.int("0 to 59"), "label" to Sch.str("Optional label")),
                listOf("hour", "minute")), Tier.T1,
            { "Alarm %02d:%02d".format(it.int("hour") ?: 0, it.int("minute") ?: 0) }) { a, _ ->
            val h = a.int("hour")?.takeIf { it in 0..23 } ?: return@tool bad("hour")
            val m = a.int("minute")?.takeIf { it in 0..59 } ?: return@tool bad("minute")
            exec(PhoneAction.SetAlarm(h, m, a.str("label").orEmpty()))
        })
        add(tool("device_flashlight", "Turn the flashlight on or off.",
            Sch.obj(mapOf("on" to Sch.bool("true = on")), listOf("on")), Tier.T1,
            { "Flashlight ${if (it.bool("on") == true) "on" else "off"}" }) { a, _ ->
            exec(PhoneAction.Flashlight(a.bool("on") ?: return@tool bad("on")))
        })
        add(tool("device_volume", "Set media volume.",
            Sch.obj(mapOf("percent" to Sch.int("0 to 100")), listOf("percent")), Tier.T1,
            { "Volume ${it.int("percent")}%" }) { a, _ ->
            exec(PhoneAction.SetVolume(a.int("percent")?.takeIf { it in 0..100 } ?: return@tool bad("percent")))
        })
        add(tool("device_open_panel", "Open a system panel (Wi-Fi/internet, Bluetooth, volume). The user flips the switch.",
            Sch.obj(mapOf("panel" to Sch.enum("Which panel", "WIFI", "BLUETOOTH", "VOLUME")), listOf("panel")), Tier.T0,
            { "Open ${it.str("panel")} panel" }) { a, _ ->
            val p = runCatching { ConnectivityPanel.valueOf(a.str("panel").orEmpty().uppercase()) }.getOrNull() ?: return@tool bad("panel")
            exec(PhoneAction.OpenPanel(p))
        })
        add(tool("contacts_lookup", "Find a contact's phone number by name.",
            Sch.obj(mapOf("query" to Sch.str("Name")), listOf("query")), Tier.T0,
            { "Look up ${it.str("query")}" }) { a, _ ->
            exec(PhoneAction.LookupContact(a.str("query") ?: return@tool bad("query")))
        })
        add(tool("calendar_create", "Open the calendar with a new event prefilled; the user saves it there.",
            Sch.obj(mapOf("title" to Sch.str("Event title"), "start" to Sch.str("Local start time yyyy-MM-ddTHH:mm, optional")), listOf("title")),
            Tier.T1, { "Calendar event “${it.str("title")}”" }) { a, _ ->
            exec(PhoneAction.AddEvent(a.str("title") ?: return@tool bad("title"), a.str("start")))
        })

        // ---- communication (confirm first) ----
        add(tool("comms_send", "Prepare a text message. The user must confirm on screen, then taps Send in the messaging app.",
            Sch.obj(mapOf(
                "channel" to Sch.enum("SMS, or WHATSAPP only if the user said WhatsApp", "SMS", "WHATSAPP"),
                "recipient" to Sch.str("Contact name or phone number exactly as given"),
                "body" to Sch.str("Final message text in the user's language and script"),
            ), listOf("channel", "recipient", "body")), Tier.T2,
            { messageOf(it).summary() },
            note = "Opens your messaging app with this draft. Nothing is sent until you tap Send there.") { a, _ ->
            exec(messageOf(a))
        })
        add(tool("comms_call", "Prepare a phone call. The user must confirm on screen, then presses call in the dialer.",
            Sch.obj(mapOf("contact" to Sch.str("Name or number")), listOf("contact")), Tier.T2,
            { "Call ${it.str("contact")}" }, note = "Opens your dialer. You press call.") { a, _ ->
            exec(PhoneAction.Dial(a.str("contact") ?: return@tool bad("contact")))
        })

        // ---- tasks & reminders ----
        add(tool("tasks_create", "Create a task or reminder. If due is set, a notification fires at that time.",
            Sch.obj(mapOf(
                "title" to Sch.str("What to do"),
                "due" to Sch.str("Local time yyyy-MM-ddTHH:mm, optional"),
                "repeat" to Sch.enum("Optional repetition", "NONE", "DAILY", "WEEKLY"),
                "notes" to Sch.str("Optional details"),
            ), listOf("title")), Tier.T1, { "Task “${it.str("title")}”" }) { a, _ ->
            val title = a.str("title") ?: return@tool bad("title")
            val dueRaw = a.str("due")
            val due = dueRaw?.let { parseDue(it) ?: return@tool ToolResult.failed("I couldn't read that time. Use yyyy-MM-ddTHH:mm.") }
            if (due != null && due < System.currentTimeMillis()) return@tool ToolResult.failed("That time has already passed.")
            val repeat = a.str("repeat")?.uppercase()?.takeIf { it in setOf("DAILY", "WEEKLY") } ?: "NONE"
            val (task, exact) = tasks.create(title, due, repeat, a.str("notes").orEmpty())
            val msg = when {
                due == null -> "Task added."
                exact -> "Reminder set for ${fmt(due)}."
                else -> "Reminder set for about ${fmt(due)}. Timing is approximate until Alarms & reminders is allowed in Settings."
            }
            ToolResult.ok(msg, buildJsonObject { put("id", task.id) })
        })
        add(tool("tasks_list", "List open tasks.",
            Sch.obj(mapOf("scope" to Sch.enum("Which tasks", "TODAY", "UPCOMING", "OVERDUE", "ALL"))), Tier.T0,
            { "List ${it.str("scope") ?: "ALL"} tasks" }) { a, _ ->
            val now = System.currentTimeMillis()
            val endOfDay = java.time.LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val all = tasks.open()
            val picked = when (a.str("scope")?.uppercase()) {
                "TODAY" -> all.filter { it.dueAt != null && it.dueAt < endOfDay && it.dueAt >= now }
                "UPCOMING" -> all.filter { it.dueAt != null && it.dueAt >= endOfDay }
                "OVERDUE" -> all.filter { it.dueAt != null && it.dueAt < now }
                else -> all
            }
            val arr = buildJsonArray {
                picked.take(30).forEach { t ->
                    addJsonObject { put("id", t.id); put("title", t.title); t.dueAt?.let { put("due", fmt(it)) } }
                }
            }
            ToolResult.ok("${picked.size} open task(s).", buildJsonObject { put("tasks", arr) })
        })
        add(tool("tasks_complete", "Mark a task done by id (from tasks_list).",
            Sch.obj(mapOf("id" to Sch.int("Task id")), listOf("id")), Tier.T1,
            { "Complete task ${it.int("id")}" }) { a, _ ->
            val id = a.int("id")?.toLong() ?: return@tool bad("id")
            if (tasks.complete(id)) ToolResult.ok("Marked done.") else ToolResult.failed("No task with that id.")
        })
        add(tool("tasks_delete", "Delete a task by id (from tasks_list).",
            Sch.obj(mapOf("id" to Sch.int("Task id")), listOf("id")), Tier.T3,
            { "Delete task “${it.int("id")?.toLong()?.let { id -> tasks.get(id)?.title } ?: it.int("id")}”" }) { a, _ ->
            val id = a.int("id")?.toLong() ?: return@tool bad("id")
            tasks.delete(id); ToolResult.ok("Task deleted.")
        })

        // ---- notes ----
        add(tool("notes_create", "Save a note.",
            Sch.obj(mapOf("body" to Sch.str("Note text"), "title" to Sch.str("Optional title")), listOf("body")), Tier.T1,
            { "Note “${(it.str("title") ?: it.str("body"))?.take(40)}”" }) { a, _ ->
            notes.create(a.str("title").orEmpty(), a.str("body") ?: return@tool bad("body")); ToolResult.ok("Note saved.")
        })
        add(tool("notes_search", "Search saved notes by keyword.",
            Sch.obj(mapOf("query" to Sch.str("Keyword")), listOf("query")), Tier.T0, { "Search notes: ${it.str("query")}" }) { a, _ ->
            val hits = notes.search(a.str("query") ?: return@tool bad("query"))
            val arr = buildJsonArray { hits.forEach { n -> addJsonObject { put("title", n.title); put("body", n.body.take(300)) } } }
            ToolResult.ok("${hits.size} note(s) found.", buildJsonObject { put("notes", arr) })
        })

        // ---- memory (user-controlled) ----
        add(ToolDef(
            name = "memory_save",
            description = "Save something to the user's memory. Use explicit=true only when the user asked to remember it. Otherwise explicit=false and the user is asked first.",
            params = Sch.obj(mapOf(
                "type" to Sch.enum("Kind of memory", "PREFERENCE", "ROUTINE", "PROJECT", "FACT"),
                "text" to Sch.str("What to remember, one short sentence"),
                "explicit" to Sch.bool("true if the user explicitly asked to remember it"),
            ), listOf("type", "text", "explicit")),
            tier = { a -> if (a.bool("explicit") == true && !SENSITIVE.containsMatchIn(a.str("text").orEmpty())) Tier.T1 else Tier.T2 },
            summarize = { "Remember: “${it.str("text")}”" },
            note = "Saved only on this phone. You can edit or delete it in Memory.",
        ) { a, _ ->
            val text = a.str("text") ?: return@ToolDef bad("text")
            val type = a.str("type")?.uppercase()?.takeIf { it in setOf("PREFERENCE", "ROUTINE", "PROJECT", "FACT") } ?: "FACT"
            memory.add(type, text, if (a.bool("explicit") == true) "explicit" else "suggested")
            ToolResult.ok("Saved to memory.")
        })
        add(tool("memory_forget", "Delete memories containing a phrase.",
            Sch.obj(mapOf("query" to Sch.str("Phrase to forget")), listOf("query")), Tier.T3,
            { "Forget memories containing “${it.str("query")}”" }) { a, _ ->
            val n = memory.forget(a.str("query") ?: return@tool bad("query")); ToolResult.ok("Forgot $n item(s).")
        })

        // ---- screen ----
        add(ToolDef(
            name = "ui_show_card",
            description = "Show a rich card on screen for lists, steps, plans, drafts, links or comparisons. Keep speech short and put depth here.",
            params = Sch.obj(mapOf(
                "type" to Sch.enum("Card type", "SUMMARY", "LIST", "STEPS", "LINK", "DRAFT", "PLAN", "COMPARISON"),
                "title" to Sch.str("Card title"),
                "items" to Sch.list("Rows", Sch.obj(mapOf(
                    "title" to Sch.str("Row text"), "subtitle" to Sch.str("Optional detail"), "url" to Sch.str("Optional https link"),
                ), listOf("title"))),
            ), listOf("type", "title", "items")),
            tier = { Tier.T0 }, summarize = { it.str("title").orEmpty() }, audited = false,
        ) { a, ctx ->
            val title = a.str("title") ?: return@ToolDef bad("title")
            if (a["items"]?.jsonArray?.all { it is JsonObject } != true) return@ToolDef bad("items")
            conversations.add(ctx.threadId, ItemKind.CARD, title, payload = a.toString())
            ToolResult.ok("Card shown.")
        })
    }

    private val byName = list.associateBy { it.name }
    fun get(name: String): ToolDef? = byName[name]
    fun declarations(): List<ToolDecl> = list.map { ToolDecl(it.name, it.description, it.params) }

    // ---- helpers ----
    private fun tool(
        name: String, desc: String, params: JsonObject?, tier: Tier,
        summary: suspend (JsonObject) -> String, note: String? = null,
        run: suspend (JsonObject, ToolCtx) -> ToolResult,
    ) = ToolDef(name, desc, params, { tier }, summary, note, true, run)

    private fun bad(field: String) = ToolResult.failed("Missing or invalid $field.")
    private suspend fun exec(a: PhoneAction): ToolResult = executor.execute(a).toResult()

    private fun messageOf(a: JsonObject) = PhoneAction.SendMessage(
        if (a.str("channel")?.uppercase() == "WHATSAPP") Channel.WHATSAPP else Channel.SMS,
        a.str("recipient").orEmpty(), a.str("body").orEmpty(),
    )

    private fun fmt(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

    private fun parseDue(s: String): Long? {
        val zone = ZoneId.systemDefault()
        return runCatching { LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
    }
}

fun Outcome.toResult(): ToolResult = when (this) {
    is Outcome.Success -> ToolResult.ok(message)
    is Outcome.Failure -> ToolResult.failed(message)
    is Outcome.NeedsPermission -> ToolResult("NEEDS_PERMISSION", rationale, permission = permission)
    is Outcome.Ambiguous -> ToolResult("AMBIGUOUS", question, options = options)
}
