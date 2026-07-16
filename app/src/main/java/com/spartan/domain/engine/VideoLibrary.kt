package com.spartan.domain.engine

import com.spartan.domain.model.Intensity
import com.spartan.domain.model.MetricType

/** How demanding a session is to follow — used to bias picks for beginners and older adults. */
enum class TrainingLevel { BEGINNER, INTERMEDIATE }

/** Joint load. Every shipped pick is LOW; the field lets the ranker prefer it explicitly. */
enum class ImpactLevel { LOW, MODERATE }

/** What kind of session it is, so a metric can draw from the right movement family. */
enum class Modality { WALK, CARDIO, STRENGTH, MOBILITY, PILATES, YOGA, BREATH }

/**
 * A specific, free follow-along training video. Only large, long-established channels are used and
 * every URL is a real video verified against YouTube's oEmbed endpoint (author + title matched)
 * before shipping; the offline [VideoLibraryTest] re-checks format and copy safety. Opening a video
 * is always user-initiated (a tap) and leaves the app — Spartan itself makes no network calls.
 */
data class VideoGuide(
    val id: String,
    val title: String,
    val channel: String,
    val minutes: Int,
    val intensity: Intensity,
    val level: TrainingLevel,
    val impact: ImpactLevel,
    val modality: Modality,
    val url: String,
)

/** The training story for one metric: why exercise moves it, and which sessions to follow. */
data class MetricTraining(
    val intro: String,
    val guides: List<VideoGuide>,
)

/**
 * Who we're recommending for. Age biases picks toward joint-friendly, progressive sessions; a
 * metric being off-target makes the recommendation lead with the gentlest, most sustainable option
 * (consistency beats intensity when a number needs to move). Both are optional — with neither set,
 * the curated default order stands.
 */
data class TrainingProfile(
    val ageYears: Int? = null,
    val offTargetMetrics: Set<MetricType> = emptySet(),
) {
    /** Adults ~40+ benefit from low-impact, beginner-first defaults (per the app's coaching notes). */
    fun favorsGentle(): Boolean = ageYears != null && ageYears >= GENTLE_AGE

    companion object {
        const val GENTLE_AGE = 40
        val NONE = TrainingProfile()
    }
}

/**
 * Maps Spartan's metrics and daily-plan activities to concrete follow-along videos, and ranks them
 * for the individual. The catalog is curated from the channels an evidence-minded coach recommends
 * for healthy adults around 40 starting a weight-loss/fitness journey (Fitness Blender, Team Body
 * Project, HASfit, Juice & Toya, growwithjo, Walk at Home, Yoga With Adriene, Caroline Girvan) —
 * beginner, low-impact, well-cued sessions rather than random HIIT. Metrics whose guidance belongs
 * to a clinician first (ApoB, Lp(a), CAC) deliberately get no training block.
 */
object VideoLibrary {

    private fun yt(id: String) = "https://www.youtube.com/watch?v=$id"

    // --- The video shelf: real, oEmbed-verified sessions (see re-verification in the PR) ---------

    // Walking / low-impact aerobic — the base that lowers resting heart rate over weeks.
    private val WALK_HAPPY_15 = VideoGuide(
        "walk_happy_15", "1-mile Happy Walk", "Walk at Home", 15,
        Intensity.EASY, TrainingLevel.BEGINNER, ImpactLevel.LOW, Modality.WALK, yt("X3q5e1pV4pc"),
    )
    private val WALK_LOWIMPACT_30 = VideoGuide(
        "walk_lowimpact_30", "30-minute low-impact walk", "growwithjo", 30,
        Intensity.MODERATE, TrainingLevel.BEGINNER, ImpactLevel.LOW, Modality.WALK, yt("yV4jyj8Hr1g"),
    )
    private val WALK_FAST_15 = VideoGuide(
        "walk_fast_15", "15-minute brisk walk (great after meals)", "growwithjo", 15,
        Intensity.EASY, TrainingLevel.BEGINNER, ImpactLevel.LOW, Modality.WALK, yt("NlVndT8-B5U"),
    )
    private val CARDIO_STANDING_20 = VideoGuide(
        "cardio_standing_20", "20-minute no-jumping home cardio", "Team Body Project", 20,
        Intensity.MODERATE, TrainingLevel.BEGINNER, ImpactLevel.LOW, Modality.CARDIO, yt("x3aogIZfVUI"),
    )
    private val CARDIO_BEGINNER_25 = VideoGuide(
        "cardio_beginner_25", "25-minute beginner low-impact cardio", "Team Body Project", 25,
        Intensity.MODERATE, TrainingLevel.BEGINNER, ImpactLevel.LOW, Modality.CARDIO, yt("iNW4lCU693Q"),
    )

