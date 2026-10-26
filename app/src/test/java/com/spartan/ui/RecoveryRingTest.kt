package com.spartan.ui

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.spartan.ui.components.OneShot
import com.spartan.ui.components.OneShotKey
import com.spartan.ui.components.RecoveryRing
import com.spartan.ui.theme.SpartanTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The verdict ring's one sweep: it plays on the first verdict of the day and reports it once;
 * reduce-motion shows the final arc and still reports it (the moment is used up); a screen visit
 * after that shows the final arc without reporting again. (The arc's extent
 * itself — exactly the score — is OneShotPlaybackTest's ringFraction.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RecoveryRingTest {

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

    @After
    fun restoreAnimatorScale() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    private fun assertPlayback(expected: OneShot) {
        composeRule.onNode(SemanticsMatcher.keyIsDefined(OneShotKey), useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(OneShotKey, expected))
    }

    @Test
    fun firstVerdictOfTheDay_sweepsOnce_andReportsItOnce() {
        var playOnce by mutableStateOf(true)
        var played = 0
        composeRule.setContent {
            SpartanTheme {
                RecoveryRing(64, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceVariant, playOnce = playOnce, onPlayed = { played++; playOnce = false })
            }
        }
        // Latched: the caller turning playOnce off after onPlayed neither stops nor repeats it.
        assertPlayback(OneShot.PLAY_ONCE)
        assertEquals(1, played)
    }

    @Test
    fun reducedMotion_showsTheFinalArc_andStillUsesTheMomentUp() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        var played = 0
        composeRule.setContent {
            SpartanTheme { RecoveryRing(64, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceVariant, playOnce = true, onPlayed = { played++ }) }
        }
        assertPlayback(OneShot.SHOW_FINAL)
        // Reported, so turning motion back on later the same day never replays the sweep.
        assertEquals(1, played)
    }

    @Test
    fun alreadySeenToday_showsTheFinalArc() {
        var played = 0
        composeRule.setContent {
            SpartanTheme { RecoveryRing(64, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceVariant, playOnce = false, onPlayed = { played++ }) }
        }
        assertPlayback(OneShot.SHOW_FINAL)
        assertEquals(0, played)
    }
}
