package com.ibfavas.flick.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Flick is always dark: a near-black background, quiet grey surfaces, and
 * one bright red accent for the stroke, the add button and key actions.
 * The look stays minimal on purpose, the gesture list and the draw pad
 * are the app, nothing else competes with them.
 */
private val FlickColors = darkColorScheme(
    primary = Color(0xFFFF3B30),
    onPrimary = Color.White,
    secondary = Color(0xFF8ECDF0),
    background = Color(0xFF0A0A0B),
    surface = Color(0xFF17181B),
    surfaceVariant = Color(0xFF232529),
    onBackground = Color(0xFFF2F2F2),
    onSurface = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF9A9CA0),
    outline = Color(0xFF33353A)
)

@Composable
fun FlickTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FlickColors,
        content = content
    )
}
