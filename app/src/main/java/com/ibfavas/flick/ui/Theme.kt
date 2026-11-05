package com.ibfavas.flick.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF6B5E),
    secondary = Color(0xFF8ECDF0),
    background = Color(0xFF0B1F3A),
    surface = Color(0xFF12294D),
    onPrimary = Color.White,
    onBackground = Color(0xFFEDE6D0),
    onSurface = Color(0xFFEDE6D0)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFD93A2B),
    secondary = Color(0xFF0B5B8A),
    background = Color(0xFFEDE6D0),
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A)
)

@Composable
fun FlickTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
