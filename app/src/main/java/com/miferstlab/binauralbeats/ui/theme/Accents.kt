package com.miferstlab.binauralbeats.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Theme-aware accent hues. The night palette uses the bright galactic neons (they sit on a near-black
 * backdrop); the light palette uses darkened variants so icons, sliders, switches and accent text keep
 * AA contrast on white/pale surfaces.
 */
class Accents(
    val dark: Boolean,
    val cyan: Color,
    val teal: Color,
    val violet: Color,
    val magenta: Color,
    val amber: Color,
    val sky: Color,
    val ember: Color,
    /** Content colour on a [cyan] filled container (play button). */
    val onCyan: Color,
    val inactiveTrack: Color,
    val switchThumb: Color,
    val switchTrack: Color,
    val glassAlpha: Float,
    val chipSelectedAlpha: Float,
    val lightCardBorder: Color
) {
    fun cardBorder(tint: Color, nightAlpha: Float): Color =
        if (dark) tint.copy(alpha = nightAlpha) else lightCardBorder

    fun chipBorder(selected: Boolean, tint: Color, outline: Color): Color = when {
        selected -> if (dark) tint.copy(alpha = 0.85f) else tint
        dark -> outline.copy(alpha = 0.4f)
        else -> outline
    }

    fun chipSelectedContainer(tint: Color, nightAlpha: Float): Color =
        tint.copy(alpha = if (dark) nightAlpha else chipSelectedAlpha)
}

val NightAccents = Accents(
    dark = true,
    cyan = NebulaCyan,
    teal = SoftTeal,
    violet = ElectricViolet,
    magenta = NebulaMagenta,
    amber = SoftAmber,
    sky = SoftSky,
    ember = SoftEmber,
    onCyan = VoidBg,
    inactiveTrack = ElectricViolet.copy(alpha = 0.25f),
    switchThumb = NebulaCyan,
    switchTrack = NebulaCyan.copy(alpha = 0.45f),
    glassAlpha = 0.72f,
    chipSelectedAlpha = 0.28f,
    lightCardBorder = Color.Transparent
)

val DayAccents = Accents(
    dark = false,
    cyan = LightCyan,
    teal = LightTeal,
    violet = LightViolet,
    magenta = LightMagenta,
    amber = LightAmber,
    sky = LightSky,
    ember = LightEmber,
    onCyan = Color.White,
    inactiveTrack = LightOutline,
    switchThumb = Color.White,
    switchTrack = LightPrimary,
    glassAlpha = 1f,
    chipSelectedAlpha = 0.16f,
    lightCardBorder = LightOutlineVariant
)

val LocalAccents = staticCompositionLocalOf { NightAccents }

/** Current accents, e.g. `Accent.cyan`. */
val Accent: Accents
    @Composable get() = LocalAccents.current

/** Card/chip surface: translucent over the galaxy at night, opaque white by day. */
@Composable
fun glassSurfaceColor(): Color =
    MaterialTheme.colorScheme.surface.copy(alpha = LocalAccents.current.glassAlpha)
