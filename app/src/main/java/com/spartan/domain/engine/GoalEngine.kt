package com.spartan.domain.engine

import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Personal goals, validated against evidence-based safe rates and tracked from the user's own
 * readings. A goal that asks for too much is never refused rudely — the engine counter-offers
 * the nearest safe version (docs/COACH_DESIGN.md §4). Pure Kotlin, fully unit-tested.
 */
enum class GoalType { WEIGHT_LOSS, SLEEP_RECOVERY, STRESS_RESILIENCE, FITNESS, RECOVERY }

enum class GoalStatus { ACTIVE, PAUSED, COMPLETED, ABANDONED }

/** A recurring high-pressure window the user declared (e.g. Tue/Thu 11:00–12:00). */
data class PressureWindow(
    val id: String,
    /** Bit 0 = Monday … bit 6 = Sunday (ISO order, matches DayOfWeek.ordinal). */
    val daysOfWeekMask: Int,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val label: String = "",
)

data class Goal(
    val id: String,
    val type: GoalType,
    /**
     * WEIGHT_LOSS: pounds to lose. SLEEP_RECOVERY: percent improvement. STRESS_RESILIENCE,
     * FITNESS and RECOVERY: sessions/week.
     */
    val targetValue: Double,
    /**
     * Captured at creation from the user's data (start weight kg / baseline sleep perf % /
     * HRV ms / resting heart rate bpm / recovery %). Habit goals show it as context only.
     */
    val baselineValue: Double?,
    val startEpochDay: Long,
    val targetEpochDay: Long,
    val status: GoalStatus = GoalStatus.ACTIVE,
) {
    val totalWeeks: Double get() = ((targetEpochDay - startEpochDay).coerceAtLeast(1)) / 7.0
}

/** Outcome of validating a requested goal. */
sealed class GoalValidation {
    data class Ok(val goal: Goal) : GoalValidation()

    /** The ask exceeds a safe rate; [adjusted] is the nearest safe version, [why] explains it. */
    data class Adjusted(val adjusted: Goal, val why: String) : GoalValidation()

    data class Invalid(val why: String) : GoalValidation()
}

/** How an active goal bends the daily/weekly plan. Consumed by the coaching pipeline. */
data class GoalPlanModifiers(
    val extraZone2MinutesPerWeek: Int = 0,
    val protectStrengthSessions: Int = 0,
    val emphasizeWindDown: Boolean = false,
    val preWindowBreathwork: List<PressureWindow> = emptyList(),
)

data class GoalProgress(
    /** 0..1+ fraction of the target achieved so far (7-day-averaged, baseline-relative). */
    val fraction: Double,
    /** 0..1 fraction of the goal period elapsed. */
    val timeFraction: Double,
    val onTrack: Boolean,
    val summary: String,
    /**
     * Habit goals (STRESS_RESILIENCE, FITNESS, RECOVERY): the sessions done this week, uncapped —
     * [fraction] stops at 1, this does not, so a 5-session week against a target of 3 reads "5 of 3".
     * Null for outcome goals, which measure a reading rather than a count.
     */
    val sessionsThisWeek: Int? = null,
    /**
     * Habit goals: the body-signal context that [summary] ends with, without the session count
     * ("resting HR -1.5 bpm since your goal started"), for a surface that already states the count. Null for
     * outcome goals, and when the baseline or this week's readings are missing.
     */
    val contextNote: String? = null,
)

object GoalEngine {

    /** CDC gradual-loss guidance: sustainable loss is roughly 1–2 lb per week. */
    const val MAX_WEIGHT_LOSS_LB_PER_WEEK = 2.0

    /** Weight readings are stored in kg; goals speak pounds. */
    const val KG_TO_LB = 2.20462

    /** Recovery/sleep-performance gains beyond ~5%/week vs baseline are not realistic asks. */
    const val MAX_SLEEP_GAIN_PERCENT_PER_WEEK = 5.0

    /** Training sessions a week Spartan will plan: at least one rest day, always. */
    const val MAX_TRAINING_SESSIONS_PER_WEEK = 6

    /** Recovery sessions a week Spartan will plan: one a day is plenty. */
    const val MAX_RECOVERY_SESSIONS_PER_WEEK = 7

