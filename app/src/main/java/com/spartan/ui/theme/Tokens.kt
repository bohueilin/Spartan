package com.spartan.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.spartan.domain.engine.PlanUrgency
import com.spartan.domain.engine.RecoveryZone
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.ReadinessBand

/**
 * Spartan design tokens — a small, disciplined system so every screen reads as one product.
 * Spacing follows a 4/8/12/16/20/24 scale; radii, motion timings, and readiness-band colors are
 * centralized so nothing drifts.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val section = 32.dp // between page sections
}

/**
 * Corner radii, the one source for both SpartanShapes (Theme.kt) and hand-drawn shapes: chip = shapes
 * extraSmall, tile = small, medium = medium, card = large, extraLarge = extraLarge. Metric and action
 * cards are [card] (20dp), the one hero per screen is [hero] (28dp), chips and the primary button are
 * full pills (CircleShape). The action row's completion morph runs card → extraLarge (20 → 28dp).
 */
object Radius {
    val chip = 8.dp
    val tile = 12.dp
    val medium = 16.dp
    val card = 20.dp
    val extraLarge = 28.dp
    val hero = extraLarge
}

/** Every action row and icon button is at least this tall and wide, even when its icon is smaller. */
val MinTouchTarget = 48.dp

/**
 * Motion is spring-based and only answers a state change — checking an action, revealing the
 * verdict, drawing a chart. Nothing loops, and every caller checks [rememberReducedMotion] first and
 * shows the final state instead. The tween durations remain for opacity fades and the Lottie morph
 * (the check pop itself is res/raw/completion_morph).
 */
object Motion {
    const val fast = 140      // micro-interactions (check-off)
    const val medium = 220    // card expand / state change
    const val slow = 420      // reveal moments (readiness ring sweep)

    /**
     * For anything that draws a real value — the recovery ring, a chart line. Critically damped, so
     * it never overshoots: a ring for 64% must never show 66% on its way there.
     */
    fun <T> measure(): SpringSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow)

    /** A layout or colour settling after a state change (expand, reflow, container tint). */
    fun <T> settle(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
}

/**
 * True while the calmer theme is on. [SpartanTheme] provides it and [rememberReducedMotion] reads
 * it, so the calmer theme stills the motion that checks rememberReducedMotion: sweeps, expands,
 * item placements and the one-shot Lottie moments. Library motion that never asks (sheet slides,
 * switch thumbs) follows only the system reduce-motion setting.
 */
val LocalCalmMode = staticCompositionLocalOf { false }

/**
 * True when the user has disabled animations system-wide (animator duration scale 0) or turned on
 * the calmer theme ([LocalCalmMode]). Movement animations (sweeps, expands, placements), list-item
 * fades and the one-shot moments (ring sweep, chart draw-in, check pop) show their final state
 * straight away; a one-shot is still used up, so it never plays later on an ordinary visit.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val systemAnimationsOff = remember {
        Settings.Global.getFloat(context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    return systemAnimationsOff || LocalCalmMode.current
}

/**
 * The semantic status palette: readiness bands, recovery zones, tones and urgency all read from
 * here. Theme-aware, and never changed by the calmer theme or wallpaper colours. Each colour is
 * always shown with its word and number — never colour alone.
 *
 * Light text colours meet 4.5:1 on white, on surfaceVariant and on their own 14% (StatusPill) and
 * 18% (urgency chip) alpha tints (TokenContrastTest checks every pair). The spec's zone colours
 * #1B7A4D and #C47A00 do not: green is 4.39:1 on its 14% tint and amber 3.43:1 on white, so text
 * uses the darkened [primedLight] #15693C (5.12:1 on 18%) and [easyLight] #875200 (4.96:1 on 18%),
 * and the spec values live on as [SpartanZones] marks, where non-text 3:1 applies. Red #B3261E
 * passes as given.
 */
object SpartanBands {
    val primedDark = Color(0xFF38D07E)
    val easyDark = Color(0xFFE7B25A)
    val restDark = Color(0xFFE67A5A)
    val primedLight = Color(0xFF15693C)
    val easyLight = Color(0xFF875200)
    val restLight = Color(0xFFB3261E)
}

