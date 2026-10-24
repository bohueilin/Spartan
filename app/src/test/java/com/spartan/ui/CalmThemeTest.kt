package com.spartan.ui

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.spartan.ui.components.MorningReveal
import com.spartan.ui.components.OneShot
import com.spartan.ui.components.OneShotKey
import com.spartan.ui.theme.LocalCalmMode
import com.spartan.ui.theme.SpartanTheme
import com.spartan.ui.theme.rememberReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The calmer theme on the JVM: it swaps in the quieter colour scheme while keeping the neutrals,
 * and it stills motion app-wide — rememberReducedMotion() turns true with system animations on, so
 * the one-shot Lottie moments show their final frame. A plain Application keeps app start-up
 * (Hilt, WorkManager) out of UI tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CalmThemeTest {

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

    @Before
    fun systemAnimationsOn() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    @Test
    fun reducedMotion_followsTheCalmerTheme_withSystemAnimationsOn() {
        var standard: Boolean? = null
        var calm: Boolean? = null
        composeRule.setContent {
            SpartanTheme { standard = rememberReducedMotion() }
            SpartanTheme(calmer = true) { calm = rememberReducedMotion() }
        }
        composeRule.waitForIdle()

        assertEquals(false, standard)
        assertEquals(true, calm)
    }

    @Test
    fun calmerTheme_providesCalmMode() {
        var standard: Boolean? = null
        var calm: Boolean? = null
        composeRule.setContent {
            SpartanTheme { standard = LocalCalmMode.current }
            SpartanTheme(calmer = true) { calm = LocalCalmMode.current }
        }
        composeRule.waitForIdle()

        assertEquals(false, standard)
        assertEquals(true, calm)
    }

    @Test
    fun calmerTheme_quietsTheAccent_andKeepsTheNeutrals() {
        lateinit var standard: ColorScheme
        lateinit var calm: ColorScheme
        composeRule.setContent {
            SpartanTheme { standard = MaterialTheme.colorScheme }
            SpartanTheme(calmer = true) { calm = MaterialTheme.colorScheme }
        }
        composeRule.waitForIdle()

        assertNotEquals(standard.primary, calm.primary)
        assertNotEquals(standard.primaryContainer, calm.primaryContainer)
        assertNotEquals(standard.tertiary, calm.tertiary)
        // Surfaces and text roles are shared, so their audited contrast carries over unchanged.
        assertEquals(standard.background, calm.background)
        assertEquals(standard.surface, calm.surface)
        assertEquals(standard.onSurface, calm.onSurface)
        assertEquals(standard.onSurfaceVariant, calm.onSurfaceVariant)
        assertEquals(standard.outlineVariant, calm.outlineVariant)
    }

    @Test
    fun calmerTheme_morningRevealShowsTheFinalFrame_withoutReportingAPlay() {
        var played = 0
        composeRule.setContent {
            SpartanTheme(calmer = true) {
                MorningReveal(64, MaterialTheme.colorScheme.primary, playOnce = true, onPlayed = { played++ })
            }
        }

        composeRule.onNode(SemanticsMatcher.keyIsDefined(OneShotKey), useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(OneShotKey, OneShot.SHOW_FINAL))
        assertEquals(0, played)
    }

    @Test
    fun standardTheme_morningRevealStillPlaysOnce() {
        var played = 0
        composeRule.setContent {
            SpartanTheme {
                MorningReveal(64, MaterialTheme.colorScheme.primary, playOnce = true, onPlayed = { played++ })
            }
        }

        composeRule.onNode(SemanticsMatcher.keyIsDefined(OneShotKey), useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(OneShotKey, OneShot.PLAY_ONCE))
        assertEquals(1, played)
    }
}
