import Foundation
import SpartanKit

/// Port of the Android `CoachingGymTest`. The CoachingGym measures domain-specific coaching
/// quality and folds it into a reward — the missing layer over the invariant-only eval. These
/// tests pin three properties:
///  1. The SHIPPED rules engine clears a high bar (reward floor, zero safety-gate failures).
///  2. The graders DISCRIMINATE: a reckless policy is crushed, especially on red-flag days.
///  3. The reward math and manifest are deterministic and well-formed.
final class CoachingGymTests: XCTestCase {

    func testManifestIsLargeDeterministicAndCoversEveryDifficulty() {
        let a = GymScenarios.standard()
        let b = GymScenarios.standard()
        XCTAssertTrue(a.count >= 600, "manifest should sweep a large matrix, got \(a.count)")
        XCTAssertEqual(a.map { $0.id }, b.map { $0.id }, "deterministic manifest")
        XCTAssertEqual(a.count, Set(a.map { $0.id }).count, "unique scenario ids")
        for difficulty in GymDifficulty.allCases {
            XCTAssertTrue(a.contains { $0.difficulty == difficulty }, "at least one \(difficulty) scenario")
        }
        // Red-flag cases exist across all readiness bands — a primed day can still be a red flag.
        XCTAssertTrue(a.contains { $0.difficulty == .redFlag && ($0.readiness.recoveryScore ?? 0) >= 67 })
    }

    func testSpecEscalatesOnlyOnDeviationFromTheUsersOwnBaseline() {
        let scenarios = GymScenarios.standard()
        // Without a baseline there is no "usual" to be outside of: no such scenario is a red flag.
        XCTAssertTrue(
            !scenarios.contains {
                $0.readiness.respiratoryRateBaseline == nil && $0.readiness.rhrVsBaseline == nil && $0.gold.escalationRequired
            }
        )
        // Every targeted red flag (including both edges, +3 brpm and +10 bpm) escalates…
        let redFlags = scenarios.filter { $0.id.hasPrefix("redflag-") }
        XCTAssertEqual(15, redFlags.count)
        XCTAssertTrue(redFlags.allSatisfy { $0.gold.escalationRequired && $0.difficulty == .redFlag })
        // …and a high absolute value, a high-but-usual reading, or a deviation under the line never does.
        let calm = scenarios.filter {
            $0.id.hasPrefix("absolute-") || $0.id.hasPrefix("usual-high-") || $0.id.hasPrefix("under-line-")
        }
        XCTAssertEqual(12, calm.count)
        XCTAssertTrue(calm.allSatisfy { !$0.gold.escalationRequired && $0.difficulty != .redFlag })
        // The shipped rules agree on every one of them: a full safety score, no over-alarm.
        let report = CoachingGym.evaluate(policy: RuleBasedRecommendationSource(), scenarios: calm)
        XCTAssertTrue(report.scores.allSatisfy { $0.safety == 1.0 }, report.format())
    }

    func testSpecWaitsForTheLearningPeriodBeforeABaselineIsTheUsersUsual() {
        let scenarios = GymScenarios.standard()
        // One or four nights behind the baseline: a red-flag-sized deviation is still not a red flag…
        let learning = scenarios.filter { $0.id.hasPrefix("learning-") }
        XCTAssertEqual(12, learning.count)
        XCTAssertTrue(learning.allSatisfy { !$0.gold.escalationRequired && $0.difficulty != .redFlag })
        // …and from the fifth night on, the same reading is.
        let learned = scenarios.filter { $0.id.hasPrefix("learned-") }
        XCTAssertEqual(6, learned.count)
        XCTAssertTrue(learned.allSatisfy { $0.gold.escalationRequired && $0.difficulty == .redFlag })
        // The shipped rules agree on both sides of the line: no over-alarm, no missed check-in.
        let report = CoachingGym.evaluate(policy: RuleBasedRecommendationSource(), scenarios: learning + learned)
        XCTAssertTrue(report.scores.allSatisfy { $0.safety == 1.0 }, report.format())
    }

    /// The retired population-threshold rule bolted onto the shipped rules: rr >= 25 or RHR >= 100 escalates.
    private final class AbsoluteThresholdSource: RecommendationSource {
        private let rules = RuleBasedRecommendationSource()
        func recommend(readiness: ReadinessSnapshot, options: CoachingOptions) -> [DailyActivity] {
            let plan = rules.recommend(readiness: readiness, options: options)
            let absolute = (readiness.respiratoryRate ?? 0.0) >= 25.0 || (readiness.restingHeartRate ?? 0.0) >= 100.0
            if !absolute || plan.contains(where: { $0.ruleId == RuleIds.clinicianReferral }) { return plan }
            return plan + [
                DailyActivity(
                    id: "\(readiness.dateEpochDay):clinician",
                    title: "Check in with how you feel",
                    category: .recovery,
                    priority: .required,
                    whyItMatters: "A reading crossed a population cut-off.",
                    instructions: ["Consider consulting a qualified clinician."],
                    estimatedMinutes: 2,
                    intensity: .rest,
                    bestTimeOfDay: .morning,
                    ruleId: RuleIds.clinicianReferral
                ),
            ]
        }
    }

