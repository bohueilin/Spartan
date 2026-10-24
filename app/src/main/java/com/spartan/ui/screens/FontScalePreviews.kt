package com.spartan.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.spartan.domain.engine.MorningVerdict
import com.spartan.domain.engine.RecoveryZone
import com.spartan.domain.engine.StrainZone
import com.spartan.domain.engine.WhyToday
import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.ReadinessBand
import com.spartan.domain.model.TimeOfDay
import com.spartan.ui.theme.SpartanTheme
import java.time.LocalDate

/**
 * Large-font QA previews (docs/Spartan_Enhancements.md §4): render the hero screen at 1.5× and
 * 2.0× font scale in Android Studio to catch clipped cards / unreachable controls before release.
 * Preview-only sample state — never shipped behavior. It carries a Ready verdict and its "Why
 * today?" so the large-font previews show the real verdict card, not the pending one.
 */
private fun previewState() = MainUiState(
    onboardingComplete = true,
    planHeadline = "Go lighter today and protect recovery.",
    readinessBand = ReadinessBand.EASY,
    recoveryScore = 42,
    whoopIsMock = true,
    verdict = MorningVerdict.Ready(
        cycleEpochDay = LocalDate.now().toEpochDay(),
        recoveryScore = 42,
        zone = RecoveryZone.YELLOW,
        band = ReadinessBand.EASY,
        previousRecoveryScore = 58,
        strainTarget = StrainZone.LIGHT,
    ),
    whyToday = WhyToday(
        summary = "Yellow · 42% — down from 58% yesterday",
        reasons = listOf("Sleep was 6h 50m of about 8h 10m needed.", "HRV is below your usual."),
        conclusion = "So today is lighter so tomorrow can be bigger — rest is part of the plan.",
    ),
    strainTarget = StrainZone.LIGHT,
    sleptHours = 6.83,
    sleepNeedHours = 8.17,
    dataThroughEpochDay = LocalDate.now().toEpochDay(),
    todayActivities = listOf(
        DailyActivity(
            id = "p:mobility",
            title = "10-minute mobility flow",
            category = ActivityCategory.MOBILITY,
            priority = ActivityPriority.REQUIRED,
            whyItMatters = "Your recovery is low today, so gentle movement supports blood flow without adding strain.",
            instructions = listOf("Move through hips and ankles.", "Breathe easily throughout."),
            estimatedMinutes = 10,
            intensity = Intensity.EASY,
            bestTimeOfDay = TimeOfDay.MORNING,
            ruleId = "LOW_RECOVERY",
        ),
    ),
)

@Preview(name = "Check-in 1.0x", fontScale = 1.0f, showBackground = true, backgroundColor = 0xFF0A0F0E)
@Composable
private fun CheckInPreviewDefault() {
    SpartanTheme { CheckInScreen(previewState(), {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Check-in 1.5x", fontScale = 1.5f, showBackground = true, backgroundColor = 0xFF0A0F0E)
@Composable
private fun CheckInPreviewLarge() {
    SpartanTheme { CheckInScreen(previewState(), {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Check-in 2.0x", fontScale = 2.0f, showBackground = true, backgroundColor = 0xFF0A0F0E)
@Composable
private fun CheckInPreviewHuge() {
    SpartanTheme { CheckInScreen(previewState(), {}, {}, {}, {}, {}, {}) }
}
