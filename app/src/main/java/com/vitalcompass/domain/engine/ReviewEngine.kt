package com.vitalcompass.domain.engine

import com.vitalcompass.domain.model.MetricReading
import com.vitalcompass.domain.model.MetricType
import com.vitalcompass.domain.model.WeeklyReviewSummary
import com.vitalcompass.domain.model.WorkoutLog
import com.vitalcompass.domain.model.WorkoutType

class ReviewEngine(
    private val planEngine: PlanEngine = PlanEngine(),
) {
    fun summarize(metrics: List<MetricReading>, logs: List<WorkoutLog>): WeeklyReviewSummary {
        val weight = values(metrics, MetricType.WEIGHT)
        val rhr = values(metrics, MetricType.RESTING_HEART_RATE)
        val systolic = values(metrics, MetricType.SYSTOLIC_BP).lastOrNull()
        val diastolic = values(metrics, MetricType.DIASTOLIC_BP).lastOrNull()
        val glucose = values(metrics, MetricType.FASTING_GLUCOSE).lastOrNull()
        val adherence = planEngine.adherencePercent(logs)
        val zone2 = logs.filter { it.type == WorkoutType.ZONE_2 }.sumOf { it.completedMinutes }
        val strength = logs.count { it.type == WorkoutType.STRENGTH && it.completedMinutes > 0 }

        val improved = buildList {
            if (adherence >= 70) add("Exercise consistency improved or held steady.")
            if (rhr.size >= 2 && rhr.last() < rhr.first()) add("Resting heart rate trend moved lower.")
            if (weight.size >= 2 && weight.last() < weight.first()) add("Weight trend moved lower.")
        }
        val needsAttention = buildList {
            if (adherence < 60) add("Adherence was below 60%; reduce friction next week.")
            if (glucose != null && glucose > 99.0) add("Fasting glucose remains worth tracking and discussing if repeated.")
            if (logs.any { it.painFlag }) add("Pain was reported; avoid progression until resolved.")
        }

        return WeeklyReviewSummary(
            adherencePercent = adherence,
            zone2Minutes = zone2,
            strengthSessions = strength,
            latestWeight = weight.lastOrNull(),
            sevenDayWeightAverage = weight.takeLast(7).averageOrNull(),
            latestRhr = rhr.lastOrNull(),
            sevenDayRhrAverage = rhr.takeLast(7).averageOrNull(),
            latestBp = if (systolic != null && diastolic != null) "${systolic.toInt()}/${diastolic.toInt()}" else null,
            latestFastingGlucose = glucose,
            improved = improved.ifEmpty { listOf("You collected data to make next week more informed.") },
            needsAttention = needsAttention.ifEmpty { listOf("No urgent trend flags from the current local data.") },
            nextWeekFocus = if (adherence < 60) "Make the plan easier to complete." else "Hold steady and confirm improvement with repeat measurements.",
        )
    }

    private fun values(metrics: List<MetricReading>, type: MetricType) =
        metrics.filter { it.type == type && it.value != null }.sortedBy { it.recordedAt }.mapNotNull { it.value }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
