package com.spartan.ui

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.spartan.R
import com.spartan.data.local.UserProfileEntity
import com.spartan.domain.engine.Goal
import com.spartan.domain.engine.GoalEngine
import com.spartan.domain.engine.GoalType
import com.spartan.domain.engine.ProjectionEngine
import com.spartan.domain.engine.Signal
import com.spartan.domain.engine.SignalPosition
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.WeeklyReviewSummary
import com.spartan.ui.screens.DAY_CHECK_MILLIS
import com.spartan.ui.screens.MainUiState
import com.spartan.ui.screens.ProgressScreen
import com.spartan.ui.screens.rememberCurrentDay
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
import java.time.LocalDate

/**
 * The whole Progress screen with spec §1's real week: with no goal the goal hero comes first and
 * its setup is the action, the week stats follow as context under a header that does not repeat
 * the subtitle's "This week", the projection disclaimer stays while every medical disclaimer (You →
 * About, Today's one line) is gone, and a trend row still opens its metric and tells TalkBack where
 * it goes; with a habit goal its weekly count is said once, in the hero, and the active days once,
 * on their tile; and the day the week is read from follows the clock past midnight. A tall window
 * composes every item of the list at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp")
class ProgressScreenTest {

    private val composeRule = createComposeRule()

    /** As in LottieMomentsTest: release-variant unit tests have no ui-test-manifest activity entry. */
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

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Seven daily readings ending two days ago, as when the newest WHOOP data is from Friday. */
    private fun signal(metric: MetricType, low: Double, high: Double, vararg values: Double): Signal {
        val newest = LocalDate.now().minusDays(2)
        val dates = values.indices.map { newest.minusDays((values.size - 1 - it).toLong()) }
        return Signal(metric, values.last(), values.toList(), low, high, SignalPosition.WITHIN, dates)
    }

    private val state = MainUiState(
        profile = UserProfileEntity(),
        whoopIsMock = false,
        consistencyDays7 = 1,
        consistencyFlags = listOf(false, false, false, false, false, false, true),
        signals = listOf(
            signal(MetricType.RECOVERY_SCORE, 49.0, 78.0, 52.0, 58.0, 61.0, 81.0, 59.0, 63.0, 64.0),
            signal(MetricType.HRV_RMSSD, 28.0, 38.0, 29.0, 30.0, 31.0, 30.0, 32.0, 31.0, 33.0),
            signal(MetricType.SLEEP_PERFORMANCE, 73.0, 83.0, 78.0, 80.0, 77.0, 79.0, 76.0, 78.0, 77.0),
        ),
        projections = ProjectionEngine().project(66.0, 33.0, 64, 1),
        review = WeeklyReviewSummary(
            adherencePercent = 0, zone2Minutes = 0, strengthSessions = 0,
            latestWeight = null, sevenDayWeightAverage = null, latestRhr = null, sevenDayRhrAverage = null,
            latestBp = null, latestFastingGlucose = null,
            improved = listOf("You collected data to make next week more informed."),
            needsAttention = listOf("No urgent trend flags from the current local data."),
            nextWeekFocus = "Hold steady and confirm improvement with repeat measurements.",
        ),
    )

    private fun show(onOpenMetric: (MetricType) -> Unit = {}, state: MainUiState = this.state) {
        composeRule.setContent {
            SpartanTheme {
                ProgressScreen(
                    state = state,
                    onSaveGoal = { _, _, _ -> },
                    onAbandonGoal = {},
                    onDismissGoalNotice = {},
                    onOpenMetric = onOpenMetric,
                    onOpenTrainingWeek = {},
                )
            }
        }
    }

    private fun top(text: String) = composeRule.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y

    @Test
    fun noGoal_theGoalHeroComesFirst_andTheWeekIsContextBelowIt() {
        show()

        composeRule.onNodeWithText("Progress").assertExists()
        composeRule.onNodeWithText("Set one focused goal").assertExists()
        assertTrue(top("One focused goal shapes your week") < top("At a glance"))
        assertTrue(top("At a glance") < top("Consistency"))
        // "This week" is said once, by the subtitle with its dates, never again as the header.
        composeRule.onAllNodesWithText("This week", substring = true).assertCountEquals(1)
        composeRule.onNodeWithText("This week · ", substring = true).assertExists()
        assertTrue(top("Consistency") < top("Where this can take you"))
        composeRule.onNodeWithText("Avg sleep performance").assertExists()
        composeRule.onNodeWithText("Strength sessions").assertExists()
    }

    @Test
    fun theProjectionDisclaimerStays_andTheFullWellnessFooterIsGone() {
        show()

        composeRule.onNodeWithText(ProjectionEngine.DISCLAIMER).assertExists()
        composeRule.onNodeWithText("Your lever right now is consistency, not these numbers.").assertExists()
        composeRule.onNodeWithText(context.getString(R.string.wellness_disclaimer)).assertDoesNotExist()
        // The projections say what their ranges are; no medical line rides along with them.
        composeRule.onNodeWithText("medical advice", substring = true).assertDoesNotExist()
        // The training week is the last item, so nothing after it was missed.
        composeRule.onNodeWithText("Your training week").assertExists()
    }

    @Test
    fun aTrendRow_opensItsMetric() {
        val opened = mutableListOf<MetricType>()
        show(onOpenMetric = { opened += it })

        composeRule.onNodeWithText("Your trends").assertExists()
        composeRule.onNode(hasText("HRV", substring = true) and hasClickAction()).performClick()
        assertEquals(listOf(MetricType.HRV_RMSSD), opened)
    }

    @Test
    fun aTrendRow_tellsTalkBackWhereItGoes() {
        show()

        val row = composeRule.onNode(hasText("HRV", substring = true) and hasClickAction()).fetchSemanticsNode()
        assertEquals(context.getString(R.string.checkin_open_metric, "HRV"), row.config[SemanticsActions.OnClick].label)
    }

    @Test
    fun aHabitGoal_isCountedOnceInTheHero_andTheActiveDaysOnceOnTheirTile() {
        val today = LocalDate.now().toEpochDay()
        val fitness = Goal("g", GoalType.FITNESS, targetValue = 3.0, baselineValue = 62.0, startEpochDay = today - 7, targetEpochDay = today + 21)
        show(state = state.copy(activeGoal = fitness, goalProgress = GoalEngine.progress(fitness, emptyList(), today, trainingSessionsThisWeek = 2)))

        val spoken = composeRule.onAllNodes(SemanticsMatcher("any node") { true }).fetchSemanticsNodes().flatMap { node ->
            node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text } +
                node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
        }
        assertEquals(spoken.toString(), listOf("2 of 3 this week"), spoken.filter { Regex("""\b2 of 3\b""").containsMatchIn(it) })
        // Active on 1 of 7 days: the stat tile says it, and the Consistency card frames the run instead.
        assertEquals(spoken.toString(), listOf("1 of 7"), spoken.filter { Regex("""\b1 of (the last )?7\b""").containsMatchIn(it) })
        composeRule.onNodeWithText("Your run has started. Each active day adds to it.").assertExists()
        composeRule.onNodeWithText("goal session", substring = true).assertDoesNotExist()
    }

    @Test
    fun theDay_followsTheClockPastMidnight_whileTheScreenStaysOpen() {
        // The minute ticker never ends, so time moves only when the test says so.
        composeRule.mainClock.autoAdvance = false
        var clock = LocalDate.of(2026, 10, 25)
        composeRule.setContent { Text(rememberCurrentDay { clock }.toString()) }
        composeRule.onNodeWithText("2026-10-25").assertExists()

        clock = LocalDate.of(2026, 10, 26)
        composeRule.mainClock.advanceTimeBy(DAY_CHECK_MILLIS)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("2026-10-26").assertExists()
        composeRule.onNodeWithText("2026-10-25").assertDoesNotExist()
    }
}
