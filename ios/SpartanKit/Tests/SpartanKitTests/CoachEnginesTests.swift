import XCTest
@testable import SpartanKit

/// Port of the Android `CoachEnginesTest`: same reference-band assertions, same counter-offer
/// arithmetic, same kg→lb progress math, same weekday-effect and pressure-window cases, same
/// SafetyEngine sweeps. Adapted where the platforms differ:
///  - Android's APOB "never faked" check becomes an enum-absence assertion plus genuine nil
///    checks on the non-coached WHOOP metrics (Swift's `MetricType` has no lab cases).
///  - Dates are epoch days; a pin test proves the Swift weekday math matches
///    `java.time.LocalDate.ofEpochDay(...).dayOfWeek` (epoch day 0 = Thursday 1970-01-01).
final class CoachEnginesTests: XCTestCase {

    // Days-from-civil (proleptic Gregorian), the mirror of java.time's epoch-day arithmetic.
    private func epochDay(_ y: Int, _ m: Int, _ d: Int) -> Int {
        let year = m <= 2 ? y - 1 : y
        let era = (year >= 0 ? year : year - 399) / 400
        let yearOfEra = year - era * 400
        let dayOfYear = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1
        let dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146_097 + dayOfEra - 719_468
    }

    // MARK: - Epoch-day weekday math (Swift-only pin; Android gets this from java.time)

    func testEpochDayWeekdayMathMatchesJavaTime() {
        XCTAssertEqual(0, epochDay(1970, 1, 1))
        XCTAssertEqual(DayOfWeek.thursday, DayOfWeek.from(epochDay: 0), "1970-01-01 was a Thursday")
        XCTAssertEqual(20605, epochDay(2026, 6, 1))
        XCTAssertEqual(DayOfWeek.monday, DayOfWeek.from(epochDay: epochDay(2026, 6, 1)), "2026-06-01 is a Monday")
        XCTAssertEqual(DayOfWeek.tuesday, DayOfWeek.from(epochDay: epochDay(2026, 7, 21)))
        XCTAssertEqual(DayOfWeek.sunday, DayOfWeek.from(epochDay: epochDay(2026, 7, 26)))
        XCTAssertEqual(DayOfWeek.wednesday, DayOfWeek.from(epochDay: -1), "1969-12-31 was a Wednesday (floored modulo)")
        XCTAssertEqual(DayOfWeek.monday, DayOfWeek.sunday.next, "next wraps like Kotlin DayOfWeek.plus(1)")
    }

    // MARK: - ReferenceRanges

    func testReferenceRangesCoverTopFiveAndAdjustByAgeAndSex() throws {
        for metric in ReferenceRanges.topFive {
            XCTAssertNotNil(ReferenceRanges.bandFor(metric, ageYears: 44, sex: .male), "band missing for \(metric)")
        }
        // HRV bands decline with age.
        let hrv20sHigh = try XCTUnwrap(ReferenceRanges.bandFor(.hrvRmssd, ageYears: 25, sex: .male)?.typicalHigh)
        let hrv60sHigh = try XCTUnwrap(ReferenceRanges.bandFor(.hrvRmssd, ageYears: 65, sex: .male)?.typicalHigh)
        XCTAssertTrue(hrv20sHigh > hrv60sHigh)
        // Women's typical RHR band sits a touch higher than men's, same bracket.
        let rhrFemaleLow = try XCTUnwrap(ReferenceRanges.bandFor(.restingHeartRate, ageYears: 44, sex: .female)?.typicalLow)
        let rhrMaleLow = try XCTUnwrap(ReferenceRanges.bandFor(.restingHeartRate, ageYears: 44, sex: .male)?.typicalLow)
        XCTAssertTrue(rhrFemaleLow > rhrMaleLow)
        // Unknown demographics still get an all-adult band, never a refusal.
        XCTAssertNotNil(ReferenceRanges.bandFor(.restingHeartRate, ageYears: nil, sex: .unspecified))
        // Non-coached metrics are never faked. Android asserts APOB → null; that case doesn't
        // exist in Swift, so assert it stays absent AND the non-coached WHOOP cases return nil.
        XCTAssertNil(MetricType(rawValue: "APOB"), "APOB must not gain a Swift case without a clinician-first review")
        for metric in [MetricType.energyKcal, .dayStrain, .sleepDebt, .respiratoryRate] {
            XCTAssertNil(ReferenceRanges.bandFor(metric, ageYears: 44, sex: .male), "\(metric) band must never be faked")
        }
        XCTAssertEqual("40–49", ReferenceRanges.bracketLabel(44))
    }

