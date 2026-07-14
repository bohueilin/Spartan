package com.spartan.domain

import com.spartan.domain.engine.MetricEngine
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validation coverage for the WHOOP-sourced MetricTypes added in Spartan. Guards the exhaustive
 * `when` in MetricEngine.validate so ranges stay sane (used by manual metric entry; WHOOP sync
 * writes normalized values directly).
 */
class MetricEngineWhoopTest {
    private val engine = MetricEngine()

    @Test
    fun recoveryScore_range() {
        assertTrue(engine.validate(MetricType.RECOVERY_SCORE, 50.0))
        assertTrue(engine.validate(MetricType.RECOVERY_SCORE, 100.0))
        assertFalse(engine.validate(MetricType.RECOVERY_SCORE, 100.1))
    }

    @Test
    fun hrv_range() {
        assertTrue(engine.validate(MetricType.HRV_RMSSD, 65.0))
        assertFalse(engine.validate(MetricType.HRV_RMSSD, 4.0))    // below plausible floor
        assertFalse(engine.validate(MetricType.HRV_RMSSD, 301.0))
    }

    @Test
    fun sleepPerformance_range() {
        assertTrue(engine.validate(MetricType.SLEEP_PERFORMANCE, 88.0))
        assertTrue(engine.validate(MetricType.SLEEP_PERFORMANCE, 100.0))
        assertFalse(engine.validate(MetricType.SLEEP_PERFORMANCE, 120.0))
    }

    @Test
    fun sleepDebt_range() {
        assertTrue(engine.validate(MetricType.SLEEP_DEBT, 1.5))
        assertFalse(engine.validate(MetricType.SLEEP_DEBT, 25.0))
    }

    @Test
    fun zeroReadings_fromRealWhoopExports_areValidWhereTheRangeAllowsZero() {
        // Regression: a real export contains "Sleep debt (min) = 0" (fully paid off). The old
        // blanket value<=0 rejection made assess() throw and crash-loop the UI after import.
        assertTrue(engine.validate(MetricType.SLEEP_DEBT, 0.0))
        assertTrue(engine.validate(MetricType.DAY_STRAIN, 0.0))
        engine.assess(MetricReading(type = MetricType.SLEEP_DEBT, value = 0.0)) // must not throw
        // Zero stays invalid where the range floor excludes it; negatives never pass.
        assertFalse(engine.validate(MetricType.WEIGHT, 0.0))
        assertFalse(engine.validate(MetricType.RESTING_HEART_RATE, 0.0))
        assertFalse(engine.validate(MetricType.SLEEP_DEBT, -0.1))
    }

    @Test
    fun respiratoryRate_range() {
        assertTrue(engine.validate(MetricType.RESPIRATORY_RATE, 15.0))
        assertFalse(engine.validate(MetricType.RESPIRATORY_RATE, 4.0))
        assertFalse(engine.validate(MetricType.RESPIRATORY_RATE, 41.0))
    }

    @Test
    fun dayStrain_range() {
        assertTrue(engine.validate(MetricType.DAY_STRAIN, 12.5))
        assertTrue(engine.validate(MetricType.DAY_STRAIN, 21.0))
        assertFalse(engine.validate(MetricType.DAY_STRAIN, 21.5))
    }

    @Test
    fun energyKcal_range() {
        assertTrue(engine.validate(MetricType.ENERGY_KCAL, 2500.0))
        assertFalse(engine.validate(MetricType.ENERGY_KCAL, 10_001.0))
    }

    @Test
    fun everyMetricType_isHandledByValidate() {
        // Exercising all types guarantees the exhaustive `when` compiles and never throws.
        MetricType.entries.forEach { type ->
            engine.validate(type, 10.0)
            engine.validate(type, null)
        }
    }
}
