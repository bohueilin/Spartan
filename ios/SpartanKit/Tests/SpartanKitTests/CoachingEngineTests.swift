import XCTest
@testable import SpartanKit

/// Port of the Android `CoachingEngineTest`: same scenarios, same thresholds, same expectations.
final class CoachingEngineTests: XCTestCase {
    private let engine = CoachingEngine()
    private let day = 20_000

    private func readiness(
        recovery: Int? = 60, hrv: Double? = 60.0, rhr: Double? = 55.0,
        sleepPerf: Int? = 90, sleepDebt: Double? = 0.0, strainPrior: Double? = 8.0,
        resp: Double? = 15.0, hrvBase: Double? = nil, rhrBase: Double? = nil, respBase: Double? = nil
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
            isStale: recovery == nil
        )
    }

    func testBandThresholdsMatchSpec() {
        XCTAssertEqual(ReadinessBand.primed, ReadinessBand.fromRecovery(67))
        XCTAssertEqual(ReadinessBand.balanced, ReadinessBand.fromRecovery(50))
        XCTAssertEqual(ReadinessBand.easy, ReadinessBand.fromRecovery(34))
        XCTAssertEqual(ReadinessBand.rest, ReadinessBand.fromRecovery(33))
        XCTAssertEqual(ReadinessBand.balanced, ReadinessBand.fromRecovery(nil))
    }

    func testLowRecoveryProducesRequiredRecoveryAndNoHardTraining() {
        let plan = engine.buildPlan(readiness: readiness(recovery: 25))
        XCTAssertEqual(ReadinessBand.rest, plan.band)
        XCTAssertTrue(plan.activities.contains {
            $0.priority == .required && $0.ruleId == RuleIds.lowRecovery
        })
        XCTAssertFalse(
            plan.activities.contains { $0.intensity == .hard },
            "no HARD session on a low-recovery day"
        )
    }

    func testPrimedGreenlightsAQualityStrengthSession() {
        let plan = engine.buildPlan(readiness: readiness(recovery: 80))
        XCTAssertEqual(ReadinessBand.primed, plan.band)
        XCTAssertTrue(plan.activities.contains {
            $0.ruleId == RuleIds.goodRecoveryGreenlight && $0.intensity == .hard
        })
    }

    func testPoorSleepAddsSleepHygiene() {
        let plan = engine.buildPlan(readiness: readiness(recovery: 55, sleepPerf: 55))
        XCTAssertTrue(plan.activities.contains { $0.ruleId == RuleIds.poorSleep })
    }

    func testElevatedRhrTrendAddsCheckIn() {
        let plan = engine.buildPlan(readiness: readiness(recovery: 55, rhr: 66.0, rhrBase: 58.0))
        XCTAssertTrue(plan.activities.contains { $0.ruleId == RuleIds.elevatedRhrTrend })
    }

    func testConcerningRespiratoryRateAddsNonDiagnosticClinicianReferral() throws {
        let plan = engine.buildPlan(readiness: readiness(recovery: 55, resp: 27.0))
        let referral = try XCTUnwrap(
            plan.activities.first { $0.ruleId == RuleIds.clinicianReferral }
        )
        XCTAssertEqual(ActivityPriority.required, referral.priority)
        XCTAssertTrue(
            referral.instructions.joined(separator: " ").lowercased().contains("clinician")
        )
    }

    func testPlanRespectsMaxActivitiesButKeepsRequiredAndReferral() {
        let plan = engine.buildPlan(
            readiness: readiness(recovery: 20, rhr: 70.0, sleepPerf: 40, resp: 28.0, rhrBase: 58.0),
            options: CoachingOptions(maxActivities: 3, painFlag: true)
        )
        let requiredCount = plan.activities.filter { $0.priority == .required }.count
        XCTAssertTrue(plan.activities.count <= max(3, requiredCount))
        XCTAssertTrue(plan.activities.contains { $0.ruleId == RuleIds.clinicianReferral })
        XCTAssertTrue(plan.activities.contains { $0.ruleId == RuleIds.painDeload })
    }

    func testStaleDataProducesSafeFallbackPlan() {
        let plan = engine.buildPlan(readiness: readiness(recovery: nil))
        XCTAssertTrue(plan.isMock || !plan.activities.isEmpty)
        XCTAssertTrue(plan.activities.contains { $0.ruleId == RuleIds.staleDataFallback })
        XCTAssertTrue(plan.totalEstimatedMinutes > 0)
    }

    func testReadinessSnapshotFromComputesTrendsAndBand() throws {
        let history = [
            WhoopSnapshot(dateEpochDay: day - 2, hrvMs: 70.0, restingHeartRate: 52.0, respiratoryRate: 14.0),
            WhoopSnapshot(dateEpochDay: day - 1, hrvMs: 68.0, restingHeartRate: 53.0, respiratoryRate: 14.0, dayStrain: 16.0),
        ]
        let today = WhoopSnapshot(
            dateEpochDay: day, recoveryScore: 30, hrvMs: 55.0,
            restingHeartRate: 61.0, respiratoryRate: 15.0
        )
        let snap = ReadinessSnapshot.from(today: today, history: history)
        XCTAssertEqual(ReadinessBand.rest, snap.band)
        let priorStrain = try XCTUnwrap(snap.dayStrainPrior)
        XCTAssertEqual(16.0, priorStrain, accuracy: 0.001)
        XCTAssertTrue((snap.rhrVsBaseline ?? 0.0) >= 5.0, "RHR delta should be elevated")
        XCTAssertTrue(
            snap.trendNotes.contains { $0.contains("HRV") },
            "HRV should read as below baseline"
        )
    }
}
