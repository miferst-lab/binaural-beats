package com.miferstlab.binauralbeats.ui.theme

import androidx.compose.ui.graphics.Color

/** Cosmic void backgrounds */
val VoidBg = Color(0xFF05060E)
val VoidBgDeep = Color(0xFF070B18)

/** Nebula accents */
val NebulaIndigo = Color(0xFF1A1040)
val NebulaMagenta = Color(0xFF7B3FA8)
val NebulaCyan = Color(0xFF3DE0E8)
val ElectricViolet = Color(0xFF8B7CFF)

/** Surfaces — translucent dark indigo, not flat gray */
val SurfaceGlass = Color(0xFF12182E)
val SurfaceGlassVariant = Color(0xFF1A2140)

/** On-text — soft starlight */
val Starlight = Color(0xFFE8ECFF)
val StarlightMuted = Color(0xFF9AA3C7)

/** Mode / accent aliases (galactic) */
val SoftBlue = NebulaCyan
val SoftBlueDark = ElectricViolet
val SoftTeal = Color(0xFF4ECDC4)
val SoftLavender = ElectricViolet
val SoftAmber = Color(0xFFE8B86D)
val SoftCoral = NebulaMagenta
val SoftSky = Color(0xFF6EC8FF)
val SoftEmber = Color(0xFFFF8A4C)

/** Legacy aliases used by Theme */
val NightBg = VoidBg
val NightSurface = SurfaceGlass
val NightSurfaceVariant = SurfaceGlassVariant
val OnNight = Starlight
val OnNightMuted = StarlightMuted

/**
 * Light scheme. Every pair used for text/icons is checked against WCAG AA in LightThemeContrastTest
 * (4.5:1 for text, 3:1 for icons/borders/controls).
 */
val LightBg = Color(0xFFEEF0F9)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFDEE1F2)
val LightOn = Color(0xFF1A1B2E)
val LightOnMuted = Color(0xFF474C6B)
val LightPrimary = Color(0xFF4F43BD)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE2DEFF)
val LightOnPrimaryContainer = Color(0xFF1E1568)
val LightSecondary = Color(0xFF006879)
val LightSecondaryContainer = Color(0xFFCDEEF3)
val LightOnSecondaryContainer = Color(0xFF00252C)
val LightTertiary = Color(0xFF8A2B9E)
val LightTertiaryContainer = Color(0xFFF5D9FA)
val LightOnTertiaryContainer = Color(0xFF3A0B45)
val LightOutline = Color(0xFF767D9F)
val LightOutlineVariant = Color(0xFFC3C8E0)
val LightError = Color(0xFFB3261E)
val LightErrorContainer = Color(0xFFF9DEDC)
val LightOnErrorContainer = Color(0xFF410E0B)
val LightDialog = Color(0xFFFFFFFF)

/** Accent hues darkened for white/pale surfaces (>= 4.5:1 on every light surface). */
val LightCyan = Color(0xFF006879)
val LightTeal = Color(0xFF00675B)
val LightViolet = Color(0xFF5B4FC7)
val LightMagenta = Color(0xFF8A2B9E)
val LightAmber = Color(0xFF865000)
val LightSky = Color(0xFF0A5AA6)
val LightEmber = Color(0xFFAD3700)
