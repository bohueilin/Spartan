package com.spartan.domain

import com.spartan.domain.engine.CoachingEngine
import com.spartan.domain.engine.PlanConstraints
import com.spartan.domain.engine.RuleIds
import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.DailyPlan
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.ReadinessBand
import com.spartan.domain.model.ReadinessSnapshot
import com.spartan.domain.model.TimeOfDay
import com.spartan.domain.model.WhoopSnapshot
import com.spartan.domain.usecase.DailyPlanSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Imported CSV data (and a lagging real API) can end before today. The plan must still land on
 * the day being planned — carried forward and clearly marked stale so the coaching engine eases
 * intensity and says why. A cycle dated after today is clamped to today, and a holding plan saved
 * before today's cycle was scored gives way once it arrives.
 */
class DailyPlanSyncCarryForwardTest {

    private val day = 20000L

    private fun readinessFor(day: Long): ReadinessSnapshot =
        ReadinessSnapshot.from(WhoopSnapshot(dateEpochDay = day, recoveryScore = 80))

    @Test
    fun dataForToday_isUntouched() {
        val readiness = readinessFor(20000)
        val result = DailyPlanSync.carryForward(readiness, targetEpochDay = 20000)
        assertEquals(readiness, result)
        assertFalse(result.isStale)
    }

    @Test
    fun olderData_isCarriedToTargetDay_andMarkedStale() {
        val result = DailyPlanSync.carryForward(readinessFor(19995), targetEpochDay = 20000)
        assertEquals(20000, result.dateEpochDay)
        assertTrue(result.isStale)
        // The underlying signals survive so the plan is still personal, just softened.
        assertEquals(80, result.recoveryScore)
    }

    @Test
    fun carriedForward_keepsTheOriginalCycleDay() {
        // The plan lands on today, but the verdict must still say the data is from day 19995.
        val result = DailyPlanSync.carryForward(readinessFor(19995), targetEpochDay = 20000)
        assertEquals(20000L, result.dateEpochDay)
        assertEquals(19995L, result.cycleEpochDay)
    }

    @Test
    fun dataForToday_cycleDayIsToday() {
        assertEquals(20000L, DailyPlanSync.carryForward(readinessFor(20000), targetEpochDay = 20000).cycleEpochDay)
    }

    // --- A cycle dated after the planned day (export made a time zone ahead) ---

    @Test
    fun futureData_isClampedToTheTargetDay_andStaysACompletedCycle() {
        val result = DailyPlanSync.carryForward(readinessFor(20001), targetEpochDay = 20000)

        assertEquals(20000L, result.dateEpochDay)
        assertFalse(result.isStale)
        assertEquals(80, result.recoveryScore)
        // The cycle still names its own day; only where the plan lands changes.
        assertEquals(20001L, result.cycleEpochDay)
    }

    @Test
    fun futureData_unscoredCycle_staysStale() {
        val unscored = ReadinessSnapshot.from(WhoopSnapshot(dateEpochDay = 20001, recoveryScore = null))

        assertTrue(DailyPlanSync.carryForward(unscored, targetEpochDay = 20000).isStale)
    }

    @Test
    fun futureData_planIsSavedUnderThePlannedDay_neverAFutureOne() {
        val plan = CoachingEngine().buildPlan(DailyPlanSync.carryForward(readinessFor(20001), targetEpochDay = 20000))

        assertEquals(20000L, plan.dateEpochDay)
        assertTrue(plan.activities.isNotEmpty())
        assertTrue(plan.activities.all { it.id.startsWith("20000:") })
    }

    // --- Last night: only today's night counts ---

    private val night = WhoopSnapshot(
        dateEpochDay = day,
        sleepPerformance = 80,
        sleepDurationHours = 6.0,
        sleepDebtHours = 2.0,
        wakeMinuteOfDay = 6 * 60,
    )

    @Test
    fun lastNight_isTodaysNight_orAClampedLaterOne() {
        assertEquals(night, DailyPlanSync.lastNight(night, day))
        val ahead = night.copy(dateEpochDay = day + 1)
        assertEquals(ahead, DailyPlanSync.lastNight(ahead, day))
    }

    @Test
    fun lastNight_anOlderNightIsNotLastNight() {
        assertNull(DailyPlanSync.lastNight(night.copy(dateEpochDay = day - 1), day))
        assertNull(DailyPlanSync.lastNight(night.copy(dateEpochDay = day - 21), day))
        assertNull(DailyPlanSync.lastNight(null, day))
    }

    @Test
    fun tonight_withAnOlderNight_usesOnlyTheUsersOwnTimes_andCarriesNoOldDebt() {
        val old = DailyPlanSync.lastNight(night.copy(dateEpochDay = day - 21), day)

        assertNull(DailyPlanSync.tonightFor(old, PlanConstraints()))
        val fromUser = DailyPlanSync.tonightFor(old, PlanConstraints(bedtimeMinute = 22 * 60 + 30))
        assertEquals(22 * 60 + 30, fromUser?.lightsOutMinute)
        assertTrue(fromUser?.fromUserBedtime == true)
        assertEquals(0.0, fromUser!!.debtHours, 1e-9)
    }

    // --- Holding plan → today's plan ---

