package com.spartan.ui

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.spartan.R
import com.spartan.domain.engine.ActionPlaybook
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.ui.components.ChartDrawSession
import com.spartan.ui.screens.DETAIL_MEANING_TAG
import com.spartan.ui.screens.MainUiState
import com.spartan.ui.screens.MetricDetailScreen
import com.spartan.ui.theme.SpartanTheme
import org.junit.After
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
 * Metric detail on the JVM: "What this means for you" is the same wellness line Insights shows —
 * never the old medical-sounding or predictive ones ("your nervous system is well rested", "a sign
 * of good recovery and aerobic fitness", "recovery should follow") — its chart is one TalkBack
 * sentence with the readings' range, the latest (Recovery's with its zone) and the usual, said
 * once, and its top action shows once, in the sticky bar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3000dp")
class MetricDetailScreenTest {

    private val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

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

    @After
    fun forgetDrawnCharts() = ChartDrawSession.reset()

    private val day0 = LocalDate.of(2026, 10, 1)

    /** One reading a day, oldest first: five days that set the usual, then [latest]. */
    private fun readings(type: MetricType, vararg values: Double) =
        values.mapIndexed { i, v -> MetricReading(type, v, day0.plusDays(i.toLong()), id = i.toLong()) }

    private fun show(type: MetricType, readings: List<MetricReading>) {
        composeRule.setContent {
            SpartanTheme {
                MetricDetailScreen(MainUiState(readings = readings), type, onBack = {}, onAdd = {}, onEdit = {}, onAddAction = { _, _ -> })
            }
        }
        composeRule.waitForIdle()
    }