    // Beginner full-body strength — protects muscle and adds to the resting-HR effect.
    private val STRENGTH_FB_28 = VideoGuide(
        "strength_fb_28", "28-minute total-body strength + core (beginner)", "Fitness Blender", 28,
        Intensity.MODERATE, TrainingLevel.BEGINNER, ImpactLevel.LOW, Modality.STRENGTH, yt("Gze8oMuj4as"),
    )
    private val STRENGTH_HASFIT_20 = VideoGuide(
        "strength_hasfit_20", "20-minute full-body dumbbells (beginner, two intensities)", "HASfit", 20,
        Intensity.MODERATE, TrainingLevel.BEGINNER, ImpactLevel.LOW, Modality.STRENGTH, yt("VPC6U9CjKMU"),
    )
    private val STRENGTH_HASFIT_JOINT_20 = VideoGuide(
        "strength_hasfit_joint_20", "20-minute joint-friendly dumbbell strength", "HASfit", 20,
        Intensity.MODERATE, TrainingLevel.BEGINNER, ImpactLevel.LOW, Modality.STRENGTH, yt("E9XfzxIlfAU"),
    )
    private val STRENGTH_JUICETOYA_30 = VideoGuide(
        "strength_juicetoya_30", "30-minute full-body dumbbells (guided form cues)", "Juice & Toya", 30,
        Intensity.MODERATE, TrainingLevel.BEGINNER, ImpactLevel.LOW, Modality.STRENGTH, yt("6Xr8EOWJrvY"),
    )
    private val STRENGTH_GIRVAN_EPIC_30 = VideoGuide(
        "strength_girvan_epic_30", "Beginner EPIC · 30-minute dumbbell full body", "Caroline Girvan", 30,
        Intensity.HARD, TrainingLevel.INTERMEDIATE, ImpactLevel.LOW, Modality.STRENGTH, yt("nKzZLE6s1cs"),
    )

    // Mobility / pilates / stretch — recovery-day movement that eases strain without adding load.
    private val PILATES_FB_16 = VideoGuide(
        "pilates_fb_16", "16-minute low-impact Pilates (core, glutes, thighs)", "Fitness Blender", 16,
        Intensity.EASY, TrainingLevel.BEGINNER, ImpactLevel.LOW, Modality.PILATES, yt("zdNlYAugUtg"),
    )
    private val STRETCH_FB_10 = VideoGuide(
        "stretch_fb_10", "10-minute feel-good cool-down stretch", "Fitness Blender", 10,
        Intensity.REST, TrainingLevel.BEGINNER, ImpactLevel.LOW, Modality.MOBILITY, yt("P8DOZRtIIEQ"),
    )
    private val MOBILITY_YOGA_11 = VideoGuide(
        "mobility_yoga_11", "11-minute morning mobility yoga", "Yoga With Adriene", 11,
        Intensity.EASY, TrainingLevel.BEGINNER, ImpactLevel.LOW, Modality.MOBILITY, yt("2IcWJobNDck"),
    )
    private val YOGA_GENTLE_25 = VideoGuide(
        "yoga_gentle_25", "25-minute gentle morning yoga", "Yoga With Adriene", 25,
        Intensity.EASY, TrainingLevel.BEGINNER, ImpactLevel.LOW, Modality.YOGA, yt("jsLAc-2y0bE"),
    )