    private fun card(
        slug: String,
        ruleId: String,
        status: ActivityStatus = ActivityStatus.PLANNED,
    ) = DailyActivity(
        id = "$day:$slug",
        title = slug,
        category = ActivityCategory.MOBILITY,
        priority = ActivityPriority.RECOMMENDED,
        whyItMatters = "Gentle movement.",
        estimatedMinutes = 10,
        intensity = Intensity.EASY,
        bestTimeOfDay = TimeOfDay.ANYTIME,
        status = status,
        ruleId = ruleId,
    )

    private val holding = listOf(
        card("connect-whoop", RuleIds.STALE_DATA_FALLBACK),
        card("hydration", RuleIds.HYDRATION_BASELINE),
    )

    private val todaysPlan = DailyPlan(
        dateEpochDay = day,
        headline = "Balanced day.",
        band = ReadinessBand.PRIMED,
        activities = listOf(card("strength", RuleIds.GOOD_RECOVERY_GREENLIGHT), card("hydration", RuleIds.HYDRATION_BASELINE)),
    )

    private val todaysCycle = readinessFor(day)

    @Test
    fun holdingPlan_isReplacedOnceTodaysCompletedCycleArrives() {
        assertTrue(DailyPlanSync.replacesHoldingPlan(holding, todaysCycle, todaysPlan))
    }

    @Test
    fun holdingPlan_completedStandInAlone_stillLetsTodaysPlanIn() {
        val doneStandIn = holding.map { if (it.ruleId == RuleIds.STALE_DATA_FALLBACK) it.copy(status = ActivityStatus.DONE) else it }

        assertTrue(DailyPlanSync.replacesHoldingPlan(doneStandIn, todaysCycle, todaysPlan))
    }

    @Test
    fun holdingPlan_aPendingStandInIsReplaced_evenWhenTodaysCardsAreAlreadySaved() {
        // A pain day: the holding plan already holds today's deload and hydration, plus the stand-in.
        val painDay = DailyPlan(
            dateEpochDay = day,
            headline = "Gentle day.",
            band = ReadinessBand.PRIMED,
            activities = listOf(card("pain-deload", RuleIds.PAIN_DELOAD), card("hydration", RuleIds.HYDRATION_BASELINE)),
        )
        val saved = painDay.activities + card("connect-whoop", RuleIds.STALE_DATA_FALLBACK, ActivityStatus.SKIPPED)

        assertTrue(DailyPlanSync.replacesHoldingPlan(saved, todaysCycle, painDay))
        // Once the stand-in is done and today's cards are all there, nothing is left to replace.
        val done = painDay.activities + card("connect-whoop", RuleIds.STALE_DATA_FALLBACK, ActivityStatus.DONE)
        assertFalse(DailyPlanSync.replacesHoldingPlan(done, todaysCycle, painDay))
    }

    @Test
    fun holdingPlan_staysWhileTheDataIsStillStaleOrUnscored() {
        val carried = DailyPlanSync.carryForward(readinessFor(day - 1), day)
        val unscored = ReadinessSnapshot.from(WhoopSnapshot(dateEpochDay = day, recoveryScore = null))
        // A hand-built snapshot that is not stale but has no score is never treated as a verdict either.
        val noScore = ReadinessSnapshot(dateEpochDay = day, recoveryScore = null, isStale = false)

        assertFalse(DailyPlanSync.replacesHoldingPlan(holding, carried, todaysPlan))
        assertFalse(DailyPlanSync.replacesHoldingPlan(holding, unscored, todaysPlan))
        assertFalse(DailyPlanSync.replacesHoldingPlan(holding, noScore, todaysPlan))
    }

    @Test
    fun aPlanThatWasNotAHoldingPlan_isNeverReplaced() {
        val saved = listOf(card("zone2", RuleIds.GOOD_RECOVERY_GREENLIGHT), card("hydration", RuleIds.HYDRATION_BASELINE))

        assertFalse(DailyPlanSync.replacesHoldingPlan(saved, todaysCycle, todaysPlan))
        assertFalse(DailyPlanSync.replacesHoldingPlan(emptyList(), todaysCycle, todaysPlan))
    }

    @Test
    fun onceTodaysPlanIsIn_aCompletedStandInKeptAsHistory_neverTriggersAnotherReplacement() {
        // After the replacement: the stand-in the user finished stays, today's plan is all there.
        val afterReseed = listOf(card("connect-whoop", RuleIds.STALE_DATA_FALLBACK, ActivityStatus.DONE)) +
            todaysPlan.activities.map { if (it.id.endsWith("strength")) it.copy(status = ActivityStatus.SNOOZED) else it }

        assertFalse(DailyPlanSync.replacesHoldingPlan(afterReseed, todaysCycle, todaysPlan))
    }

    @Test
    fun holdingPlanFromTheEngine_givesWayToTheEnginesPlanForTodaysCycle() {
        val engine = CoachingEngine()
        val holdingPlan = engine.buildPlan(DailyPlanSync.carryForward(readinessFor(day - 1), day))
        val freshPlan = engine.buildPlan(todaysCycle)

        assertTrue(holdingPlan.activities.any { it.ruleId == RuleIds.STALE_DATA_FALLBACK })
        assertTrue(DailyPlanSync.replacesHoldingPlan(holdingPlan.activities, todaysCycle, freshPlan))
        // A second holding sync on the same holding plan changes nothing.
        assertFalse(
            DailyPlanSync.replacesHoldingPlan(
                holdingPlan.activities,
                DailyPlanSync.carryForward(readinessFor(day - 1), day),
                holdingPlan,
            ),
        )
    }
}
