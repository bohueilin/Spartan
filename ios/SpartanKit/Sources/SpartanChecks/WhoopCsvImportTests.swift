import Foundation
import SpartanKit

/// Port of the Android `WhoopCsvParserTest`: fixtures mirror the real WHOOP export format exactly
/// (headers, local timestamps, blank cells, minutes-based durations) with synthetic values. Edge
/// rows reproduce real export quirks: no-sleep cycles with blank recovery, nap-only rows, cycles
/// that cross midnight, and an in-progress cycle with no end time.
final class WhoopCsvImportTests: XCTestCase {

    private let cyclesHeader =
        "Cycle start time,Cycle end time,Cycle timezone,Recovery score %,Resting heart rate (bpm),"
            + "Heart rate variability (ms),Skin temp (celsius),Blood oxygen %,Day Strain,Energy burned (cal),"
            + "Max HR (bpm),Average HR (bpm),Sleep onset,Wake onset,Sleep performance %,Respiratory rate (rpm),"
            + "Asleep duration (min),In bed duration (min),Light sleep duration (min),Deep (SWS) duration (min),"
            + "REM duration (min),Awake duration (min),Sleep need (min),Sleep debt (min),Sleep efficiency %,Sleep consistency %"

    private let sleepsHeader =
        "Cycle start time,Cycle end time,Cycle timezone,Sleep onset,Wake onset,Sleep performance %,"
            + "Respiratory rate (rpm),Asleep duration (min),In bed duration (min),Light sleep duration (min),"
            + "Deep (SWS) duration (min),REM duration (min),Awake duration (min),Sleep need (min),Sleep debt (min),"
            + "Sleep efficiency %,Sleep consistency %,Nap"

    private let workoutsHeader =
        "Cycle start time,Cycle end time,Cycle timezone,Workout start time,Workout end time,Duration (min),"
            + "Activity name,Activity Strain,Energy burned (cal),Max HR (bpm),Average HR (bpm),"
            + "HR Zone 1 %,HR Zone 2 %,HR Zone 3 %,HR Zone 4 %,HR Zone 5 %,GPS enabled"

    private let journalHeader = "Cycle start time,Cycle end time,Cycle timezone,Question text,Answered yes,Notes"

    /// Days since 1970-01-01 of an ISO date, computed in the same fixed-UTC frame as the parser.
    private func day(_ iso: String) -> Int {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.dateFormat = "yyyy-MM-dd"
        guard let date = formatter.date(from: iso) else { fatalError("bad fixture date \(iso)") }
        return Int(date.timeIntervalSince1970 / 86_400)
    }

    func testParseDetectsAllFourKinds() {
        XCTAssertEqual(WhoopCsvKind.physiologicalCycles, WhoopCsvParser.parse(cyclesHeader)?.kind)
        XCTAssertEqual(WhoopCsvKind.sleeps, WhoopCsvParser.parse(sleepsHeader)?.kind)
        XCTAssertEqual(WhoopCsvKind.workouts, WhoopCsvParser.parse(workoutsHeader)?.kind)
        XCTAssertEqual(WhoopCsvKind.journalEntries, WhoopCsvParser.parse(journalHeader)?.kind)
    }

    func testParseUnknownCsvReturnsNil() {
        XCTAssertNil(WhoopCsvParser.parse("Date,Steps,Calories\n2030-01-10,9000,2100"))
        XCTAssertNil(WhoopCsvParser.parse(""))
    }

