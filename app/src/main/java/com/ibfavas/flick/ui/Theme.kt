package com.ibfavas.flick.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Default dark surfaces and drawing-pad colours. UI highlights use the current Material primary. */
object FlickBrand {
    val AmoledBackground = Color(0xFF000000)
    val AmoledSurface = Color(0xFF0A0A0A)
    val AmoledSurfaceVariant = Color(0xFF202022)
    val DefaultAccent = Color(com.ibfavas.flick.data.SettingsStore.DEFAULT_ACCENT_COLOR_ARGB)
    /** Dark text on accent fills (buttons, selected tabs). Every accent
     *  preset is light enough for black text on top. */
    val OnAccent = Color(0xFF000000)
    val TextWhite = Color(0xFFFFFFFF)
    val TextMuted = Color(0xFFB3B3B3)
    val Outline = Color(0xFF2E2E2E)

    const val StrokeWidth: Float = 14f
}

@Composable
fun accentColor(): Color = MaterialTheme.colorScheme.primary

private fun flickColors(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = FlickBrand.OnAccent,
    secondary = accent,
    tertiary = accent,
    primaryContainer = accent.copy(alpha = .18f),
    onPrimaryContainer = accent,
    secondaryContainer = accent.copy(alpha = .18f),
    onSecondaryContainer = accent,
    surfaceTint = accent,
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
fun FlickTheme(accent: Color = FlickBrand.DefaultAccent, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = flickColors(accent), content = content)
}
