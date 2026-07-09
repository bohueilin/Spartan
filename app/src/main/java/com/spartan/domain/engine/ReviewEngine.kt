package com.spartan.domain.engine

import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.WeeklyReviewSummary
import com.spartan.domain.model.WorkoutLog
import com.spartan.domain.model.WorkoutType
import java.time.LocalDate

class ReviewEngine(
    private val planEngine: PlanEngine = PlanEngine(),
    private val safetyEngine: SafetyEngine = SafetyEngine(),
) {
    fun summarize(
        metrics: List<MetricReading>,
        logs: List<WorkoutLog>,
        referenceDate: LocalDate = LocalDate.now(),
    ): WeeklyReviewSummary {
        val windowStart = referenceDate.minusDays(6)
        val weight = values(metrics, MetricType.WEIGHT)
        val rhr = values(metrics, MetricType.RESTING_HEART_RATE)
        val sevenDayWeight = valuesInWindow(metrics, MetricType.WEIGHT, referenceDate)
        val sevenDayRhr = valuesInWindow(metrics, MetricType.RESTING_HEART_RATE, referenceDate)
        val glucose = values(metrics, MetricType.FASTING_GLUCOSE).lastOrNull()
        val recentLogs = logs.filter { it.completedAt in windowStart..referenceDate }
        val adherence = planEngine.adherencePercent(recentLogs)
        val zone2 = recentLogs.filter { it.type == WorkoutType.ZONE_2 }.sumOf { it.completedMinutes }
        val strength = recentLogs.count { it.type == WorkoutType.STRENGTH && it.completedMinutes > 0 }

        val improved = buildList {
            if (adherence >= 70) add("Exercise consistency improved or held steady.")
            if (sevenDayRhr.size >= 2 && sevenDayRhr.last() < sevenDayRhr.first()) add("Resting heart rate trend moved lower.")
            if (sevenDayWeight.size >= 2 && sevenDayWeight.last() < sevenDayWeight.first()) add("Weight trend moved lower.")
        }
        val needsAttention = buildList {
            if (adherence < 60) add("Adherence was below 60%; reduce friction next week.")
            if (glucose != null && glucose > 99.0) add("Fasting glucose remains worth tracking and discussing if repeated.")
            if (recentLogs.any { it.painFlag }) add("Pain was reported; avoid progression until resolved.")
        }

        return WeeklyReviewSummary(
            adherencePercent = adherence,
            zone2Minutes = zone2,
            strengthSessions = strength,
            latestWeight = weight.lastOrNull(),
            sevenDayWeightAverage = sevenDayWeight.averageOrNull(),
            latestRhr = rhr.lastOrNull(),
            sevenDayRhrAverage = sevenDayRhr.averageOrNull(),
            latestBp = latestPairedBp(metrics),
            latestFastingGlucose = glucose,
            improved = improved.ifEmpty { listOf("You collected data to make next week more informed.") },
            needsAttention = needsAttention.ifEmpty { listOf("No urgent trend flags from the current local data.") },
            nextWeekFocus = if (adherence < 60) "Make the plan easier to complete." else "Hold steady and confirm improvement with repeat measurements.",
        ).also { summary ->
            listOf(
                summary.improved,
                summary.needsAttention,
                listOf(summary.nextWeekFocus),
            ).flatten().forEach(safetyEngine::sanitize)
        }
    }

    private fun values(metrics: List<MetricReading>, type: MetricType) =
        metrics.filter { it.type == type && it.value != null }.sortedBy { it.recordedAt }.mapNotNull { it.value }

    private fun latestPairedBp(metrics: List<MetricReading>): String? {
        val byDate = metrics
            .filter { it.type == MetricType.SYSTOLIC_BP || it.type == MetricType.DIASTOLIC_BP }
            .groupBy { it.recordedAt }

        return byDate.keys.sortedDescending().firstNotNullOfOrNull { date ->
            val readings = byDate.getValue(date)
            val systolic = readings.lastOrNull { it.type == MetricType.SYSTOLIC_BP }?.value
            val diastolic = readings.lastOrNull { it.type == MetricType.DIASTOLIC_BP }?.value
            if (systolic != null && diastolic != null) "${systolic.toInt()}/${diastolic.toInt()}" else null
        }
    }

    private fun valuesInWindow(metrics: List<MetricReading>, type: MetricType, referenceDate: LocalDate): List<Double> {
        val start = referenceDate.minusDays(6)
        return metrics
            .filter { it.type == type && it.value != null && it.recordedAt in start..referenceDate }
            .sortedBy { it.recordedAt }
            .mapNotNull { it.value }
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
