package com.spartan.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Spartan visual language: calm, confident, high-contrast. A barely-tinted light (or near-black
 * dark) canvas, flat outlined cards (no stacked shadows), one filled deep-teal hero per screen, and
 * a single deep-teal accent (#0B5B4F) so state reads instantly. Secondary text is the darkened
 * #46564F (7.77:1 on white); TokenContrastTest checks every text and mark pair in all four schemes.
 *
 * Every Material 3 colour role is defined on purpose. Any role left unset falls back to M3's
 * baseline purple palette, which is what used to tint the navigation bar and sheets lavender.
 */
internal val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF0B5B4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEEE8),
    onPrimaryContainer = Color(0xFF0B2E28),
    secondary = Color(0xFF2C3A44),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3ECEA),
    onSecondaryContainer = Color(0xFF13201E),
    tertiary = Color(0xFF7E5613),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7E7C8),
    onTertiaryContainer = Color(0xFF2A1A00),
    background = Color(0xFFF6F8F7),
    onBackground = Color(0xFF101A18),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101A18),
    surfaceVariant = Color(0xFFF2F5F3),
    onSurfaceVariant = Color(0xFF46564F),
    surfaceTint = Color(0xFF0B5B4F),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE1E6E5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F8F8),
    surfaceContainer = Color(0xFFF2F4F4),
    surfaceContainerHigh = Color(0xFFECEFEF),
    surfaceContainerHighest = Color(0xFFE6ECEB),
    outline = Color(0xFF738581),
    outlineVariant = Color(0xFFDDE4E2),
    inverseSurface = Color(0xFF1F2B29),
    inverseOnSurface = Color(0xFFEEF3F2),
    inversePrimary = Color(0xFF84D5C4),
)

internal val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF84D5C4),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005046),
    onPrimaryContainer = Color(0xFFA0F2DF),
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
    surfaceTint = Color(0xFF84D5C4),
    surfaceBright = Color(0xFF1E2725),
    surfaceDim = Color(0xFF0A0F0E),
    surfaceContainerLowest = Color(0xFF070B0A),
    surfaceContainerLow = Color(0xFF101615),
    surfaceContainer = Color(0xFF141B19),
    surfaceContainerHigh = Color(0xFF1A2220),
    surfaceContainerHighest = Color(0xFF1E2A26),
    outline = Color(0xFF6B7D77),
    outlineVariant = Color(0xFF2A3532),
    inverseSurface = Color(0xFFEAF1EF),
    inverseOnSurface = Color(0xFF1A2220),
    inversePrimary = Color(0xFF0B5B4F),
)

// Every role reads its radius from [Radius], so hand-drawn shapes (the completion morph) and the
// theme's shape roles can never drift apart.
private val SpartanShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.chip),
    small = RoundedCornerShape(Radius.tile),
    medium = RoundedCornerShape(Radius.medium),
    large = RoundedCornerShape(Radius.card),
    extraLarge = RoundedCornerShape(Radius.extraLarge),
)

/**
 * The calmer theme (You → Display): the same layout and neutrals with the accent, container and
 * tertiary chroma roughly halved in OKLCH. Surfaces, outlines and onSurface roles are copied
 * unchanged, so their audited contrast holds, and the semantic recovery-zone / band colours
 * (SpartanBands, SpartanZones) never change with it. The hero quiets separately (CalmHero), and the
 * calmer theme also stills celebration motion (LocalCalmMode).
 *
 * Contrast is by the WCAG 2.x formula — (L1 + 0.05) / (L2 + 0.05) on linearised sRGB, tints
 * alpha-composited over surface first; text needs 4.5:1, non-text 3:1. TokenContrastTest asserts
 * every changed pair (onPrimary on primary, primary on surface / background / its 12% and 14%
 * tints, the containers, tertiary, inversePrimary) for both calm schemes.
 */
internal val CalmLightColors: ColorScheme = LightColors.copy(
    primary = Color(0xFF3E625C),
    primaryContainer = Color(0xFFE2EBE9),
    onPrimaryContainer = Color(0xFF15211F),
    secondary = Color(0xFF3A4448),
    secondaryContainer = Color(0xFFE8EDEC),
    onSecondaryContainer = Color(0xFF182120),
    tertiary = Color(0xFF6F5B3E),
    tertiaryContainer = Color(0xFFF0E9DE),
    onTertiaryContainer = Color(0xFF261E12),
    surfaceTint = Color(0xFF3E625C),
    inversePrimary = Color(0xFFA6C8C1),
)

internal val CalmDarkColors: ColorScheme = DarkColors.copy(
    primary = Color(0xFF9DC4BC),
    onPrimary = Color(0xFF132420),
    primaryContainer = Color(0xFF223330),
    onPrimaryContainer = Color(0xFFD2E4E0),
    secondary = Color(0xFFB5C0BE),
    secondaryContainer = Color(0xFF212A28),
    onSecondaryContainer = Color(0xFFD8E0DE),
    tertiary = Color(0xFFD5BC93),
    onTertiary = Color(0xFF2A2114),
    tertiaryContainer = Color(0xFF3A3224),
    onTertiaryContainer = Color(0xFFEFE2CA),
    surfaceTint = Color(0xFF9DC4BC),
    inversePrimary = Color(0xFF3E625C),
)

/**
 * Spartan's theme. [calmer] picks the lower-chroma scheme and provides [LocalCalmMode], which
 * stills the motion that checks rememberReducedMotion (sweeps, expands, Lottie moments); library
 * motion such as sheet slides follows only the system setting. [wallpaperColours] uses the
 * system's wallpaper-derived scheme on Android 12+ (ignored below, where there is none); it wins
 * over the calmer colours when both are on — the user picked their own palette — while calm
 * motion stays.
 */
@Composable
fun SpartanTheme(
    calmer: Boolean = false,
    wallpaperColours: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val colorScheme = when {
        wallpaperColours && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        calmer -> if (dark) CalmDarkColors else CalmLightColors
        else -> if (dark) DarkColors else LightColors
    }
    CompositionLocalProvider(LocalCalmMode provides calmer) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SpartanTypography,
            shapes = SpartanShapes,
            content = content,
        )
    }
}
