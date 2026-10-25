package com.spartan.ui

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.spartan.domain.engine.ProjectionEngine
import com.spartan.ui.screens.TrajectoryCard
import com.spartan.ui.theme.SpartanTheme
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
 * The Progress "Where this can take you" card shows each projection the way the metric shows
 * everywhere else: whole bpm, ms and %, no space before %, and "1 day" not "1 days" (found on a Pixel).
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

    @Test
    fun projections_useEachMetricsOwnPrecision() {
        composeRule.setContent { SpartanTheme { TrajectoryCard(ProjectionEngine().project(59.0, 58.0, 42, 1)) } }

        composeRule.onNodeWithText("At your current consistency of 1 day a week").assertExists()
        val lines = composeRule.onAllNodesWithText(" in 8 wks", substring = true).fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
        assertTrue(lines.toString(), lines.any { Regex("""59 → \d+(–\d+)? bpm in 8 wks""").matches(it) })
        assertTrue(lines.toString(), lines.any { Regex("""58 → \d+(–\d+)? ms in 8 wks""").matches(it) })
        assertTrue(lines.toString(), lines.any { Regex("""42 → \d+(–\d+)?% in 8 wks""").matches(it) })
    }
}
