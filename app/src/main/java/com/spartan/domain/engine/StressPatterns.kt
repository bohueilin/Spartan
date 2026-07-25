package com.spartan.domain.engine

import com.spartan.domain.model.WhoopSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs

/**
 * Finds honest stress patterns in the user's own imported data and turns declared high-pressure
 * windows into pre-emptive calm. The WHOOP CSV export has no intraday stress stream, so this
 * engine never pretends to see one (docs/COACH_DESIGN.md §5):
 *
 *  1. Weekday effect — which weekday's *following morning* runs below the personal average
 *     (recovery/HRV), gated on sample size and effect size before anything is shown.
 *  2. Pressure windows — user-declared recurring blocks (Tue/Thu 11:00–12:00); Spartan schedules
 *     a short breathwork five minutes before each one on matching days.
 */
data class WeekdayEffect(
    /** The weekday whose FOLLOWING morning runs low (the stressor day, e.g. Tuesday). */
    val stressorDay: DayOfWeek,
    /** Mean next-morning recovery delta vs the personal mean (negative = worse). */
    val recoveryDelta: Double,
    /** Number of weeks of evidence behind the effect. */
    val sampleWeeks: Int,
    val insight: String,
)

object StressPatterns {

    /** Minimum observations of a weekday before an effect is trusted. */
    const val MIN_SAMPLES = 3

    /** Minimum recovery-point deficit before an effect is worth surfacing. */
    const val MIN_EFFECT_RECOVERY_POINTS = 8.0

    /** Lead time for the pre-window breathwork nudge. */
    const val PRE_WINDOW_LEAD_MINUTES = 5

    /**
     * Weekday effects from imported daily snapshots: for each weekday D, compare mean recovery on
     * the morning AFTER D (i.e. dates whose previous day is D) against the overall mean. Sorted
     * worst-first; empty when nothing clears the sample/effect gates.
     */
    fun weekdayEffects(snapshots: List<WhoopSnapshot>): List<WeekdayEffect> {
        val withRecovery = snapshots.filter { it.recoveryScore != null }
        if (withRecovery.size < MIN_SAMPLES * 2) return emptyList()
        val overallMean = withRecovery.mapNotNull { it.recoveryScore }.average()

        return DayOfWeek.entries.mapNotNull { stressor ->
            // Mornings that follow the stressor day.
            val mornings = withRecovery.filter {
                LocalDate.ofEpochDay(it.dateEpochDay).dayOfWeek == stressor.plus(1)
            }
            if (mornings.size < MIN_SAMPLES) return@mapNotNull null
            val delta = mornings.mapNotNull { it.recoveryScore }.average() - overallMean
            if (delta > -MIN_EFFECT_RECOVERY_POINTS) return@mapNotNull null
            WeekdayEffect(
                stressorDay = stressor,
                recoveryDelta = delta,
                sampleWeeks = mornings.size,
                insight = "Mornings after ${dayName(stressor)}s run about ${abs(delta).toInt()} " +
                    "recovery points below your average (${mornings.size} weeks of data). A " +
                    "${dayName(stressor)} evening wind-down or an easier ${dayName(stressor)} " +
                    "could pay off the next day.",
            )
        }.sortedBy { it.recoveryDelta }
    }

    /** The pressure windows that occur on [date], soonest first. */
    fun windowsForDay(windows: List<PressureWindow>, date: LocalDate): List<PressureWindow> =
        windows.filter { (it.daysOfWeekMask shr date.dayOfWeek.ordinal) and 1 == 1 }
            .sortedBy { it.startMinuteOfDay }

    /** Minute-of-day the pre-window breathwork nudge should fire for [window]. */
    fun nudgeMinuteFor(window: PressureWindow): Int =
        (window.startMinuteOfDay - PRE_WINDOW_LEAD_MINUTES).coerceAtLeast(0)

    /** Human summary of a window's schedule, e.g. "Tue, Thu 11:00–12:00". */
    fun describeWindow(window: PressureWindow): String {
        val days = DayOfWeek.entries
            .filter { (window.daysOfWeekMask shr it.ordinal) and 1 == 1 }
            .joinToString(", ") { dayName(it).take(3) }
        return "$days ${clock(window.startMinuteOfDay)}–${clock(window.endMinuteOfDay)}"
    }

    private fun clock(minuteOfDay: Int): String =
        "%d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    private fun dayName(day: DayOfWeek): String =
        day.name.lowercase().replaceFirstChar { it.uppercase() }
}