    fun validate(requested: Goal): GoalValidation {
        if (requested.targetValue <= 0.0) return GoalValidation.Invalid("Pick a target above zero.")
        if (requested.targetEpochDay <= requested.startEpochDay) {
            return GoalValidation.Invalid("Give the goal at least a week of runway.")
        }
        return when (requested.type) {
            GoalType.WEIGHT_LOSS -> {
                val rate = requested.targetValue / requested.totalWeeks
                if (rate <= MAX_WEIGHT_LOSS_LB_PER_WEEK) GoalValidation.Ok(requested)
                else {
                    val safeWeeks = ceil(requested.targetValue / MAX_WEIGHT_LOSS_LB_PER_WEEK).toInt()
                    GoalValidation.Adjusted(
                        adjusted = requested.copy(
                            targetEpochDay = requested.startEpochDay + safeWeeks * 7L,
                        ),
                        why = "Sustainable loss is about 1–2 lb per week, so " +
                            "${requested.targetValue.toInt()} lb fits better in $safeWeeks weeks. " +
                            "Faster than that tends to rebound.",
                    )
                }
            }
            GoalType.SLEEP_RECOVERY -> {
                val rate = requested.targetValue / requested.totalWeeks
                if (rate <= MAX_SLEEP_GAIN_PERCENT_PER_WEEK) GoalValidation.Ok(requested)
                else {
                    val safeWeeks = ceil(requested.targetValue / MAX_SLEEP_GAIN_PERCENT_PER_WEEK).toInt()
                    GoalValidation.Adjusted(
                        adjusted = requested.copy(
                            targetEpochDay = requested.startEpochDay + safeWeeks * 7L,
                        ),
                        why = "Sleep gains compound gradually — aim for " +
                            "${requested.targetValue.toInt()}% over $safeWeeks weeks of " +
                            "consistent bed and wake times.",
                    )
                }
            }
            GoalType.STRESS_RESILIENCE -> GoalValidation.Ok(requested)
            GoalType.FITNESS -> capWeeklySessions(
                requested, MAX_TRAINING_SESSIONS_PER_WEEK,
                why = "Rest days are where fitness is built — Spartan plans at most " +
                    "$MAX_TRAINING_SESSIONS_PER_WEEK sessions a week.",
            )
            GoalType.RECOVERY -> capWeeklySessions(
                requested, MAX_RECOVERY_SESSIONS_PER_WEEK,
                why = "One recovery session a day is plenty — Spartan plans at most " +
                    "$MAX_RECOVERY_SESSIONS_PER_WEEK a week.",
            )
        }
    }

    /** Habit goals ask for sessions a week; above [max] the nearest safe version is [max] itself. */
    private fun capWeeklySessions(requested: Goal, max: Int, why: String): GoalValidation =
        if (requested.targetValue <= max) GoalValidation.Ok(requested)
        else GoalValidation.Adjusted(adjusted = requested.copy(targetValue = max.toDouble()), why = why)

    /** Plan emphasis for the active goal; the recovery-gated daily engine still owns the floor. */
    fun planModifiers(goal: Goal?, windows: List<PressureWindow>): GoalPlanModifiers = when (goal?.type) {
        null -> GoalPlanModifiers(preWindowBreathwork = windows)
        GoalType.WEIGHT_LOSS -> GoalPlanModifiers(
            extraZone2MinutesPerWeek = 60,
            protectStrengthSessions = 2,
            preWindowBreathwork = windows,
        )
        GoalType.SLEEP_RECOVERY -> GoalPlanModifiers(
            emphasizeWindDown = true,
            preWindowBreathwork = windows,
        )
        GoalType.STRESS_RESILIENCE -> GoalPlanModifiers(
            emphasizeWindDown = true,
            preWindowBreathwork = windows,
        )
        GoalType.FITNESS -> GoalPlanModifiers(
            extraZone2MinutesPerWeek = 30,
            protectStrengthSessions = 2,
            preWindowBreathwork = windows,
        )
        GoalType.RECOVERY -> GoalPlanModifiers(
            emphasizeWindDown = true,
            preWindowBreathwork = windows,
        )
    }

