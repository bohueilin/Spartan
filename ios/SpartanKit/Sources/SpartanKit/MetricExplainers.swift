import Foundation

/// Plain-language education card for one WHOOP metric: what it is, what moves it, what a good
/// pattern looks like, and how Spartan actually uses it. Calm, non-diagnostic wellness copy only —
/// every string is run through `SafetyEngine` before it can be shown.
public struct MetricExplainer: Codable, Equatable {
    public let metric: MetricType
    public let title: String
    public let whatItIs: String
    public let whatMovesIt: [String]
    public let whatGoodLooksLike: String
    public let howSpartanUsesIt: String

    public init(
        metric: MetricType,
        title: String,
        whatItIs: String,
        whatMovesIt: [String],
        whatGoodLooksLike: String,
        howSpartanUsesIt: String
    ) {
        self.metric = metric
        self.title = title
        self.whatItIs = whatItIs
        self.whatMovesIt = whatMovesIt
        self.whatGoodLooksLike = whatGoodLooksLike
        self.howSpartanUsesIt = howSpartanUsesIt
    }
}

/// The static explainer catalog — one entry per `MetricType` case. iOS port of the Android
/// `MetricExplainers` object: same nine metrics, same voice.
public enum MetricExplainers {

    public static let all: [MetricExplainer] = validated([
        MetricExplainer(
            metric: .recoveryScore,
            title: "Recovery",
            whatItIs: "WHOOP's 0 to 100 read on how ready your body looks for load today. It blends heart rate variability, resting heart rate, sleep, and respiratory rate into one score.",
            whatMovesIt: [
                "Sleep amount and quality the night before",
                "Training load from the past few days",
                "Alcohol, late meals, and late screens",
                "Stress, travel, and getting sick",
            ],
            whatGoodLooksLike: "Nobody is green every day. A healthy pattern cycles through green, yellow, and red across a week, with a weekly average that holds steady or drifts up.",
            howSpartanUsesIt: "Recovery sets the intensity band for your daily plan: primed days green-light quality training, and low days shift the plan toward rest and recovery actions."
        ),
        MetricExplainer(
            metric: .hrvRmssd,
            title: "HRV",
            whatItIs: "Heart rate variability: the tiny beat-to-beat differences in timing between heartbeats, measured in milliseconds during deep sleep. More variability generally reflects a well-recovered nervous system.",
            whatMovesIt: [
                "Hard training and accumulated fatigue",
                "Sleep quality and consistency",
                "Alcohol and late eating",
                "Stress, illness, and dehydration",
            ],
            whatGoodLooksLike: "HRV is personal, so comparing yours with anyone else's is not useful. What matters is your own baseline and a trend that holds steady or climbs over weeks.",
            howSpartanUsesIt: "Spartan watches HRV against your recent baseline. A sustained dip nudges the plan toward easier days and more recovery work."
        ),
        MetricExplainer(
            metric: .sleepPerformance,
            title: "Sleep performance",
            whatItIs: "The share of your personal sleep need you actually got last night, as a percentage. Sleep need is estimated from your baseline plus recent strain and sleep debt.",
            whatMovesIt: [
                "Consistent bed and wake times",
                "Accumulated sleep debt",
                "Caffeine and alcohol timing",
                "A cool, dark, quiet bedroom",
            ],
            whatGoodLooksLike: "Regularly meeting 85 percent or more of your sleep need, with bed and wake times that stay within about an hour night to night.",
            howSpartanUsesIt: "Low sleep performance adds sleep-hygiene actions to your plan and softens training intensity until sleep recovers."
        ),
        MetricExplainer(
            metric: .sleepDebt,
            title: "Sleep debt",
            whatItIs: "The running gap between the sleep your body needed and the sleep it got, in hours. It builds when short nights stack up.",
            whatMovesIt: [
                "A string of short nights builds it up",
                "Earlier nights and naps pay it down",
                "High-strain days raise the sleep your body asks for",
            ],
            whatGoodLooksLike: "Staying under about an hour of debt most of the week, and paying debt down within a few days instead of letting it snowball.",
            howSpartanUsesIt: "Growing debt swaps training intensity for an earlier wind-down and recovery-first days until the gap closes."
        ),
        MetricExplainer(
            metric: .respiratoryRate,
            title: "Respiratory rate",
            whatItIs: "How many breaths you take per minute while you sleep. For most people it sits in a narrow band and barely moves night to night.",
            whatMovesIt: [
                "Getting sick is the most common cause of a rise",
                "Altitude and heat",
                "Alcohol close to bedtime",
            ],
            whatGoodLooksLike: "Stability. Staying within about one breath per minute of your own baseline is the pattern to expect.",
            howSpartanUsesIt: "Because it is so stable, Spartan treats a sustained clear rise above your baseline as a signal to pause hard training and, if it persists, a pattern worth discussing with a clinician. Spartan flags trends; it never diagnoses."
        ),
        MetricExplainer(
            metric: .dayStrain,
            title: "Day strain",
            whatItIs: "WHOOP's 0 to 21 measure of the total cardiovascular load your day put on your body. Workouts, commutes, and stressful hours all count toward it.",
            whatMovesIt: [
                "Workout length and intensity",
                "All-day movement, not just training",
                "Mental stress can add load too",
            ],
            whatGoodLooksLike: "Strain that roughly matches recovery: pushing on primed days, keeping it light on low days, and avoiding weeks where every day is maxed out.",
            howSpartanUsesIt: "Yesterday's strain feeds today's plan, so big days are followed by enough recovery to absorb the work."
        ),
        MetricExplainer(
            metric: .restingHeartRate,
            title: "Resting heart rate",
            whatItIs: "Your heart's beats per minute during the deepest part of sleep. It is one of the clearest long-term markers of aerobic fitness.",
            whatMovesIt: [
                "Consistent aerobic training gradually lowers it",
                "Alcohol, late meals, and dehydration push it up for a night",
                "Illness, heat, and stress raise it temporarily",
            ],
            whatGoodLooksLike: "Small day-to-day wobble around your baseline, with a slow drift downward over months of consistent training.",
            howSpartanUsesIt: "A resting heart rate clearly above your baseline adds a gentle check-in to your plan and tempers intensity for the day."
        ),
        MetricExplainer(
            metric: .sleepDuration,
            title: "Sleep duration",
            whatItIs: "Total time actually asleep, in hours — not just time in bed.",
            whatMovesIt: [
                "A consistent schedule is the biggest lever",
                "Evening light, caffeine, and alcohol",
                "A wind-down routine before bed",
            ],
            whatGoodLooksLike: "Most adults do best somewhere around 7 to 9 hours, but your own sleep-need number is a better target than a universal rule.",
            howSpartanUsesIt: "Duration feeds sleep performance and sleep debt; short nights tilt the next day's plan toward recovery."
        ),
        MetricExplainer(
            metric: .energyKcal,
            title: "Energy",
            whatItIs: "An estimate of the total energy your body burned across the day, in kilocalories, combining your baseline burn with activity.",
            whatMovesIt: [
                "Overall activity volume and strain",
                "Body size and composition",
                "High-intensity sessions add a modest afterburn",
            ],
            whatGoodLooksLike: "There is no score to chase here — it is context. Expect it to track your strain: higher on big days, lower on rest days.",
            howSpartanUsesIt: "Spartan uses it as context for fueling and hydration reminders on high-output days, never as a target to hit."
        ),
    ])

    public static func forMetric(_ type: MetricType) -> MetricExplainer? {
        all.first { $0.metric == type }
    }

    /// Runs every user-facing string through the safety guardrail before the catalog is usable.
    private static func validated(_ explainers: [MetricExplainer]) -> [MetricExplainer] {
        let safety = SafetyEngine()
        for explainer in explainers {
            safety.sanitizeOrFatal(explainer.title)
            safety.sanitizeOrFatal(explainer.whatItIs)
            for item in explainer.whatMovesIt {
                safety.sanitizeOrFatal(item)
            }
            safety.sanitizeOrFatal(explainer.whatGoodLooksLike)
            safety.sanitizeOrFatal(explainer.howSpartanUsesIt)
        }
        return explainers
    }
}
