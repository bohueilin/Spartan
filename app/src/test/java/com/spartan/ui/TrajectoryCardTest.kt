package com.spartan.ui

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.test.core.app.ApplicationProvider
import com.spartan.domain.engine.ProjectionEngine
import com.spartan.ui.screens.TrajectoryCard
import com.spartan.ui.theme.SpartanTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The Progress "Where this can take you" card: each projection is a range band with one TalkBack
 * sentence in the metric's own precision (whole bpm, ms and %, no space before %), the
 * illustrative-estimate disclaimer always shows, and low consistency leads with the lever —
 * consistency itself — above the ranges, in place of the engine's pace line and without counting
 * the active days the stat tile already counts. Under each band the real value now is the emphasised label and the
 * week-8 estimate the quieter one, smaller still while the lever leads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TrajectoryCardTest {

    private val composeRule = createComposeRule()

    /** As in CalmThemeTest: release-variant unit tests have no ui-test-manifest activity entry. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Context>()
            Shadows.shadowOf(app.packageManager).addOrUpdateActivity(
                ActivityInfo().apply {
                    name = ComponentActivity::class.java.name
                    packageName = app.packageName
                },
            )
        }
    }).around(composeRule)

    private val lever = "Your lever right now is consistency, not these numbers."

    /** The style a visible label is drawn with (colour and size), read from its text layout. */
    private fun drawnStyle(text: String): TextStyle {
        val node = composeRule.onNodeWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single().layoutInput.style
    }

    /** Every chart's TalkBack sentence (the band's whole column is one node). */
    private fun spokenSummaries(): List<String> =
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).fetchSemanticsNodes()
            .flatMap { it.config[SemanticsProperties.ContentDescription] }

    @Test
    fun bands_speakOneSentenceEach_inEachMetricsOwnPrecision_withTheDisclaimer() {
        composeRule.setContent { SpartanTheme { TrajectoryCard(ProjectionEngine().project(59.0, 58.0, 42, 3), consistencyDays7 = 3) } }

        composeRule.onNodeWithText("At your current consistency of 3 days a week").assertExists()
        composeRule.onNodeWithText(ProjectionEngine.DISCLAIMER).assertExists()
        val spoken = spokenSummaries()
        assertTrue(spoken.toString(), spoken.any { Regex("""Resting heart rate: 59 bpm now\. Illustrative range by week 8: \d+ bpm to \d+ bpm\.""").matches(it) })
        assertTrue(spoken.toString(), spoken.any { Regex("""HRV: 58 ms now\. Illustrative range by week 8: \d+ ms to \d+ ms\.""").matches(it) })
        // Recovery is drawn in its zone colour, so the zone is said too — 42% is Yellow, never "Good".
        assertTrue(spoken.toString(), spoken.any { Regex("""Recovery: 42%, Yellow zone now\. Illustrative range by week 8: \d+% to \d+%\.""").matches(it) })
        // Steady enough: no lever, and no "→ … in 8 wks" payoff lines any more.
        composeRule.onNodeWithText(lever).assertDoesNotExist()
        composeRule.onAllNodesWithText(" in 8 wks", substring = true).fetchSemanticsNodes().let { assertEquals(0, it.size) }
    }

    @Test
    fun bandLabels_markNowAndWeekEight_underTheChart() {
        composeRule.setContent { SpartanTheme { TrajectoryCard(ProjectionEngine().project(59.0, null, null, 3), consistencyDays7 = 3) } }

        // The column is one TalkBack node, so its visible labels are checked in the unmerged tree.
        composeRule.onNodeWithText("Now · 59 bpm", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Week 8 · ", substring = true, useUnmergedTree = true).assertExists()
        val week8 = composeRule.onAllNodesWithText("Week 8 · ", substring = true, useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
        assertTrue(week8.toString(), week8.all { Regex("""Week 8 · \d+(–\d+)? bpm""").matches(it) })
    }

    @Test
    fun lowConsistency_leadsWithTheLever_aboveTheRanges() {
        composeRule.setContent { SpartanTheme { TrajectoryCard(ProjectionEngine().project(66.0, 33.0, 64, 1), consistencyDays7 = 1) } }

        composeRule.onNodeWithText(lever).assertExists()
        composeRule.onNodeWithText("At this pace the ranges below stay narrow", substring = true).assertExists()
        // The lever replaces the engine's pace line, and the stat tile already counts the active
        // days, so neither repeats here.
        composeRule.onNodeWithText("At your current consistency", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("of the last 7 days", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText(ProjectionEngine.DISCLAIMER).assertExists()
        // "Leads": the lever sits above the first band (positions are unclipped by the window).
        val leverTop = composeRule.onNodeWithText(lever).fetchSemanticsNode().positionInRoot.y
        val firstBand = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).fetchSemanticsNodes()
            .filter { node -> node.config[SemanticsProperties.ContentDescription].any { it.contains("Illustrative") } }
            .minOf { it.positionInRoot.y }
        assertTrue("lever $leverTop, first band $firstBand", leverTop < firstBand)
    }

    @Test
    fun noActiveDays_showsTheLeverAndCurrentValues_withoutARange() {
        composeRule.setContent { SpartanTheme { TrajectoryCard(ProjectionEngine().project(66.0, 33.0, 64, 0), consistencyDays7 = 0) } }

        composeRule.onNodeWithText("Ranges appear once you have a few active days", substring = true).assertExists()
        // The engine's "Complete a few activities…" line said the same thing again.
        composeRule.onNodeWithText(ProjectionEngine().project(66.0, 33.0, 64, 0).first().assumption).assertDoesNotExist()
        composeRule.onNodeWithText(ProjectionEngine.DISCLAIMER).assertExists()
        composeRule.onNodeWithText("Now · 66 bpm", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Week 8", substring = true, useUnmergedTree = true).assertDoesNotExist()
        assertTrue(spokenSummaries().none { it.contains("Illustrative") })
    }

    @Test
    fun theRealValueNowLeads_andWeekEightStaysQuieter() {
        lateinit var scheme: ColorScheme
        composeRule.setContent {
            SpartanTheme {
                scheme = MaterialTheme.colorScheme
                TrajectoryCard(ProjectionEngine().project(59.0, null, null, 3), consistencyDays7 = 3)
            }
        }

        val now = drawnStyle("Now · 59 bpm")
        val week8 = drawnStyle("Week 8 · ")
        assertEquals(scheme.onSurface, now.color)
        assertEquals(scheme.onSurfaceVariant, week8.color)
        assertTrue("now ${now.fontSize}, week 8 ${week8.fontSize}", week8.fontSize.value <= now.fontSize.value)
    }

    @Test
    fun whileTheLeverLeads_theProjectedNumberIsVisiblySecondary() {
        lateinit var scheme: ColorScheme
        composeRule.setContent {
            SpartanTheme {
                scheme = MaterialTheme.colorScheme
                TrajectoryCard(ProjectionEngine().project(59.0, null, null, 1), consistencyDays7 = 1)
            }
        }

        val now = drawnStyle("Now · 59 bpm")
        val week8 = drawnStyle("Week 8 · ")
        assertEquals(scheme.onSurface, now.color)
        assertEquals(scheme.onSurfaceVariant, week8.color)
        assertTrue("now ${now.fontSize}, week 8 ${week8.fontSize}", week8.fontSize.value < now.fontSize.value)
    }
}