    /**
     * Progress from the user's own readings. [readings] are raw entries; the relevant metric is
     * trailing-7-day averaged so a single odd day never whipsaws the goal card. Weight readings
     * are stored in kg and converted here, because goals speak pounds.
     * [breathworkThisWeek], [trainingSessionsThisWeek] and [recoverySessionsThisWeek] feed the
     * STRESS_RESILIENCE, FITNESS and RECOVERY habit goals (sessions completed this week).
     */
    fun progress(
        goal: Goal,
        readings: List<MetricReading>,
        todayEpochDay: Long,
        breathworkThisWeek: Int = 0,
        trainingSessionsThisWeek: Int = 0,
        recoverySessionsThisWeek: Int = 0,
    ): GoalProgress {
        val timeFraction = ((todayEpochDay - goal.startEpochDay).toDouble() /
            (goal.targetEpochDay - goal.startEpochDay).coerceAtLeast(1)).coerceIn(0.0, 1.0)
        val remainingWeeks = ceil((goal.targetEpochDay - todayEpochDay) / 7.0).toInt().coerceAtLeast(0)

        // Stress resilience, fitness and recovery are habit goals: showing up this week IS the
        // progress; the body signal (HRV, resting HR, recovery) rides along as context, never as
        // a pass/fail judgment.
        val sessionsThisWeek = when (goal.type) {
            GoalType.STRESS_RESILIENCE -> breathworkThisWeek
            GoalType.FITNESS -> trainingSessionsThisWeek
            GoalType.RECOVERY -> recoverySessionsThisWeek
            GoalType.WEIGHT_LOSS, GoalType.SLEEP_RECOVERY -> null
        }
        if (sessionsThisWeek != null) {
            val weeklyTarget = goal.targetValue.coerceAtLeast(1.0)
            val fraction = (sessionsThisWeek / weeklyTarget).coerceIn(0.0, 1.0)
            val context = contextNote(goal, readings, todayEpochDay)
            return GoalProgress(
                fraction = fraction, timeFraction = timeFraction,
                onTrack = fraction >= 0.5 || timeFraction < 0.2,
                summary = "$sessionsThisWeek of ${weeklyTarget.toInt()} ${habitNoun(goal.type)}${if (weeklyTarget.toInt() == 1) "" else "s"} this week" +
                    (context?.let { " · $it" } ?: ""),
                sessionsThisWeek = sessionsThisWeek,
                contextNote = context,
            )
        }

        val baseline = goal.baselineValue
        val current = trailingAverage(readings, metricFor(goal.type), todayEpochDay)
        if (baseline == null || current == null) {
            return GoalProgress(
                fraction = 0.0, timeFraction = timeFraction, onTrack = timeFraction < 0.25,
                summary = "Log ${metricFor(goal.type).label.lowercase()} entries to track this goal.",
            )
        }
        val achieved = when (goal.type) {
            GoalType.WEIGHT_LOSS -> (baseline - current) * KG_TO_LB   // readings in kg → lb lost
            else ->                                                    // % gained vs baseline
                if (baseline == 0.0) 0.0 else (current - baseline) / baseline * 100.0
        }
        val fraction = (achieved / goal.targetValue).coerceAtLeast(0.0)
        // On-track = progress keeping pace with time, with a small grace margin early on.
        val onTrack = fraction + 0.15 >= timeFraction
        val summary = when (goal.type) {
            // Report the direction the data actually moved: abs() here told a user who had gained
            // weight that they had lost it. A wrong-signed number is worse than a blunt one.
            GoalType.WEIGHT_LOSS -> if (achieved < 0) {
                "${roundTo1(-achieved)} lb up since your goal started, $remainingWeeks wk left"
            } else {
                "${roundTo1(achieved)} of ${goal.targetValue.toInt()} lb down, $remainingWeeks wk left"
            }
            else -> if (achieved < 0) {
                "${roundTo1(-achieved)}% lower since your goal started, $remainingWeeks wk left"
            } else {
                "${roundTo1(achieved)}% of ${goal.targetValue.toInt()}% gained, $remainingWeeks wk left"
            }
        }
        return GoalProgress(fraction = fraction, timeFraction = timeFraction, onTrack = onTrack, summary = summary)
    }

    /** The reading type that measures each goal (habit goals: the context metric shown beside it). */
    fun metricFor(type: GoalType): MetricType = when (type) {
        GoalType.WEIGHT_LOSS -> MetricType.WEIGHT
        GoalType.SLEEP_RECOVERY -> MetricType.SLEEP_PERFORMANCE
        GoalType.STRESS_RESILIENCE -> MetricType.HRV_RMSSD
        GoalType.FITNESS -> MetricType.RESTING_HEART_RATE
        GoalType.RECOVERY -> MetricType.RECOVERY_SCORE
    }

    private fun habitNoun(type: GoalType): String = when (type) {
        GoalType.FITNESS -> "training session"
        GoalType.RECOVERY -> "recovery session"
        else -> "calm session"
    }

    /**
     * "resting HR -1.5 bpm since your goal started" — context beside a habit goal, against the value
     * captured when the goal started (never called "baseline" on screen, where "your usual" is the
     * personal range). Null without both numbers.
     */
    private fun contextNote(goal: Goal, readings: List<MetricReading>, todayEpochDay: Long): String? {
        val baseline = goal.baselineValue ?: return null
        val now = trailingAverage(readings, metricFor(goal.type), todayEpochDay) ?: return null
        val d = now - baseline
        val (label, unit) = when (goal.type) {
            GoalType.FITNESS -> "resting HR" to " bpm"
            GoalType.RECOVERY -> "recovery" to "%"
            else -> "HRV" to " ms"
        }
        return "$label ${if (d >= 0) "+" else ""}${roundTo1(d)}$unit since your goal started"
    }

    /**
     * Baseline captured at goal creation: trailing-14-day average of the goal metric, in the
     * metric's native unit (kg for weight — progress() converts to pounds when comparing).
     */
    fun baselineFor(type: GoalType, readings: List<MetricReading>, todayEpochDay: Long): Double? =
        trailingAverage(readings, metricFor(type), todayEpochDay, windowDays = 14)

    private fun trailingAverage(
        readings: List<MetricReading>,
        type: MetricType,
        todayEpochDay: Long,
        windowDays: Int = 7,
    ): Double? = readings
        .filter {
            it.type == type && it.value != null &&
                it.recordedAt.toEpochDay() in (todayEpochDay - windowDays + 1)..todayEpochDay
        }
        .mapNotNull { it.value }
        .takeIf { it.isNotEmpty() }
        ?.average()

    private fun roundTo1(v: Double): String = (Math.round(v * 10) / 10.0).toString()
}
