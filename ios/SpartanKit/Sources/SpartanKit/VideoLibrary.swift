import Foundation

/// A specific, free follow-along training video. Only large, long-established channels are used so
/// links stay alive; every URL is verified against YouTube's oEmbed endpoint by the Android
/// VideoLibraryTest. Opening a video is always user-initiated (a tap) and leaves the app —
/// Spartan itself still makes no network calls.
public struct VideoGuide: Codable, Equatable {
    public let id: String
    public let title: String
    public let channel: String
    public let minutes: Int
    public let intensity: Intensity
    public let url: String

    public init(id: String, title: String, channel: String, minutes: Int, intensity: Intensity, url: String) {
        self.id = id
        self.title = title
        self.channel = channel
        self.minutes = minutes
        self.intensity = intensity
        self.url = url
    }
}

/// The training story for one metric: why exercise moves it, and which sessions to follow.
public struct MetricTraining: Codable, Equatable {
    public let intro: String
    public let guides: [VideoGuide]

    public init(intro: String, guides: [VideoGuide]) {
        self.intro = intro
        self.guides = guides
    }
}

/// Maps Spartan's metrics and daily-plan activities to concrete follow-along videos, so "go do
/// Zone 2" always comes with a session the user can press play on. Copy is wellness-framed and
/// SafetyEngine-checked in tests; metrics where guidance belongs to a clinician first (ApoB, Lp(a),
/// CAC) deliberately get no training block.
///
/// Faithful port of `com.spartan.domain.engine.VideoLibrary` (Android): same nine guides, same
/// activity-slug map, same intro copy. The Android catalog also maps the lab/lifestyle metrics
/// (exercise minutes, fasting glucose, lipids, blood pressure, body composition, vitamin D,
/// strength sessions); those `MetricType` cases don't exist in SpartanKit yet, so their training
/// blocks stay Android-only until the Swift metric catalog grows.
public enum VideoLibrary {

    // MARK: - The video shelf (small on purpose; each slot is reused across metrics)

    private static let zone2Walk30 = VideoGuide(
        id: "zone2_walk_30",
        title: "30-minute indoor power walk",
        channel: "Walk at Home",
        minutes: 30,
        intensity: .moderate,
        url: "https://www.youtube.com/watch?v=enYITYwvPAQ"
    )

    private static let easyWalk15 = VideoGuide(
        id: "easy_walk_15",
        title: "15-minute easy walk",
        channel: "Walk at Home",
        minutes: 15,
        intensity: .easy,
        url: "https://www.youtube.com/watch?v=njeZ29umqVE"
    )

    private static let strength30 = VideoGuide(
        id: "strength_full_body_30",
        title: "30-minute full-body strength (beginner, light weights or bottles)",
        channel: "HASfit",
        minutes: 30,
        intensity: .hard,
        url: "https://www.youtube.com/watch?v=R4x9rXThlQs"
    )

    private static let mobility10 = VideoGuide(
        id: "mobility_10",
        title: "10-minute full-body mobility",
        channel: "Fraser Wilson",
        minutes: 10,
        intensity: .easy,
        url: "https://www.youtube.com/watch?v=Igzmhbghcd4"
    )

    private static let stretch10 = VideoGuide(
        id: "stretch_10",
        title: "10-minute full-body stretch",
        channel: "MadFit",
        minutes: 10,
        intensity: .easy,
        url: "https://www.youtube.com/watch?v=yLDAHd_C7G0"
    )

    private static let breathing5 = VideoGuide(
        id: "breathing_5",
        title: "5-minute calming breathwork",
        channel: "Yoga With Adriene",
        minutes: 5,
        intensity: .rest,
        url: "https://www.youtube.com/watch?v=9fEo9my03Ks"
    )

    private static let winddown20 = VideoGuide(
        id: "winddown_sleep_20",
        title: "20-minute bedtime yoga wind-down",
        channel: "Yoga With Adriene",
        minutes: 20,
        intensity: .rest,
        url: "https://www.youtube.com/watch?v=v7SN-d4qXx0"
    )

    private static let postMealWalk10 = VideoGuide(
        id: "post_meal_walk_10",
        title: "10-minute after-meal walk",
        channel: "Walk at Home",
        minutes: 10,
        intensity: .easy,
        url: "https://www.youtube.com/watch?v=tVpUCkMLgms"
    )

