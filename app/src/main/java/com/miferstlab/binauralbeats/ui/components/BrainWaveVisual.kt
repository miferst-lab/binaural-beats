package com.miferstlab.binauralbeats.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.miferstlab.binauralbeats.ui.theme.ElectricViolet
import com.miferstlab.binauralbeats.ui.theme.NebulaCyan
import com.miferstlab.binauralbeats.ui.theme.NebulaMagenta
import com.miferstlab.binauralbeats.ui.theme.Starlight
import kotlin.math.PI
import kotlin.math.sin

/**
 * L/R metaphor sine waves while playing; faint static rings when idle.
 * Uses Compose InfiniteTransition only — no extra dependencies.
 */
@Composable
fun BrainWaveVisual(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "brainWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        if (!isPlaying) {
            val rings = listOf(0.22f, 0.34f, 0.46f)
            rings.forEachIndexed { i, frac ->
                val r = minOf(w, h) * frac
                drawCircle(
                    color = Starlight.copy(alpha = 0.08f + i * 0.03f),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.2f)
                )
            }
            drawCircle(
                color = NebulaCyan.copy(alpha = 0.12f),
                radius = minOf(w, h) * 0.08f,
                center = Offset(cx, cy)
            )
            return@Canvas
        }

        val waves = listOf(
            Triple(NebulaCyan.copy(alpha = 0.85f), 1.0f, 0f),
            Triple(ElectricViolet.copy(alpha = 0.7f), 0.72f, 0.7f),
            Triple(NebulaMagenta.copy(alpha = 0.55f), 0.5f, 1.4f)
        )

        waves.forEach { (color, ampScale, phaseOffset) ->
            val path = Path()
            val amp = h * 0.28f * ampScale * pulse
            val cycles = 2.2f
            val steps = 64
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val x = t * w
                val angle = t * cycles * 2f * PI.toFloat() + phase + phaseOffset
                val y = cy + sin(angle.toDouble()).toFloat() * amp
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 2.4f, cap = StrokeCap.Round)
            )
        }
    }
}
