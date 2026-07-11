package com.spartan.domain

import com.spartan.domain.engine.MetricProjection
import com.spartan.domain.engine.ProjectionEngine
import com.spartan.domain.engine.SafetyEngine
import com.spartan.domain.model.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionEngineTest {
    private val engine = ProjectionEngine()
    private val safety = SafetyEngine()

    private fun List<MetricProjection>.byMetric(metric: MetricType): MetricProjection =
        first { it.metric == metric }

    @Test
    fun week0_equalsCurrentExactly() {
        val projections = engine.project(
            restingHeartRate = 58.37,
            hrvMs = 47.13,
            recoveryScore = 55,
            consistencyDays7 = 5,
        )

        assertEquals(3, projections.size)
        for (projection in projections) {
            val week0 = projection.points.first()
            assertEquals(0, week0.week)
            assertEquals(projection.currentValue, week0.low, 0.0)
            assertEquals(projection.currentValue, week0.high, 0.0)
        }
        assertEquals(58.37, projections.byMetric(MetricType.RESTING_HEART_RATE).currentValue, 0.0)
        assertEquals(47.13, projections.byMetric(MetricType.HRV_RMSSD).currentValue, 0.0)
        assertEquals(55.0, projections.byMetric(MetricType.RECOVERY_SCORE).currentValue, 0.0)
    }

    @Test
    fun pointsCoverWeeksZeroThroughEight_withLabelsAndDirections() {
        val projections = engine.project(60.0, 48.0, 55, 5)

        for (projection in projections) {
            assertEquals(listOf(0, 2, 4, 6, 8), projection.points.map { it.week })
        }
        val rhr = projections.byMetric(MetricType.RESTING_HEART_RATE)
        assertEquals("Resting heart rate", rhr.label)
        assertEquals("bpm", rhr.unit)
        assertFalse(rhr.higherIsBetter)
        val hrv = projections.byMetric(MetricType.HRV_RMSSD)
        assertEquals("ms", hrv.unit)
        assertTrue(hrv.higherIsBetter)
        val recovery = projections.byMetric(MetricType.RECOVERY_SCORE)
        assertEquals("%", recovery.unit)
        assertTrue(recovery.higherIsBetter)
    }

    @Test
    fun fullEffect_week8_matchesLiteratureRanges() {
        val projections = engine.project(60.0, 48.0, 55, 5)

        // RHR: 3.0-6.0 bpm reduction at week 8, half of that at week 4.
        val rhr = projections.byMetric(MetricType.RESTING_HEART_RATE)
        assertEquals(54.0, rhr.points.last().low, 1e-9)
        assertEquals(57.0, rhr.points.last().high, 1e-9)
        assertEquals(57.0, rhr.points[2].low, 1e-9)
        assertEquals(58.5, rhr.points[2].high, 1e-9)

        // HRV: +5% to +15% at week 8.
        val hrv = projections.byMetric(MetricType.HRV_RMSSD)
        assertEquals(50.4, hrv.points.last().low, 1e-9)
        assertEquals(55.2, hrv.points.last().high, 1e-9)
        assertEquals(49.2, hrv.points[2].low, 1e-9)
        assertEquals(51.6, hrv.points[2].high, 1e-9)

        // Recovery: +5 to +12 points at week 8.
        val recovery = projections.byMetric(MetricType.RECOVERY_SCORE)
        assertEquals(60.0, recovery.points.last().low, 1e-9)
        assertEquals(67.0, recovery.points.last().high, 1e-9)
        assertEquals(57.5, recovery.points[2].low, 1e-9)
        assertEquals(61.0, recovery.points[2].high, 1e-9)
    }

    @Test
    fun lowNeverExceedsHigh_acrossAllTiersAndWeeks() {
        for (days in 0..7) {
            val projections = engine.project(58.37, 33.3, 41, days)
            for (projection in projections) {
                for (point in projection.points) {
                    assertTrue(
                        "week ${point.week} days $days ${projection.metric}: " +
                            "${point.low} > ${point.high}",
                        point.low <= point.high + 1e-9,
                    )
                }
            }
        }
    }

    @Test
    fun rhr_neverProjectsBelowFloor_orAboveCurrent() {
        // Floor = max(45.0, 46*0.85) = 45.0, which full effect would otherwise breach (46-6=40).
        val nearFloor = engine.project(46.0, null, null, 7).byMetric(MetricType.RESTING_HEART_RATE)
        for (point in nearFloor.points) {
            assertTrue("week ${point.week} low ${point.low}", point.low >= 45.0 - 1e-9)
            assertTrue("week ${point.week} high ${point.high}", point.high >= 45.0 - 1e-9)
            assertTrue("week ${point.week} high ${point.high}", point.high <= 46.0 + 1e-9)
        }

        // Percentage floor: max(45.0, 100*0.85) = 85.0.
        val high = engine.project(100.0, null, null, 5).byMetric(MetricType.RESTING_HEART_RATE)
        for (point in high.points) {
            assertTrue(point.low >= 85.0 - 1e-9)
        }
    }

    @Test
    fun hrv_neverProjectsAboveCap() {
        for (days in 0..7) {
            val hrv = engine.project(null, 200.0, null, days).byMetric(MetricType.HRV_RMSSD)
            for (point in hrv.points) {
                assertTrue("week ${point.week} days $days", point.high <= 200.0 * 1.25 + 1e-9)
            }
        }
        // Full effect stays inside the cap: +15% at week 8.
        val full = engine.project(null, 200.0, null, 5).byMetric(MetricType.HRV_RMSSD)
        assertEquals(230.0, full.points.last().high, 1e-9)
    }

    @Test
    fun recovery_gainsAreCappedAtNinety() {
        val recovery = engine.project(null, null, 85, 5).byMetric(MetricType.RECOVERY_SCORE)
        for (point in recovery.points) {
            assertTrue("week ${point.week}", point.high <= 90.0 + 1e-9)
        }
        // Raw week-8 optimistic gain would be 97; it must clamp to 90.
        assertEquals(90.0, recovery.points.last().high, 1e-9)
        assertEquals(90.0, recovery.points.last().low, 1e-9) // 85 + 5 == cap exactly
        assertEquals(87.5, recovery.points[2].low, 1e-9)
        assertEquals(90.0, recovery.points[2].high, 1e-9)
    }

    @Test
    fun recovery_alreadyAboveCap_staysFlatAndNeverExceeds99() {
        val at95 = engine.project(null, null, 95, 5).byMetric(MetricType.RECOVERY_SCORE)
        for (point in at95.points) {
            assertEquals(95.0, point.low, 0.0)
            assertEquals(95.0, point.high, 0.0)
        }
        val at99 = engine.project(null, null, 99, 7).byMetric(MetricType.RECOVERY_SCORE)
        for (point in at99.points) {
            assertTrue(point.high <= 99.0 + 1e-9)
            assertEquals(99.0, point.low, 0.0)
        }
    }

    @Test
    fun zeroConsistency_projectsFlatRanges() {
        val projections = engine.project(60.0, 48.0, 55, 0)

        assertEquals(3, projections.size)
        for (projection in projections) {
            assertEquals(
                "Complete a few activities each week to see a projected range",
                projection.assumption,
            )
            for (point in projection.points) {
                assertEquals(projection.currentValue, point.low, 0.0)
                assertEquals(projection.currentValue, point.high, 0.0)
            }
        }
    }

    @Test
    fun negativeConsistency_isTreatedAsZero() {
        val projections = engine.project(60.0, null, null, -3)
        val rhr = projections.byMetric(MetricType.RESTING_HEART_RATE)
        for (point in rhr.points) {
            assertEquals(60.0, point.low, 0.0)
            assertEquals(60.0, point.high, 0.0)
        }
    }

    @Test
    fun higherConsistency_projectsStrictlyLargerWeek8Improvement() {
        fun week8MidImprovement(days: Int, metric: MetricType): Double {
            val projection = engine.project(60.0, 48.0, 55, days).byMetric(metric)
            val week8 = projection.points.last()
            val mid = (week8.low + week8.high) / 2.0
            return if (projection.higherIsBetter) mid - projection.currentValue
            else projection.currentValue - mid
        }

        for (metric in listOf(
            MetricType.RESTING_HEART_RATE,
            MetricType.HRV_RMSSD,
            MetricType.RECOVERY_SCORE,
        )) {
            val full = week8MidImprovement(days = 6, metric = metric)
            val moderate = week8MidImprovement(days = 3, metric = metric)
            val light = week8MidImprovement(days = 2, metric = metric)
            val none = week8MidImprovement(days = 0, metric = metric)
            assertTrue("$metric full=$full moderate=$moderate", full > moderate)
            assertTrue("$metric moderate=$moderate light=$light", moderate > light)
            assertTrue("$metric light=$light none=$none", light > none)
            assertEquals(0.0, none, 1e-9)
        }
    }

    @Test
    fun improvementNeverReversesAcrossWeeks() {
        for (days in 1..7) {
            val projections = engine.project(60.0, 48.0, 55, days)
            for (projection in projections) {
                projection.points.zipWithNext { earlier, later ->
                    if (projection.higherIsBetter) {
                        assertTrue("${projection.metric} days $days", later.low >= earlier.low - 1e-9)
                        assertTrue("${projection.metric} days $days", later.high >= earlier.high - 1e-9)
                    } else {
                        assertTrue("${projection.metric} days $days", later.low <= earlier.low + 1e-9)
                        assertTrue("${projection.metric} days $days", later.high <= earlier.high + 1e-9)
                    }
                }
            }
        }
    }

    @Test
    fun nullMetrics_areOmitted() {
        val hrvOnly = engine.project(null, 48.0, null, 5)
        assertEquals(1, hrvOnly.size)
        assertEquals(MetricType.HRV_RMSSD, hrvOnly.first().metric)

        val rhrOnly = engine.project(60.0, null, null, 3)
        assertEquals(1, rhrOnly.size)
        assertEquals(MetricType.RESTING_HEART_RATE, rhrOnly.first().metric)

        assertTrue(engine.project(null, null, null, 5).isEmpty())
    }

    @Test
    fun projectionsAreDeterministic() {
        val first = engine.project(58.37, 47.13, 63, 4)
        val second = engine.project(58.37, 47.13, 63, 4)
        assertEquals(first, second)

        val otherInstance = ProjectionEngine().project(58.37, 47.13, 63, 4)
        assertEquals(first, otherInstance)
    }

    @Test
    fun assumption_matchesConsistencyTierWording() {
        val full = engine.project(60.0, null, null, 6).first()
        assertEquals("If you complete your plan on 5+ days a week", full.assumption)

        val moderate = engine.project(60.0, null, null, 3).first()
        assertEquals("At your current consistency of 3 days a week", moderate.assumption)

        val light = engine.project(60.0, null, null, 2).first()
        assertEquals("At your current consistency of 2 days a week", light.assumption)
    }

    @Test
    fun allRenderedCopy_passesSafetyEngine() {
        assertTrue(safety.validateCopy(ProjectionEngine.DISCLAIMER))
        for (days in listOf(0, 1, 3, 5, 7)) {
            for (projection in engine.project(60.0, 48.0, 55, days)) {
                assertTrue(projection.assumption, safety.validateCopy(projection.assumption))
                assertTrue(projection.label, safety.validateCopy(projection.label))
                assertTrue(safety.validateCopy(projection.unit))
            }
        }
    }
}
