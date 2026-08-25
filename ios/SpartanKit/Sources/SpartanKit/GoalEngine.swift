import Foundation

/// Personal goals, validated against evidence-based safe rates and tracked from the user's own
/// readings. A goal that asks for too much is never refused rudely — the engine counter-offers
/// the nearest safe version (docs/COACH_DESIGN.md §4). Pure Swift, fully unit-tested.
///
/// Faithful port of `com.spartan.domain.engine.GoalEngine` (Android): same caps, same
/// counter-offer arithmetic, same kg→lb conversion, same summary copy. One structural deviation:
/// Android keys readings by its full `MetricType` catalog (which includes WEIGHT); SpartanKit's
/// `MetricType` carries only the nine WHOOP metrics, so the goal layer defines its own reading
/// key (`GoalMetric`) with the one extra case it needs. Raw values match the Android enum names.
public enum GoalType: String, Codable, CaseIterable, Equatable {
    case weightLoss = "WEIGHT_LOSS"
    case sleepRecovery = "SLEEP_RECOVERY"
    case stressResilience = "STRESS_RESILIENCE"
}

public enum GoalStatus: String, Codable, CaseIterable, Equatable {
    case active = "ACTIVE"
    case completed = "COMPLETED"
    case abandoned = "ABANDONED"
}

/// A recurring high-pressure window the user declared (e.g. Tue/Thu 11:00–12:00).
public struct PressureWindow: Codable, Equatable {
    public let id: String
    /// Bit 0 = Monday … bit 6 = Sunday (ISO order, matches `DayOfWeek` raw values).
    public let daysOfWeekMask: Int
    public let startMinuteOfDay: Int
    public let endMinuteOfDay: Int
    public let label: String

    public init(id: String, daysOfWeekMask: Int, startMinuteOfDay: Int, endMinuteOfDay: Int, label: String = "") {
        self.id = id
        self.daysOfWeekMask = daysOfWeekMask
        self.startMinuteOfDay = startMinuteOfDay
        self.endMinuteOfDay = endMinuteOfDay
        self.label = label
    }
}

public struct Goal: Codable, Equatable {
    public let id: String
    public let type: GoalType
    /// WEIGHT_LOSS: pounds to lose. SLEEP_RECOVERY: percent improvement. STRESS: sessions/week.
    public let targetValue: Double
    /// Captured at creation from the user's data (start weight kg / baseline sleep perf %).
    public let baselineValue: Double?
    public let startEpochDay: Int
    public let targetEpochDay: Int
    public let status: GoalStatus

    public init(
        id: String,
        type: GoalType,
        targetValue: Double,
        baselineValue: Double?,
        startEpochDay: Int,
        targetEpochDay: Int,
        status: GoalStatus = .active
    ) {
        self.id = id
        self.type = type
        self.targetValue = targetValue
        self.baselineValue = baselineValue
        self.startEpochDay = startEpochDay
        self.targetEpochDay = targetEpochDay
        self.status = status
    }

    public var totalWeeks: Double {
        Double(max(targetEpochDay - startEpochDay, 1)) / 7.0
    }

    /// Kotlin `copy(targetEpochDay = …)` analog for the counter-offer path.
    public func with(targetEpochDay: Int) -> Goal {
        Goal(
            id: id, type: type, targetValue: targetValue, baselineValue: baselineValue,
            startEpochDay: startEpochDay, targetEpochDay: targetEpochDay, status: status
        )
    }
}

/// Outcome of validating a requested goal.
public enum GoalValidation: Equatable {
    case ok(Goal)

    /// The ask exceeds a safe rate; `adjusted` is the nearest safe version, `why` explains it.
    case adjusted(adjusted: Goal, why: String)

    case invalid(why: String)
}

/// How an active goal bends the daily/weekly plan. Consumed by the coaching pipeline.
public struct GoalPlanModifiers: Equatable {
    public let extraZone2MinutesPerWeek: Int
    public let protectStrengthSessions: Int
    public let emphasizeWindDown: Bool
    public let preWindowBreathwork: [PressureWindow]

    public init(
        extraZone2MinutesPerWeek: Int = 0,
        protectStrengthSessions: Int = 0,
        emphasizeWindDown: Bool = false,
        preWindowBreathwork: [PressureWindow] = []
    ) {
        self.extraZone2MinutesPerWeek = extraZone2MinutesPerWeek
        self.protectStrengthSessions = protectStrengthSessions
        self.emphasizeWindDown = emphasizeWindDown
        self.preWindowBreathwork = preWindowBreathwork
    }
}

public struct GoalProgress: Equatable {
    /// 0..1+ fraction of the target achieved so far (7-day-averaged, baseline-relative).
    public let fraction: Double
    /// 0..1 fraction of the goal period elapsed.
    public let timeFraction: Double
    public let onTrack: Bool
    public let summary: String

