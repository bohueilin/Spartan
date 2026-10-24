package com.spartan.ui

import com.spartan.domain.engine.MorningVerdict
import com.spartan.domain.engine.PendingReason
import com.spartan.domain.engine.PlanConstraints
import com.spartan.domain.engine.RecoveryZone
import com.spartan.domain.engine.StrainZone
import com.spartan.domain.engine.WeeklyDecision
import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.ReadinessBand
import com.spartan.domain.model.ReadinessSnapshot
import com.spartan.domain.model.WhoopSnapshot
import com.spartan.domain.model.WorkoutLog
import com.spartan.domain.model.WorkoutType
import com.spartan.domain.usecase.DailyPlanSync
import com.spartan.ui.screens.RECOVERY_CATEGORIES
import com.spartan.ui.screens.RECOVERY_WORKOUTS
import com.spartan.ui.screens.TRAINING_CATEGORIES
import com.spartan.ui.screens.TRAINING_WORKOUTS
import com.spartan.ui.screens.completesWeeklyReview
import com.spartan.ui.screens.habitSessionsThisWeek
import com.spartan.ui.screens.importedAtMillis
import com.spartan.ui.screens.sessionsThisWeek
import com.spartan.ui.screens.todayVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Today's derived state: the verdict is null only until the first sync settles and is never
 * invented after that; habit-goal sessions count the trailing week; tonight's bedtime is the
 * user's own estimated need (and their own bed / wake times), never a fixed eight hours.
 */
class TodayStateTest {

    private val today = 20_000L

    // --- Verdict ---

    @Test
    fun verdict_isNullUntilTheFirstSyncSettles() {
        val readiness = ReadinessSnapshot(dateEpochDay = today, recoveryScore = 72, band = ReadinessBand.PRIMED)

        assertNull(todayVerdict(settled = false, readinessToday = readiness, strainTarget = StrainZone.MODERATE))
        assertNull(todayVerdict(settled = false, readinessToday = null, strainTarget = null))
    }

    @Test
    fun verdict_settledWithNoDataAndNoFailedSync_isPendingNoData() {
        assertEquals(
            MorningVerdict.Pending(PendingReason.NO_DATA),
            todayVerdict(settled = true, readinessToday = null, strainTarget = null),
        )
    }

    @Test
    fun verdict_fromTodaysCompletedCycle_isReadyWithThePlansStrainTarget() {
        val readiness = ReadinessSnapshot(
            dateEpochDay = today, recoveryScore = 72, band = ReadinessBand.PRIMED, previousRecoveryScore = 58,
        )

        val verdict = todayVerdict(true, DailyPlanSync.carryForward(readiness, today), StrainZone.MODERATE)

        assertTrue(verdict is MorningVerdict.Ready)
        verdict as MorningVerdict.Ready
        assertEquals(today, verdict.cycleEpochDay)
        assertEquals(72, verdict.recoveryScore)
        assertEquals(RecoveryZone.GREEN, verdict.zone)
        assertEquals(58, verdict.previousRecoveryScore)
        assertEquals(StrainZone.MODERATE, verdict.strainTarget)
    }

    @Test
    fun verdict_yesterdaysCachedSnapshot_readsAsStaleNotAsToday() {
        // A failed sync past midnight keeps yesterday's snapshot; carried forward it is Pending.
        val yesterday = ReadinessSnapshot(dateEpochDay = today - 1, recoveryScore = 80, band = ReadinessBand.PRIMED)

        val verdict = todayVerdict(true, DailyPlanSync.carryForward(yesterday, today), StrainZone.HIGH)

        assertEquals(MorningVerdict.Pending(PendingReason.STALE, lastCycleEpochDay = today - 1), verdict)
    }

    @Test
    fun verdict_afterAFailedSyncWithNothingSynced_saysTheSyncFailed_notNoData() {
        // A connected user offline at app open is never told "No WHOOP data yet — connect WHOOP".
        assertEquals(
            MorningVerdict.Pending(PendingReason.SYNC_FAILED),
            todayVerdict(settled = true, readinessToday = null, strainTarget = null, syncFailed = true),
        )
        assertNull(todayVerdict(settled = false, readinessToday = null, strainTarget = null, syncFailed = true))
    }

    @Test
    fun verdict_aFailedRefreshKeepsTodaysVerdictFromTheEarlierSync() {
        val readiness = ReadinessSnapshot(dateEpochDay = today, recoveryScore = 72, band = ReadinessBand.PRIMED)

        val verdict = todayVerdict(true, readiness, StrainZone.MODERATE, syncFailed = true)

        assertTrue(verdict is MorningVerdict.Ready)
    }

    // --- Freshness: the import time ---

    @Test
    fun importedAt_showsOnlyForConnectedRealData() {
        val at = 1_760_000_000_000L

        assertEquals(at, importedAtMillis(at, whoopConnected = true, isSample = false))
        // Sample data, or a source the user has disconnected, never borrows the old import time.
        assertNull(importedAtMillis(at, whoopConnected = true, isSample = true))
        assertNull(importedAtMillis(at, whoopConnected = false, isSample = false))
        assertNull(importedAtMillis(null, whoopConnected = true, isSample = false))
    }

    // --- Weekly check-in ---

