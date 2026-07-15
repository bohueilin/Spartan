package com.spartan.domain

import com.spartan.domain.engine.MetricBenefits
import com.spartan.domain.engine.PlanClock
import com.spartan.domain.engine.PlanUrgency
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlanUrgencyTest {

    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    @Test
    fun beforeNoon_incompleteRecommended_isNotUrgent() {
        assertEquals(
            PlanUrgency.NONE,
            PlanClock.urgencyFor(ActivityPriority.RECOMMENDED, ActivityStatus.PLANNED, at(9)),
        )
        assertEquals(
            PlanUrgency.NONE,
            PlanClock.urgencyFor(ActivityPriority.RECOMMENDED, ActivityStatus.PLANNED, at(11, 59)),
        )
    }

    @Test
    fun noonToSixPm_incompleteRecommended_isDue() {
        assertEquals(PlanUrgency.DUE, PlanClock.urgencyFor(ActivityPriority.RECOMMENDED, ActivityStatus.PLANNED, at(12)))
        assertEquals(PlanUrgency.DUE, PlanClock.urgencyFor(ActivityPriority.RECOMMENDED, ActivityStatus.PLANNED, at(17, 59)))
    }

    @Test
    fun afterSixPm_incompleteRecommended_isOverdue() {
        assertEquals(PlanUrgency.OVERDUE, PlanClock.urgencyFor(ActivityPriority.RECOMMENDED, ActivityStatus.PLANNED, at(18)))
        assertEquals(PlanUrgency.OVERDUE, PlanClock.urgencyFor(ActivityPriority.RECOMMENDED, ActivityStatus.PLANNED, at(23, 30)))
    }

    @Test
    fun requiredItemsAlsoEscalate() {
        assertEquals(PlanUrgency.DUE, PlanClock.urgencyFor(ActivityPriority.REQUIRED, ActivityStatus.PLANNED, at(13)))
        assertEquals(PlanUrgency.OVERDUE, PlanClock.urgencyFor(ActivityPriority.REQUIRED, ActivityStatus.PLANNED, at(19)))
    }

    @Test
    fun optionalItemsNeverEscalate() {
        assertEquals(PlanUrgency.NONE, PlanClock.urgencyFor(ActivityPriority.OPTIONAL, ActivityStatus.PLANNED, at(13)))
        assertEquals(PlanUrgency.NONE, PlanClock.urgencyFor(ActivityPriority.OPTIONAL, ActivityStatus.PLANNED, at(20)))
    }

    @Test
    fun doneAndSkippedNeverEscalate_evenLateAndHighPriority() {
        assertEquals(PlanUrgency.NONE, PlanClock.urgencyFor(ActivityPriority.REQUIRED, ActivityStatus.DONE, at(22)))
        assertEquals(PlanUrgency.NONE, PlanClock.urgencyFor(ActivityPriority.RECOMMENDED, ActivityStatus.SKIPPED, at(22)))
    }

    @Test
    fun snoozedAndRescheduledStillEscalate() {
        assertEquals(PlanUrgency.OVERDUE, PlanClock.urgencyFor(ActivityPriority.RECOMMENDED, ActivityStatus.SNOOZED, at(19)))
        assertEquals(PlanUrgency.DUE, PlanClock.urgencyFor(ActivityPriority.RECOMMENDED, ActivityStatus.RESCHEDULED, at(14)))
    }

    @Test
    fun thresholdsAreNoonAndSixPm() {
        assertEquals(12 * 60, PlanClock.DUE_MINUTE_OF_DAY)
        assertEquals(18 * 60, PlanClock.OVERDUE_MINUTE_OF_DAY)
    }

    @Test
    fun metricBenefits_coverEveryMetricActivitiesRelateTo() {
        // Every relatedMetric the coaching engine attaches to an activity must have a phrase, or
        // the card would show a blank "improves" line.
        val coached = listOf(
            MetricType.RECOVERY_SCORE, MetricType.HRV_RMSSD, MetricType.RESTING_HEART_RATE,
            MetricType.SLEEP_PERFORMANCE, MetricType.SLEEP_DEBT, MetricType.DAY_STRAIN,
            MetricType.RESPIRATORY_RATE,
        )
        coached.forEach { assertNotNull("missing benefit for $it", MetricBenefits.forMetric(it)) }
        // Lab metrics have no wellness benefit phrase.
        assertNull(MetricBenefits.forMetric(MetricType.APOB))
    }
}
