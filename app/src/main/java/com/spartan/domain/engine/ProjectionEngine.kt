package com.spartan.domain.engine

import com.spartan.domain.model.MetricType
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/**
 * One projected point on the 8-week improvement chart.
 *
 * [low] and [high] are the numeric bounds of the projected VALUE, always ordered
 * `low <= high` — they are NOT "pessimistic/optimistic" in improvement terms. For a
 * lower-is-better metric like resting heart rate the numerically low bound is the
 * more-improved end of the range; for higher-is-better metrics it is the less-improved end.
 */
data class ProjectionPoint(val week: Int, val low: Double, val high: Double)

data class MetricProjection(
    val metric: MetricType,
    val label: String,            // e.g. "Resting heart rate"
    val unit: String,             // e.g. "bpm"
    val currentValue: Double,
    val points: List<ProjectionPoint>,   // weeks 0,2,4,6,8 — week 0 == current (low==high==current)
    val assumption: String,       // e.g. "If you complete your plan on 5+ days a week"
    val higherIsBetter: Boolean,
)

/**
 * Projects an honest EXPECTED IMPROVEMENT RANGE for a user's wearable metrics over the
 * next 8 weeks if they keep completing their Spartan plan.
 *
 * The ranges are typical effects of consistent aerobic-base plus strength training reported
 * in general exercise-science findings, scaled down conservatively when the user's trailing
 * 7-day consistency is low. They are population-typical ranges — never a personal prediction,
 * diagnosis, or guarantee. Every surface that renders these projections must also render
 * [DISCLAIMER]. The model is fully deterministic: same inputs, same output.
 */
class ProjectionEngine(private val safetyEngine: SafetyEngine = SafetyEngine()) {

    init {
        // Fail fast at construction if the always-rendered copy ever drifts into blocked phrasing.
        safetyEngine.sanitize(DISCLAIMER)
        safetyEngine.sanitize(ASSUMPTION_FULL)
        safetyEngine.sanitize(ASSUMPTION_INACTIVE)
    }

    /**
     * Consistency = days with a completed activity in the trailing 7.
     * Returns projections only for metrics with a current value.
     */
    fun project(
        restingHeartRate: Double?,
        hrvMs: Double?,
        recoveryScore: Int?,
        consistencyDays7: Int,
    ): List<MetricProjection> {
        val days = consistencyDays7.coerceIn(0, 7)
        val effect = when {
            days >= FULL_EFFECT_MIN_DAYS -> EFFECT_FULL
            days >= 3 -> EFFECT_MODERATE
            days >= 1 -> EFFECT_LIGHT
            else -> 0.0
        }
        val assumption = safetyEngine.sanitize(
            when {
                days >= FULL_EFFECT_MIN_DAYS -> ASSUMPTION_FULL
                days >= 1 -> "At your current consistency of $days days a week"
                else -> ASSUMPTION_INACTIVE
            },
        )
        val projections = mutableListOf<MetricProjection>()
        restingHeartRate?.let { projections += projectRestingHeartRate(it, effect, assumption) }
        hrvMs?.let { projections += projectHrv(it, effect, assumption) }
        recoveryScore?.let { projections += projectRecovery(it.toDouble(), effect, assumption) }
        return projections
    }

    /**
     * Resting heart rate (lower is better): full-effect improvement of 3.0–6.0 bpm at week 8,
     * ramping linearly, never projected below max(45 bpm, 85% of current) and never above current.
     */
    private fun projectRestingHeartRate(
        current: Double,
        effect: Double,
        assumption: String,
    ): MetricProjection {
        // Ceil the floor onto the 1-decimal grid so rounding can never land below it.
        val floorValue = ceilTo1(maxOf(RHR_FLOOR_BPM, current * RHR_FLOOR_FRACTION))
        return buildProjection(
            metric = MetricType.RESTING_HEART_RATE,
            current = current,
            effect = effect,
            assumption = assumption,
            // The biggest bpm drop is the most-improved bound, i.e. the numerically LOW value.
            rawLow = { f -> current - RHR_DROP_MAX_BPM * effect * f },
            rawHigh = { f -> current - RHR_DROP_MIN_BPM * effect * f },
            clamp = { v -> v.coerceAtLeast(floorValue).coerceAtMost(current) },
        )
    }

