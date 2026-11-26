package com.maherma.app.tools

import com.maherma.app.data.repo.AuditRepository
import com.maherma.app.data.repo.ConversationRepository
import com.maherma.app.domain.model.ActionStatus
import com.maherma.app.domain.model.ItemKind
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** Single choke point for every action: tier policy, confirmation cards, audit log. */
@Singleton
class ToolRouter @Inject constructor(
    private val registry: ToolRegistry,
    private val repo: ConversationRepository,
    private val audit: AuditRepository,
    private val json: Json,
) {
    @Serializable
    private data class Pending(val tool: String, val args: JsonObject)

    /** Called by the orchestrator for a model (or local-router) tool call. */
    suspend fun invoke(threadId: Long, name: String, args: JsonObject): ToolResult {
        val tool = registry.get(name) ?: return ToolResult.failed("Unknown tool $name.")
        return try {
            when (tool.tier(args)) {
                Tier.T2, Tier.T3 -> {
                    val summary = tool.summarize(args)
                    repo.add(threadId, ItemKind.ACTION, summary, ActionStatus.AWAITING_CONFIRM, encode(tool.name, args), tool.note)
                    audit.log("PROPOSED", summary, "Awaiting your confirmation", "PENDING")
                    ToolResult("AWAITING_CONFIRMATION", "Prepared. The user must tap Confirm on screen. Do not say it was done.")
                }
                else -> execute(threadId, null, tool, args)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolResult.failed("Invalid or failed call: ${e.localizedMessage ?: "error"}")
        }
    }

    /** User tapped Confirm (or Allow after a permission prompt) on a persisted card. */
    suspend fun confirm(cardId: Long) {
        val item = repo.get(cardId) ?: return
        if (item.status != ActionStatus.AWAITING_CONFIRM && item.status != ActionStatus.NEEDS_PERMISSION) return
        if (item.status == ActionStatus.AWAITING_CONFIRM && System.currentTimeMillis() - item.createdAt > EXPIRY_MS) {
            repo.update(cardId, ActionStatus.CANCELLED, "Expired. Ask again."); return
        }
        val p = runCatching { json.decodeFromString(Pending.serializer(), item.payload.orEmpty()) }.getOrNull()
        val tool = p?.let { registry.get(it.tool) }
        if (p == null || tool == null) { repo.update(cardId, ActionStatus.FAILED, "This action could not be restored."); return }
        repo.update(cardId, ActionStatus.RUNNING, null)
        execute(item.threadId, cardId, tool, p.args)
    }

    suspend fun cancel(cardId: Long) = repo.update(cardId, ActionStatus.CANCELLED, "Cancelled. Nothing was done.")
    suspend fun permissionDenied(cardId: Long) =
        repo.update(cardId, ActionStatus.FAILED, "Permission denied. Allow it in Settings, or give me the number directly.")

    private suspend fun execute(threadId: Long, cardId: Long?, tool: ToolDef, args: JsonObject): ToolResult {
        val r = try {
            tool.run(args, ToolCtx(threadId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolResult.failed("${tool.name} failed: ${e.localizedMessage ?: "error"}")
        }
        val summary = runCatching { tool.summarize(args) }.getOrDefault(tool.name)
        if (tool.audited) audit.log("TOOL", summary, r.message, r.status)
        when (r.status) {
            "OK" -> cardId?.let { repo.update(it, ActionStatus.DONE, r.message) }
            "NEEDS_PERMISSION" -> {
                val detail = "${r.permission}|${r.message}"
                if (cardId != null) repo.update(cardId, ActionStatus.NEEDS_PERMISSION, detail)
                else repo.add(threadId, ItemKind.ACTION, summary, ActionStatus.NEEDS_PERMISSION, encode(tool.name, args), detail)
            }
            "AMBIGUOUS" -> {
                cardId?.let { repo.update(it, ActionStatus.NEEDS_INPUT, "More than one match.") }
                repo.add(threadId, ItemKind.ASSISTANT, r.message, chips = r.options)
            }
            else -> cardId?.let { repo.update(it, ActionStatus.FAILED, r.message) }
        }
        return r
    }

    private fun encode(tool: String, args: JsonObject) = json.encodeToString(Pending.serializer(), Pending(tool, args))
    private companion object { const val EXPIRY_MS = 10 * 60_000L }
}
