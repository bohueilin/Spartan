package com.spartan.domain.engine

import com.spartan.domain.model.MetricType
import java.time.LocalDate
import kotlin.math.abs

/**
 * The one human sentence for a daily WHOOP metric, plus what its number is always shown with.
 *
 * A number never appears alone: it carries its unit and the user's own usual, and a population
 * reference — only where a meaningful one exists — comes second and is labelled as such. HRV
 * deliberately has none, because there is no population "good" HRV. Every string is wellness
 * copy written to pass [SafetyEngine.validateCopy]: no diagnoses, no alerts, no fixed targets.
 */
data class GlossaryEntry(
    val metric: MetricType,
    val name: String,                // short display name: "HRV", "Resting heart rate"
    val sentence: String,            // the one plain sentence on what the metric is
    val showAlongside: String,       // what the number is always shown with
    val populationNote: String?,     // labelled population reference, shown second; null when none
) {
    /** True when the entry carries a labelled population reference. Never true for HRV. */
    val hasPopulationGrade: Boolean get() = populationNote != null
}

/** Which way a metric has moved over recent days. Direction only — never better or worse. */
enum class Trend { RISING, FALLING, STEADY, LEARNING }

/**
 * Plain-language glossary for the seven daily metrics Spartan coaches on, with the small trend
 * and "vs yesterday" helpers shown next to each number. Lab, body, and custom metric types
 * intentionally have no entry; [entry] returns null for them.
 */
object MetricGlossary {

    /** Readings a trend needs inside its window before Spartan names a direction. */
    const val MIN_TREND_READINGS = 4

    /** A move smaller than this share of the older average reads as steady. */
    private const val STEADY_SHARE = 0.03

    private val entries: List<GlossaryEntry> = listOf(
        GlossaryEntry(
            metric = MetricType.RECOVERY_SCORE,
            name = "Recovery",
            sentence = "How ready your body is for a hard day, based on last night's sleep and heart signals.",
            showAlongside = "% + Green/Yellow/Red + vs yesterday",
            populationNote = null,
        ),
        GlossaryEntry(
            metric = MetricType.HRV_RMSSD,
            name = "HRV",
            sentence = "How much the gaps between your heartbeats vary — a pattern close to your usual is the goal.",
            showAlongside = "ms + vs your usual + 7-day trend — never a population grade",
            populationNote = null,
        ),
        GlossaryEntry(
            metric = MetricType.RESTING_HEART_RATE,
            name = "Resting heart rate",
            sentence = "How fast your heart beats when you're fully at rest — usually lower when " +
                "you're fitter and well-rested.",
            showAlongside = "bpm + vs your usual — the population range comes second",
            populationNote = "For most adults, resting heart rate is 60–100 bpm — your own usual matters more.",
        ),
        GlossaryEntry(
            metric = MetricType.SLEEP_PERFORMANCE,
            name = "Sleep performance",
            sentence = "How much of the sleep your body needed last night you actually got.",
            showAlongside = "% + hours got vs hours needed (never a fixed 8 hours)",
            populationNote = null,
        ),
        GlossaryEntry(
            metric = MetricType.SLEEP_DEBT,
            name = "Sleep debt",
            sentence = "The extra sleep you still owe your body from recent short nights.",
            showAlongside = "hours and minutes — pay it back over a few nights, not one",
            populationNote = null,
        ),
        GlossaryEntry(
            metric = MetricType.DAY_STRAIN,
            name = "Strain",
            sentence = "How much total load your body took on today — workouts plus stress, heat and busy days.",
            showAlongside = "0–21 + zone word + today's target zone",
            populationNote = "Light 0–9 · Moderate 10–13 · High 14–17 · All out 18–21. " +
                "There's no universally good strain.",
        ),
        GlossaryEntry(
            metric = MetricType.RESPIRATORY_RATE,
            name = "Respiratory rate",
            sentence = "How many breaths you took per minute overnight — it usually stays steady, " +
                "so a jump stands out.",
            showAlongside = "breaths per minute + vs your usual",
            populationNote = "Most adults breathe 12–20 times a minute at rest — your own steady " +
                "rate matters more.",
        ),
    )