    // Breathwork + wind-down — the levers for HRV, respiratory rate, and sleep.
    private val BREATH_5 = VideoGuide(
        "breath_5", "5-minute calming breathwork", "Yoga With Adriene", 5,
        Intensity.REST, TrainingLevel.BEGINNER, ImpactLevel.LOW, Modality.BREATH, yt("9fEo9my03Ks"),
    )
    private val MEDITATION_STRESS_15 = VideoGuide(
        "meditation_stress_15", "15-minute stillness for stress relief", "Yoga With Adriene", 15,
        Intensity.REST, TrainingLevel.BEGINNER, ImpactLevel.LOW, Modality.BREATH, yt("CscxGprl1yw"),
    )
    private val YOGA_BEDTIME_20 = VideoGuide(
        "yoga_bedtime_20", "20-minute bedtime yoga wind-down", "Yoga With Adriene", 20,
        Intensity.REST, TrainingLevel.BEGINNER, ImpactLevel.LOW, Modality.YOGA, yt("v7SN-d4qXx0"),
    )
    private val YOGA_WINDDOWN_12 = VideoGuide(
        "yoga_winddown_12", "12-minute wind-down bedtime yoga", "Yoga With Adriene", 12,
        Intensity.REST, TrainingLevel.BEGINNER, ImpactLevel.LOW, Modality.YOGA, yt("BiWDsfZ3zbo"),
    )

    val allGuides: List<VideoGuide> = listOf(
        WALK_HAPPY_15, WALK_LOWIMPACT_30, WALK_FAST_15, CARDIO_STANDING_20, CARDIO_BEGINNER_25,
        STRENGTH_FB_28, STRENGTH_HASFIT_20, STRENGTH_HASFIT_JOINT_20, STRENGTH_JUICETOYA_30, STRENGTH_GIRVAN_EPIC_30,
        PILATES_FB_16, STRETCH_FB_10, MOBILITY_YOGA_11, YOGA_GENTLE_25,
        BREATH_5, MEDITATION_STRESS_15, YOGA_BEDTIME_20, YOGA_WINDDOWN_12,
    )

    // --- Metric → training (guides listed in default effectiveness order) -----------------------

