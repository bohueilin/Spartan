import Foundation
import SpartanKit

/// Mirrors the Android ProjectionEngine + MetricExplainers tests: modeling invariants
/// (week 0 anchored to today, numeric low <= high, floors/caps, flat at zero consistency,
/// determinism) plus full explainer coverage and the SafetyEngine sweep over every
/// user-facing string either feature can emit.
final class ProjectionAndExplainerChecks: XCTestCase {
    private let engine = ProjectionEngine()
    private let safety = SafetyEngine()

    private let tiers = [0, 1, 2, 3, 4, 5, 6, 7]

    private func projections(consistency: Int) -> [MetricProjection] {
        engine.project(
            restingHeartRate: 58.0,
            hrvMs: 62.0,
            recoveryScore: 55,
            consistencyDays7: consistency
        )
    }

    func testWeekZeroMatchesCurrentValueForEveryMetricAndTier() {
        for tier in tiers {
            for projection in projections(consistency: tier) {
                let first = projection.points[0]
                XCTAssertEqual(first.week, 0, "\(projection.label) tier \(tier): first point must be week 0")
                XCTAssertEqual(first.low, projection.currentValue, accuracy: 0.001,
                               "\(projection.label) tier \(tier): week-0 low must equal current")
                XCTAssertEqual(first.high, projection.currentValue, accuracy: 0.001,
                               "\(projection.label) tier \(tier): week-0 high must equal current")
            }
        }
    }

    func testWeeksRunZeroToEightInTwoWeekSteps() {
        for projection in projections(consistency: 5) {
            XCTAssertEqual(projection.points.map { $0.week }, [0, 2, 4, 6, 8],
                           "\(projection.label): weeks must be [0, 2, 4, 6, 8]")
        }
    }

    func testLowNeverExceedsHighAcrossAllTiersAndWeeks() {
        for tier in tiers {
            for projection in projections(consistency: tier) {
                for point in projection.points {
                    XCTAssertTrue(point.low <= point.high,
                                  "\(projection.label) tier \(tier) week \(point.week): low \(point.low) > high \(point.high)")
                }
            }
        }
    }

    func testRhrProjectionNeverGoesBelowFloor() {
        // current 48 -> floor = max(45, 40.8) = 45; the full-effect best case (48 - 6 = 42)
        // must clamp to 45, and the modest case (48 - 3 = 45) lands exactly on it.
        let result = engine.project(restingHeartRate: 48.0, hrvMs: nil, recoveryScore: nil, consistencyDays7: 7)
        XCTAssertEqual(result.count, 1)
        let rhr = result[0]
        XCTAssertEqual(rhr.metric, MetricType.restingHeartRate)
        XCTAssertFalse(rhr.higherIsBetter)
        for point in rhr.points {
            XCTAssertTrue(point.low >= 45.0, "week \(point.week): low \(point.low) fell below the 45 bpm floor")
        }
        let week8 = rhr.points[4]
        XCTAssertEqual(week8.low, 45.0, accuracy: 0.001)
        XCTAssertEqual(week8.high, 45.0, accuracy: 0.001)
    }

    func testHrvProjectionNeverExceedsCap() {
        // current 62 -> cap = 77.5; full-effect week-8 high is 62 * 1.15 = 71.3, inside the cap.
        let result = engine.project(restingHeartRate: nil, hrvMs: 62.0, recoveryScore: nil, consistencyDays7: 6)
        XCTAssertEqual(result.count, 1)
        let hrv = result[0]
        XCTAssertEqual(hrv.metric, MetricType.hrvRmssd)
        XCTAssertTrue(hrv.higherIsBetter)
        let cap = 62.0 * 1.25
        for point in hrv.points {
            XCTAssertTrue(point.high <= cap + 0.001, "week \(point.week): high \(point.high) exceeded cap \(cap)")
        }
        let week8 = hrv.points[4]
        XCTAssertEqual(week8.low, 65.1, accuracy: 0.001)  // 62 * 1.05
        XCTAssertEqual(week8.high, 71.3, accuracy: 0.001) // 62 * 1.15
    }

    func testRecoveryProjectionCapsAtNinety() {
        // current 85 -> best case 85 + 12 = 97 clamps to 90; modest case 85 + 5 = 90 exactly.
        let result = engine.project(restingHeartRate: nil, hrvMs: nil, recoveryScore: 85, consistencyDays7: 5)
        XCTAssertEqual(result.count, 1)
        let recovery = result[0]
        XCTAssertEqual(recovery.metric, MetricType.recoveryScore)
        XCTAssertTrue(recovery.higherIsBetter)
        for point in recovery.points {
            XCTAssertTrue(point.high <= 90.0 + 0.001, "week \(point.week): high \(point.high) exceeded the 90 cap")
        }
        let week8 = recovery.points[4]
        XCTAssertEqual(week8.low, 90.0, accuracy: 0.001)
        XCTAssertEqual(week8.high, 90.0, accuracy: 0.001)
    }