    /**
     * HRV (higher is better): full-effect improvement of +5% to +15% at week 8,
     * ramping linearly, capped at 125% of current.
     */
    private fun projectHrv(current: Double, effect: Double, assumption: String): MetricProjection {
        // Floor the cap onto the 1-decimal grid so rounding can never land above it.
        val cap = floorTo1(current * HRV_CAP_FRACTION)
        return buildProjection(
            metric = MetricType.HRV_RMSSD,
            current = current,
            effect = effect,
            assumption = assumption,
            rawLow = { f -> current * (1.0 + HRV_GAIN_MIN_FRACTION * effect * f) },
            rawHigh = { f -> current * (1.0 + HRV_GAIN_MAX_FRACTION * effect * f) },
            clamp = { v -> v.coerceAtMost(cap) },
        )
    }

    /**
     * Recovery score (higher is better): full-effect improvement of +5 to +12 points at week 8,
     * ramping linearly. Projected gains are hard-capped at 90 and never exceed 99; a user already
     * at or above the cap is shown a flat line at their current value (we never project a decline).
     */
    private fun projectRecovery(current: Double, effect: Double, assumption: String): MetricProjection {
        val cap = minOf(RECOVERY_HARD_LIMIT, maxOf(RECOVERY_SOFT_CAP, current))
        return buildProjection(
            metric = MetricType.RECOVERY_SCORE,
            current = current,
            effect = effect,
            assumption = assumption,
            rawLow = { f -> current + RECOVERY_GAIN_MIN_POINTS * effect * f },
            rawHigh = { f -> current + RECOVERY_GAIN_MAX_POINTS * effect * f },
            clamp = { v -> v.coerceAtMost(cap).coerceAtLeast(current) },
        )
    }

    /**
     * Shared point builder. [rawLow]/[rawHigh] take the linear ramp fraction (week / 8) and
     * must satisfy rawLow(f) <= rawHigh(f); [clamp] must be monotone non-decreasing, so the
     * numeric ordering low <= high survives clamping and rounding.
     */
    private fun buildProjection(
        metric: MetricType,
        current: Double,
        effect: Double,
        assumption: String,
        rawLow: (Double) -> Double,
        rawHigh: (Double) -> Double,
        clamp: (Double) -> Double,
    ): MetricProjection {
        val points = PROJECTED_WEEKS.map { week ->
            if (week == 0 || effect == 0.0) {
                // Week 0 is always exactly the current value; zero consistency projects flat.
                ProjectionPoint(week, current, current)
            } else {
                val fraction = week / HORIZON_WEEKS
                ProjectionPoint(
                    week = week,
                    low = finish(rawLow(fraction), clamp, current),
                    high = finish(rawHigh(fraction), clamp, current),
                )
            }
        }
        return MetricProjection(
            metric = metric,
            label = metric.label,
            unit = metric.unit,
            currentValue = current,
            points = points,
            assumption = assumption,
            higherIsBetter = !metric.lowerIsBetter,
        )
    }

    /** Clamp then round to 1 decimal; a bound clamped back to the current value stays exact. */
    private fun finish(raw: Double, clamp: (Double) -> Double, current: Double): Double {
        val clamped = clamp(raw)
        return if (clamped == current) current else roundTo1(clamped)
    }

    private fun roundTo1(value: Double): Double = round(value * 10.0) / 10.0

    private fun ceilTo1(value: Double): Double = ceil(value * 10.0) / 10.0

    private fun floorTo1(value: Double): Double = floor(value * 10.0) / 10.0

    companion object {
        /** Shown wherever projections render. */
        const val DISCLAIMER: String = "Typical ranges seen with consistent aerobic and strength training in general research — not a prediction or a guarantee for you. Individual results vary. This is wellness guidance, not medical advice."

        private const val ASSUMPTION_FULL = "If you complete your plan on 5+ days a week"
        private const val ASSUMPTION_INACTIVE =
            "Complete a few activities each week to see a projected range"

        private val PROJECTED_WEEKS = listOf(0, 2, 4, 6, 8)
        private const val HORIZON_WEEKS = 8.0

        private const val FULL_EFFECT_MIN_DAYS = 5
        private const val EFFECT_FULL = 1.0
        private const val EFFECT_MODERATE = 0.65
        private const val EFFECT_LIGHT = 0.35

        private const val RHR_DROP_MIN_BPM = 3.0
        private const val RHR_DROP_MAX_BPM = 6.0
        private const val RHR_FLOOR_BPM = 45.0
        private const val RHR_FLOOR_FRACTION = 0.85

        private const val HRV_GAIN_MIN_FRACTION = 0.05
        private const val HRV_GAIN_MAX_FRACTION = 0.15
        private const val HRV_CAP_FRACTION = 1.25

        private const val RECOVERY_GAIN_MIN_POINTS = 5.0
        private const val RECOVERY_GAIN_MAX_POINTS = 12.0
        private const val RECOVERY_SOFT_CAP = 90.0
        private const val RECOVERY_HARD_LIMIT = 99.0
    }
}