    /** The metrics with a glossary entry, in the order the glossary presents them. */
    val coachedMetrics: List<MetricType> = entries.map { it.metric }

    private val byMetric: Map<MetricType, GlossaryEntry> = entries.associateBy { it.metric }

    /** Returns the entry for a coached daily metric, or null for any other [MetricType]. */
    fun entry(metric: MetricType): GlossaryEntry? = byMetric[metric]

    /**
     * Direction of [signal] over its last [window] days: the readings dated within [window] days
     * of the latest one, or the last [window] readings when the signal carries no dates. LEARNING
     * until at least [MIN_TREND_READINGS] readings fall inside the window. Otherwise the newer
     * half's average is compared with the older half's (an odd middle reading belongs to neither,
     * so the halves stay equal); a move larger than 3% of the older average reads as rising or
     * falling. A small per-metric floor keeps near-zero values, like a light sleep debt, from
     * flipping on noise.
     */
    fun trend(signal: Signal, window: Int = 7): Trend {
        val recent = recentIndices(signal, window).map { signal.history[it] }
        if (recent.size < MIN_TREND_READINGS) return Trend.LEARNING
        val half = recent.size / 2
        val older = recent.take(half).average()
        val delta = recent.takeLast(half).average() - older
        val threshold = maxOf(abs(older) * STEADY_SHARE, steadyFloor(signal.metric))
        return when {
            delta > threshold -> Trend.RISING
            delta < -threshold -> Trend.FALLING
            else -> Trend.STEADY
        }
    }

    /**
     * Days the readings behind [trend] actually cover — latest date − earliest used date + 1 — so a
     * label never calls gappy readings "7 days". Without dates, the count of readings used.
     */
    fun trendSpanDays(signal: Signal, window: Int = 7): Int {
        val used = recentIndices(signal, window)
        if (used.isEmpty() || !hasDates(signal)) return used.size
        return (signal.dates[used.last()].toEpochDay() - signal.dates[used.first()].toEpochDay() + 1).toInt()
    }

    /**
     * The reading just before [Signal.latest] — not necessarily yesterday's; see [previousIsYesterday]
     * — or null when there is no earlier reading to compare with.
     */
    fun previousValue(signal: Signal): Double? =
        if (signal.history.size < 2) null else signal.history[signal.history.size - 2]

    /** The day of [previousValue], or null when there is none or the signal carries no dates. */
    fun previousDate(signal: Signal): LocalDate? =
        if (!hasDates(signal) || signal.dates.size < 2) null else signal.dates[signal.dates.size - 2]

    /** True only when [previousValue] is dated the day before the latest reading. */
    fun previousIsYesterday(signal: Signal): Boolean {
        val previous = previousDate(signal) ?: return false
        return previous.plusDays(1) == signal.dates.last()
    }

    /** Dates are usable only when there is one per history value. */
    internal fun hasDates(signal: Signal): Boolean = signal.dates.size == signal.history.size

    /**
     * History indices inside the trend window, oldest first: dated within [window] days of the
     * latest, else (no usable dates) the last [window]. [trend], [trendSpanDays] and the Insights
     * chart all read this one window, so the chart always shows exactly the readings the "rising
     * over N days" sentence counts.
     */
    internal fun recentIndices(signal: Signal, window: Int): List<Int> {
        if (window <= 0) return emptyList()
        val size = signal.history.size
        if (!hasDates(signal)) return (maxOf(0, size - window) until size).toList()
        val first = signal.dates.lastOrNull()?.minusDays(window - 1L) ?: return emptyList()
        return signal.history.indices.filter { !signal.dates[it].isBefore(first) }
    }

    /** Smallest move that counts as a direction, in the metric's own unit. */
    private fun steadyFloor(metric: MetricType): Double = when (metric) {
        MetricType.DAY_STRAIN, MetricType.RESPIRATORY_RATE -> 0.1
        MetricType.SLEEP_DEBT -> 0.25 // hours: a quarter hour
        else -> 1.0
    }
}
