import Foundation

/// Sex assigned at birth, used only to pick the honest reference band.
public enum SexAtBirth: String, Codable, CaseIterable, Equatable {
    case female = "FEMALE"
    case male = "MALE"
    case unspecified = "UNSPECIFIED"
}

public struct ReferenceBand: Codable, Equatable {
    public let metric: MetricType
    /// Inclusive typical range for the bracket; nil bound = open-ended side.
    public let typicalLow: Double?
    public let typicalHigh: Double?
    /// Range often seen in aerobically trained people of the bracket (nil when n/a).
    public let fitnessForwardLow: Double?
    public let fitnessForwardHigh: Double?
    /// One-sentence, bracket-aware education line.
    public let education: String

    public init(
        metric: MetricType,
        typicalLow: Double?,
        typicalHigh: Double?,
        fitnessForwardLow: Double? = nil,
        fitnessForwardHigh: Double? = nil,
        education: String
    ) {
        self.metric = metric
        self.typicalLow = typicalLow
        self.typicalHigh = typicalHigh
        self.fitnessForwardLow = fitnessForwardLow
        self.fitnessForwardHigh = fitnessForwardHigh
        self.education = education
    }
}

/// Age/sex-aware education for the top-5 wearable metrics: what a typical range looks like for
/// the user's bracket, next to their own value. Buckets are deliberately AGE + SEX only — never
/// race (no credible reference data exists for wearable metrics, and clinical medicine is
/// actively removing race-based corrections; see docs/COACH_DESIGN.md §2). All copy is
/// wellness-framed, passes SafetyEngine, and always defers to the user's own baseline.
///
/// Sources (encoded conservatively as bands, not diagnoses):
///  - Resting HR: AHA adult span 60–100 bpm; large wearable-cohort percentiles by age/sex show
///    typical 58–72 with fitness-forward 50–62; women a few bpm higher on average.
///  - HRV (RMSSD): wearable-cohort medians decline with age (~3–5%/decade); mid-30s ms is a
///    typical 40s median. Highly individual — bands are wide by design.
///  - Sleep duration: AASM/NSF consensus 7–9 h for adults 18–64.
///  - Sleep consistency: sleep-regularity literature associates ±30–45 min bed/wake windows
///    with better outcomes; WHOOP expresses this as a 0–100 consistency score (≥70 is a solid
///    pattern for most adults).
///  - Recovery: WHOOP-proprietary composite; educated as a distribution to read over weeks.
///
/// Faithful port of `com.spartan.domain.engine.ReferenceRanges` (Android): same brackets, same
/// bands, same education copy. All five `topFive` metrics exist in the Swift `MetricType`; the
/// Android "never faked" guarantee for lab metrics (ApoB, Lp(a), CAC, …) holds by construction
/// here because those cases don't exist in SpartanKit at all.
public enum ReferenceRanges {

    /// The five metrics the Coach hub educates, in display order.
    public static let topFive: [MetricType] = [
        .restingHeartRate,
        .hrvRmssd,
        .sleepDuration,
        .sleepPerformance,
        .recoveryScore,
    ]

    /// The band for `metric` in the user's bracket, or nil when Spartan refuses to fake one.
    /// `ageYears`/`sex` are optional — missing demographics widen to all-adult bands.
    public static func bandFor(_ metric: MetricType, ageYears: Int?, sex: SexAtBirth) -> ReferenceBand? {
        switch metric {
        case .restingHeartRate:
            return restingHr(ageYears: ageYears, sex: sex)
        case .hrvRmssd:
            return hrv(ageYears: ageYears)
        case .sleepDuration:
            return ReferenceBand(
                metric: metric, typicalLow: 7.0, typicalHigh: 9.0,
                education: "Consensus guidance for adults is 7–9 hours of actual sleep. Your WHOOP "
                    + "sleep-need estimate personalizes this night to night."
            )
        case .sleepPerformance:
            return ReferenceBand(
                metric: metric, typicalLow: 70.0, typicalHigh: nil,
                education: "Sleep performance compares sleep you got with sleep you needed. Most "
                    + "adults do well keeping it at 70%+ on most nights, with consistent bed and wake "
                    + "times doing the heavy lifting."
            )
        case .recoveryScore:
            return ReferenceBand(
                metric: metric, typicalLow: nil, typicalHigh: nil,
                education: "Recovery is not a score to max out — a healthy month mixes green, "
                    + "yellow, and the occasional red day. What matters is bouncing back within a day "
                    + "or two rather than sitting low for weeks."
            )
        default:
            return nil
        }
    }

