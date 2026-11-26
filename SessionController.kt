package com.maherma.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.maherma.app.data.prefs.AppSettings
import com.maherma.app.data.prefs.SettingsStore
import com.maherma.app.data.repo.ConversationRepository
import com.maherma.app.device.NetworkMonitor
import com.maherma.app.domain.model.ItemKind
import com.maherma.app.llm.AiException
import com.maherma.app.orchestrator.OrchEvent
import com.maherma.app.orchestrator.Orchestrator
import com.maherma.app.orchestrator.SentenceChunker
import com.maherma.app.tools.ToolRouter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class SessionState { IDLE, LISTENING, THINKING, ACTING, SPEAKING }

sealed interface SessionEvent {
    data object NeedMicPermission : SessionEvent
    data class Notice(val text: String) : SessionEvent
}

/**
 * Owns the voice session state machine. App-scoped so it survives configuration changes and can later be
 * driven by a VoiceInteractionService without UI changes.
 */
@Singleton
class SessionController @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val stt: AndroidStt,
    private val tts: TtsQueue,
    private val barge: BargeInDetector,
    private val orchestrator: Orchestrator,
    private val repo: ConversationRepository,
    private val router: ToolRouter,
    private val settings: SettingsStore,
    net: NetworkMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(SessionState.IDLE)
    val state: StateFlow<SessionState> = _state
    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial
    private val _reply = MutableStateFlow<String?>(null)
    val liveReply: StateFlow<String?> = _reply
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level
    private val _threadId = MutableStateFlow<Long?>(null)
    val threadId: StateFlow<Long?> = _threadId
    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SessionEvent> = _events
    val online: StateFlow<Boolean> = net.online

    private var job: Job? = null
    private var bargeJob: Job? = null
    private var gen = 0

    // ---------------- entry points ----------------
    /** Orb pressed. Returns true if this press started listening (so a long hold means push-to-talk). */
    fun orbPress(): Boolean = when (_state.value) {
        SessionState.IDLE -> { startListening(); true }
        SessionState.LISTENING -> { stt.stopListening(); false }
        else -> { interrupt(); false }
    }

    fun orbRelease(startedByPress: Boolean, heldMs: Long) {
        if (startedByPress && heldMs >= 500 && _state.value == SessionState.LISTENING) stt.stopListening()
    }

    fun startListening() {
        if (_state.value == SessionState.LISTENING) return
        if (!hasMic()) { _events.tryEmit(SessionEvent.NeedMicPermission); return }
        if (!stt.available()) { notice("No speech recognizer is installed on this device. Type instead."); return }
        launchTurn { voiceLoop() }
    }

    fun sendText(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        launchTurn { addUserAndRun(t) }
    }

    fun retry(errorId: Long) {
        val tid = _threadId.value ?: return
        launchTurn { repo.delete(errorId); runAssistant(tid) }
    }

    fun interrupt() {
        gen++
        job?.cancel(); bargeJob?.cancel()
        tts.stop()
        _state.value = SessionState.IDLE
        _reply.value = null; _partial.value = ""; _level.value = 0f
    }

    fun newThread() { interrupt(); _threadId.value = null }
    fun openThread(id: Long) { interrupt(); _threadId.value = id }
    fun deleteThread(id: Long) {
        scope.launch { repo.deleteThread(id); if (_threadId.value == id) _threadId.value = null }
    }

    fun confirm(cardId: Long) { scope.launch { router.confirm(cardId) } }
    fun cancelAction(cardId: Long) { scope.launch { router.cancel(cardId) } }
    fun permissionResult(cardId: Long, granted: Boolean) {
        if (cardId < 0) return
        scope.launch { if (granted) router.confirm(cardId) else router.permissionDenied(cardId) }
    }

    fun notice(text: String) { _events.tryEmit(SessionEvent.Notice(text)) }

    // ---------------- internals ----------------
    /** Starts a new turn; the generation counter stops a cancelled turn from resetting a newer one's state. */
    private fun launchTurn(block: suspend () -> Unit) {
        job?.cancel(); bargeJob?.cancel()
        tts.stop()
        val my = ++gen
        job = scope.launch {
            try {
                block()
            } finally {
                if (gen == my) {
                    _state.value = SessionState.IDLE
                    _reply.value = null; _partial.value = ""; _level.value = 0f
                    bargeJob?.cancel()
                }
            }
        }
    }

    private suspend fun voiceLoop() {
        while (true) {
            val cfg = settings.settings.first()
            val text = listenOnce(cfg) ?: return
            addUserAndRun(text)
            if (!settings.settings.first().handsFree) return
        }
    }

    private suspend fun listenOnce(cfg: AppSettings): String? {
        _state.value = SessionState.LISTENING
        _partial.value = ""
        var finalText: String? = null
        var error: SttError? = null
        stt.listen(cfg.voiceLocale, cfg.preferOnDevice).collect { e ->
            when (e) {
                is SttEvent.Rms -> _level.value = e.level
                is SttEvent.Partial -> _partial.value = e.text
                is SttEvent.Final -> finalText = e.text
                is SttEvent.Error -> error = e.kind
                SttEvent.Ready -> {}
            }
        }
        _level.value = 0f
        val text = (finalText?.takeIf { it.isNotBlank() } ?: _partial.value).trim()
        _partial.value = ""
        if (text.isEmpty()) {
            val err = error
            when (err) {
                SttError.PERMISSION -> _events.tryEmit(SessionEvent.NeedMicPermission)
                SttError.NETWORK -> notice("The speech service needs a connection. Turn on on-device recognition in Settings, or type.")
                SttError.BUSY -> notice("The microphone is busy. Try again in a moment.")
                SttError.UNAVAILABLE -> notice("Speech recognition isn't available for that language on this device.")
                SttError.OTHER -> notice("Speech recognition failed. Try again or type.")
                SttError.NO_SPEECH, null -> if (!cfg.handsFree) notice("I didn't catch that.")
            }
            return null
        }
        return text
    }

    private suspend fun addUserAndRun(text: String) {
        val tid = _threadId.value?.takeIf { repo.exists(it) } ?: repo.createThread(text.take(48)).also { _threadId.value = it }
        repo.add(tid, ItemKind.USER, text)
        runAssistant(tid)
    }

    private suspend fun runAssistant(tid: Long) {
        val cfg = settings.settings.first()
        _state.value = SessionState.THINKING
        _reply.value = null
        tts.setLocale(cfg.voiceLocale)
        val chunker = SentenceChunker()
        var bargeStarted = false

        fun say(s: String) {
            if (!bargeStarted) { bargeStarted = true; startBargeIn(cfg) }
            _state.value = SessionState.SPEAKING
            tts.speak(s)
        }

        try {
            orchestrator.run(tid).collect { e ->
                when (e) {
                    is OrchEvent.Working -> {
                        if (cfg.speakReplies) chunker.flush()?.let { say(it) }
                        if (!tts.speaking.value) _state.value = SessionState.ACTING
                    }
                    is OrchEvent.Speech -> {
                        _reply.value = (_reply.value ?: "") + e.text
                        if (cfg.speakReplies) chunker.push(e.text).forEach { say(it) }
                    }
                }
            }
            if (cfg.speakReplies) {
                chunker.flush()?.let { say(it) }
                if (!tts.languageOk) notice("This device has no voice for that language. Showing text only.")
                tts.awaitIdle()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiException) {
            repo.add(tid, ItemKind.ERROR, e.message ?: "Something went wrong.")
        } catch (e: Exception) {
            repo.add(tid, ItemKind.ERROR, "Unexpected error: ${e.localizedMessage ?: "unknown"}")
        }
    }

    private fun startBargeIn(cfg: AppSettings) {
        if (!cfg.voiceBargeIn || !barge.supported() || !hasMic()) return
        bargeJob?.cancel()
        bargeJob = scope.launch { barge.detect().collect { bargeIn() } }
    }

    /** User spoke over Maherma: stop talking, drop the rest of the reply, listen. */
    private fun bargeIn() {
        interrupt()
        startListening()
    }

    private fun hasMic() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}
