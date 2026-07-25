import Foundation

/// ISO weekday, Monday-first, with raw values matching `java.time.DayOfWeek` ordinals
/// (Monday = 0 … Sunday = 6) so `PressureWindow.daysOfWeekMask` bit math is identical across
/// platforms.
public enum DayOfWeek: Int, Codable, CaseIterable, Equatable {
    case monday = 0, tuesday, wednesday, thursday, friday, saturday, sunday

    /// The following weekday, wrapping like Kotlin's `DayOfWeek.plus(1)` (Sunday → Monday).
    public var next: DayOfWeek { DayOfWeek(rawValue: (rawValue + 1) % 7)! }

    /// "Monday" … "Sunday" — how Android renders `name.lowercase().replaceFirstChar { … }`.
    public var displayName: String {
        switch self {
        case .monday: return "Monday"
        case .tuesday: return "Tuesday"
        case .wednesday: return "Wednesday"
        case .thursday: return "Thursday"
        case .friday: return "Friday"
        case .saturday: return "Saturday"
        case .sunday: return "Sunday"
        }
    }

    /// Weekday of an epoch day under the repo's fixed-UTC epoch-day convention (day 0 =
    /// Thursday 1970-01-01, same as `java.time.LocalDate.ofEpochDay`). Floored modulo keeps
    /// pre-1970 days correct.
    public static func from(epochDay: Int) -> DayOfWeek {
        DayOfWeek(rawValue: (((epochDay + 3) % 7) + 7) % 7)!
    }
}

public struct WeekdayEffect: Equatable {
    /// The weekday whose FOLLOWING morning runs low (the stressor day, e.g. Tuesday).
    public let stressorDay: DayOfWeek
    /// Mean next-morning recovery delta vs the personal mean (negative = worse).
    public let recoveryDelta: Double
    /// Number of weeks of evidence behind the effect.
    public let sampleWeeks: Int
    public let insight: String

    public init(stressorDay: DayOfWeek, recoveryDelta: Double, sampleWeeks: Int, insight: String) {
        self.stressorDay = stressorDay
        self.recoveryDelta = recoveryDelta
        self.sampleWeeks = sampleWeeks
        self.insight = insight
    }
}

/// Finds honest stress patterns in the user's own imported data and turns declared high-pressure
/// windows into pre-emptive calm. The WHOOP CSV export has no intraday stress stream, so this
/// engine never pretends to see one (docs/COACH_DESIGN.md §5):
///
///  1. Weekday effect — which weekday's *following morning* runs below the personal average
///     (recovery/HRV), gated on sample size and effect size before anything is shown.
///  2. Pressure windows — user-declared recurring blocks (Tue/Thu 11:00–12:00); Spartan schedules
///     a short breathwork five minutes before each one on matching days.
///
/// Faithful port of `com.spartan.domain.engine.StressPatterns` (Android): same gates, same
/// worst-first ordering, same insight and schedule copy. Dates are epoch days (Swift convention)
/// instead of `java.time.LocalDate`; `DayOfWeek.from(epochDay:)` reproduces
/// `LocalDate.ofEpochDay(...).dayOfWeek` exactly.
public enum StressPatterns {

    /// Minimum observations of a weekday before an effect is trusted.
    public static let minSamples = 3

    /// Minimum recovery-point deficit before an effect is worth surfacing.
    public static let minEffectRecoveryPoints = 8.0

    /// Lead time for the pre-window breathwork nudge.
    public static let preWindowLeadMinutes = 5

    /// Weekday effects from imported daily snapshots: for each weekday D, compare mean recovery on
    /// the morning AFTER D (i.e. dates whose previous day is D) against the overall mean. Sorted
    /// worst-first; empty when nothing clears the sample/effect gates.
    public static func weekdayEffects(_ snapshots: [WhoopSnapshot]) -> [WeekdayEffect] {
        let withRecovery = snapshots.filter { $0.recoveryScore != nil }
        if withRecovery.count < minSamples * 2 { return [] }
        let allScores = withRecovery.compactMap { $0.recoveryScore }
        let overallMean = Double(allScores.reduce(0, +)) / Double(allScores.count)

        let effects: [WeekdayEffect] = DayOfWeek.allCases.compactMap { stressor in
            // Mornings that follow the stressor day.
            let mornings = withRecovery.filter {
                DayOfWeek.from(epochDay: $0.dateEpochDay) == stressor.next
            }
            guard mornings.count >= minSamples else { return nil }
            let morningScores = mornings.compactMap { $0.recoveryScore }
            let delta = Double(morningScores.reduce(0, +)) / Double(morningScores.count) - overallMean
            guard delta <= -minEffectRecoveryPoints else { return nil }
            let dayName: String = stressor.displayName
            let deficitInt: Int = Int(abs(delta))
            let weeks: Int = mornings.count
            var insight = "Mornings after \(dayName)s run about \(deficitInt) "
            insight += "recovery points below your average (\(weeks) weeks of data). A "
            insight += "\(dayName) evening wind-down or an easier \(dayName) "
            insight += "could pay off the next day."
            return WeekdayEffect(
                stressorDay: stressor,
                recoveryDelta: delta,
                sampleWeeks: mornings.count,
                insight: insight
            )
        }
        // Worst-first; stable like Kotlin's sortedBy, so equal deltas keep weekday order.
        return effects.enumerated()
            .sorted { lhs, rhs in
                if lhs.element.recoveryDelta != rhs.element.recoveryDelta {
                    return lhs.element.recoveryDelta < rhs.element.recoveryDelta
                }
                return lhs.offset < rhs.offset
            }
            .map { $0.element }
    }

    /// The pressure windows that occur on `dateEpochDay`, soonest first.
    public static func windowsForDay(_ windows: [PressureWindow], dateEpochDay: Int) -> [PressureWindow] {
        let ordinal = DayOfWeek.from(epochDay: dateEpochDay).rawValue
        return windows
            .filter { ($0.daysOfWeekMask >> ordinal) & 1 == 1 }
            .enumerated()
            .sorted { lhs, rhs in
                if lhs.element.startMinuteOfDay != rhs.element.startMinuteOfDay {
                    return lhs.element.startMinuteOfDay < rhs.element.startMinuteOfDay
                }
                return lhs.offset < rhs.offset
            }
            .map { $0.element }
    }

    /// Minute-of-day the pre-window breathwork nudge should fire for `window`.
    public static func nudgeMinuteFor(_ window: PressureWindow) -> Int {
        max(window.startMinuteOfDay - preWindowLeadMinutes, 0)
    }

    /// Human summary of a window's schedule, e.g. "Tue, Thu 11:00–12:00".
    public static func describeWindow(_ window: PressureWindow) -> String {
        let days = DayOfWeek.allCases
            .filter { (window.daysOfWeekMask >> $0.rawValue) & 1 == 1 }
            .map { String($0.displayName.prefix(3)) }
            .joined(separator: ", ")
        return "\(days) \(clock(window.startMinuteOfDay))–\(clock(window.endMinuteOfDay))"
    }

    private static func clock(_ minuteOfDay: Int) -> String {
        String(format: "%d:%02d", minuteOfDay / 60, minuteOfDay % 60)
    }
}
