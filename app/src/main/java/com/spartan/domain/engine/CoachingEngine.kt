package com.spartan.domain.engine

import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.DailyPlan
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.ReadinessBand
import com.spartan.domain.model.ReadinessSnapshot
import com.spartan.domain.model.TimeOfDay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Options that tune a day's plan without changing the readiness signal itself. The defaults add
 * nothing: a plan built with default options is exactly the readiness-only plan.
 */
data class CoachingOptions(
    val maxActivities: Int = 4,
    val missedGoalYesterday: Boolean = false,
    val painFlag: Boolean = false,
    /** Tonight's lights-out target; when set, the plan carries an "In bed by …" sleep anchor. */
    val bedtime: BedtimePlan? = null,
    /** The user's one primary goal; adds a goal-focus card for the goals that have one. */
    val goalType: GoalType? = null,
    /** What the user said they won't do, and the minutes a day they have. */
    val constraints: PlanConstraints = PlanConstraints(),
    /**
     * The fewest checkable actions a day should hold (never more than [maxActivities]). A plan that
     * is shorter after the cap and the time budget is topped up with small, gentle habits — never a
     * training session. 0 = no floor.
     */
    val minActivities: Int = 0,
    /** Write clock times as "22:40" instead of "10:40 PM", matching the device's 24-hour setting. */
    val use24HourClock: Boolean = false,
)

/**
 * A source of recommended activities for a given readiness. The MVP ships one implementation,
 * [RuleBasedRecommendationSource]. An AI-backed source can be added later and bound in DI
 * WITHOUT any change to [CoachingEngine] or the UI — the seam that keeps the first version
 * from depending on a complex AI system.
 */
interface RecommendationSource {
    fun recommend(readiness: ReadinessSnapshot, options: CoachingOptions): List<DailyActivity>
}

/**
 * Transparent, rules-based daily coaching. Every rule is small, named (see [DailyActivity.ruleId]),
 * and independently unit-testable. All user-facing copy is passed through [SafetyEngine] so no
 * generated text can make a medical claim or unsafe recommendation.
 *
 * Guidance is framed as wellness / fitness / recovery / habit support — never diagnosis. When a
 * metric looks potentially concerning, the engine adds a [CLINICIAN_REFERRAL] activity that
 * recommends seeing a qualified clinician rather than interpreting the value.
 */
