package com.maherma.app.orchestrator

import com.maherma.app.data.prefs.AppSettings
import com.maherma.app.data.prefs.SettingsStore
import com.maherma.app.data.repo.AuditRepository
import com.maherma.app.data.repo.ConversationRepository
import com.maherma.app.data.repo.MemoryRepository
import com.maherma.app.domain.model.ItemKind
import com.maherma.app.domain.model.Turn
import com.maherma.app.llm.AiError
import com.maherma.app.llm.AiException
import com.maherma.app.llm.Content
import com.maherma.app.llm.GeminiClient
import com.maherma.app.llm.LlmEvent
import com.maherma.app.llm.Part
import com.maherma.app.tools.ToolRegistry
import com.maherma.app.tools.ToolRouter
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

sealed interface OrchEvent {
    /** A piece of the spoken/on-screen reply. */
    data class Speech(val text: String) : OrchEvent
    /** A tool is about to run. */
    data class Working(val tool: String) : OrchEvent
}

/** Runs one assistant turn: context -> streamed LLM -> tool calls (max 4 hops) -> persisted reply. */
@Singleton
class Orchestrator @Inject constructor(
    private val llm: GeminiClient,
    private val registry: ToolRegistry,
    private val router: ToolRouter,
    private val memory: MemoryRepository,
    private val repo: ConversationRepository,
    private val settings: SettingsStore,
    private val audit: AuditRepository,
    private val local: LocalRouter,
) {
    fun run(threadId: Long): Flow<OrchEvent> = flow {
        val cfg = settings.settings.first()
        val history = repo.history(threadId, 24)
        val lastUser = history.lastOrNull { it.role == "user" }?.text.orEmpty()
        if (cfg.apiKey.isBlank()) {
            emitAll(offline(threadId, lastUser, "I need an API key for that. Add one in Settings. "))
            return@flow
        }
        try {
            emitAll(online(threadId, cfg, history, lastUser))
        } catch (e: AiException) {
            if (e.error == AiError.NETWORK && local.route(lastUser) != null) emitAll(offline(threadId, lastUser, ""))
            else throw e
        }
    }

    private fun online(threadId: Long, cfg: AppSettings, history: List<Turn>, lastUser: String): Flow<OrchEvent> = flow {
        val memories = if (cfg.memoryEnabled) memory.relevant(lastUser, 5) else emptyList()
        val tools = registry.declarations()
        audit.log("LLM", "Sent to ${cfg.model}", "${history.size} turns of history, ${memories.size} memories, ${tools.size} tools", "OK")
        val system = Prompts.system(ZonedDateTime.now(), memories.map { it.text })
        val contents = normalize(history).toMutableList()
        val unsaved = StringBuilder()
        var anySpeech = false
        var anyTool = false

        suspend fun save() {
            val t = unsaved.toString().trim()
            unsaved.clear()
            if (t.isNotEmpty()) withContext(NonCancellable) { repo.add(threadId, ItemKind.ASSISTANT, t) }
        }

        try {
            var hops = 0
            while (true) {
                val calls = mutableListOf<LlmEvent.ToolCall>()
                val turnText = StringBuilder()
                llm.stream(cfg, system, contents, tools).collect { e ->
                    when (e) {
                        is LlmEvent.Text -> {
                            turnText.append(e.text); unsaved.append(e.text); anySpeech = true
                            emit(OrchEvent.Speech(e.text))
                        }
                        is LlmEvent.ToolCall -> calls += e
                    }
                }
                if (calls.isEmpty() || ++hops > MAX_HOPS) break
                anyTool = true
                save()   // keep chat order: spoken lead-in, then action cards
                contents += Content("model", buildList {
                    if (turnText.isNotBlank()) add(Part.Text(turnText.toString()))
                    calls.forEach { add(Part.Raw(it.raw)) }
                })
                val responses = calls.map { c ->
                    emit(OrchEvent.Working(c.name))
                    Part.FunctionResponse(c.name, router.invoke(threadId, c.name, c.args).toJson())
                }
                contents += Content("user", responses)
                if (anySpeech) { unsaved.append(' '); emit(OrchEvent.Speech(" ")) }
            }
            if (!anySpeech && !anyTool) throw AiException(AiError.EMPTY, "The assistant returned an empty reply. Try again.")
        } finally {
            save()
        }
    }

    private fun offline(threadId: Long, text: String, prefix: String): Flow<OrchEvent> = flow {
        val route = local.route(text)
        val msg = if (route == null) {
            "${prefix}Offline I can only handle basics like timers, reminders, notes, opening apps and the flashlight."
        } else {
            emit(OrchEvent.Working(route.first))
            router.invoke(threadId, route.first, route.second).let {
                if (it.status == "AWAITING_CONFIRMATION") "Ready. Confirm on screen." else it.message
            }
        }
        repo.add(threadId, ItemKind.ASSISTANT, msg)
        emit(OrchEvent.Speech(msg))
    }

    /** Merge consecutive same-role turns and make sure the conversation starts with the user. */
    private fun normalize(h: List<Turn>): List<Content> {
        val out = mutableListOf<Turn>()
        for (t in h) {
            if (t.text.isBlank()) continue
            if (out.isNotEmpty() && out.last().role == t.role) out[out.lastIndex] = out.last().copy(text = out.last().text + "\n" + t.text)
            else out += t
        }
        while (out.isNotEmpty() && out.first().role != "user") out.removeAt(0)
        return out.map { Content(it.role, listOf(Part.Text(it.text))) }
    }

    private companion object { const val MAX_HOPS = 4 }
}
