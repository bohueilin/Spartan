package com.spartan.domain.engine

import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.MetricType

/** How overdue an incomplete plan item has become as the day passes. */
enum class PlanUrgency { NONE, DUE, OVERDUE }

/**
 * Time-of-day escalation for the daily plan. An incomplete, non-optional activity turns amber at
 * midday and red in the evening, so a plan the user meant to do doesn't quietly slip past. Pure
 * and time-injected so it is fully unit-testable; the UI feeds it the device-local minute of day
 * (which is Pacific for a PST user) and the background nudge reuses the same thresholds.
 */
object PlanClock {
    /** 12:00 local — by midday an untouched plan should be underway (amber). */
    const val DUE_MINUTE_OF_DAY = 12 * 60

    /** 18:00 local — after 6pm an untouched plan is overdue (red + a local nudge). */
    const val OVERDUE_MINUTE_OF_DAY = 18 * 60

    fun urgencyFor(priority: ActivityPriority, status: ActivityStatus, minuteOfDay: Int): PlanUrgency {
        val open = status != ActivityStatus.DONE && status != ActivityStatus.SKIPPED
        // Finished work and low-stakes optional items (e.g. hydration) never nag.
        if (!open || priority == ActivityPriority.OPTIONAL) return PlanUrgency.NONE
        return when {
            minuteOfDay >= OVERDUE_MINUTE_OF_DAY -> PlanUrgency.OVERDUE
            minuteOfDay >= DUE_MINUTE_OF_DAY -> PlanUrgency.DUE
            else -> PlanUrgency.NONE
        }
    }
}

/**
 * Short, wellness-framed phrase for what improving a metric does for the user — surfaced on plan
 * cards ("Improves your recovery") so every recommended activity says which number it moves.
 * Only the WHOOP metrics Spartan coaches on have a phrase; anything else returns null.
 */
object MetricBenefits {
    private val phrases = mapOf(
        MetricType.RECOVERY_SCORE to "how ready your body is each morning",
        MetricType.HRV_RMSSD to "your heart-rate variability and stress balance",
        MetricType.RESTING_HEART_RATE to "your resting heart rate and aerobic base",
        MetricType.SLEEP_PERFORMANCE to "how well you sleep against your need",
        MetricType.SLEEP_DURATION to "the sleep you get each night",
        MetricType.SLEEP_DEBT to "how quickly you clear sleep debt",
        MetricType.DAY_STRAIN to "how well today's load matches your recovery",
        MetricType.RESPIRATORY_RATE to "your breathing steadiness overnight",
    )

    fun forMetric(type: MetricType): String? = phrases[type]
}
