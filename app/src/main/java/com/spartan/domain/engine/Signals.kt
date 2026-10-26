package com.spartan.domain.engine

import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.TimeOfDay
import com.spartan.domain.model.WhoopSnapshot
import java.time.LocalDate
import kotlin.math.roundToInt

/** Where today's reading sits against the user's own recent normal range. */
enum class SignalPosition { ABOVE, WITHIN, BELOW, LEARNING }

/**
 * One daily signal read against the user's own history rather than a population table.
 * [normalLow]..[normalHigh] is the middle half (25th–75th percentile) of the prior readings, so a
 * single odd night moves it very little. [history] runs oldest → newest and ends with [latest].
 * [dates] is the day of each [history] value, in the same order; empty when the days are unknown.
 */
data class Signal(
    val metric: MetricType,
    val latest: Double,
    val history: List<Double>,
    val normalLow: Double?,
    val normalHigh: Double?,
    val position: SignalPosition,
    val dates: List<LocalDate> = emptyList(),
) {
    /** Days of history behind the normal range (the latest reading is not part of it). */
    val baselineDays: Int get() = history.size - 1
}

object SignalEngine {
    /** The daily wearable signals, in the order Spartan presents them. */
    val DAILY = listOf(
        MetricType.RECOVERY_SCORE,
        MetricType.HRV_RMSSD,
        MetricType.RESTING_HEART_RATE,
        MetricType.SLEEP_PERFORMANCE,
        MetricType.SLEEP_DEBT,
        MetricType.RESPIRATORY_RATE,
        MetricType.DAY_STRAIN,
    )

    /** Prior days needed before Spartan will call anything "your normal". */
    const val MIN_BASELINE_DAYS = 5
    private const val WINDOW_DAYS = 30

    fun signals(readings: List<MetricReading>): List<Signal> = DAILY.mapNotNull { signal(it, readings) }

    fun signal(metric: MetricType, readings: List<MetricReading>): Signal? {
        // One value per day — the newest entry wins, so a re-sync never double-counts a day.
        val days = readings
            .filter { it.type == metric && it.value != null }
            .sortedWith(compareBy({ it.recordedAt }, { it.id }))
            .associateBy { it.recordedAt }
            .mapNotNull { (day, reading) -> reading.value?.let { day to it } }
            .takeLast(WINDOW_DAYS + 1)
        val series = days.map { it.second }
        val dates = days.map { it.first }
        val latest = series.lastOrNull() ?: return null
        val prior = series.dropLast(1).sorted()
        if (prior.size < MIN_BASELINE_DAYS) return Signal(metric, latest, series, null, null, SignalPosition.LEARNING, dates)
        val low = percentile(prior, 0.25)
        val high = percentile(prior, 0.75)
        val position = when {
            latest > high -> SignalPosition.ABOVE
            latest < low -> SignalPosition.BELOW
            else -> SignalPosition.WITHIN
        }
        return Signal(metric, latest, series, low, high, position, dates)
    }

    /** Linear-interpolated percentile of an already-sorted, non-empty list. */
    private fun percentile(sorted: List<Double>, p: Double): Double {
        val rank = p * (sorted.size - 1)
        val lower = sorted[rank.toInt()]
        val upper = sorted[minOf(rank.toInt() + 1, sorted.lastIndex)]
        return lower + (upper - lower) * (rank - rank.toInt())
    }
}

/** A small, concrete thing to do about a metric, ready to drop into today's plan. */
data class SuggestedAction(
    val key: String,
    val title: String,
    val minutes: Int,
    val why: String,
    val category: ActivityCategory,
    val intensity: Intensity,
    val time: TimeOfDay,
    val steps: List<String>,
)

/**
 * The levers that move each metric, best first. Titles match the coaching engine's own activities
 * where the idea is the same, so an action that is already in today's plan is recognised as such.
 * Clinician-first labs (ApoB, Lp(a), CAC) deliberately get no self-directed action.
 */
object ActionPlaybook {
    /** Rule id stamped on activities the user added themselves. */
    const val USER_ADDED_RULE = "USER_ADDED"

