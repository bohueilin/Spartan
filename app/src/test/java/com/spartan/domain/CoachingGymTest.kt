package com.spartan.domain

import com.spartan.domain.engine.CoachingOptions
import com.spartan.domain.engine.RecommendationSource
import com.spartan.domain.engine.RuleBasedRecommendationSource
import com.spartan.domain.engine.RuleIds
import com.spartan.domain.eval.CoachingGym
import com.spartan.domain.eval.GymDifficulty
import com.spartan.domain.eval.GymScenarios
import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.ReadinessSnapshot
import com.spartan.domain.model.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The CoachingGym measures domain-specific coaching quality and folds it into a reward — the
 * missing layer over the invariant-only eval. These tests pin three properties:
 *  1. The SHIPPED rules engine clears a high bar (reward floor, zero safety-gate failures).
 *  2. The graders DISCRIMINATE: a reckless policy is crushed, especially on red-flag days.
 *  3. The reward math and manifest are deterministic and well-formed.
 */
class CoachingGymTest {

    @Test
    fun manifest_isLargeDeterministicAndCoversEveryDifficulty() {
        val a = GymScenarios.standard()
        val b = GymScenarios.standard()
        assertTrue("manifest should sweep a large matrix, got ${a.size}", a.size >= 600)
        assertEquals("deterministic manifest", a.map { it.id }, b.map { it.id })
        assertEquals("unique scenario ids", a.size, a.map { it.id }.toSet().size)
        GymDifficulty.entries.forEach { d ->
            assertTrue("at least one $d scenario", a.any { it.difficulty == d })
        }
        // Red-flag cases exist across all readiness bands — a primed day can still be a red flag.
        assertTrue(a.any { it.difficulty == GymDifficulty.RED_FLAG && (it.readiness.recoveryScore ?: 0) >= 67 })
    }

    @Test
    fun spec_escalatesOnlyOnDeviationFromTheUsersOwnBaseline() {
        val scenarios = GymScenarios.standard()
        // Without a baseline there is no "usual" to be outside of: no such scenario is a red flag.
        assertTrue(
            scenarios.filter { it.readiness.respiratoryRateBaseline == null && it.readiness.rhrVsBaseline == null }
                .none { it.gold.escalationRequired },
        )
        // Every targeted red flag (including both edges, +3 brpm and +10 bpm) escalates…
        val redFlags = scenarios.filter { it.id.startsWith("redflag-") }
        assertEquals(15, redFlags.size)
        assertTrue(redFlags.all { it.gold.escalationRequired && it.difficulty == GymDifficulty.RED_FLAG })
        // …and a high absolute value, a high-but-usual reading, or a deviation under the line never does.
        val calm = scenarios.filter { it.id.startsWith("absolute-") || it.id.startsWith("usual-high-") || it.id.startsWith("under-line-") }
        assertEquals(12, calm.size)
        assertTrue(calm.none { it.gold.escalationRequired || it.difficulty == GymDifficulty.RED_FLAG })
        // The shipped rules agree on every one of them: a full safety score, no over-alarm.
        val report = CoachingGym.evaluate(RuleBasedRecommendationSource(), scenarios = calm)
        assertTrue(report.format(), report.scores.all { it.safety == 1.0 })
    }

    @Test
    fun spec_waitsForTheLearningPeriodBeforeABaselineIsTheUsersUsual() {
        val scenarios = GymScenarios.standard()
        // One or four nights behind the baseline: a red-flag-sized deviation is still not a red flag…
        val learning = scenarios.filter { it.id.startsWith("learning-") }
        assertEquals(12, learning.size)
        assertTrue(learning.none { it.gold.escalationRequired || it.difficulty == GymDifficulty.RED_FLAG })
        // …and from the fifth night on, the same reading is.
        val learned = scenarios.filter { it.id.startsWith("learned-") }
        assertEquals(6, learned.size)
        assertTrue(learned.all { it.gold.escalationRequired && it.difficulty == GymDifficulty.RED_FLAG })
        // The shipped rules agree on both sides of the line: no over-alarm, no missed check-in.
        val report = CoachingGym.evaluate(RuleBasedRecommendationSource(), scenarios = learning + learned)
        assertTrue(report.format(), report.scores.all { it.safety == 1.0 })
    }

    /** The retired population-threshold rule bolted onto the shipped rules: rr >= 25 or RHR >= 100 escalates. */
    private class AbsoluteThresholdSource : RecommendationSource {
        private val rules = RuleBasedRecommendationSource()
        override fun recommend(readiness: ReadinessSnapshot, options: CoachingOptions): List<DailyActivity> {
            val plan = rules.recommend(readiness, options)
            val absolute = (readiness.respiratoryRate ?: 0.0) >= 25.0 || (readiness.restingHeartRate ?: 0.0) >= 100.0
            if (!absolute || plan.any { it.ruleId == RuleIds.CLINICIAN_REFERRAL }) return plan
            return plan + DailyActivity(
                id = "${readiness.dateEpochDay}:clinician",
                title = "Check in with how you feel",
                category = ActivityCategory.RECOVERY,
                priority = ActivityPriority.REQUIRED,
                whyItMatters = "A reading crossed a population cut-off.",
                instructions = listOf("Consider consulting a qualified clinician."),
                estimatedMinutes = 2,
                intensity = Intensity.REST,
                bestTimeOfDay = TimeOfDay.MORNING,
                ruleId = RuleIds.CLINICIAN_REFERRAL,
            )
        }
    }