    private val trainings: Map<MetricType, MetricTraining> = mapOf(
        MetricType.RESTING_HEART_RATE to MetricTraining(
            "Consistent easy aerobic work is the most reliable lever for a lower resting heart rate " +
                "over 6–12 weeks. Two weekly strength sessions add to the effect.",
            listOf(WALK_LOWIMPACT_30, CARDIO_STANDING_20, WALK_HAPPY_15, STRENGTH_FB_28),
        ),
        MetricType.HRV_RMSSD to MetricTraining(
            "Slow breathing acutely supports HRV; a steady aerobic base and good sleep raise its " +
                "floor over time.",
            listOf(BREATH_5, MEDITATION_STRESS_15, YOGA_WINDDOWN_12, WALK_LOWIMPACT_30),
        ),
        MetricType.RECOVERY_SCORE to MetricTraining(
            "On low-recovery days, gentle movement supports blood flow without adding strain — that " +
                "is what tends to bring tomorrow's score back.",
            listOf(STRETCH_FB_10, MOBILITY_YOGA_11, WALK_HAPPY_15, BREATH_5),
        ),
        MetricType.SLEEP_PERFORMANCE to MetricTraining(
            "A consistent wind-down helps you fall asleep faster and sleep deeper; daytime movement " +
                "supports it from the other side.",
            listOf(YOGA_BEDTIME_20, YOGA_WINDDOWN_12, BREATH_5),
        ),
        MetricType.SLEEP_DURATION to MetricTraining(
            "Protect the runway into bed: a short wind-down at a consistent time is the " +
                "highest-leverage habit for longer sleep.",
            listOf(YOGA_BEDTIME_20, YOGA_WINDDOWN_12, BREATH_5),
        ),
        MetricType.SLEEP_DEBT to MetricTraining(
            "Paying down sleep debt is mostly about earlier, calmer nights — a wind-down routine " +
                "makes the earlier bedtime actually stick.",
            listOf(YOGA_WINDDOWN_12, YOGA_BEDTIME_20, BREATH_5),
        ),
        MetricType.RESPIRATORY_RATE to MetricTraining(
            "Slow-paced breathing practice supports a calm nighttime respiratory pattern.",
            listOf(BREATH_5, MEDITATION_STRESS_15, YOGA_WINDDOWN_12),
        ),
        MetricType.DAY_STRAIN to MetricTraining(
            "If strain runs low, add structured low-impact cardio; if it spikes, swap one for an " +
                "easy walk or mobility. Consistency beats intensity.",
            listOf(WALK_LOWIMPACT_30, CARDIO_STANDING_20, STRETCH_FB_10),
        ),
        MetricType.ENERGY_KCAL to MetricTraining(
            "Daily energy burned follows activity volume — steady aerobic work and strength sessions " +
                "raise it sustainably.",
            listOf(STRENGTH_JUICETOYA_30, WALK_LOWIMPACT_30, STRENGTH_FB_28),
        ),
        MetricType.EXERCISE_MINUTES to MetricTraining(
            "A common baseline is ~150 minutes of moderate activity per week plus two strength days. " +
                "These sessions are an easy way to bank them.",
            listOf(WALK_LOWIMPACT_30, CARDIO_STANDING_20, STRENGTH_FB_28),
        ),
        MetricType.FASTING_GLUCOSE to MetricTraining(
            "Easy walks after meals and regular strength work support healthy glucose patterns. " +
                "Trends matter more than any single reading.",
            listOf(WALK_FAST_15, STRENGTH_FB_28, WALK_LOWIMPACT_30),
        ),
        MetricType.TRIGLYCERIDES to MetricTraining(
            "Regular aerobic exercise supports healthy triglyceride patterns, alongside nutrition " +
                "and sleep.",
            listOf(WALK_LOWIMPACT_30, CARDIO_STANDING_20),
        ),
        MetricType.HDL_C to MetricTraining(
            "Aerobic exercise is one of the few levers that supports HDL over time — volume and " +
                "consistency count most.",
            listOf(CARDIO_STANDING_20, WALK_LOWIMPACT_30),
        ),
        MetricType.TG_HDL_RATIO to MetricTraining(
            "The ratio tends to follow the same habits: steady aerobic work, strength, and " +
                "consistent sleep.",
            listOf(WALK_LOWIMPACT_30, STRENGTH_FB_28),
        ),
        MetricType.SYSTOLIC_BP to MetricTraining(
            "Moderate aerobic activity and slow-breathing practice both support healthy " +
                "blood-pressure patterns. Ease in and stay conversational.",
            listOf(WALK_HAPPY_15, WALK_LOWIMPACT_30, BREATH_5),
        ),
        MetricType.DIASTOLIC_BP to MetricTraining(
            "Moderate aerobic activity and slow-breathing practice both support healthy " +
                "blood-pressure patterns. Ease in and stay conversational.",
            listOf(WALK_HAPPY_15, WALK_LOWIMPACT_30, BREATH_5),
        ),
        MetricType.WEIGHT to MetricTraining(
            "Strength work preserves muscle while aerobic volume adds energy burn — the sustainable " +
                "combination.",
            listOf(STRENGTH_JUICETOYA_30, CARDIO_STANDING_20, WALK_LOWIMPACT_30),
        ),
        MetricType.BMI to MetricTraining(
            "Strength work preserves muscle while aerobic volume adds energy burn — trends and " +
                "habits matter more than one number.",
            listOf(STRENGTH_JUICETOYA_30, CARDIO_STANDING_20, WALK_LOWIMPACT_30),
        ),
        MetricType.WAIST_CIRCUMFERENCE to MetricTraining(
            "Aerobic volume plus core-focused strength is the classic combination for waist trends.",
            listOf(PILATES_FB_16, WALK_LOWIMPACT_30, STRENGTH_FB_28),
        ),
        MetricType.WAIST_TO_HEIGHT_RATIO to MetricTraining(
            "Aerobic volume plus core-focused strength is the classic combination for waist trends.",
            listOf(PILATES_FB_16, WALK_LOWIMPACT_30, STRENGTH_FB_28),
        ),
        MetricType.VITAMIN_D_25OH to MetricTraining(
            "Take your easy walks outside in daylight — the movement helps, and the daylight is the " +
                "point for vitamin D.",
            listOf(WALK_HAPPY_15),
        ),
        MetricType.STRENGTH_SESSIONS to MetricTraining(
            "Two quality strength sessions a week is the baseline worth protecting.",
            listOf(STRENGTH_HASFIT_20, STRENGTH_JUICETOYA_30, STRENGTH_GIRVAN_EPIC_30),
        ),
        // ApoB, Lp(a), CAC, custom: clinician-first territory — no training block on purpose.
    )

