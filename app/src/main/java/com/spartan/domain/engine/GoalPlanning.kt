package com.spartan.domain.engine

import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.WorkoutLog
import com.spartan.domain.model.WorkoutType
import kotlin.math.ceil
import kotlin.math.roundToInt

/*
 * The goal wizard's rules: one primary goal from four pre-filled presets, a SMART target anchored to
 * the user's own recent average (never an ideal), the constraints the plan must respect, a preview
 * of week one only, and the weekly keep / change / pause check-in. Pure Kotlin, unit-tested.
 */

/** The four presets the wizard offers — one primary goal at a time. */
enum class GoalPreset(val goalType: GoalType, val label: String) {
    SLEEP_BETTER(GoalType.SLEEP_RECOVERY, "Sleep better"),
    GET_FITTER(GoalType.FITNESS, "Get fitter"),
    FEEL_STEADIER(GoalType.STRESS_RESILIENCE, "Feel steadier"),
    RECOVER_WELL(GoalType.RECOVERY, "Recover well");

    companion object {
        /** The preset a stored goal came from, or null for goal types with no preset (weight). */
        fun forGoalType(type: GoalType): GoalPreset? = entries.firstOrNull { it.goalType == type }
    }
}

/** Things the user said they won't do. The plan works around them rather than arguing. */
enum class AvoidOption(val label: String) {
    STRENGTH_TRAINING("Strength training"),
    BREATHWORK("Breathing exercises"),
    MORNING_SESSIONS("Morning workouts"),
}

/**
 * The constraints from the wizard. All optional and stored on-device only (DataStore, excluded from
 * backup). [limitsNote] is free text about injuries or limits: Spartan shows it back on training
 * activities as a reminder but never interprets it.
 */
data class PlanConstraints(
    /** Minutes a day the user has for planned activities (e.g. 10, 20, 30, 45, 60); null = no limit given. */
    val minutesPerDay: Int? = null,
    val limitsNote: String = "",
    /** Usual lights-out, minute of day (0..1439); null = not given. */
    val bedtimeMinute: Int? = null,
    /** Usual wake time, minute of day (0..1439); null = not given. */
    val wakeMinute: Int? = null,
    val avoid: Set<AvoidOption> = emptySet(),
) {
    val isEmpty: Boolean
        get() = minutesPerDay == null && limitsNote.isBlank() && bedtimeMinute == null && wakeMinute == null && avoid.isEmpty()
}

/** The user's own recent averages the presets anchor to (null = not enough data yet). */
data class GoalBaselines(
    /** Mean sleep performance over the last 14 days, 0..100. */
    val sleepPerformancePercent: Double? = null,
    /** Mean hours asleep over the last 14 days. */
    val sleepHours: Double? = null,
    /** Zone 2 + strength sessions per week, averaged over the last 28 days. */
    val trainingSessionsPerWeek: Double? = null,
    /** Breathing sessions per week, averaged over the last 28 days. */
    val calmSessionsPerWeek: Double? = null,
    /** Mobility / recovery sessions per week, averaged over the last 28 days. */
    val recoverySessionsPerWeek: Double? = null,
)

/** A pre-filled, SMART version of a preset: never a blank form. */
data class GoalDraft(
    val preset: GoalPreset,
    /** The value saved as [Goal.targetValue], in that goal type's units. */
    val targetValue: Double,
    val weeks: Int,
    /** The recent average the target is anchored to, in display units; null while Spartan is still learning. */
    val baselineDisplayValue: Double?,
    /** Where the target lands, in display units (e.g. 86.0 = % sleep performance, 3.0 = sessions a week). */
    val targetDisplayValue: Double,
    /** Unit for the display values: "%" or "sessions a week". */
    val displayUnit: String,
    /** One SMART sentence: action + number + frequency + review date. */
    val smartSentence: String,
)

/** One line of the week-one preview: "Wind-down routine · 5× this week · 20 min". */
data class PreviewItem(val title: String, val timesThisWeek: Int, val minutes: Int)

/** Week one only — "This week: about 3 actions a day, ~20 min a day." */
data class WeekPreview(
    val items: List<PreviewItem>,
    val actionsPerDay: Int,
    val minutesPerDay: Int,
    val summary: String,
)