    private val breathing = SuggestedAction("breathing", "5-minute breathing protocol", 5,
        "Slow, exhale-led breathing is an easy way to downshift before bed.",
        ActivityCategory.BREATHWORK, Intensity.REST, TimeOfDay.EVENING,
        listOf("Sit comfortably.", "Inhale for 4 seconds, exhale for 6.", "Repeat for 5 minutes, shoulders relaxed."))
    private val easyWalk = SuggestedAction("easy-walk", "15-minute easy walk", 15,
        "An easy walk keeps you moving without adding strain.",
        ActivityCategory.ZONE2, Intensity.EASY, TimeOfDay.MIDDAY,
        listOf("Walk at a conversational pace.", "Keep effort easy — you should be able to talk."))
    private val zone2 = SuggestedAction("zone2", "25-minute Zone 2 session", 25,
        "Regular easy aerobic work tends to bring resting heart rate down slowly, over months.",
        ActivityCategory.ZONE2, Intensity.MODERATE, TimeOfDay.AFTERNOON,
        listOf("Walk briskly, cycle, or row.", "Hold a pace where you can still talk.", "Finish feeling like you could keep going."))
    private val mobility = SuggestedAction("mobility", "10-minute mobility flow", 10,
        "Gentle range-of-motion work loosens you up without adding load.",
        ActivityCategory.MOBILITY, Intensity.EASY, TimeOfDay.MORNING,
        listOf("Move through hips, ankles, upper back, and shoulders.", "Slow, controlled reps — never force the end range."))
    private val windDown = SuggestedAction("wind-down", "Sleep wind-down routine", 20,
        "A consistent wind-down makes it easier to be in bed on time.",
        ActivityCategory.SLEEP, Intensity.REST, TimeOfDay.EVENING,
        listOf("Dim screens 45 minutes before lights-out.", "Keep the room cool and dark.", "Same bedtime as last night, give or take 30 minutes."))
    private val caffeineCutoff = SuggestedAction("caffeine-cutoff", "Caffeine cutoff at 2 PM", 1,
        "Many people find falling asleep easier with no caffeine after early afternoon.",
        ActivityCategory.NUTRITION, Intensity.REST, TimeOfDay.MIDDAY,
        listOf("Switch to water or decaf after 2 PM."))
    private val morningLight = SuggestedAction("morning-light", "10 minutes of morning daylight", 10,
        "Daylight soon after waking is a simple habit, and many people find bedtime comes easier with it.",
        ActivityCategory.MOVEMENT, Intensity.EASY, TimeOfDay.MORNING,
        listOf("Step outside within an hour of waking.", "A short walk counts — no sunglasses if comfortable."))
    private val hydrate = SuggestedAction("hydrate", "Front-load your water", 2,
        "An early glass of water is an easy way to start the day.",
        ActivityCategory.HYDRATION, Intensity.REST, TimeOfDay.MORNING,
        listOf("Drink a full glass of water now.", "Keep a bottle within reach this morning."))
    private val stretchReset = SuggestedAction("stretch-reset", "10-minute stretch and reset", 10,
        "After a big day, light stretching helps you downshift for the night.",
        ActivityCategory.MOBILITY, Intensity.EASY, TimeOfDay.EVENING,
        listOf("Light full-body stretching.", "Breathe slowly and stay comfortable."))
    private val mealWalk = SuggestedAction("meal-walk", "10-minute walk after a meal", 10,
        "A short, easy walk after a meal is a simple way to add movement to your day.",
        ActivityCategory.ZONE2, Intensity.EASY, TimeOfDay.ANYTIME,
        listOf("Walk easily for 10 minutes after your biggest meal."))
    private val strength = SuggestedAction("strength", "30-minute strength session", 30,
        "Regular strength work tends to make everyday lifting and carrying feel easier, over months.",
        ActivityCategory.STRENGTH, Intensity.MODERATE, TimeOfDay.AFTERNOON,
        listOf("Warm up for 5 minutes.", "3–4 compound movements with clean form.", "Leave 2–3 reps in reserve."))

