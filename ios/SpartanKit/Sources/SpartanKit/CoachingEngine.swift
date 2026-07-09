import Foundation

/// Options that tune a day's plan without changing the readiness signal itself.
public struct CoachingOptions: Equatable {
    public let maxActivities: Int
    public let missedGoalYesterday: Bool
    public let painFlag: Bool

    public init(
        maxActivities: Int = 4,
        missedGoalYesterday: Bool = false,
        painFlag: Bool = false
    ) {
        self.maxActivities = maxActivities
        self.missedGoalYesterday = missedGoalYesterday
        self.painFlag = painFlag
    }
}

/// A source of recommended activities for a given readiness. The MVP ships one implementation,
/// `RuleBasedRecommendationSource`. An AI-backed source can be added later and bound in DI
/// WITHOUT any change to `CoachingEngine` or the UI — the seam that keeps the first version
/// from depending on a complex AI system.
public protocol RecommendationSource {
    func recommend(readiness: ReadinessSnapshot, options: CoachingOptions) -> [DailyActivity]
}

/// Stable rule identifiers, carried on each activity for provenance and analytics.
/// Same string values as the Android `RuleIds`.
public enum RuleIds {
    public static let lowRecovery = "LOW_RECOVERY"
    public static let poorSleep = "POOR_SLEEP"
    public static let highStrainLowRecovery = "HIGH_STRAIN_LOW_RECOVERY"
    public static let lowHrvTrend = "LOW_HRV_TREND"
    public static let elevatedRhrTrend = "ELEVATED_RHR_TREND"
    public static let missedGoal = "MISSED_GOAL"
    public static let goodRecoveryGreenlight = "GOOD_RECOVERY_GREENLIGHT"
    public static let painDeload = "PAIN_DELOAD"
    public static let hydrationBaseline = "HYDRATION_BASELINE"
    public static let staleDataFallback = "STALE_DATA_FALLBACK"
    public static let clinicianReferral = "CLINICIAN_REFERRAL"
}

/// Transparent, rules-based daily coaching. Every rule is small, named (see `DailyActivity.ruleId`),
/// and independently unit-testable. All user-facing copy is passed through `SafetyEngine` so no
/// generated text can make a medical claim or unsafe recommendation.
///
/// Guidance is framed as wellness / fitness / recovery / habit support — never diagnosis. When a
/// metric looks potentially concerning, the engine adds a `RuleIds.clinicianReferral` activity that
/// recommends seeing a qualified clinician rather than interpreting the value.
public final class CoachingEngine {
    private let source: RecommendationSource
    private let safetyEngine: SafetyEngine

    public init(
        source: RecommendationSource = RuleBasedRecommendationSource(),
        safetyEngine: SafetyEngine = SafetyEngine()
    ) {
        self.source = source
        self.safetyEngine = safetyEngine
    }

    public func buildPlan(
        readiness: ReadinessSnapshot,
        options: CoachingOptions = CoachingOptions()
    ) -> DailyPlan {
        let raw = source.recommend(readiness: readiness, options: options)
        let activities = prioritizeAndCap(raw, maxActivities: options.maxActivities)
        let plan = DailyPlan(
            dateEpochDay: readiness.dateEpochDay,
            headline: headlineFor(readiness),
            band: readiness.band,
            activities: activities,
            safetyBanner: safetyBannerFor(readiness),
            isMock: readiness.isMock
        )
        // Defensive: every string that can reach the user is validated here as well.
        for activity in plan.activities {
            safetyEngine.sanitizeOrFatal(activity.title)
            safetyEngine.sanitizeOrFatal(activity.whyItMatters)
            for step in activity.instructions { safetyEngine.sanitizeOrFatal(step) }
            if let note = activity.safetyNote { safetyEngine.sanitizeOrFatal(note) }
        }
        if let banner = plan.safetyBanner { safetyEngine.sanitizeOrFatal(banner) }
        safetyEngine.sanitizeOrFatal(plan.headline)
        return plan
    }

    /// REQUIRED first, then RECOMMENDED, then OPTIONAL; always keep required + clinician referral.
    private func prioritizeAndCap(_ activities: [DailyActivity], maxActivities: Int) -> [DailyActivity] {
        var seenIds = Set<String>()
        var deduped: [DailyActivity] = []
        for activity in activities where seenIds.insert(activity.id).inserted {
            deduped.append(activity)
        }
        let ordered = stableSortByPriority(deduped)
        let mustKeep = ordered.filter {
            $0.priority == .required || $0.ruleId == RuleIds.clinicianReferral
        }
        let mustKeepIds = Set(mustKeep.map { $0.id })
        let rest = ordered.filter { !mustKeepIds.contains($0.id) }
        let room = max(maxActivities - mustKeep.count, 0)
        return stableSortByPriority(mustKeep + Array(rest.prefix(room)))
    }