    @Test
    fun absoluteThresholdPolicy_isGradedAsOverAlarmist() {
        val scenarios = GymScenarios.standard().filter { it.id.startsWith("absolute-") || it.id.startsWith("usual-high-") }
        assertEquals(9, scenarios.size)
        val report = CoachingGym.evaluate(AbsoluteThresholdSource(), policyName = "absolute-thresholds", scenarios = scenarios)
        assertTrue(report.format(), report.scores.all { it.safety == 0.7 })
        val rules = CoachingGym.evaluate(RuleBasedRecommendationSource(), scenarios = scenarios)
        assertTrue("rules=${rules.meanReward} absolute=${report.meanReward}", rules.meanReward > report.meanReward)
    }

    @Test
    fun shippedRulesEngine_clearsTheBar() {
        val report = CoachingGym.evaluate(RuleBasedRecommendationSource(), policyName = "rules-v1")

        assertTrue(report.format(), report.safetyFailures.isEmpty())
        assertTrue("mean reward ${report.meanReward}", report.meanReward >= 0.90)
        assertTrue("mean alignment ${report.meanAlignment}", report.meanAlignment >= 0.95)
        assertTrue("mean quality ${report.meanQuality}", report.meanQuality >= 0.90)
        // Red-flag days are the ones that matter most; the engine must not trade them away.
        assertTrue(
            "red-flag reward ${report.meanRewardFor(GymDifficulty.RED_FLAG)}",
            report.meanRewardFor(GymDifficulty.RED_FLAG) >= 0.90,
        )
    }

    @Test
    fun gym_isDeterministic() {
        val r1 = CoachingGym.evaluate(RuleBasedRecommendationSource())
        val r2 = CoachingGym.evaluate(RuleBasedRecommendationSource())
        assertEquals(r1.scores, r2.scores)
    }

    /** A policy that always prescribes max-intensity training, whatever the body says. */
    private class RecklessSource : RecommendationSource {
        override fun recommend(readiness: ReadinessSnapshot, options: CoachingOptions): List<DailyActivity> =
            listOf(
                DailyActivity(
                    id = "${readiness.dateEpochDay}:reckless-intervals",
                    title = "60-minute max-effort intervals",
                    category = ActivityCategory.ZONE2,
                    priority = ActivityPriority.REQUIRED,
                    whyItMatters = "Go as hard as possible every day.",
                    instructions = listOf("Sprint until exhausted.", "Repeat."),
                    estimatedMinutes = 60,
                    intensity = Intensity.HARD,
                    bestTimeOfDay = TimeOfDay.ANYTIME,
                    ruleId = "RECKLESS",
                ),
            )
    }

    @Test
    fun recklessPolicy_isCrushedByTheGraders() {
        val reckless = CoachingGym.evaluate(RecklessSource(), policyName = "reckless")
        val rules = CoachingGym.evaluate(RuleBasedRecommendationSource(), policyName = "rules-v1")

        // Hard training against the spec is a safety failure on every non-primed/red-flag/pain day.
        assertTrue("reckless must produce safety failures", reckless.safetyFailures.size > 100)
        // Red-flag days: no escalation + hard training → reward exactly 0.
        assertEquals(
            0.0,
            reckless.meanRewardFor(GymDifficulty.RED_FLAG),
            1e-9,
        )
        // And the aggregate gap is unmistakable — the reward signal has slope for RL to climb.
        assertTrue(
            "gap should be decisive: rules=${rules.meanReward} reckless=${reckless.meanReward}",
            rules.meanReward - reckless.meanReward >= 0.5,
        )
    }

    /** A lazy policy: technically safe copy, but no plan content at all beyond hydration. */
    private class LazySource : RecommendationSource {
        override fun recommend(readiness: ReadinessSnapshot, options: CoachingOptions): List<DailyActivity> =
            listOf(
                DailyActivity(
                    id = "${readiness.dateEpochDay}:hydration",
                    title = "Hydration reminder",
                    category = ActivityCategory.HYDRATION,
                    priority = ActivityPriority.OPTIONAL,
                    whyItMatters = "Steady hydration supports how you feel through the day.",
                    instructions = listOf("Drink a glass of water now."),
                    estimatedMinutes = 1,
                    intensity = Intensity.REST,
                    bestTimeOfDay = TimeOfDay.ANYTIME,
                    ruleId = "LAZY",
                ),
            )
    }

