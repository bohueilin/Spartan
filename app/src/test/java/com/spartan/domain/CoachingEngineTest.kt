package com.spartan.domain

import com.spartan.domain.engine.AvoidOption
import com.spartan.domain.engine.BedtimePlan
import com.spartan.domain.engine.CoachingEngine
import com.spartan.domain.engine.CoachingOptions
import com.spartan.domain.engine.GoalType
import com.spartan.domain.engine.PlanConstraints
import com.spartan.domain.engine.RecommendationSource
import com.spartan.domain.engine.RuleIds
import com.spartan.domain.engine.SafetyEngine
import com.spartan.domain.engine.SleepPlanner
import com.spartan.domain.engine.StrainZone
import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.DailyPlan
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.ReadinessBand
import com.spartan.domain.model.ReadinessSnapshot
import com.spartan.domain.model.TimeOfDay
import com.spartan.domain.model.WhoopSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class CoachingEngineTest {
    private val engine = CoachingEngine()
    private val day = 20_000L

    private fun readiness(
        recovery: Int? = 60, hrv: Double? = 60.0, rhr: Double? = 55.0,
        sleepPerf: Int? = 90, sleepDebt: Double? = 0.0, strainPrior: Double? = 8.0,
        resp: Double? = 15.0, hrvBase: Double? = null, rhrBase: Double? = null, respBase: Double? = null,
        // Nights behind each baseline given; past the learning period unless a test says otherwise.
        baselineDays: Int = 7,
    ): ReadinessSnapshot = ReadinessSnapshot(
        dateEpochDay = day, recoveryScore = recovery, hrvMs = hrv,
        hrvVsBaseline = if (hrv != null && hrvBase != null) hrv - hrvBase else null,
        restingHeartRate = rhr, rhrVsBaseline = if (rhr != null && rhrBase != null) rhr - rhrBase else null,
        sleepPerformance = sleepPerf, sleepDebtHours = sleepDebt, dayStrainPrior = strainPrior,
        respiratoryRate = resp, respiratoryRateBaseline = respBase,
        band = ReadinessBand.fromRecovery(recovery), isStale = recovery == null,
        rrBaselineDays = if (respBase != null) baselineDays else 0,
        rhrBaselineDays = if (rhrBase != null) baselineDays else 0,
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
    fun safetyBanner_onlyCautionsALowRecoveryDay_neverRepeatsTheDisclaimer() {
        val rest = engine.buildPlan(readiness(recovery = 25)).safetyBanner
        assertNotNull(rest)
        assertTrue(rest!!.contains("low-recovery day"))
        // Today carries the general "not medical advice" line once, as its own in-context line.
        for (recovery in listOf(40, 60, 80)) {
            assertNull("recovery $recovery", engine.buildPlan(readiness(recovery = recovery)).safetyBanner)
        }
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
        val plan = engine.buildPlan(readiness(recovery = 55, resp = 19.0, respBase = 15.0))
        val referral = plan.activities.firstOrNull { it.ruleId == RuleIds.CLINICIAN_REFERRAL }
        assertNotNull(referral)
        assertEquals(ActivityPriority.REQUIRED, referral!!.priority)
        assertTrue(referral.instructions.joinToString(" ").contains("clinician", ignoreCase = true))
        // A wellness check-in, never an illness signal: how you feel, in plain words.
        assertEquals("Notice how you feel today — energy, sleep, anything unusual.", referral.instructions.first())
        val said = (listOf(referral.title, referral.whyItMatters) + referral.instructions).joinToString(" ")
        listOf("illness", "sick").forEach { assertFalse("\"$it\" in: $said", said.contains(it, ignoreCase = true)) }
    }

    @Test
    fun clinicianReferral_firesOnlyOnDeviationFromTheUsersOwnBaseline() {
        fun referral(r: ReadinessSnapshot) = engine.buildPlan(r).byId("clinician")
        // Respiratory rate: +3 breaths a minute over the user's usual is the line; just under it is not.
        val respUp = referral(readiness(recovery = 60, resp = 18.0, respBase = 15.0))
        assertEquals(MetricType.RESPIRATORY_RATE, respUp!!.relatedMetric)
        assertEquals(ActivityPriority.REQUIRED, respUp.priority)
        assertNull(referral(readiness(recovery = 60, resp = 17.9, respBase = 15.0)))
        // Resting heart rate: +10 bpm over the user's usual is the line; just under it is not.
        val rhrUp = referral(readiness(recovery = 60, rhr = 66.0, rhrBase = 56.0))
        assertEquals(MetricType.RESTING_HEART_RATE, rhrUp!!.relatedMetric)
        assertEquals(ActivityPriority.REQUIRED, rhrUp.priority)
        assertNull(referral(readiness(recovery = 60, rhr = 65.9, rhrBase = 56.0)))
        // Both up: one card, tagged with respiratory rate.
        val both = engine.buildPlan(readiness(recovery = 60, resp = 19.0, respBase = 15.0, rhr = 70.0, rhrBase = 56.0))
        assertEquals(1, both.activities.count { it.ruleId == RuleIds.CLINICIAN_REFERRAL })
        assertEquals(MetricType.RESPIRATORY_RATE, both.byId("clinician")!!.relatedMetric)
    }

    @Test
    fun clinicianReferral_neverFiresOnAnAbsoluteValueAlone() {
        for (recovery in listOf(20, 60, 85, null)) {
            // No baseline yet: no reading, however high, is "outside your usual range".
            val noBaseline = engine.buildPlan(readiness(recovery = recovery, resp = 30.0, rhr = 120.0))
            assertNull("recovery=$recovery", noBaseline.byId("clinician"))
            // A high reading that IS the user's usual is not a deviation either.
            val usual = engine.buildPlan(readiness(recovery = recovery, resp = 25.5, respBase = 25.0, rhr = 101.0, rhrBase = 100.0))
            assertNull("recovery=$recovery", usual.byId("clinician"))
            assertFalse("recovery=$recovery", usual.activities.any { it.ruleId == RuleIds.CONCERNING_VITAL_EASY })
        }
        // A respiratory baseline with no reading today, or a reading with no resting-HR delta: nothing to compare.
        assertNull(engine.buildPlan(readiness(recovery = 60, resp = null, respBase = 15.0)).byId("clinician"))
        assertNull(engine.buildPlan(readiness(recovery = 60, rhr = null, rhrBase = 56.0)).byId("clinician"))
    }

    /** [nights] prior nights at respiratory rate 14.5 and resting HR 55, then [today]: the snapshot a sync builds. */
    private fun afterNights(nights: Int, today: WhoopSnapshot): ReadinessSnapshot = ReadinessSnapshot.from(
        today,
        (1..nights).map { WhoopSnapshot(dateEpochDay = day - it, recoveryScore = 70, restingHeartRate = 55.0, respiratoryRate = 14.5) },
    )

    @Test
    fun clinicianReferral_waitsForTheLearningPeriod_beforeABaselineIsTheUsersUsual() {
        val rrUp = WhoopSnapshot(dateEpochDay = day, recoveryScore = 80, restingHeartRate = 55.0, respiratoryRate = 17.6)
        val rhrUp = WhoopSnapshot(dateEpochDay = day, recoveryScore = 80, restingHeartRate = 66.0, respiratoryRate = 14.5)
        for (today in listOf(rrUp, rhrUp)) {
            // One night, or four: Spartan is still learning this user's normal, so nothing is "outside" it yet.
            for (nights in listOf(1, 4)) {
                val ctx = "nights=$nights rr=${today.respiratoryRate} rhr=${today.restingHeartRate}"
                val plan = engine.buildPlan(afterNights(nights, today))
                assertNull(ctx, plan.byId("clinician"))
                assertFalse(ctx, plan.activities.any { it.ruleId == RuleIds.CONCERNING_VITAL_EASY })
                assertEquals(ctx, "You're primed. Make today count.", plan.headline)
            }
            // Five nights is the learning period: the same reading now is a check-in day.
            val learned = afterNights(5, today)
            assertEquals(5, learned.rrBaselineDays)
            assertEquals(5, learned.rhrBaselineDays)
            val plan = engine.buildPlan(learned)
            val ctx = "rr=${today.respiratoryRate} rhr=${today.restingHeartRate}"
            assertEquals(ctx, ActivityPriority.REQUIRED, plan.byId("clinician")!!.priority)
            assertEquals(ctx, RuleIds.CONCERNING_VITAL_EASY, plan.byId("zone2-walk")!!.ruleId)
            assertEquals(ctx, "Take it gently today and notice how you feel.", plan.headline)
            assertEquals(ctx, StrainZone.LIGHT, plan.strainTarget)
        }
    }

    @Test
    fun clinicianReferral_needsTheLearningPeriodBehindEachBaselineItReads() {
        fun referral(r: ReadinessSnapshot) = engine.buildPlan(r).byId("clinician")
        // Exactly the learning period counts; one night short does not.
        assertNotNull(referral(readiness(recovery = 60, resp = 18.0, respBase = 15.0, baselineDays = 5)))
        assertNull(referral(readiness(recovery = 60, resp = 18.0, respBase = 15.0, baselineDays = 4)))
        assertNotNull(referral(readiness(recovery = 60, rhr = 66.0, rhrBase = 56.0, baselineDays = 5)))
        assertNull(referral(readiness(recovery = 60, rhr = 66.0, rhrBase = 56.0, baselineDays = 4)))
        // Each baseline stands on its own nights: a learned one never vouches for a newer one.
        val rhrNew = readiness(recovery = 60, resp = 15.0, respBase = 15.0, rhr = 70.0, rhrBase = 56.0).copy(rhrBaselineDays = 4)
        assertNull(referral(rhrNew))
        assertEquals(MetricType.RESPIRATORY_RATE, referral(rhrNew.copy(respiratoryRate = 19.0))!!.relatedMetric)
        val rrNew = readiness(recovery = 60, resp = 19.0, respBase = 15.0, rhr = 70.0, rhrBase = 56.0).copy(rrBaselineDays = 1)
        assertEquals(MetricType.RESTING_HEART_RATE, referral(rrNew)!!.relatedMetric)
    }

    @Test
    fun readinessSnapshot_from_countsThePriorNightsBehindEachBaseline() {
        val history = listOf(
            WhoopSnapshot(dateEpochDay = day - 3, restingHeartRate = 54.0, respiratoryRate = 14.0),
            WhoopSnapshot(dateEpochDay = day - 2, restingHeartRate = 55.0), // no respiratory rate that night
            WhoopSnapshot(dateEpochDay = day - 1, restingHeartRate = 56.0, respiratoryRate = 15.0),
            WhoopSnapshot(dateEpochDay = day, restingHeartRate = 90.0, respiratoryRate = 30.0), // today is never its own baseline
        )
        val snap = ReadinessSnapshot.from(
            WhoopSnapshot(dateEpochDay = day, recoveryScore = 70, restingHeartRate = 57.0, respiratoryRate = 15.0), history,
        )
        assertEquals(2, snap.rrBaselineDays)
        assertEquals(3, snap.rhrBaselineDays)
        assertEquals(14.5, snap.respiratoryRateBaseline!!, 1e-9)
        assertEquals(2.0, snap.rhrVsBaseline!!, 1e-9)
        val alone = ReadinessSnapshot.from(WhoopSnapshot(dateEpochDay = day, recoveryScore = 70, respiratoryRate = 15.0))
        assertEquals(0, alone.rrBaselineDays)
        assertEquals(0, alone.rhrBaselineDays)
        assertNull(alone.respiratoryRateBaseline)
    }

    @Test
    fun plan_respectsMaxActivities_butKeepsRequiredAndReferral() {
        val plan = engine.buildPlan(
            readiness(recovery = 20, resp = 28.0, respBase = 15.0, sleepPerf = 40, rhr = 70.0, rhrBase = 58.0),
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

    @Test
    fun readinessSnapshot_from_carriesPreviousRecoverySleepAndCycleDay() {
        val history = listOf(
            WhoopSnapshot(dateEpochDay = day - 2, recoveryScore = 40),
            WhoopSnapshot(dateEpochDay = day - 1, recoveryScore = 58),
        )
        val snap = ReadinessSnapshot.from(WhoopSnapshot(dateEpochDay = day, recoveryScore = 72, sleepDurationHours = 7.5), history)
        assertEquals(58, snap.previousRecoveryScore)
        assertEquals(7.5, snap.sleepDurationHours!!, 1e-9)
        assertEquals(day, snap.cycleEpochDay)
        assertEquals(day - 1, snap.previousCycleEpochDay)
        val alone = ReadinessSnapshot.from(WhoopSnapshot(dateEpochDay = day, recoveryScore = 72))
        assertNull(alone.previousRecoveryScore)
        assertNull(alone.previousCycleEpochDay)
    }

    @Test
    fun readinessSnapshot_from_comparesWithYesterdayOnlyWhenItWasTheDayBefore() {
        // No strap yesterday: the newest earlier cycle is two days back, so there is no "yesterday".
        val gap = ReadinessSnapshot.from(
            WhoopSnapshot(dateEpochDay = day, recoveryScore = 72),
            listOf(
                WhoopSnapshot(dateEpochDay = day - 3, recoveryScore = 40),
                WhoopSnapshot(dateEpochDay = day - 2, recoveryScore = 58, dayStrain = 13.0),
            ),
        )
        assertNull(gap.previousRecoveryScore)
        assertEquals(day - 2, gap.previousCycleEpochDay)
        assertEquals(13.0, gap.dayStrainPrior!!, 1e-9) // still the last recorded strain, named as such in copy
    }

    // --- Options: defaults ------------------------------------------------------------------

    private val safety = SafetyEngine()
    private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private fun clockAt(minuteOfDay: Int) = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(clock)

    /** Recovery 25 after a 15-strain day with poor sleep: a full low-recovery plan. */
    private fun busyLowDay() = readiness(recovery = 25, sleepPerf = 55, strainPrior = 15.0)
    private val bedtime = SleepPlanner.tonight(7 * 60, 0.0, 8.0 + 10.0 / 60)!! // lights out 22:50

    private fun DailyPlan.byId(slug: String) = activities.firstOrNull { it.id == "$day:$slug" }

    @Test
    fun defaultOptions_planIsUnchanged() {
        assertEquals(listOf("$day:strength", "$day:hydration"), engine.buildPlan(readiness(recovery = 80)).activities.map { it.id })
        assertEquals(
            listOf("$day:mobility", "$day:stretch", "$day:winddown", "$day:hydration"),
            engine.buildPlan(busyLowDay()).activities.map { it.id },
        )
        val explicit = CoachingOptions(bedtime = null, goalType = null, constraints = PlanConstraints())
        assertEquals(engine.buildPlan(busyLowDay()), engine.buildPlan(busyLowDay(), explicit))
        assertEquals(engine.buildPlan(readiness(recovery = 80)), engine.buildPlan(readiness(recovery = 80), explicit))
    }

    // --- Strain target ----------------------------------------------------------------------

    @Test
    fun strainTarget_followsTheSessionActuallyPlanned() {
        val primed = engine.buildPlan(readiness(recovery = 80))
        assertEquals(StrainZone.HIGH, primed.strainTarget)
        assertTrue(primed.activities.any { it.intensity == Intensity.HARD })

        val balanced = engine.buildPlan(readiness(recovery = 60))
        assertEquals(StrainZone.MODERATE, balanced.strainTarget)
        assertTrue(balanced.activities.any { it.ruleId == RuleIds.GOOD_RECOVERY_GREENLIGHT && it.intensity == Intensity.MODERATE })

        assertEquals(StrainZone.LIGHT, engine.buildPlan(readiness(recovery = 40)).strainTarget)
        assertEquals(StrainZone.LIGHT, engine.buildPlan(readiness(recovery = 20)).strainTarget)
        // A concerning vital on a primed day swaps the hard session for an easy walk — and the target follows.
        assertEquals(StrainZone.LIGHT, engine.buildPlan(readiness(recovery = 80, resp = 19.0, respBase = 15.0)).strainTarget)
    }

    // --- Concerning vitals ------------------------------------------------------------------

    @Test
    fun concerningVital_goodRecoveryDayGetsAnEasyWalk_andALightTarget() {
        for (recovery in listOf(80, 60)) {
            for (r in listOf(readiness(recovery = recovery, resp = 19.0, respBase = 15.0), readiness(recovery = recovery, rhr = 66.0, rhrBase = 55.0))) {
                val plan = engine.buildPlan(r)
                val ctx = "recovery=$recovery resp=${r.respiratoryRate} rhr=${r.restingHeartRate}"
                val walk = plan.byId("zone2-walk")
                assertNotNull(ctx, walk)
                assertEquals(ctx, RuleIds.CONCERNING_VITAL_EASY, walk!!.ruleId)
                assertEquals(ctx, Intensity.EASY, walk.intensity)
                assertEquals(ctx, 15, walk.estimatedMinutes)
                assertNull(ctx, plan.byId("zone2"))
                assertNull(ctx, plan.byId("strength"))
                assertFalse(ctx, plan.activities.any { it.intensity == Intensity.MODERATE || it.intensity == Intensity.HARD })
                assertEquals(ctx, StrainZone.LIGHT, plan.strainTarget)
                assertNotNull(ctx, plan.byId("clinician"))
                (listOf(walk.title, walk.whyItMatters) + walk.instructions + listOfNotNull(walk.safetyNote))
                    .forEach { assertTrue(it, safety.validateCopy(it)) }
            }
        }
        // Won't-do strength changes nothing here: the check-in day is easy either way.
        val noStrength = engine.buildPlan(
            readiness(recovery = 80, resp = 19.0, respBase = 15.0), CoachingOptions(constraints = avoiding(AvoidOption.STRENGTH_TRAINING)),
        )
        assertEquals(RuleIds.CONCERNING_VITAL_EASY, noStrength.byId("zone2-walk")!!.ruleId)
        // A low-recovery day keeps its own easy block.
        assertEquals(RuleIds.LOW_RECOVERY, engine.buildPlan(readiness(recovery = 40, resp = 19.0, respBase = 15.0)).byId("zone2-walk")!!.ruleId)
    }

    @Test
    fun clinicianReferral_namesWhereTheReadingsComeFrom() {
        val fresh = engine.buildPlan(readiness(recovery = 55, resp = 19.0, respBase = 15.0)).byId("clinician")!!
        assertEquals(
            "One or more of today's readings is outside your usual range. This is not a diagnosis, and one reading is not a conclusion.",
            fresh.whyItMatters,
        )
        // Carried forward from an earlier day: never "today's readings".
        val stale = engine.buildPlan(readiness(recovery = 55, resp = 19.0, respBase = 15.0).copy(isStale = true)).byId("clinician")!!
        assertEquals(
            "One or more of your most recent readings is outside your usual range. This is not a diagnosis, and one reading is not a conclusion.",
            stale.whyItMatters,
        )
        assertEquals(ActivityPriority.REQUIRED, stale.priority)
        assertTrue(safety.validateCopy(stale.whyItMatters))
    }

    @Test
    fun headline_followsTheBand_andStaysGentleOnPainAndCheckInDays() {
        val gentle = "Take it gently today and notice how you feel."
        assertEquals(gentle, engine.buildPlan(readiness(recovery = 80), CoachingOptions(painFlag = true)).headline)
        assertEquals(gentle, engine.buildPlan(readiness(recovery = 80, resp = 19.0, respBase = 15.0)).headline)
        assertEquals(gentle, engine.buildPlan(readiness(recovery = 20, rhr = 66.0, rhrBase = 55.0)).headline)
        assertEquals("You're primed. Make today count.", engine.buildPlan(readiness(recovery = 80)).headline)
        assertEquals("Balanced day. Steady, quality work.", engine.buildPlan(readiness(recovery = 60)).headline)
        assertEquals("Go lighter today and protect recovery.", engine.buildPlan(readiness(recovery = 40)).headline)
        assertEquals("Recovery first. Today is for rebuilding.", engine.buildPlan(readiness(recovery = 20)).headline)
        // A stale day keeps its own line, pain or not.
        assertEquals(
            "Let's ease in while Spartan waits for today's data.",
            engine.buildPlan(readiness(recovery = null), CoachingOptions(painFlag = true)).headline,
        )
    }

    @Test
    fun strainTarget_isLightOnPainDays_andNoneOnStaleDays() {
        assertEquals(StrainZone.LIGHT, engine.buildPlan(readiness(recovery = 80), CoachingOptions(painFlag = true)).strainTarget)
        assertNull(engine.buildPlan(readiness(recovery = null)).strainTarget)
        // Carried forward with yesterday's recovery: still no target for today.
        assertNull(engine.buildPlan(readiness(recovery = 80).copy(isStale = true)).strainTarget)
        assertNull(engine.buildPlan(readiness(recovery = null), CoachingOptions(painFlag = true)).strainTarget)
    }

    @Test
    fun strainTarget_isNullWhenNoSessionIsPlanned() {
        val waterOnly = object : RecommendationSource {
            override fun recommend(readiness: ReadinessSnapshot, options: CoachingOptions): List<DailyActivity> = listOf(
                DailyActivity(
                    id = "${readiness.dateEpochDay}:hydration", title = "Hydration reminder",
                    category = ActivityCategory.HYDRATION, priority = ActivityPriority.OPTIONAL,
                    whyItMatters = "Steady hydration supports how you feel.", estimatedMinutes = 1,
                    intensity = Intensity.REST, bestTimeOfDay = TimeOfDay.ANYTIME, ruleId = RuleIds.HYDRATION_BASELINE,
                ),
            )
        }
        assertNull(CoachingEngine(source = waterOnly).buildPlan(readiness(recovery = 80)).strainTarget)
    }

    // --- Constraints: won't-do options -------------------------------------------------------

    private fun avoiding(vararg options: AvoidOption) = PlanConstraints(avoid = options.toSet())

    @Test
    fun avoidStrength_primedDayGetsTheSteadySession() {
        val plan = engine.buildPlan(readiness(recovery = 80), CoachingOptions(constraints = avoiding(AvoidOption.STRENGTH_TRAINING)))
        assertFalse(plan.activities.any { it.category == ActivityCategory.STRENGTH || it.intensity == Intensity.HARD })
        assertNotNull(plan.byId("zone2"))
        assertEquals(StrainZone.MODERATE, plan.strainTarget)
    }

    @Test
    fun avoidBreathwork_dropsBreathingCards() {
        val lowHrv = readiness(recovery = 60, hrv = 45.0, hrvBase = 70.0)
        assertTrue(engine.buildPlan(lowHrv).activities.any { it.category == ActivityCategory.BREATHWORK })
        val avoid = avoiding(AvoidOption.BREATHWORK)
        assertFalse(engine.buildPlan(lowHrv, CoachingOptions(constraints = avoid)).activities.any { it.category == ActivityCategory.BREATHWORK })
        // The calm-goal card is breathing too, so it is skipped as well.
        val calmGoal = engine.buildPlan(readiness(recovery = 80), CoachingOptions(goalType = GoalType.STRESS_RESILIENCE, constraints = avoid))
        assertFalse(calmGoal.activities.any { it.category == ActivityCategory.BREATHWORK })
    }

    @Test
    fun avoidMorningSessions_movesOptionalWorkoutsToMidday() {
        val avoid = avoiding(AvoidOption.MORNING_SESSIONS)
        val withGoal = CoachingOptions(goalType = GoalType.RECOVERY)
        assertEquals(TimeOfDay.MORNING, engine.buildPlan(readiness(recovery = 80), withGoal).byId("mobility")!!.bestTimeOfDay)
        assertEquals(TimeOfDay.MIDDAY, engine.buildPlan(readiness(recovery = 80), withGoal.copy(constraints = avoid)).byId("mobility")!!.bestTimeOfDay)

        // A required card keeps its slot, and a non-workout card is not a morning session.
        val required = engine.buildPlan(readiness(recovery = 25), CoachingOptions(constraints = avoid)).byId("mobility")!!
        assertEquals(ActivityPriority.REQUIRED, required.priority)
        assertEquals(TimeOfDay.MORNING, required.bestTimeOfDay)
        val checkIn = engine.buildPlan(readiness(recovery = 55, rhr = 66.0, rhrBase = 58.0), CoachingOptions(constraints = avoid)).byId("hydrate-rest")!!
        assertEquals(TimeOfDay.MORNING, checkIn.bestTimeOfDay)
    }

    // --- Goal focus -------------------------------------------------------------------------

    @Test
    fun goalFocus_addsOneCardNamingTheGoal() {
        val expected = mapOf(
            GoalType.SLEEP_RECOVERY to ("winddown" to "Part of your Sleep better goal:"),
            GoalType.STRESS_RESILIENCE to ("breathing" to "Part of your Feel steadier goal:"),
            GoalType.RECOVERY to ("mobility" to "Part of your Recover well goal:"),
        )
        expected.forEach { (goal, slugAndLead) ->
            val plan = engine.buildPlan(readiness(recovery = 80), CoachingOptions(goalType = goal))
            val card = plan.byId(slugAndLead.first)
            assertNotNull("$goal adds a card", card)
            assertEquals(RuleIds.GOAL_FOCUS, card!!.ruleId)
            assertTrue(card.whyItMatters, card.whyItMatters.startsWith(slugAndLead.second))
            assertTrue(card.intensity == Intensity.REST || card.intensity == Intensity.EASY)
            assertEquals(1, plan.activities.count { it.ruleId == RuleIds.GOAL_FOCUS })
        }
    }

    @Test
    fun goalFocus_deDupesAgainstTheMatchingRuleCard_andTheRuleWins() {
        fun only(plan: DailyPlan, slug: String): DailyActivity {
            assertEquals(1, plan.activities.count { it.id == "$day:$slug" })
            return plan.byId(slug)!!
        }
        val poorSleep = engine.buildPlan(readiness(recovery = 80, sleepPerf = 55), CoachingOptions(goalType = GoalType.SLEEP_RECOVERY))
        assertEquals(RuleIds.POOR_SLEEP, only(poorSleep, "winddown").ruleId)

        val lowHrv = engine.buildPlan(readiness(recovery = 80, hrv = 45.0, hrvBase = 70.0), CoachingOptions(goalType = GoalType.STRESS_RESILIENCE))
        assertEquals(RuleIds.LOW_HRV_TREND, only(lowHrv, "breathing").ruleId)

        val lowDay = engine.buildPlan(readiness(recovery = 25), CoachingOptions(goalType = GoalType.RECOVERY))
        val mobility = only(lowDay, "mobility")
        assertEquals(RuleIds.LOW_RECOVERY, mobility.ruleId)
        assertEquals(ActivityPriority.REQUIRED, mobility.priority)
    }

    @Test
    fun goalFocus_noCardForFitnessWeightOrNoGoal() {
        val base = engine.buildPlan(readiness(recovery = 80))
        listOf(GoalType.FITNESS, GoalType.WEIGHT_LOSS, null).forEach { goal ->
            val plan = engine.buildPlan(readiness(recovery = 80), CoachingOptions(goalType = goal))
            assertEquals("$goal", base.activities, plan.activities)
        }
    }

    // --- Sleep anchor -----------------------------------------------------------------------

    @Test
    fun sleepAnchor_putsTonightsTargetInThePlan() {
        val plan = engine.buildPlan(readiness(recovery = 80), CoachingOptions(bedtime = bedtime))
        val anchor = plan.byId("sleep-anchor")!!
        assertEquals("In bed by ${clockAt(22 * 60 + 50)}", anchor.title)
        assertEquals(ActivityCategory.SLEEP, anchor.category)
        assertEquals(ActivityPriority.RECOMMENDED, anchor.priority)
        assertEquals(RuleIds.SLEEP_ANCHOR, anchor.ruleId)
        assertEquals(MetricType.SLEEP_PERFORMANCE, anchor.relatedMetric)
        assertEquals(1, anchor.estimatedMinutes)
        assertEquals(Intensity.REST, anchor.intensity)
        assertEquals(TimeOfDay.EVENING, anchor.bestTimeOfDay)
        assertEquals("Gives you about 8h 10m of sleep — your estimated need.", anchor.whyItMatters)
        assertEquals("Start winding down at ${clockAt(22 * 60 + 5)}.", anchor.instructions.first())
        (listOf(anchor.title, anchor.whyItMatters) + anchor.instructions).forEach { assertTrue(it, safety.validateCopy(it)) }
    }

    @Test
    fun sleepAnchor_whyNamesPaybackOrTheUsersOwnBedtime() {
        fun why(plan: BedtimePlan) = engine.buildPlan(readiness(recovery = 80), CoachingOptions(bedtime = plan)).byId("sleep-anchor")!!.whyItMatters
        assertEquals(
            "Gives you about 8h 10m of sleep — your estimated need, which already counts recent short nights.",
            why(SleepPlanner.tonight(7 * 60, 0.5, 8.0 + 10.0 / 60)!!),
        )
        assertEquals(
            "Your own bedtime from your plan settings — a steady bedtime is one of the strongest sleep habits.",
            why(SleepPlanner.tonight(null, null, null, userBedtimeMinute = 22 * 60 + 30)!!),
        )
        assertEquals(
            "A steady bedtime helps protect tomorrow's recovery.",
            why(BedtimePlan(windDownMinute = 21 * 60 + 45, lightsOutMinute = 22 * 60 + 30, sleepHours = null, debtHours = 0.0, needHours = null)),
        )
    }

    @Test
    fun sleepAnchor_userBedtimeWinsTheWhyEvenWhenItsHoursAreKnown() {
        val own = BedtimePlan(
            windDownMinute = 22 * 60 + 45, lightsOutMinute = 23 * 60 + 30, sleepHours = 7.5,
            debtHours = 1.0, needHours = 8.0, fromUserBedtime = true,
        )
        assertEquals(
            "Your own bedtime from your plan settings — a steady bedtime is one of the strongest sleep habits.",
            engine.buildPlan(readiness(recovery = 80), CoachingOptions(bedtime = own)).byId("sleep-anchor")!!.whyItMatters,
        )
    }

    @Test
    fun sleepAnchor_followsThe24HourClockSetting() {
        fun anchor(lightsOut: Int, windDown: Int, use24: Boolean) = engine.buildPlan(
            readiness(recovery = 80),
            CoachingOptions(
                bedtime = BedtimePlan(windDown, lightsOut, sleepHours = 8.0, debtHours = 0.0, needHours = 8.0),
                use24HourClock = use24,
            ),
        ).byId("sleep-anchor")!!
        val evening = anchor(22 * 60 + 50, 22 * 60 + 5, use24 = true)
        assertEquals("In bed by 22:50", evening.title)
        assertEquals("Start winding down at 22:05.", evening.instructions.first())
        assertEquals("In bed by 00:30", anchor(30, 23 * 60 + 45, use24 = true).title)
        // The default stays 12-hour, as before.
        assertEquals("In bed by ${clockAt(22 * 60 + 50)}", anchor(22 * 60 + 50, 22 * 60 + 5, use24 = false).title)
    }

    @Test
    fun sleepAnchor_survivesTheCapOnABusyDay() {
        val plan = engine.buildPlan(busyLowDay(), CoachingOptions(bedtime = bedtime))
        assertEquals(
            listOf("$day:mobility", "$day:sleep-anchor", "$day:stretch", "$day:winddown"),
            plan.activities.map { it.id },
        )
    }

    // --- Time budget ------------------------------------------------------------------------

    private fun budget(minutes: Int) = PlanConstraints(minutesPerDay = minutes)

    @Test
    fun timeBudget_dropsTheLowestPriorityLatestCardsFirst() {
        val plan = engine.buildPlan(busyLowDay(), CoachingOptions(bedtime = bedtime, constraints = budget(15)))
        // Wind-down (20) then stretch (10) go; the required mobility and tonight's anchor stay.
        assertEquals(listOf("$day:mobility", "$day:sleep-anchor"), plan.activities.map { it.id })
        assertEquals(11, plan.totalEstimatedMinutes)
    }

    @Test
    fun timeBudget_keepsTheMainSessionAndShowsTheHonestTotal() {
        val plan = engine.buildPlan(readiness(recovery = 80), CoachingOptions(constraints = budget(10)))
        assertEquals(listOf("$day:strength"), plan.activities.map { it.id })
        assertEquals(35, plan.totalEstimatedMinutes) // over budget, but the session is never silently dropped
        assertEquals(StrainZone.HIGH, plan.strainTarget)

        val goalDay = engine.buildPlan(readiness(recovery = 80), CoachingOptions(goalType = GoalType.SLEEP_RECOVERY, constraints = budget(36)))
        assertEquals(listOf("$day:strength"), goalDay.activities.map { it.id })
    }

    @Test
    fun timeBudget_neverDropsTheReferralOrTheStaleDayFallback() {
        val referral = engine.buildPlan(readiness(recovery = 55, resp = 19.0, respBase = 15.0), CoachingOptions(constraints = budget(5)))
        // The check-in day's session is the easy walk (MED-2), and it is the main session, so it stays.
        assertEquals(listOf("$day:clinician", "$day:zone2-walk"), referral.activities.map { it.id })

        val stale = engine.buildPlan(readiness(recovery = null), CoachingOptions(constraints = budget(5)))
        assertEquals(listOf("$day:connect-whoop"), stale.activities.map { it.id })
    }

    @Test
    fun timeBudget_dropsOptionalThenRuleCards_andTheGoalCardLast() {
        // Balanced day, poor sleep, Feel-steadier goal: zone2 25, anchor 1, wind-down 20, breathing (goal) 5, water 1.
        val options = CoachingOptions(
            maxActivities = 5, bedtime = bedtime, goalType = GoalType.STRESS_RESILIENCE, constraints = budget(35),
        )
        val plan = engine.buildPlan(readiness(recovery = 60, sleepPerf = 55), options)
        // Water goes first, then the wind-down rule card — the goal's own card survives.
        assertEquals(listOf("$day:zone2", "$day:sleep-anchor", "$day:breathing"), plan.activities.map { it.id })
        assertEquals(RuleIds.GOAL_FOCUS, plan.byId("breathing")!!.ruleId)
        assertEquals(31, plan.totalEstimatedMinutes)
    }

    @Test
    fun timeBudget_thatFitsChangesNothing() {
        val options = CoachingOptions(bedtime = bedtime)
        assertEquals(
            engine.buildPlan(busyLowDay(), options).activities,
            engine.buildPlan(busyLowDay(), options.copy(constraints = budget(120))).activities,
        )
    }

    // --- Daily minimum ----------------------------------------------------------------------

    private fun floorOf(min: Int) = CoachingOptions(maxActivities = 5, minActivities = min)
    private fun ids(plan: DailyPlan) = plan.activities.map { it.id.substringAfter(':') }
    private val movement = setOf(ActivityCategory.MOBILITY, ActivityCategory.MOVEMENT, ActivityCategory.ZONE2, ActivityCategory.STRENGTH)

    @Test
    fun minActivities_topsAShortPlanUpWithAGentleHabit() {
        val base = engine.buildPlan(readiness(recovery = 60), CoachingOptions(maxActivities = 5))
        assertEquals(listOf("zone2", "hydration"), ids(base))

        val plan = engine.buildPlan(readiness(recovery = 60), floorOf(3))
        assertEquals(listOf("zone2", "hydration", "morning-light"), ids(plan))
        val habit = plan.byId("morning-light")!!
        assertEquals("10 minutes of morning daylight", habit.title)
        assertEquals(RuleIds.DAILY_HABIT, habit.ruleId)
        assertEquals(ActivityPriority.OPTIONAL, habit.priority)
        assertEquals(MetricType.SLEEP_PERFORMANCE, habit.relatedMetric)
        assertEquals(Intensity.EASY, habit.intensity)
        assertTrue(habit.instructions.isNotEmpty())
        // A habit never moves the day's target, and the same input gives the same plan.
        assertEquals(StrainZone.MODERATE, plan.strainTarget)
        assertEquals(plan, engine.buildPlan(readiness(recovery = 60), floorOf(3)))
        // A plan already at the floor is untouched, and the floor never goes past the cap.
        assertEquals(base.activities, engine.buildPlan(readiness(recovery = 60), floorOf(2)).activities)
        assertEquals(base.activities, engine.buildPlan(readiness(recovery = 60), CoachingOptions(maxActivities = 2, minActivities = 5)).activities)
    }

    @Test
    fun minActivities_respectsWontDoLists_andNeverDoublesUpANeed() {
        // No morning workouts: morning daylight is skipped and the evening stretch comes next.
        val noMornings = engine.buildPlan(readiness(recovery = 60), floorOf(3).copy(constraints = avoiding(AvoidOption.MORNING_SESSIONS)))
        assertEquals(listOf("zone2", "hydration", "stretch-reset"), ids(noMornings))

        // No breathing either: water is already planned, so the caffeine cutoff is the last habit — and
        // the plan honestly stops at four rather than repeating a need.
        val strict = engine.buildPlan(
            readiness(recovery = 60),
            floorOf(5).copy(constraints = avoiding(AvoidOption.MORNING_SESSIONS, AvoidOption.BREATHWORK)),
        )
        assertEquals(listOf("zone2", "hydration", "stretch-reset", "caffeine-cutoff"), ids(strict))
        assertFalse(strict.activities.any { it.category == ActivityCategory.BREATHWORK })

        // Breathing is already in the plan (low HRV), so no second breathing card is added.
        val lowHrv = engine.buildPlan(readiness(recovery = 60, hrv = 45.0, hrvBase = 70.0), floorOf(5))
        assertEquals(listOf("zone2", "breathing", "hydration", "morning-light", "stretch-reset"), ids(lowHrv))
        assertEquals(RuleIds.LOW_HRV_TREND, lowHrv.byId("breathing")!!.ruleId)
    }

    @Test
    fun minActivities_staysRestfulOnPainAndCheckInDays() {
        val pain = engine.buildPlan(readiness(recovery = 80), floorOf(3).copy(painFlag = true))
        assertEquals(listOf("pain-deload", "hydration", "breathing"), ids(pain))
        assertEquals(StrainZone.LIGHT, pain.strainTarget)

        val checkIn = engine.buildPlan(readiness(recovery = 60, resp = 19.0, respBase = 15.0), floorOf(4))
        assertEquals(listOf("clinician", "zone2-walk", "hydration", "breathing"), ids(checkIn))
        assertEquals(StrainZone.LIGHT, checkIn.strainTarget)

        listOf(pain, checkIn).forEach { plan ->
            assertFalse(plan.activities.any { it.ruleId == RuleIds.DAILY_HABIT && it.category in movement })
        }
    }

    @Test
    fun minActivities_fitsTheTimeBudget_andTopsUpStaleDaysToo() {
        // 40 minutes: the session and water leave 14, so the 10-minute daylight habit fits.
        val roomy = engine.buildPlan(readiness(recovery = 60), floorOf(3).copy(constraints = budget(40)))
        assertEquals(listOf("zone2", "hydration", "morning-light"), ids(roomy))
        assertEquals(36, roomy.totalEstimatedMinutes)
        // 30 minutes leaves 4: only the one-minute caffeine cutoff fits.
        val tight = engine.buildPlan(readiness(recovery = 60), floorOf(3).copy(constraints = budget(30)))
        assertEquals(listOf("zone2", "hydration", "caffeine-cutoff"), ids(tight))
        // Already over budget: nothing is added on top.
        assertEquals(listOf("zone2"), ids(engine.buildPlan(readiness(recovery = 60), floorOf(3).copy(constraints = budget(10)))))

        val stale = engine.buildPlan(readiness(recovery = null), floorOf(3))
        assertEquals(listOf("connect-whoop", "hydration", "morning-light"), ids(stale))
        assertNull(stale.strainTarget)
    }

    @Test
    fun minActivities_everyHabitIsSafeCopyAndNeverATrainingSession() {
        val plans = listOf(
            engine.buildPlan(readiness(recovery = 60), floorOf(5)),
            engine.buildPlan(readiness(recovery = 80), floorOf(5).copy(painFlag = true)),
            engine.buildPlan(readiness(recovery = null), floorOf(5).copy(constraints = avoiding(AvoidOption.MORNING_SESSIONS))),
        )
        val habits = plans.flatMap { plan -> plan.activities.filter { it.ruleId == RuleIds.DAILY_HABIT } }
        assertTrue(habits.isNotEmpty())
        habits.forEach { a ->
            assertTrue(a.title, a.intensity == Intensity.EASY || a.intensity == Intensity.REST)
            assertTrue(a.title, a.category != ActivityCategory.STRENGTH)
            (listOf(a.title, a.whyItMatters) + a.instructions).forEach { assertTrue(it, safety.validateCopy(it)) }
        }
        plans.forEach { plan -> assertEquals(plan.activities.size, plan.activities.map { it.id }.toSet().size) }
    }

    // --- Invariants across the new options --------------------------------------------------

    @Test
    fun options_keepThePlanSafeAndTheStrainTargetHonest() {
        val goals = GoalType.entries + listOf<GoalType?>(null)
        val avoids = listOf(emptySet<AvoidOption>(), AvoidOption.entries.toSet(), setOf(AvoidOption.STRENGTH_TRAINING))
        for (recovery in listOf(10, 40, 60, 80, null)) {
            for (goal in goals) for (avoid in avoids) for (minutes in listOf(null, 10, 30)) for (pain in listOf(false, true)) {
                val r = readiness(recovery = recovery, sleepPerf = 60, hrv = 45.0, hrvBase = 70.0)
                val options = CoachingOptions(
                    painFlag = pain, bedtime = bedtime, goalType = goal,
                    constraints = PlanConstraints(minutesPerDay = minutes, avoid = avoid),
                )
                val plan = engine.buildPlan(r, options)
                val ctx = "recovery=$recovery goal=$goal avoid=$avoid minutes=$minutes pain=$pain"
                assertTrue(ctx, plan.activities.isNotEmpty())
                assertEquals(ctx, plan.activities.size, plan.activities.map { it.id }.toSet().size)
                assertTrue(ctx, plan.activities.any { it.ruleId == RuleIds.SLEEP_ANCHOR })
                plan.activities.forEach { a ->
                    (listOf(a.title, a.whyItMatters) + a.instructions + listOfNotNull(a.safetyNote))
                        .forEach { assertTrue("$ctx unsafe: $it", safety.validateCopy(it)) }
                }
                if (AvoidOption.BREATHWORK in avoid) assertFalse(ctx, plan.activities.any { it.category == ActivityCategory.BREATHWORK })
                if (AvoidOption.STRENGTH_TRAINING in avoid) assertFalse(ctx, plan.activities.any { it.category == ActivityCategory.STRENGTH })
                // Hard work only ever appears with a High target, and a High target only with hard work.
                assertEquals(ctx, plan.activities.any { it.intensity == Intensity.HARD }, plan.strainTarget == StrainZone.HIGH)
            }
        }
    }
}