    public init(fraction: Double, timeFraction: Double, onTrack: Bool, summary: String) {
        self.fraction = fraction
        self.timeFraction = timeFraction
        self.onTrack = onTrack
        self.summary = summary
    }
}

/// The reading types goals track. Mirrors the relevant slice of Android's `MetricType` (raw
/// values and labels match); WEIGHT exists only here because the Swift `MetricType` is pinned to
/// the nine WHOOP metrics by the explainer suite.
public enum GoalMetric: String, Codable, CaseIterable, Equatable {
    case weight = "WEIGHT"
    case sleepPerformance = "SLEEP_PERFORMANCE"
    case hrvRmssd = "HRV_RMSSD"

    /// Human label, mirroring Android's `MetricType.label` for these cases.
    public var label: String {
        switch self {
        case .weight: return "Weight"
        case .sleepPerformance: return "Sleep performance"
        case .hrvRmssd: return "HRV"
        }
    }
}

/// One logged reading for a goal metric (weight in kg, sleep performance in %, HRV in ms).
public struct MetricReading: Codable, Equatable {
    public let type: GoalMetric
    public let value: Double?
    public let recordedAtEpochDay: Int
    public let note: String

    public init(type: GoalMetric, value: Double?, recordedAtEpochDay: Int, note: String = "") {
        self.type = type
        self.value = value
        self.recordedAtEpochDay = recordedAtEpochDay
        self.note = note
    }
}

public enum GoalEngine {

    /// CDC gradual-loss guidance: sustainable loss is roughly 1–2 lb per week.
    public static let maxWeightLossLbPerWeek = 2.0

    /// Weight readings are stored in kg; goals speak pounds.
    public static let kgToLb = 2.20462

    /// Recovery/sleep-performance gains beyond ~5%/week vs baseline are not realistic asks.
    public static let maxSleepGainPercentPerWeek = 5.0

    public static func validate(_ requested: Goal) -> GoalValidation {
        if requested.targetValue <= 0.0 { return .invalid(why: "Pick a target above zero.") }
        if requested.targetEpochDay <= requested.startEpochDay {
            return .invalid(why: "Give the goal at least a week of runway.")
        }
        switch requested.type {
        case .weightLoss:
            let rate = requested.targetValue / requested.totalWeeks
            if rate <= maxWeightLossLbPerWeek { return .ok(requested) }
            let safeWeeks: Int = Int(ceil(requested.targetValue / maxWeightLossLbPerWeek))
            let targetInt: Int = Int(requested.targetValue)
            var why = "Sustainable loss is about 1–2 lb per week, so "
            why += "\(targetInt) lb fits better in \(safeWeeks) weeks. "
            why += "Faster than that tends to rebound."
            return .adjusted(
                adjusted: requested.with(targetEpochDay: requested.startEpochDay + safeWeeks * 7),
                why: why
            )
        case .sleepRecovery:
            let rate = requested.targetValue / requested.totalWeeks
            if rate <= maxSleepGainPercentPerWeek { return .ok(requested) }
            let safeWeeks: Int = Int(ceil(requested.targetValue / maxSleepGainPercentPerWeek))
            let targetInt: Int = Int(requested.targetValue)
            var why = "Sleep gains compound gradually — aim for "
            why += "\(targetInt)% over \(safeWeeks) weeks of "
            why += "consistent bed and wake times."
            return .adjusted(
                adjusted: requested.with(targetEpochDay: requested.startEpochDay + safeWeeks * 7),
                why: why
            )
        case .stressResilience:
            return .ok(requested)
        }
    }

    /// Plan emphasis for the active goal; the recovery-gated daily engine still owns the floor.
    public static func planModifiers(_ goal: Goal?, windows: [PressureWindow]) -> GoalPlanModifiers {
        switch goal?.type {
        case nil:
            return GoalPlanModifiers(preWindowBreathwork: windows)
        case .weightLoss:
            return GoalPlanModifiers(
                extraZone2MinutesPerWeek: 60,
                protectStrengthSessions: 2,
                preWindowBreathwork: windows
            )
        case .sleepRecovery:
            return GoalPlanModifiers(
                emphasizeWindDown: true,
                preWindowBreathwork: windows
            )
        case .stressResilience:
            return GoalPlanModifiers(
                emphasizeWindDown: true,
                preWindowBreathwork: windows
            )
        }
    }

