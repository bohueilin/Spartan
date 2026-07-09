package com.spartan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Spartan visual language: disciplined, calm, high-contrast. A near-neutral canvas with a single
 * confident accent, so activity cards and readiness state read instantly. Dark-forward but fully
 * themed for light as well.
 */

// A crisp, athletic teal-cyan accent reads as "readiness" without feeling clinical.
private val SpartanAccent = Color(0xFF15C9B0)
private val SpartanAccentDark = Color(0xFF3FE0C8)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF0E7C6E),
    onPrimary = Color.White,
    secondary = Color(0xFF2C3A44),
    tertiary = Color(0xFF9A6A1B),
    background = Color(0xFFF6F8F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6ECEB),
    onSurface = Color(0xFF11201E),
    onSurfaceVariant = Color(0xFF4A5654),
    outline = Color(0xFFC3CFCD),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = SpartanAccentDark,
    onPrimary = Color(0xFF04211D),
    secondary = Color(0xFFB7C4C2),
    tertiary = Color(0xFFE7B25A),
    background = Color(0xFF0A0F0E),
    surface = Color(0xFF121817),
    surfaceVariant = Color(0xFF1E2A26),
    onSurface = Color(0xFFEAF1EF),
    onSurfaceVariant = Color(0xFF9DB0AB),
    outline = Color(0xFF293630),
)

@Suppress("unused")
val SpartanAccentColor = SpartanAccent

@Composable
fun SpartanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
