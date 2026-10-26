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
 * today?" so the large-font previews show the hero verdict, not the pending one, using the real
 * screenshot data from SPARTAN_WORLD_CLASS_SPEC.md §1 (64% Yellow, 7h 34m of about 9h 50m).
 */
private fun previewState() = MainUiState(
    onboardingComplete = true,
    planHeadline = "A steady day.",
    readinessBand = ReadinessBand.BALANCED,
    recoveryScore = 64,
    whoopIsMock = true,
    verdict = MorningVerdict.Ready(
        cycleEpochDay = LocalDate.now().toEpochDay(),
        recoveryScore = 64,
        zone = RecoveryZone.YELLOW,
        band = ReadinessBand.BALANCED,
        previousRecoveryScore = 81,
        strainTarget = StrainZone.MODERATE,
    ),
    whyToday = WhyToday(
        summary = "Yellow · 64% — down from 81% yesterday",
        reasons = listOf("Sleep was 7h 34m of about 9h 50m needed.", "HRV is in your usual range."),
        conclusion = "So today is a steady day — moderate work, and an earlier night to pay down sleep debt.",
    ),
    strainTarget = StrainZone.MODERATE,
    sleptHours = 7.0 + 34.0 / 60,
    sleepNeedHours = 9.0 + 50.0 / 60,
    dataThroughEpochDay = LocalDate.now().toEpochDay(),
    todayActivities = listOf(
        DailyActivity(
            id = "p:mobility",
            title = "10-minute mobility flow",
            category = ActivityCategory.MOBILITY,
            priority = ActivityPriority.REQUIRED,
            whyItMatters = "Gentle range-of-motion work loosens you up without adding load.",
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
