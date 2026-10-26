package com.spartan.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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
 * Covers: onboarding → daily check-in renders a plan; the morning verdict as colour + word +
 * number; a11y contract (labeled checkboxes, 48dp touch targets) on the hero screen.
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

    private val activityCheckbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    /**
     * Today is a lazy list: on a short screen (CI's default emulator is one) the plan starts below the
     * verdict card's fold, and rows below the fold are not composed until scrolled to. Scroll the list
     * to what a test asserts instead of assuming it is on screen. False until the list and the node
     * exist, so it can be polled while the first sync settles.
     */
    private fun scrollTodayTo(matcher: SemanticsMatcher): Boolean = runCatching {
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher)
    }.isSuccess

    // 20s: the first sync after a cold install is slow on a software-rendered CI emulator.
    private fun awaitTodaysPlan() {
        composeRule.waitUntil(20_000) { scrollTodayTo(hasText("Today's plan")) }
    }

    /** The first activity's checkbox, scrolled into view. */
    private fun firstActivityCheckbox(): SemanticsNodeInteraction {
        composeRule.waitUntil(20_000) { scrollTodayTo(activityCheckbox) }
        return composeRule.onAllNodes(activityCheckbox).onFirst()
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
        // Honesty requirement: mock mode must be visibly labeled. The chip trails Today's title, the
        // list's first item, so check it before scrolling to the plan (which can dispose the title).
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText("SAMPLE DATA").fetchSemanticsNodes().isNotEmpty()
        }
        awaitTodaysPlan()
        // The plan is non-empty: at least one activity checkbox exists.
        firstActivityCheckbox().assertExists()
    }

    @Test
    fun skippingAnActivity_confirmsWithAnUndoableSnackbar() {
        completeOnboardingIfShown()
        awaitTodaysPlan()
        // Open the first activity's sheet (its card carries the title as text) and skip it.
        val title = firstActivityCheckbox().fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].first()
        composeRule.onAllNodesWithText(title).onFirst().performScrollTo().performClick()
        composeRule.onNodeWithText("Skip today").performScrollTo().performClick()

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
    fun verdictCard_statesColourWordAndNumber_inSampleMode() {
        completeOnboardingIfShown()
        // The sample day's completed cycle scores 42: a Ready verdict, Yellow. Status is never colour
        // alone, so the card must say the word and the number in text. The verdict leads the list, so
        // it is on screen without scrolling (scrolling to the plan first could dispose it).
        val verdict = "Yellow · 42%"
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText(verdict).fetchSemanticsNodes().isNotEmpty()
        }
        // TalkBack hears the same verdict as one stop, with the provenance the chip shows sighted users.
        composeRule.onNode(hasText(verdict) and hasContentDescription("Recovery Yellow, 42 percent", substring = true))
            .assertExists()
        composeRule.onNode(hasText(verdict) and hasContentDescription("Sample data", substring = true))
            .assertExists()
    }

    @Test
    fun activityCheckbox_meetsTouchTargetMinimum() {
        completeOnboardingIfShown()
        awaitTodaysPlan()
        // First activity checkbox: 48dp minimum touch target (a11y contract from the design system).
        firstActivityCheckbox()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }
}
