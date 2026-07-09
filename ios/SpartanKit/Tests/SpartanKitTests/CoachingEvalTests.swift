import XCTest
@testable import SpartanKit

/// Evaluation harness for the rules-based coaching engine. Rather than a handful of examples, this
/// sweeps a large matrix of readiness inputs and asserts a set of INVARIANTS that must hold for every
/// generated plan — the same way an LLM/agent eval asserts properties over many samples. It exists to
/// catch coaching-quality regressions (unsafe intensity, missing safety copy, unbounded plans,
/// non-determinism) before they ship. Port of the Android `CoachingEvalTest`.
final class CoachingEvalTests: XCTestCase {
    private let engine = CoachingEngine()
    private let safety = SafetyEngine()
    private let day = 20_000

    private func snap(
        recovery: Int? = 60,
        hrv: Double? = 60.0,
        hrvBase: Double? = nil,
        rhr: Double? = 55.0,
        rhrBase: Double? = nil,
        sleepPerf: Int? = 85,
        sleepDebt: Double? = 0.0,
        strainPrior: Double? = 8.0,
        resp: Double? = 15.0,
        respBase: Double? = nil,
        mock: Bool = false
    ) -> ReadinessSnapshot {
        var hrvVsBaseline: Double? = nil
        if let hrv = hrv, let base = hrvBase { hrvVsBaseline = hrv - base }
        var rhrVsBaseline: Double? = nil
        if let rhr = rhr, let base = rhrBase { rhrVsBaseline = rhr - base }
        return ReadinessSnapshot(
            dateEpochDay: day,
            recoveryScore: recovery,
            hrvMs: hrv,
            hrvVsBaseline: hrvVsBaseline,
            restingHeartRate: rhr,
            rhrVsBaseline: rhrVsBaseline,
            sleepPerformance: sleepPerf,
            sleepDebtHours: sleepDebt,
            dayStrainPrior: strainPrior,
            respiratoryRate: resp,
            respiratoryRateBaseline: respBase,
            band: ReadinessBand.fromRecovery(recovery),
            isStale: recovery == nil,
            isMock: mock
        )
    }

