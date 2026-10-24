package com.spartan.ui

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.spartan.domain.engine.Goal
import com.spartan.domain.engine.GoalProgress
import com.spartan.domain.engine.GoalType
import com.spartan.ui.screens.GoalCard
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
import java.time.LocalDate

/**
 * The Progress goal card on the JVM: a habit goal's weekly ring says the real count in words and
 * keeps the engine's summary (noun and body-signal context) under it; an outcome goal keeps its
 * bar and summary with no weekly count. A plain Application keeps app start-up out of UI tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GoalCardRingTest {

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

    private val today = LocalDate.now().toEpochDay()

    private fun showGoalCard(goal: Goal, progress: GoalProgress) {
        composeRule.setContent {
            SpartanTheme {
                GoalCard(MainUiState(activeGoal = goal, goalProgress = progress), onOpenWizard = {}, onAbandon = {}, onResume = {})
            }
        }
    }

    @Test
    fun habitGoal_ringSaysTheUncappedCount_withTheSummaryUnderIt() {
        val summary = "7 of 5 training sessions this week · resting HR -1.5 bpm vs baseline"
        showGoalCard(
            Goal("g", GoalType.FITNESS, targetValue = 5.0, baselineValue = 62.0, startEpochDay = today - 7, targetEpochDay = today + 21),
            GoalProgress(fraction = 1.0, timeFraction = 0.25, onTrack = true, summary = summary, sessionsThisWeek = 7),
        )

        // Not "5 of 5": the ring is full, but the words count every session.
        composeRule.onNodeWithText("7 of 5 this week").assertExists()
        composeRule.onNodeWithText(summary).assertExists()
    }

    @Test
    fun outcomeGoal_keepsItsSummary_withoutAWeeklyCount() {
        val summary = "3.0 of 6 lb down, 3 wk left"
        showGoalCard(
            Goal("g", GoalType.WEIGHT_LOSS, targetValue = 6.0, baselineValue = 80.0, startEpochDay = today - 7, targetEpochDay = today + 21),
            GoalProgress(fraction = 0.5, timeFraction = 0.25, onTrack = true, summary = summary),
        )

        composeRule.onNodeWithText(summary).assertExists()
        composeRule.onNodeWithText("this week", substring = true).assertDoesNotExist()
    }
}