    /** Every text and spoken description on screen, from the unmerged tree. */
    private fun everythingSaid(): List<String> =
        composeRule.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true).fetchSemanticsNodes().flatMap { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        }

    private val medical = listOf("nervous system", "well rested", "a sign of", "should follow", "run down", "aerobic fitness", "recovered than usual")

    /** The lines the deleted SignalEngine.meaning gave these readings. */
    private val oldLines = listOf(
        "Higher than usual — your nervous system is well rested.",
        "Lower than usual — a sign of good recovery and aerobic fitness.",
        "Better sleep than usual — recovery should follow.",
        "You're better recovered than usual — a good window for a quality session.",
    )

    private fun assertWellnessMeaning(expected: Int) {
        val line = context.getString(expected)
        composeRule.onNodeWithTag(DETAIL_MEANING_TAG).assertTextEquals(line)
        medical.forEach { phrase -> assertTrue("\"$phrase\" in: $line", !line.contains(phrase, ignoreCase = true)) }
        val said = everythingSaid()
        assertTrue(said.contains(line)) // the check below sees the screen's texts
        oldLines.forEach { old -> assertTrue(old, old !in said) }
    }

    @Test
    fun hrvAboveItsUsual_meansWhatInsightsSays_withNoNervousSystemClaim() {
        show(MetricType.HRV_RMSSD, readings(MetricType.HRV_RMSSD, 60.0, 62.0, 64.0, 66.0, 68.0, 80.0))
        assertWellnessMeaning(R.string.insights_meaning_hrv_above)
    }

    @Test
    fun restingHeartRateBelowItsUsual_isNotCalledASignOfFitness() {
        show(MetricType.RESTING_HEART_RATE, readings(MetricType.RESTING_HEART_RATE, 60.0, 62.0, 64.0, 66.0, 68.0, 50.0))
        assertWellnessMeaning(R.string.insights_meaning_rhr_below)
    }

    @Test
    fun sleepAboveItsUsual_promisesNoRecovery() {
        show(MetricType.SLEEP_PERFORMANCE, readings(MetricType.SLEEP_PERFORMANCE, 70.0, 72.0, 74.0, 76.0, 78.0, 95.0))
        assertWellnessMeaning(R.string.insights_meaning_sleep_above)
    }

    @Test
    fun recoveryMeaning_followsItsZone() {
        // Usual 42–46% from the first five days; 64% is above it and still Yellow.
        show(MetricType.RECOVERY_SCORE, readings(MetricType.RECOVERY_SCORE, 40.0, 42.0, 44.0, 46.0, 48.0, 64.0))
        assertWellnessMeaning(R.string.insights_meaning_recovery_yellow_above)
    }

    @Test
    fun theChart_isOneTalkBackSentence_withItsRangeLatestAndUsual() {
        show(MetricType.HRV_RMSSD, readings(MetricType.HRV_RMSSD, 60.0, 62.0, 64.0, 66.0, 68.0, 80.0))
        val summary = "HRV, last 6 readings: lowest 60 ms, highest 80 ms, latest 80 ms, your usual 62 to 66 ms"
        // TalkBack (the merged tree) finds the chart by its summary, said once.
        composeRule.onNode(hasContentDescription(summary)).assertExists()
        assertEquals(1, composeRule.onAllNodes(hasContentDescription(summary), useUnmergedTree = true).fetchSemanticsNodes().size)
        // The band is labelled with its numbers, and the legend says "usual", never "normal".
        val said = everythingSaid()
        assertTrue(said.toString(), said.contains("Your usual 62–66 ms"))
        assertTrue(said.contains(context.getString(R.string.detail_band_legend)))
        assertTrue(said.none { it.contains("your normal", ignoreCase = true) })
    }

    @Test
    fun recoverysChart_saysTheLatestZone_andTheReadingsCountOnce() {
        // Usual 42–46% from the first five days; the latest, 64%, is a Yellow-zone score.
        show(MetricType.RECOVERY_SCORE, readings(MetricType.RECOVERY_SCORE, 40.0, 42.0, 44.0, 46.0, 48.0, 64.0))
        val summary = "Recovery, last 6 readings: lowest 40%, highest 64%, latest 64%, Yellow zone, your usual 42 to 46%"
        composeRule.onNode(hasContentDescription(summary)).assertExists()
        // The card's visible "Last 6 readings" title is not a second TalkBack stop saying the count again.
        val said = everythingSaid()
        assertEquals(said.toString(), 1, said.count { it.contains("6 readings", ignoreCase = true) })
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun theTopAction_showsOnce_inTheStickyBar_andHowToImproveItListsTheRest() {
        show(MetricType.HRV_RMSSD, readings(MetricType.HRV_RMSSD, 60.0, 62.0, 64.0, 66.0, 68.0, 80.0))
        val (top, rest) = ActionPlaybook.forMetric(MetricType.HRV_RMSSD).let { it.first() to it.drop(1) }
        assertTrue(rest.isNotEmpty())
        // The sticky bar's one Add, and no second Add for the same action in the list.
        assertEquals(1, count(top.title))
        assertEquals(1, count(context.getString(R.string.detail_add_to_today)))
        assertTrue(composeRule.onAllNodes(hasContentDescription(context.getString(R.string.insights_add_a11y, top.title)), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        // The rest are listed under "How to improve it", each once with its own Add.
        assertEquals(1, count(context.getString(R.string.detail_improve)))
        rest.forEach { action ->
            assertEquals(action.title, 1, count(action.title))
            composeRule.onNode(hasContentDescription(context.getString(R.string.insights_add_a11y, action.title)), useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun aMetricWithOneAction_leavesOutHowToImproveIt() {
        val vitaminD = listOf(MetricReading(MetricType.VITAMIN_D_25OH, 32.0, day0, id = 1))
        show(MetricType.VITAMIN_D_25OH, vitaminD)
        val only = ActionPlaybook.forMetric(MetricType.VITAMIN_D_25OH).single()
        assertEquals(1, count(only.title)) // the sticky bar
        assertEquals(0, count(context.getString(R.string.detail_improve)))
    }

    @Test
    fun aLabsChart_saysItsRangeAndLatest_withNoUsualBand() {
        val glucose = listOf(92.0, 101.0, 96.0).mapIndexed { i, v -> MetricReading(MetricType.FASTING_GLUCOSE, v, day0.plusDays(30L * i), id = i.toLong()) }
        show(MetricType.FASTING_GLUCOSE, glucose)
        composeRule.onNode(hasContentDescription("Fasting glucose, last 3 readings: lowest 92 mg/dL, highest 101 mg/dL, latest 96 mg/dL")).assertExists()
    }
}