    func testReferenceRangesEducationCopyPassesSafetyEngine() throws {
        let safety = SafetyEngine()
        let ages: [Int?] = [nil, 25, 44, 65]
        for age in ages {
            for sex in SexAtBirth.allCases {
                for metric in ReferenceRanges.topFive {
                    if let band = ReferenceRanges.bandFor(metric, ageYears: age, sex: sex) {
                        try safety.sanitize(band.education)
                    }
                }
            }
        }
    }

    // MARK: - GoalEngine

    private func goal(_ type: GoalType, target: Double, weeks: Int, baseline: Double? = nil) -> Goal {
        Goal(
            id: "g1", type: type, targetValue: target, baselineValue: baseline,
            startEpochDay: 20000, targetEpochDay: 20000 + weeks * 7
        )
    }

    private func isOk(_ v: GoalValidation) -> Bool {
        if case .ok = v { return true }
        return false
    }

    private func isInvalid(_ v: GoalValidation) -> Bool {
        if case .invalid = v { return true }
        return false
    }

    private func asAdjusted(_ v: GoalValidation) -> (adjusted: Goal, why: String)? {
        if case .adjusted(let adjusted, let why) = v { return (adjusted, why) }
        return nil
    }

    func testWeightGoalWithin2lbPerWeekIsAccepted() {
        let v = GoalEngine.validate(goal(.weightLoss, target: 8.0, weeks: 5))
        XCTAssertTrue(isOk(v))
    }

    func testWeightGoalTooFastIsCounterOfferedAtSafeRateNotRefused() throws {
        // The user's own example: 10 lb in 4 weeks = 2.5 lb/wk → nearest safe is 5 weeks.
        let v = GoalEngine.validate(goal(.weightLoss, target: 10.0, weeks: 4))
        let (adjusted, why) = try XCTUnwrap(asAdjusted(v))
        XCTAssertEqual(20000 + 5 * 7, adjusted.targetEpochDay)
        XCTAssertTrue(why.contains("1–2 lb"))
        try SafetyEngine().sanitize(why)
    }

    func testSleepGoal10pctIn3WeeksIsAcceptedAnd30pctIn2WeeksIsAdjusted() throws {
        XCTAssertTrue(isOk(GoalEngine.validate(goal(.sleepRecovery, target: 10.0, weeks: 3))))
        let fast = GoalEngine.validate(goal(.sleepRecovery, target: 30.0, weeks: 2))
        let (adjusted, _) = try XCTUnwrap(asAdjusted(fast))
        XCTAssertEqual(20000 + 6 * 7, adjusted.targetEpochDay)
    }

    func testInvalidGoalsZeroTargetOrNoRunwayAreRejected() {
        XCTAssertTrue(isInvalid(GoalEngine.validate(goal(.weightLoss, target: 0.0, weeks: 4))))
        let noRunway = Goal(
            id: "g", type: .weightLoss, targetValue: 5.0, baselineValue: nil,
            startEpochDay: 20000, targetEpochDay: 20000
        )
        XCTAssertTrue(isInvalid(GoalEngine.validate(noRunway)))
    }

    func testWeightProgressConvertsKgReadingsToPounds() {
        // Baseline 81.6 kg; current week average 79.4 kg → ~4.9 lb lost of a 10 lb target.
        let today = 20028
        let readings = (0...6).map { d in
            MetricReading(type: .weight, value: 79.4, recordedAtEpochDay: today - d)
        }
        let g = goal(.weightLoss, target: 10.0, weeks: 6, baseline: 81.6)
        let p = GoalEngine.progress(g, readings: readings, todayEpochDay: today)
        XCTAssertEqual(0.485, p.fraction, accuracy: 0.02)
        XCTAssertTrue(p.summary.contains("lb"))
    }

    func testSleepProgressPercentGainVsBaseline() {
        let today = 20014
        let readings = (0...6).map { d in
            MetricReading(type: .sleepPerformance, value: 77.0, recordedAtEpochDay: today - d)
        }
        let g = goal(.sleepRecovery, target: 10.0, weeks: 3, baseline: 70.0)
        let p = GoalEngine.progress(g, readings: readings, todayEpochDay: today)
        XCTAssertEqual(1.0, p.fraction, accuracy: 0.01) // 70→77 = +10%
        XCTAssertTrue(p.onTrack)
    }

