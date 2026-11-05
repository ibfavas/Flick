package com.ibfavas.flick.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Flick's own brand (v1.0.6): deep teal, white text, one bright cyan
 * accent. The logo is a minimal white swoosh ending in a cyan dot on a
 * deep teal background; the wordmark is rounded lowercase "flick" with
 * the dot of the i in cyan. The app is always dark.
 */
object FlickBrand {
    val TealBackground = Color(0xFF07272B)
    val TealSurface = Color(0xFF0C3439)
    val TealSurfaceVariant = Color(0xFF143F44)
    val Cyan = Color(0xFF00E5FF)
    val CyanDark = Color(0xFF00323A)
    val TextWhite = Color(0xFFEDF7F7)
    val TextMuted = Color(0xFF9FC0C0)
    val Outline = Color(0xFF2C5055)

    /**
     * The drawn stroke is fixed, not a setting (v1.0.7): brand cyan at a
     * fixed width, so every stroke looks the same everywhere in the app.
     */
    val StrokeColor = Cyan
    const val StrokeWidth: Float = 10f
}

private val FlickColors = darkColorScheme(
    primary = FlickBrand.Cyan,
    onPrimary = FlickBrand.CyanDark,
    secondary = Color(0xFF8ECDF0),
    background = FlickBrand.TealBackground,
    surface = FlickBrand.TealSurface,
    surfaceVariant = FlickBrand.TealSurfaceVariant,
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