    func testCyclesMapValuesAndConvertMinutesToHours() throws {
        let csv = cyclesHeader + "\n"
            + "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,75,58,82,33.50,96.00,9.5,2100,168,88,"
            + "2030-01-10 00:40:00,2030-01-10 07:40:00,80,14.8,390,420,180,90,120,30,540,90,93,85"
        let cycles = try XCTUnwrap(WhoopCsvParser.parse(csv)).cycles
        XCTAssertEqual(1, cycles.count)
        let row = try XCTUnwrap(cycles.first)
        XCTAssertEqual(day("2030-01-10"), row.dateEpochDay)
        XCTAssertEqual(75, row.recoveryScore)
        let restingHeartRate = try XCTUnwrap(row.restingHeartRate)
        XCTAssertEqual(58.0, restingHeartRate, accuracy: 0.0)
        let hrvMs = try XCTUnwrap(row.hrvMs)
        XCTAssertEqual(82.0, hrvMs, accuracy: 0.0)
        let dayStrain = try XCTUnwrap(row.dayStrain)
        XCTAssertEqual(9.5, dayStrain, accuracy: 0.0)
        let energyKcal = try XCTUnwrap(row.energyKcal)
        XCTAssertEqual(2100.0, energyKcal, accuracy: 0.0)
        XCTAssertEqual(80, row.sleepPerformance)
        let respiratoryRate = try XCTUnwrap(row.respiratoryRate)
        XCTAssertEqual(14.8, respiratoryRate, accuracy: 0.0)
        let sleepDurationHours = try XCTUnwrap(row.sleepDurationHours)
        XCTAssertEqual(6.5, sleepDurationHours, accuracy: 1e-9) // 390 min
        let sleepDebtHours = try XCTUnwrap(row.sleepDebtHours)
        XCTAssertEqual(1.5, sleepDebtHours, accuracy: 1e-9) // 90 min
        XCTAssertEqual(40, row.bedMinuteOfDay)
        XCTAssertEqual(7 * 60 + 40, row.wakeMinuteOfDay)
    }

    func testCyclesNoSleepCycleBlanksParseAsNil() throws {
        // Real exports contain cycles with no recorded sleep: recovery/HRV/sleep cells all blank.
        let csv = cyclesHeader + "\n"
            + "2030-01-12 00:57:00,2030-01-13 00:22:00,UTC-07:00,,,,,,4.5,1500,140,82,,,,,,,,,,,,,,"
        let row = try XCTUnwrap(WhoopCsvParser.parse(csv)?.cycles.first)
        XCTAssertEqual(day("2030-01-12"), row.dateEpochDay) // falls back to cycle start date
        XCTAssertNil(row.recoveryScore)
        XCTAssertNil(row.hrvMs)
        XCTAssertNil(row.sleepPerformance)
        XCTAssertNil(row.bedMinuteOfDay)
        let dayStrain = try XCTUnwrap(row.dayStrain)
        XCTAssertEqual(4.5, dayStrain, accuracy: 0.0)
    }

    func testCyclesDayIsTheDateTheUserWokeUp() throws {
        // A cycle that starts before midnight belongs to the NEXT day (the morning it wakes into).
        let csv = cyclesHeader + "\n"
            + "2030-01-14 23:09:00,2030-01-16 00:11:00,UTC-07:00,60,55,70,33.80,94.50,7.0,2000,147,86,"
            + "2030-01-14 23:09:00,2030-01-15 05:56:00,85,15.2,398,406,186,92,120,8,569,113,98,90"
        let row = try XCTUnwrap(WhoopCsvParser.parse(csv)?.cycles.first)
        XCTAssertEqual(day("2030-01-15"), row.dateEpochDay)
    }

    func testCyclesInProgressCycleWithoutEndTimeParses() throws {
        let csv = cyclesHeader + "\n"
            + "2030-01-20 01:47:00,,UTC-07:00,92,52,88,33.70,97.00,,,,,"
            + "2030-01-20 01:47:00,2030-01-20 08:23:00,82,15.9,369,396,176,79,114,27,537,83,93,81"
        let row = try XCTUnwrap(WhoopCsvParser.parse(csv)?.cycles.first)
        XCTAssertEqual(92, row.recoveryScore)
        XCTAssertNil(row.dayStrain)
    }

