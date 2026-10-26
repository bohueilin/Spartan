import XCTest
@testable import SpartanKit

/// Port of the Android `CoachingEngineTest`: same scenarios, same thresholds, same expectations.
final class CoachingEngineTests: XCTestCase {
    private let engine = CoachingEngine()
    private let safety = SafetyEngine()
    private let day = 20_000

    /// `rrDays` / `rhrDays`: nights behind each baseline given — past the learning period unless a test says otherwise.
    private func readiness(
        recovery: Int? = 60, hrv: Double? = 60.0, rhr: Double? = 55.0,
        sleepPerf: Int? = 90, sleepDebt: Double? = 0.0, strainPrior: Double? = 8.0,
        resp: Double? = 15.0, hrvBase: Double? = nil, rhrBase: Double? = nil, respBase: Double? = nil,
        rrDays: Int = 7, rhrDays: Int = 7
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
            rrBaselineDays: respBase == nil ? 0 : rrDays,
            rhrBaselineDays: rhrBase == nil ? 0 : rhrDays
        )
    }

    private func byId(_ plan: DailyPlan, _ slug: String) -> DailyActivity? {
        plan.activities.first { $0.id == "\(day):\(slug)" }
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

    func testSafetyBannerOnlyCautionsALowRecoveryDayNeverRepeatsTheDisclaimer() throws {
        let rest = try XCTUnwrap(engine.buildPlan(readiness: readiness(recovery: 25)).safetyBanner)
        XCTAssertTrue(rest.contains("low-recovery day"))
        // Today carries the general "not medical advice" line once, as its own footer line.
        for recovery in [40, 60, 80] {
            XCTAssertNil(engine.buildPlan(readiness: readiness(recovery: recovery)).safetyBanner, "recovery \(recovery)")
        }
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

    func testTrendCheckInsSpeakOfYourUsualNeverAStressSign() throws {
        let rhr = try XCTUnwrap(engine.buildPlan(readiness: readiness(recovery: 55, rhr: 66.0, rhrBase: 58.0))
            .activities.first { $0.ruleId == RuleIds.elevatedRhrTrend })
        XCTAssertEqual(
            "Resting heart rate is above your usual — a good cue to hydrate, ease intensity, and check how you slept.",
            rhr.whyItMatters
        )
        let breathing = try XCTUnwrap(engine.buildPlan(readiness: readiness(recovery: 55, hrv: 48.0, hrvBase: 60.0))
            .activities.first { $0.ruleId == RuleIds.lowHrvTrend })
        XCTAssertEqual("Your HRV is below your usual. Slow breathing is an easy way to downshift tonight.", breathing.whyItMatters)
    }

    func testConcerningRespiratoryRateAddsNonDiagnosticClinicianReferral() throws {
        let plan = engine.buildPlan(readiness: readiness(recovery: 55, resp: 19.0, respBase: 15.0))
        let referral = try XCTUnwrap(
            plan.activities.first { $0.ruleId == RuleIds.clinicianReferral }
        )
        XCTAssertEqual(ActivityPriority.required, referral.priority)
        XCTAssertTrue(
            referral.instructions.joined(separator: " ").lowercased().contains("clinician")
        )
        // A wellness check-in, never an illness signal: how you feel, in plain words.
        XCTAssertEqual("Notice how you feel today — energy, sleep, anything unusual.", referral.instructions.first)
        let said = ([referral.title, referral.whyItMatters] + referral.instructions).joined(separator: " ").lowercased()
        for word in ["illness", "sick"] {
            XCTAssertFalse(said.contains(word), "\"\(word)\" in: \(said)")
        }
    }

    func testClinicianReferralFiresOnlyOnDeviationFromTheUsersOwnBaseline() throws {
        func referral(_ r: ReadinessSnapshot) -> DailyActivity? {
            engine.buildPlan(readiness: r).activities.first { $0.ruleId == RuleIds.clinicianReferral }
        }
        // Respiratory rate: +3 breaths a minute over the user's usual is the line; just under it is not.
        let respUp = try XCTUnwrap(referral(readiness(recovery: 60, resp: 18.0, respBase: 15.0)))
        XCTAssertEqual(MetricType.respiratoryRate, respUp.relatedMetric)
        XCTAssertEqual(ActivityPriority.required, respUp.priority)
        XCTAssertNil(referral(readiness(recovery: 60, resp: 17.9, respBase: 15.0)))
        // Resting heart rate: +10 bpm over the user's usual is the line; just under it is not.
        let rhrUp = try XCTUnwrap(referral(readiness(recovery: 60, rhr: 66.0, rhrBase: 56.0)))
        XCTAssertEqual(MetricType.restingHeartRate, rhrUp.relatedMetric)
        XCTAssertEqual(ActivityPriority.required, rhrUp.priority)
        XCTAssertNil(referral(readiness(recovery: 60, rhr: 65.9, rhrBase: 56.0)))
        // Both up: one card, tagged with respiratory rate.
        let both = engine.buildPlan(readiness: readiness(recovery: 60, rhr: 70.0, resp: 19.0, rhrBase: 56.0, respBase: 15.0))
        let referrals = both.activities.filter { $0.ruleId == RuleIds.clinicianReferral }
        XCTAssertEqual(1, referrals.count)
        XCTAssertEqual(MetricType.respiratoryRate, referrals.first?.relatedMetric)
    }

    func testClinicianReferralNeverFiresOnAnAbsoluteValueAlone() {
        func hasReferral(_ r: ReadinessSnapshot) -> Bool {
            engine.buildPlan(readiness: r).activities.contains { $0.ruleId == RuleIds.clinicianReferral }
        }
        let recoveries: [Int?] = [20, 60, 85, nil]
        for recovery in recoveries {
            let ctx = "recovery=\(recovery.map(String.init) ?? "nil")"
            // No baseline yet: no reading, however high, is "outside your usual range".
            XCTAssertFalse(hasReferral(readiness(recovery: recovery, rhr: 120.0, resp: 30.0)), ctx)
            // A high reading that IS the user's usual is not a deviation either.
            XCTAssertFalse(hasReferral(readiness(recovery: recovery, rhr: 101.0, resp: 25.5, rhrBase: 100.0, respBase: 25.0)), ctx)
        }
        // A baseline with no reading today: nothing to compare.
        XCTAssertFalse(hasReferral(readiness(recovery: 60, resp: nil, respBase: 15.0)))
        XCTAssertFalse(hasReferral(readiness(recovery: 60, rhr: nil, rhrBase: 56.0)))
    }

    /// `nights` prior nights at respiratory rate 14.5 and resting HR 55, then `today`: the snapshot a sync builds.
    private func afterNights(_ nights: Int, today: WhoopSnapshot) -> ReadinessSnapshot {
        ReadinessSnapshot.from(
            today: today,
            history: (1...nights).map {
                WhoopSnapshot(dateEpochDay: day - $0, recoveryScore: 70, restingHeartRate: 55.0, respiratoryRate: 14.5)
            }
        )
    }

    func testClinicianReferralWaitsForTheLearningPeriodBeforeABaselineIsTheUsersUsual() throws {
        let rrUp = WhoopSnapshot(dateEpochDay: day, recoveryScore: 80, restingHeartRate: 55.0, respiratoryRate: 17.6)
        let rhrUp = WhoopSnapshot(dateEpochDay: day, recoveryScore: 80, restingHeartRate: 66.0, respiratoryRate: 14.5)
        for today in [rrUp, rhrUp] {
            let ctx = "rr=\(today.respiratoryRate ?? 0) rhr=\(today.restingHeartRate ?? 0)"
            // One night, or four: Spartan is still learning this user's normal, so nothing is "outside" it yet.
            for nights in [1, 4] {
                let plan = engine.buildPlan(readiness: afterNights(nights, today: today))
                XCTAssertNil(byId(plan, "clinician"), "nights=\(nights) \(ctx)")
                XCTAssertFalse(plan.activities.contains { $0.ruleId == RuleIds.concerningVitalEasy }, "nights=\(nights) \(ctx)")
                XCTAssertEqual("You're primed. Make today count.", plan.headline, "nights=\(nights) \(ctx)")
            }
            // Five nights is the learning period: the same reading now is a check-in day.
            let learned = afterNights(5, today: today)
            XCTAssertEqual(5, learned.rrBaselineDays)
            XCTAssertEqual(5, learned.rhrBaselineDays)
            let plan = engine.buildPlan(readiness: learned)
            let referral = try XCTUnwrap(byId(plan, "clinician"), ctx)
            XCTAssertEqual(ActivityPriority.required, referral.priority, ctx)
            let walk = try XCTUnwrap(byId(plan, "zone2-walk"), ctx)
            XCTAssertEqual(RuleIds.concerningVitalEasy, walk.ruleId, ctx)
            XCTAssertEqual("Take it gently today and notice how you feel.", plan.headline, ctx)
        }
    }

    func testClinicianReferralNeedsTheLearningPeriodBehindEachBaselineItReads() throws {
        func referral(_ r: ReadinessSnapshot) -> DailyActivity? { byId(engine.buildPlan(readiness: r), "clinician") }
        // Exactly the learning period counts; one night short does not.
        XCTAssertNotNil(referral(readiness(recovery: 60, resp: 18.0, respBase: 15.0, rrDays: 5)))
        XCTAssertNil(referral(readiness(recovery: 60, resp: 18.0, respBase: 15.0, rrDays: 4)))
        XCTAssertNotNil(referral(readiness(recovery: 60, rhr: 66.0, rhrBase: 56.0, rhrDays: 5)))
        XCTAssertNil(referral(readiness(recovery: 60, rhr: 66.0, rhrBase: 56.0, rhrDays: 4)))
        // Each baseline stands on its own nights: a learned one never vouches for a newer one.
        XCTAssertNil(referral(readiness(recovery: 60, rhr: 70.0, resp: 15.0, rhrBase: 56.0, respBase: 15.0, rhrDays: 4)))
        let rrLearned = try XCTUnwrap(referral(readiness(recovery: 60, rhr: 70.0, resp: 19.0, rhrBase: 56.0, respBase: 15.0, rhrDays: 4)))
        XCTAssertEqual(MetricType.respiratoryRate, rrLearned.relatedMetric)
        let rhrLearned = try XCTUnwrap(referral(readiness(recovery: 60, rhr: 70.0, resp: 19.0, rhrBase: 56.0, respBase: 15.0, rrDays: 1)))
        XCTAssertEqual(MetricType.restingHeartRate, rhrLearned.relatedMetric)
    }

    func testReadinessSnapshotFromCountsThePriorNightsBehindEachBaseline() throws {
        let history = [
            WhoopSnapshot(dateEpochDay: day - 3, restingHeartRate: 54.0, respiratoryRate: 14.0),
            WhoopSnapshot(dateEpochDay: day - 2, restingHeartRate: 55.0), // no respiratory rate that night
            WhoopSnapshot(dateEpochDay: day - 1, restingHeartRate: 56.0, respiratoryRate: 15.0),
            WhoopSnapshot(dateEpochDay: day, restingHeartRate: 90.0, respiratoryRate: 30.0), // today is never its own baseline
        ]
        let snap = ReadinessSnapshot.from(
            today: WhoopSnapshot(dateEpochDay: day, recoveryScore: 70, restingHeartRate: 57.0, respiratoryRate: 15.0),
            history: history
        )
        XCTAssertEqual(2, snap.rrBaselineDays)
        XCTAssertEqual(3, snap.rhrBaselineDays)
        let rrBaseline = try XCTUnwrap(snap.respiratoryRateBaseline)
        XCTAssertEqual(14.5, rrBaseline, accuracy: 1e-9)
        let rhrDelta = try XCTUnwrap(snap.rhrVsBaseline)
        XCTAssertEqual(2.0, rhrDelta, accuracy: 1e-9)
        let alone = ReadinessSnapshot.from(today: WhoopSnapshot(dateEpochDay: day, recoveryScore: 70, respiratoryRate: 15.0))
        XCTAssertEqual(0, alone.rrBaselineDays)
        XCTAssertEqual(0, alone.rhrBaselineDays)
        XCTAssertNil(alone.respiratoryRateBaseline)
    }

    func testConcerningVitalGoodRecoveryDayGetsAnEasyWalk() throws {
        for recovery in [80, 60] {
            let days = [
                readiness(recovery: recovery, resp: 19.0, respBase: 15.0),
                readiness(recovery: recovery, rhr: 66.0, rhrBase: 55.0),
            ]
            for r in days {
                let plan = engine.buildPlan(readiness: r)
                let ctx = "recovery=\(recovery) resp=\(r.respiratoryRate ?? 0) rhr=\(r.restingHeartRate ?? 0)"
                let walk = try XCTUnwrap(byId(plan, "zone2-walk"), ctx)
                XCTAssertEqual(RuleIds.concerningVitalEasy, walk.ruleId, ctx)
                XCTAssertEqual(Intensity.easy, walk.intensity, ctx)
                XCTAssertEqual(15, walk.estimatedMinutes, ctx)
                XCTAssertNil(byId(plan, "zone2"), ctx)
                XCTAssertNil(byId(plan, "strength"), ctx)
                XCTAssertFalse(plan.activities.contains { $0.intensity == .moderate || $0.intensity == .hard }, ctx)
                XCTAssertNotNil(byId(plan, "clinician"), ctx)
                var copy = [walk.title, walk.whyItMatters] + walk.instructions
                if let note = walk.safetyNote { copy.append(note) }
                for line in copy { XCTAssertTrue(safety.validateCopy(line), line) }
            }
        }
        // A low-recovery day keeps its own easy block.
        let low = engine.buildPlan(readiness: readiness(recovery: 40, resp: 19.0, respBase: 15.0))
        XCTAssertEqual(RuleIds.lowRecovery, byId(low, "zone2-walk")?.ruleId)
    }

    func testHeadlineFollowsTheBandAndStaysGentleOnPainAndCheckInDays() {
        let gentle = "Take it gently today and notice how you feel."
        XCTAssertEqual(gentle, engine.buildPlan(readiness: readiness(recovery: 80), options: CoachingOptions(painFlag: true)).headline)
        XCTAssertEqual(gentle, engine.buildPlan(readiness: readiness(recovery: 80, resp: 19.0, respBase: 15.0)).headline)
        XCTAssertEqual(gentle, engine.buildPlan(readiness: readiness(recovery: 20, rhr: 66.0, rhrBase: 55.0)).headline)
        XCTAssertEqual("You're primed. Make today count.", engine.buildPlan(readiness: readiness(recovery: 80)).headline)
        XCTAssertEqual("Balanced day. Steady, quality work.", engine.buildPlan(readiness: readiness(recovery: 60)).headline)
        XCTAssertEqual("Go lighter today and protect recovery.", engine.buildPlan(readiness: readiness(recovery: 40)).headline)
        XCTAssertEqual("Recovery first. Today is for rebuilding.", engine.buildPlan(readiness: readiness(recovery: 20)).headline)
        // A stale day keeps its own line, pain or not.
        XCTAssertEqual(
            "Let's ease in while Spartan waits for today's data.",
            engine.buildPlan(readiness: readiness(recovery: nil), options: CoachingOptions(painFlag: true)).headline
        )
    }

    func testPlanRespectsMaxActivitiesButKeepsRequiredAndReferral() {
        let plan = engine.buildPlan(
            readiness: readiness(recovery: 20, rhr: 70.0, sleepPerf: 40, resp: 28.0, rhrBase: 58.0, respBase: 15.0),
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
