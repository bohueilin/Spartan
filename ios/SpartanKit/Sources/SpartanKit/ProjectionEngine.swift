import Foundation

/// One projected range at a week offset from today. `low`/`high` are *numeric* bounds
/// (`low <= high` always); whether the top of the range is the desirable end comes from
/// `MetricProjection.higherIsBetter`, not from this struct.
public struct ProjectionPoint: Codable, Equatable {
    public let week: Int
    public let low: Double
    public let high: Double

    public init(week: Int, low: Double, high: Double) {
        self.week = week
        self.low = low
        self.high = high
    }
}

/// An illustrative 8-week range for one WHOOP metric, produced by `ProjectionEngine`.
/// Every user-facing string on this type has passed the `SafetyEngine` blocked-phrase check.
public struct MetricProjection: Codable, Equatable {
    public let metric: MetricType
    public let label: String
    public let unit: String
    public let currentValue: Double
    public let points: [ProjectionPoint]
    public let assumption: String
    public let higherIsBetter: Bool

    public init(
        metric: MetricType,
        label: String,
        unit: String,
        currentValue: Double,
        points: [ProjectionPoint],
        assumption: String,
        higherIsBetter: Bool
    ) {
        self.metric = metric
        self.label = label
        self.unit = unit
        self.currentValue = currentValue
        self.points = points
        self.assumption = assumption
        self.higherIsBetter = higherIsBetter
    }
}

/// Deterministic, rules-based "where could this metric plausibly go" projector.
/// iOS port of the Android `ProjectionEngine`: same tiers, same deltas, same clamps.
///
/// Modeling rules (all illustrative wellness estimates — never medical predictions):
/// - Consistency tier scales the full 8-week effect: >=5 plan days/week -> full effect,
///   3-4 -> 0.65x, 1-2 -> 0.35x, 0 -> flat (every week holds at today's value).
/// - Resting heart rate: -3..-6 bpm at week 8 (scaled by tier), floored at
///   `max(45, current * 0.85)` and never pushed above today's value by the floor.
/// - HRV: +5%..+15% at week 8 (scaled by tier), capped at `current * 1.25`.
/// - Recovery: +5..+12 points at week 8 (scaled by tier), capped at 90.
/// - Points at weeks [0, 2, 4, 6, 8], linearly ramped; week 0 equals the current value.
/// - Values rounded to 1 decimal; numeric `low <= high` on every point.
public final class ProjectionEngine {

    /// Shown alongside every projection surface. Keep in sync with Android's
    /// `ProjectionEngine.DISCLAIMER`.
    // Kept byte-identical to the Android ProjectionEngine.DISCLAIMER for cross-platform parity.
    public static let disclaimer =
        "Typical ranges seen with consistent aerobic and strength training in general research" +
        " — not a prediction or a guarantee for you. Individual results vary." +
        " This is wellness guidance, not medical advice."

    private static let weeks: [Int] = [0, 2, 4, 6, 8]
    private static let horizonWeeks = 8.0

    private let safety = SafetyEngine()

    public init() {
        // The disclaimer is user-facing copy: hold it to the same guardrail as everything else.
        safety.sanitizeOrFatal(Self.disclaimer)
    }

