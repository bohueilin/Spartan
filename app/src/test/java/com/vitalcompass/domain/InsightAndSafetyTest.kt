package com.vitalcompass.domain

import com.vitalcompass.domain.engine.InsightEngine
import com.vitalcompass.domain.engine.MetricEngine
import com.vitalcompass.domain.engine.SafetyEngine
import com.vitalcompass.domain.model.MetricReading
import com.vitalcompass.domain.model.MetricType
import com.vitalcompass.domain.model.TargetValue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightAndSafetyTest {
    private val metricEngine = MetricEngine()

    @Test
    fun safetyEngine_blocksMedicalOverclaiming() {
        val safety = SafetyEngine()

        assertFalse(safety.validateCopy("You need " + "medication for this."))
        assertFalse(safety.validateCopy("Ignore your " + "doctor and keep going."))
        assertTrue(safety.validateCopy("This pattern is worth tracking and discussing with a clinician."))
    }

    @Test
    fun insightGeneration_usesSafeNonDiagnosticLanguage() {
        val assessments = listOf(
            metricEngine.assess(MetricReading(MetricType.FASTING_GLUCOSE, 108.0)),
            metricEngine.assess(
                MetricReading(MetricType.VITAMIN_D_25OH, 23.0),
                TargetValue(MetricType.VITAMIN_D_25OH, minValue = 30.0),
            ),
        )

        val insights = InsightEngine().generate(assessments)
        val copy = insights.joinToString(" ") { it.explanation + " " + it.safeActions.joinToString(" ") }

        assertTrue(copy.contains("One value is not a diagnosis."))
        assertFalse(copy.contains("deficient", ignoreCase = true))
    }
}