    /// Stable sort by priority rank (Swift's `sorted` does not guarantee stability, Kotlin's does).
    private func stableSortByPriority(_ activities: [DailyActivity]) -> [DailyActivity] {
        activities.enumerated()
            .sorted { lhs, rhs in
                if lhs.element.priority.sortRank != rhs.element.priority.sortRank {
                    return lhs.element.priority.sortRank < rhs.element.priority.sortRank
                }
                return lhs.offset < rhs.offset
            }
            .map { $0.element }
    }

    private func headlineFor(_ readiness: ReadinessSnapshot) -> String {
        if readiness.isStale {
            return "Let's ease in while Spartan waits for today's data."
        }
        switch readiness.band {
        case .primed: return "You're primed. Make today count."
        case .balanced: return "Balanced day. Steady, quality work."
        case .easy: return "Go lighter today and protect recovery."
        case .rest: return "Recovery first. Today is for rebuilding."
        }
    }

    private func safetyBannerFor(_ readiness: ReadinessSnapshot) -> String? {
        if readiness.band == .rest {
            return "Pushing hard on a low-recovery day rarely pays off. Keep intensity low and stop if anything hurts."
        }
        return "This is wellness and fitness guidance, not medical advice. Stop and seek care for pain, illness, or unusual symptoms."
    }
}

public final class RuleBasedRecommendationSource: RecommendationSource {
    private let safetyEngine: SafetyEngine

    public init(safetyEngine: SafetyEngine = SafetyEngine()) {
        self.safetyEngine = safetyEngine
    }

    public func recommend(readiness: ReadinessSnapshot, options: CoachingOptions) -> [DailyActivity] {
        var out: [DailyActivity] = []
        let day = readiness.dateEpochDay

        let referral = clinicianReferral(readiness, day: day)
        let concerning = referral != nil
        if let referral = referral { out.append(referral) }

        if options.painFlag { out.append(painDeload(day)) }

        switch readiness.band {
        case .rest, .easy:
            out.append(contentsOf: lowRecoveryBlock(readiness, day: day))
        case .primed:
            // A concerning vital suppresses hard training even on a primed day — safety over strain.
            out.append(contentsOf: concerning ? balancedSession(readiness, day: day) : greenlight(readiness, day: day))
        case .balanced:
            out.append(contentsOf: balancedSession(readiness, day: day))
        }

        if highStrainLowRecovery(readiness) { out.append(activeRecovery(day)) }
        if poorSleep(readiness) { out.append(sleepHygiene(readiness, day: day)) }
        if lowHrvTrend(readiness) { out.append(breathwork(readiness, day: day)) }
        if elevatedRhrTrend(readiness) { out.append(rhrCheckIn(readiness, day: day)) }
        if options.missedGoalYesterday { out.append(quickWin(day)) }
        if readiness.isStale { out.append(staleFallback(day)) }

        out.append(hydration(day))

        // Sanitize as we build so a bad string fails fast at the rule that produced it.
        for activity in out {
            safetyEngine.sanitizeOrFatal(activity.title)
            safetyEngine.sanitizeOrFatal(activity.whyItMatters)
            for step in activity.instructions { safetyEngine.sanitizeOrFatal(step) }
            if let note = activity.safetyNote { safetyEngine.sanitizeOrFatal(note) }
        }
        return out
    }

    // MARK: - Rule predicates

    private func highStrainLowRecovery(_ r: ReadinessSnapshot) -> Bool {
        (r.dayStrainPrior ?? 0.0) >= 14.0 && (r.band == .rest || r.band == .easy)
    }

    private func poorSleep(_ r: ReadinessSnapshot) -> Bool {
        if let performance = r.sleepPerformance, performance < 70 { return true }
        return (r.sleepDebtHours ?? 0.0) >= 1.5
    }

    private func lowHrvTrend(_ r: ReadinessSnapshot) -> Bool {
        guard let delta = r.hrvVsBaseline, let hrv = r.hrvMs else { return false }
        return delta <= -0.10 * (hrv - delta)
    }

    private func elevatedRhrTrend(_ r: ReadinessSnapshot) -> Bool {
        (r.rhrVsBaseline ?? 0.0) >= 5.0
    }

