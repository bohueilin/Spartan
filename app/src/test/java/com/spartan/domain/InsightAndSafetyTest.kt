package com.spartan.domain

import com.spartan.domain.engine.InsightEngine
import com.spartan.domain.engine.MetricEngine
import com.spartan.domain.engine.SafetyEngine
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.TargetValue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightAndSafetyTest {
    private val metricEngine = MetricEngine()

    @Test
    fun safetyEngine_blocksMedicalOverclaiming() {
        val safety = SafetyEngine()

        assertFalse(safety.validateCopy("You have diabetes."))
        assertFalse(safety.validateCopy("Your pancreas is overloaded."))
        assertFalse(safety.validateCopy("You need " + "medication for this."))
        assertFalse(safety.validateCopy("You need a statin for this."))
        assertFalse(safety.validateCopy("You need statins."))
        assertFalse(safety.validateCopy("Take this supplement dose."))
        assertFalse(safety.validateCopy("Ignore your " + "doctor and keep going."))
        assertFalse(safety.validateCopy("Exercise, through pain."))
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

    @Test
    fun insightGeneration_doesNotCallAbnormalBpNormal() {
        val assessment = metricEngine.assess(MetricReading(MetricType.SYSTOLIC_BP, 130.0))

        val insight = InsightEngine().generate(listOf(assessment)).single()

        assertTrue(insight.explanation.contains("above the normal clinical range", ignoreCase = true))
        assertFalse(insight.explanation.contains("in the normal range", ignoreCase = true))
    }
}
