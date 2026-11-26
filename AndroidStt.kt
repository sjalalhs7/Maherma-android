package com.maherma.app.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class SttError { NO_SPEECH, PERMISSION, NETWORK, BUSY, UNAVAILABLE, OTHER }

sealed interface SttEvent {
    data object Ready : SttEvent
    data class Rms(val level: Float) : SttEvent
    data class Partial(val text: String) : SttEvent
    data class Final(val text: String) : SttEvent
    data class Error(val kind: SttError) : SttEvent
}

/** Speech-to-text behind a Flow. Collect on the main thread (SpeechRecognizer requirement). */
@Singleton
class AndroidStt @Inject constructor(@ApplicationContext private val ctx: Context) {
    @Volatile private var current: SpeechRecognizer? = null

    fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(ctx)

    /** Ends capture early; the recognizer still returns what it heard. */
    fun stopListening() { current?.stopListening() }

    fun listen(locale: String, preferOnDevice: Boolean): Flow<SttEvent> = callbackFlow {
        val rec = if (preferOnDevice && Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
        } else {
            SpeechRecognizer.createSpeechRecognizer(ctx)
        }
        current = rec
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { trySend(SttEvent.Ready) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { trySend(SttEvent.Rms(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { trySend(SttEvent.Error(map(error))); close() }
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                trySend(SttEvent.Final(t)); close()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.let { trySend(SttEvent.Partial(it)) }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            // Hints only: recognizers may ignore them.
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
        if (locale.isNotBlank()) intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
        rec.startListening(intent)
        awaitClose { current = null; rec.cancel(); rec.destroy() }
    }

    private fun map(code: Int): SttError = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SttError.NO_SPEECH
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SttError.PERMISSION
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> SttError.NETWORK
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SttError.BUSY
        12, 13 -> SttError.UNAVAILABLE   // language not supported / unavailable (API 31+ constants)
        else -> SttError.OTHER
    }
}
