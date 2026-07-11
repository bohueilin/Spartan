package com.spartan.domain.engine

import com.spartan.domain.model.MetricType

/**
 * Plain-language education for a single WHOOP metric: what it is, what moves it,
 * what a healthy-looking pattern is, and how Spartan's coaching rules use it.
 *
 * These are wellness explanations, not medical interpretations. Every string is
 * written to pass [SafetyEngine.validateCopy]: no diagnoses, no clinical thresholds
 * presented as judgments, no guarantees. "Good" is always framed as trends and
 * consistency against the user's own baseline.
 */
data class MetricExplainer(
    val metric: MetricType,
    val title: String,
    val whatItIs: String,          // 1-2 sentences, plain language
    val whatMovesIt: List<String>, // 3-5 bullets (sleep, alcohol, stress, training load, hydration, illness...)
    val whatGoodLooksLike: String, // trends/consistency framing, NEVER clinical thresholds as judgments
    val howSpartanUsesIt: String,  // ties to the coaching rules in CoachingEngine
)

/**
 * Static explainers for the nine WHOOP-sourced metrics Spartan coaches on.
 * Non-WHOOP metric types (labs, body measurements, custom entries) intentionally
 * have no explainer here; [forMetric] returns null for them.
 */
object MetricExplainers {

    val all: List<MetricExplainer> = listOf(
        MetricExplainer(
            metric = MetricType.RECOVERY_SCORE,
            title = "Recovery",
            whatItIs = "Recovery is WHOOP's 0-100 percent summary of how ready your body looks " +
                "for load today. It blends overnight signals like HRV, resting heart rate, and " +
                "sleep into a single morning read.",
            whatMovesIt = listOf(
                "Sleep quantity and quality the night before.",
                "Training load from the previous day or two.",
                "Alcohol, late meals, and dehydration.",
                "Stress, travel, and irregular schedules.",
                "Getting sick, sometimes a day or two before you feel it.",
            ),
            whatGoodLooksLike = "There is no prize for a perfect score every day. A good pattern " +
                "is recovery that swings with your training and bounces back within a day or " +
                "two, rather than sitting low week after week. Compare today with your own " +
                "recent weeks, not with anyone else's numbers.",
            howSpartanUsesIt = "Recovery sets the tone of your daily plan. On low-recovery days " +
                "Spartan steers you toward mobility and easy walking, mid-range days get steady " +
                "Zone 2 work, and well-recovered days can green-light a quality strength session.",
        ),
        MetricExplainer(
            metric = MetricType.HRV_RMSSD,
            title = "Heart rate variability (HRV)",
            whatItIs = "HRV measures the small timing differences between your heartbeats, in " +
                "milliseconds. It reflects how your nervous system is balancing effort and " +
                "recovery from one day to the next.",
            whatMovesIt = listOf(
                "Sleep, especially consistency and how late you go to bed.",
                "Alcohol, which often lowers HRV the following night.",
                "Hard training, which can suppress HRV for a day or two.",
                "Mental stress and overall life load.",
                "Hydration and coming down with something.",
            ),
            whatGoodLooksLike = "HRV is highly individual, so the raw number matters less than " +
                "your own baseline. A good pattern is HRV that dips after hard days and returns " +
                "to its usual range within a day or two, with a steady or gently rising trend " +
                "over weeks.",
            howSpartanUsesIt = "Spartan watches your HRV against your own baseline, not a " +
                "universal target. When it trends below that baseline, your plan adds a short " +
                "slow-breathing protocol and leans easier, since suppressed HRV often signals " +
                "accumulated stress.",
        ),
        MetricExplainer(
            metric = MetricType.RESTING_HEART_RATE,
            title = "Resting heart rate",
            whatItIs = "Resting heart rate is how many times your heart beats per minute while " +
                "you sleep. It is one of the simplest windows into how recovered you are.",
            whatMovesIt = listOf(
                "Aerobic fitness, which tends to lower it gradually over months.",
                "Alcohol, late eating, and dehydration, which often raise it overnight.",
                "Hard training or an unusually stressful day.",
                "Heat, poor sleep, or getting sick.",
            ),
            whatGoodLooksLike = "Focus on your own baseline rather than a universal number. A " +
                "good pattern is a resting heart rate that stays near its usual range most " +
                "nights and drifts slowly downward as your aerobic base builds.",
            howSpartanUsesIt = "When your resting heart rate runs well above your baseline, " +
                "Spartan adds a hydration and easy-day check-in and softens the day's " +
                "intensity. If a reading looks far outside your usual range, the plan also " +
                "includes a gentle, non-diagnostic reminder to notice how you feel and to " +
                "consider a qualified clinician if it persists.",
        ),
        MetricExplainer(
            metric = MetricType.SLEEP_PERFORMANCE,
            title = "Sleep performance",
            whatItIs = "Sleep performance compares the sleep you actually got with the sleep " +
                "WHOOP estimates your body needed, shown as a percentage.",
            whatMovesIt = listOf(
                "Bedtime consistency and total time in bed.",
                "Caffeine timing, especially in the afternoon and evening.",
                "Alcohol and heavy late meals.",
                "Screens, light, and room temperature near bedtime.",
                "Higher sleep need after hard training or a string of short nights.",
            ),
            whatGoodLooksLike = "Aim for a pattern, not perfection: most nights close to your " +
                "need, and a quick rebound after the occasional short night. Consistent bed and " +
                "wake times move this number more reliably than any single trick.",
            howSpartanUsesIt = "When your recent sleep performance drops below target, Spartan " +
                "adds a wind-down routine to your plan — an earlier caffeine cutoff, dimmer " +
                "screens, and a consistent bedtime — because sleep is the biggest lever behind " +
                "tomorrow's recovery.",
        ),
        MetricExplainer(
            metric = MetricType.SLEEP_DURATION,
            title = "Sleep duration",
            whatItIs = "Sleep duration is the total hours you actually slept, which is usually " +
                "less than the time you spent in bed.",
            whatMovesIt = listOf(
                "When you get into bed and how consistent that time is.",
                "Evening light, screens, and stimulation.",
                "Caffeine and alcohol earlier in the day.",
                "Training load, which can raise how much sleep you need.",
                "Stress and a busy mind at lights-out.",
            ),
            whatGoodLooksLike = "Sleep needs differ from person to person and week to week. A " +
                "good pattern is duration that regularly meets your own sleep need and stays " +
                "fairly consistent, without big weekday-to-weekend swings.",
            howSpartanUsesIt = "Duration feeds your sleep performance and sleep debt, which " +
                "Spartan reads together. When nights run short, your plan leans toward an " +
                "earlier wind-down and lighter training until you catch back up.",
        ),
        MetricExplainer(
            metric = MetricType.SLEEP_DEBT,
            title = "Sleep debt",
            whatItIs = "Sleep debt is the gap between the sleep your body needed recently and " +
                "the sleep you actually got, measured in hours.",
            whatMovesIt = listOf(
                "Several short nights stacking up in a row.",
                "Late nights followed by early alarms.",
                "High training load raising your sleep need.",
                "Travel, schedule changes, and weekend shifts in bedtime.",
            ),
            whatGoodLooksLike = "Small, short-lived debt is normal life. A good pattern is debt " +
                "that gets paid down within a few nights — a slightly earlier bedtime works " +
                "better than one long weekend lie-in.",
            howSpartanUsesIt = "When sleep debt builds past about an hour and a half, Spartan " +
                "treats sleep as the day's priority: your plan adds a wind-down routine and " +
                "keeps training easier so the debt does not keep compounding.",
        ),
        MetricExplainer(
            metric = MetricType.RESPIRATORY_RATE,
            title = "Respiratory rate",
            whatItIs = "Respiratory rate is how many breaths you take per minute during sleep. " +
                "For most people it is one of the steadiest signals WHOOP tracks.",
            whatMovesIt = listOf(
                "Getting sick, which can nudge it upward for a stretch of nights.",
                "Alcohol and heavy late meals.",
                "Sleeping at altitude or in a very warm room.",
                "Unusually high stress or training load.",
            ),
            whatGoodLooksLike = "Stability matters more than the number itself. Your rate tends " +
                "to sit in a narrow personal band night after night, so the useful signal is a " +
                "sustained shift away from that band, not any single reading.",
            howSpartanUsesIt = "Spartan watches for sustained moves away from your baseline. " +
                "When your rate looks unusual, the plan pulls back on intensity and adds a " +
                "check-in rather than an interpretation — Spartan does not diagnose. An unusual " +
                "sustained change is worth mentioning to a qualified clinician, especially if " +
                "it comes with symptoms or does not settle.",
        ),
        MetricExplainer(
            metric = MetricType.DAY_STRAIN,
            title = "Day strain",
            whatItIs = "Day strain is WHOOP's 0-21 measure of the total cardiovascular load you " +
                "accumulated across the day, from workouts and everything in between.",
            whatMovesIt = listOf(
                "Workout duration and intensity.",
                "Time spent in higher heart-rate zones.",
                "Everyday load like walking, stairs, and physical work.",
                "Heat, stress, and a long day on your feet.",
            ),
            whatGoodLooksLike = "Strain is not a score to maximize. A good pattern is strain " +
                "that roughly tracks your recovery — bigger days when you are recovered, " +
                "lighter days when you are not — with genuinely easy days in the mix each week.",
            howSpartanUsesIt = "Spartan looks at yesterday's strain next to today's recovery. " +
                "After a high-strain day that lands on low recovery, your plan swaps intensity " +
                "for active recovery — light stretching, easy walking, hydration — so the work " +
                "you already did can turn into fitness.",
        ),
        MetricExplainer(
            metric = MetricType.ENERGY_KCAL,
            title = "Energy burned",
            whatItIs = "Energy is WHOOP's estimate of the total calories you burned across the " +
                "day, combining your baseline metabolism with activity on top of it.",
            whatMovesIt = listOf(
                "Overall activity and workout volume.",
                "Body size and muscle mass.",
                "Day-to-day movement outside workouts.",
                "Estimation error — treat it as an approximation, not a measurement.",
            ),
            whatGoodLooksLike = "Read it as a trend, not a target. A good pattern is energy " +
                "that rises on bigger training days and settles on rest days, which helps you " +
                "notice when a heavy week quietly asks for more fuel and more rest.",
            howSpartanUsesIt = "Spartan uses energy as context alongside strain: on " +
                "higher-output days, plans emphasize refueling with protein and steady " +
                "hydration so recovery has something to work with. Spartan never uses it to " +
                "prescribe a diet or a calorie target.",
        ),
    )

    private val byMetric: Map<MetricType, MetricExplainer> = all.associateBy { it.metric }

    /** Returns the explainer for a WHOOP-coached metric, or null for any other [MetricType]. */
    fun forMetric(type: MetricType): MetricExplainer? = byMetric[type]
}
