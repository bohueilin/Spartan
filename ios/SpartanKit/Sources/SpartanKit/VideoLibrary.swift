import Foundation

/// How demanding a session is to follow — used to bias picks for beginners and older adults.
public enum TrainingLevel: String, Codable, Equatable {
    case beginner
    case intermediate
}

/// Joint load. Every shipped pick is `.low`; the field lets the ranker prefer it explicitly.
public enum ImpactLevel: String, Codable, Equatable {
    case low
    case moderate
}

/// What kind of session it is, so a metric can draw from the right movement family.
public enum Modality: String, Codable, Equatable {
    case walk, cardio, strength, mobility, pilates, yoga, breath
}

/// A specific, free follow-along training video. Only large, long-established channels are used and
/// every URL is a real video verified against YouTube's oEmbed endpoint (author + title matched)
/// before shipping; the offline `VideoLibraryTests` re-check format and copy safety. Opening a video
/// is always user-initiated (a tap) and leaves the app — Spartan itself makes no network calls.
public struct VideoGuide: Codable, Equatable {
    public let id: String
    public let title: String
    public let channel: String
    public let minutes: Int
    public let intensity: Intensity
    public let level: TrainingLevel
    public let impact: ImpactLevel
    public let modality: Modality
    public let url: String

    public init(
        id: String,
        title: String,
        channel: String,
        minutes: Int,
        intensity: Intensity,
        level: TrainingLevel,
        impact: ImpactLevel,
        modality: Modality,
        url: String
    ) {
        self.id = id
        self.title = title
        self.channel = channel
        self.minutes = minutes
        self.intensity = intensity
        self.level = level
        self.impact = impact
        self.modality = modality
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

/// Who we're recommending for. Age biases picks toward joint-friendly, progressive sessions; a
/// metric being off-target makes the recommendation lead with the gentlest, most sustainable option
/// (consistency beats intensity when a number needs to move). Both are optional — with neither set,
/// the curated default order stands.
public struct TrainingProfile: Equatable {
    public let ageYears: Int?
    public let offTargetMetrics: Set<MetricType>

    public init(ageYears: Int? = nil, offTargetMetrics: Set<MetricType> = []) {
        self.ageYears = ageYears
        self.offTargetMetrics = offTargetMetrics
    }

    /// Adults ~40+ benefit from low-impact, beginner-first defaults (per the app's coaching notes).
    public func favorsGentle() -> Bool {
        guard let ageYears = ageYears else { return false }
        return ageYears >= Self.gentleAge
    }

    public static let gentleAge = 40
    public static let none = TrainingProfile()
}

/// Maps Spartan's metrics and daily-plan activities to concrete follow-along videos, and ranks them
/// for the individual. The catalog is curated from the channels an evidence-minded coach recommends
/// for healthy adults around 40 starting a weight-loss/fitness journey (Fitness Blender, Team Body
/// Project, HASfit, Juice & Toya, growwithjo, Walk at Home, Yoga With Adriene, Caroline Girvan) —
/// beginner, low-impact, well-cued sessions rather than random HIIT. Metrics whose guidance belongs
/// to a clinician first (ApoB, Lp(a), CAC) deliberately get no training block.
///
/// Faithful port of `com.spartan.domain.engine.VideoLibrary` (Android): same 18-video shelf, same
/// per-metric ordering, same age/off-target ranking, same activity-slug map, same intro copy. The
/// Android catalog additionally maps lab/lifestyle metrics (exercise minutes, fasting glucose,
/// lipids, blood pressure, body composition, vitamin D, strength sessions); those `MetricType`
/// cases don't exist in SpartanKit yet, so their training blocks stay Android-only until the Swift
/// metric catalog grows. Every guide still lives in `allGuides` — only the metric→training mapping
/// is trimmed to the nine Swift metrics.
public enum VideoLibrary {

    private static func yt(_ id: String) -> String { "https://www.youtube.com/watch?v=\(id)" }

    // MARK: - The video shelf: real, oEmbed-verified sessions

    // Walking / low-impact aerobic — the base that lowers resting heart rate over weeks.
    private static let walkHappy15 = VideoGuide(
        id: "walk_happy_15", title: "1-mile Happy Walk", channel: "Walk at Home", minutes: 15,
        intensity: .easy, level: .beginner, impact: .low, modality: .walk, url: yt("X3q5e1pV4pc")
    )
    private static let walkLowImpact30 = VideoGuide(
        id: "walk_lowimpact_30", title: "30-minute low-impact walk", channel: "growwithjo", minutes: 30,
        intensity: .moderate, level: .beginner, impact: .low, modality: .walk, url: yt("yV4jyj8Hr1g")
    )
    private static let walkFast15 = VideoGuide(
        id: "walk_fast_15", title: "15-minute brisk walk (great after meals)", channel: "growwithjo", minutes: 15,
        intensity: .easy, level: .beginner, impact: .low, modality: .walk, url: yt("NlVndT8-B5U")
    )
    private static let cardioStanding20 = VideoGuide(
        id: "cardio_standing_20", title: "20-minute no-jumping home cardio", channel: "Team Body Project", minutes: 20,
        intensity: .moderate, level: .beginner, impact: .low, modality: .cardio, url: yt("x3aogIZfVUI")
    )
    private static let cardioBeginner25 = VideoGuide(
        id: "cardio_beginner_25", title: "25-minute beginner low-impact cardio", channel: "Team Body Project", minutes: 25,
        intensity: .moderate, level: .beginner, impact: .low, modality: .cardio, url: yt("iNW4lCU693Q")
    )

    // Beginner full-body strength — protects muscle and adds to the resting-HR effect.
    private static let strengthFb28 = VideoGuide(
        id: "strength_fb_28", title: "28-minute total-body strength + core (beginner)", channel: "Fitness Blender", minutes: 28,
        intensity: .moderate, level: .beginner, impact: .low, modality: .strength, url: yt("Gze8oMuj4as")
    )
    private static let strengthHasfit20 = VideoGuide(
        id: "strength_hasfit_20", title: "20-minute full-body dumbbells (beginner, two intensities)", channel: "HASfit", minutes: 20,
        intensity: .moderate, level: .beginner, impact: .low, modality: .strength, url: yt("VPC6U9CjKMU")
    )
    private static let strengthHasfitJoint20 = VideoGuide(
        id: "strength_hasfit_joint_20", title: "20-minute joint-friendly dumbbell strength", channel: "HASfit", minutes: 20,
        intensity: .moderate, level: .beginner, impact: .low, modality: .strength, url: yt("E9XfzxIlfAU")
    )
    private static let strengthJuicetoya30 = VideoGuide(
        id: "strength_juicetoya_30", title: "30-minute full-body dumbbells (guided form cues)", channel: "Juice & Toya", minutes: 30,
        intensity: .moderate, level: .beginner, impact: .low, modality: .strength, url: yt("6Xr8EOWJrvY")
    )
    private static let strengthGirvanEpic30 = VideoGuide(
        id: "strength_girvan_epic_30", title: "Beginner EPIC · 30-minute dumbbell full body", channel: "Caroline Girvan", minutes: 30,
        intensity: .hard, level: .intermediate, impact: .low, modality: .strength, url: yt("nKzZLE6s1cs")
    )

    // Mobility / pilates / stretch — recovery-day movement that eases strain without adding load.
    private static let pilatesFb16 = VideoGuide(
        id: "pilates_fb_16", title: "16-minute low-impact Pilates (core, glutes, thighs)", channel: "Fitness Blender", minutes: 16,
        intensity: .easy, level: .beginner, impact: .low, modality: .pilates, url: yt("zdNlYAugUtg")
    )
    private static let stretchFb10 = VideoGuide(
        id: "stretch_fb_10", title: "10-minute feel-good cool-down stretch", channel: "Fitness Blender", minutes: 10,
        intensity: .rest, level: .beginner, impact: .low, modality: .mobility, url: yt("P8DOZRtIIEQ")
    )
    private static let mobilityYoga11 = VideoGuide(
        id: "mobility_yoga_11", title: "11-minute morning mobility yoga", channel: "Yoga With Adriene", minutes: 11,
        intensity: .easy, level: .beginner, impact: .low, modality: .mobility, url: yt("2IcWJobNDck")
    )
    private static let yogaGentle25 = VideoGuide(
        id: "yoga_gentle_25", title: "25-minute gentle morning yoga", channel: "Yoga With Adriene", minutes: 25,
        intensity: .easy, level: .beginner, impact: .low, modality: .yoga, url: yt("jsLAc-2y0bE")
    )

    // Breathwork + wind-down — the levers for HRV, respiratory rate, and sleep.
    private static let breath5 = VideoGuide(
        id: "breath_5", title: "5-minute calming breathwork", channel: "Yoga With Adriene", minutes: 5,
        intensity: .rest, level: .beginner, impact: .low, modality: .breath, url: yt("9fEo9my03Ks")
    )
    private static let meditationStress15 = VideoGuide(
        id: "meditation_stress_15", title: "15-minute stillness for stress relief", channel: "Yoga With Adriene", minutes: 15,
        intensity: .rest, level: .beginner, impact: .low, modality: .breath, url: yt("CscxGprl1yw")
    )
    private static let yogaBedtime20 = VideoGuide(
        id: "yoga_bedtime_20", title: "20-minute bedtime yoga wind-down", channel: "Yoga With Adriene", minutes: 20,
        intensity: .rest, level: .beginner, impact: .low, modality: .yoga, url: yt("v7SN-d4qXx0")
    )
    private static let yogaWinddown12 = VideoGuide(
        id: "yoga_winddown_12", title: "12-minute wind-down bedtime yoga", channel: "Yoga With Adriene", minutes: 12,
        intensity: .rest, level: .beginner, impact: .low, modality: .yoga, url: yt("BiWDsfZ3zbo")
    )

    public static let allGuides: [VideoGuide] = [
        walkHappy15, walkLowImpact30, walkFast15, cardioStanding20, cardioBeginner25,
        strengthFb28, strengthHasfit20, strengthHasfitJoint20, strengthJuicetoya30, strengthGirvanEpic30,
        pilatesFb16, stretchFb10, mobilityYoga11, yogaGentle25,
        breath5, meditationStress15, yogaBedtime20, yogaWinddown12,
    ]

    // MARK: - Metric → training (guides listed in default effectiveness order)

    private static let trainings: [MetricType: MetricTraining] = [
        .restingHeartRate: MetricTraining(
            intro: "Consistent easy aerobic work is the most reliable lever for a lower resting heart rate "
                + "over 6–12 weeks. Two weekly strength sessions add to the effect.",
            guides: [walkLowImpact30, cardioStanding20, walkHappy15, strengthFb28]
        ),
        .hrvRmssd: MetricTraining(
            intro: "Slow breathing acutely supports HRV; a steady aerobic base and good sleep raise its "
                + "floor over time.",
            guides: [breath5, meditationStress15, yogaWinddown12, walkLowImpact30]
        ),
        .recoveryScore: MetricTraining(
            intro: "On low-recovery days, gentle movement supports blood flow without adding strain — that "
                + "is what tends to bring tomorrow's score back.",
            guides: [stretchFb10, mobilityYoga11, walkHappy15, breath5]
        ),
        .sleepPerformance: MetricTraining(
            intro: "A consistent wind-down helps you fall asleep faster and sleep deeper; daytime movement "
                + "supports it from the other side.",
            guides: [yogaBedtime20, yogaWinddown12, breath5]
        ),
        .sleepDuration: MetricTraining(
            intro: "Protect the runway into bed: a short wind-down at a consistent time is the "
                + "highest-leverage habit for longer sleep.",
            guides: [yogaBedtime20, yogaWinddown12, breath5]
        ),
        .sleepDebt: MetricTraining(
            intro: "Paying down sleep debt is mostly about earlier, calmer nights — a wind-down routine "
                + "makes the earlier bedtime actually stick.",
            guides: [yogaWinddown12, yogaBedtime20, breath5]
        ),
        .respiratoryRate: MetricTraining(
            intro: "Slow-paced breathing practice supports a calm nighttime respiratory pattern.",
            guides: [breath5, meditationStress15, yogaWinddown12]
        ),
        .dayStrain: MetricTraining(
            intro: "If strain runs low, add structured low-impact cardio; if it spikes, swap one for an "
                + "easy walk or mobility. Consistency beats intensity.",
            guides: [walkLowImpact30, cardioStanding20, stretchFb10]
        ),
        .energyKcal: MetricTraining(
            intro: "Daily energy burned follows activity volume — steady aerobic work and strength sessions "
                + "raise it sustainably.",
            guides: [strengthJuicetoya30, walkLowImpact30, strengthFb28]
        ),
        // Android additionally maps EXERCISE_MINUTES, FASTING_GLUCOSE, TRIGLYCERIDES, HDL_C,
        // TG_HDL_RATIO, SYSTOLIC_BP, DIASTOLIC_BP, WEIGHT, BMI, WAIST_CIRCUMFERENCE,
        // WAIST_TO_HEIGHT_RATIO, VITAMIN_D_25OH, and STRENGTH_SESSIONS — cases the Swift
        // MetricType doesn't carry yet.
        // ApoB, Lp(a), CAC, custom: clinician-first territory — no training block on purpose.
    ]

    private static let maxGuides = 3

    /// The training block for `metric`, ranked for `profile`: with no profile the curated
    /// effectiveness order stands (capped at `maxGuides`); for an older adult, or a metric that's
    /// off-target, picks are re-ranked to lead with the gentlest, most sustainable sessions and the
    /// intro says so.
    public static func recommend(_ metric: MetricType, profile: TrainingProfile = .none) -> MetricTraining? {
        guard let base = trainings[metric] else { return nil }
        let gentleFirst = profile.favorsGentle() || profile.offTargetMetrics.contains(metric)
        let ranked = gentleFirst
            ? gentleRanked(base.guides)
            : Array(base.guides.prefix(maxGuides))
        let intro = (gentleFirst && !ranked.isEmpty)
            ? base.intro + " These picks are low-impact and beginner-friendly."
            : base.intro
        return MetricTraining(intro: intro, guides: ranked)
    }

    /// Backward-compatible entry point (no personalization).
    public static func trainingFor(_ metric: MetricType) -> MetricTraining? {
        recommend(metric, profile: .none)
    }

    /// The gentle-first re-ranking `recommend` applies for an older or off-target profile: lead with
    /// beginner-level sessions only — the harder intermediate progressions are hidden while any
    /// beginner option exists — then order the survivors gentlest-first (ties keep curated order),
    /// capped at `maxGuides`. Exposed so the ranking can be verified directly on the strength shelf,
    /// whose Caroline Girvan session is the catalog's only non-beginner pick and whose Android
    /// `STRENGTH_SESSIONS` training block has no Swift `MetricType` to hang on.
    public static func gentleRanked(_ guides: [VideoGuide]) -> [VideoGuide] {
        let beginner = guides.filter { $0.level == .beginner }
        let pool = beginner.isEmpty ? guides : beginner
        // Stable descending sort on gentleness; ties keep the curated order via the original index.
        let ordered = pool.enumerated()
            .sorted { lhs, rhs in
                let scoreL = gentlenessScore(lhs.element)
                let scoreR = gentlenessScore(rhs.element)
                if scoreL != scoreR { return scoreL > scoreR }
                return lhs.offset < rhs.offset
            }
            .map { $0.element }
        return Array(ordered.prefix(maxGuides))
    }

    private static func gentlenessScore(_ g: VideoGuide) -> Int {
        var s = 0
        if g.impact == .low { s += 3 }
        if g.level == .beginner { s += 2 }
        if g.intensity != .hard { s += 1 }
        return s
    }

    // MARK: - Daily activity → video

    /// The follow-along video for a generated plan activity, keyed by the stable slug in its id
    /// ("<epochDay>:<slug>"). For strength, an older adult gets the gentler beginner session. Pain,
    /// clinician, and trivial one-minute items get no video on purpose.
    public static func guideForActivity(activityId: String, profile: TrainingProfile = .none) -> VideoGuide? {
        guard let colon = activityId.firstIndex(of: ":") else { return nil }
        let slug = String(activityId[activityId.index(after: colon)...])
        switch slug {
        case "mobility": return mobilityYoga11
        case "zone2-walk": return walkHappy15
        case "zone2": return walkLowImpact30
        case "strength": return profile.favorsGentle() ? strengthHasfit20 : strengthJuicetoya30
        case "stretch": return stretchFb10
        case "winddown": return yogaBedtime20
        case "breathing": return breath5
        case "quickwin": return walkFast15
        case "connect-whoop": return mobilityYoga11
        default: return nil
        }
    }
}