    private func isBlank(_ s: String) -> Bool {
        s.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    /// The universal invariants every plan must satisfy.
    private func assertInvariants(
        _ r: ReadinessSnapshot,
        options: CoachingOptions = CoachingOptions(),
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let plan = engine.buildPlan(readiness: r, options: options)
        let recoveryText = r.recoveryScore.map { String($0) } ?? "nil"
        let sleepText = r.sleepPerformance.map { String($0) } ?? "nil"
        let strainText = r.dayStrainPrior.map { String($0) } ?? "nil"
        let respText = r.respiratoryRate.map { String($0) } ?? "nil"
        let ctx = "recovery=\(recoveryText) band=\(r.band) sleep=\(sleepText) strain=\(strainText) resp=\(respText)"

        XCTAssertTrue(!plan.activities.isEmpty, "[\(ctx)] plan is never empty", file: file, line: line)

        let requiredCount = plan.activities.filter { $0.priority == .required }.count
        XCTAssertTrue(
            plan.activities.count <= max(options.maxActivities, requiredCount),
            "[\(ctx)] plan bounded to maxActivities (or required set)",
            file: file, line: line
        )
        XCTAssertTrue(plan.totalEstimatedMinutes > 0, "[\(ctx)] total minutes positive", file: file, line: line)

        // Hard training is only ever green-lit on a primed day with no concerning vital.
        if r.band != .primed {
            XCTAssertTrue(
                !plan.activities.contains { $0.intensity == .hard },
                "[\(ctx)] no HARD intensity off a primed day",
                file: file, line: line
            )
        }

        // Every card is well-formed and passes the safety sanitizer (no medical over-claiming).
        for a in plan.activities {
            XCTAssertTrue(!isBlank(a.title), "[\(ctx)] non-blank title", file: file, line: line)
            XCTAssertTrue(!isBlank(a.whyItMatters), "[\(ctx)] non-blank why", file: file, line: line)
            XCTAssertTrue(!isBlank(a.ruleId), "[\(ctx)] non-blank ruleId", file: file, line: line)
            XCTAssertTrue((1...90).contains(a.estimatedMinutes), "[\(ctx)] sane duration", file: file, line: line)
            XCTAssertTrue(safety.validateCopy(a.title), "[\(ctx)] safe title: \(a.title)", file: file, line: line)
            XCTAssertTrue(safety.validateCopy(a.whyItMatters), "[\(ctx)] safe why", file: file, line: line)
            for step in a.instructions {
                XCTAssertTrue(safety.validateCopy(step), "[\(ctx)] safe step", file: file, line: line)
            }
            if let note = a.safetyNote {
                XCTAssertTrue(safety.validateCopy(note), "[\(ctx)] safe note", file: file, line: line)
            }
        }
        XCTAssertTrue(safety.validateCopy(plan.headline), "[\(ctx)] safe headline", file: file, line: line)
        if let banner = plan.safetyBanner {
            XCTAssertTrue(safety.validateCopy(banner), "[\(ctx)] safe banner", file: file, line: line)
        }

        // Ids are unique and generation is deterministic.
        XCTAssertEqual(
            plan.activities.count, Set(plan.activities.map { $0.id }).count,
            "[\(ctx)] unique activity ids",
            file: file, line: line
        )
        let again = engine.buildPlan(readiness: r, options: options)
        XCTAssertEqual(
            plan.activities.map { $0.id }, again.activities.map { $0.id },
            "[\(ctx)] deterministic",
            file: file, line: line
        )
    }

    func testEvalInvariantsHoldAcrossFullReadinessMatrix() {
        var evaluated = 0
        for recovery in stride(from: 0, through: 100, by: 5) {
            for sleepPerf in [35, 60, 80, 100] {
                for strain in [3.0, 12.0, 18.0] {
                    for resp in [14.0, 21.0, 27.0] {
                        assertInvariants(snap(recovery: recovery, sleepPerf: sleepPerf, strainPrior: strain, resp: resp))
                        evaluated += 1
                    }
                }
            }
        }
        XCTAssertTrue(evaluated >= 500, "eval should cover a large matrix")
    }

    func testEvalInvariantsHoldWithOptionAndTrendVariations() {
        let options = [
            CoachingOptions(),
            CoachingOptions(maxActivities: 2),
            CoachingOptions(maxActivities: 6, painFlag: true),
            CoachingOptions(missedGoalYesterday: true),
            CoachingOptions(missedGoalYesterday: true, painFlag: true),
        ]
        let recoveries: [Int?] = [15, 40, 55, 72, 90, nil]
        for recovery in recoveries {
            for o in options {
                assertInvariants(
                    snap(recovery: recovery, hrv: 45.0, hrvBase: 70.0, rhr: 66.0, rhrBase: 55.0),
                    options: o
                )
            }
        }
    }

    func testEvalConcerningVitalsNeverGreenlightHardTraining() {
        for recovery in [20, 55, 85] {
            let highResp = engine.buildPlan(readiness: snap(recovery: recovery, resp: 28.0))
            XCTAssertTrue(
                !highResp.activities.contains { $0.intensity == .hard },
                "high respiratory rate must not green-light HARD"
            )
            XCTAssertTrue(highResp.activities.contains {
                $0.ruleId == RuleIds.clinicianReferral && $0.priority == .required
            })

            let highRhr = engine.buildPlan(readiness: snap(recovery: recovery, rhr: 105.0))
            XCTAssertTrue(
                !highRhr.activities.contains { $0.intensity == .hard },
                "very high resting HR must not green-light HARD"
            )
            XCTAssertTrue(highRhr.activities.contains { $0.ruleId == RuleIds.clinicianReferral })
        }
    }

    func testEvalLowRecoveryAlwaysCarriesARequiredRecoveryAction() {
        for recovery in 0...33 {
            let plan = engine.buildPlan(readiness: snap(recovery: recovery))
            XCTAssertTrue(
                plan.activities.contains { $0.priority == .required },
                "recovery=\(recovery) must include a REQUIRED action"
            )
        }
    }

    func testEvalMaxActivitiesRespectedWhenNoRequiredOverflow() {
        // A clean balanced day has no forced REQUIRED items, so the cap must hold exactly.
        let plan = engine.buildPlan(
            readiness: snap(recovery: 60, resp: 15.0),
            options: CoachingOptions(maxActivities: 2)
        )
        XCTAssertTrue(plan.activities.count <= 2)
    }
}
