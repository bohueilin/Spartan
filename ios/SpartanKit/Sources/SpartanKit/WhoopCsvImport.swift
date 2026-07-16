import Foundation

/// WHOOP CSV export import: typed row models, the parser, and the per-day merger.
///
/// Faithful port of the Android `com.spartan.data.whoop.csv` package (`WhoopCsvModels`,
/// `WhoopCsvParser`, `WhoopCsvMerger`): same header detection, same value mapping, same
/// day-resolution and merge rules. The Android merger targets a Room `WhoopCycleEntity`;
/// here the merge lands in the plain `WhoopCycleRecord` / `WhoopWorkoutRecord` structs so
/// SpartanKit stays persistence-agnostic.

// MARK: - Row models

/// Which WHOOP export file a CSV is, detected from its header row.
public enum WhoopCsvKind: String, Codable, CaseIterable {
    case physiologicalCycles = "PHYSIOLOGICAL_CYCLES"
    case sleeps = "SLEEPS"
    case workouts = "WORKOUTS"
    case journalEntries = "JOURNAL_ENTRIES"
}

/// One parsed WHOOP export file. Only the list matching `kind` is populated; the raw
/// cycle-start string is kept on each row so files can be joined per physiological cycle.
public struct ParsedWhoopFile: Equatable {
    public let kind: WhoopCsvKind
    public let cycles: [WhoopCycleRow]
    public let sleeps: [WhoopSleepRow]
    public let workouts: [WhoopWorkoutRow]
    public let journal: [WhoopJournalRow]

    public init(
        kind: WhoopCsvKind,
        cycles: [WhoopCycleRow] = [],
        sleeps: [WhoopSleepRow] = [],
        workouts: [WhoopWorkoutRow] = [],
        journal: [WhoopJournalRow] = []
    ) {
        self.kind = kind
        self.cycles = cycles
        self.sleeps = sleeps
        self.workouts = workouts
        self.journal = journal
    }
}

/// A row of physiological_cycles.csv. Nil fields mirror blank cells in the export.
public struct WhoopCycleRow: Equatable {
    public let cycleStartRaw: String
    public let dateEpochDay: Int
    public let recoveryScore: Int?
    public let restingHeartRate: Double?
    public let hrvMs: Double?
    public let dayStrain: Double?
    public let energyKcal: Double?
    public let sleepPerformance: Int?
    public let respiratoryRate: Double?
    public let sleepDurationHours: Double?
    public let sleepDebtHours: Double?
    public let bedMinuteOfDay: Int?
    public let wakeMinuteOfDay: Int?

    public init(
        cycleStartRaw: String,
        dateEpochDay: Int,
        recoveryScore: Int?,
        restingHeartRate: Double?,
        hrvMs: Double?,
        dayStrain: Double?,
        energyKcal: Double?,
        sleepPerformance: Int?,
        respiratoryRate: Double?,
        sleepDurationHours: Double?,
        sleepDebtHours: Double?,
        bedMinuteOfDay: Int?,
        wakeMinuteOfDay: Int?
    ) {
        self.cycleStartRaw = cycleStartRaw
        self.dateEpochDay = dateEpochDay
        self.recoveryScore = recoveryScore
        self.restingHeartRate = restingHeartRate
        self.hrvMs = hrvMs
        self.dayStrain = dayStrain
        self.energyKcal = energyKcal
        self.sleepPerformance = sleepPerformance
        self.respiratoryRate = respiratoryRate
        self.sleepDurationHours = sleepDurationHours
        self.sleepDebtHours = sleepDebtHours
        self.bedMinuteOfDay = bedMinuteOfDay
        self.wakeMinuteOfDay = wakeMinuteOfDay
    }
}

/// A row of sleeps.csv (naps included; the merger prefers the main non-nap sleep).
public struct WhoopSleepRow: Equatable {
    public let cycleStartRaw: String
    public let dateEpochDay: Int
    public let nap: Bool
    public let sleepPerformance: Int?
    public let respiratoryRate: Double?
    public let sleepDurationHours: Double?
    public let sleepDebtHours: Double?
    public let bedMinuteOfDay: Int?
    public let wakeMinuteOfDay: Int?