    func testStressGoalIsAHabitGoalCountingCalmSessions() {
        let g = goal(.stressResilience, target: 5.0, weeks: 3, baseline: 35.0)
        let p = GoalEngine.progress(g, readings: [], todayEpochDay: 20007, breathworkThisWeek: 3)
        XCTAssertEqual(0.6, p.fraction, accuracy: 0.001)
        XCTAssertTrue(p.summary.contains("3 of 5"))
    }

    func testGoalModifiersBendThePlanTowardTheGoal() {
        let weight = GoalEngine.planModifiers(goal(.weightLoss, target: 10.0, weeks: 6), windows: [])
        XCTAssertEqual(60, weight.extraZone2MinutesPerWeek)
        XCTAssertEqual(2, weight.protectStrengthSessions)
        let sleep = GoalEngine.planModifiers(goal(.sleepRecovery, target: 10.0, weeks: 3), windows: [])
        XCTAssertTrue(sleep.emphasizeWindDown)
        let none = GoalEngine.planModifiers(nil, windows: [])
        XCTAssertEqual(0, none.extraZone2MinutesPerWeek)
        XCTAssertFalse(none.emphasizeWindDown)
    }

    // MARK: - StressPatterns

    /// Snapshots where every Wednesday morning (after Tuesday) runs 20 points low.
    private func tuesdayStressedSnapshots(weeks: Int) -> [WhoopSnapshot] {
        let start = epochDay(2026, 6, 1) // a Monday
        return (0..<(weeks * 7)).map { i in
            let day = start + i
            let recovery = DayOfWeek.from(epochDay: day) == .wednesday ? 45 : 65
            return WhoopSnapshot(dateEpochDay: day, recoveryScore: recovery)
        }
    }

    func testWeekdayEffectFindsTheStressorDayWithEnoughEvidence() throws {
        let effects = StressPatterns.weekdayEffects(tuesdayStressedSnapshots(weeks: 4))
        XCTAssertEqual(1, effects.count)
        let effect = try XCTUnwrap(effects.first)
        XCTAssertEqual(DayOfWeek.tuesday, effect.stressorDay)
        XCTAssertTrue(effect.recoveryDelta < -StressPatterns.minEffectRecoveryPoints)
        XCTAssertEqual(4, effect.sampleWeeks)
        try SafetyEngine().sanitize(effect.insight)
    }

    func testWeekdayEffectStaysSilentOnThinOrFlatData() {
        XCTAssertTrue(StressPatterns.weekdayEffects(tuesdayStressedSnapshots(weeks: 2)).isEmpty)
        let flat = (0..<28).map { i in
            WhoopSnapshot(dateEpochDay: 20000 + i, recoveryScore: 60 + (i % 3))
        }
        XCTAssertTrue(StressPatterns.weekdayEffects(flat).isEmpty)
    }

    func testPressureWindowsMatchDaysAndNudgeFiresFiveMinutesEarly() {
        // Tue+Thu 11:00–12:00 (mask bits: Mon=0 … Sun=6).
        let window = PressureWindow(
            id: "w1",
            daysOfWeekMask: (1 << DayOfWeek.tuesday.rawValue) | (1 << DayOfWeek.thursday.rawValue),
            startMinuteOfDay: 11 * 60, endMinuteOfDay: 12 * 60, label: "Standup"
        )
        let tue = epochDay(2026, 7, 21)
        let wed = epochDay(2026, 7, 22)
        XCTAssertEqual([window], StressPatterns.windowsForDay([window], dateEpochDay: tue))
        XCTAssertTrue(StressPatterns.windowsForDay([window], dateEpochDay: wed).isEmpty)
        XCTAssertEqual(11 * 60 - 5, StressPatterns.nudgeMinuteFor(window))
        XCTAssertEqual("Tue, Thu 11:00–12:00", StressPatterns.describeWindow(window))
    }

    func testPressureWindowSundayBitAndMidnightClamp() {
        let sunday = PressureWindow(
            id: "w2", daysOfWeekMask: 1 << DayOfWeek.sunday.rawValue,
            startMinuteOfDay: 2, endMinuteOfDay: 60
        )
        XCTAssertEqual(
            [sunday],
            StressPatterns.windowsForDay([sunday], dateEpochDay: epochDay(2026, 7, 26))
        )
        XCTAssertEqual(0, StressPatterns.nudgeMinuteFor(sunday)) // 2-min start clamps to midnight
    }
}
