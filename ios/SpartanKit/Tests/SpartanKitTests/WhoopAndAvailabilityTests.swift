import XCTest
@testable import SpartanKit

/// Port of the Android `WhoopAndAvailabilityTest` plus the availability edge cases and
/// `DailyPlan` math from `MappingAndDataTest`.
final class WhoopAndAvailabilityTests: XCTestCase {

    private let svc = AvailabilityService()

    // MARK: - MockWhoopClient

    func testMockWhoopClient_isLabeledSampleData_andReturnsSeries() {
        let client = MockWhoopClient()
        XCTAssertTrue(client.isMock)
        let days = client.fetchRecentDays(days: 7)
        XCTAssertEqual(days.count, 7)
        XCTAssertTrue(days.allSatisfy { $0.isMock })
        // oldest first, today last, strictly increasing dates
        for (a, b) in zip(days, days.dropFirst()) {
            XCTAssertEqual(b.dateEpochDay, a.dateEpochDay + 1)
        }
        // the series ends on the easy-recovery sample day (recovery 42)
        XCTAssertEqual(days.last?.recoveryScore, 42)
    }

    // MARK: - AvailabilityService

    func testOpenWindows_subtractsAndMergesBusyBlocks() {
        // Working window 0..600 (minutes). Busy: 60-120, 100-180 (overlap -> merge), 300-360.
        let open = svc.openWindows(dayStart: 0, dayEnd: 600, busy: [
            TimeWindow(startEpochMinute: 60, endEpochMinute: 120),
            TimeWindow(startEpochMinute: 100, endEpochMinute: 180),
            TimeWindow(startEpochMinute: 300, endEpochMinute: 360),
        ])
        XCTAssertEqual(open, [
            TimeWindow(startEpochMinute: 0, endEpochMinute: 60),
            TimeWindow(startEpochMinute: 180, endEpochMinute: 300),
            TimeWindow(startEpochMinute: 360, endEpochMinute: 600),
        ])
    }

    func testSuggestSlot_returnsEarliestFittingGapTrimmedToLength() {
        let slot = svc.suggestSlot(activityMinutes: 30, dayStart: 0, dayEnd: 600, busy: [
            TimeWindow(startEpochMinute: 0, endEpochMinute: 50),
            TimeWindow(startEpochMinute: 60, endEpochMinute: 180),
        ])
        // first gap 50..60 is only 10 min; next gap 180..600 fits -> 180..210
        XCTAssertNotNil(slot)
        XCTAssertEqual(slot, TimeWindow(startEpochMinute: 180, endEpochMinute: 210))
    }

    func testSuggestSlot_returnsNilWhenNothingFits() {
        let slot = svc.suggestSlot(activityMinutes: 120, dayStart: 0, dayEnd: 100, busy: [])
        XCTAssertNil(slot)
    }

    // MARK: - Availability edge cases

    func testAvailability_fullyBusyDayHasNoOpenWindows() {
        let busy = [TimeWindow(startEpochMinute: 0, endEpochMinute: 480)]
        let open = svc.openWindows(dayStart: 0, dayEnd: 480, busy: busy)
        XCTAssertTrue(open.isEmpty)
        XCTAssertNil(svc.suggestSlot(activityMinutes: 30, dayStart: 0, dayEnd: 480, busy: busy))
    }

    func testAvailability_zeroLengthDayIsEmpty() {
        XCTAssertTrue(svc.openWindows(dayStart: 100, dayEnd: 100, busy: []).isEmpty)
    }

    func testAvailability_exactFitSlotIsFound() {
        let slot = svc.suggestSlot(activityMinutes: 60, dayStart: 0, dayEnd: 60, busy: [])
        XCTAssertEqual(slot, TimeWindow(startEpochMinute: 0, endEpochMinute: 60))
    }

    func testAvailability_adjacentBusyBlocksMergeAndDoNotLeaveSlivers() {
        // 0-30 and 30-60 busy back-to-back leave a single open window 60-120.
        let open = svc.openWindows(dayStart: 0, dayEnd: 120, busy: [
            TimeWindow(startEpochMinute: 0, endEpochMinute: 30),
            TimeWindow(startEpochMinute: 30, endEpochMinute: 60),
        ])
        XCTAssertEqual(open, [TimeWindow(startEpochMinute: 60, endEpochMinute: 120)])
    }

    func testAvailability_respectsMinWindow() {
        // Gaps shorter than the activity are skipped.
        let slot = svc.suggestSlot(activityMinutes: 40, dayStart: 0, dayEnd: 200, busy: [
            TimeWindow(startEpochMinute: 20, endEpochMinute: 60),
            TimeWindow(startEpochMinute: 90, endEpochMinute: 130),
        ])
        // open windows: 0-20 (too short), 60-90 (too short), 130-200 (fits) -> 130..170
        XCTAssertEqual(slot, TimeWindow(startEpochMinute: 130, endEpochMinute: 170))
    }

    // MARK: - DailyPlan math

    func testDailyPlan_progressAndTotals() {
        func act(_ id: String, _ minutes: Int, _ status: ActivityStatus) -> DailyActivity {
            DailyActivity(
                id: id,
                title: id,
                category: .movement,
                priority: .optional,
                whyItMatters: "w",
                instructions: [],
                estimatedMinutes: minutes,
                intensity: .easy,
                bestTimeOfDay: .anytime,
                status: status,
                ruleId: "R"
            )
        }
        let plan = DailyPlan(
            dateEpochDay: 20_000,
            headline: "h",
            band: .balanced,
            activities: [
                act("a", 10, .done),
                act("b", 15, .planned),
                act("c", 5, .skipped),
                act("d", 20, .planned),
            ]
        )
        XCTAssertEqual(plan.totalEstimatedMinutes, 50)
        XCTAssertEqual(plan.completedCount, 1)
        XCTAssertEqual(plan.progressPercent, 25)
    }
}
