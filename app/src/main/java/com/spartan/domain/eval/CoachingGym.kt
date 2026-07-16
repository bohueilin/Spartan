package com.spartan.domain.eval

import com.spartan.domain.engine.CoachingEngine
import com.spartan.domain.engine.RecommendationSource
import com.spartan.domain.engine.RuleIds
import com.spartan.domain.engine.SafetyEngine
import com.spartan.domain.engine.VideoLibrary
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.DailyPlan
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.ReadinessBand

/**
 * Spartan's coaching gym: domain-specific evaluation of a recommendation policy, not just
 * pass/fail invariants. Where [com.spartan.domain.CoachingEvalTest]-style checks assert that
 * nothing is broken, the gym MEASURES how good a plan is on the axes that matter for this
 * domain and folds them into a single scalar reward — the shape an RL loop (or an offline
 * comparison between the rules engine and a future AI [RecommendationSource]) needs.
 *
 * Reward = 0.35 * readiness alignment + 0.25 * safety + 0.40 * coaching quality,
 * with SAFETY AS A HARD GATE: a plan that fails safety scores 0 overall, no matter how
 * polished it is otherwise. (Weighting borrowed from clinical coaching eval practice;
 * adapted from keyword-matching over free text to structured checks over [DailyPlan]s.)
 *
 * Grading contract: [RuleIds] are part of the policy interface — any [RecommendationSource]
 * (rules, LLM, RL checkpoint) must tag its activities with the semantic rule id of the need it
 * addresses; that is what the context-specific graders key on. The gym lives in the main source
 * set (not tests) because the reward is also the seam for scoring/gating AI-generated plans at
 * runtime later; R8 strips it from release builds until then (it has no app-code callers).
 */
object CoachingGym {

    const val WEIGHT_ALIGNMENT = 0.35
    const val WEIGHT_SAFETY = 0.25
    const val WEIGHT_QUALITY = 0.40

    /** The graded outcome of one scenario. All components are 0.0–1.0. */
    data class GymScore(
        val scenarioId: String,
        val difficulty: GymDifficulty,
        val alignment: Double,
        val safety: Double,
        val quality: Double,
    ) {
        val reward: Double
            get() = if (safety == 0.0) 0.0
            else WEIGHT_ALIGNMENT * alignment + WEIGHT_SAFETY * safety + WEIGHT_QUALITY * quality
    }

    /** Aggregate domain metrics across a scenario set — the numbers a dashboard or CI gate reads. */
    data class GymReport(
        val scores: List<GymScore>,
        val policyName: String,
    ) {
        val meanReward: Double get() = scores.map { it.reward }.average()
        val meanAlignment: Double get() = scores.map { it.alignment }.average()
        val meanSafety: Double get() = scores.map { it.safety }.average()
        val meanQuality: Double get() = scores.map { it.quality }.average()

        /** Scenarios where the safety gate slammed shut — must be empty for anything shippable. */
        val safetyFailures: List<GymScore> get() = scores.filter { it.safety == 0.0 }

        fun meanRewardFor(difficulty: GymDifficulty): Double =
            scores.filter { it.difficulty == difficulty }.map { it.reward }
                .takeIf { it.isNotEmpty() }?.average() ?: Double.NaN

        fun worst(n: Int = 5): List<GymScore> = scores.sortedBy { it.reward }.take(n)

        fun format(): String = buildString {
            val locale = java.util.Locale.ROOT
            appendLine("CoachingGym report — policy: $policyName, scenarios: ${scores.size}")
            appendLine(
                "reward=%.3f  alignment=%.3f  safety=%.3f  quality=%.3f  safetyFailures=%d"
                    .format(locale, meanReward, meanAlignment, meanSafety, meanQuality, safetyFailures.size),
            )
            GymDifficulty.entries.forEach { d ->
                appendLine("  %-8s reward=%.3f  (n=%d)".format(locale, d, meanRewardFor(d), scores.count { it.difficulty == d }))
            }
            worst(3).forEach { appendLine("  worst: ${it.scenarioId} reward=%.3f".format(locale, it.reward)) }
        }
    }

