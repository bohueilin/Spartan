package com.spartan.domain

import com.spartan.domain.engine.ActionPlaybook
import com.spartan.domain.engine.SafetyEngine
import com.spartan.domain.engine.Signal
import com.spartan.domain.engine.SignalEngine
import com.spartan.domain.engine.SignalPosition
import com.spartan.domain.engine.SleepNeed
import com.spartan.domain.engine.SleepPlanner
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.TimeOfDay
import com.spartan.domain.model.WhoopSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SignalsTest {
    private val day0 = LocalDate.of(2026, 10, 1)
    private val safety = SafetyEngine()

    /** One reading per day for [values], oldest first. */
    private fun series(type: MetricType, vararg values: Double) =
        values.mapIndexed { i, v -> MetricReading(type, v, day0.plusDays(i.toLong()), id = i.toLong()) }

    // --- SignalEngine -----------------------------------------------------------------------

    @Test
    fun noReadingsMeansNoSignal() {
        assertNull(SignalEngine.signal(MetricType.HRV_RMSSD, emptyList()))
        assertTrue(SignalEngine.signals(emptyList()).isEmpty())
    }

    @Test
    fun tooLittleHistoryIsStillLearning() {
        val s = SignalEngine.signal(MetricType.HRV_RMSSD, series(MetricType.HRV_RMSSD, 60.0, 62.0, 64.0, 66.0, 40.0))!!
        assertEquals(SignalPosition.LEARNING, s.position)
        assertNull(s.normalLow)
        assertEquals(4, s.baselineDays)
    }

    @Test
    fun normalRangeIsTheMiddleHalfOfPriorDays() {
        // Prior days 60..68 (step 2): 25th pct = 62, 75th = 66.
        val s = SignalEngine.signal(MetricType.HRV_RMSSD, series(MetricType.HRV_RMSSD, 60.0, 62.0, 64.0, 66.0, 68.0, 64.0))!!
        assertEquals(62.0, s.normalLow!!, 1e-9)
        assertEquals(66.0, s.normalHigh!!, 1e-9)
        assertEquals(SignalPosition.WITHIN, s.position)
        assertEquals(64.0, s.latest, 1e-9)
    }

    @Test
    fun percentilesInterpolateBetweenDays() {
        // Prior: 10, 20, 30, 40, 50, 60 → 25th pct at rank 1.25 = 22.5, 75th at rank 3.75 = 47.5.
        val s = SignalEngine.signal(MetricType.RECOVERY_SCORE, series(MetricType.RECOVERY_SCORE, 10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 35.0))!!
        assertEquals(22.5, s.normalLow!!, 1e-9)
        assertEquals(47.5, s.normalHigh!!, 1e-9)
    }

    @Test
    fun aboveAndBelowAreTheDirectionOnly_whicheverWayIsHealthy() {
        // Position is where the reading sits, never a grade: a high resting HR is ABOVE just as a high HRV is.
        val base = doubleArrayOf(60.0, 62.0, 64.0, 66.0, 68.0)
        listOf(MetricType.HRV_RMSSD, MetricType.RESTING_HEART_RATE, MetricType.RESPIRATORY_RATE, MetricType.DAY_STRAIN).forEach { metric ->
            assertEquals(metric.name, SignalPosition.ABOVE, SignalEngine.signal(metric, series(metric, *base, 80.0))!!.position)
            assertEquals(metric.name, SignalPosition.BELOW, SignalEngine.signal(metric, series(metric, *base, 40.0))!!.position)
        }
    }

    @Test
    fun sameDayDuplicatesKeepTheNewestEntry() {
        val readings = series(MetricType.RESTING_HEART_RATE, 55.0, 55.0, 55.0, 55.0, 55.0, 59.0) +
            // A manual entry made earlier the same day (lower id) must not override the sync.
            MetricReading(MetricType.RESTING_HEART_RATE, 68.0, day0.plusDays(5), id = -1)
        val s = SignalEngine.signal(MetricType.RESTING_HEART_RATE, readings)!!
        assertEquals(59.0, s.latest, 1e-9)
        assertEquals(6, s.history.size)
        assertEquals((0L..5L).map { day0.plusDays(it) }, s.dates)
    }

    @Test
    fun historyIsCappedToAMonthPlusToday() {
        val s = SignalEngine.signal(MetricType.HRV_RMSSD, series(MetricType.HRV_RMSSD, *DoubleArray(45) { 50.0 + it }))!!
        assertEquals(31, s.history.size)
        assertEquals(30, s.baselineDays)
        // The dates stay parallel to the capped history, ending on the latest day.
        assertEquals(31, s.dates.size)
        assertEquals(day0.plusDays(14), s.dates.first())
        assertEquals(day0.plusDays(44), s.dates.last())
    }

    @Test
    fun datesFollowTheReadings_gapsIncluded() {
        // Readings on days 0, 3 and 4 only: the dates say so, oldest first, even when given out of order.
        val readings = listOf(4L to 61.0, 0L to 55.0, 3L to 58.0).map { (d, v) ->
            MetricReading(MetricType.HRV_RMSSD, v, day0.plusDays(d), id = d)
        } + MetricReading(MetricType.HRV_RMSSD, null, day0.plusDays(5)) // an empty entry adds no day
        val s = SignalEngine.signal(MetricType.HRV_RMSSD, readings)!!
        assertEquals(SignalPosition.LEARNING, s.position)
        assertEquals(listOf(55.0, 58.0, 61.0), s.history)
        assertEquals(listOf(day0, day0.plusDays(3), day0.plusDays(4)), s.dates)
        // Signals built by hand carry no dates unless given.
        assertTrue(Signal(MetricType.HRV_RMSSD, 1.0, listOf(1.0), null, null, SignalPosition.LEARNING).dates.isEmpty())
    }

    @Test
    fun signalsComeBackInPresentationOrderAndSkipEmptyMetrics() {
        val readings = series(MetricType.DAY_STRAIN, 10.0) + series(MetricType.RECOVERY_SCORE, 50.0)
        assertEquals(listOf(MetricType.RECOVERY_SCORE, MetricType.DAY_STRAIN), SignalEngine.signals(readings).map { it.metric })
    }

    // What a reading means is copy, not engine logic: Insights and metric detail share string
    // resources, checked line by line through SafetyEngine in InsightsCopyTest
    // (everyMeaningLine_onInsightsAndInDetail_isSafeWellnessCopy).

    // --- ActionPlaybook ---------------------------------------------------------------------

    @Test
    fun everyMetricHasSafeActionsExceptClinicianFirstLabs() {
        val clinicianFirst = setOf(MetricType.APOB, MetricType.LPA, MetricType.CAC, MetricType.CUSTOM)
        MetricType.entries.forEach { metric ->
            val actions = ActionPlaybook.forMetric(metric)
            assertEquals(metric.name, metric in clinicianFirst, actions.isEmpty())
            actions.forEach { a ->
                (listOf(a.title, a.why) + a.steps).forEach { assertTrue("unsafe: $it", safety.validateCopy(it)) }
                assertTrue(a.minutes > 0)
            }
        }
    }

    @Test
    fun everyActionsWhy_isObservationalWellnessCopy_notAClaimAboutTheBody() {
        // The old lines' physiological outcome claims: "the state that lifts HRV", "the most reliable
        // way to lower resting heart rate", "boosts circulation", "anchors your body clock"...
        val claims = Regex(
            """\b(lifts?|raises?|boosts?|lowers?|improves?|protects?|anchors?|supports?)\b|rest-and-recover|nervous system|""" +
                """circulation|body clock|deep sleep|dehydration|fuel|long-term health|most reliable|sleep deeper""",
            RegexOption.IGNORE_CASE,
        )
        val actions = MetricType.entries.flatMap { ActionPlaybook.forMetric(it) }.distinctBy { it.key }
        assertEquals(11, actions.size)
        actions.forEach { assertFalse("${it.key}: ${it.why}", claims.containsMatchIn(it.why)) }
    }

    @Test
    fun uniqueFirstActions_neverRepeatAnActionAcrossTheCards() {
        val picks = ActionPlaybook.uniqueFirstActions(SignalEngine.DAILY)
        // Every pick is distinct, and each is one of its own metric's actions.
        assertEquals(picks.size, picks.values.map { it.key }.toSet().size)
        picks.forEach { (metric, action) -> assertTrue(metric.name, action in ActionPlaybook.forMetric(metric)) }
        // Sleep performance keeps its wind-down; sleep debt gets the next lever instead of repeating it.
        assertEquals("wind-down", picks.getValue(MetricType.SLEEP_PERFORMANCE).key)
        assertEquals("caffeine-cutoff", picks.getValue(MetricType.SLEEP_DEBT).key)
        // HRV keeps breathing; respiratory rate's breathing and water are both taken, so it gets none.
        assertEquals("breathing", picks.getValue(MetricType.HRV_RMSSD).key)
        assertEquals("hydrate", picks.getValue(MetricType.RESTING_HEART_RATE).key)
        assertFalse(MetricType.RESPIRATORY_RATE in picks)
        // A metric with nothing taken before it keeps its best action.
        assertEquals(ActionPlaybook.forMetric(MetricType.RECOVERY_SCORE).first(), picks.getValue(MetricType.RECOVERY_SCORE))
    }

    @Test
    fun uniqueFirstActions_followTheCardOrder() {
        // Order decides who keeps a shared action: debt first takes the wind-down, so sleep moves on.
        val picks = ActionPlaybook.uniqueFirstActions(listOf(MetricType.SLEEP_DEBT, MetricType.SLEEP_PERFORMANCE))
        assertEquals("wind-down", picks.getValue(MetricType.SLEEP_DEBT).key)
        assertEquals("caffeine-cutoff", picks.getValue(MetricType.SLEEP_PERFORMANCE).key)
        // Respiratory rate alone keeps its breathing.
        assertEquals("breathing", ActionPlaybook.uniqueFirstActions(listOf(MetricType.RESPIRATORY_RATE)).getValue(MetricType.RESPIRATORY_RATE).key)
    }

    @Test
    fun uniqueFirstActions_skipClinicianFirstLabsAndRepeatedMetrics() {
        assertTrue(ActionPlaybook.uniqueFirstActions(emptyList()).isEmpty())
        assertTrue(ActionPlaybook.uniqueFirstActions(listOf(MetricType.APOB, MetricType.CAC)).isEmpty())
        // The same metric twice keeps its first pick rather than taking a second action.
        val twice = ActionPlaybook.uniqueFirstActions(listOf(MetricType.HRV_RMSSD, MetricType.HRV_RMSSD, MetricType.RESPIRATORY_RATE))
        assertEquals("breathing", twice.getValue(MetricType.HRV_RMSSD).key)
        assertEquals("hydrate", twice.getValue(MetricType.RESPIRATORY_RATE).key)
    }

    @Test
    fun anActionBecomesAStableUserAddedPlanItem() {
        val action = ActionPlaybook.forMetric(MetricType.HRV_RMSSD).first()
        val activity = ActionPlaybook.toActivity(action, MetricType.HRV_RMSSD, 20_000)
        assertEquals("20000:add:${action.key}", activity.id)
        assertEquals(ActionPlaybook.USER_ADDED_RULE, activity.ruleId)
        assertEquals(ActivityPriority.RECOMMENDED, activity.priority)
        assertEquals(MetricType.HRV_RMSSD, activity.relatedMetric)
        assertEquals(action.steps, activity.instructions)
        assertEquals(action.minutes, activity.estimatedMinutes)
    }

    @Test
    fun anActionIsPlannedWhenItsTitleOrIdIsAlreadyInToday() {
        val action = ActionPlaybook.forMetric(MetricType.SLEEP_DEBT).first()
        fun item(id: String, title: String) = DailyActivity(id, title, ActivityCategory.SLEEP, ActivityPriority.RECOMMENDED,
            "why", estimatedMinutes = 5, intensity = Intensity.REST, bestTimeOfDay = TimeOfDay.EVENING, ruleId = "R")
        assertTrue(ActionPlaybook.isPlanned(action, listOf(item("1:winddown", action.title.uppercase()))))
        assertTrue(ActionPlaybook.isPlanned(action, listOf(item("1:add:${action.key}", "renamed"))))
        assertFalse(ActionPlaybook.isPlanned(action, listOf(item("1:hydration", "Hydration reminder"))))
    }

    // --- SleepNeed ---------------------------------------------------------------------------

    @Test
    fun sleepNeedIsHoursSleptOverPerformance() {
        // 7h at 85% of need → about 8h 14m needed.
        assertEquals(7.0 / 0.85, SleepNeed.neededHours(7.0, 85)!!, 1e-9)
        assertEquals(7.0 / 0.99, SleepNeed.neededHours(7.0, 99)!!, 1e-9)
    }

    @Test
    fun sleepNeedIsUnknownAtWhoopsHundredPercentCap() {
        // At 100% the need is only known to be at most the hours slept, so no need is claimed.
        assertNull(SleepNeed.neededHours(9.75, 100))
        assertNull(SleepNeed.neededHours(7.0, 100))
        assertNull(SleepNeed.neededHours(8.25, 110))
    }

    @Test
    fun sleepNeedNeedsBothValuesAndPositiveInputs() {
        assertNull(SleepNeed.neededHours(null, 85))
        assertNull(SleepNeed.neededHours(7.0, null))
        assertNull(SleepNeed.neededHours(0.0, 85))
        assertNull(SleepNeed.neededHours(-1.0, 85))
        assertNull(SleepNeed.neededHours(7.0, 0))
        assertNull(SleepNeed.neededHours(7.0, -10))
        assertNull(SleepNeed.neededHours(Double.NaN, 85))
    }

    @Test
    fun implausibleSleepNeedIsDropped() {
        assertNull(SleepNeed.neededHours(7.0, 40))   // 17.5 h
        assertNull(SleepNeed.neededHours(3.0, 90))   // 3h 20m
        assertEquals(4.0, SleepNeed.neededHours(2.0, 50)!!, 1e-9)   // bounds are inclusive
        assertEquals(14.0, SleepNeed.neededHours(7.0, 50)!!, 1e-9)
    }

    @Test
    fun whoopsOwnSleepNeedWinsOverTheEstimate() {
        // 7 h at 85% estimates ~8h 14m, but WHOOP said 8.5 h: WHOOP's own figure is used.
        val night = WhoopSnapshot(dateEpochDay = 1, sleepDurationHours = 7.0, sleepPerformance = 85, sleepNeedHours = 8.5)
        assertEquals(8.5, SleepNeed.forSnapshot(night)!!, 1e-9)
        // Even where the ratio can't say (WHOOP's 100% cap).
        assertEquals(7.9, SleepNeed.forSnapshot(night.copy(sleepPerformance = 100, sleepNeedHours = 7.9))!!, 1e-9)
    }

    @Test
    fun withoutWhoopsNeedTheEstimateIsUsed() {
        val night = WhoopSnapshot(dateEpochDay = 1, sleepDurationHours = 7.0, sleepPerformance = 85)
        assertEquals(7.0 / 0.85, SleepNeed.forSnapshot(night)!!, 1e-9)
    }

    @Test
    fun anImplausibleWhoopNeedIsABadReading_andFallsBackToTheEstimate() {
        val night = WhoopSnapshot(dateEpochDay = 1, sleepDurationHours = 7.0, sleepPerformance = 85)
        assertEquals(7.0 / 0.85, SleepNeed.forSnapshot(night.copy(sleepNeedHours = 20.0))!!, 1e-9)
        assertEquals(7.0 / 0.85, SleepNeed.forSnapshot(night.copy(sleepNeedHours = 0.0))!!, 1e-9)
        assertEquals(7.0 / 0.85, SleepNeed.forSnapshot(night.copy(sleepNeedHours = Double.NaN))!!, 1e-9)
        // The plausible range is inclusive, as for the estimate.
        assertEquals(4.0, SleepNeed.forSnapshot(night.copy(sleepNeedHours = 4.0))!!, 1e-9)
        assertEquals(14.0, SleepNeed.forSnapshot(night.copy(sleepNeedHours = 14.0))!!, 1e-9)
    }

    @Test
    fun noSnapshotOrNothingToGoOnMeansNoNeed() {
        assertNull(SleepNeed.forSnapshot(null))
        assertNull(SleepNeed.forSnapshot(WhoopSnapshot(dateEpochDay = 1)))
        // A bad WHOOP figure and no estimate either: still no need, never an invented one.
        assertNull(SleepNeed.forSnapshot(WhoopSnapshot(dateEpochDay = 1, sleepNeedHours = 30.0)))
    }

    // --- SleepPlanner -----------------------------------------------------------------------

    @Test
    fun noNeedAndNoUserBedtimeMeansNoTarget() {
        // Never an invented 8-hour default: without the user's own need there is no target.
        assertNull(SleepPlanner.tonight(null, null, null))
        assertNull(SleepPlanner.tonight(7 * 60, 1.0, null))
        assertNull(SleepPlanner.tonight(null, 0.0, 8.0)) // need known, wake unknown
    }

    @Test
    fun lightsOutComesFromTheUsersOwnNeed() {
        val need = 8.0 + 10.0 / 60 // 8h 10m
        val plan = SleepPlanner.tonight(7 * 60, 0.0, need)!!
        assertEquals(22 * 60 + 50, plan.lightsOutMinute)
        assertEquals(22 * 60 + 5, plan.windDownMinute)
        assertEquals(need, plan.sleepHours!!, 1e-9)
        assertEquals(need, plan.needHours!!, 1e-9)
        assertEquals(0.0, plan.debtHours, 1e-9)
        assertFalse(plan.fromUserBedtime)
        // A shorter need moves lights-out later — the target follows the person, not a constant.
        assertEquals(23 * 60 + 30, SleepPlanner.tonight(7 * 60, 0.0, 7.5)!!.lightsOutMinute)
    }

    @Test
    fun debtIsShownButNeverCountedTwice() {
        // WHOOP's sleep need already includes paying back recent short nights.
        val plan = SleepPlanner.tonight(7 * 60, 1.8, 8.0)!!
        assertEquals(8.0, plan.sleepHours!!, 1e-9)
        assertEquals(23 * 60, plan.lightsOutMinute)
        assertEquals(1.8, plan.debtHours, 1e-9)
        assertEquals(8.0, plan.needHours!!, 1e-9)
    }

    @Test
    fun aLargerNeedAndEarlyWakeMoveLightsOutEarlier() {
        val plan = SleepPlanner.tonight(6 * 60 + 30, 0.5, 8.5)!!
        assertEquals(8.5, plan.sleepHours!!, 1e-9)
        assertEquals(22 * 60, plan.lightsOutMinute)
    }

    @Test
    fun theUsersWakeTimeOverridesTheObservedOne() {
        val plan = SleepPlanner.tonight(7 * 60, 0.0, 8.0, userWakeMinute = 6 * 60)!!
        assertEquals(22 * 60, plan.lightsOutMinute)
        // A user wake time alone is enough when the observed one is missing.
        assertEquals(22 * 60, SleepPlanner.tonight(null, 0.0, 8.0, userWakeMinute = 6 * 60)!!.lightsOutMinute)
    }

    @Test
    fun theUsersBedtimeWinsOverAComputedOne() {
        // Need 8h and wake 7:00 would give 11:00 PM, but the user said 11:30 PM: the plan honours it.
        val plan = SleepPlanner.tonight(7 * 60, 0.4, 8.0, userBedtimeMinute = 23 * 60 + 30)!!
        assertTrue(plan.fromUserBedtime)
        assertEquals(23 * 60 + 30, plan.lightsOutMinute)
        assertEquals(22 * 60 + 45, plan.windDownMinute)
        assertEquals(7.5, plan.sleepHours!!, 1e-9) // 11:30 PM → 7:00 AM, the night it actually plans for
        assertEquals(8.0, plan.needHours!!, 1e-9)  // the need is kept alongside, never invented
        assertEquals(0.4, plan.debtHours, 1e-9)
        // The user's wake time sets the length of that night too.
        assertEquals(8.0, SleepPlanner.tonight(7 * 60, null, null, userBedtimeMinute = 22 * 60, userWakeMinute = 6 * 60)!!.sleepHours!!, 1e-9)
        // A bedtime equal to the wake time is no night at all: no hours claimed.
        assertNull(SleepPlanner.tonight(7 * 60, null, null, userBedtimeMinute = 7 * 60)!!.sleepHours)
    }

    @Test
    fun withoutANeedTheUsersOwnBedtimeIsTheTarget() {
        val plan = SleepPlanner.tonight(7 * 60, 1.2, null, userBedtimeMinute = 22 * 60 + 33)!!
        assertTrue(plan.fromUserBedtime)
        assertEquals(22 * 60 + 30, plan.lightsOutMinute) // rounded down to five minutes
        assertEquals(21 * 60 + 45, plan.windDownMinute)
        assertEquals(8.5, plan.sleepHours!!, 1e-9) // 10:30 PM → 7:00 AM
        assertNull(plan.needHours)
        assertEquals(1.2, plan.debtHours, 1e-9)

        // A known need without any wake time also falls back to the bedtime, and keeps the need.
        val noWake = SleepPlanner.tonight(null, null, 8.0, userBedtimeMinute = 22 * 60)!!
        assertTrue(noWake.fromUserBedtime)
        assertEquals(8.0, noWake.needHours!!, 1e-9)
        assertEquals(0.0, noWake.debtHours, 1e-9)
    }

    @Test
    fun aBedtimeJustAfterMidnightWindsDownTheEveningBefore() {
        val plan = SleepPlanner.tonight(null, null, null, userBedtimeMinute = 20)!!
        assertEquals(20, plan.lightsOutMinute)
        assertEquals(23 * 60 + 35, plan.windDownMinute)
    }

    @Test
    fun lightsOutRoundsDownToFiveMinutesAndNegativeDebtIsIgnored() {
        val plan = SleepPlanner.tonight(7 * 60 + 3, -2.0, 8.0)!!
        assertEquals(23 * 60, plan.lightsOutMinute) // 23:03 → 23:00
        assertEquals(0.0, plan.debtHours, 1e-9)
        assertEquals(8.0, plan.sleepHours!!, 1e-9)
    }

    @Test
    fun pastLightsOutCoversTheNightButNotTheNextDay() {
        val plan = SleepPlanner.tonight(7 * 60, 0.0, 8.0)!! // lights out 23:00
        assertFalse(SleepPlanner.isPastLightsOut(plan, 22 * 60))      // before target
        assertFalse(SleepPlanner.isPastLightsOut(plan, 23 * 60))      // exactly on time
        assertTrue(SleepPlanner.isPastLightsOut(plan, 23 * 60 + 30))  // late
        assertTrue(SleepPlanner.isPastLightsOut(plan, 2 * 60))        // after midnight
        assertFalse(SleepPlanner.isPastLightsOut(plan, 9 * 60))       // next morning: a new plan
    }
}
