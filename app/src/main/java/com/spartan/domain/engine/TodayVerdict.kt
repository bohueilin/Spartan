package com.spartan.domain.engine

import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.DailyPlan
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.ReadinessBand
import com.spartan.domain.model.ReadinessSnapshot
import java.util.Locale
import kotlin.math.roundToInt

/*
 * Today's morning verdict: one colour + word + number, one strain target, and a plain-language
 * "why today". Built from the newest COMPLETED WHOOP cycle only — a cycle runs from sleep onset to
 * the next sleep onset, so recovery exists only once the user has woken. When there is no
 * completed cycle for today, the verdict is Pending and nothing is fabricated.
 */

/**
 * WHOOP's recovery colour bands (WHOOP-verified: Green 67–100, Yellow 34–66, Red 0–33). Colour is
 * never shown alone — always colour + word + number ("Green · 72%").
 */
enum class RecoveryZone(val label: String) {
    GREEN("Green"),
    YELLOW("Yellow"),
    RED("Red");

    companion object {
        /** 67–100 → GREEN, 34–66 → YELLOW, 0–33 → RED. Out-of-range input is clamped to 0..100. */
        fun of(recoveryScore: Int): RecoveryZone = when (recoveryScore.coerceIn(0, 100)) {
            in 67..100 -> GREEN
            in 34..66 -> YELLOW
            else -> RED
        }
    }
}

/**
 * WHOOP's day-strain zones on its 0–21 logarithmic scale (Light 0–9, Moderate 10–13, High 14–17,
 * All out 18–21). There is no universally good strain — a zone is a target, never a grade.
 */
enum class StrainZone(val label: String, val low: Int, val high: Int) {
    LIGHT("Light", 0, 9),
    MODERATE("Moderate", 10, 13),
    HIGH("High", 14, 17),
    ALL_OUT("All out", 18, 21);

    companion object {
        /** < 10 → LIGHT, < 14 → MODERATE, < 18 → HIGH, otherwise ALL_OUT (input clamped to 0..21). */
        fun of(strain: Double): StrainZone {
            // NaN would slip through coerceIn and land in ALL_OUT; an unreadable value is no load.
            val clamped = if (strain.isNaN()) 0.0 else strain.coerceIn(0.0, 21.0)
            return when {
                clamped < 10.0 -> LIGHT
                clamped < 14.0 -> MODERATE
                clamped < 18.0 -> HIGH
                else -> ALL_OUT
            }
        }
    }
}

/** Why there is no verdict yet. */
enum class PendingReason {
    /** No wearable data at all (first run before any sync). */
    NO_DATA,

    /** Today's cycle exists but has no recovery score yet (cycle not completed / still scoring). */
    NO_COMPLETED_CYCLE,

    /** The newest cycle is from another day (export or sync lag); it is not today's verdict. */
    STALE,

    /** The sync failed and this session has no readiness to show — not the same as never connected. */
    SYNC_FAILED,
}

/**
 * Which hero line the verdict card shows. Read from the band AND the plan: on a pain or check-in day
 * the plan stays gentle, so the headline does too — never "Ready for a big day" above "rest today".
 */
enum class VerdictHeadline { BIG_DAY, STEADY, EASIER, RECOVERY, GENTLE }

sealed interface MorningVerdict {
    /** No verdict — never fabricated. [lastCycleEpochDay] is the newest cycle Spartan has, if any. */
    data class Pending(val reason: PendingReason, val lastCycleEpochDay: Long? = null) : MorningVerdict

    /** Today's verdict from today's completed cycle. */
    data class Ready(
        /** Wake day of the cycle the verdict describes (always today for a Ready verdict). */
        val cycleEpochDay: Long,
        val recoveryScore: Int,
        val zone: RecoveryZone,
        /** The coaching band (finer-grained than [zone]) that shapes the plan's wording. */
        val band: ReadinessBand,
        /** Yesterday's recovery, for "up from 58% yesterday"; null when unknown or not from yesterday. */
        val previousRecoveryScore: Int?,
        /** The plan's strain target for today; null when the plan sets none. */
        val strainTarget: StrainZone?,
    ) : MorningVerdict
}

