package com.spartan.ui

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.spartan.domain.engine.Goal
import com.spartan.domain.engine.GoalEngine
import com.spartan.domain.engine.GoalPreset
import com.spartan.domain.engine.GoalProgress
import com.spartan.domain.engine.GoalStatus
import com.spartan.domain.engine.GoalType
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.ui.components.OneShotKey
import com.spartan.ui.screens.GOAL_RING_STROKE
import com.spartan.ui.screens.GoalCard
import com.spartan.ui.screens.MainUiState
import com.spartan.ui.theme.SpartanTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The Progress goal hero on the JVM: a habit goal's ring says the real, uncapped count (and before
 * the first session, what is to go — never a big 0), and the count is said once — only the engine's
 * body-signal context sits under the ring; an outcome goal's ring states its real share of the way
 * to the goal in words that cannot be read as the metric, and no caption ever crosses the ring's
 * stroke at 100% or 200% text; with no goal the hero's one button opens the wizard, and a paused
 * goal's one primary action resumes it. A plain Application keeps app start-up out of UI tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GoalCardRingTest {

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

    private val today = LocalDate.now().toEpochDay()

    private val buttons = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    /** "2 of 3", "7 of 5", "3 to go": a statement of this week's session count. */
    private val weeklyCount = Regex("""\b\d+ of \d+\b|\b\d+ to go\b""")

    /** Every text and content description: as TalkBack hears them (merged), or as drawn (unmerged). */
    private fun strings(unmerged: Boolean): List<String> =
        composeRule.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = unmerged).fetchSemanticsNodes().flatMap { node ->
            node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text } +
                node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
        }

    private fun showGoalCard(
        state: MainUiState,
        onOpenWizard: (GoalPreset?) -> Unit = {},
        onResume: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            SpartanTheme {
                GoalCard(state, onOpenWizard = onOpenWizard, onAbandon = {}, onResume = onResume)
            }
        }
    }

    /** The goal card in a 312dp column (a 360dp phone less its gutters) at a font scale that can change. */
    private fun showGoalCardAt(state: MainUiState, fontScale: () -> Float) {
        composeRule.setContent {
            SpartanTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale())) {
                    Box(Modifier.width(312.dp)) { GoalCard(state, onOpenWizard = {}, onAbandon = {}, onResume = {}) }
                }
            }
        }
    }

    /** The ring's own square: its canvas, the one node carrying [OneShotKey]. */
    private fun ringBounds(): Rect =
        composeRule.onNode(SemanticsMatcher.keyIsDefined(OneShotKey), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun drawnBounds(text: String): Rect = composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /** True when [box] lies wholly inside the ring's inner circle (inside its stroke). */
    private fun insideTheStroke(box: Rect, ring: Rect): Boolean {
        val inner = ring.width / 2 - with(composeRule.density) { GOAL_RING_STROKE.toPx() }
        return listOf(box.topLeft, box.topRight, box.bottomLeft, box.bottomRight).all { (it - ring.center).getDistance() <= inner + 0.5f }
    }

    /** True when [box] lies wholly under the ring and within its width. */
    private fun underTheRing(box: Rect, ring: Rect): Boolean =
        box.top >= ring.bottom - 0.5f && box.left >= ring.left - 0.5f && box.right <= ring.right + 0.5f

    private fun fitness(target: Double) =
        Goal("g", GoalType.FITNESS, targetValue = target, baselineValue = 62.0, startEpochDay = today - 7, targetEpochDay = today + 21)

    @Test
    fun habitGoal_ringSaysTheUncappedCount_once_withOnlyTheContextUnderIt() {
        // Resting HR 60.5 bpm this week against a 62 bpm baseline: the engine's real context note.
        val restingHr = (0..6).map { MetricReading(MetricType.RESTING_HEART_RATE, 60.5, LocalDate.ofEpochDay(today - it)) }
        val progress = GoalEngine.progress(fitness(5.0), restingHr, today, trainingSessionsThisWeek = 7)
        showGoalCard(MainUiState(activeGoal = fitness(5.0), goalProgress = progress))

        // Not "5 of 5": the ring is full, but the words count every session.
        composeRule.onNodeWithContentDescription("7 of 5 this week").assertExists()
        composeRule.onNodeWithText("Resting HR -1.5 bpm since your goal started").assertExists()
        composeRule.onNodeWithText("baseline", substring = true).assertDoesNotExist()
        // The engine's summary repeats the count, so it is not under the ring.
        composeRule.onNodeWithText(progress.summary).assertDoesNotExist()
        val spoken = strings(unmerged = false)
        assertEquals(spoken.toString(), listOf("7 of 5 this week"), spoken.filter { weeklyCount.containsMatchIn(it) })
        composeRule.onNodeWithText("On track").assertExists()
        composeRule.onNodeWithText("Get fitter", substring = true).assertExists()
    }

    @Test
    fun habitGoal_beforeTheFirstSession_saysWhatIsToGo_once_neverZeroOf() {
        // No readings this week, so the engine has no context note: nothing sits under the ring.
        val progress = GoalEngine.progress(fitness(3.0), emptyList(), today)
        showGoalCard(MainUiState(activeGoal = fitness(3.0), goalProgress = progress))

        composeRule.onNodeWithContentDescription("None yet this week, 3 to go").assertExists()
        val spoken = strings(unmerged = false)
        assertEquals(spoken.toString(), listOf("None yet this week, 3 to go"), spoken.filter { weeklyCount.containsMatchIn(it) })
        // Neither TalkBack nor the screen says "0 of 3", nor shows a lone 0.
        (spoken + strings(unmerged = true)).forEach { assertFalse(it, it.contains("0 of")) }
        composeRule.onNodeWithText("0", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun outcomeGoal_keepsItsSummary_andItsRealShare_withoutAWeeklyCount() {
        val summary = "3.0 of 6 lb down, 3 wk left"
        showGoalCard(
            MainUiState(
                activeGoal = Goal("g", GoalType.WEIGHT_LOSS, targetValue = 6.0, baselineValue = 80.0, startEpochDay = today - 7, targetEpochDay = today + 21),
                goalProgress = GoalProgress(fraction = 0.5, timeFraction = 0.25, onTrack = true, summary = summary),
            ),
        )

        composeRule.onNodeWithText(summary).assertExists()
        // "of the way there", never a bare "of target" that could be read as the metric itself.
        composeRule.onNodeWithContentDescription("50% of the way to your goal").assertExists()
        composeRule.onNodeWithText("of the way there", useUnmergedTree = true).assertExists()
        (strings(unmerged = false) + strings(unmerged = true)).forEach { assertFalse(it, it.contains("of target") || it.contains("of your target")) }
        composeRule.onNodeWithText("this week", substring = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("this week", substring = true).assertDoesNotExist()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // glyphs are really measured
    @Config(qualifiers = "w360dp-h2000dp")
    fun outcomeCaption_neverCrossesTheRingStroke_at100And200PercentText() {
        var scale by mutableFloatStateOf(1f)
        val weight = Goal("g", GoalType.WEIGHT_LOSS, targetValue = 6.0, baselineValue = 80.0, startEpochDay = today - 7, targetEpochDay = today + 21)
        showGoalCardAt(MainUiState(activeGoal = weight, goalProgress = GoalProgress(fraction = 0.5, timeFraction = 0.25, onTrack = true, summary = "3.0 of 6 lb down, 3 wk left"))) { scale }

        for (fontScale in listOf(1f, 2f)) {
            scale = fontScale
            composeRule.waitForIdle()
            val ring = ringBounds()
            val caption = drawnBounds("of the way there")
            assertTrue("at ${fontScale}x: caption $caption is really measured", caption.width > 0f && caption.height > 0f)
            // "of the way there" is wider than the ring's inner circle, so it sits under the ring.
            assertTrue("at ${fontScale}x: caption $caption, ring $ring", insideTheStroke(caption, ring) || underTheRing(caption, ring))
            assertTrue("at ${fontScale}x: numeral ${drawnBounds("50%")}, ring $ring", insideTheStroke(drawnBounds("50%"), ring))
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // glyphs are really measured
    @Config(qualifiers = "w360dp-h2000dp")
    fun habitCaptions_stayInsideTheRingStroke_at100And200PercentText() {
        var scale by mutableFloatStateOf(1f)
        val goal = fitness(5.0)
        showGoalCardAt(MainUiState(activeGoal = goal, goalProgress = GoalEngine.progress(goal, emptyList(), today))) { scale }

        for (fontScale in listOf(1f, 2f)) {
            scale = fontScale
            composeRule.waitForIdle()
            val ring = ringBounds()
            assertTrue("at ${fontScale}x: to go ${drawnBounds("to go")}, ring $ring", insideTheStroke(drawnBounds("to go"), ring))
            assertTrue("at ${fontScale}x: numeral ${drawnBounds("5")}, ring $ring", insideTheStroke(drawnBounds("5"), ring))
        }
    }

    @Test
    fun noGoal_theHeroHoldsExactlyOnePrimaryButton_thatOpensTheWizard() {
        val opened = mutableListOf<GoalPreset?>()
        showGoalCard(MainUiState(), onOpenWizard = { opened += it })

        composeRule.onNodeWithText("One focused goal shapes your week").assertExists()
        composeRule.onAllNodes(buttons).assertCountEquals(1)
        composeRule.onNodeWithText("Set one focused goal").performClick()
        // A fresh wizard, not pre-selected on any preset.
        assertEquals(listOf<GoalPreset?>(null), opened)
    }

    @Test
    fun pausedGoal_resumeIsThePrimaryAction() {
        val resumed = mutableListOf<String>()
        val paused = fitness(3.0).copy(id = "paused", status = GoalStatus.PAUSED)
        showGoalCard(MainUiState(pausedGoal = paused), onResume = { resumed += it })

        composeRule.onNodeWithText("Paused", substring = true).assertExists()
        composeRule.onNodeWithText("Resume").performClick()
        assertEquals(listOf("paused"), resumed)
    }
}
