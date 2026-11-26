package com.maherma.app.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** T0 read-only · T1 reversible local · T2 leaves the app or contacts someone (tap to confirm) · T3 destructive (tap to confirm). */
enum class Tier { T0, T1, T2, T3 }

data class ToolCtx(val threadId: Long)

data class ToolResult(
    val status: String,                 // OK | FAILED | NEEDS_PERMISSION | AMBIGUOUS | AWAITING_CONFIRMATION
    val message: String,
    val data: JsonObject? = null,
    val options: List<String> = emptyList(),
    val permission: String? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("status", status)
        put("message", message)
        data?.let { put("data", it) }
        if (options.isNotEmpty()) putJsonArray("options") { options.forEach { add(it) } }
    }

    companion object {
        fun ok(message: String, data: JsonObject? = null) = ToolResult("OK", message, data)
        fun failed(message: String) = ToolResult("FAILED", message)
    }
}

class ToolDef(
    val name: String,
    val description: String,
    val params: JsonObject?,
    val tier: (JsonObject) -> Tier,
    val summarize: suspend (JsonObject) -> String,
    val note: String? = null,
    val audited: Boolean = true,
    val run: suspend (JsonObject, ToolCtx) -> ToolResult,
)

/** Gemini function-declaration schema helpers. */
object Sch {
    fun str(d: String) = buildJsonObject { put("type", "STRING"); put("description", d) }
    fun int(d: String) = buildJsonObject { put("type", "INTEGER"); put("description", d) }
    fun bool(d: String) = buildJsonObject { put("type", "BOOLEAN"); put("description", d) }
    fun enum(d: String, vararg v: String) = buildJsonObject {
        put("type", "STRING"); put("description", d)
        putJsonArray("enum") { v.forEach { add(it) } }
    }
    fun obj(props: Map<String, JsonObject>, required: List<String> = emptyList()) = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") { props.forEach { (k, v) -> put(k, v) } }
        if (required.isNotEmpty()) putJsonArray("required") { required.forEach { add(it) } }
    }
    fun list(d: String, item: JsonObject) = buildJsonObject {
        put("type", "ARRAY"); put("description", d); put("items", item)
    }
}

fun JsonObject.str(k: String): String? = (this[k] as? JsonElement)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
fun JsonObject.int(k: String): Int? = this[k]?.jsonPrimitive?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() }
fun JsonObject.bool(k: String): Boolean? = this[k]?.jsonPrimitive?.booleanOrNull