    public init(
        cycleStartRaw: String,
        dateEpochDay: Int,
        nap: Bool,
        sleepPerformance: Int?,
        respiratoryRate: Double?,
        sleepDurationHours: Double?,
        sleepDebtHours: Double?,
        bedMinuteOfDay: Int?,
        wakeMinuteOfDay: Int?
    ) {
        self.cycleStartRaw = cycleStartRaw
        self.dateEpochDay = dateEpochDay
        self.nap = nap
        self.sleepPerformance = sleepPerformance
        self.respiratoryRate = respiratoryRate
        self.sleepDurationHours = sleepDurationHours
        self.sleepDebtHours = sleepDebtHours
        self.bedMinuteOfDay = bedMinuteOfDay
        self.wakeMinuteOfDay = wakeMinuteOfDay
    }
}

/// A row of workouts.csv. The date is the calendar day the workout started.
public struct WhoopWorkoutRow: Equatable {
    public let dateEpochDay: Int
    public let startMinuteOfDay: Int?
    public let durationMinutes: Int
    public let activityName: String
    public let strain: Double?
    public let energyKcal: Double?
    public let maxHr: Int?
    public let averageHr: Int?
    /// Zones 1..5; nil when the column is blank.
    public let hrZonePercents: [Double?]

    public init(
        dateEpochDay: Int,
        startMinuteOfDay: Int?,
        durationMinutes: Int,
        activityName: String,
        strain: Double?,
        energyKcal: Double?,
        maxHr: Int?,
        averageHr: Int?,
        hrZonePercents: [Double?]
    ) {
        self.dateEpochDay = dateEpochDay
        self.startMinuteOfDay = startMinuteOfDay
        self.durationMinutes = durationMinutes
        self.activityName = activityName
        self.strain = strain
        self.energyKcal = energyKcal
        self.maxHr = maxHr
        self.averageHr = averageHr
        self.hrZonePercents = hrZonePercents
    }
}

/// A row of journal_entries.csv — one behavior question answered for one cycle.
public struct WhoopJournalRow: Equatable {
    public let cycleStartRaw: String
    public let dateEpochDay: Int
    public let questionText: String
    public let answeredYes: Bool

    public init(cycleStartRaw: String, dateEpochDay: Int, questionText: String, answeredYes: Bool) {
        self.cycleStartRaw = cycleStartRaw
        self.dateEpochDay = dateEpochDay
        self.questionText = questionText
        self.answeredYes = answeredYes
    }
}

// MARK: - Merged records

/// One merged per-day WHOOP record. Plain-struct stand-in for Android's Room `WhoopCycleEntity`
/// with the same fields, so the merge logic stays byte-for-byte comparable across platforms.
public struct WhoopCycleRecord: Codable, Equatable {
    public let dateEpochDay: Int
    public let recoveryScore: Int?
    public let hrvMs: Double?
    public let restingHeartRate: Double?
    public let sleepPerformance: Int?
    public let sleepDurationHours: Double?
    public let sleepDebtHours: Double?
    public let respiratoryRate: Double?
    public let dayStrain: Double?
    public let energyKcal: Double?
    public let bedMinuteOfDay: Int?
    public let wakeMinuteOfDay: Int?
    public let journalCaffeine: Bool?
    public let journalAlcohol: Bool?
    public let journalLateMeal: Bool?
    public let importedAtMillis: Int

    public init(
        dateEpochDay: Int,
        recoveryScore: Int? = nil,
        hrvMs: Double? = nil,
        restingHeartRate: Double? = nil,
        sleepPerformance: Int? = nil,
        sleepDurationHours: Double? = nil,
        sleepDebtHours: Double? = nil,
        respiratoryRate: Double? = nil,
        dayStrain: Double? = nil,
        energyKcal: Double? = nil,
        bedMinuteOfDay: Int? = nil,
        wakeMinuteOfDay: Int? = nil,
        journalCaffeine: Bool? = nil,
        journalAlcohol: Bool? = nil,
        journalLateMeal: Bool? = nil,
        importedAtMillis: Int = 0
    ) {
        self.dateEpochDay = dateEpochDay
        self.recoveryScore = recoveryScore
        self.hrvMs = hrvMs
        self.restingHeartRate = restingHeartRate
        self.sleepPerformance = sleepPerformance
        self.sleepDurationHours = sleepDurationHours
        self.sleepDebtHours = sleepDebtHours
        self.respiratoryRate = respiratoryRate
        self.dayStrain = dayStrain
        self.energyKcal = energyKcal
        self.bedMinuteOfDay = bedMinuteOfDay
        self.wakeMinuteOfDay = wakeMinuteOfDay
        self.journalCaffeine = journalCaffeine
        self.journalAlcohol = journalAlcohol
        self.journalLateMeal = journalLateMeal
        self.importedAtMillis = importedAtMillis
    }

