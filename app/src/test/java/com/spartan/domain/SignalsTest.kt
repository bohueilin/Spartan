package com.spartan.domain

import com.spartan.domain.engine.ActionPlaybook
import com.spartan.domain.engine.SafetyEngine
import com.spartan.domain.engine.Signal
import com.spartan.domain.engine.SignalEngine
import com.spartan.domain.engine.SignalPosition
import com.spartan.domain.engine.SleepPlanner
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.TimeOfDay
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
        assertNull(s.favorable)
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
        assertNull(s.favorable)
    }

    @Test
    fun percentilesInterpolateBetweenDays() {
        // Prior: 10, 20, 30, 40, 50, 60 → 25th pct at rank 1.25 = 22.5, 75th at rank 3.75 = 47.5.
        val s = SignalEngine.signal(MetricType.RECOVERY_SCORE, series(MetricType.RECOVERY_SCORE, 10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 35.0))!!
        assertEquals(22.5, s.normalLow!!, 1e-9)
        assertEquals(47.5, s.normalHigh!!, 1e-9)
    }

    @Test
    fun aboveAndBelowFollowEachMetricsHealthyDirection() {
        val base = doubleArrayOf(60.0, 62.0, 64.0, 66.0, 68.0)
        val hrvHigh = SignalEngine.signal(MetricType.HRV_RMSSD, series(MetricType.HRV_RMSSD, *base, 80.0))!!
        assertEquals(SignalPosition.ABOVE, hrvHigh.position)
        assertEquals(true, hrvHigh.favorable)

        val hrvLow = SignalEngine.signal(MetricType.HRV_RMSSD, series(MetricType.HRV_RMSSD, *base, 40.0))!!
        assertEquals(SignalPosition.BELOW, hrvLow.position)
        assertEquals(false, hrvLow.favorable)

        val rhrHigh = SignalEngine.signal(MetricType.RESTING_HEART_RATE, series(MetricType.RESTING_HEART_RATE, *base, 80.0))!!
        assertEquals(false, rhrHigh.favorable)

        val rrHigh = SignalEngine.signal(MetricType.RESPIRATORY_RATE, series(MetricType.RESPIRATORY_RATE, *base, 80.0))!!
        assertEquals(false, rrHigh.favorable)

        val strainHigh = SignalEngine.signal(MetricType.DAY_STRAIN, series(MetricType.DAY_STRAIN, *base, 80.0))!!
        assertEquals(SignalPosition.ABOVE, strainHigh.position)
        assertNull(strainHigh.favorable)
    }

    @Test
    fun healthyDirections() {
        assertEquals(true, SignalEngine.healthyDirectionUp(MetricType.HRV_RMSSD))
        assertEquals(false, SignalEngine.healthyDirectionUp(MetricType.SLEEP_DEBT))
        assertEquals(false, SignalEngine.healthyDirectionUp(MetricType.RESPIRATORY_RATE))
        assertNull(SignalEngine.healthyDirectionUp(MetricType.ENERGY_KCAL))
    }

    @Test
    fun sameDayDuplicatesKeepTheNewestEntry() {
        val readings = series(MetricType.RESTING_HEART_RATE, 55.0, 55.0, 55.0, 55.0, 55.0, 59.0) +
            // A manual entry made earlier the same day (lower id) must not override the sync.
            MetricReading(MetricType.RESTING_HEART_RATE, 68.0, day0.plusDays(5), id = -1)
        val s = SignalEngine.signal(MetricType.RESTING_HEART_RATE, readings)!!
        assertEquals(59.0, s.latest, 1e-9)
        assertEquals(6, s.history.size)
    }

    @Test
    fun historyIsCappedToAMonthPlusToday() {
        val s = SignalEngine.signal(MetricType.HRV_RMSSD, series(MetricType.HRV_RMSSD, *DoubleArray(45) { 50.0 + it }))!!
        assertEquals(31, s.history.size)
        assertEquals(30, s.baselineDays)
    }

    @Test
    fun signalsComeBackInPresentationOrderAndSkipEmptyMetrics() {
        val readings = series(MetricType.DAY_STRAIN, 10.0) + series(MetricType.RECOVERY_SCORE, 50.0)
        assertEquals(listOf(MetricType.RECOVERY_SCORE, MetricType.DAY_STRAIN), SignalEngine.signals(readings).map { it.metric })
    }

    @Test
    fun everyMeaningIsSafeCopy() {
        val positions = listOf(SignalPosition.ABOVE, SignalPosition.BELOW, SignalPosition.WITHIN, SignalPosition.LEARNING)
        val metrics = SignalEngine.DAILY + MetricType.ENERGY_KCAL
        val seen = mutableSetOf<String>()
        metrics.forEach { metric ->
            positions.forEach { pos ->
                val text = SignalEngine.meaning(Signal(metric, 1.0, listOf(1.0), 0.0, 2.0, pos))
                assertTrue("unsafe: $text", safety.validateCopy(text))
                seen += text
            }
        }
        // 7 metrics x 3 outcomes + learning + the generic fallback for metrics without a script.
        assertEquals(23, seen.size)
    }

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

    // --- SleepPlanner -----------------------------------------------------------------------

    @Test
    fun noDataMeansEightHoursBeforeASevenAmWake() {
        val plan = SleepPlanner.tonight(null, null)
        assertEquals(23 * 60, plan.lightsOutMinute)
        assertEquals(22 * 60 + 15, plan.windDownMinute)
        assertEquals(8.0, plan.sleepHours, 1e-9)
        assertEquals(0.0, plan.debtHours, 1e-9)
    }

    @Test
    fun debtAddsAtMostOneHourInOneNight() {
        val plan = SleepPlanner.tonight(7 * 60, 1.8)
        assertEquals(9.0, plan.sleepHours, 1e-9)
        assertEquals(22 * 60, plan.lightsOutMinute)
        assertEquals(1.8, plan.debtHours, 1e-9)
    }

    @Test
    fun partialDebtAndEarlyWake() {
        val plan = SleepPlanner.tonight(6 * 60 + 30, 0.5)
        assertEquals(8.5, plan.sleepHours, 1e-9)
        assertEquals(22 * 60, plan.lightsOutMinute)
    }

    @Test
    fun lightsOutRoundsDownToFiveMinutesAndNegativeDebtIsIgnored() {
        val plan = SleepPlanner.tonight(7 * 60 + 3, -2.0)
        assertEquals(23 * 60, plan.lightsOutMinute) // 23:03 → 23:00
        assertEquals(0.0, plan.debtHours, 1e-9)
    }

    @Test
    fun pastLightsOutCoversTheNightButNotTheNextDay() {
        val plan = SleepPlanner.tonight(7 * 60, 0.0) // lights out 23:00
        assertFalse(SleepPlanner.isPastLightsOut(plan, 22 * 60))      // before target
        assertFalse(SleepPlanner.isPastLightsOut(plan, 23 * 60))      // exactly on time
        assertTrue(SleepPlanner.isPastLightsOut(plan, 23 * 60 + 30))  // late
        assertTrue(SleepPlanner.isPastLightsOut(plan, 2 * 60))        // after midnight
        assertFalse(SleepPlanner.isPastLightsOut(plan, 9 * 60))       // next morning: a new plan
    }
}
