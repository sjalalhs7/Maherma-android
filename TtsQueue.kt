package com.maherma.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** Sentence-by-sentence TTS queue (platform TextToSpeech has no streaming-text input). */
@Singleton
class TtsQueue @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val am = ctx.getSystemService(AudioManager::class.java)
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private val buffered = ConcurrentLinkedQueue<String>()
    private val pending = AtomicInteger(0)
    private val counter = AtomicInteger(0)
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking
    @Volatile private var locale: Locale = Locale.getDefault()
    @Volatile var languageOk: Boolean = true
        private set
    private var hasFocus = false

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs)
        .setOnAudioFocusChangeListener { c ->
            if (c == AudioManager.AUDIOFOCUS_LOSS || c == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
        }.build()

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) = finished()
        @Deprecated("Deprecated in API 21") override fun onError(utteranceId: String?) = finished()
        override fun onError(utteranceId: String?, errorCode: Int) = finished()
        override fun onStop(utteranceId: String?, interrupted: Boolean) = finished()
    }

    init {
        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setAudioAttributes(attrs)
                tts?.setOnUtteranceProgressListener(listener)
                ready = true
                applyLocale()
                while (true) { doSpeak(buffered.poll() ?: break) }
            } else {
                languageOk = false
                buffered.clear(); pending.set(0); _speaking.value = false
            }
        }
    }

    fun setLocale(tag: String) {
        val l = if (tag.isBlank()) Locale.getDefault() else Locale.forLanguageTag(tag)
        if (l == locale && ready) return
        locale = l
        if (ready) applyLocale()
    }

    fun speak(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        pending.incrementAndGet()
        _speaking.value = true
        if (ready) doSpeak(t) else buffered.add(t)
    }

    fun stop() {
        buffered.clear()
        pending.set(0)
        tts?.stop()
        _speaking.value = false
        abandonFocus()
    }

    /** Suspends until everything queued has been spoken (or 60 s, if the engine never reports back). */
    suspend fun awaitIdle() { withTimeoutOrNull(60_000) { speaking.first { !it } } }

    private fun applyLocale() {
        val r = tts?.setLanguage(locale)
        languageOk = r != null && r >= TextToSpeech.LANG_AVAILABLE
    }

    private fun doSpeak(t: String) {
        requestFocus()
        val r = tts?.speak(t, TextToSpeech.QUEUE_ADD, null, "m${counter.incrementAndGet()}")
        if (r != TextToSpeech.SUCCESS) finished()
    }

    private fun finished() {
        if (pending.updateAndGet { maxOf(0, it - 1) } == 0) {
            _speaking.value = false
            abandonFocus()
        }
    }

    private fun requestFocus() {
        if (!hasFocus) hasFocus = am.requestAudioFocus(focusReq) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        if (hasFocus) { am.abandonAudioFocusRequest(focusReq); hasFocus = false }
    }
}