object VerdictEngine {
    /**
     * The verdict for [readiness] (the snapshot DailyPlanSync built for today, possibly carried
     * forward and marked stale):
     *  - null readiness → Pending(SYNC_FAILED) when [syncFailed], otherwise Pending(NO_DATA)
     *  - cycle from an earlier day → Pending(STALE, readiness.cycleEpochDay), checked before the
     *    score so an old cycle with a blank recovery never reads as "not completed yet today"
     *  - recoveryScore == null → Pending(NO_COMPLETED_CYCLE, readiness.cycleEpochDay)
     *  - isStale (carried forward from another cycle) → Pending(STALE, readiness.cycleEpochDay)
     *  - otherwise Ready, with [strainTarget] from today's plan.
     */
    fun verdict(readiness: ReadinessSnapshot?, strainTarget: StrainZone?, syncFailed: Boolean = false): MorningVerdict {
        if (readiness == null) {
            return MorningVerdict.Pending(if (syncFailed) PendingReason.SYNC_FAILED else PendingReason.NO_DATA)
        }
        if (readiness.cycleEpochDay < readiness.dateEpochDay) {
            return MorningVerdict.Pending(PendingReason.STALE, readiness.cycleEpochDay)
        }
        val score = readiness.recoveryScore
            ?: return MorningVerdict.Pending(PendingReason.NO_COMPLETED_CYCLE, readiness.cycleEpochDay)
        if (readiness.isStale) return MorningVerdict.Pending(PendingReason.STALE, readiness.cycleEpochDay)
        return MorningVerdict.Ready(
            cycleEpochDay = readiness.cycleEpochDay,
            recoveryScore = score,
            zone = RecoveryZone.of(score),
            band = readiness.band,
            previousRecoveryScore = readiness.previousRecoveryScore,
            strainTarget = strainTarget,
        )
    }

    /**
     * The hero line for a Ready verdict and today's planned [activities]: GENTLE whenever the plan
     * carries the pain deload or the clinician check-in, otherwise the band's own line.
     */
    fun headline(verdict: MorningVerdict.Ready, activities: List<DailyActivity>): VerdictHeadline {
        if (activities.any { it.ruleId == RuleIds.PAIN_DELOAD || it.ruleId == RuleIds.CLINICIAN_REFERRAL }) {
            return VerdictHeadline.GENTLE
        }
        return when (verdict.band) {
            ReadinessBand.PRIMED -> VerdictHeadline.BIG_DAY
            ReadinessBand.BALANCED -> VerdictHeadline.STEADY
            ReadinessBand.EASY -> VerdictHeadline.EASIER
            ReadinessBand.REST -> VerdictHeadline.RECOVERY
        }
    }
}

/**
 * The expandable "Why today?" card. [summary] is the collapsed one-liner; [reasons] are the short
 * observations behind the plan (each one sentence, numbers with their personal context); [conclusion]
 * says what the plan does about them. Correlational, uncertain language only — never "because",
 * never a diagnosis, never a causal claim from journal data. Every string passes [SafetyEngine].
 */
data class WhyToday(
    val summary: String,
    val reasons: List<String>,
    val conclusion: String,
)