/** The weekly re-plan: kept, changed, or paused. A missed goal is a goal-design problem, never a failure. */
enum class WeeklyDecision { KEEP, CHANGE, PAUSE }

object GoalPlanning {
    /** Sleep baselines average the last two weeks, today included. */
    private const val SLEEP_WINDOW_DAYS = 14

    /** Session baselines count the last four weeks, today included. */
    private const val HABIT_WINDOW_DAYS = 28
    private const val HABIT_WINDOW_WEEKS = 4.0

    /** The sleep-performance anchor while Spartan is still learning the user's usual. */
    private const val DEFAULT_SLEEP_PERFORMANCE = 75.0

    /** WHOOP's own sleep-goal choices, as % of the sleep you need. */
    private val SLEEP_GOAL_CHOICES = listOf(70.0, 85.0, 100.0)

    /** The smallest sleep goal worth setting, in % gained over the baseline. */
    private const val MIN_SLEEP_GAIN_PERCENT = 2.0

    /** The gain saved when the usual is already 100%: just above zero, so the goal is valid. */
    private const val KEEP_UP_SLEEP_GAIN_PERCENT = 0.1

    private const val MIN_WEEKS = 2
    private const val MAX_WEEKS = 12
    private const val MAX_CALM_SESSIONS_PER_WEEK = 7
    private const val SESSIONS_UNIT = "sessions a week"
    private const val MINUTES_PER_DAY = 24 * 60

    /** Every playbook action by key, so preview lines carry exactly the titles the plan uses. */
    private val playbook: Map<String, SuggestedAction> by lazy {
        MetricType.entries.flatMap { ActionPlaybook.forMetric(it) }.associateBy { it.key }
    }

    /**
     * The user's own recent averages for anchoring presets. [calmSessionsLast28Days] comes from the plan history.
     * A field is null while its window holds no data at all: a brand-new user is "still learning", not "0".
     * Once any session is logged in the last 28 days, a zero for the other kinds is real.
     */
    fun baselines(
        readings: List<MetricReading>,
        logs: List<WorkoutLog>,
        calmSessionsLast28Days: Int,
        todayEpochDay: Long,
    ): GoalBaselines {
        val habitDays = window(todayEpochDay, HABIT_WINDOW_DAYS)
        val recentLogs = logs.filter { it.completedAt.toEpochDay() in habitDays }
        fun perWeek(vararg types: WorkoutType): Double? =
            if (recentLogs.isEmpty()) null else recentLogs.count { it.type in types } / HABIT_WINDOW_WEEKS
        return GoalBaselines(
            sleepPerformancePercent = recentMean(readings, MetricType.SLEEP_PERFORMANCE, todayEpochDay),
            sleepHours = recentMean(readings, MetricType.SLEEP_DURATION, todayEpochDay),
            trainingSessionsPerWeek = perWeek(WorkoutType.ZONE_2, WorkoutType.STRENGTH),
            calmSessionsPerWeek = calmSessionsLast28Days.takeIf { it > 0 }?.let { it / HABIT_WINDOW_WEEKS },
            recoverySessionsPerWeek = perWeek(WorkoutType.MOBILITY),
        )
    }

