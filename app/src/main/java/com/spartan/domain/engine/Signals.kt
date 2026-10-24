package com.spartan.domain.engine

import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.TimeOfDay
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

    /** True if the reading moved the healthy way, false the other way; null inside the range or when neither way is better. */
    val favorable: Boolean?
        get() {
            val up = SignalEngine.healthyDirectionUp(metric) ?: return null
            return when (position) {
                SignalPosition.ABOVE -> up
                SignalPosition.BELOW -> !up
                else -> null
            }
        }
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

    /**
     * Which way is healthy, judged against the user's own baseline. Respiratory rate counts as
     * lower-is-healthier because a rise over baseline is the signal worth noticing; strain and
     * energy have no healthy direction at all.
     */
    fun healthyDirectionUp(metric: MetricType): Boolean? = when (metric) {
        MetricType.DAY_STRAIN, MetricType.ENERGY_KCAL -> null
        MetricType.RESPIRATORY_RATE -> false
        else -> !metric.lowerIsBetter
    }

    /** One plain sentence on what this reading means for today. Observational, never diagnostic. */
    fun meaning(signal: Signal): String {
        if (signal.position == SignalPosition.LEARNING) {
            return "Spartan learns your normal from about a week of readings. Keep wearing WHOOP."
        }
        val fav = signal.favorable
        return when (signal.metric) {
            MetricType.RECOVERY_SCORE -> when (fav) {
                false -> "Your body is still recovering. Gentle movement beats intensity today."
                true -> "You're better recovered than usual — a good window for a quality session."
                null -> "Typical recovery for you. A balanced day of steady work fits."
            }
            MetricType.HRV_RMSSD -> when (fav) {
                false -> "Lower HRV often follows short sleep, alcohol, or hard training. Keep today easier and protect tonight's sleep."
                true -> "Higher than usual — your nervous system is well rested."
                null -> "Steady. Consistent sleep and easy aerobic work keep it here."
            }
            MetricType.RESTING_HEART_RATE -> when (fav) {
                false -> "Up versus your usual. Hydrate, keep training easy, and check how you slept."
                true -> "Lower than usual — a sign of good recovery and aerobic fitness."
                null -> "Steady. Regular Zone 2 work lowers it slowly over months."
            }
            MetricType.SLEEP_PERFORMANCE -> when (fav) {
                false -> "You got less of the sleep you needed. A wind-down tonight helps you catch up."
                true -> "Better sleep than usual — recovery should follow."
                null -> "Steady sleep. Consistent bed and wake times keep it that way."
            }
            MetricType.SLEEP_DEBT -> when (fav) {
                false -> "More sleep debt than usual. An earlier lights-out tonight pays some of it down."
                true -> "Less sleep debt than usual. Keep the rhythm."
                null -> "Typical sleep debt for you."
            }
            MetricType.RESPIRATORY_RATE -> when (fav) {
                false -> "Higher than your usual. It often rises after hard effort or when you are run down — take it easy and notice how you feel."
                true -> "Calm and steady."
                null -> "Steady — one of your most stable signals, so changes are worth noticing."
            }
            MetricType.DAY_STRAIN -> when (signal.position) {
                SignalPosition.ABOVE -> "A bigger day than usual. Pair it with extra recovery tonight."
                SignalPosition.BELOW -> "A lighter day than usual."
                else -> "A typical load for you."
            }
            else -> "Compare this with your recent trend rather than any single reading."
        }
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
        "Slow, exhale-led breathing shifts you toward rest-and-recover — the state that lifts HRV.",
        ActivityCategory.BREATHWORK, Intensity.REST, TimeOfDay.EVENING,
        listOf("Sit comfortably.", "Inhale for 4 seconds, exhale for 6.", "Repeat for 5 minutes, shoulders relaxed."))
    private val easyWalk = SuggestedAction("easy-walk", "15-minute easy walk", 15,
        "Easy movement boosts circulation and recovery without adding strain.",
        ActivityCategory.ZONE2, Intensity.EASY, TimeOfDay.MIDDAY,
        listOf("Walk at a conversational pace.", "Keep effort easy — you should be able to talk."))
    private val zone2 = SuggestedAction("zone2", "25-minute Zone 2 session", 25,
        "Steady aerobic work is the most reliable way to lower resting heart rate and raise HRV over weeks.",
        ActivityCategory.ZONE2, Intensity.MODERATE, TimeOfDay.AFTERNOON,
        listOf("Walk briskly, cycle, or row.", "Hold a pace where you can still talk.", "Finish feeling like you could keep going."))
    private val mobility = SuggestedAction("mobility", "10-minute mobility flow", 10,
        "Gentle range-of-motion work supports recovery without adding load.",
        ActivityCategory.MOBILITY, Intensity.EASY, TimeOfDay.MORNING,
        listOf("Move through hips, ankles, upper back, and shoulders.", "Slow, controlled reps — never force the end range."))
    private val windDown = SuggestedAction("wind-down", "Sleep wind-down routine", 20,
        "A consistent wind-down helps you fall asleep faster and sleep deeper.",
        ActivityCategory.SLEEP, Intensity.REST, TimeOfDay.EVENING,
        listOf("Dim screens 45 minutes before lights-out.", "Keep the room cool and dark.", "Same bedtime as last night, give or take 30 minutes."))
    private val caffeineCutoff = SuggestedAction("caffeine-cutoff", "Caffeine cutoff at 2 PM", 1,
        "Caffeine lingers for hours, so an early cutoff protects tonight's deep sleep.",
        ActivityCategory.NUTRITION, Intensity.REST, TimeOfDay.MIDDAY,
        listOf("Switch to water or decaf after 2 PM."))
    private val morningLight = SuggestedAction("morning-light", "10 minutes of morning daylight", 10,
        "Morning light anchors your body clock, so falling asleep on time gets easier.",
        ActivityCategory.MOVEMENT, Intensity.EASY, TimeOfDay.MORNING,
        listOf("Step outside within an hour of waking.", "A short walk counts — no sunglasses if comfortable."))
    private val hydrate = SuggestedAction("hydrate", "Front-load your water", 2,
        "Mild dehydration nudges resting heart rate up; an early glass helps.",
        ActivityCategory.HYDRATION, Intensity.REST, TimeOfDay.MORNING,
        listOf("Drink a full glass of water now.", "Keep a bottle within reach this morning."))
    private val stretchReset = SuggestedAction("stretch-reset", "10-minute stretch and reset", 10,
        "After a big day, light stretching helps you downshift for the night.",
        ActivityCategory.MOBILITY, Intensity.EASY, TimeOfDay.EVENING,
        listOf("Light full-body stretching.", "Breathe slowly and stay comfortable."))
    private val mealWalk = SuggestedAction("meal-walk", "10-minute walk after a meal", 10,
        "A short walk after eating helps your body handle the meal.",
        ActivityCategory.ZONE2, Intensity.EASY, TimeOfDay.ANYTIME,
        listOf("Walk easily for 10 minutes after your biggest meal."))
    private val strength = SuggestedAction("strength", "30-minute strength session", 30,
        "Building muscle improves how your body uses fuel and supports long-term health.",
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
 * Last night's sleep need, estimated from WHOOP's own ratio. WHOOP's Sleep Performance is hours
 * slept / hours needed, so need ≈ slept ÷ performance. This is an estimate — not WHOOP's exact
 * Sleep Need, whose inputs (baseline, strain, debt, naps) are not in the data Spartan reads.
 */
object SleepNeed {
    /** Anything outside this is a rounding artefact or a bad reading, not a sleep need. */
    private val PLAUSIBLE_HOURS = 4.0..14.0

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
