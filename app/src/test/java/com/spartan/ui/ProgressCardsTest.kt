package com.spartan.ui

import com.spartan.R
import com.spartan.domain.engine.Goal
import com.spartan.domain.engine.GoalEngine
import com.spartan.domain.engine.GoalProgress
import com.spartan.domain.engine.GoalType
import com.spartan.domain.engine.Streaks
import com.spartan.ui.screens.MainUiState
import com.spartan.ui.screens.calmerThemeBodyRes
import com.spartan.ui.screens.consistencyStreak
import com.spartan.ui.screens.weeklyRingCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Progress cards' own logic: the weekly ring states the real, uncapped session count; the
 * Consistency card reads the two-week window (falling back to the week); and the calmer theme's
 * subtitle on You only promises what still applies when wallpaper colours win.
 */
class ProgressCardsTest {

    private val today = 20_000L

    private fun goal(type: GoalType, target: Double) = Goal(
        id = "g", type = type, targetValue = target, baselineValue = null,
        startEpochDay = today - 7, targetEpochDay = today + 21,
    )

    private fun progress(sessions: Int?) = GoalProgress(
        fraction = 1.0, timeFraction = 0.25, onTrack = true, summary = "", sessionsThisWeek = sessions,
    )

    /** "x" = a day with a completed activity, "." = none; oldest → today. */
    private fun days(pattern: String) = pattern.map { it == 'x' }

    // --- Weekly ring ---

    @Test
    fun weeklyRing_statesTheRealCount_evenAboveTheTarget() {
        // The ring itself is full (fraction capped at 1.0); the words still say 7, not 5.
        assertEquals(7 to 5, weeklyRingCount(goal(GoalType.FITNESS, 5.0), progress(7)))
        assertEquals(2 to 3, weeklyRingCount(goal(GoalType.STRESS_RESILIENCE, 3.0), progress(2)))
    }

    @Test
    fun weeklyRing_followsTheEngine_forEveryHabitGoal() {
        val fitness = goal(GoalType.FITNESS, 3.0)
        assertEquals(5 to 3, weeklyRingCount(fitness, GoalEngine.progress(fitness, emptyList(), today, trainingSessionsThisWeek = 5)))
        val calm = goal(GoalType.STRESS_RESILIENCE, 4.0)
        assertEquals(1 to 4, weeklyRingCount(calm, GoalEngine.progress(calm, emptyList(), today, breathworkThisWeek = 1)))
        val recovery = goal(GoalType.RECOVERY, 2.0)
        assertEquals(0 to 2, weeklyRingCount(recovery, GoalEngine.progress(recovery, emptyList(), today)))
    }

    @Test
    fun weeklyRing_isOnlyForGoalsCountedInSessions() {
        val weight = goal(GoalType.WEIGHT_LOSS, 6.0)
        assertNull(weeklyRingCount(weight, GoalEngine.progress(weight, emptyList(), today)))
        assertNull(weeklyRingCount(goal(GoalType.SLEEP_RECOVERY, 5.0), progress(null)))
    }

    @Test
    fun weeklyRing_guardsACorruptTarget() {
        assertNull(weeklyRingCount(goal(GoalType.FITNESS, Double.NaN), progress(2)))
        assertNull(weeklyRingCount(goal(GoalType.FITNESS, Double.POSITIVE_INFINITY), progress(2)))
        // A zero target reads as one session, as the engine scores it.
        assertEquals(2 to 1, weeklyRingCount(goal(GoalType.FITNESS, 0.0), progress(2)))
    }

    // --- Consistency ---

    @Test
    fun consistency_readsTheTwoWeekWindow_soAReturnAfterAGapIsBackOnTrack() {
        // Active eight days ago, then a quiet week, then today: the week alone shows only today.
        val state = MainUiState(
            streakFlags = days(".....x.......x"),
            consistencyFlags = days("......x"),
        )
        val streak = consistencyStreak(state)
        assertTrue(streak.backOnTrack)
        assertEquals(1, streak.longestRun)
        assertFalse(Streaks.summarize(state.consistencyFlags).backOnTrack)
    }

    @Test
    fun consistency_fallsBackToTheWeek_untilTheLongerWindowLoads() {
        val week = days("xx.xx..")
        assertEquals(Streaks.summarize(week), consistencyStreak(MainUiState(consistencyFlags = week)))
        assertEquals(0, consistencyStreak(MainUiState()).longestRun)
    }

    // --- You → Display ---

    @Test
    fun calmerThemeSubtitle_dropsTheColourPromise_whenWallpaperColoursWin() {
        assertEquals(R.string.more_calmer_theme_body_wallpaper, calmerThemeBodyRes(wallpaperAvailable = true, wallpaperColours = true))
        assertEquals(R.string.more_calmer_theme_body, calmerThemeBodyRes(wallpaperAvailable = true, wallpaperColours = false))
        // Below Android 12 a stored "on" does nothing, so the calmer colours still apply.
        assertEquals(R.string.more_calmer_theme_body, calmerThemeBodyRes(wallpaperAvailable = false, wallpaperColours = true))
        assertEquals(R.string.more_calmer_theme_body, calmerThemeBodyRes(wallpaperAvailable = false, wallpaperColours = false))
    }
}
