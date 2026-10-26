package com.spartan.ui

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.spartan.BuildConfig
import com.spartan.R
import com.spartan.ui.screens.AboutScreen
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
 * You → About: the one place the version, the full wellness disclaimer and the font licence appear.
 * It never says less than Today's in-context line (not medical advice; stop and seek care), it says
 * where the data lives, there is no badge or claim, and Back leaves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h2000dp")
class AboutScreenTest {

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

    @Test
    fun showsVersion_fullDisclaimer_inContextLine_privacy_andLicence() {
        composeRule.setContent { SpartanTheme { AboutScreen(onBack = {}) } }

        composeRule.onNodeWithText("About Spartan").assertExists()
        composeRule.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertExists()
        // The full disclaimer, word for word.
        composeRule.onNodeWithText(context.getString(R.string.wellness_disclaimer)).assertExists()
        composeRule.onNodeWithText("Spartan is a general-wellness planner", substring = true).assertExists()
        composeRule.onNodeWithText("It is not a medical device and does not diagnose, treat or prevent any condition", substring = true).assertExists()
        // At least as much as Today says in context (spec §5).
        composeRule.onNodeWithText(
            "Wellness and fitness guidance, not medical advice. Stop and seek care for pain, illness, or unusual symptoms.",
        ).assertExists()
        composeRule.onNodeWithText("Your data stays on this device. See Privacy for export and deletion.").assertExists()
        composeRule.onNodeWithText("Licences").assertExists()
        composeRule.onNodeWithText("Set in Inter, used under the SIL Open Font License.").assertExists()
        assertTrue(composeRule.onAllNodesWithText("Premium", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Award", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun back_leaves() {
        var back = false
        composeRule.setContent { SpartanTheme { AboutScreen(onBack = { back = true }) } }

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(back)
    }
}