    private static let lowImpactCardio20 = VideoGuide(
        id: "low_impact_cardio_20",
        title: "20-minute low-impact cardio (no jumping)",
        channel: "MadFit",
        minutes: 20,
        intensity: .moderate,
        url: "https://www.youtube.com/watch?v=DMAxIrCAAZ0"
    )

    public static let allGuides: [VideoGuide] = [
        zone2Walk30, easyWalk15, strength30, mobility10, stretch10,
        breathing5, winddown20, postMealWalk10, lowImpactCardio20,
    ]

    // MARK: - Metric → training

    private static let trainings: [MetricType: MetricTraining] = [
        .restingHeartRate: MetricTraining(
            intro: "Consistent easy aerobic work is the most reliable lever for a lower resting "
                + "heart rate over 6–12 weeks. Strength adds to the effect.",
            guides: [zone2Walk30, lowImpactCardio20, strength30]
        ),
        .hrvRmssd: MetricTraining(
            intro: "Slow breathing acutely supports HRV, and a steady aerobic base plus good sleep "
                + "raise its floor over time.",
            guides: [breathing5, zone2Walk30, winddown20]
        ),
        .recoveryScore: MetricTraining(
            intro: "On low-recovery days, gentle movement supports blood flow without adding "
                + "strain — that is what tends to bring tomorrow's score back.",
            guides: [stretch10, easyWalk15, breathing5]
        ),
        .sleepPerformance: MetricTraining(
            intro: "A consistent wind-down helps you fall asleep faster and sleep deeper; daytime "
                + "movement supports it from the other side.",
            guides: [winddown20, breathing5]
        ),
        .sleepDuration: MetricTraining(
            intro: "Protect the runway into bed: a short wind-down at a consistent time is the "
                + "highest-leverage habit for longer sleep.",
            guides: [winddown20, breathing5]
        ),
        .sleepDebt: MetricTraining(
            intro: "Paying down sleep debt is mostly about earlier, calmer nights — a wind-down "
                + "routine makes the earlier bedtime actually stick.",
            guides: [winddown20, breathing5]
        ),
        .respiratoryRate: MetricTraining(
            intro: "Slow-paced breathing practice supports a calm nighttime respiratory pattern.",
            guides: [breathing5]
        ),
        .dayStrain: MetricTraining(
            intro: "If strain runs low, add structured aerobic sessions; if it spikes, swap one "
                + "for an easy walk. Consistency beats intensity.",
            guides: [zone2Walk30, lowImpactCardio20]
        ),
        .energyKcal: MetricTraining(
            intro: "Daily energy burned follows activity volume — steady aerobic work and "
                + "strength sessions raise it sustainably.",
            guides: [zone2Walk30, strength30]
        ),
        // Android additionally maps EXERCISE_MINUTES, FASTING_GLUCOSE, TRIGLYCERIDES, HDL_C,
        // TG_HDL_RATIO, SYSTOLIC_BP, DIASTOLIC_BP, WEIGHT, BMI, WAIST_CIRCUMFERENCE,
        // WAIST_TO_HEIGHT_RATIO, VITAMIN_D_25OH, and STRENGTH_SESSIONS — cases the Swift
        // MetricType doesn't carry yet.
        // ApoB, Lp(a), CAC, custom: clinician-first territory — no training block on purpose.
    ]

    /// The training block for `metric`, or nil where exercise guidance isn't Spartan's to give.
    public static func trainingFor(_ metric: MetricType) -> MetricTraining? {
        trainings[metric]
    }

    // MARK: - Daily activity → video

    /// The follow-along video for a generated plan activity, keyed by the stable slug in its id
    /// ("<epochDay>:<slug>"). Pain, clinician, and trivial one-minute items get no video on purpose.
    public static func guideForActivity(activityId: String) -> VideoGuide? {
        guard let colon = activityId.firstIndex(of: ":") else { return nil }
        let slug = String(activityId[activityId.index(after: colon)...])
        switch slug {
        case "mobility": return mobility10
        case "zone2-walk": return easyWalk15
        case "zone2": return zone2Walk30
        case "strength": return strength30
        case "stretch": return stretch10
        case "winddown": return winddown20
        case "breathing": return breathing5
        case "quickwin": return mobility10
        case "connect-whoop": return mobility10
        default: return nil
        }
    }
}