    private const val MAX_GUIDES = 3

    /**
     * The training block for [metric], ranked for [profile]: with no profile the curated
     * effectiveness order stands; for an older adult, or a metric that's off-target, picks are
     * re-ranked to lead with the gentlest, most sustainable sessions and the intro says so.
     */
    fun recommend(metric: MetricType, profile: TrainingProfile = TrainingProfile.NONE): MetricTraining? {
        val base = trainings[metric] ?: return null
        val gentleFirst = profile.favorsGentle() || metric in profile.offTargetMetrics
        val ranked = if (gentleFirst) {
            // Lead an older or struggling beginner with beginner-level sessions only — the
            // harder intermediate progressions are hidden while any beginner option exists —
            // then order the survivors gentlest-first (ties keep curated order).
            val beginner = base.guides.filter { it.level == TrainingLevel.BEGINNER }
            (beginner.ifEmpty { base.guides }).sortedByDescending(::gentlenessScore)
        } else {
            base.guides
        }.take(MAX_GUIDES)
        val intro = if (gentleFirst && ranked.any()) {
            base.intro + " These picks are low-impact and beginner-friendly."
        } else {
            base.intro
        }
        return MetricTraining(intro, ranked)
    }

    /** Backward-compatible entry point (no personalization). */
    fun trainingFor(metric: MetricType): MetricTraining? = recommend(metric, TrainingProfile.NONE)

    private fun gentlenessScore(g: VideoGuide): Int {
        var s = 0
        if (g.impact == ImpactLevel.LOW) s += 3
        if (g.level == TrainingLevel.BEGINNER) s += 2
        if (g.intensity != Intensity.HARD) s += 1
        return s
    }

    // --- Daily activity → video -----------------------------------------------------------------

    /**
     * The follow-along video for a generated plan activity, keyed by the stable slug in its id
     * ("<epochDay>:<slug>"). For strength, an older adult gets the gentler beginner session. Pain,
     * clinician, and trivial one-minute items get no video on purpose.
     */
    fun guideForActivity(activityId: String, profile: TrainingProfile = TrainingProfile.NONE): VideoGuide? =
        when (activityId.substringAfter(':', missingDelimiterValue = "")) {
            "mobility" -> MOBILITY_YOGA_11
            "zone2-walk" -> WALK_HAPPY_15
            "zone2" -> WALK_LOWIMPACT_30
            "strength" -> if (profile.favorsGentle()) STRENGTH_HASFIT_20 else STRENGTH_JUICETOYA_30
            "stretch" -> STRETCH_FB_10
            "winddown" -> YOGA_BEDTIME_20
            "breathing" -> BREATH_5
            "quickwin" -> WALK_FAST_15
            "connect-whoop" -> MOBILITY_YOGA_11
            else -> null
        }
}