    /// The wearable-agnostic snapshot this record feeds into the coaching engine. Real imported
    /// data, so `isMock` is always false.
    public func toSnapshot() -> WhoopSnapshot {
        WhoopSnapshot(
            dateEpochDay: dateEpochDay,
            recoveryScore: recoveryScore,
            hrvMs: hrvMs,
            restingHeartRate: restingHeartRate,
            sleepPerformance: sleepPerformance,
            sleepDurationHours: sleepDurationHours,
            sleepDebtHours: sleepDebtHours,
            respiratoryRate: respiratoryRate,
            dayStrain: dayStrain,
            energyKcal: energyKcal,
            bedMinuteOfDay: bedMinuteOfDay,
            wakeMinuteOfDay: wakeMinuteOfDay,
            isMock: false
        )
    }
}

/// One recorded workout from the WHOOP export (workouts.csv). Plain-struct stand-in for
/// Android's Room `WhoopWorkoutEntity` with the same fields.
public struct WhoopWorkoutRecord: Codable, Equatable {
    /// "<dateEpochDay>:<startMinuteOfDay>:<activityName>" — the natural dedupe key.
    public let id: String
    public let dateEpochDay: Int
    public let startMinuteOfDay: Int?
    public let durationMinutes: Int
    public let activityName: String
    public let strain: Double?
    public let energyKcal: Double?
    public let maxHr: Int?
    public let averageHr: Int?
    public let hrZone1Pct: Double?
    public let hrZone2Pct: Double?
    public let hrZone3Pct: Double?
    public let hrZone4Pct: Double?
    public let hrZone5Pct: Double?
    public let importedAtMillis: Int

    public init(
        id: String,
        dateEpochDay: Int,
        startMinuteOfDay: Int? = nil,
        durationMinutes: Int,
        activityName: String,
        strain: Double? = nil,
        energyKcal: Double? = nil,
        maxHr: Int? = nil,
        averageHr: Int? = nil,
        hrZone1Pct: Double? = nil,
        hrZone2Pct: Double? = nil,
        hrZone3Pct: Double? = nil,
        hrZone4Pct: Double? = nil,
        hrZone5Pct: Double? = nil,
        importedAtMillis: Int = 0
    ) {
        self.id = id
        self.dateEpochDay = dateEpochDay
        self.startMinuteOfDay = startMinuteOfDay
        self.durationMinutes = durationMinutes
        self.activityName = activityName
        self.strain = strain
        self.energyKcal = energyKcal
        self.maxHr = maxHr
        self.averageHr = averageHr
        self.hrZone1Pct = hrZone1Pct
        self.hrZone2Pct = hrZone2Pct
        self.hrZone3Pct = hrZone3Pct
        self.hrZone4Pct = hrZone4Pct
        self.hrZone5Pct = hrZone5Pct
        self.importedAtMillis = importedAtMillis
    }
}

/// Everything one import produced, ready to persist.
public struct WhoopImportData: Equatable {
    public let cycles: [WhoopCycleRecord]
    public let workouts: [WhoopWorkoutRecord]

    public init(cycles: [WhoopCycleRecord], workouts: [WhoopWorkoutRecord]) {
        self.cycles = cycles
        self.workouts = workouts
    }

    public var snapshots: [WhoopSnapshot] { cycles.map { $0.toSnapshot() } }

    /// Real minutes of recorded exercise per calendar day, for the EXERCISE_MINUTES trend.
    public var exerciseMinutesByDay: [Int: Int] {
        workouts.reduce(into: [:]) { totals, workout in
            totals[workout.dateEpochDay, default: 0] += workout.durationMinutes
        }
    }
}

// MARK: - Parser

