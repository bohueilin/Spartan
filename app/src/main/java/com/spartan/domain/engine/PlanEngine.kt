package com.spartan.domain.engine

import com.spartan.domain.model.PlannedWorkout
import com.spartan.domain.model.WeeklyPlan
import com.spartan.domain.model.WorkoutLog
import com.spartan.domain.model.WorkoutType

class PlanEngine(
    private val safetyEngine: SafetyEngine = SafetyEngine(),
) {
    fun defaultPlan(previousLogs: List<WorkoutLog> = emptyList()): WeeklyPlan {
        val painReported = previousLogs.any { it.painFlag }
        val highRpeReported = previousLogs.any { it.rpe >= 8 }
        val adherence = adherencePercent(previousLogs)
        val needsDeload = painReported || highRpeReported || adherence < 60
        val zone2Minutes = if (needsDeload) 30 else 35
        val strengthMinutes = if (painReported || highRpeReported) 25 else 35
        val focus = when {
            painReported -> "Keep training gentle and pain-free while you recover."
            highRpeReported -> "Hold intensity steady and prioritize recovery before progressing."
            adherence < 60 -> "Repeat a manageable week and build consistency."
            adherence >= 85 -> "Add only a small progression if recovery feels good."
            else -> "Hold steady and confirm improvement with trends."
        }

        val plan = WeeklyPlan(
            workouts = listOf(
                PlannedWorkout("Mon", WorkoutType.ZONE_2, zone2Minutes, "Easy conversational pace", "Stop or scale down if pain appears."),
                PlannedWorkout("Tue", WorkoutType.STRENGTH, strengthMinutes, "Moderate, clean form", "Leave 2 to 3 reps in reserve."),
                PlannedWorkout("Wed", WorkoutType.ZONE_2, zone2Minutes, "Easy conversational pace", "Keep it repeatable."),
                PlannedWorkout("Thu", WorkoutType.MOBILITY, 20, "Gentle", "Focus on range of motion and breathing."),
                PlannedWorkout("Fri", WorkoutType.STRENGTH, strengthMinutes, "Moderate, clean form", "Avoid painful movements."),
                PlannedWorkout("Sat", WorkoutType.ZONE_2, zone2Minutes, "Easy conversational pace", "Optional outdoor session."),
                PlannedWorkout("Sun AM", WorkoutType.RECOVERY, 20, "Easy", "Walk, stretch, or rest based on how you feel."),
                PlannedWorkout("Sun PM", WorkoutType.REVIEW, 15, "Reflective", "Review trends and set next week focus."),
            ),
            focus = focus,
            safetyNote = "Pain means stop, scale down, or choose recovery. Seek clinical guidance for concerning symptoms.",
        )
        safetyEngine.sanitize(plan.focus)
        safetyEngine.sanitize(plan.safetyNote)
        plan.workouts.forEach {
            safetyEngine.sanitize("${it.day} ${it.type} ${it.intensity} ${it.guidance}")
        }
        return plan
    }

    fun adherencePercent(logs: List<WorkoutLog>): Int {
        if (logs.isEmpty()) return 0
        val planned = logs.sumOf { it.plannedMinutes }.coerceAtLeast(1)
        val completed = logs.sumOf { it.completedMinutes.coerceAtMost(it.plannedMinutes) }
        return ((completed.toDouble() / planned.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    }
}