    func testSleepsNapColumnParsedBothWays() throws {
        let csv = sleepsHeader + "\n"
            + "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,2030-01-10 19:29:00,2030-01-10 20:27:00,"
            + "13,15.9,56,58,24,32,0,2,581,127,97,,true\n"
            + "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,2030-01-10 00:40:00,2030-01-10 07:18:00,"
            + "61,16.0,283,304,100,115,68,21,579,126,95,83,false"
        let sleeps = try XCTUnwrap(WhoopCsvParser.parse(csv)).sleeps
        XCTAssertEqual([true, false], sleeps.map { $0.nap })
        XCTAssertEqual(2, sleeps.count)
    }

    func testWorkoutsParseDurationsZonesAndHeartRates() throws {
        let csv = workoutsHeader + "\n"
            + "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,2030-01-10 19:04:00,2030-01-10 19:25:59,21,"
            + "Running,11.6,298.0,168,150,6,10,21,20,41,false"
        let workouts = try XCTUnwrap(WhoopCsvParser.parse(csv)).workouts
        XCTAssertEqual(1, workouts.count)
        let w = try XCTUnwrap(workouts.first)
        XCTAssertEqual(day("2030-01-10"), w.dateEpochDay)
        XCTAssertEqual(19 * 60 + 4, w.startMinuteOfDay)
        XCTAssertEqual(21, w.durationMinutes)
        XCTAssertEqual("Running", w.activityName)
        let strain = try XCTUnwrap(w.strain)
        XCTAssertEqual(11.6, strain, accuracy: 0.0)
        XCTAssertEqual(168, w.maxHr)
        XCTAssertEqual(150, w.averageHr)
        XCTAssertEqual([6.0, 10.0, 21.0, 20.0, 41.0] as [Double?], w.hrZonePercents)
    }

    func testJournalQuotedNoteWithCommaAndNewlineDoesNotBreakRows() throws {
        let csv = journalHeader + "\n"
            + "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,Consumed caffeine?,true,\"late espresso,\nregretted it\"\n"
            + "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,Have any alcoholic drinks?,false,"
        let journal = try XCTUnwrap(WhoopCsvParser.parse(csv)).journal
        XCTAssertEqual(2, journal.count)
        let first = try XCTUnwrap(journal.first)
        let last = try XCTUnwrap(journal.last)
        XCTAssertTrue(first.answeredYes)
        XCTAssertFalse(last.answeredYes)
    }

    // MARK: - Merger

    func testMergerJoinsSleepJournalAndCyclesPerDay() throws {
        let cycles = try XCTUnwrap(WhoopCsvParser.parse(
            cyclesHeader + "\n"
                // The cycles file lists the NAP window (19:29) as its sleep onset — a real export quirk.
                + "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,75,58,82,33.50,96.00,9.5,2100,168,88,"
                + "2030-01-10 19:29:00,2030-01-10 20:27:00,80,14.8,390,420,180,90,120,30,540,90,93,85"
        ))
        let sleeps = try XCTUnwrap(WhoopCsvParser.parse(
            sleepsHeader + "\n"
                + "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,2030-01-10 19:29:00,2030-01-10 20:27:00,"
                + "13,15.9,56,58,24,32,0,2,581,127,97,,true\n"
                + "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,2030-01-10 00:40:00,2030-01-10 07:18:00,"
                + "61,16.0,283,304,100,115,68,21,579,126,95,83,false"
        ))
        let journal = try XCTUnwrap(WhoopCsvParser.parse(
            journalHeader + "\n"
                + "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,Consumed caffeine?,true,\n"
                + "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,Have any alcoholic drinks?,false,\n"
                + "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,Ate food close to bedtime?,true,"
        ))

        let merged = WhoopCsvMerger.merge(files: [cycles, sleeps, journal], importedAtMillis: 42)
        XCTAssertEqual(1, merged.cycles.count)
        let dayRecord = try XCTUnwrap(merged.cycles.first)
        XCTAssertEqual(75, dayRecord.recoveryScore)
        // Bed/wake refined to the MAIN sleep, not the nap the cycles file listed.
        XCTAssertEqual(40, dayRecord.bedMinuteOfDay)
        XCTAssertEqual(7 * 60 + 18, dayRecord.wakeMinuteOfDay)
        XCTAssertEqual(true, dayRecord.journalCaffeine)
        XCTAssertEqual(false, dayRecord.journalAlcohol)
        XCTAssertEqual(true, dayRecord.journalLateMeal)
        XCTAssertEqual(42, dayRecord.importedAtMillis)

        XCTAssertEqual(1, merged.snapshots.count)
        let snapshot = try XCTUnwrap(merged.snapshots.first)
        XCTAssertFalse(snapshot.isMock)
        XCTAssertEqual(75, snapshot.recoveryScore)
    }

