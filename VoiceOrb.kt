package com.maherma.app.ui.components

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maherma.app.ui.theme.OrbColors
import com.maherma.app.voice.SessionState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Breathing voice orb. Tap: talk / stop / interrupt. Hold (500 ms+): push-to-talk, release to send.
 * Speaking amplitude is synthetic because platform TTS does not expose PCM.
 */
@Composable
fun VoiceOrb(
    state: SessionState,
    level: Float,
    online: Boolean,
    awaitingConfirm: Boolean,
    modifier: Modifier = Modifier,
    onPress: () -> Boolean,
    onRelease: (Boolean, Long) -> Unit,
) {
    val ctx = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val inf = rememberInfiniteTransition(label = "orb")
    val slow by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "slow")
    val fast by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "fast")
    val lvl by animateFloatAsState(level, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "lvl")
    val t = if (reduceMotion) 0f else slow
    val f = if (reduceMotion) 0f else fast
    val twoPi = (2 * PI).toFloat()

    val amp = when (state) {
        SessionState.IDLE -> 0.03f * sin(twoPi * t)
        SessionState.LISTENING -> 0.04f + lvl * 0.22f
        SessionState.THINKING -> 0.03f * sin(twoPi * f)
        SessionState.ACTING -> 0.02f
        SessionState.SPEAKING -> if (reduceMotion) 0.08f else 0.06f + 0.08f * abs(sin(twoPi * f * 2))
    }
    val offline = !online && state == SessionState.IDLE
    val c1 = if (offline) OrbColors.Offline else OrbColors.Indigo
    val c2 = if (offline) OrbColors.Offline.copy(alpha = 0.8f) else OrbColors.Violet
    val c3 = if (offline) OrbColors.Offline.copy(alpha = 0.6f) else OrbColors.Saffron

    val label = when {
        awaitingConfirm -> "confirmation needed"
        offline -> "offline, basic commands only"
        else -> when (state) {
            SessionState.IDLE -> "ready"
            SessionState.LISTENING -> "listening"
            SessionState.THINKING -> "thinking"
            SessionState.ACTING -> "working"
            SessionState.SPEAKING -> "speaking"
        }
    }
    val press by rememberUpdatedState(onPress)
    val release by rememberUpdatedState(onRelease)

    Canvas(
        modifier
            .size(180.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val t0 = SystemClock.uptimeMillis()
                    val started = press()
                    tryAwaitRelease()
                    release(started, SystemClock.uptimeMillis() - t0)
                })
            }
            .semantics {
                contentDescription = "Maherma, $label. Double tap to talk."
                role = Role.Button
                liveRegion = LiveRegionMode.Polite
                onClick(label = "Talk or stop") { press(); true }
            },
    ) {
        val c = center
        val r = size.minDimension / 2f * 0.62f * (1f + amp)
        val angle = if (state == SessionState.THINKING) twoPi * f else twoPi * t
        drawCircle(
            brush = Brush.radialGradient(listOf(c1.copy(alpha = 0.35f), Color.Transparent), c, r * 1.9f),
            radius = r * 1.9f, center = c,
        )
        listOf(c1, c2, c3).forEachIndexed { i, col ->
            val a = angle + i * 2.094f
            val o = Offset(c.x + cos(a) * r * 0.22f, c.y + sin(a) * r * 0.22f)
            drawCircle(
                brush = Brush.radialGradient(listOf(col.copy(alpha = 0.95f), col.copy(alpha = 0f)), o, r * 1.05f),
                radius = r * 1.05f, center = o,
            )
        }
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent), c, r * 0.7f),
            radius = r * 0.7f, center = c,
        )
        if (awaitingConfirm) drawCircle(OrbColors.Saffron, radius = r * 1.25f, center = c, style = Stroke(width = 3.dp.toPx()))
    }
}
