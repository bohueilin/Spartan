package com.spartan.domain

import com.spartan.domain.engine.InsightEngine
import com.spartan.domain.engine.MetricEngine
import com.spartan.domain.engine.SafetyEngine
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Broad coverage for the medical-safety guardrail: it must reject over-claiming/unsafe copy (in
 * many surface forms) and accept legitimate wellness language, and every insight the app can
 * generate must pass it.
 */
class SafetyEngineCoverageTest {
    private val safety = SafetyEngine()

    @Test
    fun rejects_blockedMedicalClaims_inManyForms() {
        val blocked = listOf(
            "You have diabetes",
            "you have diabetic complications",
            "Your pancreas is overloaded",
            "your pancreas is overloading",
            "You need medication",
            "you need a statin",
            "You need statins now",
            "Take a vitamin D supplement dose",
            "Ignore your doctor",
            "ignore your physician's advice",
            "Just exercise through pain",
        )
        blocked.forEach { assertFalse("should block: '$it'", safety.validateCopy(it)) }
    }

    @Test
    fun accepts_legitimateWellnessLanguage() {
        val ok = listOf(
            "This value is above the normal clinical range.",
            "This pattern is worth tracking and discussing with a clinician.",
            "One value is not a diagnosis.",
            "Here are safe behavior actions to try this week.",
            "Take a 15-minute easy walk after meals when practical.",
            "If readings stay unusual, consider consulting a qualified clinician.",
            "Prioritize consistent sleep and hydration today.",
        )
        ok.forEach { assertTrue("should allow: '$it'", safety.validateCopy(it)) }
    }

    @Test
    fun sanitize_throwsOnlyForBlockedCopy() {
        // Does not throw for safe copy.
        safety.sanitize("This is worth discussing with a clinician.")
        var threw = false
        try {
            safety.sanitize("You need medication.")
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue("sanitize must reject blocked copy", threw)
    }

    @Test
    fun everyInsightCard_forEveryMetric_passesSafety() {
        val metricEngine = MetricEngine()
        val insightEngine = InsightEngine()
        // A representative in-range value per metric that supports clinical assessment.
        val samples = mapOf(
            MetricType.FASTING_GLUCOSE to 108.0,
            MetricType.TG_HDL_RATIO to 3.2,
            MetricType.VITAMIN_D_25OH to 23.0,
            MetricType.RESTING_HEART_RATE to 68.0,
            MetricType.BMI to 25.9,
            MetricType.SYSTOLIC_BP to 130.0,
            MetricType.DIASTOLIC_BP to 85.0,
        )
        val assessments = samples.map { (type, value) -> metricEngine.assess(MetricReading(type, value)) }
        val cards = insightEngine.generate(assessments)
        assertTrue("insights generated", cards.isNotEmpty())
        cards.forEach { card ->
            assertTrue(safety.validateCopy(card.title))
            assertTrue(safety.validateCopy(card.explanation))
            assertTrue(safety.validateCopy(card.whyItMatters))
            card.safeActions.forEach { assertTrue(safety.validateCopy(it)) }
            card.clinicianTriggers.forEach { assertTrue(safety.validateCopy(it)) }
        }
    }
}