class CoachingEngine(
    private val source: RecommendationSource = RuleBasedRecommendationSource(),
    private val safetyEngine: SafetyEngine = SafetyEngine(),
) {
    fun buildPlan(
        readiness: ReadinessSnapshot,
        options: CoachingOptions = CoachingOptions(),
    ): DailyPlan {
        val raw = source.recommend(readiness, options)
        val capped = prioritizeAndCap(raw, options.maxActivities)
        val fitted = options.constraints.minutesPerDay?.let { fitToBudget(capped, it) } ?: capped
        val activities = topUp(fitted, readiness.dateEpochDay, options)
        val plan = DailyPlan(
            dateEpochDay = readiness.dateEpochDay,
            headline = headlineFor(readiness, activities),
            band = readiness.band,
            activities = activities,
            safetyBanner = safetyBannerFor(readiness),
            isMock = readiness.isMock,
            strainTarget = strainTargetFor(readiness, options, activities),
        )
        // Defensive: every string that can reach the user is validated here as well.
        plan.activities.forEach { activity ->
            safetyEngine.sanitize(activity.title)
            safetyEngine.sanitize(activity.whyItMatters)
            activity.instructions.forEach(safetyEngine::sanitize)
            activity.safetyNote?.let(safetyEngine::sanitize)
        }
        plan.safetyBanner?.let(safetyEngine::sanitize)
        safetyEngine.sanitize(plan.headline)
        return plan
    }

    /**
     * REQUIRED first, then RECOMMENDED, then OPTIONAL; always keep required + clinician referral.
     * When the cap forces choices, prefer covering DISTINCT needs (one card per rule) before a
     * second card from a rule that's already represented — a stacked day (red flag + poor sleep +
     * low recovery) must not spend its whole budget on one rule and drop the wind-down.
     * (Caught by the CoachingGym's quality grader.)
     */
    private fun prioritizeAndCap(activities: List<DailyActivity>, maxActivities: Int): List<DailyActivity> {
        val deduped = activities.distinctBy { it.id }
        val ordered = deduped.sortedBy { it.priority.ordinal }
        val mustKeep = ordered.filter {
            it.priority == ActivityPriority.REQUIRED || it.ruleId == RuleIds.CLINICIAN_REFERRAL
        }
        val optional = ordered.filterNot { it in mustKeep }
        val room = (maxActivities - mustKeep.size).coerceAtLeast(0)

        val picked = mutableListOf<DailyActivity>()
        val coveredRules = mustKeep.map { it.ruleId }.toMutableSet()
        for (a in optional) { // first pass: unrepresented rules, in priority order
            if (picked.size == room) break
            if (coveredRules.add(a.ruleId)) picked += a
        }
        for (a in optional) { // second pass: fill remaining room, in priority order
            if (picked.size == room) break
            if (a !in picked) picked += a
        }
        return (mustKeep + picked).sortedBy { it.priority.ordinal }
    }

    /**
     * Trim a capped plan to the user's minutes a day, least important first: optional cards, then
     * the recommended rule cards, and the user's own goal card last — the latest card first within
     * each. Required cards, the clinician referral, the main training session and the sleep anchor
     * are never dropped — if those alone run over, the plan keeps them and the UI shows the honest
     * total rather than quietly removing guidance.
     */
    private fun fitToBudget(activities: List<DailyActivity>, minutesPerDay: Int): List<DailyActivity> {
        val kept = activities.toMutableList()
        val mainId = mainSession(activities)?.id
        fun droppable(a: DailyActivity) = a.priority != ActivityPriority.REQUIRED &&
            a.ruleId != RuleIds.CLINICIAN_REFERRAL && a.id != mainId && a.ruleId != RuleIds.SLEEP_ANCHOR
        while (kept.sumOf { it.estimatedMinutes } > minutesPerDay) {
            val drop = kept.withIndex()
                .filter { droppable(it.value) }
                .minWithOrNull(compareBy<IndexedValue<DailyActivity>>({ dropTier(it.value) }, { -it.index }))
                ?.value ?: break
            kept.remove(drop)
        }
        return kept
    }

    /** Budget drop order: 0 = an optional card, 1 = a recommended rule card, 2 = the goal's own card. */
    private fun dropTier(activity: DailyActivity): Int = when {
        activity.ruleId == RuleIds.GOAL_FOCUS -> 2
        activity.priority == ActivityPriority.OPTIONAL -> 0
        else -> 1
    }

    /**
     * Lift a plan shorter than [CoachingOptions.minActivities] (never past the cap) with small,
     * gentle habits from the [ActionPlaybook], in a fixed order. A habit is only added when it
     * covers a need the plan doesn't already (no second card of the same kind), the user's won't-do
     * list allows it, and it fits what is left of the time budget. Never a training session, and on a
     * pain or check-in day no movement at all — those days stay restful. Stale days are topped up too.
     */
    private fun topUp(activities: List<DailyActivity>, day: Long, options: CoachingOptions): List<DailyActivity> {
        val floor = minOf(options.minActivities, options.maxActivities)
        if (activities.size >= floor) return activities
        val avoid = options.constraints.avoid
        val restful = activities.any { it.ruleId == RuleIds.PAIN_DELOAD || it.ruleId == RuleIds.CLINICIAN_REFERRAL }
        val out = activities.toMutableList()
        for ((metric, action) in TOP_UPS) {
            if (out.size >= floor) break
            val minutesLeft = options.constraints.minutesPerDay?.let { it - out.sumOf { a -> a.estimatedMinutes } }
            val movement = action.category in MOVEMENT_CATEGORIES
            val allowed = when {
                out.any { it.category == action.category } || ActionPlaybook.isPlanned(action, out) -> false
                action.category == ActivityCategory.BREATHWORK && AvoidOption.BREATHWORK in avoid -> false
                movement && restful -> false
                movement && action.time == TimeOfDay.MORNING && AvoidOption.MORNING_SESSIONS in avoid -> false
                minutesLeft != null && action.minutes > minutesLeft -> false
                else -> true
            }
            if (allowed) out += topUpActivity(action, metric, day)
        }
        return out
    }

    private fun topUpActivity(action: SuggestedAction, metric: MetricType, day: Long) = DailyActivity(
        id = "$day:${action.key}",
        title = action.title,
        category = action.category,
        priority = ActivityPriority.OPTIONAL,
        whyItMatters = action.why,
        relatedMetric = metric,
        instructions = action.steps,
        estimatedMinutes = action.minutes,
        intensity = action.intensity,
        bestTimeOfDay = action.time,
        ruleId = RuleIds.DAILY_HABIT,
    )

    /**
     * The day's strain target, read from the session actually planned so the two never disagree:
     * none on a stale day (no verdict to target), Light on a pain day, otherwise the main session's
     * zone — hard → High, moderate → Moderate, easy → Light.
     */
    private fun strainTargetFor(readiness: ReadinessSnapshot, options: CoachingOptions, activities: List<DailyActivity>): StrainZone? = when {
        readiness.isStale -> null
        options.painFlag -> StrainZone.LIGHT
        else -> when (mainSession(activities)?.intensity) {
            Intensity.HARD -> StrainZone.HIGH
            Intensity.MODERATE -> StrainZone.MODERATE
            Intensity.EASY, Intensity.REST -> StrainZone.LIGHT
            null -> null
        }
    }

    /** The day's main training card: the band session, or the pain / stale card that stands in for it. */
    private fun mainSession(activities: List<DailyActivity>): DailyActivity? =
        activities.firstOrNull { it.ruleId in TRAINING_RULES }

    private fun headlineFor(readiness: ReadinessSnapshot, activities: List<DailyActivity>): String = when {
        readiness.isStale -> "Let's ease in while Spartan waits for today's data."
        // A pain or check-in day never gets "make today count" — the plan on it stays gentle.
        activities.any { it.ruleId == RuleIds.PAIN_DELOAD || it.ruleId == RuleIds.CLINICIAN_REFERRAL } ->
            "Take it gently today and notice how you feel."
        readiness.band == ReadinessBand.PRIMED -> "You're primed. Make today count."
        readiness.band == ReadinessBand.BALANCED -> "Balanced day. Steady, quality work."
        readiness.band == ReadinessBand.EASY -> "Go lighter today and protect recovery."
        else -> "Recovery first. Today is for rebuilding."
    }

    private fun safetyBannerFor(readiness: ReadinessSnapshot): String? = when {
        readiness.band == ReadinessBand.REST ->
            "Pushing hard on a low-recovery day rarely pays off. Keep intensity low and stop if anything hurts."
        else ->
            "This is wellness and fitness guidance, not medical advice. Stop and seek care for pain, illness, or unusual symptoms."
    }

    private companion object {
        /** Rules whose card is the day's main training session (or the pain / stale stand-in for it). */
        val TRAINING_RULES = setOf(
            RuleIds.GOOD_RECOVERY_GREENLIGHT, RuleIds.LOW_RECOVERY, RuleIds.PAIN_DELOAD, RuleIds.STALE_DATA_FALLBACK,
            RuleIds.CONCERNING_VITAL_EASY,
        )

        /**
         * The gentle habits a short plan is topped up with, in the order they are offered: morning
         * daylight, a stretch, slow breathing, water, a caffeine cutoff. Each keeps the metric it
         * belongs to in the playbook.
         */
        val TOP_UPS: List<Pair<MetricType, SuggestedAction>> = listOf(
            MetricType.SLEEP_PERFORMANCE to "morning-light",
            MetricType.DAY_STRAIN to "stretch-reset",
            MetricType.HRV_RMSSD to "breathing",
            MetricType.RESTING_HEART_RATE to "hydrate",
            MetricType.SLEEP_PERFORMANCE to "caffeine-cutoff",
        ).mapNotNull { (metric, key) -> ActionPlaybook.forMetric(metric).firstOrNull { it.key == key }?.let { metric to it } }
    }
}

