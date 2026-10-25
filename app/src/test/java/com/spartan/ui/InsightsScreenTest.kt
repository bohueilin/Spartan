package com.spartan.ui

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.spartan.domain.engine.Signal
import com.spartan.domain.engine.SignalPosition
import com.spartan.domain.model.Confidence
import com.spartan.domain.model.InsightCard
import com.spartan.domain.model.MetricType
import com.spartan.ui.screens.InsightsScreen
import com.spartan.ui.screens.MainUiState
import com.spartan.ui.screens.formatMetricValue
import com.spartan.ui.screens.formatNumber
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
 * Insights on the JVM. Systolic and diastolic readings each produce a "Blood pressure" insight;
 * scrolling both into view once crashed the app with a duplicate LazyColumn key (found on a Pixel).
 * A usual range whose two ends round to one value reads as that value, never "78–78 ms".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class InsightsScreenTest {

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

    @Test
    fun twoBloodPressureInsights_scrollIntoViewWithoutCrashing() {
        val bp = InsightCard(
            title = "Blood pressure",
            explanation = "Your current blood pressure entry is in the normal range.",
            whyItMatters = "Blood pressure is best confirmed with repeated seated readings under consistent conditions.",
            safeActions = emptyList(),
            clinicianTriggers = emptyList(),
            confidence = Confidence.MODERATE,
        )
        composeRule.setContent {
            SpartanTheme {
                InsightsScreen(MainUiState(insights = listOf(bp, bp)), onOpenMetric = {}, onAddAction = { _, _ -> }, onAddLabResult = {})
            }
        }

        composeRule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("None of this is a diagnosis.", substring = true))
        composeRule.onNode(hasText("None of this is a diagnosis.", substring = true)).assertExists()
    }

    private fun showHrv(low: Double, high: Double) {
        val hrv = Signal(MetricType.HRV_RMSSD, 58.0, listOf(78.0, 70.0, 66.0, 75.0, 72.0, 58.0), low, high, SignalPosition.BELOW)
        composeRule.setContent {
            SpartanTheme {
                InsightsScreen(MainUiState(signals = listOf(hrv)), onOpenMetric = {}, onAddAction = { _, _ -> }, onAddLabResult = {})
            }
        }
    }

    @Test
    fun collapsedUsual_readsAsOneValue() {
        showHrv(low = 78.0, high = 78.0)

        // The card speaks one merged description; its visible lines live in the unmerged tree.
        composeRule.onNode(hasText("Your usual 78 ms", substring = true), useUnmergedTree = true).assertExists()
        assertTrue(composeRule.onAllNodesWithText("78–78", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        composeRule.onNode(hasContentDescription("below your usual of 78 ms", substring = true)).assertExists()
    }

    @Test
    fun spreadUsual_keepsTheRange() {
        showHrv(low = 62.0, high = 78.0)

        composeRule.onNode(hasText("Your usual 62–78 ms", substring = true), useUnmergedTree = true).assertExists()
        composeRule.onNode(hasContentDescription("below your usual of 62 to 78 ms", substring = true)).assertExists()
    }

    @Test
    fun valuesReadTheSameEverywhere() {
        assertEquals("1h 48m", formatMetricValue(1.8, MetricType.SLEEP_DEBT))
        // The low end of a sleep-debt range uses the same form as the high end.
        assertEquals("12m", formatNumber(0.2, MetricType.SLEEP_DEBT))
        assertEquals("1 session", formatMetricValue(1.0, MetricType.STRENGTH_SESSIONS))
        assertEquals("3 sessions", formatMetricValue(3.0, MetricType.STRENGTH_SESSIONS))
    }
}
