package com.spartan.ui

import com.spartan.data.local.MetricEntryEntity
import com.spartan.data.repository.HealthRepository
import com.spartan.domain.engine.AvoidOption
import com.spartan.domain.engine.MorningVerdict
import com.spartan.domain.engine.PendingReason
import com.spartan.domain.engine.PlanConstraints
import com.spartan.domain.engine.PlanEngine
import com.spartan.domain.engine.RecoveryZone
import com.spartan.domain.engine.SafetyEngine
import com.spartan.domain.engine.StrainZone
import com.spartan.domain.engine.WeeklyDecision
import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.DailyReflection
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.PlannedWorkout
import com.spartan.domain.model.ReadinessBand
import com.spartan.domain.model.ReadinessSnapshot
import com.spartan.domain.model.ReflectionMood
import com.spartan.domain.model.WeeklyPlan
import com.spartan.domain.model.WhoopSnapshot
import com.spartan.domain.model.WorkoutLog
import com.spartan.domain.model.WorkoutType
import com.spartan.domain.usecase.DailyPlanSync
import com.spartan.ui.screens.MIN_WEEKLY_SLOT_MINUTES
import com.spartan.ui.screens.RECOVERY_CATEGORIES
import com.spartan.ui.screens.RECOVERY_WORKOUTS
import com.spartan.ui.screens.REFLECTION_PREFILL_DAYS
import com.spartan.ui.screens.TRAINING_CATEGORIES
import com.spartan.ui.screens.TRAINING_WORKOUTS
import com.spartan.ui.screens.capSlotMinutes
import com.spartan.ui.screens.completesWeeklyReview
import com.spartan.ui.screens.habitSessionsThisWeek
import com.spartan.ui.screens.importedAtMillis
import com.spartan.ui.screens.lastReflectionMood
import com.spartan.ui.screens.sessionsThisWeek
import com.spartan.ui.screens.swapAvoidedSessions
import com.spartan.ui.screens.todayVerdict
import com.spartan.ui.widget.WidgetVerdict
import com.spartan.ui.widget.widgetVerdict
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
 * user's own need — WHOOP's figure when the data has it, else the estimate — (and their own bed /
 * wake times), never a fixed eight hours. Also the weekly template bent to the wizard's
 * constraints, the reflection pre-fill, and the widget's verdict line.
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

    @Test
    fun tonight_prefersWhoopsOwnSleepNeedOverTheEstimate() {
        val plan = DailyPlanSync.tonightFor(lastNight.copy(sleepNeedHours = 8.5), PlanConstraints())

        // Wake 6:30 AM minus WHOOP's 8 h 30 m → 10:00 PM (the estimate alone would say 10:30 PM).
        assertEquals(22 * 60, plan?.lightsOutMinute)
        assertEquals(8.5, plan!!.needHours!!, 1e-9)
    }

    @Test
    fun tonight_anImplausibleWhoopNeedFallsBackToTheEstimate() {
        val plan = DailyPlanSync.tonightFor(lastNight.copy(sleepNeedHours = 20.0), PlanConstraints())

        assertEquals(22 * 60 + 30, plan?.lightsOutMinute)
    }

    // --- "Last night" is today's night only ---

    @Test
    fun lastNightsSleep_isShownOnlyFromTodaysSnapshot() {
        assertEquals(7.0, DailyPlanSync.lastNight(lastNight, today)!!.sleepDurationHours!!, 1e-9)
        // A night from three weeks ago (old import) or yesterday (lagging sync) is not "last night".
        assertNull(DailyPlanSync.lastNight(lastNight.copy(dateEpochDay = today - 21), today))
        assertNull(DailyPlanSync.lastNight(lastNight.copy(dateEpochDay = today - 1), today))
    }

    // --- Weekly template vs the wizard's constraints (SCO-17) ---

    private val week = PlanEngine().defaultPlan()

    @Test
    fun weeklyPlan_strengthOnTheWontDoList_becomesZone2_withTheWeeksOwnZone2Session() {
        val zone2 = week.workouts.first { it.type == WorkoutType.ZONE_2 }

        val swapped = swapAvoidedSessions(week, PlanConstraints(avoid = setOf(AvoidOption.STRENGTH_TRAINING)))

        assertTrue(swapped.workouts.none { it.type == WorkoutType.STRENGTH })
        assertEquals(week.workouts.size, swapped.workouts.size)
        assertEquals(week.workouts.map { it.day }, swapped.workouts.map { it.day })
        val tuesday = swapped.workouts.single { it.day == "Tue" }
        assertEquals(WorkoutType.ZONE_2, tuesday.type)
        assertEquals(zone2.minutes, tuesday.minutes)
        assertEquals(zone2.intensity, tuesday.intensity)
        assertEquals(zone2.guidance, tuesday.guidance)
        // Everything that wasn't strength is untouched.
        assertEquals(
            week.workouts.filter { it.type != WorkoutType.STRENGTH },
            swapped.workouts.filter { it.day != "Tue" && it.day != "Fri" },
        )
    }

    @Test
    fun weeklyPlan_otherWontDosOrNone_leaveStrengthAlone() {
        assertEquals(week, swapAvoidedSessions(week, PlanConstraints()))
        assertEquals(week, swapAvoidedSessions(week, PlanConstraints(avoid = setOf(AvoidOption.BREATHWORK))))
    }

    @Test
    fun weeklyPlan_aWeekWithNoZone2SlotStillSwapsToEasyZone2Copy() {
        val strengthOnly = WeeklyPlan(
            workouts = listOf(
                PlannedWorkout("Tue", WorkoutType.STRENGTH, 35, "Moderate, clean form", "Leave 2 to 3 reps in reserve."),
            ),
            focus = "Hold steady.",
            safetyNote = "Pain means stop.",
        )

        val slot = swapAvoidedSessions(strengthOnly, PlanConstraints(avoid = setOf(AvoidOption.STRENGTH_TRAINING)))
            .workouts.single()

        assertEquals(WorkoutType.ZONE_2, slot.type)
        assertEquals(35, slot.minutes)
        assertEquals("Easy conversational pace", slot.intensity)
        assertEquals("Keep it repeatable.", slot.guidance)
        assertTrue(SafetyEngine().validateCopy("${slot.intensity} ${slot.guidance}"))
    }

    @Test
    fun weeklyPlan_minutesPerDayCapsEverySlot_neverBelowTen() {
        val capped = capSlotMinutes(week, minutesPerDay = 20)
        assertTrue(capped.workouts.all { it.minutes <= 20 })
        // Slots already inside the limit keep their own minutes.
        week.workouts.filter { it.minutes <= 20 }.forEach { slot ->
            assertEquals(slot.minutes, capped.workouts.single { it.day == slot.day }.minutes)
        }

        val floor = capSlotMinutes(week, minutesPerDay = 5)
        assertTrue(floor.workouts.all { it.minutes == MIN_WEEKLY_SLOT_MINUTES })
        assertEquals(10, MIN_WEEKLY_SLOT_MINUTES)
    }

    @Test
    fun weeklyPlan_noTimeLimitGiven_changesNothing() {
        assertEquals(week, capSlotMinutes(week, minutesPerDay = null))
        assertEquals(week, capSlotMinutes(week, minutesPerDay = 180))
    }

    // --- Reflection pre-fill ---

    @Test
    fun lastReflectionMood_isTheNewestSavedDay_regardlessOfListOrder() {
        val reflections = listOf(
            DailyReflection(today - 3, ReflectionMood.TOUGH),
            DailyReflection(today - 1, ReflectionMood.STRONG),
            DailyReflection(today - 2, ReflectionMood.OKAY),
        )

        assertEquals(ReflectionMood.STRONG, lastReflectionMood(reflections, today))
        assertNull(lastReflectionMood(emptyList(), today))
    }

    @Test
    fun lastReflectionMood_preFillsOnlyARecentAnswer_neverOneFromWeeksAgo() {
        val twoDaysAgo = listOf(DailyReflection(today - REFLECTION_PREFILL_DAYS, ReflectionMood.OKAY))
        val threeDaysAgo = listOf(DailyReflection(today - REFLECTION_PREFILL_DAYS - 1, ReflectionMood.OKAY))
        val weeksAgo = listOf(
            DailyReflection(today - 21, ReflectionMood.TOUGH),
            DailyReflection(today - 30, ReflectionMood.STRONG),
        )

        // Yesterday's, or the day before's, is still "last time".
        assertEquals(ReflectionMood.OKAY, lastReflectionMood(twoDaysAgo, today))
        // Older than that, the sheet opens with nothing selected — the user picks today's afresh.
        assertNull(lastReflectionMood(threeDaysAgo, today))
        assertNull(lastReflectionMood(weeksAgo, today))
        // The cut-off counts from the day asked: the day after the newest answer, it pre-fills.
        assertEquals(ReflectionMood.TOUGH, lastReflectionMood(weeksAgo, today - 20))
    }

    // --- Widget: verdict line ---

    private fun recovery(value: Double?, note: String = "WHOOP") = MetricEntryEntity(
        type = MetricType.RECOVERY_SCORE,
        value = value,
        unit = MetricType.RECOVERY_SCORE.unit,
        note = note,
        recordedAtEpochDay = today,
    )

    @Test
    fun widgetVerdict_isTheZoneWordAndNumber_fromTodaysRecovery() {
        assertEquals(WidgetVerdict(RecoveryZone.YELLOW, 42, isSample = false), widgetVerdict(recovery(42.0)))
        assertEquals(RecoveryZone.GREEN, widgetVerdict(recovery(67.0))?.zone)
        assertEquals(RecoveryZone.RED, widgetVerdict(recovery(33.4))?.zone) // rounds to 33
        assertEquals(100, widgetVerdict(recovery(104.0))?.recoveryScore) // clamped, never past 100%
    }

    @Test
    fun widgetVerdict_withoutAReadingIsPending_neverAGuess() {
        assertNull(widgetVerdict(null))
        assertNull(widgetVerdict(recovery(null)))
        assertNull(widgetVerdict(recovery(Double.NaN)))
    }

    @Test
    fun widgetVerdict_aSampleReadingIsLabelledSample() {
        assertTrue(widgetVerdict(recovery(72.0, note = HealthRepository.SAMPLE_WHOOP_NOTE))!!.isSample)
        assertFalse(widgetVerdict(recovery(72.0, note = "WHOOP"))!!.isSample)
        assertEquals("WHOOP (sample)", HealthRepository.SAMPLE_WHOOP_NOTE) // the tag WhoopMapper writes
    }
}