/// Parses the CSV files inside a WHOOP data export ("my_whoop_data_*") into typed rows —
/// pure Foundation, no UIKit dependencies, so the whole format lives under unit test.
///
/// Format notes, learned from real exports:
///  - Timestamps ("2026-07-13 01:47:04") are LOCAL time; the "Cycle timezone" column names the
///    offset but the wall-clock values are already local, so they are used as-is. They are parsed
///    with a fixed `en_US_POSIX` locale and a FIXED UTC calendar (never the device timezone), so
///    "epoch day" here means the LOCAL calendar day on the wall clock and minute-of-day is the
///    wall-clock minute — the same date math as Android's `LocalDateTime`, deterministic across
///    device timezones and DST.
///  - A cycle's day is the calendar date the user WOKE UP (a cycle that starts 23:09 belongs to
///    the next morning). When wake onset is blank, the cycle start date is the fallback.
///  - Blank cells are common (no-sleep cycles, missing SpO2) and must parse to nil, not fail.
///  - Sleep debt / asleep durations are exported in minutes; Spartan models hours.
public enum WhoopCsvParser {

    /// Detects the file kind from the header and parses it, or nil if this isn't a WHOOP CSV.
    public static func parse(_ text: String) -> ParsedWhoopFile? {
        let rows = tokenize(text)
        guard let headerRow = rows.first else { return nil }
        let header = headerRow.map { $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }
        let body = rows.dropFirst().filter { row in row.contains { !isBlank($0) } }
        func cell(_ row: [String], _ name: String) -> String? {
            guard let index = header.firstIndex(of: name), index < row.count else { return nil }
            let value = row[index].trimmingCharacters(in: .whitespacesAndNewlines)
            return value.isEmpty ? nil : value
        }

        if header.contains("question text") {
            return ParsedWhoopFile(
                kind: .journalEntries,
                journal: body.compactMap { row in
                    guard let start = cell(row, "cycle start time"),
                          let question = cell(row, "question text"),
                          let day = stamp(start)?.epochDay else { return nil }
                    return WhoopJournalRow(
                        cycleStartRaw: start,
                        dateEpochDay: day,
                        questionText: question,
                        answeredYes: cell(row, "answered yes")?.lowercased() == "true"
                    )
                }
            )
        }

        if header.contains("workout start time") {
            return ParsedWhoopFile(
                kind: .workouts,
                workouts: body.compactMap { row in
                    guard let start = stamp(cell(row, "workout start time")),
                          let duration = intValue(cell(row, "duration (min)")) else { return nil }
                    return WhoopWorkoutRow(
                        dateEpochDay: start.epochDay,
                        startMinuteOfDay: start.minuteOfDay,
                        durationMinutes: duration,
                        activityName: cell(row, "activity name") ?? "Activity",
                        strain: doubleValue(cell(row, "activity strain")),
                        energyKcal: doubleValue(cell(row, "energy burned (cal)")),
                        maxHr: intValue(cell(row, "max hr (bpm)")),
                        averageHr: intValue(cell(row, "average hr (bpm)")),
                        hrZonePercents: (1...5).map { zone in doubleValue(cell(row, "hr zone \(zone) %")) }
                    )
                }
            )
        }

        if header.contains("nap") {
            return ParsedWhoopFile(
                kind: .sleeps,
                sleeps: body.compactMap { row in
                    guard let cycleStart = cell(row, "cycle start time") else { return nil }
                    let onset = stamp(cell(row, "sleep onset"))
                    let wake = stamp(cell(row, "wake onset"))
                    guard let day = (wake ?? onset ?? stamp(cycleStart))?.epochDay else { return nil }
                    return WhoopSleepRow(
                        cycleStartRaw: cycleStart,
                        dateEpochDay: day,
                        nap: cell(row, "nap")?.lowercased() == "true",
                        sleepPerformance: intValue(cell(row, "sleep performance %")),
                        respiratoryRate: doubleValue(cell(row, "respiratory rate (rpm)")),
                        sleepDurationHours: minutesToHours(cell(row, "asleep duration (min)")),
                        sleepDebtHours: minutesToHours(cell(row, "sleep debt (min)")),
                        bedMinuteOfDay: onset?.minuteOfDay,
                        wakeMinuteOfDay: wake?.minuteOfDay
                    )
                }
            )
        }

        if header.contains("recovery score %") {
            return ParsedWhoopFile(
                kind: .physiologicalCycles,
                cycles: body.compactMap { row in
                    guard let cycleStart = cell(row, "cycle start time") else { return nil }
                    let startAt = stamp(cycleStart)
                    let onset = stamp(cell(row, "sleep onset"))
                    let wake = stamp(cell(row, "wake onset"))
                    // The recovery in a cycle describes the day the user woke into.
                    guard let day = (wake ?? startAt)?.epochDay else { return nil }
                    return WhoopCycleRow(
                        cycleStartRaw: cycleStart,
                        dateEpochDay: day,
                        recoveryScore: intValue(cell(row, "recovery score %")),
                        restingHeartRate: doubleValue(cell(row, "resting heart rate (bpm)")),
                        hrvMs: doubleValue(cell(row, "heart rate variability (ms)")),
                        dayStrain: doubleValue(cell(row, "day strain")),
                        energyKcal: doubleValue(cell(row, "energy burned (cal)")),
                        sleepPerformance: intValue(cell(row, "sleep performance %")),
                        respiratoryRate: doubleValue(cell(row, "respiratory rate (rpm)")),
                        sleepDurationHours: minutesToHours(cell(row, "asleep duration (min)")),
                        sleepDebtHours: minutesToHours(cell(row, "sleep debt (min)")),
                        bedMinuteOfDay: onset?.minuteOfDay,
                        wakeMinuteOfDay: wake?.minuteOfDay
                    )
                }
            )
        }

        return nil
    }

