import XCTest
@testable import SpartanKit

/// Port of the Android `VideoLibraryTest`: same URL/duration invariants, same safety sweep, same
/// slug coverage, same approved-channel guard, and the same age/off-target ranking. The
/// clinician-first check is adapted — those `MetricType` cases don't exist in SpartanKit at all, so
/// the "no training block" guarantee holds by construction.
///
/// One ranking case diverges from Android by necessity: the "drop the intermediate Caroline Girvan
/// session" behavior lives in Android's `STRENGTH_SESSIONS` block, and that `MetricType` isn't in
/// SpartanKit (the Swift enum stays at nine metrics). We exercise the exact production ranking on
/// the strength shelf directly via `VideoLibrary.gentleRanked(_:)` instead.
final class VideoLibraryTests: XCTestCase {

    private let safety = SafetyEngine()

    func testEveryGuideHasHttpsYoutubeWatchUrlAndSaneDuration() {
        for guide in VideoLibrary.allGuides {
            XCTAssertTrue(
                guide.url.hasPrefix("https://www.youtube.com/watch?v=") || guide.url.hasPrefix("https://youtu.be/"),
                "\(guide.id) url must be a specific YouTube watch link: \(guide.url)"
            )
            XCTAssertTrue((3...45).contains(guide.minutes), "\(guide.id) minutes out of range")
            XCTAssertTrue(!guide.title.isEmpty && !guide.channel.isEmpty)
        }
    }

    func testAllCopyPassesTheSafetyEngine() throws {
        for guide in VideoLibrary.allGuides { try safety.sanitize(guide.title) }
        for type in MetricType.allCases {
            if let training = VideoLibrary.trainingFor(type) {
                try safety.sanitize(training.intro)
                XCTAssertFalse(training.guides.isEmpty, "\(type) training must list at least one guide")
            }
        }
    }

    func testClinicianFirstMetricsGetNoTrainingBlock() {
        // Exercise prescriptions for these belong to a clinician; Spartan stays out on purpose.
        // The Swift MetricType carries none of the Android clinician-first cases (APOB, LPA, CAC,
        // CUSTOM), so they cannot even be asked for — assert they stay absent from the enum.
        for raw in ["APOB", "LPA", "CAC", "CUSTOM"] {
            XCTAssertNil(MetricType(rawValue: raw), "\(raw) must not gain a Swift case without a clinician-first review")
        }
    }

    func testWhoopMetricsTheAppCoachesOnAllHaveTraining() {
        // Android also coaches on EXERCISE_MINUTES; that MetricType case doesn't exist in Swift yet.
        let coached: [MetricType] = [
            .recoveryScore, .hrvRmssd, .restingHeartRate,
            .sleepPerformance, .sleepDuration, .sleepDebt, .dayStrain,
        ]
        for metric in coached {
            XCTAssertNotNil(VideoLibrary.trainingFor(metric), "\(metric) should have a training block")
        }
    }

    func testEveryGuideIsFromAnApprovedChannel() {
        // The catalog is drawn only from large, long-established channels; a copy-safety guard on
        // the verbatim shelf so a bad edit to a channel name is caught offline.
        let approved: Set<String> = [
            "Walk at Home", "growwithjo", "Team Body Project", "Fitness Blender", "HASfit",
            "Juice & Toya", "Caroline Girvan", "Yoga With Adriene",
        ]
        for guide in VideoLibrary.allGuides {
            XCTAssertTrue(approved.contains(guide.channel), "\(guide.id) from unapproved channel '\(guide.channel)'")
        }
    }

    func testEveryTrainingActivityTheEngineGeneratesHasAVideoAndSafetyItemsDoNot() {
        let source = RuleBasedRecommendationSource()
        // Sweep the readiness space so every rule fires at least once. Each snapshot carries the
        // suppressed HRV / elevated RHR the Kotlin test applies via `copy(...)`.
        let todays = [
            WhoopSnapshot(dateEpochDay: 1000, recoveryScore: 92, hrvMs: 40.0, restingHeartRate: 62.0, sleepPerformance: 85),
            WhoopSnapshot(dateEpochDay: 1000, recoveryScore: 55, hrvMs: 40.0, restingHeartRate: 62.0, sleepPerformance: 60, sleepDebtHours: 2.0),
            WhoopSnapshot(dateEpochDay: 1000, recoveryScore: 20, hrvMs: 40.0, restingHeartRate: 62.0, dayStrain: 15.0),
            WhoopSnapshot(dateEpochDay: 1000, recoveryScore: nil, hrvMs: 40.0, restingHeartRate: 62.0),
        ]
        let history = (1...6).map { offset in
            WhoopSnapshot(dateEpochDay: 1000 - offset, hrvMs: 60.0, restingHeartRate: 55.0, dayStrain: 15.0)
        }
        let activities = todays.flatMap { today in
            source.recommend(
                readiness: ReadinessSnapshot.from(today: today, history: history),
                options: CoachingOptions(missedGoalYesterday: true, painFlag: true)
            )
        }
        let trainingSlugs: Set<String> = [
            "mobility", "zone2-walk", "zone2", "strength", "stretch", "winddown", "breathing", "quickwin", "connect-whoop",
        ]
        for activity in activities {
            let slug = activity.id.firstIndex(of: ":").map { String(activity.id[activity.id.index(after: $0)...]) } ?? ""
            let guide = VideoLibrary.guideForActivity(activityId: activity.id)
            if trainingSlugs.contains(slug) {
                XCTAssertNotNil(guide, "training activity '\(slug)' should link a follow-along video")
            } else {
                XCTAssertNil(guide, "non-training activity '\(slug)' must not push a video")
            }
        }
        // Pain and clinician items explicitly stay video-free.
        XCTAssertNil(VideoLibrary.guideForActivity(activityId: "1000:pain-deload"))
        XCTAssertNil(VideoLibrary.guideForActivity(activityId: "1000:clinician"))
        XCTAssertNil(VideoLibrary.guideForActivity(activityId: "1000:hydration"))
        XCTAssertNil(VideoLibrary.guideForActivity(activityId: "no-colon-id"))
    }