/**
 * The recovery zones as marks — rings, chart lines, the completion check — and on the hero.
 * A chart line sits on its own shaded band and area fill, so every mark must reach 3:1 against its
 * own 14% band with the 18% area on top, not just against white (TokenContrastTest). Green #1B7A4D
 * and red #B3261E are the spec's values (3.48 / 3.92:1 there). The spec's amber #C47A00 is only
 * 2.45:1 there (3.43:1 on white), so the amber mark is the darker #A36500 (3.19:1 on its band,
 * 4.75:1 on white). Dark surfaces reuse the dark band colours. The spec's amber fill #F7C948 is
 * 1.57:1 on white, so it appears only on the deep-teal hero ([amberOnHero]). The on-hero colours
 * are ≥4.5:1 on both hero containers, so they may also be text there.
 */
object SpartanZones {
    val greenMarkLight = Color(0xFF1B7A4D)
    val amberMarkLight = Color(0xFFA36500)
    val redMarkLight = Color(0xFFB3261E)
    val greenOnHero = Color(0xFF7EE2A8)
    val amberOnHero = Color(0xFFF7C948)
    val redOnHero = Color(0xFFFFB4AB)
}

@Composable
fun bandColor(band: ReadinessBand?): Color {
    val dark = isSystemInDarkTheme()
    return when (band) {
        ReadinessBand.PRIMED -> if (dark) SpartanBands.primedDark else SpartanBands.primedLight
        ReadinessBand.BALANCED -> MaterialTheme.colorScheme.primary
        ReadinessBand.EASY -> if (dark) SpartanBands.easyDark else SpartanBands.easyLight
        ReadinessBand.REST -> if (dark) SpartanBands.restDark else SpartanBands.restLight
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * WHOOP's recovery colour (Green / Yellow / Red) for text and icons on a surface, in the same
 * verified-contrast semantic palette as [bandColor]. Always shown with its word and number — never
 * colour alone. A Yellow recovery is never teal or green.
 */
@Composable
fun zoneColor(zone: RecoveryZone): Color {
    val dark = isSystemInDarkTheme()
    return when (zone) {
        RecoveryZone.GREEN -> if (dark) SpartanBands.primedDark else SpartanBands.primedLight
        RecoveryZone.YELLOW -> if (dark) SpartanBands.easyDark else SpartanBands.easyLight
        RecoveryZone.RED -> if (dark) SpartanBands.restDark else SpartanBands.restLight
    }
}

/** The recovery zone as a mark (ring arc, chart line, dot) on a surface: ≥3:1 against it. */
@Composable
fun zoneMarkColor(zone: RecoveryZone): Color {
    val dark = isSystemInDarkTheme()
    return when (zone) {
        RecoveryZone.GREEN -> if (dark) SpartanBands.primedDark else SpartanZones.greenMarkLight
        RecoveryZone.YELLOW -> if (dark) SpartanBands.easyDark else SpartanZones.amberMarkLight
        RecoveryZone.RED -> if (dark) SpartanBands.restDark else SpartanZones.redMarkLight
    }
}

/** The recovery zone on the deep-teal hero (either theme): ≥4.5:1 there, so text or mark. */
fun zoneOnHeroColor(zone: RecoveryZone): Color = when (zone) {
    RecoveryZone.GREEN -> SpartanZones.greenOnHero
    RecoveryZone.YELLOW -> SpartanZones.amberOnHero
    RecoveryZone.RED -> SpartanZones.redOnHero
}

/**
 * The one hero surface per screen: a filled deep teal in both themes, so the zone colours placed on
 * it keep one verified contrast. It is not derived from the colour scheme on purpose — wallpaper
 * colours could otherwise put a Yellow ring on a yellow card. The calmer theme quiets it to a
 * lower-chroma teal ([CalmHero]) that keeps every pair above 4.5:1.
 */
@Immutable
data class HeroColors(
    val container: Color,
    val content: Color,
    val contentVariant: Color,
    /** The ring track: white at 16% over the container, ≥3:1 under each on-hero zone colour. */
    val track: Color,
)

internal val StandardHero = HeroColors(
    container = Color(0xFF0B5B4F),
    content = Color.White,
    contentVariant = Color(0xFFC9E3DC),
    track = Color(0xFF32756B),
)

internal val CalmHero = HeroColors(
    container = Color(0xFF2F4F4A),
    content = Color.White,
    contentVariant = Color(0xFFC9E3DC),
    track = Color(0xFF506B67),
)

@Composable
fun heroColors(): HeroColors = if (LocalCalmMode.current) CalmHero else StandardHero

/**
 * Chart identity only — never zone meaning (Recovery takes its zone mark colour instead). Each
 * signal on Insights gets its own: HRV teal, resting heart rate violet, sleep deep blue,
 * respiratory rate ocean, strain slate. Light values are ≥3:1 (in fact ≥4.5:1) on white; dark
 * values ≥4.5:1 on the dark surfaces.
 */
object MetricAccents {
    val hrvLight = Color(0xFF0A7A6D)
    val hrvDark = Color(0xFF5FD4C4)
    val restingHrLight = Color(0xFF6A4C9C)
    val restingHrDark = Color(0xFFC9B6F2)
    val sleepLight = Color(0xFF2F4DA8)
    val sleepDark = Color(0xFFA9B8FF)
    val respiratoryLight = Color(0xFF1F6A8A)
    val respiratoryDark = Color(0xFF8FCFEA)
    val strainLight = Color(0xFF4A5A66)
    val strainDark = Color(0xFFB7C4C2)
}

/**
 * A metric's chart colour. [zone] is the recovery zone for the value being charted; it is only
 * read for Recovery, which is always charted in its zone's mark colour.
 */
@Composable
fun metricAccent(metric: MetricType, zone: RecoveryZone? = null): Color {
    val dark = isSystemInDarkTheme()
    return when (metric) {
        MetricType.RECOVERY_SCORE -> zone?.let { zoneMarkColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
        MetricType.HRV_RMSSD -> if (dark) MetricAccents.hrvDark else MetricAccents.hrvLight
        MetricType.RESTING_HEART_RATE -> if (dark) MetricAccents.restingHrDark else MetricAccents.restingHrLight
        MetricType.SLEEP_PERFORMANCE, MetricType.SLEEP_DURATION, MetricType.SLEEP_DEBT ->
            if (dark) MetricAccents.sleepDark else MetricAccents.sleepLight
        MetricType.RESPIRATORY_RATE -> if (dark) MetricAccents.respiratoryDark else MetricAccents.respiratoryLight
        MetricType.DAY_STRAIN -> if (dark) MetricAccents.strainDark else MetricAccents.strainLight
        else -> MaterialTheme.colorScheme.primary
    }
}

/**
 * The completion check's filled circle and its check mark: zone green, never the brand teal, so a
 * done action reads as done. ≥3:1 against the row surface and between circle and mark.
 */
@Composable
fun completionGreen(): Color = if (isSystemInDarkTheme()) SpartanBands.primedDark else SpartanZones.greenMarkLight

@Composable
fun onCompletionGreen(): Color = if (isSystemInDarkTheme()) Color(0xFF04211D) else Color.White

fun bandLabel(band: ReadinessBand): String = when (band) {
    ReadinessBand.PRIMED -> "Primed"
    ReadinessBand.BALANCED -> "Balanced"
    ReadinessBand.EASY -> "Take it easy"
    ReadinessBand.REST -> "Recovery day"
}

/**
 * Time-of-day urgency color for an incomplete plan item: amber once it's past midday, red after
 * 6pm, null before then. Reuses the readiness-band amber/red so the palette stays coherent and
 * theme-aware (the bright variants fail contrast on light surfaces, so light mode darkens them).
 */
@Composable
fun planUrgencyColor(urgency: PlanUrgency): Color? {
    val dark = isSystemInDarkTheme()
    return when (urgency) {
        PlanUrgency.DUE -> if (dark) SpartanBands.easyDark else SpartanBands.easyLight
        PlanUrgency.OVERDUE -> if (dark) SpartanBands.restDark else SpartanBands.restLight
        PlanUrgency.NONE -> null
    }
}