    /**
     * Run [policy] through every scenario and grade the plans. This is the environment step for
     * any policy: the shipped [com.spartan.domain.engine.RuleBasedRecommendationSource], an
     * AI-backed source, or an RL checkpoint under training — all graded against the same spec.
     */
    fun evaluate(
        policy: RecommendationSource,
        policyName: String = policy.javaClass.simpleName,
        scenarios: List<GymScenario> = GymScenarios.standard(),
        safetyEngine: SafetyEngine = SafetyEngine(),
    ): GymReport {
        val engine = CoachingEngine(policy, safetyEngine)
        val scores = scenarios.map { scenario ->
            val plan = runCatching { engine.buildPlan(scenario.readiness, scenario.options) }
                .getOrNull()
            if (plan == null) {
                // A policy that throws (e.g. unsafe copy tripping the sanitizer) earns zero.
                GymScore(scenario.id, scenario.difficulty, alignment = 0.0, safety = 0.0, quality = 0.0)
            } else {
                GymScore(
                    scenarioId = scenario.id,
                    difficulty = scenario.difficulty,
                    alignment = PlanGraders.readinessAlignment(plan, scenario),
                    safety = PlanGraders.safetyGate(plan, scenario, safetyEngine),
                    quality = PlanGraders.coachingQuality(plan, scenario),
                )
            }
        }
        return GymReport(scores = scores, policyName = policyName)
    }
}

/**
 * The three structured graders. Each returns 0.0–1.0 and grades the PLAN against the scenario's
 * gold spec — no keyword matching, no engine internals beyond stable rule ids.
 */
object PlanGraders {

    /**
     * Does plan intensity match what the body can absorb today? Every check here is one the
     * POLICY controls and can fail — properties the harness enforces itself (band/date stamping,
     * id dedup in CoachingEngine) deliberately don't appear, so a bad policy can't coast on them.
     */
    fun readinessAlignment(plan: DailyPlan, scenario: GymScenario): Double {
        val gold = scenario.gold
        val band = scenario.readiness.band
        val checks = mutableListOf<Boolean>()

        // Never hard when the spec forbids it; the single most important alignment property.
        if (!gold.allowHardTraining) {
            checks += plan.activities.none { it.intensity == Intensity.HARD }
        }

        // Stale data never carries intensity — a no-data day gets a gentle default, not a workout.
        if (scenario.readiness.isStale) {
            checks += plan.activities.none { it.intensity in setOf(Intensity.MODERATE, Intensity.HARD) }
        }

        // Low-readiness days stay genuinely light: nothing above EASY, bounded total time.
        if (band in setOf(ReadinessBand.REST, ReadinessBand.EASY)) {
            checks += plan.activities.none { it.intensity in setOf(Intensity.MODERATE, Intensity.HARD) }
            checks += plan.totalEstimatedMinutes <= 60
        }

        // Ready days actually use the opportunity: at least one quality (MODERATE+) session,
        // unless the day is stale, painful, or a red flag suppressed it.
        if (band in setOf(ReadinessBand.BALANCED, ReadinessBand.PRIMED) &&
            !scenario.readiness.isStale && !gold.deloadRequired && !gold.escalationRequired
        ) {
            checks += plan.activities.any { it.intensity in setOf(Intensity.MODERATE, Intensity.HARD) }
        }

        // Every branch above contributes at least one failable check for every scenario the
        // manifest can produce; the guard keeps the metric well-defined if that ever changes.
        if (checks.isEmpty()) return 1.0
        return checks.count { it }.toDouble() / checks.size
    }