    fun forMetric(metric: MetricType): List<SuggestedAction> = when (metric) {
        MetricType.RECOVERY_SCORE -> listOf(mobility, windDown, easyWalk)
        MetricType.HRV_RMSSD -> listOf(breathing, zone2, windDown)
        MetricType.RESTING_HEART_RATE -> listOf(hydrate, zone2, windDown)
        MetricType.SLEEP_PERFORMANCE, MetricType.SLEEP_DURATION, MetricType.SLEEP_DEBT ->
            listOf(windDown, caffeineCutoff, morningLight)
        MetricType.RESPIRATORY_RATE -> listOf(breathing, hydrate)
        MetricType.DAY_STRAIN -> listOf(stretchReset, windDown, hydrate)
        MetricType.ENERGY_KCAL, MetricType.EXERCISE_MINUTES -> listOf(easyWalk, zone2)
        MetricType.STRENGTH_SESSIONS, MetricType.HDL_C -> listOf(strength, zone2)
        MetricType.FASTING_GLUCOSE, MetricType.TRIGLYCERIDES, MetricType.TG_HDL_RATIO,
        MetricType.WEIGHT, MetricType.WAIST_CIRCUMFERENCE, MetricType.BMI, MetricType.WAIST_TO_HEIGHT_RATIO ->
            listOf(mealWalk, zone2, strength)
        MetricType.SYSTOLIC_BP, MetricType.DIASTOLIC_BP -> listOf(zone2, breathing, easyWalk)
        MetricType.VITAMIN_D_25OH -> listOf(morningLight)
        MetricType.APOB, MetricType.LPA, MetricType.CAC, MetricType.CUSTOM -> emptyList()
    }

    /**
     * One action per metric for a list of cards shown together, so no suggestion repeats on screen:
     * each metric, in [metrics] order, gets its first [forMetric] action that no earlier metric
     * already took (Sleep debt no longer echoes Sleep performance's wind-down, nor respiratory rate
     * HRV's breathing). A metric whose actions are all taken gets none — it is left out of the map —
     * and a metric listed twice keeps its first pick.
     */
    fun uniqueFirstActions(metrics: List<MetricType>): Map<MetricType, SuggestedAction> {
        val picked = LinkedHashMap<MetricType, SuggestedAction>()
        val taken = mutableSetOf<String>()
        metrics.forEach { metric ->
            if (metric in picked) return@forEach
            val action = forMetric(metric).firstOrNull { it.key !in taken } ?: return@forEach
            taken += action.key
            picked[metric] = action
        }
        return picked
    }

    /** The action as a plan item for [dayEpoch]. Same action, same day → same id, so re-adding is a no-op. */
    fun toActivity(action: SuggestedAction, metric: MetricType, dayEpoch: Long) = DailyActivity(
        id = "$dayEpoch:add:${action.key}",
        title = action.title,
        category = action.category,
        priority = ActivityPriority.RECOMMENDED,
        whyItMatters = action.why,
        relatedMetric = metric,
        instructions = action.steps,
        estimatedMinutes = action.minutes,
        intensity = action.intensity,
        bestTimeOfDay = action.time,
        ruleId = USER_ADDED_RULE,
    )

    /** True when today's plan already covers this action, generated or user-added. */
    fun isPlanned(action: SuggestedAction, today: List<DailyActivity>): Boolean =
        today.any { it.title.equals(action.title, ignoreCase = true) || it.id.endsWith(":add:${action.key}") }
}

/**
 * Last night's sleep need. WHOOP's own figure when the data carries it (the export's "Sleep need
 * (min)", the API's sleep_needed); otherwise an estimate from WHOOP's ratio — Sleep Performance is
 * hours slept / hours needed, so need ≈ slept ÷ performance.
 */
object SleepNeed {
    /** Anything outside this is a rounding artefact or a bad reading, not a sleep need. */
    private val PLAUSIBLE_HOURS = 4.0..14.0

    /**
     * The need for [snapshot]'s night: WHOOP's own [WhoopSnapshot.sleepNeedHours] when present and
     * plausible, else the [neededHours] estimate from hours slept and performance; null when neither
     * is known. A WHOOP figure outside the plausible range is a bad reading and is not used.
     */
    fun forSnapshot(snapshot: WhoopSnapshot?): Double? =
        snapshot?.sleepNeedHours?.takeIf { it in PLAUSIBLE_HOURS }
            ?: neededHours(snapshot?.sleepDurationHours, snapshot?.sleepPerformance)

