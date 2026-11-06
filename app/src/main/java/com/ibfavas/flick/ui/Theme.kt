package com.ibfavas.flick.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Flick's brand, AMOLED edition (v1.0.8): pitch black everywhere, white
 * text, one bright cyan accent. Pure black backgrounds save battery on
 * AMOLED screens and make the cyan pop. The logo is a minimal white
 * swoosh ending in a cyan dot; the wordmark is rounded lowercase
 * "flick" with the dot of the i in cyan. The app is always dark.
 *
 * The deep teal of v1.0.6/v1.0.7 is gone from the theme: surfaces are
 * pure black or at most #0A0A0A, and cards are separated from the
 * background by a thin neutral outline instead of a lighter surface.
 * Teal survives only inside the brand assets themselves (the launcher
 * icon and the wordmark image), which keep their own colours.
 *
 * v1.2.0: the accent is a setting now, not a constant. Cyan stays the
 * default, but Settings offers five more accents. FlickTheme takes the
 * accent and publishes it as [LocalAccent]; every accent-coloured bit
 * of UI (buttons via colorScheme.primary, selected tabs, gesture
 * glyphs, the pad wave, the Done label) reads it from there, so a
 * change applies app-wide at once. The stroke colour setting stays
 * separate: it colours only the stroke you draw.
 */
object FlickBrand {
    val AmoledBackground = Color(0xFF000000)
    val AmoledSurface = Color(0xFF0A0A0A)
    val AmoledSurfaceVariant = Color(0xFF0A0A0A)
    val Cyan = Color(0xFF00E5FF)
    /** Dark text on accent fills (buttons, selected tabs). Every accent
     *  preset is light enough for black text on top. */
    val OnCyan = Color(0xFF000000)
    val TextWhite = Color(0xFFFFFFFF)
    val TextMuted = Color(0xFFB3B3B3)
    val Outline = Color(0xFF2E2E2E)

    /**
     * The drawn stroke's defaults (v1.0.11): brand cyan at 14px. Both
     * are configurable in Settings (stroke width slider, colour
     * presets); these values are only the defaults. Card glyphs in
     * the list follow the app accent (v1.2.0), not the stroke colour.
     */
    val StrokeColor = Cyan
    const val StrokeWidth: Float = 14f
}

/** The current app accent colour, provided by [FlickTheme]. */
val LocalAccent = compositionLocalOf { FlickBrand.Cyan }

/** Read the current app accent colour inside a composable. */
@Composable
fun accentColor(): Color = LocalAccent.current

private fun flickColors(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = FlickBrand.OnCyan,
    secondary = Color(0xFF8ECDF0),
    background = FlickBrand.AmoledBackground,
    surface = FlickBrand.AmoledSurface,
    surfaceVariant = FlickBrand.AmoledSurfaceVariant,
    onBackground = FlickBrand.TextWhite,
    onSurface = FlickBrand.TextWhite,
    onSurfaceVariant = FlickBrand.TextMuted,
    outline = FlickBrand.Outline,
    error = Color(0xFFFF8A80)
)

@Composable
fun FlickTheme(accent: Color = FlickBrand.Cyan, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalAccent provides accent
    ) {
        MaterialTheme(
            colorScheme = flickColors(accent),
            content = content
        )
    }
}