object WhyTodayEngine {
    /**
     * Explain today's plan from the verdict, the plan itself, the user's signals (each read against
     * their own normal), and last night's sleep. Pending verdicts explain the holding plan and the
     * cycle rule ("a WHOOP cycle runs from sleep onset to the next sleep onset").
     */
    fun explain(
        readiness: ReadinessSnapshot?,
        verdict: MorningVerdict,
        plan: DailyPlan?,
        signals: List<Signal>,
        sleptHours: Double?,
        sleepNeedHours: Double?,
    ): WhyToday {
        val why = when (verdict) {
            is MorningVerdict.Ready -> ready(verdict, readiness, plan, signals, sleptHours, sleepNeedHours)
            is MorningVerdict.Pending -> pending(verdict, readiness)
        }
        return WhyToday(
            summary = safety.sanitize(why.summary),
            reasons = why.reasons.map(safety::sanitize),
            conclusion = safety.sanitize(why.conclusion),
        )
    }

    private val safety = SafetyEngine()

    /** Sleep debt below this is noise, not worth a line of its own. */
    private const val DEBT_WORTH_MENTIONING_HOURS = 0.25

    private const val CYCLE_RULE =
        "A WHOOP cycle runs from when you fall asleep to when you next fall asleep, so recovery is scored after you wake."

    private fun ready(
        verdict: MorningVerdict.Ready,
        readiness: ReadinessSnapshot?,
        plan: DailyPlan?,
        signals: List<Signal>,
        sleptHours: Double?,
        sleepNeedHours: Double?,
    ): WhyToday {
        val head = "${verdict.zone.label} · ${verdict.recoveryScore}%"
        val previous = verdict.previousRecoveryScore
        val summary = when {
            previous == null -> head
            verdict.recoveryScore > previous -> "$head — up from $previous% yesterday"
            verdict.recoveryScore < previous -> "$head — down from $previous% yesterday"
            else -> "$head — same as yesterday"
        }
        val reasons = listOfNotNull(
            sleepReason(readiness, sleptHours, sleepNeedHours),
            hrvReason(signals),
            restingHrReason(signals),
            respiratoryRateReason(signals),
            strainReason(readiness),
        )
        return WhyToday(summary, reasons, conclusion(plan))
    }

    /** Hours got of hours needed when both are known; otherwise any debt worth naming. */
    private fun sleepReason(readiness: ReadinessSnapshot?, sleptHours: Double?, sleepNeedHours: Double?): String? {
        if (sleptHours != null && sleepNeedHours != null) {
            return "Sleep was ${formatHoursMinutes(sleptHours)} of about ${formatHoursMinutes(sleepNeedHours)} needed."
        }
        val debt = readiness?.sleepDebtHours ?: return null
        return if (debt >= DEBT_WORTH_MENTIONING_HOURS) "You're carrying about ${formatHoursMinutes(debt)} of sleep debt." else null
    }

    /** Against the user's own usual only — HRV never gets a population number or grade. */
    private fun hrvReason(signals: List<Signal>): String? =
        when (signals.firstOrNull { it.metric == MetricType.HRV_RMSSD }?.position) {
            SignalPosition.WITHIN -> "HRV is near your usual."
            SignalPosition.ABOVE -> "HRV is above your usual."
            SignalPosition.BELOW -> "HRV is below your usual."
            SignalPosition.LEARNING -> "Spartan is still learning your usual HRV."
            null -> null
        }

    /** Only worth a line when it has moved off the user's usual. */
    private fun restingHrReason(signals: List<Signal>): String? =
        when (signals.firstOrNull { it.metric == MetricType.RESTING_HEART_RATE }?.position) {
            SignalPosition.ABOVE -> "Resting heart rate is above your usual."
            SignalPosition.BELOW -> "Resting heart rate is below your usual."
            else -> null
        }

    /** Only the rise is worth a line — it is the direction a clinician check-in can follow. */
    private fun respiratoryRateReason(signals: List<Signal>): String? {
        val position = signals.firstOrNull { it.metric == MetricType.RESPIRATORY_RATE }?.position
        return if (position == SignalPosition.ABOVE) "Respiratory rate is above your usual." else null
    }

