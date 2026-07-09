package com.spartan.domain

import com.spartan.domain.engine.CoachingEngine
import com.spartan.domain.engine.CoachingOptions
import com.spartan.domain.engine.RuleIds
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.ReadinessBand
import com.spartan.domain.model.ReadinessSnapshot
import com.spartan.domain.model.WhoopSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachingEngineTest {
    private val engine = CoachingEngine()
    private val day = 20_000L

    private fun readiness(
        recovery: Int? = 60, hrv: Double? = 60.0, rhr: Double? = 55.0,
        sleepPerf: Int? = 90, sleepDebt: Double? = 0.0, strainPrior: Double? = 8.0,
        resp: Double? = 15.0, hrvBase: Double? = null, rhrBase: Double? = null, respBase: Double? = null,
    ): ReadinessSnapshot = ReadinessSnapshot(
        dateEpochDay = day, recoveryScore = recovery, hrvMs = hrv,
        hrvVsBaseline = if (hrv != null && hrvBase != null) hrv - hrvBase else null,
        restingHeartRate = rhr, rhrVsBaseline = if (rhr != null && rhrBase != null) rhr - rhrBase else null,
        sleepPerformance = sleepPerf, sleepDebtHours = sleepDebt, dayStrainPrior = strainPrior,
        respiratoryRate = resp, respiratoryRateBaseline = respBase,
        band = ReadinessBand.fromRecovery(recovery), isStale = recovery == null,
    )

    @Test
    fun band_thresholds_matchSpec() {
        assertEquals(ReadinessBand.PRIMED, ReadinessBand.fromRecovery(67))
        assertEquals(ReadinessBand.BALANCED, ReadinessBand.fromRecovery(50))
        assertEquals(ReadinessBand.EASY, ReadinessBand.fromRecovery(34))
        assertEquals(ReadinessBand.REST, ReadinessBand.fromRecovery(33))
        assertEquals(ReadinessBand.BALANCED, ReadinessBand.fromRecovery(null))
    }

    @Test
    fun lowRecovery_producesRequiredRecovery_andNoHardTraining() {
        val plan = engine.buildPlan(readiness(recovery = 25))
        assertEquals(ReadinessBand.REST, plan.band)
        assertTrue(plan.activities.any { it.priority == ActivityPriority.REQUIRED && it.ruleId == RuleIds.LOW_RECOVERY })
        assertFalse("no HARD session on a low-recovery day", plan.activities.any { it.intensity == Intensity.HARD })
    }

    @Test
    fun primed_greenlightsAQualityStrengthSession() {
        val plan = engine.buildPlan(readiness(recovery = 80))
        assertEquals(ReadinessBand.PRIMED, plan.band)
        assertTrue(plan.activities.any { it.ruleId == RuleIds.GOOD_RECOVERY_GREENLIGHT && it.intensity == Intensity.HARD })
    }

    @Test
    fun poorSleep_addsSleepHygiene() {
        val plan = engine.buildPlan(readiness(recovery = 55, sleepPerf = 55))
        assertTrue(plan.activities.any { it.ruleId == RuleIds.POOR_SLEEP })
    }

    @Test
    fun elevatedRhrTrend_addsCheckIn() {
        val plan = engine.buildPlan(readiness(recovery = 55, rhr = 66.0, rhrBase = 58.0))
        assertTrue(plan.activities.any { it.ruleId == RuleIds.ELEVATED_RHR_TREND })
    }

    @Test
    fun concerningRespiratoryRate_addsNonDiagnosticClinicianReferral() {
        val plan = engine.buildPlan(readiness(recovery = 55, resp = 27.0))
        val referral = plan.activities.firstOrNull { it.ruleId == RuleIds.CLINICIAN_REFERRAL }
        assertNotNull(referral)
        assertEquals(ActivityPriority.REQUIRED, referral!!.priority)
        assertTrue(referral.instructions.joinToString(" ").contains("clinician", ignoreCase = true))
    }

    @Test
    fun plan_respectsMaxActivities_butKeepsRequiredAndReferral() {
        val plan = engine.buildPlan(
            readiness(recovery = 20, resp = 28.0, sleepPerf = 40, rhr = 70.0, rhrBase = 58.0),
            CoachingOptions(maxActivities = 3, painFlag = true),
        )
        assertTrue(plan.activities.size <= maxOf(3, plan.activities.count { it.priority == ActivityPriority.REQUIRED }))
        assertTrue(plan.activities.any { it.ruleId == RuleIds.CLINICIAN_REFERRAL })
        assertTrue(plan.activities.any { it.ruleId == RuleIds.PAIN_DELOAD })
    }

    @Test
    fun staleData_producesSafeFallbackPlan() {
        val plan = engine.buildPlan(readiness(recovery = null))
        assertTrue(plan.isMock || plan.activities.isNotEmpty())
        assertTrue(plan.activities.any { it.ruleId == RuleIds.STALE_DATA_FALLBACK })
        assertTrue(plan.totalEstimatedMinutes > 0)
    }

    @Test
    fun readinessSnapshot_from_computesTrendsAndBand() {
        val history = listOf(
            WhoopSnapshot(dateEpochDay = day - 2, hrvMs = 70.0, restingHeartRate = 52.0, respiratoryRate = 14.0),
            WhoopSnapshot(dateEpochDay = day - 1, hrvMs = 68.0, restingHeartRate = 53.0, respiratoryRate = 14.0, dayStrain = 16.0),
        )
        val today = WhoopSnapshot(dateEpochDay = day, recoveryScore = 30, hrvMs = 55.0, restingHeartRate = 61.0, respiratoryRate = 15.0)
        val snap = ReadinessSnapshot.from(today, history)
        assertEquals(ReadinessBand.REST, snap.band)
        assertEquals(16.0, snap.dayStrainPrior!!, 0.001)
        assertTrue("RHR delta should be elevated", (snap.rhrVsBaseline ?: 0.0) >= 5.0)
        assertTrue("HRV should read as below baseline", snap.trendNotes.any { it.contains("HRV") })
    }
}
