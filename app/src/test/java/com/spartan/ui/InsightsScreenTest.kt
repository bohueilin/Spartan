package com.spartan.ui

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.spartan.domain.model.Confidence
import com.spartan.domain.model.InsightCard
import com.spartan.ui.screens.InsightsScreen
import com.spartan.ui.screens.MainUiState
import com.spartan.ui.theme.SpartanTheme
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
}