    func testZeroConsistencyHoldsFlat() {
        for projection in projections(consistency: 0) {
            for point in projection.points {
                XCTAssertEqual(point.low, projection.currentValue, accuracy: 0.001,
                               "\(projection.label) week \(point.week): flat tier must hold low at current")
                XCTAssertEqual(point.high, projection.currentValue, accuracy: 0.001,
                               "\(projection.label) week \(point.week): flat tier must hold high at current")
            }
        }
    }

    func testMoreConsistencyMeansAtLeastAsMuchProjectedChange() {
        // RHR at 70 bpm, week-8 best case: full 64.0 < 0.65x 66.1 < 0.35x 67.9 < flat 70.0.
        func week8Low(_ tier: Int) -> Double {
            engine.project(restingHeartRate: 70.0, hrvMs: nil, recoveryScore: nil, consistencyDays7: tier)[0].points[4].low
        }
        XCTAssertEqual(week8Low(6), 64.0, accuracy: 0.001)
        XCTAssertEqual(week8Low(4), 66.1, accuracy: 0.001)
        XCTAssertEqual(week8Low(2), 67.9, accuracy: 0.001)
        XCTAssertEqual(week8Low(0), 70.0, accuracy: 0.001)
        XCTAssertTrue(week8Low(6) < week8Low(4))
        XCTAssertTrue(week8Low(4) < week8Low(2))
        XCTAssertTrue(week8Low(2) < week8Low(0))
    }

    func testProjectionIsDeterministic() {
        let first = projections(consistency: 4)
        let second = projections(consistency: 4)
        XCTAssertEqual(first, second, "same inputs must always produce identical projections")
    }

    func testOnlyProvidedMetricsAreProjected() {
        let onlyRecovery = engine.project(restingHeartRate: nil, hrvMs: nil, recoveryScore: 70, consistencyDays7: 5)
        XCTAssertEqual(onlyRecovery.map { $0.metric }, [MetricType.recoveryScore])
        let none = engine.project(restingHeartRate: nil, hrvMs: nil, recoveryScore: nil, consistencyDays7: 5)
        XCTAssertTrue(none.isEmpty)
        let all = projections(consistency: 5)
        XCTAssertEqual(all.map { $0.metric },
                       [MetricType.restingHeartRate, MetricType.hrvRmssd, MetricType.recoveryScore])
    }

    func testExplainersCoverAllNineMetrics() {
        XCTAssertEqual(MetricExplainers.all.count, 9)
        XCTAssertEqual(Set(MetricExplainers.all.map { $0.metric }).count, 9, "explainer metrics must be unique")
        for type in MetricType.allCases {
            let explainer = MetricExplainers.forMetric(type)
            XCTAssertNotNil(explainer, "missing explainer for \(type.rawValue)")
            if let explainer = explainer {
                XCTAssertEqual(explainer.metric, type)
                XCTAssertFalse(explainer.title.isEmpty)
                XCTAssertFalse(explainer.whatItIs.isEmpty)
                XCTAssertFalse(explainer.whatMovesIt.isEmpty)
                XCTAssertFalse(explainer.whatGoodLooksLike.isEmpty)
                XCTAssertFalse(explainer.howSpartanUsesIt.isEmpty)
            }
        }
    }

    func testRespiratoryRateExplainerCarriesClinicianNote() throws {
        let explainer = try XCTUnwrap(MetricExplainers.forMetric(.respiratoryRate))
        XCTAssertTrue(explainer.howSpartanUsesIt.contains("clinician"),
                      "respiratory-rate explainer must carry the non-diagnostic clinician note")
        XCTAssertTrue(explainer.howSpartanUsesIt.contains("never diagnoses"),
                      "respiratory-rate explainer must state that Spartan does not diagnose")
    }

    func testEveryUserFacingStringPassesSafetyCheck() {
        XCTAssertTrue(safety.validateCopy(ProjectionEngine.disclaimer), "disclaimer failed safety check")
        for tier in tiers {
            for projection in projections(consistency: tier) {
                XCTAssertTrue(safety.validateCopy(projection.label), "label failed safety: '\(projection.label)'")
                XCTAssertTrue(safety.validateCopy(projection.assumption),
                              "assumption failed safety: '\(projection.assumption)'")
            }
        }
        for explainer in MetricExplainers.all {
            XCTAssertTrue(safety.validateCopy(explainer.title), "title failed safety: '\(explainer.title)'")
            XCTAssertTrue(safety.validateCopy(explainer.whatItIs), "whatItIs failed safety for \(explainer.title)")
            for item in explainer.whatMovesIt {
                XCTAssertTrue(safety.validateCopy(item), "whatMovesIt failed safety: '\(item)'")
            }
            XCTAssertTrue(safety.validateCopy(explainer.whatGoodLooksLike),
                          "whatGoodLooksLike failed safety for \(explainer.title)")
            XCTAssertTrue(safety.validateCopy(explainer.howSpartanUsesIt),
                          "howSpartanUsesIt failed safety for \(explainer.title)")
        }
    }
}
