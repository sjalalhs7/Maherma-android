package com.maherma.app.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Detects the user talking over Maherma. Only runs when the device offers hardware/OS echo cancellation;
 * without it Maherma's own voice would trigger it, so the UI falls back to tap-to-interrupt.
 */
@Singleton
class BargeInDetector @Inject constructor() {
    fun supported(): Boolean = AcousticEchoCanceler.isAvailable()

    /** Emits once after about 150 ms of sustained speech energy, then completes. */
    fun detect(): Flow<Unit> = flow {
        val rate = 16_000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return@flow
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 2)
        } catch (e: Exception) { return@flow }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return@flow }
        var aec: AcousticEchoCanceler? = null
        var ns: NoiseSuppressor? = null
        try {
            aec = AcousticEchoCanceler.create(rec.audioSessionId)?.also { it.enabled = true }
            if (aec == null) return@flow
            ns = if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(rec.audioSessionId)?.also { it.enabled = true } else null
            rec.startRecording()
            val buf = ShortArray(rate / 20)
            var floor = 400.0
            var loud = 0
            while (currentCoroutineContext().isActive) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                var sum = 0.0
                for (i in 0 until n) sum += buf[i].toDouble() * buf[i]
                val rms = sqrt(sum / n)
                if (rms > maxOf(1800.0, floor * 3)) {
                    loud++
                    if (loud >= 3) { emit(Unit); return@flow }
                } else {
                    loud = 0
                    floor = floor * 0.95 + rms * 0.05
                }
            }
        } finally {
            runCatching { rec.stop() }
            aec?.release(); ns?.release(); rec.release()
        }
    }.flowOn(Dispatchers.IO)
}