    /**
     * The pre-filled draft for [preset], anchored to [baselines] (a sensible, labelled default while
     * a baseline is missing). [targetDisplayValue] / [weeks] override the suggestion when the user
     * nudges them; the result is clamped to safe, realistic values for that goal type, so every
     * draft passes [GoalEngine.validate] as-is.
     */
    fun draft(
        preset: GoalPreset,
        baselines: GoalBaselines,
        targetDisplayValue: Double? = null,
        weeks: Int? = null,
    ): GoalDraft {
        val target = targetDisplayValue?.takeIf { it.isFinite() }
        return when (preset) {
            GoalPreset.SLEEP_BETTER -> sleepDraft(baselines.sleepPerformancePercent, target, weeks)
            GoalPreset.GET_FITTER -> sessionsDraft(
                preset = preset, usual = baselines.trainingSessionsPerWeek, fallbackUsual = 2, step = 1,
                minSuggested = 2, maxSessions = GoalEngine.MAX_TRAINING_SESSIONS_PER_WEEK, defaultWeeks = 4,
                targetOverride = target, weeksOverride = weeks,
            ) { n, usual, w ->
                "Do $n Zone 2 or strength ${if (n == 1) "session" else "sessions"} a week" +
                    "${versusUsual(n, usual)} and review in $w weeks."
            }
            GoalPreset.FEEL_STEADIER -> sessionsDraft(
                preset = preset, usual = baselines.calmSessionsPerWeek, fallbackUsual = 1, step = 2,
                minSuggested = 3, maxSessions = MAX_CALM_SESSIONS_PER_WEEK, defaultWeeks = 3,
                targetOverride = target, weeksOverride = weeks,
            ) { n, _, w -> "Do a 5-minute breathing session ${timesAWeek(n)} and review in $w weeks." }
            GoalPreset.RECOVER_WELL -> sessionsDraft(
                preset = preset, usual = baselines.recoverySessionsPerWeek, fallbackUsual = 1, step = 2,
                minSuggested = 3, maxSessions = GoalEngine.MAX_RECOVERY_SESSIONS_PER_WEEK, defaultWeeks = 3,
                targetOverride = target, weeksOverride = weeks,
            ) { n, _, w -> "Do a 10-minute mobility or wind-down session ${timesAWeek(n)} and review in $w weeks." }
        }
    }

    /**
     * True when [constraints] rule out the very sessions [preset]'s goal counts: Feel steadier
     * counts breathing sessions, so it cannot progress while breathing exercises are off.
     */
    fun conflicts(preset: GoalPreset, constraints: PlanConstraints): Boolean =
        preset == GoalPreset.FEEL_STEADIER && AvoidOption.BREATHWORK in constraints.avoid

    /**
     * Week one of [draft] under [constraints] (time budget and won't-do options respected). The
     * bedtime line is the lights-out [SleepPlanner] will plan, in the device's 12/24-hour style.
     */
    fun preview(draft: GoalDraft, constraints: PlanConstraints, use24HourClock: Boolean = false): WeekPreview {
        val n = draft.targetDisplayValue.roundToInt().coerceAtLeast(1)
        val avoid = constraints.avoid
        val planned = when (draft.preset) {
            GoalPreset.SLEEP_BETTER -> listOf(
                PreviewItem(
                    title = SleepPlanner.tonight(null, null, null, userBedtimeMinute = constraints.bedtimeMinute)
                        ?.let { "In bed by ${clock(it.lightsOutMinute, use24HourClock)}" }
                        ?: "In bed at the same time each night",
                    timesThisWeek = 5,
                    minutes = 1,
                ),
                item("wind-down", 5),
                item("morning-light", 3),
                item("caffeine-cutoff", 5),
            )
            GoalPreset.GET_FITTER -> if (AvoidOption.STRENGTH_TRAINING in avoid) {
                listOf(item("zone2", n), item("mobility", 2))
            } else {
                listOf(item("zone2", (n + 1) / 2), item("strength", n / 2), item("mobility", 2))
            }
            GoalPreset.FEEL_STEADIER -> listOf(
                item(if (AvoidOption.BREATHWORK in avoid) "easy-walk" else "breathing", n),
                item("easy-walk", 3),
                item("wind-down", 3),
            )
            GoalPreset.RECOVER_WELL -> listOf(item("mobility", n), item("wind-down", 4), item("easy-walk", 3))
        }
        // A walk standing in for breathing joins the other walks as one line; empty lines drop out.
        val items = planned
            .filter { it.timesThisWeek > 0 }
            .groupBy { it.title }
            .map { (title, same) -> PreviewItem(title, same.sumOf { it.timesThisWeek }, same.first().minutes) }

        // Over the time budget: drop from the end, where the least important actions sit. The first stays.
        val budget = constraints.minutesPerDay?.takeIf { it > 0 }
        var kept = items
        while (budget != null && kept.size > 1 && minutesPerDay(kept) > budget) kept = kept.dropLast(1)
        val actions = actionsPerDay(kept)
        val minutes = minutesPerDay(kept)
        val trimNote = if (budget == null || kept.size == items.size) {
            ""
        } else if (minutes <= budget) {
            " Trimmed to fit your ${minutesLabel(budget)} a day."
        } else {
            " Trimmed to the main action — it still takes more than your ${minutesLabel(budget)} a day, " +
                "so a smaller target may fit better."
        }
        val conflictNote = if (conflicts(draft.preset, constraints)) {
            " This goal counts breathing sessions, which you'd rather skip — the walks won't count " +
                "toward it, so a different goal may fit better."
        } else {
            ""
        }
        return WeekPreview(
            items = kept,
            actionsPerDay = actions,
            minutesPerDay = minutes,
            summary = "This week: about $actions ${if (actions == 1) "action" else "actions"} a day, " +
                "~$minutes min a day. Review in a week.$trimNote$conflictNote",
        )
    }

