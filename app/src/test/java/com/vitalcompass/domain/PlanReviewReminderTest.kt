package com.vitalcompass.domain

import com.vitalcompass.domain.engine.PlanEngine
import com.vitalcompass.domain.engine.ReminderEngine
import com.vitalcompass.domain.engine.ReminderRequest
import com.vitalcompass.domain.engine.ReviewEngine
import com.vitalcompass.domain.model.MetricReading
import com.vitalcompass.domain.model.MetricType
import com.vitalcompass.domain.model.WorkoutLog
import com.vitalcompass.domain.model.WorkoutType
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
        assertEquals(1, plan.workouts.count { it.type == WorkoutType.REVIEW })
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
        val logs = listOf(WorkoutLog(WorkoutType.ZONE_2, 30, 30, 5, false))

        val review = ReviewEngine().summarize(metrics, logs)

        assertEquals(100, review.adherencePercent)
        assertEquals(30, review.zone2Minutes)
        assertEquals("102/67", review.latestBp)
        assertEquals(108.0, review.latestFastingGlucose!!, 0.0)
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
}