    /// Progress from the user's own readings. `readings` are raw entries; the relevant metric is
    /// trailing-7-day averaged so a single odd day never whipsaws the goal card. Weight readings
    /// are stored in kg and converted here, because goals speak pounds.
    /// `breathworkThisWeek` feeds the STRESS_RESILIENCE habit goal (sessions completed this week).
    public static func progress(
        _ goal: Goal,
        readings: [MetricReading],
        todayEpochDay: Int,
        breathworkThisWeek: Int = 0
    ) -> GoalProgress {
        let elapsedDays: Double = Double(todayEpochDay - goal.startEpochDay)
        let runwayDays: Double = Double(max(goal.targetEpochDay - goal.startEpochDay, 1))
        let timeFraction: Double = min(max(elapsedDays / runwayDays, 0.0), 1.0)
        let remainingDays: Double = Double(goal.targetEpochDay - todayEpochDay)
        let remainingWeeks: Int = max(Int(ceil(remainingDays / 7.0)), 0)

        // Stress resilience is a habit goal: showing up this week IS the progress; the HRV trend
        // rides along as context, never as a pass/fail judgment.
        if goal.type == .stressResilience {
            let weeklyTarget: Double = max(goal.targetValue, 1.0)
            let fraction: Double = min(max(Double(breathworkThisWeek) / weeklyTarget, 0.0), 1.0)
            let hrvNow = trailingAverage(readings, type: .hrvRmssd, todayEpochDay: todayEpochDay)
            var hrvNote = ""
            if let baseline = goal.baselineValue, let hrvNow = hrvNow {
                let d: Double = hrvNow - baseline
                let sign: String = d >= 0 ? "+" : ""
                let deltaStr: String = roundTo1(d)
                hrvNote = " · HRV \(sign)\(deltaStr) ms vs baseline"
            }
            let weeklyTargetInt: Int = Int(weeklyTarget)
            return GoalProgress(
                fraction: fraction, timeFraction: timeFraction,
                onTrack: fraction >= 0.5 || timeFraction < 0.2,
                summary: "\(breathworkThisWeek) of \(weeklyTargetInt) calm sessions this week\(hrvNote)"
            )
        }

        let baseline = goal.baselineValue
        let current = trailingAverage(readings, type: metricFor(goal.type), todayEpochDay: todayEpochDay)
        guard let baseline = baseline, let current = current else {
            let metricLabel: String = metricFor(goal.type).label.lowercased()
            return GoalProgress(
                fraction: 0.0, timeFraction: timeFraction, onTrack: timeFraction < 0.25,
                summary: "Log \(metricLabel) entries to track this goal."
            )
        }
        let achieved: Double
        switch goal.type {
        case .weightLoss:
            achieved = (baseline - current) * kgToLb   // readings in kg → lb lost
        default:                                       // % gained vs baseline
            achieved = baseline == 0.0 ? 0.0 : (current - baseline) / baseline * 100.0
        }
        let fraction: Double = max(achieved / goal.targetValue, 0.0)
        // On-track = progress keeping pace with time, with a small grace margin early on.
        let onTrack: Bool = fraction + 0.15 >= timeFraction
        let targetInt: Int = Int(goal.targetValue)
        let summary: String
        switch goal.type {
        case .weightLoss:
            // Report the direction the data actually moved: abs() here told a user who had gained
            // weight that they had lost it. A wrong-signed number is worse than a blunt one.
            if achieved < 0 {
                summary = "\(roundTo1(-achieved)) lb up vs baseline, \(remainingWeeks) wk left"
            } else {
                summary = "\(roundTo1(achieved)) of \(targetInt) lb down, \(remainingWeeks) wk left"
            }
        default:
            if achieved < 0 {
                summary = "\(roundTo1(-achieved))% below baseline, \(remainingWeeks) wk left"
            } else {
                summary = "\(roundTo1(achieved))% of \(targetInt)% gained, \(remainingWeeks) wk left"
            }
        }
        return GoalProgress(fraction: fraction, timeFraction: timeFraction, onTrack: onTrack, summary: summary)
    }

    /// The reading type that measures each goal.
    public static func metricFor(_ type: GoalType) -> GoalMetric {
        switch type {
        case .weightLoss: return .weight
        case .sleepRecovery: return .sleepPerformance
        case .stressResilience: return .hrvRmssd
        }
    }

    /// Baseline captured at goal creation: trailing-14-day average of the goal metric, in the
    /// metric's native unit (kg for weight — `progress` converts to pounds when comparing).
    public static func baselineFor(_ type: GoalType, readings: [MetricReading], todayEpochDay: Int) -> Double? {
        trailingAverage(readings, type: metricFor(type), todayEpochDay: todayEpochDay, windowDays: 14)
    }

    private static func trailingAverage(
        _ readings: [MetricReading],
        type: GoalMetric,
        todayEpochDay: Int,
        windowDays: Int = 7
    ) -> Double? {
        let values = readings
            .filter {
                $0.type == type && $0.value != nil &&
                    $0.recordedAtEpochDay >= todayEpochDay - windowDays + 1 &&
                    $0.recordedAtEpochDay <= todayEpochDay
            }
            .compactMap { $0.value }
        guard !values.isEmpty else { return nil }
        return values.reduce(0, +) / Double(values.count)
    }

    /// Truncate-to-one-decimal, matching Kotlin's `((v * 10).toInt() / 10.0).toString()`.
    private static func roundTo1(_ v: Double) -> String {
        String(Double(Int(v * 10)) / 10.0)
    }
}
