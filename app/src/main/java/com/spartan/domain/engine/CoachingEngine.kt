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

/**
 * Options that tune a day's plan without changing the readiness signal itself.
 */
data class CoachingOptions(
    val maxActivities: Int = 4,
    val missedGoalYesterday: Boolean = false,
    val painFlag: Boolean = false,
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
        val activities = prioritizeAndCap(raw, options.maxActivities)
        val plan = DailyPlan(
            dateEpochDay = readiness.dateEpochDay,
            headline = headlineFor(readiness),
            band = readiness.band,
            activities = activities,
            safetyBanner = safetyBannerFor(readiness),
            isMock = readiness.isMock,
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

    private fun headlineFor(readiness: ReadinessSnapshot): String = when {
        readiness.isStale -> "Let's ease in while Spartan waits for today's data."
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
}

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
}

class RuleBasedRecommendationSource(
    private val safetyEngine: SafetyEngine = SafetyEngine(),
) : RecommendationSource {

    override fun recommend(readiness: ReadinessSnapshot, options: CoachingOptions): List<DailyActivity> {
        val out = mutableListOf<DailyActivity>()
        val day = readiness.dateEpochDay

        val referral = clinicianReferral(readiness, day)
        val concerning = referral != null
        referral?.let(out::add)

        if (options.painFlag) out += painDeload(day)

        // On a pain day the deload IS the training guidance, and on a stale day the gentle
        // fallback is — no band-based session on top of either. (Both caught by the CoachingGym:
        // a primed pain day used to green-light hard strength next to "gentle, comfortable
        // movement only", and a no-data day used to prescribe a moderate session anyway.)
        if (!options.painFlag && !readiness.isStale) {
            when (readiness.band) {
                ReadinessBand.REST, ReadinessBand.EASY -> out += lowRecoveryBlock(readiness, day)
                // A concerning vital suppresses hard training even on a primed day — safety over strain.
                ReadinessBand.PRIMED -> out += if (concerning) balancedSession(readiness, day) else greenlight(readiness, day)
                ReadinessBand.BALANCED -> out += balancedSession(readiness, day)
            }
        }

        if (highStrainLowRecovery(readiness)) out += activeRecovery(day)
        if (poorSleep(readiness)) out += sleepHygiene(readiness, day)
        if (lowHrvTrend(readiness)) out += breathwork(readiness, day)
        if (elevatedRhrTrend(readiness)) out += rhrCheckIn(readiness, day)
        if (options.missedGoalYesterday) out += quickWin(day)
        if (readiness.isStale) out += staleFallback(day)

        out += hydration(day)

        // Sanitize as we build so a bad string fails fast at the rule that produced it.
        return out.onEach { a ->
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
        return activity(day, "clinician", "Check in with how you feel", ActivityCategory.RECOVERY,
            ActivityPriority.REQUIRED, RuleIds.CLINICIAN_REFERRAL, metric,
            why = "One or more of today's readings is outside your usual range. This is not a diagnosis, and one reading is not a conclusion.",
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