    @Test
    fun weeklyCheckIn_keepAndPauseCompleteTheReview_changeWaitsForTheNewGoal() {
        assertTrue(completesWeeklyReview(WeeklyDecision.KEEP))
        assertTrue(completesWeeklyReview(WeeklyDecision.PAUSE))
        // Backing out of the wizard after "Change it" leaves this week's check-in due.
        assertFalse(completesWeeklyReview(WeeklyDecision.CHANGE))
    }

    // --- Habit-goal sessions this week ---

    private fun log(type: WorkoutType, daysAgo: Long) = WorkoutLog(
        type = type, plannedMinutes = 30, completedMinutes = 30, rpe = 5, painFlag = false,
        completedAt = LocalDate.ofEpochDay(today - daysAgo),
    )

    @Test
    fun sessionsThisWeek_countsTodayAndTheSixDaysBefore() {
        val logs = listOf(
            log(WorkoutType.ZONE_2, daysAgo = 0),
            log(WorkoutType.STRENGTH, daysAgo = 6),
            log(WorkoutType.ZONE_2, daysAgo = 7), // eight days back: last week
            log(WorkoutType.STRENGTH, daysAgo = -1), // tomorrow: not logged yet
            log(WorkoutType.MOBILITY, daysAgo = 2),
            log(WorkoutType.MOBILITY, daysAgo = 8),
        )

        assertEquals(2, sessionsThisWeek(logs, today, TRAINING_WORKOUTS))
        assertEquals(1, sessionsThisWeek(logs, today, RECOVERY_WORKOUTS))
        assertEquals(0, sessionsThisWeek(emptyList(), today, TRAINING_WORKOUTS))
    }

    @Test
    fun habitSessions_creditCheckOffsAndLogs_neverTheSameSessionTwice() {
        // Three check-offs, two of them debriefed into logs: three sessions, not five.
        assertEquals(3, habitSessionsThisWeek(checkOffs = 3, logged = 2))
        // Sessions logged outside Today still count when nothing was checked off.
        assertEquals(2, habitSessionsThisWeek(checkOffs = 0, logged = 2))
        assertEquals(0, habitSessionsThisWeek(checkOffs = 0, logged = 0))
    }

    @Test
    fun habitCategories_fitnessIsTraining_recoveryIncludesTheWindDown() {
        assertEquals(setOf(ActivityCategory.ZONE2, ActivityCategory.STRENGTH), TRAINING_CATEGORIES)
        assertTrue(ActivityCategory.MOBILITY in RECOVERY_CATEGORIES)
        assertTrue(ActivityCategory.RECOVERY in RECOVERY_CATEGORIES)
        assertTrue(ActivityCategory.SLEEP in RECOVERY_CATEGORIES)
        assertFalse(ActivityCategory.STRENGTH in RECOVERY_CATEGORIES)
    }

    // --- Tonight's bedtime (shared by the sync's sleep anchor and Today's card) ---

    private val lastNight = WhoopSnapshot(
        dateEpochDay = today,
        sleepPerformance = 88,
        sleepDurationHours = 7.0,
        sleepDebtHours = 0.5,
        wakeMinuteOfDay = 6 * 60 + 30,
    )

    @Test
    fun tonight_isTheEstimatedNeedBeforeTheObservedWake_neverAFixedEightHours() {
        val plan = DailyPlanSync.tonightFor(lastNight, PlanConstraints())

        assertNotNull(plan)
        plan!!
        // Need ≈ 7.0 h ÷ 0.88 ≈ 7 h 57 m; wake 6:30 AM → lights out 10:33 PM, rounded down to 10:30 PM.
        assertEquals(22 * 60 + 30, plan.lightsOutMinute)
        assertEquals(plan.needHours!!, plan.sleepHours!!, 1e-9)
        assertNotEquals(8.0, plan.sleepHours!!, 1e-6)
        assertEquals(0.5, plan.debtHours, 1e-9)
    }

    @Test
    fun tonight_theUsersOwnWakeTimeWinsOverTheObservedOne() {
        val plan = DailyPlanSync.tonightFor(lastNight, PlanConstraints(wakeMinute = 7 * 60))

        // Wake 7:00 AM minus about 7 h 57 m → 11:03 PM, rounded down to 11:00 PM.
        assertEquals(23 * 60, plan?.lightsOutMinute)
    }

    @Test
    fun tonight_withoutANeed_fallsBackToTheUsersBedtime_elseNone() {
        val fromUser = DailyPlanSync.tonightFor(null, PlanConstraints(bedtimeMinute = 22 * 60 + 40))

        assertEquals(22 * 60 + 40, fromUser?.lightsOutMinute)
        assertTrue(fromUser?.fromUserBedtime == true)
        assertNull(DailyPlanSync.tonightFor(null, PlanConstraints()))
        assertNull(DailyPlanSync.tonightFor(lastNight.copy(sleepPerformance = null), PlanConstraints()))
    }

    // --- "Last night" is today's night only ---

    @Test
    fun lastNightsSleep_isShownOnlyFromTodaysSnapshot() {
        assertEquals(7.0, DailyPlanSync.lastNight(lastNight, today)!!.sleepDurationHours!!, 1e-9)
        // A night from three weeks ago (old import) or yesterday (lagging sync) is not "last night".
        assertNull(DailyPlanSync.lastNight(lastNight.copy(dateEpochDay = today - 21), today))
        assertNull(DailyPlanSync.lastNight(lastNight.copy(dateEpochDay = today - 1), today))
    }
}