    /**
     * The absolute sleep-performance % a sleep goal aims for (its baseline plus the saved relative
     * gain, at most 100), so the goal reads as the "85%" the wizard offered rather than "+13%".
     * Null for other goal types, or while the goal has no baseline to anchor to.
     */
    fun sleepTargetPercent(goal: Goal): Double? {
        if (goal.type != GoalType.SLEEP_RECOVERY) return null
        val baseline = goal.baselineValue?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        return minOf(100.0, baseline * (1 + goal.targetValue / 100.0))
    }

    /** True when an active goal has run a week since it started or since the last weekly check-in. */
    fun isWeeklyReviewDue(goal: Goal?, lastReviewEpochDay: Long?, todayEpochDay: Long): Boolean {
        if (goal == null || goal.status != GoalStatus.ACTIVE) return false
        // A check-in left over from an earlier goal predates this one, so the goal's own start wins.
        val since = maxOf(goal.startEpochDay, lastReviewEpochDay ?: goal.startEpochDay)
        return todayEpochDay - since >= 7
    }

    /**
     * Sleep better: the next of WHOOP's sleep-goal choices above the usual, saved as % gained over
     * it. No hour count anywhere — the need is the user's own, never a fixed eight hours.
     */
    private fun sleepDraft(usual: Double?, targetOverride: Double?, weeksOverride: Int?): GoalDraft {
        val known = usual?.takeIf { it.isFinite() }?.coerceIn(1.0, 100.0)
        val baseline = known ?: DEFAULT_SLEEP_PERFORMANCE
        val target = targetOverride?.coerceIn(minOf(baseline + 1, 100.0), 100.0)
            ?: SLEEP_GOAL_CHOICES.firstOrNull { it > baseline }
            ?: 100.0
        // The floor keeps a sliver of a gain meaningful but never asks for more than 100%; at a usual
        // of 100% there is no headroom, so a token gain keeps the goal valid while the target stays 100%.
        val headroom = (100.0 - baseline) / baseline * 100.0
        val floor = minOf(MIN_SLEEP_GAIN_PERCENT, headroom).takeIf { it > 0.0 } ?: KEEP_UP_SLEEP_GAIN_PERCENT
        val gain = maxOf(floor, (target - baseline) / baseline * 100.0)
        val safeWeeks = safeSleepWeeks(gain)
        val weeks = maxOf(weeksOverride?.coerceIn(MIN_WEEKS, MAX_WEEKS) ?: safeWeeks, safeWeeks)
        val clause = when {
            known == null -> ","
            target.roundToInt() > known.roundToInt() -> " — up from your usual ${known.roundToInt()}% —"
            else -> " — keeping up your usual ${known.roundToInt()}% —"
        }
        return GoalDraft(
            preset = GoalPreset.SLEEP_BETTER,
            targetValue = gain,
            weeks = weeks,
            baselineDisplayValue = known,
            targetDisplayValue = target,
            displayUnit = "%",
            smartSentence = "Get ${target.roundToInt()}% of the sleep you need on most nights$clause " +
                "and review in $weeks weeks.",
        )
    }

    /** Fewest weeks (at least [MIN_WEEKS]) that keep [gainPercent] within the safe weekly rate. */
    private fun safeSleepWeeks(gainPercent: Double): Int {
        var weeks = maxOf(MIN_WEEKS, ceil(gainPercent / GoalEngine.MAX_SLEEP_GAIN_PERCENT_PER_WEEK).toInt())
        // GoalEngine.validate divides rather than multiplies; check the same way so a rounding edge never disagrees.
        while (gainPercent / weeks > GoalEngine.MAX_SLEEP_GAIN_PERCENT_PER_WEEK) weeks++
        return weeks
    }

