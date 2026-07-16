package com.spartan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spartan.domain.engine.PlanUrgency
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
}

object Radius {
    val chip = 8.dp
    val card = 18.dp
}

object Motion {
    const val fast = 140      // micro-interactions (check-off)
    const val medium = 220    // card expand / state change
}

/**
 * The single place readiness band → color is defined. Used by the ring, band label, and progress.
 * Theme-aware: the bright variants fail WCAG 3:1 on light surfaces (verified by the contrast
 * audit in docs/ACCESSIBILITY.md), so light mode uses darkened equivalents (all ≥4.5:1 on white).
 */
object SpartanBands {
    val primedDark = Color(0xFF38D07E)
    val easyDark = Color(0xFFE7B25A)
    val restDark = Color(0xFFE67A5A)
    val primedLight = Color(0xFF0E7B43)
    val easyLight = Color(0xFF8F6410)
    val restLight = Color(0xFFB23E20)
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