    func testMergerDedupesSameDayKeepingTheRicherRecord() throws {
        let csv = cyclesHeader + "\n"
            // Sparse cycle waking on Jan 15.
            + "2030-01-14 23:09:00,2030-01-15 06:00:00,UTC-07:00,,,,,,3.0,900,120,80,"
            + "2030-01-14 23:09:00,2030-01-15 05:56:00,,,,,,,,,,,,\n"
            // Rich cycle also waking on Jan 15.
            + "2030-01-15 06:20:00,2030-01-16 01:12:00,UTC-07:00,44,60,65,32.35,94.00,13.1,2125,143,96,"
            + "2030-01-15 06:20:00,2030-01-15 08:09:00,17,16.1,84,108,44,40,0,24,527,127,78,57"
        let parsed = try XCTUnwrap(WhoopCsvParser.parse(csv))
        let merged = WhoopCsvMerger.merge(files: [parsed], importedAtMillis: 0)
        XCTAssertEqual(1, merged.cycles.count)
        let record = try XCTUnwrap(merged.cycles.first)
        XCTAssertEqual(44, record.recoveryScore)
    }

    func testMergerSleepsWithoutCyclesStillProduceSleepDays() throws {
        let sleeps = try XCTUnwrap(WhoopCsvParser.parse(
            sleepsHeader + "\n"
                + "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,2030-01-10 00:40:00,2030-01-10 07:18:00,"
                + "61,16.0,283,304,100,115,68,21,579,126,95,83,false"
        ))
        let merged = WhoopCsvMerger.merge(files: [sleeps], importedAtMillis: 0)
        XCTAssertEqual(1, merged.cycles.count)
        let record = try XCTUnwrap(merged.cycles.first)
        XCTAssertEqual(61, record.sleepPerformance)
        XCTAssertNil(record.recoveryScore)
        let sleepDurationHours = try XCTUnwrap(record.sleepDurationHours)
        XCTAssertEqual(4.72, sleepDurationHours, accuracy: 1e-9) // 283 min, rounded to 2 decimals
    }

    func testMergerSumsExerciseMinutesPerDayAndDedupesWorkouts() throws {
        let csv = workoutsHeader + "\n"
            + "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,2030-01-10 14:16:00,2030-01-10 14:43:59,27,"
            + "Activity,5.5,137.0,132,119,93,0,0,0,0,false\n"
            + "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,2030-01-10 15:16:00,2030-01-10 15:30:59,14,"
            + "Activity,5.1,86.0,143,123,79,9,0,0,0,false\n"
            // Exact duplicate row (double import of the same file).
            + "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,2030-01-10 15:16:00,2030-01-10 15:30:59,14,"
            + "Activity,5.1,86.0,143,123,79,9,0,0,0,false"
        let parsed = try XCTUnwrap(WhoopCsvParser.parse(csv))
        let merged = WhoopCsvMerger.merge(files: [parsed], importedAtMillis: 0)
        XCTAssertEqual(2, merged.workouts.count)
        XCTAssertEqual([day("2030-01-10"): 41], merged.exerciseMinutesByDay)
    }
}
