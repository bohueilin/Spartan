package com.spartan.domain

import com.spartan.domain.engine.PlanEngine
import com.spartan.domain.engine.ReminderEngine
import com.spartan.domain.engine.ReminderRequest
import com.spartan.domain.engine.ReviewEngine
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.WorkoutLog
import com.spartan.domain.model.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanReviewReminderTest {
    @Test
    fun weeklyPlan_hasDefaultDistribution() {
        val plan = PlanEngine().defaultPlan()

        assertEquals(3, plan.workouts.count { it.type == WorkoutType.ZONE_2 })
        assertEquals(2, plan.workouts.count { it.type == WorkoutType.STRENGTH })
        assertEquals(1, plan.workouts.count { it.type == WorkoutType.MOBILITY })
        assertEquals(1, plan.workouts.count { it.type == WorkoutType.RECOVERY })
        assertEquals(1, plan.workouts.count { it.type == WorkoutType.REVIEW })
    }

    @Test
    fun weeklyPlan_reducesProgressionAfterPainFlag() {
        val plan = PlanEngine().defaultPlan(
            listOf(WorkoutLog(WorkoutType.STRENGTH, plannedMinutes = 35, completedMinutes = 35, rpe = 8, painFlag = true)),
        )

        assertEquals(30, plan.workouts.first { it.type == WorkoutType.ZONE_2 }.minutes)
        assertEquals(25, plan.workouts.first { it.type == WorkoutType.STRENGTH }.minutes)
        assertTrue(plan.focus.contains("pain-free"))
    }

    @Test
    fun weeklyPlan_holdsProgressionAfterHighRpe() {
        val plan = PlanEngine().defaultPlan(
            listOf(WorkoutLog(WorkoutType.ZONE_2, plannedMinutes = 35, completedMinutes = 35, rpe = 9, painFlag = false)),
        )

        assertEquals(30, plan.workouts.first { it.type == WorkoutType.ZONE_2 }.minutes)
        assertEquals(25, plan.workouts.first { it.type == WorkoutType.STRENGTH }.minutes)
        assertTrue(plan.focus.contains("recovery", ignoreCase = true))
    }

    @Test
    fun adherenceCalculation_capsCompletedAtPlannedMinutes() {
        val logs = listOf(
            WorkoutLog(WorkoutType.ZONE_2, plannedMinutes = 30, completedMinutes = 45, rpe = 5, painFlag = false),
            WorkoutLog(WorkoutType.STRENGTH, plannedMinutes = 30, completedMinutes = 15, rpe = 6, painFlag = false),
        )

        assertEquals(75, PlanEngine().adherencePercent(logs))
    }

    @Test
    fun reviewSummaryCalculatesAdherenceAndLatestValues() {
        val today = LocalDate.now()
        val metrics = listOf(
            MetricReading(MetricType.WEIGHT, 82.0, today.minusDays(1)),
            MetricReading(MetricType.WEIGHT, 81.0, today),
            MetricReading(MetricType.RESTING_HEART_RATE, 70.0, today.minusDays(1)),
            MetricReading(MetricType.RESTING_HEART_RATE, 68.0, today),
            MetricReading(MetricType.SYSTOLIC_BP, 102.0, today),
            MetricReading(MetricType.DIASTOLIC_BP, 67.0, today),
            MetricReading(MetricType.FASTING_GLUCOSE, 108.0, today),
        )
        val logs = listOf(
            WorkoutLog(WorkoutType.ZONE_2, 30, 30, 5, false, today),
            WorkoutLog(WorkoutType.STRENGTH, 35, 35, 6, false, today),
        )

        val review = ReviewEngine().summarize(metrics, logs, referenceDate = today)

        assertEquals(100, review.adherencePercent)
        assertEquals(30, review.zone2Minutes)
        assertEquals(1, review.strengthSessions)
        assertEquals("102/67", review.latestBp)
        assertEquals(108.0, review.latestFastingGlucose!!, 0.0)
    }

    @Test
    fun reviewSummaryUsesSevenCalendarDayAverages() {
        val today = LocalDate.of(2026, 5, 11)
        val metrics = listOf(
            MetricReading(MetricType.WEIGHT, 90.0, today.minusDays(8)),
            MetricReading(MetricType.WEIGHT, 80.0, today.minusDays(6)),
            MetricReading(MetricType.WEIGHT, 82.0, today),
            MetricReading(MetricType.RESTING_HEART_RATE, 75.0, today.minusDays(7)),
            MetricReading(MetricType.RESTING_HEART_RATE, 70.0, today.minusDays(1)),
            MetricReading(MetricType.RESTING_HEART_RATE, 68.0, today),
        )

        val review = ReviewEngine().summarize(metrics, emptyList(), referenceDate = today)

        assertEquals(81.0, review.sevenDayWeightAverage!!, 0.0)
        assertEquals(69.0, review.sevenDayRhrAverage!!, 0.0)
    }

    @Test
    fun reviewSummaryUsesLatestPairedBpDate() {
        val today = LocalDate.of(2026, 5, 11)
        val metrics = listOf(
            MetricReading(MetricType.SYSTOLIC_BP, 130.0, today),
            MetricReading(MetricType.SYSTOLIC_BP, 102.0, today.minusDays(1)),
            MetricReading(MetricType.DIASTOLIC_BP, 67.0, today.minusDays(1)),
        )

        val review = ReviewEngine().summarize(metrics, emptyList(), referenceDate = today)

        assertEquals("102/67", review.latestBp)
    }

    @Test
    fun reviewSummaryIgnoresOldPainAndOldMetricChanges() {
        val today = LocalDate.of(2026, 5, 11)
        val metrics = listOf(
            MetricReading(MetricType.WEIGHT, 90.0, today.minusDays(30)),
            MetricReading(MetricType.WEIGHT, 80.0, today),
            MetricReading(MetricType.RESTING_HEART_RATE, 80.0, today.minusDays(30)),
            MetricReading(MetricType.RESTING_HEART_RATE, 70.0, today),
        )
        val logs = listOf(
            WorkoutLog(WorkoutType.STRENGTH, 30, 30, 6, painFlag = true, completedAt = today.minusDays(20)),
            WorkoutLog(WorkoutType.ZONE_2, 30, 30, 5, painFlag = false, completedAt = today),
        )

        val review = ReviewEngine().summarize(metrics, logs, referenceDate = today)

        assertFalse(review.improved.any { it.contains("heart rate", ignoreCase = true) || it.contains("weight", ignoreCase = true) })
        assertFalse(review.needsAttention.any { it.contains("Pain was reported", ignoreCase = true) })
    }

    @Test
    fun reminderEnginePreventsDuplicateIds() {
        val engine = ReminderEngine()
        val original = ReminderRequest("logging", "Log", 20, 30, true)
        val updated = ReminderRequest("logging", "Log again", 21, 0, true)

        val result = engine.deduplicate(listOf(original), updated)

        assertEquals(1, result.size)
        assertEquals(21, result.single().hour)
        assertTrue(engine.canSchedule(permissionGranted = true, updated))
        assertFalse(engine.canSchedule(permissionGranted = false, updated))
    }

    @Test
    fun reminderEngineRejectsInvalidTimesAndDisabledRequests() {
        val engine = ReminderEngine()

        assertFalse(engine.canSchedule(permissionGranted = true, ReminderRequest("bad-hour", "Bad", 24, 0, true)))
        assertFalse(engine.canSchedule(permissionGranted = true, ReminderRequest("bad-minute", "Bad", 20, 60, true)))
        assertFalse(engine.canSchedule(permissionGranted = true, ReminderRequest("off", "Off", 20, 30, false)))
        assertTrue(
            engine.deduplicate(
                listOf(ReminderRequest("logging", "Log", 20, 30, true)),
                ReminderRequest("logging", "Log", 20, 30, false),
            ).isEmpty(),
        )
    }
}
