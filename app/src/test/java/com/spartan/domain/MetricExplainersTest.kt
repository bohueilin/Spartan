package com.spartan.domain

import com.spartan.domain.engine.MetricExplainer
import com.spartan.domain.engine.MetricExplainers
import com.spartan.domain.engine.SafetyEngine
import com.spartan.domain.model.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricExplainersTest {

    private val safetyEngine = SafetyEngine()

    private val coveredMetrics = setOf(
        MetricType.RECOVERY_SCORE,
        MetricType.HRV_RMSSD,
        MetricType.RESTING_HEART_RATE,
        MetricType.SLEEP_PERFORMANCE,
        MetricType.SLEEP_DURATION,
        MetricType.SLEEP_DEBT,
        MetricType.RESPIRATORY_RATE,
        MetricType.DAY_STRAIN,
        MetricType.ENERGY_KCAL,
    )

    private fun allStrings(explainer: MetricExplainer): List<String> = buildList {
        add(explainer.title)
        add(explainer.whatItIs)
        addAll(explainer.whatMovesIt)
        add(explainer.whatGoodLooksLike)
        add(explainer.howSpartanUsesIt)
    }

    @Test
    fun all_coversExactlyTheNineWhoopMetrics() {
        assertEquals(9, MetricExplainers.all.size)
        assertEquals(coveredMetrics, MetricExplainers.all.map { it.metric }.toSet())
    }

    @Test
    fun forMetric_returnsMatchingExplainerForEveryCoveredMetric() {
        coveredMetrics.forEach { type ->
            val explainer = MetricExplainers.forMetric(type)
            assertNotNull("expected an explainer for $type", explainer)
            assertEquals(type, explainer!!.metric)
        }
    }

    @Test
    fun forMetric_returnsNullForNonWhoopTypes() {
        val nonWhoop = MetricType.entries.filterNot { it in coveredMetrics }
        assertTrue(nonWhoop.contains(MetricType.FASTING_GLUCOSE))
        nonWhoop.forEach { type ->
            assertNull("expected no explainer for non-WHOOP type $type", MetricExplainers.forMetric(type))
        }
    }

    @Test
    fun everyStringField_passesSafetyEngineValidation() {
        MetricExplainers.all.forEach { explainer ->
            allStrings(explainer).forEach { copy ->
                assertTrue(
                    "SafetyEngine rejected copy for ${explainer.metric}: \"$copy\"",
                    safetyEngine.validateCopy(copy),
                )
            }
        }
    }

    @Test
    fun noFieldIsEmptyOrBlank() {
        MetricExplainers.all.forEach { explainer ->
            allStrings(explainer).forEach { copy ->
                assertTrue("blank field on ${explainer.metric}", copy.isNotBlank())
            }
        }
    }

    @Test
    fun whatMovesIt_hasThreeToFiveBullets() {
        MetricExplainers.all.forEach { explainer ->
            assertTrue(
                "${explainer.metric} has ${explainer.whatMovesIt.size} bullets, expected 3..5",
                explainer.whatMovesIt.size in 3..5,
            )
        }
    }

    @Test
    fun respiratoryRateExplainer_framesStabilityAndClinicianNonDiagnostically() {
        val explainer = MetricExplainers.forMetric(MetricType.RESPIRATORY_RATE)
        assertNotNull(explainer)
        val fullText = allStrings(explainer!!).joinToString(" ")
        assertTrue(
            "respiratory explainer should say stability matters more than the number",
            fullText.contains("stability matters more than the number", ignoreCase = true),
        )
        assertTrue(
            "respiratory explainer should mention a clinician for sustained unusual changes",
            fullText.contains("clinician", ignoreCase = true),
        )
        assertTrue(
            "respiratory explainer must stay non-diagnostic",
            fullText.contains("does not diagnose", ignoreCase = true),
        )
    }
}