    /** "Yesterday" only when the strain really is from the day before; after a gap it says so plainly. */
    private fun strainReason(readiness: ReadinessSnapshot?): String? {
        val r = readiness ?: return null
        val strain = r.dayStrainPrior?.takeIf { it.isFinite() } ?: return null
        val value = "${"%.1f".format(Locale.ROOT, strain)} (${StrainZone.of(strain).label})"
        return if (r.previousCycleEpochDay == r.cycleEpochDay - 1) {
            "Yesterday's strain was $value."
        } else {
            "Your last recorded strain was $value."
        }
    }

    /** What the plan does about it — read from the plan itself, so the card never contradicts it. */
    private fun conclusion(plan: DailyPlan?): String {
        val rules = plan?.activities.orEmpty().map { it.ruleId }.toSet()
        // A check-in day stays easy before anything else — rest-first, never a diagnosis.
        if (RuleIds.CLINICIAN_REFERRAL in rules) {
            return "One of today's readings is outside your usual range, so today stays easy — notice how you feel. " +
                "One reading is not a diagnosis."
        }
        if (RuleIds.PAIN_DELOAD in rules) {
            return "You flagged pain recently, so today stays gentle — stop anything that hurts."
        }
        return when (plan?.strainTarget) {
            StrainZone.HIGH, StrainZone.ALL_OUT -> "So today leans into training, with bedtime held to protect tomorrow."
            StrainZone.MODERATE -> "So today holds steady: moderate effort and a protected bedtime."
            StrainZone.LIGHT -> "So today is lighter so tomorrow can be bigger — rest is part of the plan."
            null -> "So today keeps things steady."
        }
    }

    /** No recovery number here: a pending verdict explains the wait, it never guesses the score. */
    private fun pending(verdict: MorningVerdict.Pending, readiness: ReadinessSnapshot?): WhyToday = when (verdict.reason) {
        PendingReason.NO_COMPLETED_CYCLE -> WhyToday(
            summary = "Plan pending — no completed WHOOP cycle yet today.",
            reasons = listOf(CYCLE_RULE),
            conclusion = "Here's a gentle holding plan. It updates when today's cycle arrives.",
        )
        PendingReason.STALE -> WhyToday(
            summary = "Plan pending — your newest WHOOP data is ${staleAge(verdict, readiness)}.",
            reasons = listOf(CYCLE_RULE),
            conclusion = "Here's a gentle holding plan until a new cycle arrives.",
        )
        PendingReason.NO_DATA -> WhyToday(
            summary = "No WHOOP data yet.",
            reasons = listOf("Connect WHOOP or import your WHOOP export to get a morning verdict."),
            conclusion = "Until then, here's a gentle holding plan.",
        )
        // A connected user whose refresh failed: no "connect WHOOP", and no promise of a plan.
        PendingReason.SYNC_FAILED -> WhyToday(
            summary = "Couldn't refresh your WHOOP data.",
            reasons = listOf("Spartan keeps what it already has and tries again next time."),
            conclusion = "Pull down to try again.",
        )
    }

    private fun staleAge(verdict: MorningVerdict.Pending, readiness: ReadinessSnapshot?): String {
        val last = verdict.lastCycleEpochDay
        val days = if (last != null && readiness != null) readiness.dateEpochDay - last else null
        return when {
            days == 1L -> "from yesterday"
            days != null && days > 1 -> "from $days days ago"
            // A cycle dated after the device's today (e.g. an export made across the date line).
            days != null && days < 0 -> "dated after today"
            else -> "from an earlier day"
        }
    }
}

/**
 * Hours as "7h 40m", rounded to the nearest minute: 8.0 → "8h", 0.75 → "45m", 7.0833 → "7h 05m".
 * Negative (or unreadable) input reads as "0m" — a duration is never negative.
 */
fun formatHoursMinutes(hours: Double): String {
    val totalMinutes = if (hours.isNaN() || hours <= 0.0) 0 else (hours * 60).roundToInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m.toString().padStart(2, '0')}m"
    }
}
