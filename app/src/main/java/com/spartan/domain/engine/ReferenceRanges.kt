package com.spartan.domain.engine

import com.spartan.domain.model.MetricType

/**
 * Age/sex-aware education for the top-5 wearable metrics: what a typical range looks like for
 * the user's bracket, next to their own value. Buckets are deliberately AGE + SEX only — never
 * race (no credible reference data exists for wearable metrics, and clinical medicine is
 * actively removing race-based corrections; see docs/COACH_DESIGN.md §2). All copy is
 * wellness-framed, passes SafetyEngine, and always defers to the user's own baseline.
 *
 * Sources (encoded conservatively as bands, not diagnoses):
 *  - Resting HR: AHA adult span 60–100 bpm; large wearable-cohort percentiles by age/sex show
 *    typical 58–72 with fitness-forward 50–62; women a few bpm higher on average.
 *  - HRV (RMSSD): wearable-cohort medians decline with age (~3–5%/decade); mid-30s ms is a
 *    typical 40s median. Highly individual — bands are wide by design.
 *  - Sleep duration: AASM/NSF consensus 7–9 h for adults 18–64.
 *  - Sleep consistency: sleep-regularity literature associates ±30–45 min bed/wake windows
 *    with better outcomes; WHOOP expresses this as a 0–100 consistency score (≥70 is a solid
 *    pattern for most adults).
 *  - Recovery: WHOOP-proprietary composite; educated as a distribution to read over weeks.
 */
enum class SexAtBirth { FEMALE, MALE, UNSPECIFIED }

data class ReferenceBand(
    val metric: MetricType,
    /** Inclusive typical range for the bracket; null bound = open-ended side. */
    val typicalLow: Double?,
    val typicalHigh: Double?,
    /** Range often seen in aerobically trained people of the bracket (null when n/a). */
    val fitnessForwardLow: Double? = null,
    val fitnessForwardHigh: Double? = null,
    /** One-sentence, bracket-aware education line. */
    val education: String,
)

object ReferenceRanges {

    /** The five metrics the Coach hub educates, in display order. */
    val topFive: List<MetricType> = listOf(
        MetricType.RESTING_HEART_RATE,
        MetricType.HRV_RMSSD,
        MetricType.SLEEP_DURATION,
        MetricType.SLEEP_PERFORMANCE,
        MetricType.RECOVERY_SCORE,
    )

    /**
     * The band for [metric] in the user's bracket, or null when Spartan refuses to fake one.
     * [ageYears]/[sex] are optional — missing demographics widen to all-adult bands.
     */
    fun bandFor(metric: MetricType, ageYears: Int?, sex: SexAtBirth): ReferenceBand? = when (metric) {
        MetricType.RESTING_HEART_RATE -> restingHr(ageYears, sex)
        MetricType.HRV_RMSSD -> hrv(ageYears)
        MetricType.SLEEP_DURATION -> ReferenceBand(
            metric = metric, typicalLow = 7.0, typicalHigh = 9.0,
            education = "Consensus guidance for adults is 7–9 hours of actual sleep. Your WHOOP " +
                "sleep-need estimate personalizes this night to night.",
        )
        MetricType.SLEEP_PERFORMANCE -> ReferenceBand(
            metric = metric, typicalLow = 70.0, typicalHigh = null,
            education = "Sleep performance compares sleep you got with sleep you needed. Most " +
                "adults do well keeping it at 70%+ on most nights, with consistent bed and wake " +
                "times doing the heavy lifting.",
        )
        MetricType.RECOVERY_SCORE -> ReferenceBand(
            metric = metric, typicalLow = null, typicalHigh = null,
            education = "Recovery is not a score to max out — a healthy month mixes green, " +
                "yellow, and the occasional red day. What matters is bouncing back within a day " +
                "or two rather than sitting low for weeks.",
        )
        else -> null
    }

    /** Age bracket label used in the UI ("40–49"), or "adult" when age is unknown. */
    fun bracketLabel(ageYears: Int?): String = when (ageYears) {
        null -> "adult"
        in 0..29 -> "18–29"
        in 30..39 -> "30–39"
        in 40..49 -> "40–49"
        in 50..59 -> "50–59"
        else -> "60+"
    }

    private fun restingHr(ageYears: Int?, sex: SexAtBirth): ReferenceBand {
        // Base typical band by age bracket (cohort percentiles, midspread), then a small,
        // honest sex adjustment: women average a few bpm higher.
        val (lo, hi) = when (ageYears) {
            null -> 58.0 to 75.0
            in 0..29 -> 56.0 to 72.0
            in 30..39 -> 57.0 to 73.0
            in 40..49 -> 58.0 to 74.0
            in 50..59 -> 58.0 to 75.0
            else -> 57.0 to 76.0
        }
        val shift = if (sex == SexAtBirth.FEMALE) 2.0 else 0.0
        return ReferenceBand(
            metric = MetricType.RESTING_HEART_RATE,
            typicalLow = lo + shift, typicalHigh = hi + shift,
            fitnessForwardLow = 50.0 + shift, fitnessForwardHigh = 62.0 + shift,
            education = "Typical resting heart rate for a ${bracketLabel(ageYears)} " +
                "${sexNoun(sex)} sits around ${(lo + shift).toInt()}–${(hi + shift).toInt()} bpm, " +
                "and aerobically trained people often run ${(50 + shift).toInt()}–" +
                "${(62 + shift).toInt()}. A slow drift down over months is the classic sign of a " +
                "building aerobic base.",
        )
    }

    private fun hrv(ageYears: Int?): ReferenceBand {
        // Cohort medians by age bracket; deliberately wide and framed as medians, not targets.
        val (lo, hi) = when (ageYears) {
            null -> 25.0 to 75.0
            in 0..29 -> 45.0 to 95.0
            in 30..39 -> 35.0 to 80.0
            in 40..49 -> 28.0 to 70.0
            in 50..59 -> 22.0 to 60.0
            else -> 18.0 to 50.0
        }
        return ReferenceBand(
            metric = MetricType.HRV_RMSSD,
            typicalLow = lo, typicalHigh = hi,
            education = "HRV is the most individual number on this list — cohort medians for " +
                "${bracketLabel(ageYears)} span roughly ${lo.toInt()}–${hi.toInt()} ms and " +
                "decline naturally with age. Judge yourself against your own baseline trend, " +
                "not this table.",
        )
    }

    private fun sexNoun(sex: SexAtBirth): String = when (sex) {
        SexAtBirth.FEMALE -> "woman"
        SexAtBirth.MALE -> "man"
        SexAtBirth.UNSPECIFIED -> "adult"
    }
}