/** Cards that are a workout, so "no morning workouts" applies to them. */
private val MOVEMENT_CATEGORIES = setOf(
    ActivityCategory.MOBILITY, ActivityCategory.ZONE2, ActivityCategory.STRENGTH, ActivityCategory.MOVEMENT,
)

/** Stable rule identifiers, carried on each activity for provenance and analytics. */
object RuleIds {
    const val LOW_RECOVERY = "LOW_RECOVERY"
    const val POOR_SLEEP = "POOR_SLEEP"
    const val HIGH_STRAIN_LOW_RECOVERY = "HIGH_STRAIN_LOW_RECOVERY"
    const val LOW_HRV_TREND = "LOW_HRV_TREND"
    const val ELEVATED_RHR_TREND = "ELEVATED_RHR_TREND"
    const val MISSED_GOAL = "MISSED_GOAL"
    const val GOOD_RECOVERY_GREENLIGHT = "GOOD_RECOVERY_GREENLIGHT"
    const val PAIN_DELOAD = "PAIN_DELOAD"
    const val HYDRATION_BASELINE = "HYDRATION_BASELINE"
    const val STALE_DATA_FALLBACK = "STALE_DATA_FALLBACK"
    const val CLINICIAN_REFERRAL = "CLINICIAN_REFERRAL"
    const val GOAL_FOCUS = "GOAL_FOCUS"
    const val SLEEP_ANCHOR = "SLEEP_ANCHOR"

