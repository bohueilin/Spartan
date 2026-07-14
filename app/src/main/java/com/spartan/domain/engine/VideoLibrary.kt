package com.spartan.domain.engine

import com.spartan.domain.model.Intensity
import com.spartan.domain.model.MetricType

/**
 * A specific, free follow-along training video. Only large, long-established channels are used so
 * links stay alive; every URL is verified against YouTube's oEmbed endpoint by VideoLibraryTest.
 * Opening a video is always user-initiated (a tap) and leaves the app — Spartan itself still makes
 * no network calls.
 */
data class VideoGuide(
    val id: String,
    val title: String,
    val channel: String,
    val minutes: Int,
    val intensity: Intensity,
    val url: String,
)

/** The training story for one metric: why exercise moves it, and which sessions to follow. */
data class MetricTraining(
    val intro: String,
    val guides: List<VideoGuide>,
)

/**
 * Maps Spartan's metrics and daily-plan activities to concrete follow-along videos, so "go do
 * Zone 2" always comes with a session the user can press play on. Copy is wellness-framed and
 * SafetyEngine-checked in tests; metrics where guidance belongs to a clinician first (ApoB, Lp(a),
 * CAC) deliberately get no training block.
 */
object VideoLibrary {

    // --- The video shelf (small on purpose; each slot is reused across metrics) ---------------

    private val ZONE2_WALK_30 = VideoGuide(
        id = "zone2_walk_30",
        title = "30-minute indoor power walk",
        channel = "Walk at Home",
        minutes = 30,
        intensity = Intensity.MODERATE,
        url = "https://www.youtube.com/watch?v=enYITYwvPAQ",
    )

    private val EASY_WALK_15 = VideoGuide(
        id = "easy_walk_15",
        title = "15-minute easy walk",
        channel = "Walk at Home",
        minutes = 15,
        intensity = Intensity.EASY,
        url = "https://www.youtube.com/watch?v=njeZ29umqVE",
    )

    private val STRENGTH_30 = VideoGuide(
        id = "strength_full_body_30",
        title = "30-minute full-body strength (beginner, light weights or bottles)",
        channel = "HASfit",
        minutes = 30,
        intensity = Intensity.HARD,
        url = "https://www.youtube.com/watch?v=R4x9rXThlQs",
    )

    private val MOBILITY_10 = VideoGuide(
        id = "mobility_10",
        title = "10-minute full-body mobility",
        channel = "Fraser Wilson",
        minutes = 10,
        intensity = Intensity.EASY,
        url = "https://www.youtube.com/watch?v=Igzmhbghcd4",
    )

    private val STRETCH_10 = VideoGuide(
        id = "stretch_10",
        title = "10-minute full-body stretch",
        channel = "MadFit",
        minutes = 10,
        intensity = Intensity.EASY,
        url = "https://www.youtube.com/watch?v=yLDAHd_C7G0",
    )

    private val BREATHING_5 = VideoGuide(
        id = "breathing_5",
        title = "5-minute calming breathwork",
        channel = "Yoga With Adriene",
        minutes = 5,
        intensity = Intensity.REST,
        url = "https://www.youtube.com/watch?v=9fEo9my03Ks",
    )

    private val WINDDOWN_20 = VideoGuide(
        id = "winddown_sleep_20",
        title = "20-minute bedtime yoga wind-down",
        channel = "Yoga With Adriene",
        minutes = 20,
        intensity = Intensity.REST,
        url = "https://www.youtube.com/watch?v=v7SN-d4qXx0",
    )

    private val POST_MEAL_WALK_10 = VideoGuide(
        id = "post_meal_walk_10",
        title = "10-minute after-meal walk",
        channel = "Walk at Home",
        minutes = 10,
        intensity = Intensity.EASY,
        url = "https://www.youtube.com/watch?v=tVpUCkMLgms",
    )