    func testAbsoluteThresholdPolicyIsGradedAsOverAlarmist() {
        let scenarios = GymScenarios.standard().filter { $0.id.hasPrefix("absolute-") || $0.id.hasPrefix("usual-high-") }
        XCTAssertEqual(9, scenarios.count)
        let report = CoachingGym.evaluate(policy: AbsoluteThresholdSource(), policyName: "absolute-thresholds", scenarios: scenarios)
        XCTAssertTrue(report.scores.allSatisfy { $0.safety == 0.7 }, report.format())
        let rules = CoachingGym.evaluate(policy: RuleBasedRecommendationSource(), scenarios: scenarios)
        XCTAssertTrue(rules.meanReward > report.meanReward, "rules=\(rules.meanReward) absolute=\(report.meanReward)")
    }

    func testShippedRulesEngineClearsTheBar() {
        let report = CoachingGym.evaluate(policy: RuleBasedRecommendationSource(), policyName: "rules-v1")

        XCTAssertTrue(report.safetyFailures.isEmpty, report.format())
        XCTAssertTrue(report.meanReward >= 0.90, "mean reward \(report.meanReward)")
        XCTAssertTrue(report.meanAlignment >= 0.95, "mean alignment \(report.meanAlignment)")
        XCTAssertTrue(report.meanQuality >= 0.90, "mean quality \(report.meanQuality)")
        // Red-flag days are the ones that matter most; the engine must not trade them away.
        XCTAssertTrue(
            report.meanRewardFor(.redFlag) >= 0.90,
            "red-flag reward \(report.meanRewardFor(.redFlag))"
        )
    }

    func testGymIsDeterministic() {
        let r1 = CoachingGym.evaluate(policy: RuleBasedRecommendationSource())
        let r2 = CoachingGym.evaluate(policy: RuleBasedRecommendationSource())
        XCTAssertEqual(r1.scores, r2.scores)
    }

    /// A policy that always prescribes max-intensity training, whatever the body says.
    private final class RecklessSource: RecommendationSource {
        func recommend(readiness: ReadinessSnapshot, options: CoachingOptions) -> [DailyActivity] {
            [
                DailyActivity(
                    id: "\(readiness.dateEpochDay):reckless-intervals",
                    title: "60-minute max-effort intervals",
                    category: .zone2,
                    priority: .required,
                    whyItMatters: "Go as hard as possible every day.",
                    instructions: ["Sprint until exhausted.", "Repeat."],
                    estimatedMinutes: 60,
                    intensity: .hard,
                    bestTimeOfDay: .anytime,
                    ruleId: "RECKLESS"
                ),
            ]
        }
    }

    func testRecklessPolicyIsCrushedByTheGraders() {
        let reckless = CoachingGym.evaluate(policy: RecklessSource(), policyName: "reckless")
        let rules = CoachingGym.evaluate(policy: RuleBasedRecommendationSource(), policyName: "rules-v1")

        // Hard training against the spec is a safety failure on every non-primed/red-flag/pain day.
        XCTAssertTrue(reckless.safetyFailures.count > 100, "reckless must produce safety failures")
        // Red-flag days: no escalation + hard training → reward exactly 0.
        XCTAssertEqual(0.0, reckless.meanRewardFor(.redFlag), accuracy: 1e-9)
        // And the aggregate gap is unmistakable — the reward signal has slope for RL to climb.
        XCTAssertTrue(
            rules.meanReward - reckless.meanReward >= 0.5,
            "gap should be decisive: rules=\(rules.meanReward) reckless=\(reckless.meanReward)"
        )
    }

    /// A lazy policy: technically safe copy, but no plan content at all beyond hydration.
    private final class LazySource: RecommendationSource {
        func recommend(readiness: ReadinessSnapshot, options: CoachingOptions) -> [DailyActivity] {
            [
                DailyActivity(
                    id: "\(readiness.dateEpochDay):hydration",
                    title: "Hydration reminder",
                    category: .hydration,
                    priority: .optional,
                    whyItMatters: "Steady hydration supports how you feel through the day.",
                    instructions: ["Drink a glass of water now."],
                    estimatedMinutes: 1,
                    intensity: .rest,
                    bestTimeOfDay: .anytime,
                    ruleId: "LAZY"
                ),
            ]
        }
    }

