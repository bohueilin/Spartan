package com.vitalcompass.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF1F6F68),
    onPrimary = Color.White,
    secondary = Color(0xFF6B5E2E),
    tertiary = Color(0xFF6E4C7E),
    background = Color(0xFFF8FAF8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE3EAE7),
    onSurface = Color(0xFF16201F),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF7FD6C9),
    secondary = Color(0xFFD8C56E),
    tertiary = Color(0xFFD7B5E8),
    background = Color(0xFF101716),
    surface = Color(0xFF18211F),
    surfaceVariant = Color(0xFF34413E),
    onSurface = Color(0xFFEAF1EE),
)

@Composable
fun VitalCompassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