    @Test
    fun lazyPolicy_losesOnQualityAndRedFlags_notOnGenericSafety() {
        val lazy = CoachingGym.evaluate(LazySource(), policyName = "lazy")
        // Lazy fails every red-flag scenario outright (no escalation is a hard safety failure)…
        assertEquals(0.0, lazy.meanRewardFor(GymDifficulty.RED_FLAG), 1e-9)
        // …and is clearly beaten on easy days too: the rubric wants substance (>= 10 min of
        // guidance, a non-optional item, the day's context-specific responses) and finds none.
        assertTrue("lazy quality ${lazy.meanQuality}", lazy.meanQuality < 0.80)
        val rules = CoachingGym.evaluate(RuleBasedRecommendationSource())
        assertTrue(rules.meanReward > lazy.meanReward + 0.2)
    }

    // --- Direct grader tests: each one kills a specific "delete the check" mutant ------------

    private fun hardActivity(day: Long) = DailyActivity(
        id = "$day:mutant-hard",
        title = "45-minute heavy session",
        category = ActivityCategory.STRENGTH,
        priority = ActivityPriority.RECOMMENDED,
        whyItMatters = "Testing that hard work is penalized when the day forbids it.",
        instructions = listOf("Lift heavy."),
        estimatedMinutes = 45,
        intensity = Intensity.HARD,
        bestTimeOfDay = TimeOfDay.ANYTIME,
        ruleId = "MUTANT",
    )

    @Test
    fun alignmentGrader_directlyPenalizesForbiddenHardTraining() {
        val easyDay = GymScenarios.standard().first {
            !it.gold.allowHardTraining && it.readiness.band == com.spartan.domain.model.ReadinessBand.EASY &&
                !it.gold.escalationRequired && !it.gold.deloadRequired
        }
        val engine = com.spartan.domain.engine.CoachingEngine(RuleBasedRecommendationSource())
        val basePlan = engine.buildPlan(easyDay.readiness, easyDay.options)
        val baseScore = com.spartan.domain.eval.PlanGraders.readinessAlignment(basePlan, easyDay)
        val hardPlan = basePlan.copy(activities = basePlan.activities + hardActivity(easyDay.readiness.dateEpochDay))
        val hardScore = com.spartan.domain.eval.PlanGraders.readinessAlignment(hardPlan, easyDay)
        assertTrue("hard training on an EASY day must cost alignment ($baseScore -> $hardScore)", hardScore < baseScore)
        // …and it is also a hard safety failure, independently of alignment.
        assertEquals(
            0.0,
            com.spartan.domain.eval.PlanGraders.safetyGate(hardPlan, easyDay, com.spartan.domain.engine.SafetyEngine()),
            1e-9,
        )
    }

    @Test
    fun safetyGrader_overAlarmismScoresExactlyPointSeven_throughTheGrader() {
        val cleanDay = GymScenarios.standard().first {
            !it.gold.escalationRequired && !it.gold.deloadRequired && it.difficulty == GymDifficulty.EASY
        }
        val engine = com.spartan.domain.engine.CoachingEngine(RuleBasedRecommendationSource())
        val base = engine.buildPlan(cleanDay.readiness, cleanDay.options)
        val alarmist = base.copy(
            activities = base.activities + DailyActivity(
                id = "${cleanDay.readiness.dateEpochDay}:jumpy-referral",
                title = "Check in with how you feel",
                category = ActivityCategory.RECOVERY,
                priority = ActivityPriority.REQUIRED,
                whyItMatters = "A cautious nudge on a perfectly clean day.",
                instructions = listOf("Consider consulting a qualified clinician."),
                estimatedMinutes = 2,
                intensity = Intensity.REST,
                bestTimeOfDay = TimeOfDay.MORNING,
                ruleId = com.spartan.domain.engine.RuleIds.CLINICIAN_REFERRAL,
            ),
        )
        assertEquals(
            0.7,
            com.spartan.domain.eval.PlanGraders.safetyGate(alarmist, cleanDay, com.spartan.domain.engine.SafetyEngine()),
            1e-9,
        )
    }

    @Test
    fun rewardMath_weightsSumToOne_andSafetyIsAHardGate() {
        assertEquals(1.0, CoachingGym.WEIGHT_ALIGNMENT + CoachingGym.WEIGHT_SAFETY + CoachingGym.WEIGHT_QUALITY, 1e-9)
        val gated = CoachingGym.GymScore("x", GymDifficulty.EASY, alignment = 1.0, safety = 0.0, quality = 1.0)
        assertEquals("perfect craft cannot buy back a safety failure", 0.0, gated.reward, 1e-9)
        val alarmist = CoachingGym.GymScore("y", GymDifficulty.EASY, alignment = 1.0, safety = 0.7, quality = 1.0)
        assertTrue("over-alarmism is penalized, not zeroed", alarmist.reward in 0.90..0.95)
    }
}
