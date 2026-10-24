package com.spartan.domain.engine

/**
 * A calm read of a run of active days (research §4.4). [currentRun] and [longestRun] count active
 * days only: a grace day keeps a run alive but never adds to it. [backOnTrack] is true when today
 * is active after an inactive yesterday and an earlier active day — the moment to say "back on
 * track", never "streak lost". [graceUsed] is true when the current run absorbed a missed day.
 */
data class StreakSummary(
    val currentRun: Int,
    val longestRun: Int,
    val backOnTrack: Boolean,
    val graceUsed: Boolean,
)

/**
 * Forgiving streaks, derived from immutable day records rather than a mutable counter, so a miss
 * never deletes history and a run never resets to a big 0 in the UI.
 */
object Streaks {

    /**
     * Summarizes [flags], ordered oldest → today (true = at least one completed activity). A run
     * tolerates up to [graceDaysPerWeek] missed days in any 7-day window; one more miss ends it.
     * Today is still open, so an inactive today is neither a miss nor the end of a run.
     */
    fun summarize(flags: List<Boolean>, graceDaysPerWeek: Int = 1): StreakSummary {
        var run = 0
        var longest = 0
        val graceDays = mutableListOf<Int>() // indices of the misses the current run absorbed
        for ((day, active) in flags.withIndex()) {
            when {
                active -> {
                    run++
                    longest = maxOf(longest, run)
                }
                day == flags.lastIndex -> Unit
                run > 0 && graceDays.count { it > day - 7 } < graceDaysPerWeek -> graceDays.add(day)
                else -> {
                    run = 0
                    graceDays.clear()
                }
            }
        }
        val today = flags.lastOrNull() == true
        val yesterday = flags.getOrNull(flags.lastIndex - 1) == true
        return StreakSummary(
            currentRun = run,
            longestRun = longest,
            backOnTrack = today && !yesterday && flags.dropLast(2).any { it },
            graceUsed = graceDays.isNotEmpty(),
        )
    }
}