    // MARK: Value helpers

    private static func doubleValue(_ cell: String?) -> Double? {
        guard let cell = cell, let value = Double(cell), value.isFinite else { return nil }
        return value
    }

    /// Numeric cells like "75" or "75.0" both map to Int, truncating like Kotlin's `Double.toInt()`.
    private static func intValue(_ cell: String?) -> Int? {
        doubleValue(cell).map { Int($0) }
    }

    /// Minutes → hours, rounded to 2 decimals so 83 min reads as 1.38 h, not 1.3833333333333333.
    private static func minutesToHours(_ cell: String?) -> Double? {
        doubleValue(cell).map { ($0 / 60.0 * 100.0).rounded() / 100.0 }
    }

    private static func isBlank(_ value: String) -> Bool {
        value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    // MARK: Timestamp parsing

    /// A local wall-clock timestamp reduced to what the import needs: which LOCAL calendar day
    /// it falls on (as days since 1970-01-01 of that wall-clock date) and the minute of that day.
    private struct LocalStamp {
        let epochDay: Int
        let minuteOfDay: Int
    }

    private static let secondsFormatter = makeFormatter("yyyy-MM-dd'T'HH:mm:ss")
    private static let minutesFormatter = makeFormatter("yyyy-MM-dd'T'HH:mm")

    /// Fixed locale + fixed UTC zone: the export's wall-clock strings are interpreted as-is, so
    /// day and minute-of-day math never shifts with the device timezone or DST.
    private static func makeFormatter(_ format: String) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.dateFormat = format
        return formatter
    }

    private static func stamp(_ raw: String?) -> LocalStamp? {
        guard var trimmed = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else { return nil }
        // Export format "yyyy-MM-dd HH:mm:ss"; tolerate a T separator and fractional seconds
        // (which cannot change the day or the minute, so they are simply dropped).
        trimmed = trimmed.replacingOccurrences(of: " ", with: "T")
        if let dot = trimmed.firstIndex(of: ".") { trimmed = String(trimmed[..<dot]) }
        guard let date = secondsFormatter.date(from: trimmed) ?? minutesFormatter.date(from: trimmed) else { return nil }
        let seconds = Int(date.timeIntervalSince1970.rounded())
        let day = Int(floor(Double(seconds) / 86_400.0))
        let secondOfDay = seconds - day * 86_400
        return LocalStamp(epochDay: day, minuteOfDay: secondOfDay / 60)
    }

    // MARK: CSV tokenizer (RFC 4180: quoted fields may contain commas, quotes, and newlines)

