package com.spartan.domain

import com.spartan.domain.engine.MetricEngine
import com.spartan.domain.model.ClinicalStatus
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.TargetStatus
import com.spartan.domain.model.TargetValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricEngineTest {
    private val engine = MetricEngine()

    @Test
    fun bmi_calculatesOneDecimal() {
        assertEquals(25.9, engine.bmi(weightKg = 81.16, heightCm = 177.0), 0.0)
    }

    @Test
    fun tgHdlRatio_calculatesTwoDecimals() {
        assertEquals(3.27, engine.tgHdlRatio(triglycerides = 134.0, hdl = 41.0), 0.0)
    }

    @Test
    fun waistToHeightRatio_calculatesTwoDecimals() {
        assertEquals(0.51, engine.waistToHeightRatio(waistCm = 90.0, heightCm = 177.0), 0.0)
    }

    @Test
    fun fastingGlucoseAboveNormal_isAboveRangeWithoutDiagnosis() {
        val assessment = engine.assess(MetricReading(MetricType.FASTING_GLUCOSE, 108.0))

        assertEquals(ClinicalStatus.ABOVE_RANGE, assessment.clinicalStatus)
        assertEquals("This value is above the normal clinical range. One value is not a diagnosis.", assessment.clinicalMessage)
    }

    @Test
    fun vitaminDCanBeClinicallyAdequateButBelowPersonalTarget() {
        val assessment = engine.assess(
            MetricReading(MetricType.VITAMIN_D_25OH, 23.0),
            TargetValue(MetricType.VITAMIN_D_25OH, minValue = 30.0),
        )

        assertEquals(ClinicalStatus.NORMAL, assessment.clinicalStatus)
        assertEquals(TargetStatus.BELOW_PERSONAL_TARGET, assessment.targetStatus)
    }

    @Test
    fun pendingApoB_isAcceptedAsPending() {
        val assessment = engine.assess(MetricReading(MetricType.APOB, null))

        assertEquals(ClinicalStatus.PENDING, assessment.clinicalStatus)
        assertEquals(TargetStatus.PENDING, assessment.targetStatus)
    }

    @Test
    fun validate_rejectsOutOfRangeValues() {
        assertFalse(engine.validate(MetricType.FASTING_GLUCOSE, 39.0))
        assertFalse(engine.validate(MetricType.FASTING_GLUCOSE, 401.0))
        assertFalse(engine.validate(MetricType.RESTING_HEART_RATE, 29.0))
        assertFalse(engine.validate(MetricType.BMI, 81.0))
    }

    @Test
    fun validate_allowsZeroCacButRejectsZeroForMostMetrics() {
        assertTrue(engine.validate(MetricType.CAC, 0.0))
        assertFalse(engine.validate(MetricType.WEIGHT, 0.0))
    }

    @Test
    fun validate_allowsPendingOnlyForPendingCapableMetrics() {
        assertTrue(engine.validate(MetricType.APOB, null))
        assertTrue(engine.validate(MetricType.LPA, null))
        assertTrue(engine.validate(MetricType.CAC, null))
        assertFalse(engine.validate(MetricType.FASTING_GLUCOSE, null))
    }

    @Test
    fun targetComparison_handlesMinAndMaxTargets() {
        assertEquals(
            TargetStatus.BELOW_PERSONAL_TARGET,
            engine.compareTarget(
                MetricReading(MetricType.VITAMIN_D_25OH, 23.0),
                TargetValue(MetricType.VITAMIN_D_25OH, minValue = 30.0),
            ),
        )
        assertEquals(
            TargetStatus.ABOVE_PERSONAL_TARGET,
            engine.compareTarget(
                MetricReading(MetricType.RESTING_HEART_RATE, 68.0),
                TargetValue(MetricType.RESTING_HEART_RATE, maxValue = 60.0),
            ),
        )
        assertEquals(
            TargetStatus.MEETS_TARGET,
            engine.compareTarget(
                MetricReading(MetricType.TG_HDL_RATIO, 2.2),
                TargetValue(MetricType.TG_HDL_RATIO, maxValue = 2.5),
            ),
        )
    }
}
