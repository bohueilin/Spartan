package com.spartan.ui

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.spartan.ui.screens.MainUiState
import com.spartan.ui.screens.PrivacyScreen
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
 * The Privacy export preview shows the first 30 lines and counts the rest truthfully. The export
 * ends in a newline, which once made a 30-line file claim one more hidden line.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PrivacyScreenTest {

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

    private fun showExport(lineCount: Int) {
        val export = (1..lineCount).joinToString("") { "row $it\n" }
        composeRule.setContent { SpartanTheme { PrivacyScreen(MainUiState(exportText = export), onShare = {}, onDelete = {}, onBack = {}) } }
    }

    @Test
    fun thirtyLines_showEverythingAndNoMoreNote() {
        showExport(30)

        assertTrue(composeRule.onAllNodesWithText("more line", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun thirtyOneLines_countTheOneHiddenLine() {
        showExport(31)

        composeRule.onNodeWithText("…and 1 more line, included when you share.").assertExists()
    }
}
