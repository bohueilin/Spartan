package com.vitalcompass.domain.model

import java.time.LocalDate

enum class ClinicalStatus {
    BELOW_RANGE,
    NORMAL,
    ABOVE_RANGE,
    PENDING,
    UNKNOWN
}

enum class TargetStatus {
    MEETS_TARGET,
    ABOVE_PERSONAL_TARGET,
    BELOW_PERSONAL_TARGET,
    NO_TARGET,
    PENDING
}

enum class Confidence {
    LOW,
    MODERATE,
    HIGH
}

data class ClinicalRange(
    val normalLow: Double? = null,
    val normalHigh: Double? = null,
    val description: String,
)

data class TargetValue(
    val metricType: MetricType,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val note: String = "",
)

data class MetricReading(
    val type: MetricType,
    val value: Double?,
    val recordedAt: LocalDate = LocalDate.now(),
    val note: String = "",
)

data class MetricAssessment(
    val reading: MetricReading,
    val clinicalStatus: ClinicalStatus,
    val targetStatus: TargetStatus,
    val clinicalMessage: String,
    val targetMessage: String,
)

data class InsightCard(
    val title: String,
    val explanation: String,
    val whyItMatters: String,
    val safeActions: List<String>,
    val clinicianTriggers: List<String>,
    val confidence: Confidence,
)

data class PlannedWorkout(
    val day: String,
    val type: WorkoutType,
    val minutes: Int,
    val intensity: String,
    val guidance: String,
)

enum class WorkoutType {
    ZONE_2,
    STRENGTH,
    MOBILITY,
    RECOVERY,
    REVIEW
}

data class WorkoutLog(
    val type: WorkoutType,
    val plannedMinutes: Int,
    val completedMinutes: Int,
    val rpe: Int,
    val painFlag: Boolean,
)

data class WeeklyPlan(
    val workouts: List<PlannedWorkout>,
    val focus: String,
    val safetyNote: String,
)

data class WeeklyReviewSummary(
    val adherencePercent: Int,
    val zone2Minutes: Int,
    val strengthSessions: Int,
    val latestWeight: Double?,
    val sevenDayWeightAverage: Double?,
    val latestRhr: Double?,
    val sevenDayRhrAverage: Double?,
    val latestBp: String?,
    val latestFastingGlucose: Double?,
    val improved: List<String>,
    val needsAttention: List<String>,
    val nextWeekFocus: String,
)
