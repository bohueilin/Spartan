package com.spartan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Spartan visual language: calm, confident, high-contrast. A white (light) or near-black (dark)
 * canvas, elevated cards for depth, and a single teal accent so state reads instantly.
 *
 * Every Material 3 colour role is defined on purpose. Any role left unset falls back to M3's
 * baseline purple palette, which is what used to tint the navigation bar and sheets lavender.
 */
private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF0B685C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F0EA),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF2C3A44),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3ECEA),
    onSecondaryContainer = Color(0xFF13201E),
    tertiary = Color(0xFF7E5613),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7E7C8),
    onTertiaryContainer = Color(0xFF2A1A00),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF11201E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF11201E),
    surfaceVariant = Color(0xFFE6ECEB),
    onSurfaceVariant = Color(0xFF4A5654),
    surfaceTint = Color(0xFF0B685C),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE1E6E5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F8F8),
    surfaceContainer = Color(0xFFF2F4F4),
    surfaceContainerHigh = Color(0xFFECEFEF),
    surfaceContainerHighest = Color(0xFFE6ECEB),
    outline = Color(0xFF9BADAA),
    outlineVariant = Color(0xFFE4E9E8),
    inverseSurface = Color(0xFF1F2B29),
    inverseOnSurface = Color(0xFFEEF3F2),
    inversePrimary = Color(0xFF7FD8C8),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF3FE0C8),
    onPrimary = Color(0xFF04211D),
    primaryContainer = Color(0xFF0F3B35),
    onPrimaryContainer = Color(0xFFBFF5EA),
    secondary = Color(0xFFB7C4C2),
    onSecondary = Color(0xFF1E2A28),
    secondaryContainer = Color(0xFF1F2E2B),
    onSecondaryContainer = Color(0xFFD7E3E0),
    tertiary = Color(0xFFE7B25A),
    onTertiary = Color(0xFF2A1A00),
    tertiaryContainer = Color(0xFF3E2C08),
    onTertiaryContainer = Color(0xFFFBDDA6),
    background = Color(0xFF0A0F0E),
    onBackground = Color(0xFFEAF1EF),
    surface = Color(0xFF121817),
    onSurface = Color(0xFFEAF1EF),
    surfaceVariant = Color(0xFF1E2A26),
    onSurfaceVariant = Color(0xFF9DB0AB),
    surfaceTint = Color(0xFF3FE0C8),
    surfaceBright = Color(0xFF1E2725),
    surfaceDim = Color(0xFF0A0F0E),
    surfaceContainerLowest = Color(0xFF070B0A),
    surfaceContainerLow = Color(0xFF101615),
    surfaceContainer = Color(0xFF141B19),
    surfaceContainerHigh = Color(0xFF1A2220),
    surfaceContainerHighest = Color(0xFF1E2A26),
    outline = Color(0xFF3D4F48),
    outlineVariant = Color(0xFF232D2A),
    inverseSurface = Color(0xFFEAF1EF),
    inverseOnSurface = Color(0xFF1A2220),
    inversePrimary = Color(0xFF0B685C),
)

private val SpartanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(Radius.card),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SpartanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = SpartanTypography,
        shapes = SpartanShapes,
        content = content,
    )
}