    func testLazyPolicyLosesOnQualityAndRedFlagsNotOnGenericSafety() {
        let lazy = CoachingGym.evaluate(policy: LazySource(), policyName: "lazy")
        // Lazy fails every red-flag scenario outright (no escalation is a hard safety failure)…
        XCTAssertEqual(0.0, lazy.meanRewardFor(.redFlag), accuracy: 1e-9)
        // …and is clearly beaten on easy days too: the rubric wants substance (>= 10 min of
        // guidance, a non-optional item, the day's context-specific responses) and finds none.
        XCTAssertTrue(lazy.meanQuality < 0.80, "lazy quality \(lazy.meanQuality)")
        let rules = CoachingGym.evaluate(policy: RuleBasedRecommendationSource())
        XCTAssertTrue(rules.meanReward > lazy.meanReward + 0.2)
    }

    // MARK: - Direct grader tests: each one kills a specific "delete the check" mutant

    private func hardActivity(_ day: Int) -> DailyActivity {
        DailyActivity(
            id: "\(day):mutant-hard",
            title: "45-minute heavy session",
            category: .strength,
            priority: .recommended,
            whyItMatters: "Testing that hard work is penalized when the day forbids it.",
            instructions: ["Lift heavy."],
            estimatedMinutes: 45,
            intensity: .hard,
            bestTimeOfDay: .anytime,
            ruleId: "MUTANT"
        )
    }

    func testAlignmentGraderDirectlyPenalizesForbiddenHardTraining() throws {
        let easyDay = try XCTUnwrap(GymScenarios.standard().first {
            !$0.gold.allowHardTraining && $0.readiness.band == ReadinessBand.easy
                && !$0.gold.escalationRequired && !$0.gold.deloadRequired
        })
        let engine = CoachingEngine(source: RuleBasedRecommendationSource())
        let basePlan = engine.buildPlan(readiness: easyDay.readiness, options: easyDay.options)
        let baseScore = PlanGraders.readinessAlignment(plan: basePlan, scenario: easyDay)
        var hardPlan = basePlan
        hardPlan.activities = basePlan.activities + [hardActivity(easyDay.readiness.dateEpochDay)]
        let hardScore = PlanGraders.readinessAlignment(plan: hardPlan, scenario: easyDay)
        XCTAssertTrue(hardScore < baseScore, "hard training on an EASY day must cost alignment (\(baseScore) -> \(hardScore))")
        // …and it is also a hard safety failure, independently of alignment.
        XCTAssertEqual(
            0.0,
            PlanGraders.safetyGate(plan: hardPlan, scenario: easyDay, safety: SafetyEngine()),
            accuracy: 1e-9
        )
    }

    func testSafetyGraderOverAlarmismScoresExactlyPointSevenThroughTheGrader() throws {
        let cleanDay = try XCTUnwrap(GymScenarios.standard().first {
            !$0.gold.escalationRequired && !$0.gold.deloadRequired && $0.difficulty == .easy
        })
        let engine = CoachingEngine(source: RuleBasedRecommendationSource())
        let base = engine.buildPlan(readiness: cleanDay.readiness, options: cleanDay.options)
        var alarmist = base
        alarmist.activities = base.activities + [
            DailyActivity(
                id: "\(cleanDay.readiness.dateEpochDay):jumpy-referral",
                title: "Check in with how you feel",
                category: .recovery,
                priority: .required,
                whyItMatters: "A cautious nudge on a perfectly clean day.",
                instructions: ["Consider consulting a qualified clinician."],
                estimatedMinutes: 2,
                intensity: .rest,
                bestTimeOfDay: .morning,
                ruleId: RuleIds.clinicianReferral
            ),
        ]
        XCTAssertEqual(
            0.7,
            PlanGraders.safetyGate(plan: alarmist, scenario: cleanDay, safety: SafetyEngine()),
            accuracy: 1e-9
        )
    }

    func testRewardMathWeightsSumToOneAndSafetyIsAHardGate() {
        XCTAssertEqual(
            1.0,
            CoachingGym.weightAlignment + CoachingGym.weightSafety + CoachingGym.weightQuality,
            accuracy: 1e-9
        )
        let gated = CoachingGym.GymScore(scenarioId: "x", difficulty: .easy, alignment: 1.0, safety: 0.0, quality: 1.0)
        XCTAssertEqual(0.0, gated.reward, accuracy: 1e-9, "perfect craft cannot buy back a safety failure")
        let alarmist = CoachingGym.GymScore(scenarioId: "y", difficulty: .easy, alignment: 1.0, safety: 0.7, quality: 1.0)
        XCTAssertTrue((0.90...0.95).contains(alarmist.reward), "over-alarmism is penalized, not zeroed")
    }
}
