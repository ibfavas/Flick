package com.ibfavas.flick.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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
 */
object FlickBrand {
    val AmoledBackground = Color(0xFF000000)
    val AmoledSurface = Color(0xFF0A0A0A)
    val AmoledSurfaceVariant = Color(0xFF0A0A0A)
    val Cyan = Color(0xFF00E5FF)
    /** Dark text on cyan fills (buttons, selected tabs). */
    val OnCyan = Color(0xFF000000)
    val TextWhite = Color(0xFFFFFFFF)
    val TextMuted = Color(0xFFB3B3B3)
    val Outline = Color(0xFF2E2E2E)

    /**
     * The drawn stroke is fixed, not a setting (v1.0.7): brand cyan at a
     * fixed width, so every stroke looks the same everywhere in the app.
     */
    val StrokeColor = Cyan
    const val StrokeWidth: Float = 10f
}

private val FlickColors = darkColorScheme(
    primary = FlickBrand.Cyan,
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
fun FlickTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FlickColors,
        content = content
    )
}