    /// Projects each metric whose current value is provided. Output order:
    /// resting heart rate, HRV, recovery. Same inputs always produce the same output.
    public func project(
        restingHeartRate: Double?,
        hrvMs: Double?,
        recoveryScore: Int?,
        consistencyDays7: Int
    ) -> [MetricProjection] {
        let factor = Self.consistencyFactor(consistencyDays7)
        let tierPhrase = Self.tierPhrase(consistencyDays7)
        var projections: [MetricProjection] = []

        if let rhr = restingHeartRate {
            // Floor never exceeds today's value, so an already-low RHR just holds flat.
            let floorValue = min(max(45.0, rhr * 0.85), rhr)
            let points = Self.weeks.map { week -> ProjectionPoint in
                let ramp = factor * Double(week) / Self.horizonWeeks
                let bestCase = max(rhr - 6.0 * ramp, floorValue)
                let modestCase = max(rhr - 3.0 * ramp, floorValue)
                return Self.point(week: week, bestCase, modestCase)
            }
            projections.append(makeProjection(
                metric: .restingHeartRate,
                label: "Resting heart rate",
                unit: "bpm",
                currentValue: rhr,
                points: points,
                tierPhrase: tierPhrase,
                mechanism: "Steady aerobic activity and good sleep tend to lower resting heart rate a little at a time.",
                higherIsBetter: false
            ))
        }

        if let hrv = hrvMs {
            let cap = hrv * 1.25
            let points = Self.weeks.map { week -> ProjectionPoint in
                let ramp = factor * Double(week) / Self.horizonWeeks
                let modestCase = min(hrv * (1.0 + 0.05 * ramp), cap)
                let bestCase = min(hrv * (1.0 + 0.15 * ramp), cap)
                return Self.point(week: week, modestCase, bestCase)
            }
            projections.append(makeProjection(
                metric: .hrvRmssd,
                label: "HRV",
                unit: "ms",
                currentValue: hrv,
                points: points,
                tierPhrase: tierPhrase,
                mechanism: "Regular movement, steady sleep, and recovery habits tend to lift HRV toward the upper end of your own range.",
                higherIsBetter: true
            ))
        }

        if let recovery = recoveryScore {
            let current = Double(recovery)
            // Cap never drops below today's value, so a score already above 90 holds flat.
            let ceiling = max(90.0, current)
            let points = Self.weeks.map { week -> ProjectionPoint in
                let ramp = factor * Double(week) / Self.horizonWeeks
                let modestCase = min(current + 5.0 * ramp, ceiling)
                let bestCase = min(current + 12.0 * ramp, ceiling)
                return Self.point(week: week, modestCase, bestCase)
            }
            projections.append(makeProjection(
                metric: .recoveryScore,
                label: "Recovery",
                unit: "%",
                currentValue: current,
                points: points,
                tierPhrase: tierPhrase,
                mechanism: "Balanced load and better sleep usually show up as a higher recovery score over weeks.",
                higherIsBetter: true
            ))
        }

        return projections
    }

    // MARK: - Internals

    /// >=5 days -> full effect; 3-4 -> 0.65; 1-2 -> 0.35; 0 (or less) -> flat.
    private static func consistencyFactor(_ days: Int) -> Double {
        switch days {
        case 5...: return 1.0
        case 3...4: return 0.65
        case 1...2: return 0.35
        default: return 0.0
        }
    }

    private static func tierPhrase(_ days: Int) -> String {
        switch days {
        case 5...: return "you keep up 5 or more plan days a week"
        case 3...4: return "you keep up 3 to 4 plan days a week"
        case 1...2: return "you keep up 1 to 2 plan days a week"
        default: return "consistency stays at zero plan days, so the range holds flat at today's value"
        }
    }

    /// Orders the two candidate values numerically and rounds both to 1 decimal.
    private static func point(week: Int, _ a: Double, _ b: Double) -> ProjectionPoint {
        ProjectionPoint(week: week, low: round1(min(a, b)), high: round1(max(a, b)))
    }

    private static func round1(_ value: Double) -> Double {
        (value * 10.0).rounded() / 10.0
    }

    private func makeProjection(
        metric: MetricType,
        label: String,
        unit: String,
        currentValue: Double,
        points: [ProjectionPoint],
        tierPhrase: String,
        mechanism: String,
        higherIsBetter: Bool
    ) -> MetricProjection {
        let assumption =
            "Illustrative estimate, not a prediction or a guarantee. Assumes \(tierPhrase)" +
            " for the next 8 weeks. \(mechanism)"
        return MetricProjection(
            metric: metric,
            label: safety.sanitizeOrFatal(label),
            unit: unit,
            currentValue: currentValue,
            points: points,
            assumption: safety.sanitizeOrFatal(assumption),
            higherIsBetter: higherIsBetter
        )
    }
}
