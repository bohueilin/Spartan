package com.vitalcompass.domain

import com.vitalcompass.domain.engine.MetricEngine
import com.vitalcompass.domain.model.ClinicalStatus
import com.vitalcompass.domain.model.MetricReading
import com.vitalcompass.domain.model.MetricType
import com.vitalcompass.domain.model.TargetStatus
import com.vitalcompass.domain.model.TargetValue
import org.junit.Assert.assertEquals
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
}
