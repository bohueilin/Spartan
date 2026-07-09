package com.spartan.domain

import com.spartan.domain.engine.CoachingEngine
import com.spartan.domain.engine.CoachingOptions
import com.spartan.domain.engine.RuleIds
import com.spartan.domain.engine.SafetyEngine
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.ReadinessBand
import com.spartan.domain.model.ReadinessSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Evaluation harness for the rules-based coaching engine. Rather than a handful of examples, this
 * sweeps a large matrix of readiness inputs and asserts a set of INVARIANTS that must hold for every
 * generated plan — the same way an LLM/agent eval asserts properties over many samples. It exists to
 * catch coaching-quality regressions (unsafe intensity, missing safety copy, unbounded plans,
 * non-determinism) before they ship.
 */
class CoachingEvalTest {
    private val engine = CoachingEngine()
    private val safety = SafetyEngine()
    private val day = 20_000L

    private fun snap(
        recovery: Int? = 60,
        hrv: Double? = 60.0,
        hrvBase: Double? = null,
        rhr: Double? = 55.0,
        rhrBase: Double? = null,
        sleepPerf: Int? = 85,
        sleepDebt: Double? = 0.0,
        strainPrior: Double? = 8.0,
        resp: Double? = 15.0,
        respBase: Double? = null,
        mock: Boolean = false,
    ): ReadinessSnapshot = ReadinessSnapshot(
        dateEpochDay = day,
        recoveryScore = recovery,
        hrvMs = hrv,
        hrvVsBaseline = if (hrv != null && hrvBase != null) hrv - hrvBase else null,
        restingHeartRate = rhr,
        rhrVsBaseline = if (rhr != null && rhrBase != null) rhr - rhrBase else null,
        sleepPerformance = sleepPerf,
        sleepDebtHours = sleepDebt,
        dayStrainPrior = strainPrior,
        respiratoryRate = resp,
        respiratoryRateBaseline = respBase,
        band = ReadinessBand.fromRecovery(recovery),
        isStale = recovery == null,
        isMock = mock,
    )

    /** The universal invariants every plan must satisfy. */
    private fun assertInvariants(r: ReadinessSnapshot, options: CoachingOptions = CoachingOptions()) {
        val plan = engine.buildPlan(r, options)
        val ctx = "recovery=${r.recoveryScore} band=${r.band} sleep=${r.sleepPerformance} strain=${r.dayStrainPrior} resp=${r.respiratoryRate}"

        assertTrue("[$ctx] plan is never empty", plan.activities.isNotEmpty())

        val requiredCount = plan.activities.count { it.priority == ActivityPriority.REQUIRED }
        assertTrue(
            "[$ctx] plan bounded to maxActivities (or required set)",
            plan.activities.size <= maxOf(options.maxActivities, requiredCount),
        )
        assertTrue("[$ctx] total minutes positive", plan.totalEstimatedMinutes > 0)

        // Hard training is only ever green-lit on a primed day with no concerning vital.
        if (r.band != ReadinessBand.PRIMED) {
            assertTrue("[$ctx] no HARD intensity off a primed day", plan.activities.none { it.intensity == Intensity.HARD })
        }

        // Every card is well-formed and passes the safety sanitizer (no medical over-claiming).
        plan.activities.forEach { a ->
            assertTrue("[$ctx] non-blank title", a.title.isNotBlank())
            assertTrue("[$ctx] non-blank why", a.whyItMatters.isNotBlank())
            assertTrue("[$ctx] non-blank ruleId", a.ruleId.isNotBlank())
            assertTrue("[$ctx] sane duration", a.estimatedMinutes in 1..90)
            assertTrue("[$ctx] safe title: ${a.title}", safety.validateCopy(a.title))
            assertTrue("[$ctx] safe why", safety.validateCopy(a.whyItMatters))
            a.instructions.forEach { assertTrue("[$ctx] safe step", safety.validateCopy(it)) }
            a.safetyNote?.let { assertTrue("[$ctx] safe note", safety.validateCopy(it)) }
        }
        assertTrue("[$ctx] safe headline", safety.validateCopy(plan.headline))
        plan.safetyBanner?.let { assertTrue("[$ctx] safe banner", safety.validateCopy(it)) }

        // Ids are unique and generation is deterministic.
        assertEquals("[$ctx] unique activity ids", plan.activities.size, plan.activities.map { it.id }.toSet().size)
        val again = engine.buildPlan(r, options)
        assertEquals("[$ctx] deterministic", plan.activities.map { it.id }, again.activities.map { it.id })
    }

    @Test
    fun eval_invariantsHoldAcrossFullReadinessMatrix() {
        var evaluated = 0
        for (recovery in 0..100 step 5) {
            for (sleepPerf in listOf(35, 60, 80, 100)) {
                for (strain in listOf(3.0, 12.0, 18.0)) {
                    for (resp in listOf(14.0, 21.0, 27.0)) {
                        assertInvariants(snap(recovery = recovery, sleepPerf = sleepPerf, strainPrior = strain, resp = resp))
                        evaluated++
                    }
                }
            }
        }
        assertTrue("eval should cover a large matrix", evaluated >= 500)
    }

    @Test
    fun eval_invariantsHoldWithOptionAndTrendVariations() {
        val options = listOf(
            CoachingOptions(),
            CoachingOptions(maxActivities = 2),
            CoachingOptions(maxActivities = 6, painFlag = true),
            CoachingOptions(missedGoalYesterday = true),
            CoachingOptions(painFlag = true, missedGoalYesterday = true),
        )
        for (recovery in listOf(15, 40, 55, 72, 90, null)) {
            for (o in options) {
                assertInvariants(snap(recovery = recovery, hrv = 45.0, hrvBase = 70.0, rhr = 66.0, rhrBase = 55.0), o)
            }
        }
    }

    @Test
    fun eval_concerningVitalsNeverGreenlightHardTraining() {
        for (recovery in listOf(20, 55, 85)) {
            val highResp = engine.buildPlan(snap(recovery = recovery, resp = 28.0))
            assertTrue("high respiratory rate must not green-light HARD", highResp.activities.none { it.intensity == Intensity.HARD })
            assertTrue(highResp.activities.any { it.ruleId == RuleIds.CLINICIAN_REFERRAL && it.priority == ActivityPriority.REQUIRED })

            val highRhr = engine.buildPlan(snap(recovery = recovery, rhr = 105.0))
            assertTrue("very high resting HR must not green-light HARD", highRhr.activities.none { it.intensity == Intensity.HARD })
            assertTrue(highRhr.activities.any { it.ruleId == RuleIds.CLINICIAN_REFERRAL })
        }
    }

    @Test
    fun eval_lowRecoveryAlwaysCarriesARequiredRecoveryAction() {
        for (recovery in 0..33) {
            val plan = engine.buildPlan(snap(recovery = recovery))
            assertTrue(
                "recovery=$recovery must include a REQUIRED action",
                plan.activities.any { it.priority == ActivityPriority.REQUIRED },
            )
        }
    }

    @Test
    fun eval_maxActivitiesRespectedWhenNoRequiredOverflow() {
        // A clean balanced day has no forced REQUIRED items, so the cap must hold exactly.
        val plan = engine.buildPlan(snap(recovery = 60, resp = 15.0), CoachingOptions(maxActivities = 2))
        assertTrue(plan.activities.size <= 2)
    }
}
