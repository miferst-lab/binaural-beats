package com.miferstlab.binauralbeats.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = NebulaCyan,
    onPrimary = VoidBg,
    primaryContainer = NebulaIndigo,
    onPrimaryContainer = Starlight,
    secondary = ElectricViolet,
    onSecondary = VoidBg,
    secondaryContainer = NebulaIndigo.copy(alpha = 0.85f),
    onSecondaryContainer = Starlight,
    tertiary = NebulaMagenta,
    onTertiary = Starlight,
    background = VoidBg,
    onBackground = Starlight,
    surface = SurfaceGlass,
    onSurface = Starlight,
    surfaceVariant = SurfaceGlassVariant,
    onSurfaceVariant = StarlightMuted,
    outline = Color(0xFF3A3F6B),
    outlineVariant = Color(0xFF2A2F4A)
)

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = ElectricViolet.copy(alpha = 0.22f),
    onPrimaryContainer = LightOn,
    secondary = LightSecondary,
    onSecondary = Color.White,
    tertiary = NebulaMagenta,
    background = LightBg,
    onBackground = LightOn,
    surface = LightSurface,
    onSurface = LightOn,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnMuted,
    outline = Color(0xFFC5C8DA)
)

@Composable
fun BinauralBeatsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
