package com.miferstlab.binauralbeats.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

internal val DarkColors = darkColorScheme(
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

internal val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = Color.White,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBg,
    onBackground = LightOn,
    surface = LightSurface,
    onSurface = LightOn,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnMuted,
    surfaceTint = LightPrimary,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightError,
    onError = Color.White,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F7FC),
    surfaceContainer = Color(0xFFF1F2FA),
    surfaceContainerHigh = LightDialog,
    surfaceContainerHighest = LightSurfaceVariant
)

internal val NightColors = DarkColors

@Composable
fun BinauralBeatsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalAccents provides if (darkTheme) NightAccents else DayAccents) {
        MaterialTheme(
            colorScheme = colors,
            typography = Typography,
            content = content
        )
    }
}