    // MARK: - Activity builders

    private func lowRecoveryBlock(_ r: ReadinessSnapshot, day: Int) -> [DailyActivity] {
        [
            activity(day, "mobility", "10-minute mobility flow", .mobility,
                     .required, RuleIds.lowRecovery, .recoveryScore,
                     why: "Your recovery is low today, so gentle movement supports blood flow without adding strain.",
                     steps: ["Move through hips, ankles, thoracic spine and shoulders.",
                             "Slow, controlled reps — no forcing end range.",
                             "Breathe easily throughout."],
                     minutes: 10, intensity: .easy, time: .morning,
                     note: "Keep it comfortable. Stop and rest if anything is painful."),
            activity(day, "zone2-walk", "15-minute easy walk", .zone2,
                     .recommended, RuleIds.lowRecovery, .recoveryScore,
                     why: "Easy Zone 2 movement aids recovery far better than intense training on a low-recovery day.",
                     steps: ["Walk at a conversational pace.",
                             "Nose-breathe if you can.",
                             "Keep effort easy — you should be able to talk."],
                     minutes: 15, intensity: .easy, time: .midday,
                     note: "Skip hard training today; prioritize sleep tonight."),
        ]
    }

    private func balancedSession(_ r: ReadinessSnapshot, day: Int) -> [DailyActivity] {
        [
            activity(day, "zone2", "25-minute Zone 2 session", .zone2,
                     .recommended, RuleIds.goodRecoveryGreenlight, .recoveryScore,
                     why: "Recovery is in a balanced range — steady aerobic work builds your base without overreaching.",
                     steps: ["Choose walking, cycling, or rowing.",
                             "Hold an easy, conversational pace.",
                             "Finish feeling like you could keep going."],
                     minutes: 25, intensity: .moderate, time: .afternoon, note: nil),
        ]
    }

    private func greenlight(_ r: ReadinessSnapshot, day: Int) -> [DailyActivity] {
        [
            activity(day, "strength", "35-minute strength session", .strength,
                     .recommended, RuleIds.goodRecoveryGreenlight, .recoveryScore,
                     why: "You're primed today — a good window for a quality strength session with a little progression.",
                     steps: ["Warm up 5 minutes.",
                             "3–4 compound movements, clean form.",
                             "Leave 2–3 reps in reserve on each set."],
                     minutes: 35, intensity: .hard, time: .afternoon,
                     note: "Progress gradually. Stop or scale down if form breaks or pain appears."),
        ]
    }

    private func activeRecovery(_ day: Int) -> DailyActivity {
        activity(day, "stretch", "10-minute stretch and reset", .mobility,
                 .recommended, RuleIds.highStrainLowRecovery, .dayStrain,
                 why: "Yesterday's strain was high while recovery is low, so active recovery beats another hard session.",
                 steps: ["Light full-body stretching.",
                         "Optional easy walk instead of intensity.",
                         "Hydrate and refuel with protein."],
                 minutes: 10, intensity: .easy, time: .anytime, note: nil)
    }

    private func sleepHygiene(_ r: ReadinessSnapshot, day: Int) -> DailyActivity {
        activity(day, "winddown", "Sleep wind-down routine", .sleep,
                 .recommended, RuleIds.poorSleep, .sleepPerformance,
                 why: "Your recent sleep is below target — a consistent wind-down helps you fall asleep faster and sleep deeper.",
                 steps: ["Set a caffeine cutoff by early afternoon.",
                         "Dim screens 60 minutes before bed.",
                         "Aim for a consistent bedtime tonight.",
                         "Keep the room cool and dark."],
                 minutes: 20, intensity: .rest, time: .evening, note: nil)
    }

    private func breathwork(_ r: ReadinessSnapshot, day: Int) -> DailyActivity {
        activity(day, "breathing", "5-minute breathing protocol", .breathwork,
                 .recommended, RuleIds.lowHrvTrend, .hrvRmssd,
                 why: "Your HRV is trending below baseline, a sign of accumulated stress. Slow breathing helps you downshift.",
                 steps: ["Sit comfortably.",
                         "Inhale 4 seconds, exhale 6 seconds.",
                         "Repeat for 5 minutes, relaxing the shoulders."],
                 minutes: 5, intensity: .rest, time: .evening, note: nil)
    }