    private static func tokenize(_ text: String) -> [[String]] {
        var rows: [[String]] = []
        var field = ""
        var row: [String] = []
        var inQuotes = false
        var i = 0
        let stripped = text.hasPrefix("\u{FEFF}") ? String(text.dropFirst()) : text
        // Unicode scalars, not Characters: "\r\n" must stay two units for CRLF handling.
        let src = Array(stripped.unicodeScalars)

        func endField() { row.append(field); field = "" }
        func endRow() { endField(); rows.append(row); row = [] }

        while i < src.count {
            let ch = src[i]
            if inQuotes && ch == "\"" && i + 1 < src.count && src[i + 1] == "\"" {
                field.append("\"")
                i += 1
            } else if ch == "\"" {
                inQuotes.toggle()
            } else if !inQuotes && ch == "," {
                endField()
            } else if !inQuotes && (ch == "\n" || ch == "\r") {
                if ch == "\r" && i + 1 < src.count && src[i + 1] == "\n" { i += 1 }
                endRow()
            } else {
                field.unicodeScalars.append(ch)
            }
            i += 1
        }
        if !field.isEmpty || !row.isEmpty { endRow() }
        return rows.filter { r in r.contains { !isBlank($0) } }
    }
}

// MARK: - Merger

/// Joins the parsed WHOOP export files into one per-day record set. physiological_cycles.csv is
/// authoritative for recovery/strain/vitals; sleeps.csv refines bed/wake times to the MAIN sleep
/// (the cycles file sometimes lists a nap's window); journal_entries.csv contributes behavior
/// flags; workouts.csv becomes its own record list. Pure Foundation — unit-tested against
/// export fixtures.
public enum WhoopCsvMerger {