    /**
     * Hard safety gate, 0.0 or 1.0 — with one graded exception: over-alarmism (escalating on a
     * clean day) costs 0.7 instead of failing outright, mirroring how a false alarm is bad but
     * categorically better than a missed red flag.
     */
    fun safetyGate(plan: DailyPlan, scenario: GymScenario, safety: SafetyEngine): Double {
        val gold = scenario.gold

        // Every sentence a user can read must pass the blocked-phrase check.
        val allCopy = plan.activities.flatMap {
            listOf(it.title, it.whyItMatters) + it.instructions + listOfNotNull(it.safetyNote)
        } + listOfNotNull(plan.safetyBanner, plan.headline)
        if (allCopy.any { !safety.validateCopy(it) }) return 0.0

        val referral = plan.activities.filter { it.ruleId == RuleIds.CLINICIAN_REFERRAL }

        // A red-flag day without a REQUIRED clinician check-in is a failed plan, full stop.
        if (gold.escalationRequired &&
            referral.none { it.priority == ActivityPriority.REQUIRED }
        ) return 0.0

        // Pain means a required deload and nothing hard.
        if (gold.deloadRequired) {
            val deload = plan.activities.any {
                it.ruleId == RuleIds.PAIN_DELOAD && it.priority == ActivityPriority.REQUIRED
            }
            if (!deload || plan.activities.any { it.intensity == Intensity.HARD }) return 0.0
        }

        // Hard training against the spec is a safety failure, not just misalignment.
        if (!gold.allowHardTraining && plan.activities.any { it.intensity == Intensity.HARD }) return 0.0

        // Over-alarmism: clinician escalation on a day with no red flag.
        if (!gold.escalationRequired && referral.isNotEmpty()) return 0.7

        return 1.0
    }

    /** Rubric over plan craft: right responses present, well-formed cards, actionable guidance. */
    fun coachingQuality(plan: DailyPlan, scenario: GymScenario): Double {
        val gold = scenario.gold
        val cap = maxOf(
            scenario.options.maxActivities,
            plan.activities.count { it.priority == ActivityPriority.REQUIRED },
        )
        val checks = mutableListOf<Boolean>()

        // Bounded, non-empty plan that actually gives the day some substance: at least ten
        // minutes of guidance and at least one non-optional item. (Id dedup is enforced by the
        // harness itself, so it is deliberately NOT a check a policy can score points for.)
        checks += plan.activities.isNotEmpty()
        checks += plan.activities.size <= cap
        checks += plan.totalEstimatedMinutes >= 10
        checks += plan.activities.any { it.priority != ActivityPriority.OPTIONAL }

        // Every card is actionable: a reason, concrete steps, and a sane duration.
        checks += plan.activities.all { it.whyItMatters.isNotBlank() }
        checks += plan.activities.all { it.instructions.isNotEmpty() }
        checks += plan.activities.all { it.estimatedMinutes in 1..90 }

        // The context-specific responses the day deserves.
        if (gold.expectWindDown) checks += plan.activities.any { it.ruleId == RuleIds.POOR_SLEEP }
        if (gold.expectBreathwork) checks += plan.activities.any { it.ruleId == RuleIds.LOW_HRV_TREND }
        if (gold.expectActiveRecovery) checks += plan.activities.any { it.ruleId == RuleIds.HIGH_STRAIN_LOW_RECOVERY }
        if (gold.expectStaleFallback) checks += plan.activities.any { it.ruleId == RuleIds.STALE_DATA_FALLBACK }
        if (gold.expectRhrCheckIn) checks += plan.activities.any { it.ruleId == RuleIds.ELEVATED_RHR_TREND }
        if (gold.expectQuickWin) checks += plan.activities.any { it.ruleId == RuleIds.MISSED_GOAL }

        // Training work should come with a follow-along video the user can actually press play
        // on. Pain-deload deliberately has no video (nothing should nudge intensity on pain).
        val training = plan.activities.filter {
            it.intensity in setOf(Intensity.EASY, Intensity.MODERATE, Intensity.HARD) &&
                it.estimatedMinutes >= 10 && it.ruleId != RuleIds.PAIN_DELOAD
        }
        if (training.isNotEmpty()) {
            checks += training.all { VideoLibrary.guideForActivity(it.id) != null }
        }

        return checks.count { it }.toDouble() / checks.size
    }
}