    private func rhrCheckIn(_ r: ReadinessSnapshot, day: Int) -> DailyActivity {
        activity(day, "hydrate-rest", "Hydration and easy-day check-in", .hydration,
                 .recommended, RuleIds.elevatedRhrTrend, .restingHeartRate,
                 why: "Resting heart rate is up versus your baseline — often a cue to hydrate, ease intensity, and check sleep.",
                 steps: ["Front-load water this morning.",
                         "Keep training easy today.",
                         "If you feel unwell, rest and monitor how you feel."],
                 minutes: 5, intensity: .rest, time: .morning, note: nil)
    }

    private func quickWin(_ day: Int) -> DailyActivity {
        activity(day, "quickwin", "5-minute movement snack", .movement,
                 .optional, RuleIds.missedGoal, nil,
                 why: "You missed yesterday's goal — a tiny, achievable win rebuilds momentum with zero pressure.",
                 steps: ["Do 5 minutes of anything: a walk, mobility, or light bodyweight moves.",
                         "The only goal is to start."],
                 minutes: 5, intensity: .easy, time: .anytime, note: nil)
    }

    private func painDeload(_ day: Int) -> DailyActivity {
        activity(day, "pain-deload", "Gentle, pain-free movement only", .recovery,
                 .required, RuleIds.painDeload, nil,
                 why: "You flagged pain — today is for gentle, comfortable movement while your body recovers.",
                 steps: ["Choose only pain-free ranges and light effort.",
                         "Skip anything that aggravates the area."],
                 minutes: 10, intensity: .easy, time: .anytime,
                 note: "If pain is sharp, worsening, or persistent, consider seeing a qualified clinician.")
    }

    private func hydration(_ day: Int) -> DailyActivity {
        activity(day, "hydration", "Hydration reminder", .hydration,
                 .optional, RuleIds.hydrationBaseline, nil,
                 why: "Steady hydration supports recovery, HRV, and how you feel through the day.",
                 steps: ["Drink a glass of water now.",
                         "Keep water nearby and sip through the day."],
                 minutes: 1, intensity: .rest, time: .anytime, note: nil)
    }

    private func staleFallback(_ day: Int) -> DailyActivity {
        activity(day, "connect-whoop", "Ease in with a mobility flow", .mobility,
                 .recommended, RuleIds.staleDataFallback, nil,
                 why: "Today's WHOOP data isn't in yet. Here's a safe default while Spartan waits to personalize your plan.",
                 steps: ["10 minutes of easy mobility.",
                         "Connect or sync WHOOP for a tailored plan."],
                 minutes: 10, intensity: .easy, time: .anytime, note: nil)
    }

    /// Emit a non-diagnostic clinician-referral nudge when a vital looks potentially concerning.
    /// Never states a cause or diagnosis — only recommends professional guidance.
    private func clinicianReferral(_ r: ReadinessSnapshot, day: Int) -> DailyActivity? {
        var respHigh = false
        if let resp = r.respiratoryRate {
            if resp >= 25.0 {
                respHigh = true
            } else if let baseline = r.respiratoryRateBaseline, resp - baseline >= 3.0 {
                respHigh = true
            }
        }
        var rhrVeryHigh = false
        if let rhr = r.restingHeartRate, rhr >= 100.0 {
            rhrVeryHigh = true
        }
        if !respHigh && !rhrVeryHigh { return nil }
        let metric: MetricType = respHigh ? .respiratoryRate : .restingHeartRate
        return activity(day, "clinician", "Check in with how you feel", .recovery,
                        .required, RuleIds.clinicianReferral, metric,
                        why: "One or more of today's readings is outside your usual range. This is not a diagnosis, and one reading is not a conclusion.",
                        steps: ["Notice any symptoms like illness, unusual fatigue, or feeling unwell.",
                                "Rest and take it easy today.",
                                "If readings stay unusual or you have symptoms, consider consulting a qualified clinician."],
                        minutes: 2, intensity: .rest, time: .morning,
                        note: "Spartan does not diagnose. For medical concerns, contact a qualified clinician.")
    }

    private func activity(
        _ day: Int, _ slug: String, _ title: String, _ category: ActivityCategory,
        _ priority: ActivityPriority, _ ruleId: String, _ metric: MetricType?,
        why: String, steps: [String], minutes: Int, intensity: Intensity,
        time: TimeOfDay, note: String?
    ) -> DailyActivity {
        DailyActivity(
            id: "\(day):\(slug)",
            title: title,
            category: category,
            priority: priority,
            whyItMatters: why,
            relatedMetric: metric,
            instructions: steps,
            estimatedMinutes: minutes,
            intensity: intensity,
            bestTimeOfDay: time,
            ruleId: ruleId,
            safetyNote: note
        )
    }
}