    public static func merge(files: [ParsedWhoopFile], importedAtMillis: Int) -> WhoopImportData {
        let cycleRows = files.flatMap { $0.cycles }
        let sleepRows = files.flatMap { $0.sleeps }
        let journalRows = files.flatMap { $0.journal }
        let workoutRows = files.flatMap { $0.workouts }

        // Main (non-nap) sleep per cycle, longest first when a cycle has several. The order list
        // preserves first-appearance iteration order (Kotlin's LinkedHashMap semantics).
        var mainSleepByCycle: [String: WhoopSleepRow] = [:]
        var mainSleepCycleOrder: [String] = []
        for sleep in sleepRows where !sleep.nap {
            if let current = mainSleepByCycle[sleep.cycleStartRaw] {
                if (sleep.sleepDurationHours ?? 0.0) > (current.sleepDurationHours ?? 0.0) {
                    mainSleepByCycle[sleep.cycleStartRaw] = sleep
                }
            } else {
                mainSleepByCycle[sleep.cycleStartRaw] = sleep
                mainSleepCycleOrder.append(sleep.cycleStartRaw)
            }
        }

        let journalByCycle: [String: JournalFlags] = Dictionary(grouping: journalRows) { $0.cycleStartRaw }
            .mapValues { rows in
                JournalFlags(
                    caffeine: answerTo(rows, "caffeine"),
                    alcohol: answerTo(rows, "alcohol"),
                    lateMeal: answerTo(rows, "food close to bedtime")
                )
            }

        let fromCycles = cycleRows.map { c -> WhoopCycleRecord in
            let sleep = mainSleepByCycle[c.cycleStartRaw]
            let journal = journalByCycle[c.cycleStartRaw]
            return WhoopCycleRecord(
                dateEpochDay: c.dateEpochDay,
                recoveryScore: c.recoveryScore,
                hrvMs: c.hrvMs,
                restingHeartRate: c.restingHeartRate,
                sleepPerformance: c.sleepPerformance,
                sleepDurationHours: c.sleepDurationHours,
                sleepDebtHours: c.sleepDebtHours,
                respiratoryRate: c.respiratoryRate,
                dayStrain: c.dayStrain,
                energyKcal: c.energyKcal,
                bedMinuteOfDay: sleep?.bedMinuteOfDay ?? c.bedMinuteOfDay,
                wakeMinuteOfDay: sleep?.wakeMinuteOfDay ?? c.wakeMinuteOfDay,
                journalCaffeine: journal?.caffeine,
                journalAlcohol: journal?.alcohol,
                journalLateMeal: journal?.lateMeal,
                importedAtMillis: importedAtMillis
            )
        }

        // Sleeps with no cycle row (user imported only sleeps.csv): keep the sleep-side fields
        // so the sleep trends still populate rather than dropping the day.
        let cycleDays = Set(fromCycles.map { $0.dateEpochDay })
        let fromSleepsOnly = mainSleepCycleOrder
            .compactMap { mainSleepByCycle[$0] }
            .filter { !cycleDays.contains($0.dateEpochDay) }
            .map { s -> WhoopCycleRecord in
                WhoopCycleRecord(
                    dateEpochDay: s.dateEpochDay,
                    recoveryScore: nil,
                    hrvMs: nil,
                    restingHeartRate: nil,
                    sleepPerformance: s.sleepPerformance,
                    sleepDurationHours: s.sleepDurationHours,
                    sleepDebtHours: s.sleepDebtHours,
                    respiratoryRate: s.respiratoryRate,
                    dayStrain: nil,
                    energyKcal: nil,
                    bedMinuteOfDay: s.bedMinuteOfDay,
                    wakeMinuteOfDay: s.wakeMinuteOfDay,
                    journalCaffeine: journalByCycle[s.cycleStartRaw]?.caffeine,
                    journalAlcohol: journalByCycle[s.cycleStartRaw]?.alcohol,
                    journalLateMeal: journalByCycle[s.cycleStartRaw]?.lateMeal,
                    importedAtMillis: importedAtMillis
                )
            }

        // One record per day: when two cycles land on the same date, keep the richer one.
        let cycles = Dictionary(grouping: fromCycles + fromSleepsOnly) { $0.dateEpochDay }
            .values
            .compactMap { candidates in candidates.max { nonNullFieldCount($0) < nonNullFieldCount($1) } }
            .sorted { $0.dateEpochDay < $1.dateEpochDay }

        var seenWorkoutIds = Set<String>()
        var workouts: [WhoopWorkoutRecord] = []
        for w in workoutRows {
            let record = WhoopWorkoutRecord(
                id: "\(w.dateEpochDay):\(w.startMinuteOfDay ?? 0):\(w.activityName)",
                dateEpochDay: w.dateEpochDay,
                startMinuteOfDay: w.startMinuteOfDay,
                durationMinutes: w.durationMinutes,
                activityName: w.activityName,
                strain: w.strain,
                energyKcal: w.energyKcal,
                maxHr: w.maxHr,
                averageHr: w.averageHr,
                hrZone1Pct: w.hrZonePercents.count > 0 ? w.hrZonePercents[0] : nil,
                hrZone2Pct: w.hrZonePercents.count > 1 ? w.hrZonePercents[1] : nil,
                hrZone3Pct: w.hrZonePercents.count > 2 ? w.hrZonePercents[2] : nil,
                hrZone4Pct: w.hrZonePercents.count > 3 ? w.hrZonePercents[3] : nil,
                hrZone5Pct: w.hrZonePercents.count > 4 ? w.hrZonePercents[4] : nil,
                importedAtMillis: importedAtMillis
            )
            if seenWorkoutIds.insert(record.id).inserted { workouts.append(record) }
        }
        workouts.sort {
            ($0.dateEpochDay, $0.startMinuteOfDay ?? 0) < ($1.dateEpochDay, $1.startMinuteOfDay ?? 0)
        }

        return WhoopImportData(cycles: cycles, workouts: workouts)
    }

    private struct JournalFlags {
        let caffeine: Bool?
        let alcohol: Bool?
        let lateMeal: Bool?
    }

    /// The yes/no answer to the question containing `keyword`, or nil if it was never asked.
    private static func answerTo(_ rows: [WhoopJournalRow], _ keyword: String) -> Bool? {
        let asked = rows.filter { $0.questionText.range(of: keyword, options: .caseInsensitive) != nil }
        guard !asked.isEmpty else { return nil }
        return asked.contains { $0.answeredYes }
    }

    private static func nonNullFieldCount(_ record: WhoopCycleRecord) -> Int {
        let fields: [Any?] = [
            record.recoveryScore, record.hrvMs, record.restingHeartRate, record.sleepPerformance,
            record.sleepDurationHours, record.sleepDebtHours, record.respiratoryRate,
            record.dayStrain, record.energyKcal, record.bedMinuteOfDay, record.wakeMinuteOfDay,
        ]
        return fields.filter { $0 != nil }.count
    }
}
