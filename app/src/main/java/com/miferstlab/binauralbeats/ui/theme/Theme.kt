package com.miferstlab.binauralbeats.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = SoftBlue,
    onPrimary = NightBg,
    primaryContainer = SoftBlueDark,
    onPrimaryContainer = OnNight,
    secondary = SoftTeal,
    onSecondary = NightBg,
    tertiary = SoftLavender,
    background = NightBg,
    onBackground = OnNight,
    surface = NightSurface,
    onSurface = OnNight,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = OnNightMuted,
    outline = Color(0xFF30363D)
)

private val LightColors = lightColorScheme(
    primary = SoftBlueDark,
    onPrimary = Color.White,
    primaryContainer = SoftBlue.copy(alpha = 0.25f),
    onPrimaryContainer = NightBg,
    secondary = SoftTeal,
    background = Color(0xFFF5F7FA),
    onBackground = NightBg,
    surface = Color.White,
    onSurface = NightBg,
    surfaceVariant = Color(0xFFE8ECF0),
    onSurfaceVariant = Color(0xFF5C6670)
)

@Composable
fun BinauralBeatsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Prefer calm dark-friendly look; still respect system light if user chooses it
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