    func testActivityVideoDurationsRoughlyMatchTheActivityEstimate() throws {
        // The zone2 session (25 min est) links a ~30 min walk; mobility (10 min) an ~11 min flow.
        let zone2 = try XCTUnwrap(VideoLibrary.guideForActivity(activityId: "1:zone2"))
        let mobility = try XCTUnwrap(VideoLibrary.guideForActivity(activityId: "1:mobility"))
        let zone2Walk = try XCTUnwrap(VideoLibrary.guideForActivity(activityId: "1:zone2-walk"))
        XCTAssertEqual(30, zone2.minutes)
        XCTAssertEqual(11, mobility.minutes)
        XCTAssertEqual(15, zone2Walk.minutes)
    }

    // MARK: - age + needs-aware ranking

    func testRecommendWithNoProfileKeepsCuratedOrder() throws {
        // Resting-HR leads with the curated low-impact walk; no gentle flag on the intro.
        let training = try XCTUnwrap(VideoLibrary.recommend(.restingHeartRate, profile: .none))
        XCTAssertEqual("walk_lowimpact_30", training.guides.first?.id)
        XCTAssertFalse(training.intro.contains("low-impact and beginner-friendly"))
        XCTAssertTrue(training.guides.count <= 3)
    }

    func testRecommendForOlderAdultLeadsWithBeginnerAppendsGentleIntroAndCaps() throws {
        let training = try XCTUnwrap(VideoLibrary.recommend(.restingHeartRate, profile: TrainingProfile(ageYears: 44)))
        XCTAssertTrue(training.intro.contains("low-impact and beginner-friendly"),
                      "older-adult intro should flag gentle picks")
        XCTAssertEqual(TrainingLevel.beginner, training.guides.first?.level)
        XCTAssertTrue(training.guides.allSatisfy { $0.level == .beginner },
                      "no intermediate session should surface for an older beginner")
        // Resting-HR carries four curated guides; the 3-cap drops the fourth.
        XCTAssertEqual(3, training.guides.count)
    }

    func testRecommendForOffTargetMetricReranksGentleEvenWhenAgeUnknown() throws {
        let profile = TrainingProfile(ageYears: nil, offTargetMetrics: [.restingHeartRate])
        let training = try XCTUnwrap(VideoLibrary.recommend(.restingHeartRate, profile: profile))
        XCTAssertTrue(training.intro.contains("low-impact and beginner-friendly"))
        XCTAssertEqual(TrainingLevel.beginner, training.guides.first?.level)
    }

    func testGentleRankingDropsTheIntermediateStrengthProgression() {
        // Mirrors Android's STRENGTH_SESSIONS block: two beginner dumbbell sessions plus Caroline
        // Girvan's INTERMEDIATE/HARD EPIC. Gentle-first must DROP Girvan (not merely sort it last),
        // leaving beginner-only picks, gentlest-first, capped at three.
        let byId = Dictionary(uniqueKeysWithValues: VideoLibrary.allGuides.map { ($0.id, $0) })
        let strengthShelf = ["strength_hasfit_20", "strength_juicetoya_30", "strength_girvan_epic_30"].compactMap { byId[$0] }
        XCTAssertEqual(3, strengthShelf.count, "strength shelf ids must all resolve from the catalog")
        let ranked = VideoLibrary.gentleRanked(strengthShelf)
        XCTAssertTrue(ranked.allSatisfy { $0.level == .beginner }, "the intermediate progression must be hidden")
        XCTAssertFalse(ranked.contains { $0.id == "strength_girvan_epic_30" }, "Caroline Girvan (intermediate) must be dropped")
        XCTAssertEqual("strength_hasfit_20", ranked.first?.id)
        XCTAssertTrue(ranked.count <= 3)
    }

    func testGuideForActivityPicksGentlerStrengthForOlderAdults() throws {
        let young = try XCTUnwrap(VideoLibrary.guideForActivity(activityId: "1:strength", profile: TrainingProfile(ageYears: 28)))
        let older = try XCTUnwrap(VideoLibrary.guideForActivity(activityId: "1:strength", profile: TrainingProfile(ageYears: 46)))
        XCTAssertEqual("strength_juicetoya_30", young.id)
        XCTAssertEqual("strength_hasfit_20", older.id)
        XCTAssertEqual(TrainingLevel.beginner, older.level)
    }

    func testRecommendCapsAtThreeGuides() {
        for type in MetricType.allCases {
            if let training = VideoLibrary.recommend(type, profile: TrainingProfile(ageYears: 44)) {
                XCTAssertTrue(training.guides.count <= 3, "\(type) returned more than 3 guides")
            }
        }
    }
}
