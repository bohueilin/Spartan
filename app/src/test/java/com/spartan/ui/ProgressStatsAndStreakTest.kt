package com.spartan.ui

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.spartan.domain.engine.StreakSummary
import com.spartan.ui.screens.ProgressConsistencyCard
import com.spartan.ui.screens.WeekStat
import com.spartan.ui.screens.WeekStatKind
import com.spartan.ui.screens.WeekStatsGrid
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
 * Progress's week stats and Consistency card on the JVM: every stat says what it counts, "Avg
 * sleep" is "Avg sleep performance", an empty strength week reads forgivingly (never a lone 0), the
 * grid stays two tiles a row at Android's "Large" text and stacks only at 200%, "Back on track — no
 * reset" shows when the streak engine says so, the card frames runs only — the active-day count is
 * the stat tile's and a habit goal's weekly count the hero's — and a 1-day run is never named.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProgressStatsAndStreakTest {

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

    // Spec §1's real week: active on 1 of 7 days, five days of WHOOP data, no strength yet.
    private val week = listOf(
        WeekStat(WeekStatKind.ACTIVE_DAYS, 1),
        WeekStat(WeekStatKind.AVG_RECOVERY, 66, 5),
        WeekStat(WeekStatKind.AVG_SLEEP_PERFORMANCE, 79, 5),
        WeekStat(WeekStatKind.AVG_HRV, 32, 5),
        WeekStat(WeekStatKind.STRENGTH_SESSIONS, 0),
    )

    private fun showStats(stats: List<WeekStat>) = composeRule.setContent { SpartanTheme { WeekStatsGrid(stats) } }

    /** The week's tiles in a 312dp content column (a 360dp phone less its gutters) at [fontScale]. */
    private fun showStatsAt(fontScale: Float) = composeRule.setContent {
        SpartanTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                Box(Modifier.width(312.dp)) { WeekStatsGrid(week) }
            }
        }
    }

    /** A tile's bounds: each tile is one merged node, found by its label. */
    private fun tile(label: String) = composeRule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot

    @Test
    fun everyStat_carriesItsValueAndADefinition_inOneTile() {
        showStats(week)

        // Each tile is one TalkBack node: label, value and definition together.
        composeRule.onNodeWithText("Active days").assert(hasText("1 of 7")).assert(hasText("Days with at least one completed action"))
        composeRule.onNodeWithText("Avg recovery").assert(hasText("66%")).assert(hasText("Daily recovery score, averaged over 5 days with data"))
        composeRule.onNodeWithText("Avg sleep performance").assert(hasText("79%")).assert(hasText("Sleep you got vs. sleep you needed, averaged over 5 nights"))
        composeRule.onNodeWithText("Avg HRV").assert(hasText("32 ms")).assert(hasText("Overnight heart rate variability, averaged over 5 nights"))
        composeRule.onNodeWithText("Strength sessions").assert(hasText("Strength workouts you logged in these 7 days"))
        // "Avg sleep" alone said nothing about what was averaged.
        composeRule.onNodeWithText("Avg sleep").assertDoesNotExist()
    }

    @Test
    fun anEmptyWeek_readsForgivingly_neverALoneZero() {
        showStats(listOf(WeekStat(WeekStatKind.ACTIVE_DAYS, 0), WeekStat(WeekStatKind.STRENGTH_SESSIONS, 0)))

        composeRule.onNodeWithText("Strength sessions").assert(hasText("None yet this week"))
        composeRule.onNodeWithText("Active days").assert(hasText("Starting this week"))
        composeRule.onAllNodesWithText("0", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "w360dp-h2000dp") // a 360dp phone, tall enough to lay out every tile
    fun androidsLargeText_keepsTwoTilesARow() {
        showStatsAt(1.15f)

        val active = tile("Active days")
        val recovery = tile("Avg recovery")
        assertEquals("$active vs $recovery", active.top, recovery.top, 0.5f)
        assertTrue("$active vs $recovery", recovery.left > active.right)
    }

    @Test
    @Config(qualifiers = "w360dp-h2000dp") // a 360dp phone, tall enough to lay out every tile
    fun doubleText_stacksTheTilesInOneColumn() {
        showStatsAt(2f)

        val active = tile("Active days")
        val recovery = tile("Avg recovery")
        assertEquals("$active vs $recovery", active.left, recovery.left, 0.5f)
        assertTrue("$active vs $recovery", recovery.top > active.bottom)
    }

    @Test
    fun strengthSessions_showTheRealCount() {
        showStats(listOf(WeekStat(WeekStatKind.STRENGTH_SESSIONS, 2)))
        composeRule.onNodeWithText("Strength sessions").assert(hasText("2"))
    }

    @Test
    fun backOnTrack_saysNoReset_withoutCountingTheDays_orAnInventedTarget() {
        composeRule.setContent {
            SpartanTheme { ProgressConsistencyCard(StreakSummary(currentRun = 1, longestRun = 1, backOnTrack = true, graceUsed = false)) }
        }

        composeRule.onNodeWithText("Back on track — no reset").assertExists()
        composeRule.onNodeWithText("Today picks up where you left off. Your earlier days still count.").assertExists()
        // The active-day count is the stat tile's; the card never repeats it.
        composeRule.onNodeWithText("of the last 7 days", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("of 7", substring = true).assertDoesNotExist()
        // No goal sets a weekly target, so none is invented.
        composeRule.onNodeWithText("goal session", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("this week", substring = true).assertDoesNotExist()
        // A one-day run is never named: it read as failure.
        composeRule.onNodeWithText("run:", substring = true).assertDoesNotExist()
    }

    @Test
    fun aSteadyRun_leadsWithItsRun_andTheGraceNote() {
        composeRule.setContent {
            SpartanTheme { ProgressConsistencyCard(StreakSummary(currentRun = 2, longestRun = 2, backOnTrack = false, graceUsed = true)) }
        }

        composeRule.onNodeWithText("Back on track", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Current run: 2 days").assertExists()
        composeRule.onNodeWithText("A missed day didn't break your run. One a week is built in.").assertExists()
        composeRule.onNodeWithText("of the last 7 days", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("goal session", substring = true).assertDoesNotExist()
    }

    @Test
    fun aFirstActiveDay_saysTheRunHasStarted_neverOneDay() {
        composeRule.setContent {
            SpartanTheme { ProgressConsistencyCard(StreakSummary(currentRun = 1, longestRun = 1, backOnTrack = false, graceUsed = false)) }
        }

        composeRule.onNodeWithText("Your run has started. Each active day adds to it.").assertExists()
        composeRule.onNodeWithText("1 day", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("of the last 7 days", substring = true).assertDoesNotExist()
    }

    @Test
    fun anEmptyWindow_invitesTheNextRun_andStillCreditsTheLongestRun() {
        composeRule.setContent {
            SpartanTheme { ProgressConsistencyCard(StreakSummary(0, 4, backOnTrack = false, graceUsed = false)) }
        }
        composeRule.onNodeWithText("Your next run starts with one activity on Today.").assertExists()
        composeRule.onNodeWithText("Longest recent run: 4 days").assertExists()
        composeRule.onNodeWithText("0", substring = false).assertDoesNotExist()
    }
}
