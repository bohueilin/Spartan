package com.spartan.ui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    /**
     * A cold start shows the system splash before `setContent` attaches, so the very first test
     * after an install can query the tree before any hierarchy exists. Wait it out.
     */
    private fun awaitComposeHierarchy() {
        composeRule.waitUntil(20_000) {
            runCatching { composeRule.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty() }
                .getOrDefault(false)
        }
    }

    private fun completeOnboardingIfShown() {
        awaitComposeHierarchy()
        // Fresh installs land on onboarding; subsequent runs land on the check-in.
        val onboarding = composeRule.onAllNodesWithText("Begin").fetchSemanticsNodes()
        if (onboarding.isNotEmpty()) {
            composeRule.onNodeWithText("What should we call you?").performScrollTo().performTextInput("Tester")
            // The onboarding form scrolls; Begin can sit below the fold on shorter screens.
            composeRule.onNodeWithText("Begin").performScrollTo().performClick()
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
    fun skippingAnActivity_confirmsWithAnUndoableSnackbar() {
        completeOnboardingIfShown()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("TODAY'S PLAN").fetchSemanticsNodes().isNotEmpty()
        }
        // Open the first activity's overflow menu and skip it.
        composeRule.onAllNodesWithContentDescription("More options for", substring = true)
            .onFirst()
            .performClick()
        composeRule.onNodeWithText("Skip today").performClick()

        // Every reversible action must confirm itself and offer a way back.
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Undo").performClick()

        // After undo the activity is planned again, so its card no longer reads as skipped.
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Skipped for today").fetchSemanticsNodes().isEmpty()
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