    /** The easy walk that stands in for the band session on a good-recovery day with a concerning vital. */
    const val CONCERNING_VITAL_EASY = "CONCERNING_VITAL_EASY"

    /** A small, gentle habit that tops a short plan up to the daily minimum. */
    const val DAILY_HABIT = "DAILY_HABIT"
}

class RuleBasedRecommendationSource(
    private val safetyEngine: SafetyEngine = SafetyEngine(),
) : RecommendationSource {

    override fun recommend(readiness: ReadinessSnapshot, options: CoachingOptions): List<DailyActivity> {
        val out = mutableListOf<DailyActivity>()
        val day = readiness.dateEpochDay
        val avoid = options.constraints.avoid

        val referral = clinicianReferral(readiness, day)
        val concerning = referral != null
        referral?.let(out::add)

        if (options.painFlag) out += painDeload(day)

        // On a pain day the deload IS the training guidance, and on a stale day the gentle
        // fallback is — no band-based session on top of either. (Both caught by the CoachingGym:
        // a primed pain day used to green-light hard strength next to "gentle, comfortable
        // movement only", and a no-data day used to prescribe a moderate session anyway.)
        if (!options.painFlag && !readiness.isStale) {
            when {
                readiness.band == ReadinessBand.REST || readiness.band == ReadinessBand.EASY ->
                    out += lowRecoveryBlock(readiness, day)
                // A concerning vital takes even a primed day down to an easy walk — safety over strain —
                // so the check-in's "take it easy" and the strain target match the session planned.
                concerning -> out += easyCheckInSession(day)
                // A user who won't do strength training gets the steady aerobic session instead.
                readiness.band == ReadinessBand.PRIMED && AvoidOption.STRENGTH_TRAINING !in avoid ->
                    out += greenlight(readiness, day)
                else -> out += balancedSession(readiness, day)
            }
        }
        // Right after the training block, so the cap's first pass reaches tonight's anchor early.
        options.bedtime?.let { out += sleepAnchor(it, day, options.use24HourClock) }

        if (highStrainLowRecovery(readiness)) out += activeRecovery(day)
        if (poorSleep(readiness)) out += sleepHygiene(readiness, day)
        if (lowHrvTrend(readiness)) out += breathwork(readiness, day)
        if (elevatedRhrTrend(readiness)) out += rhrCheckIn(readiness, day)
        if (options.missedGoalYesterday) out += quickWin(day)
        if (readiness.isStale) out += staleFallback(day)

        out += hydration(day)
        // After every rule card: a goal card shares its slug with the matching rule card, so the
        // cap's de-dupe keeps the rule version (and its reasoning) when both apply.
        options.goalType?.let { goalFocus(it, day) }?.let { out += it }

        val shaped = out
            .filterNot { AvoidOption.BREATHWORK in avoid && it.category == ActivityCategory.BREATHWORK }
            .map { if (AvoidOption.MORNING_SESSIONS in avoid) awayFromMorning(it) else it }

        // Sanitize as we build so a bad string fails fast at the rule that produced it.
        return shaped.onEach { a ->
            safetyEngine.sanitize(a.title); safetyEngine.sanitize(a.whyItMatters)
            a.instructions.forEach(safetyEngine::sanitize); a.safetyNote?.let(safetyEngine::sanitize)
        }
    }

    // --- rule predicates -----------------------------------------------------
    private fun highStrainLowRecovery(r: ReadinessSnapshot): Boolean =
        (r.dayStrainPrior ?: 0.0) >= 14.0 && r.band in setOf(ReadinessBand.REST, ReadinessBand.EASY)

    private fun poorSleep(r: ReadinessSnapshot): Boolean =
        (r.sleepPerformance != null && r.sleepPerformance < 70) || (r.sleepDebtHours ?: 0.0) >= 1.5

    private fun lowHrvTrend(r: ReadinessSnapshot): Boolean =
        r.hrvVsBaseline != null && r.hrvMs != null && r.hrvVsBaseline <= -0.10 * (r.hrvMs - r.hrvVsBaseline)

    private fun elevatedRhrTrend(r: ReadinessSnapshot): Boolean =
        (r.rhrVsBaseline ?: 0.0) >= 5.0

    /** No morning workouts: optional movement cards move to midday; required ones keep their slot. */
    private fun awayFromMorning(a: DailyActivity): DailyActivity =
        if (a.priority != ActivityPriority.REQUIRED && a.category in MOVEMENT_CATEGORIES && a.bestTimeOfDay == TimeOfDay.MORNING) {
            a.copy(bestTimeOfDay = TimeOfDay.MIDDAY)
        } else {
            a
        }

    // --- activity builders ---------------------------------------------------
    private fun lowRecoveryBlock(r: ReadinessSnapshot, day: Long): List<DailyActivity> = listOf(
        activity(day, "mobility", "10-minute mobility flow", ActivityCategory.MOBILITY,
            ActivityPriority.REQUIRED, RuleIds.LOW_RECOVERY, MetricType.RECOVERY_SCORE,
            why = "Your recovery is low today, so gentle movement supports blood flow without adding strain.",
            steps = listOf("Move through hips, ankles, thoracic spine and shoulders.",
                "Slow, controlled reps — no forcing end range.", "Breathe easily throughout."),
            minutes = 10, intensity = Intensity.EASY, time = TimeOfDay.MORNING,
            note = "Keep it comfortable. Stop and rest if anything is painful."),
        activity(day, "zone2-walk", "15-minute easy walk", ActivityCategory.ZONE2,
            ActivityPriority.RECOMMENDED, RuleIds.LOW_RECOVERY, MetricType.RECOVERY_SCORE,
            why = "Easy Zone 2 movement aids recovery far better than intense training on a low-recovery day.",
            steps = listOf("Walk at a conversational pace.", "Nose-breathe if you can.",
                "Keep effort easy — you should be able to talk."),
            minutes = 15, intensity = Intensity.EASY, time = TimeOfDay.MIDDAY,
            note = "Skip hard training today; prioritize sleep tonight."),
    )

    private fun balancedSession(r: ReadinessSnapshot, day: Long): List<DailyActivity> = listOf(
        activity(day, "zone2", "25-minute Zone 2 session", ActivityCategory.ZONE2,
            ActivityPriority.RECOMMENDED, RuleIds.GOOD_RECOVERY_GREENLIGHT, MetricType.RECOVERY_SCORE,
            why = "Recovery is in a balanced range — steady aerobic work builds your base without overreaching.",
            steps = listOf("Choose walking, cycling, or rowing.", "Hold an easy, conversational pace.",
                "Finish feeling like you could keep going."),
            minutes = 25, intensity = Intensity.MODERATE, time = TimeOfDay.AFTERNOON, note = null),
    )

    /** The day's only session when a vital is outside the user's usual on a good-recovery day. */
    private fun easyCheckInSession(day: Long): DailyActivity = activity(day, "zone2-walk",
        "15-minute easy walk", ActivityCategory.ZONE2, ActivityPriority.RECOMMENDED,
        RuleIds.CONCERNING_VITAL_EASY, MetricType.RECOVERY_SCORE,
        why = "A reading is outside your usual range today, so easy movement keeps you active without adding strain.",
        steps = listOf("Walk at a conversational pace.", "Keep effort easy — you should be able to talk.",
            "Stop and rest if you feel unwell."),
        minutes = 15, intensity = Intensity.EASY, time = TimeOfDay.MIDDAY,
        note = "Skip hard training today and notice how you feel.")

    private fun greenlight(r: ReadinessSnapshot, day: Long): List<DailyActivity> = listOf(
        activity(day, "strength", "35-minute strength session", ActivityCategory.STRENGTH,
            ActivityPriority.RECOMMENDED, RuleIds.GOOD_RECOVERY_GREENLIGHT, MetricType.RECOVERY_SCORE,
            why = "You're primed today — a good window for a quality strength session with a little progression.",
            steps = listOf("Warm up 5 minutes.", "3–4 compound movements, clean form.",
                "Leave 2–3 reps in reserve on each set."),
            minutes = 35, intensity = Intensity.HARD, time = TimeOfDay.AFTERNOON,
            note = "Progress gradually. Stop or scale down if form breaks or pain appears."),
    )

    private fun activeRecovery(day: Long): DailyActivity = activity(day, "stretch",
        "10-minute stretch and reset", ActivityCategory.MOBILITY, ActivityPriority.RECOMMENDED,
        RuleIds.HIGH_STRAIN_LOW_RECOVERY, MetricType.DAY_STRAIN,
        why = "Yesterday's strain was high while recovery is low, so active recovery beats another hard session.",
        steps = listOf("Light full-body stretching.", "Optional easy walk instead of intensity.",
            "Hydrate and refuel with protein."),
        minutes = 10, intensity = Intensity.EASY, time = TimeOfDay.ANYTIME, note = null)

    private fun sleepHygiene(r: ReadinessSnapshot, day: Long): DailyActivity = activity(day, "winddown",
        "Sleep wind-down routine", ActivityCategory.SLEEP, ActivityPriority.RECOMMENDED,
        RuleIds.POOR_SLEEP, MetricType.SLEEP_PERFORMANCE,
        why = "Your recent sleep is below target — a consistent wind-down helps you fall asleep faster and sleep deeper.",
        steps = listOf("Set a caffeine cutoff by early afternoon.", "Dim screens 60 minutes before bed.",
            "Aim for a consistent bedtime tonight.", "Keep the room cool and dark."),
        minutes = 20, intensity = Intensity.REST, time = TimeOfDay.EVENING, note = null)

    private fun breathwork(r: ReadinessSnapshot, day: Long): DailyActivity = activity(day, "breathing",
        "5-minute breathing protocol", ActivityCategory.BREATHWORK, ActivityPriority.RECOMMENDED,
        RuleIds.LOW_HRV_TREND, MetricType.HRV_RMSSD,
        why = "Your HRV is trending below baseline, a sign of accumulated stress. Slow breathing helps you downshift.",
        steps = listOf("Sit comfortably.", "Inhale 4 seconds, exhale 6 seconds.",
            "Repeat for 5 minutes, relaxing the shoulders."),
        minutes = 5, intensity = Intensity.REST, time = TimeOfDay.EVENING, note = null)

    private fun rhrCheckIn(r: ReadinessSnapshot, day: Long): DailyActivity = activity(day, "hydrate-rest",
        "Hydration and easy-day check-in", ActivityCategory.HYDRATION, ActivityPriority.RECOMMENDED,
        RuleIds.ELEVATED_RHR_TREND, MetricType.RESTING_HEART_RATE,
        why = "Resting heart rate is up versus your baseline — often a cue to hydrate, ease intensity, and check sleep.",
        steps = listOf("Front-load water this morning.", "Keep training easy today.",
            "If you feel unwell, rest and monitor how you feel."),
        minutes = 5, intensity = Intensity.REST, time = TimeOfDay.MORNING, note = null)

    private fun quickWin(day: Long): DailyActivity = activity(day, "quickwin",
        "5-minute movement snack", ActivityCategory.MOVEMENT, ActivityPriority.OPTIONAL,
        RuleIds.MISSED_GOAL, null,
        why = "You missed yesterday's goal — a tiny, achievable win rebuilds momentum with zero pressure.",
        steps = listOf("Do 5 minutes of anything: a walk, mobility, or light bodyweight moves.",
            "The only goal is to start."),
        minutes = 5, intensity = Intensity.EASY, time = TimeOfDay.ANYTIME, note = null)

    private fun painDeload(day: Long): DailyActivity = activity(day, "pain-deload",
        "Gentle, pain-free movement only", ActivityCategory.RECOVERY, ActivityPriority.REQUIRED,
        RuleIds.PAIN_DELOAD, null,
        why = "You flagged pain — today is for gentle, comfortable movement while your body recovers.",
        steps = listOf("Choose only pain-free ranges and light effort.", "Skip anything that aggravates the area."),
        minutes = 10, intensity = Intensity.EASY, time = TimeOfDay.ANYTIME,
        note = "If pain is sharp, worsening, or persistent, consider seeing a qualified clinician.")

    private fun hydration(day: Long): DailyActivity = activity(day, "hydration",
        "Hydration reminder", ActivityCategory.HYDRATION, ActivityPriority.OPTIONAL,
        RuleIds.HYDRATION_BASELINE, MetricType.RECOVERY_SCORE,
        why = "Steady hydration supports recovery, HRV, and how you feel through the day.",
        steps = listOf("Drink a glass of water now.", "Keep water nearby and sip through the day."),
        minutes = 1, intensity = Intensity.REST, time = TimeOfDay.ANYTIME, note = null)

    /**
     * Tonight's anchor: "In bed by 10:40 PM" (or "22:40" on a 24-hour device). The why says it is the
     * user's own bedtime when it is — checked first, since a user bedtime can also know its hours —
     * or names the night it plans for: the estimated need, which already counts recent short nights
     * when there is debt.
     */
    private fun sleepAnchor(bedtime: BedtimePlan, day: Long, use24HourClock: Boolean): DailyActivity {
        val hours = bedtime.sleepHours
        val why = when {
            bedtime.fromUserBedtime ->
                "Your own bedtime from your plan settings — a steady bedtime is one of the strongest sleep habits."
            hours != null && bedtime.debtHours >= 0.25 ->
                "Gives you about ${formatHoursMinutes(hours)} of sleep — your estimated need, which already counts recent short nights."
            hours != null -> "Gives you about ${formatHoursMinutes(hours)} of sleep — your estimated need."
            else -> "A steady bedtime helps protect tomorrow's recovery."
        }
        return activity(day, "sleep-anchor", "In bed by ${clock(bedtime.lightsOutMinute, use24HourClock)}",
            ActivityCategory.SLEEP, ActivityPriority.RECOMMENDED, RuleIds.SLEEP_ANCHOR, MetricType.SLEEP_PERFORMANCE,
            why = why,
            steps = listOf("Start winding down at ${clock(bedtime.windDownMinute, use24HourClock)}.",
                "Keep the room cool, dark, and quiet."),
            minutes = 1, intensity = Intensity.REST, time = TimeOfDay.EVENING, note = null)
    }

    /**
     * One card for the user's primary goal, reusing the slug of the rule card with the same idea so
     * the two de-duplicate. Fitness has no extra card — the day's session already is the work.
     * (Weight goals have no preset, so no card either.)
     */
    private fun goalFocus(goalType: GoalType, day: Long): DailyActivity? {
        val preset = GoalPreset.forGoalType(goalType) ?: return null
        val lead = "Part of your ${preset.label} goal:"
        return when (preset) {
            GoalPreset.SLEEP_BETTER -> activity(day, "winddown", "Sleep wind-down routine", ActivityCategory.SLEEP,
                ActivityPriority.RECOMMENDED, RuleIds.GOAL_FOCUS, MetricType.SLEEP_PERFORMANCE,
                why = "$lead a consistent wind-down helps you fall asleep faster and sleep deeper.",
                steps = listOf("Dim screens 45 minutes before lights-out.", "Keep the room cool and dark.",
                    "Same bedtime as last night, give or take 30 minutes."),
                minutes = 20, intensity = Intensity.REST, time = TimeOfDay.EVENING, note = null)
            GoalPreset.FEEL_STEADIER -> activity(day, "breathing", "5-minute breathing protocol", ActivityCategory.BREATHWORK,
                ActivityPriority.RECOMMENDED, RuleIds.GOAL_FOCUS, MetricType.HRV_RMSSD,
                why = "$lead slow, exhale-led breathing is a simple daily way to practice downshifting.",
                steps = listOf("Sit comfortably.", "Inhale 4 seconds, exhale 6 seconds.",
                    "Repeat for 5 minutes, relaxing the shoulders."),
                minutes = 5, intensity = Intensity.REST, time = TimeOfDay.EVENING, note = null)
            GoalPreset.RECOVER_WELL -> activity(day, "mobility", "10-minute mobility flow", ActivityCategory.MOBILITY,
                ActivityPriority.RECOMMENDED, RuleIds.GOAL_FOCUS, MetricType.RECOVERY_SCORE,
                why = "$lead gentle range-of-motion work supports recovery without adding load.",
                steps = listOf("Move through hips, ankles, upper back, and shoulders.",
                    "Slow, controlled reps — never force the end range."),
                minutes = 10, intensity = Intensity.EASY, time = TimeOfDay.MORNING,
                note = "Keep it comfortable. Stop and rest if anything is painful.")
            GoalPreset.GET_FITTER -> null
        }
    }

    /** Clock time the way the rest of the app writes it: "10:40 PM", or "22:40" on a 24-hour device. */
    private fun clock(minuteOfDay: Int, use24HourClock: Boolean): String {
        val minute = Math.floorMod(minuteOfDay, 24 * 60)
        val pattern = if (use24HourClock) "HH:mm" else "h:mm a"
        return LocalTime.of(minute / 60, minute % 60).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    private fun staleFallback(day: Long): DailyActivity = activity(day, "connect-whoop",
        "Ease in with a mobility flow", ActivityCategory.MOBILITY, ActivityPriority.RECOMMENDED,
        RuleIds.STALE_DATA_FALLBACK, MetricType.RECOVERY_SCORE,
        why = "Today's WHOOP data isn't in yet. Here's a safe default while Spartan waits to personalize your plan.",
        steps = listOf("10 minutes of easy mobility.", "Connect or sync WHOOP for a tailored plan."),
        minutes = 10, intensity = Intensity.EASY, time = TimeOfDay.ANYTIME, note = null)

    /**
     * Emit a non-diagnostic clinician-referral nudge when a vital looks potentially concerning.
     * Never states a cause or diagnosis — only recommends professional guidance.
     */
    private fun clinicianReferral(r: ReadinessSnapshot, day: Long): DailyActivity? {
        val respHigh = r.respiratoryRate != null &&
            (r.respiratoryRate >= 25.0 ||
                (r.respiratoryRateBaseline != null && r.respiratoryRate - r.respiratoryRateBaseline >= 3.0))
        val rhrVeryHigh = r.restingHeartRate != null && r.restingHeartRate >= 100.0
        if (!respHigh && !rhrVeryHigh) return null
        val metric = if (respHigh) MetricType.RESPIRATORY_RATE else MetricType.RESTING_HEART_RATE
        // A carried-forward (stale) day still holds an earlier day's readings — never call them today's.
        val readings = if (r.isStale) "your most recent readings" else "today's readings"
        return activity(day, "clinician", "Check in with how you feel", ActivityCategory.RECOVERY,
            ActivityPriority.REQUIRED, RuleIds.CLINICIAN_REFERRAL, metric,
            why = "One or more of $readings is outside your usual range. This is not a diagnosis, and one reading is not a conclusion.",
            steps = listOf("Notice any symptoms like illness, unusual fatigue, or feeling unwell.",
                "Rest and take it easy today.",
                "If readings stay unusual or you have symptoms, consider consulting a qualified clinician."),
            minutes = 2, intensity = Intensity.REST, time = TimeOfDay.MORNING,
            note = "Spartan does not diagnose. For medical concerns, contact a qualified clinician.")
    }

    private fun activity(
        day: Long, slug: String, title: String, category: ActivityCategory,
        priority: ActivityPriority, ruleId: String, metric: MetricType?,
        why: String, steps: List<String>, minutes: Int, intensity: Intensity,
        time: TimeOfDay, note: String?,
    ): DailyActivity = DailyActivity(
        id = "$day:$slug", title = title, category = category, priority = priority,
        whyItMatters = why, relatedMetric = metric, instructions = steps,
        estimatedMinutes = minutes, intensity = intensity, bestTimeOfDay = time,
        ruleId = ruleId, safetyNote = note,
    )
}