    /** A sessions-a-week habit draft: a small step up from the usual, never past [maxSessions]. */
    private fun sessionsDraft(
        preset: GoalPreset,
        usual: Double?,
        fallbackUsual: Int,
        step: Int,
        minSuggested: Int,
        maxSessions: Int,
        defaultWeeks: Int,
        targetOverride: Double?,
        weeksOverride: Int?,
        sentence: (sessions: Int, usual: Int?, weeks: Int) -> String,
    ): GoalDraft {
        val base = usual?.takeIf { it.isFinite() }?.roundToInt()?.coerceAtLeast(0)
        val target = targetOverride?.coerceIn(1.0, maxSessions.toDouble())?.roundToInt()
            ?: ((base ?: fallbackUsual).coerceAtMost(maxSessions) + step).coerceIn(minSuggested, maxSessions)
        val weeks = weeksOverride?.coerceIn(MIN_WEEKS, MAX_WEEKS) ?: defaultWeeks
        return GoalDraft(
            preset = preset,
            targetValue = target.toDouble(),
            weeks = weeks,
            baselineDisplayValue = base?.toDouble(),
            targetDisplayValue = target.toDouble(),
            displayUnit = SESSIONS_UNIT,
            smartSentence = sentence(target, base, weeks),
        )
    }

    /** " — one more than your usual 2 —", or nothing while the usual is still unknown. */
    private fun versusUsual(target: Int, usual: Int?): String = when {
        usual == null -> ""
        usual == 0 -> " — a gentle start —"
        target > usual -> " — ${inWords(target - usual)} more than your usual $usual —"
        target < usual -> " — ${inWords(usual - target)} fewer than your usual $usual, leaving room for rest —"
        else -> " — the same as your usual $usual —"
    }

    private fun inWords(n: Int): String =
        listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")
            .getOrElse(n) { n.toString() }

    private fun timesAWeek(n: Int): String = when (n) {
        1 -> "once a week"
        2 -> "twice a week"
        else -> "$n times a week"
    }

    private fun minutesLabel(minutes: Int): String = if (minutes == 1) "1 minute" else "$minutes minutes"

    private fun item(key: String, times: Int): PreviewItem {
        val action = playbook.getValue(key)
        return PreviewItem(title = action.title, timesThisWeek = times, minutes = action.minutes)
    }

    /** Average planned minutes a day, to the nearest five ("~20 min" reads as a plan), at least five. */
    private fun minutesPerDay(items: List<PreviewItem>): Int =
        ((items.sumOf { it.timesThisWeek * it.minutes } / 7.0 / 5.0).roundToInt() * 5).coerceAtLeast(5)

    private fun actionsPerDay(items: List<PreviewItem>): Int =
        ceil(items.sumOf { it.timesThisWeek } / 7.0).toInt().coerceAtLeast(1)

    /** Minute of day as "10:30 PM", or "22:30" on a 24-hour device. */
    private fun clock(minuteOfDay: Int, use24HourClock: Boolean): String {
        val m = Math.floorMod(minuteOfDay, MINUTES_PER_DAY)
        val hour = m / 60
        val minutes = (m % 60).toString().padStart(2, '0')
        if (use24HourClock) return "${hour.toString().padStart(2, '0')}:$minutes"
        val hour12 = if (hour % 12 == 0) 12 else hour % 12
        return "$hour12:$minutes ${if (hour < 12) "AM" else "PM"}"
    }

    private fun window(todayEpochDay: Long, days: Int): LongRange = (todayEpochDay - days + 1)..todayEpochDay

    private fun recentMean(readings: List<MetricReading>, type: MetricType, todayEpochDay: Long): Double? {
        val days = window(todayEpochDay, SLEEP_WINDOW_DAYS)
        return readings
            .filter { it.type == type && it.recordedAt.toEpochDay() in days }
            .mapNotNull { it.value }
            .takeIf { it.isNotEmpty() }
            ?.average()
    }
}
