package com.spartan.ui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.unit.dp
import com.spartan.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose smoke + accessibility assertions against the REAL app in its default mock mode — the
 * deterministic sample WHOOP data acts as the test fixture, so no Hilt test doubles are needed.
 * Covers: onboarding → daily check-in renders a plan; a11y contract (labeled checkboxes,
 * 48dp touch targets) on the hero screen.
 */
@RunWith(AndroidJUnit4::class)
class CheckInScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun completeOnboardingIfShown() {
        // Fresh installs land on onboarding; subsequent runs land on the check-in.
        val onboarding = composeRule.onAllNodesWithText("Begin").fetchSemanticsNodes()
        if (onboarding.isNotEmpty()) {
            composeRule.onNodeWithText("What should we call you?").performTextInput("Tester")
            composeRule.onNodeWithText("Begin").performClick()
        }
    }

    @Test
    fun onboardingLeadsToDailyPlan_withSampleDataLabeled() {
        completeOnboardingIfShown()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("TODAY'S PLAN").fetchSemanticsNodes().isNotEmpty()
        }
        // Honesty requirement: mock mode must be visibly labeled.
        composeRule.onNodeWithText("SAMPLE DATA").assertExists()
        // The plan is non-empty: at least one activity checkbox with a content description exists.
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithContentDescription("Recovery", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun activityCheckbox_meetsTouchTargetMinimum() {
        completeOnboardingIfShown()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("TODAY'S PLAN").fetchSemanticsNodes().isNotEmpty()
        }
        // First activity checkbox: 48dp minimum touch target (a11y contract from the design system).
        composeRule.onAllNodesWithContentDescription("minute", substring = true)
            .onFirst()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }
}
