package com.maherma.app.llm

import com.maherma.app.data.prefs.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface Part {
    data class Text(val text: String) : Part
    /** A part echoed back verbatim (function calls keep any provider metadata such as thought signatures). */
    data class Raw(val json: JsonObject) : Part
    data class FunctionResponse(val name: String, val response: JsonObject) : Part
}

data class Content(val role: String, val parts: List<Part>)

sealed interface LlmEvent {
    data class Text(val text: String) : LlmEvent
    data class ToolCall(val name: String, val args: JsonObject, val raw: JsonObject) : LlmEvent
}

data class ToolDecl(val name: String, val description: String, val parameters: JsonObject?)

enum class AiError { NO_KEY, BAD_KEY, MODEL, RATE_LIMIT, NETWORK, SERVER, EMPTY, UNKNOWN }
class AiException(val error: AiError, message: String) : Exception(message)

/** Gemini REST client with streaming (SSE) and function calling. Swap this class to change provider. */
@Singleton
class GeminiClient @Inject constructor(private val http: OkHttpClient, private val json: Json) {

    fun stream(cfg: AppSettings, system: String, contents: List<Content>, tools: List<ToolDecl>): Flow<LlmEvent> = flow {
        val req = request(cfg, system, contents, tools)
        val resp = try { http.newCall(req).await() } catch (e: IOException) {
            throw AiException(AiError.NETWORK, "Can't reach the AI service. Check your connection.")
        }
        resp.use { r ->
            if (!r.isSuccessful) throw r.toError()
            val source = r.body?.source() ?: throw AiException(AiError.EMPTY, "Empty response.")
            try {
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty()) continue
                    val el = runCatching { json.parseToJsonElement(payload) }.getOrNull() ?: continue
                    for (e in events(el)) emit(e)
                }
            } catch (e: IOException) {
                throw AiException(AiError.NETWORK, "The connection dropped mid-reply.")
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun request(cfg: AppSettings, system: String, contents: List<Content>, tools: List<ToolDecl>): Request {
        if (cfg.apiKey.isBlank()) throw AiException(AiError.NO_KEY, "Add your Gemini API key in Settings. Basic commands still work without it.")
        val body = buildJsonObject {
            putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", system) } } }
            putJsonArray("contents") { contents.forEach { add(it.toJson()) } }
            if (tools.isNotEmpty()) putJsonArray("tools") {
                addJsonObject {
                    putJsonArray("functionDeclarations") {
                        tools.forEach { t ->
                            addJsonObject {
                                put("name", t.name)
                                put("description", t.description)
                                t.parameters?.let { put("parameters", it) }
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") { put("temperature", 0.6) }
        }.toString()
        return Request.Builder()
            .url("$BASE/${cfg.model}:streamGenerateContent?alt=sse")
            .header("x-goog-api-key", cfg.apiKey)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun Content.toJson(): JsonObject = buildJsonObject {
        put("role", role)
        putJsonArray("parts") {
            parts.forEach { p ->
                when (p) {
                    is Part.Text -> addJsonObject { put("text", p.text) }
                    is Part.Raw -> add(p.json)
                    is Part.FunctionResponse -> addJsonObject {
                        putJsonObject("functionResponse") { put("name", p.name); put("response", p.response) }
                    }
                }
            }
        }
    }

    private fun events(el: JsonElement): List<LlmEvent> {
        val parts = el.jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray ?: return emptyList()
        return parts.mapNotNull { p ->
            val o = p.jsonObject
            if (o["thought"]?.jsonPrimitive?.booleanOrNull == true) return@mapNotNull null
            val fc = o["functionCall"]?.jsonObject
            if (fc != null) {
                LlmEvent.ToolCall(
                    fc["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    fc["args"]?.jsonObject ?: JsonObject(emptyMap()),
                    o,
                )
            } else {
                o["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { LlmEvent.Text(it) }
            }
        }
    }

    private fun Response.toError(): AiException {
        val msg = runCatching { body?.string() }.getOrNull().orEmpty()
        return when {
            (code == 400 && msg.contains("API key", ignoreCase = true)) || code == 401 || code == 403 ->
                AiException(AiError.BAD_KEY, "The AI service rejected the API key. Check it in Settings.")
            code == 404 -> AiException(AiError.MODEL, "That model isn't available for your key. Change it in Settings.")
            code == 429 -> AiException(AiError.RATE_LIMIT, "Rate limit or quota reached. Wait a moment and retry.")
            code >= 500 -> AiException(AiError.SERVER, "The AI service is having trouble. Retry in a moment.")
            else -> AiException(AiError.UNKNOWN, "Request failed (HTTP $code).")
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) { cont.resume(response) }
        })
    }

    private companion object { const val BASE = "https://generativelanguage.googleapis.com/v1beta/models" }
}