    private val LOW_IMPACT_CARDIO_20 = VideoGuide(
        id = "low_impact_cardio_20",
        title = "20-minute low-impact cardio (no jumping)",
        channel = "MadFit",
        minutes = 20,
        intensity = Intensity.MODERATE,
        url = "https://www.youtube.com/watch?v=DMAxIrCAAZ0",
    )

    val allGuides: List<VideoGuide> = listOf(
        ZONE2_WALK_30, EASY_WALK_15, STRENGTH_30, MOBILITY_10, STRETCH_10,
        BREATHING_5, WINDDOWN_20, POST_MEAL_WALK_10, LOW_IMPACT_CARDIO_20,
    )

    // --- Metric → training ---------------------------------------------------------------------

    private val trainings: Map<MetricType, MetricTraining> = mapOf(
        MetricType.RESTING_HEART_RATE to MetricTraining(
            intro = "Consistent easy aerobic work is the most reliable lever for a lower resting " +
                "heart rate over 6–12 weeks. Strength adds to the effect.",
            guides = listOf(ZONE2_WALK_30, LOW_IMPACT_CARDIO_20, STRENGTH_30),
        ),
        MetricType.HRV_RMSSD to MetricTraining(
            intro = "Slow breathing acutely supports HRV, and a steady aerobic base plus good sleep " +
                "raise its floor over time.",
            guides = listOf(BREATHING_5, ZONE2_WALK_30, WINDDOWN_20),
        ),
        MetricType.RECOVERY_SCORE to MetricTraining(
            intro = "On low-recovery days, gentle movement supports blood flow without adding " +
                "strain — that is what tends to bring tomorrow's score back.",
            guides = listOf(STRETCH_10, EASY_WALK_15, BREATHING_5),
        ),
        MetricType.SLEEP_PERFORMANCE to MetricTraining(
            intro = "A consistent wind-down helps you fall asleep faster and sleep deeper; daytime " +
                "movement supports it from the other side.",
            guides = listOf(WINDDOWN_20, BREATHING_5),
        ),
        MetricType.SLEEP_DURATION to MetricTraining(
            intro = "Protect the runway into bed: a short wind-down at a consistent time is the " +
                "highest-leverage habit for longer sleep.",
            guides = listOf(WINDDOWN_20, BREATHING_5),
        ),
        MetricType.SLEEP_DEBT to MetricTraining(
            intro = "Paying down sleep debt is mostly about earlier, calmer nights — a wind-down " +
                "routine makes the earlier bedtime actually stick.",
            guides = listOf(WINDDOWN_20, BREATHING_5),
        ),
        MetricType.RESPIRATORY_RATE to MetricTraining(
            intro = "Slow-paced breathing practice supports a calm nighttime respiratory pattern.",
            guides = listOf(BREATHING_5),
        ),
        MetricType.DAY_STRAIN to MetricTraining(
            intro = "If strain runs low, add structured aerobic sessions; if it spikes, swap one " +
                "for an easy walk. Consistency beats intensity.",
            guides = listOf(ZONE2_WALK_30, LOW_IMPACT_CARDIO_20),
        ),
        MetricType.ENERGY_KCAL to MetricTraining(
            intro = "Daily energy burned follows activity volume — steady aerobic work and " +
                "strength sessions raise it sustainably.",
            guides = listOf(ZONE2_WALK_30, STRENGTH_30),
        ),
        MetricType.EXERCISE_MINUTES to MetricTraining(
            intro = "A common baseline is ~150 minutes of moderate activity per week. These " +
                "sessions are an easy way to bank them.",
            guides = listOf(ZONE2_WALK_30, LOW_IMPACT_CARDIO_20, STRENGTH_30),
        ),
        MetricType.FASTING_GLUCOSE to MetricTraining(
            intro = "Easy walks after meals and regular strength work support healthy glucose " +
                "patterns. Trends matter more than any single reading.",
            guides = listOf(POST_MEAL_WALK_10, STRENGTH_30, ZONE2_WALK_30),
        ),
        MetricType.TRIGLYCERIDES to MetricTraining(
            intro = "Regular aerobic exercise supports healthy triglyceride patterns, alongside " +
                "nutrition and sleep.",
            guides = listOf(ZONE2_WALK_30, LOW_IMPACT_CARDIO_20),
        ),
        MetricType.HDL_C to MetricTraining(
            intro = "Aerobic exercise is one of the few levers that supports HDL over time — " +
                "volume and consistency count most.",
            guides = listOf(LOW_IMPACT_CARDIO_20, ZONE2_WALK_30),
        ),
        MetricType.TG_HDL_RATIO to MetricTraining(
            intro = "The ratio tends to follow the same habits: steady aerobic work, strength, " +
                "and consistent sleep.",
            guides = listOf(ZONE2_WALK_30, STRENGTH_30),
        ),
        MetricType.SYSTOLIC_BP to MetricTraining(
            intro = "Moderate aerobic activity and slow-breathing practice both support healthy " +
                "blood-pressure patterns. Ease in and stay conversational.",
            guides = listOf(EASY_WALK_15, ZONE2_WALK_30, BREATHING_5),
        ),
        MetricType.DIASTOLIC_BP to MetricTraining(
            intro = "Moderate aerobic activity and slow-breathing practice both support healthy " +
                "blood-pressure patterns. Ease in and stay conversational.",
            guides = listOf(EASY_WALK_15, ZONE2_WALK_30, BREATHING_5),
        ),
        MetricType.WEIGHT to MetricTraining(
            intro = "Strength work preserves muscle while aerobic volume adds energy burn — the " +
                "sustainable combination.",
            guides = listOf(STRENGTH_30, LOW_IMPACT_CARDIO_20, ZONE2_WALK_30),
        ),
        MetricType.BMI to MetricTraining(
            intro = "Strength work preserves muscle while aerobic volume adds energy burn — " +
                "trends and habits matter more than one number.",
            guides = listOf(STRENGTH_30, LOW_IMPACT_CARDIO_20, ZONE2_WALK_30),
        ),
        MetricType.WAIST_CIRCUMFERENCE to MetricTraining(
            intro = "Aerobic volume plus strength is the classic combination for waist trends.",
            guides = listOf(ZONE2_WALK_30, STRENGTH_30),
        ),
        MetricType.WAIST_TO_HEIGHT_RATIO to MetricTraining(
            intro = "Aerobic volume plus strength is the classic combination for waist trends.",
            guides = listOf(ZONE2_WALK_30, STRENGTH_30),
        ),
        MetricType.VITAMIN_D_25OH to MetricTraining(
            intro = "Take your easy walks outside in daylight — the movement helps, and the " +
                "daylight is the point for vitamin D.",
            guides = listOf(EASY_WALK_15),
        ),
        MetricType.STRENGTH_SESSIONS to MetricTraining(
            intro = "Two quality strength sessions a week is the baseline worth protecting.",
            guides = listOf(STRENGTH_30),
        ),
        // ApoB, Lp(a), CAC, custom: clinician-first territory — no training block on purpose.
    )

    /** The training block for [metric], or null where exercise guidance isn't Spartan's to give. */
    fun trainingFor(metric: MetricType): MetricTraining? = trainings[metric]

    // --- Daily activity → video -----------------------------------------------------------------

    /**
     * The follow-along video for a generated plan activity, keyed by the stable slug in its id
     * ("<epochDay>:<slug>"). Pain, clinician, and trivial one-minute items get no video on purpose.
     */
    fun guideForActivity(activityId: String): VideoGuide? =
        when (activityId.substringAfter(':', missingDelimiterValue = "")) {
            "mobility" -> MOBILITY_10
            "zone2-walk" -> EASY_WALK_15
            "zone2" -> ZONE2_WALK_30
            "strength" -> STRENGTH_30
            "stretch" -> STRETCH_10
            "winddown" -> WINDDOWN_20
            "breathing" -> BREATHING_5
            "quickwin" -> MOBILITY_10
            "connect-whoop" -> MOBILITY_10
            else -> null
        }
}
