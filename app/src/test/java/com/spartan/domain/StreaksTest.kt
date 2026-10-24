package com.spartan.domain

import com.spartan.domain.engine.StreakSummary
import com.spartan.domain.engine.Streaks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreaksTest {

    /** "x" = active, "." = inactive, oldest → today. */
    private fun days(pattern: String): List<Boolean> = pattern.map { it == 'x' }

    @Test
    fun noDays_isAllZeroAndCalm() {
        assertEquals(StreakSummary(0, 0, backOnTrack = false, graceUsed = false), Streaks.summarize(emptyList()))
    }

    @Test
    fun everyDayActive_isOneRunOfSeven() {
        assertEquals(StreakSummary(7, 7, backOnTrack = false, graceUsed = false), Streaks.summarize(days("xxxxxxx")))
    }

    @Test
    fun noActiveDays_hasNoRunAndNothingToCelebrate() {
        assertEquals(StreakSummary(0, 0, backOnTrack = false, graceUsed = false), Streaks.summarize(days(".......")))
    }

    @Test
    fun oneMissedDay_isAbsorbedByGrace_andAddsNothingToTheRun() {
        val summary = Streaks.summarize(days("xx.xx"))
        assertEquals(4, summary.currentRun)
        assertEquals(4, summary.longestRun)
        assertTrue(summary.graceUsed)
    }

    @Test
    fun aSecondMissInTheSameWeek_endsTheRun_butLongestIsKept() {
        val summary = Streaks.summarize(days("xxx.x.x"))
        assertEquals(1, summary.currentRun)
        assertEquals(4, summary.longestRun)
        assertFalse(summary.graceUsed)
    }

    @Test
    fun twoMissesInARow_endTheRun() {
        val summary = Streaks.summarize(days("xx..xx"))
        assertEquals(2, summary.currentRun)
        assertEquals(2, summary.longestRun)
        assertFalse(summary.graceUsed)
    }

    @Test
    fun graceRenewsOnceTheEarlierMissLeavesTheSevenDayWindow() {
        // Misses 7 days apart: each window holds one, so the run survives both.
        val summary = Streaks.summarize(days("x.xxxxxx.x"))
        assertEquals(8, summary.currentRun)
        assertEquals(8, summary.longestRun)
        assertTrue(summary.graceUsed)
    }

    @Test
    fun missesSixDaysApart_shareAWindow_soTheSecondEndsTheRun() {
        val summary = Streaks.summarize(days("x.xxxxx.x"))
        assertEquals(1, summary.currentRun)
        assertEquals(6, summary.longestRun)
    }

    @Test
    fun zeroGrace_meansAnyMissEndsTheRun() {
        val summary = Streaks.summarize(days("xx.xx"), graceDaysPerWeek = 0)
        assertEquals(2, summary.currentRun)
        assertEquals(2, summary.longestRun)
        assertFalse(summary.graceUsed)
    }

    @Test
    fun twoGraceDays_absorbTwoMissesInAWeek() {
        val summary = Streaks.summarize(days("x.x.x"), graceDaysPerWeek = 2)
        assertEquals(3, summary.currentRun)
        assertTrue(summary.graceUsed)
    }

    @Test
    fun aMissBeforeAnyActiveDay_usesNoGrace() {
        val summary = Streaks.summarize(days("..xx"))
        assertEquals(2, summary.currentRun)
        assertFalse(summary.graceUsed)
        assertFalse(summary.backOnTrack)
    }

    @Test
    fun anInactiveToday_isStillOpen_soTheRunStandsWithoutSpendingGrace() {
        val summary = Streaks.summarize(days("xxx."))
        assertEquals(3, summary.currentRun)
        assertFalse(summary.graceUsed)
        assertFalse(summary.backOnTrack)
    }

    @Test
    fun anInactiveToday_afterAGraceDay_keepsTheRunAlive() {
        val summary = Streaks.summarize(days("xx.."))
        assertEquals(2, summary.currentRun)
        assertTrue(summary.graceUsed)
    }

    @Test
    fun anInactiveToday_afterAnEndedRun_hasNoCurrentRun_butKeepsTheLongest() {
        val summary = Streaks.summarize(days("xxx..."))
        assertEquals(0, summary.currentRun)
        assertEquals(3, summary.longestRun)
        assertFalse(summary.graceUsed)
    }

    @Test
    fun backOnTrack_whenTodayFollowsAGapAfterEarlierActivity() {
        assertTrue(Streaks.summarize(days("x.x")).backOnTrack)
        assertTrue(Streaks.summarize(days("xx...x")).backOnTrack)
    }

    @Test
    fun notBackOnTrack_withoutAGapYesterday() {
        assertFalse(Streaks.summarize(days("x.xx")).backOnTrack)
    }

    @Test
    fun notBackOnTrack_onAFirstEverActiveDay() {
        assertFalse(Streaks.summarize(days("...x")).backOnTrack)
        assertFalse(Streaks.summarize(days(".x")).backOnTrack)
        assertFalse(Streaks.summarize(days("x")).backOnTrack)
    }

    @Test
    fun backOnTrack_andGrace_canBothBeTrue() {
        // Yesterday's miss was absorbed, so today both continues the run and ends the gap.
        val summary = Streaks.summarize(days("xx.x"))
        assertEquals(3, summary.currentRun)
        assertTrue(summary.backOnTrack)
        assertTrue(summary.graceUsed)
    }
}
