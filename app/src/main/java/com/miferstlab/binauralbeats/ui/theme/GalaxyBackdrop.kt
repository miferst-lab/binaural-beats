package com.miferstlab.binauralbeats.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

private data class Star(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float
)

/**
 * Full-bleed deep-space backdrop: void fill, soft nebula blobs, static seeded star field.
 */
@Composable
fun GalaxyBackdrop(modifier: Modifier = Modifier) {
    val stars = remember {
        val rng = Random(seed = 42L)
        List(90) {
            Star(
                x = rng.nextFloat(),
                y = rng.nextFloat(),
                radius = rng.nextFloat() * 1.8f + 0.4f,
                alpha = rng.nextFloat() * 0.55f + 0.2f
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(VoidBg)

        val w = size.width
        val h = size.height

        // Soft radial nebula blobs (cyan / violet / magenta), low alpha
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NebulaCyan.copy(alpha = 0.14f),
                    Color.Transparent
                ),
                center = Offset(w * 0.18f, h * 0.22f),
                radius = w * 0.55f
            ),
            center = Offset(w * 0.18f, h * 0.22f),
            radius = w * 0.55f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ElectricViolet.copy(alpha = 0.18f),
                    Color.Transparent
                ),
                center = Offset(w * 0.82f, h * 0.35f),
                radius = w * 0.6f
            ),
            center = Offset(w * 0.82f, h * 0.35f),
            radius = w * 0.6f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NebulaMagenta.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = Offset(w * 0.45f, h * 0.78f),
                radius = w * 0.5f
            ),
            center = Offset(w * 0.45f, h * 0.78f),
            radius = w * 0.5f
        )

        // Subtle indigo wash near center
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NebulaIndigo.copy(alpha = 0.35f),
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, h * 0.45f),
                radius = w * 0.7f
            ),
            center = Offset(w * 0.5f, h * 0.45f),
            radius = w * 0.7f
        )

        stars.forEach { star ->
            drawCircle(
                color = Starlight.copy(alpha = star.alpha),
                radius = star.radius,
                center = Offset(star.x * w, star.y * h)
            )
        }
    }
}
