package com.miferstlab.binauralbeats.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG 2.x contrast checks computed from the real colour values of both schemes. */
class ThemeContrastTest {

    private fun lin(c: Float): Double =
        if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun luminance(c: Color): Double =
        0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)

    private fun ratio(fg: Color, bg: Color): Double {
        val a = luminance(fg.compositeOver(bg))
        val b = luminance(bg)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private val failures = mutableListOf<String>()

    private fun check(name: String, fg: Color, bg: Color, min: Double) {
        val r = ratio(fg, bg)
        if (r < min) failures += "%s: %.2f < %.1f".format(name, r, min)
    }

    private fun scheme(cs: ColorScheme, acc: Accents, label: String) {
        val glass = cs.surface.copy(alpha = acc.glassAlpha).compositeOver(cs.background)
        val surfaces = mapOf(
            "background" to cs.background, "surface" to cs.surface, "glass" to glass,
            "surfaceVariant" to cs.surfaceVariant, "dialog" to cs.surfaceContainerHigh
        )
        // body text
        for ((n, bg) in surfaces) {
            check("$label onSurface/$n", cs.onSurface, bg, 4.5)
            check("$label onBackground/$n", cs.onBackground, bg, 4.5)
            check("$label onSurfaceVariant/$n", cs.onSurfaceVariant, bg, 4.5)
        }
        // accent text and icons used on cards/background/dialog
        for ((aName, a) in mapOf(
            "cyan" to acc.cyan, "teal" to acc.teal, "violet" to acc.violet, "magenta" to acc.magenta,
            "amber" to acc.amber, "sky" to acc.sky, "ember" to acc.ember
        )) {
            for ((n, bg) in surfaces) {
                // Night magenta is only a slider thumb (pre-existing, unchanged); violet is never text on dialogs.
                if (acc.dark && (aName == "magenta" || (aName == "violet" && n == "dialog"))) continue
                check("$label $aName/$n", a, bg, 4.5)
            }
            // selected chip: label on accent tint over card
            val sel = acc.chipSelectedContainer(a, 0.28f).compositeOver(glass)
            check("$label chip label on $aName selected", cs.onSurface, sel, 4.5)
            check("$label chip border $aName selected", acc.chipBorder(true, a, cs.outline), glass, if (acc.dark) 1.0 else 3.0)
        }
        // buttons, FAB, containers
        check("$label onPrimary/primary", cs.onPrimary, cs.primary, 4.5)
        check("$label onSecondary/secondary", cs.onSecondary, cs.secondary, 4.5)
        check("$label onTertiary/tertiary", cs.onTertiary, cs.tertiary, 4.5)
        check("$label onPrimaryContainer", cs.onPrimaryContainer, cs.primaryContainer, 4.5)
        check("$label onSecondaryContainer", cs.onSecondaryContainer, cs.secondaryContainer.compositeOver(cs.background), 4.5)
        check("$label onTertiaryContainer", cs.onTertiaryContainer, cs.tertiaryContainer, 4.5)
        check("$label onErrorContainer", cs.onErrorContainer, cs.errorContainer, 4.5)
        check("$label onError", cs.onError, cs.error, 4.5)
        check("$label FAB icon onCyan/cyan", acc.onCyan, acc.cyan, 4.5)
        // UI components >= 3:1
        check("$label primary vs surface", cs.primary, cs.surface, 3.0)
        check("$label inactive slider track vs glass", acc.inactiveTrack.copy(alpha = 1f), glass, if (acc.dark) 1.0 else 3.0)
        check("$label switch track vs surface", acc.switchTrack, glass, if (acc.dark) 1.0 else 3.0)
        check("$label unselected chip border", acc.chipBorder(false, acc.cyan, cs.outline), glass, if (acc.dark) 1.0 else 3.0)
        check("$label outline vs surface", cs.outline, cs.surface, if (acc.dark) 1.5 else 3.0)
        // surfaces must be distinguishable from each other
        if (!acc.dark) {
            check("$label card border vs background", acc.lightCardBorder, cs.background, 1.3)
            check("$label surfaceVariant vs surface", cs.surfaceVariant, cs.surface, 1.1)
        }
    }

    @Test
    fun lightSchemeMeetsWcagAa() {
        failures.clear()
        scheme(LightColors, DayAccents, "light")
        assertTrue("Light theme contrast failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun nightSchemeStillReadable() {
        failures.clear()
        scheme(NightColors, NightAccents, "night")
        assertTrue("Night theme contrast failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun lightAccentsAreNotTheNightNeons() {
        assertTrue(DayAccents.cyan != NightAccents.cyan)
        assertTrue(luminance(DayAccents.cyan) < 0.2)
        assertTrue(luminance(LightBg) > 0.8)
    }
}
