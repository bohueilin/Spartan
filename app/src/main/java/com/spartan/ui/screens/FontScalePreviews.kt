package com.spartan.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.ReadinessBand
import com.spartan.domain.model.TimeOfDay
import com.spartan.ui.theme.SpartanTheme

/**
 * Large-font QA previews (docs/Spartan_Enhancements.md §4): render the hero screen at 1.5× and
 * 2.0× font scale in Android Studio to catch clipped cards / unreachable controls before release.
 * Preview-only sample state — never shipped behavior.
 */
private fun previewState() = MainUiState(
    onboardingComplete = true,
    planHeadline = "Go lighter today and protect recovery.",
    readinessBand = ReadinessBand.EASY,
    recoveryScore = 42,
    whoopIsMock = true,
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