    /**
     * Hours needed from [sleptHours] and [performancePercent]; null when either is missing or
     * non-positive, or the result is implausible. No need is better than an invented one.
     * WHOOP caps performance at 100%, so at 100 the need is only known to be at most the hours
     * slept — unknown, so null rather than "need = slept".
     */
    fun neededHours(sleptHours: Double?, performancePercent: Int?): Double? {
        if (sleptHours == null || performancePercent == null || sleptHours <= 0.0) return null
        if (performancePercent <= 0 || performancePercent >= 100) return null
        return (sleptHours / (performancePercent / 100.0)).takeIf { it in PLAUSIBLE_HOURS }
    }
}

/**
 * Tonight's sleep target: when to start winding down and when the lights go out. [sleepHours] is
 * the night it plans for: the estimated need, or — when the target is the user's own bedtime —
 * the time from that bedtime to the wake time (null without a wake time). [needHours] is the
 * estimated need, if known. [debtHours] is shown alongside, never added on top: WHOOP's sleep
 * need already counts recent short nights.
 */
data class BedtimePlan(
    val windDownMinute: Int,
    val lightsOutMinute: Int,
    val sleepHours: Double?,
    val debtHours: Double,
    val needHours: Double?,
    /** True when the target is the bedtime the user gave in plan settings, not one Spartan derived. */
    val fromUserBedtime: Boolean = false,
)

/**
 * Tonight's lights-out: the bedtime the user gave, else one from their own sleep need — never a
 * fixed eight hours. With neither, Spartan sets no target.
 */
object SleepPlanner {
    private const val WIND_DOWN_MINUTES = 45

    /**
     * [userWakeMinute] (from plan settings) wins over the observed [wakeMinuteOfDay]. The user's
     * [userBedtimeMinute] is the target as given — the plan works around what they said — and
     * [BedtimePlan.sleepHours] is then the time from it to the wake time. Without one, a wake time
     * and [needHours] give lights-out = wake − need. Debt is not added again: WHOOP's sleep need
     * (which [needHours] is estimated from) already includes paying it back, so adding it twice
     * would push bedtime unrealistically early. With neither, null.
     */
    fun tonight(
        wakeMinuteOfDay: Int?,
        sleepDebtHours: Double?,
        needHours: Double?,
        userBedtimeMinute: Int? = null,
        userWakeMinute: Int? = null,
    ): BedtimePlan? {
        val debt = (sleepDebtHours ?: 0.0).coerceAtLeast(0.0)
        val wake = userWakeMinute ?: wakeMinuteOfDay
        if (userBedtimeMinute != null) {
            val lightsOut = roundDown(userBedtimeMinute)
            val inBed = wake?.let { Math.floorMod(it - lightsOut, MINUTES_PER_DAY) / 60.0 }?.takeIf { it > 0.0 }
            return BedtimePlan(windDown(lightsOut), lightsOut, inBed, debt, needHours, fromUserBedtime = true)
        }
        if (needHours != null && wake != null) {
            val lightsOut = roundDown(wake - (needHours * 60).roundToInt())
            return BedtimePlan(windDown(lightsOut), lightsOut, needHours, debt, needHours)
        }
        return null
    }

    // Rounded down to five minutes: "10:40 PM" reads as a plan, "10:43 PM" as a calculation.
    private fun roundDown(minute: Int): Int = Math.floorMod(minute, MINUTES_PER_DAY) / 5 * 5

    private fun windDown(lightsOut: Int): Int = Math.floorMod(lightsOut - WIND_DOWN_MINUTES, MINUTES_PER_DAY)

    /**
     * True from lights-out until six hours after it: the target has passed but the night is still
     * on, so the advice becomes "sleep as soon as you can" rather than a time already gone.
     */
    fun isPastLightsOut(plan: BedtimePlan, nowMinuteOfDay: Int): Boolean =
        Math.floorMod(nowMinuteOfDay - plan.lightsOutMinute, MINUTES_PER_DAY) in 1 until 6 * 60

    private const val MINUTES_PER_DAY = 24 * 60
}