    /// Age bracket label used in the UI ("40–49"), or "adult" when age is unknown.
    public static func bracketLabel(_ ageYears: Int?) -> String {
        guard let age = ageYears else { return "adult" }
        switch age {
        case 0...29: return "18–29"
        case 30...39: return "30–39"
        case 40...49: return "40–49"
        case 50...59: return "50–59"
        default: return "60+"
        }
    }

    private static func restingHr(ageYears: Int?, sex: SexAtBirth) -> ReferenceBand {
        // Base typical band by age bracket (cohort percentiles, midspread), then a small,
        // honest sex adjustment: women average a few bpm higher.
        let (lo, hi): (Double, Double)
        switch ageYears {
        case nil: (lo, hi) = (58.0, 75.0)
        case .some(0...29): (lo, hi) = (56.0, 72.0)
        case .some(30...39): (lo, hi) = (57.0, 73.0)
        case .some(40...49): (lo, hi) = (58.0, 74.0)
        case .some(50...59): (lo, hi) = (58.0, 75.0)
        default: (lo, hi) = (57.0, 76.0)
        }
        let shift: Double = sex == .female ? 2.0 : 0.0
        let typicalLowInt: Int = Int(lo + shift)
        let typicalHighInt: Int = Int(hi + shift)
        let fitLowInt: Int = Int(50.0 + shift)
        let fitHighInt: Int = Int(62.0 + shift)
        let bracket: String = bracketLabel(ageYears)
        let noun: String = sexNoun(sex)
        var education = "Typical resting heart rate for a \(bracket) "
        education += "\(noun) sits around \(typicalLowInt)–\(typicalHighInt) bpm, "
        education += "and aerobically trained people often run \(fitLowInt)–"
        education += "\(fitHighInt). A slow drift down over months is the classic sign of a "
        education += "building aerobic base."
        return ReferenceBand(
            metric: .restingHeartRate,
            typicalLow: lo + shift, typicalHigh: hi + shift,
            fitnessForwardLow: 50.0 + shift, fitnessForwardHigh: 62.0 + shift,
            education: education
        )
    }

    private static func hrv(ageYears: Int?) -> ReferenceBand {
        // Cohort medians by age bracket; deliberately wide and framed as medians, not targets.
        let (lo, hi): (Double, Double)
        switch ageYears {
        case nil: (lo, hi) = (25.0, 75.0)
        case .some(0...29): (lo, hi) = (45.0, 95.0)
        case .some(30...39): (lo, hi) = (35.0, 80.0)
        case .some(40...49): (lo, hi) = (28.0, 70.0)
        case .some(50...59): (lo, hi) = (22.0, 60.0)
        default: (lo, hi) = (18.0, 50.0)
        }
        let lowInt: Int = Int(lo)
        let highInt: Int = Int(hi)
        let bracket: String = bracketLabel(ageYears)
        var education = "HRV is the most individual number on this list — cohort medians for "
        education += "\(bracket) span roughly \(lowInt)–\(highInt) ms and "
        education += "decline naturally with age. Judge yourself against your own baseline trend, "
        education += "not this table."
        return ReferenceBand(
            metric: .hrvRmssd,
            typicalLow: lo, typicalHigh: hi,
            education: education
        )
    }

    private static func sexNoun(_ sex: SexAtBirth) -> String {
        switch sex {
        case .female: return "woman"
        case .male: return "man"
        case .unspecified: return "adult"
        }
    }
}
