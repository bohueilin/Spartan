package com.spartan.domain

import com.spartan.domain.engine.Goal
import com.spartan.domain.engine.GoalEngine
import com.spartan.domain.engine.GoalType
import com.spartan.domain.engine.GoalValidation
import com.spartan.domain.engine.PressureWindow
import com.spartan.domain.engine.ReferenceRanges
import com.spartan.domain.engine.SafetyEngine
import com.spartan.domain.engine.SexAtBirth
import com.spartan.domain.engine.StressPatterns
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.WhoopSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class CoachEnginesTest {

    // --- ReferenceRanges -----------------------------------------------------

    @Test
    fun referenceRanges_coverTopFive_andAdjustByAgeAndSex() {
        ReferenceRanges.topFive.forEach { metric ->
            assertNotNull("band missing for $metric", ReferenceRanges.bandFor(metric, 44, SexAtBirth.MALE))
        }
        // HRV bands decline with age.
        val hrv20s = ReferenceRanges.bandFor(MetricType.HRV_RMSSD, 25, SexAtBirth.MALE)!!
        val hrv60s = ReferenceRanges.bandFor(MetricType.HRV_RMSSD, 65, SexAtBirth.MALE)!!
        assertTrue(hrv20s.typicalHigh!! > hrv60s.typicalHigh!!)
        // Women's typical RHR band sits a touch higher than men's, same bracket.
        val rhrF = ReferenceRanges.bandFor(MetricType.RESTING_HEART_RATE, 44, SexAtBirth.FEMALE)!!
        val rhrM = ReferenceRanges.bandFor(MetricType.RESTING_HEART_RATE, 44, SexAtBirth.MALE)!!
        assertTrue(rhrF.typicalLow!! > rhrM.typicalLow!!)
        // Unknown demographics still get an all-adult band, never a refusal.
        assertNotNull(ReferenceRanges.bandFor(MetricType.RESTING_HEART_RATE, null, SexAtBirth.UNSPECIFIED))
        // Non-coached metrics are never faked.
        assertNull(ReferenceRanges.bandFor(MetricType.APOB, 44, SexAtBirth.MALE))
        assertEquals("40–49", ReferenceRanges.bracketLabel(44))
    }

    @Test
    fun referenceRanges_educationCopy_passesSafetyEngine() {
        val safety = SafetyEngine()
        for (age in listOf(null, 25, 44, 65)) {
            for (sex in SexAtBirth.entries) {
                ReferenceRanges.topFive.forEach { metric ->
                    ReferenceRanges.bandFor(metric, age, sex)?.let { safety.sanitize(it.education) }
                }
            }
        }
    }

    // --- GoalEngine ----------------------------------------------------------

    private fun goal(type: GoalType, target: Double, weeks: Int, baseline: Double? = null) = Goal(
        id = "g1", type = type, targetValue = target, baselineValue = baseline,
        startEpochDay = 20000, targetEpochDay = 20000 + weeks * 7L,
    )

    @Test
    fun weightGoal_within2lbPerWeek_isAccepted() {
        val v = GoalEngine.validate(goal(GoalType.WEIGHT_LOSS, target = 8.0, weeks = 5))
        assertTrue(v is GoalValidation.Ok)
    }

    @Test
    fun weightGoal_tooFast_isCounterOfferedAtSafeRate_notRefused() {
        // The user's own example: 10 lb in 4 weeks = 2.5 lb/wk → nearest safe is 5 weeks.
        val v = GoalEngine.validate(goal(GoalType.WEIGHT_LOSS, target = 10.0, weeks = 4))
        assertTrue(v is GoalValidation.Adjusted)
        val adjusted = (v as GoalValidation.Adjusted).adjusted
        assertEquals(20000 + 5 * 7L, adjusted.targetEpochDay)
        assertTrue(v.why.contains("1–2 lb"))
        SafetyEngine().sanitize(v.why)
    }

    @Test
    fun sleepGoal_10pctIn3Weeks_isAccepted_and30pctIn2WeeksIsAdjusted() {
        assertTrue(GoalEngine.validate(goal(GoalType.SLEEP_RECOVERY, 10.0, weeks = 3)) is GoalValidation.Ok)
        val fast = GoalEngine.validate(goal(GoalType.SLEEP_RECOVERY, 30.0, weeks = 2))
        assertTrue(fast is GoalValidation.Adjusted)
        assertEquals(20000 + 6 * 7L, (fast as GoalValidation.Adjusted).adjusted.targetEpochDay)
    }

    @Test
    fun invalidGoals_zeroTarget_orNoRunway_areRejected() {
        assertTrue(GoalEngine.validate(goal(GoalType.WEIGHT_LOSS, 0.0, 4)) is GoalValidation.Invalid)
        val noRunway = Goal("g", GoalType.WEIGHT_LOSS, 5.0, null, 20000, 20000)
        assertTrue(GoalEngine.validate(noRunway) is GoalValidation.Invalid)
    }

    @Test
    fun weightProgress_convertsKgReadingsToPounds() {
        // Baseline 81.6 kg; current week average 79.4 kg → ~4.9 lb lost of a 10 lb target.
        val today = 20028L
        val readings = (0..6).map { d ->
            MetricReading(MetricType.WEIGHT, 79.4, LocalDate.ofEpochDay(today - d))
        }
        val g = goal(GoalType.WEIGHT_LOSS, target = 10.0, weeks = 6, baseline = 81.6)
        val p = GoalEngine.progress(g, readings, today)
        assertEquals(0.485, p.fraction, 0.02)
        assertTrue(p.summary.contains("lb"))
    }

    @Test
    fun sleepProgress_percentGainVsBaseline() {
        val today = 20014L
        val readings = (0..6).map { d ->
            MetricReading(MetricType.SLEEP_PERFORMANCE, 77.0, LocalDate.ofEpochDay(today - d))
        }
        val g = goal(GoalType.SLEEP_RECOVERY, target = 10.0, weeks = 3, baseline = 70.0)
        val p = GoalEngine.progress(g, readings, today)
        assertEquals(1.0, p.fraction, 0.01) // 70→77 = +10%
        assertTrue(p.onTrack)
    }

    @Test
    fun stressGoal_isAHabitGoal_countingCalmSessions() {
        val g = goal(GoalType.STRESS_RESILIENCE, target = 5.0, weeks = 3, baseline = 35.0)
        val p = GoalEngine.progress(g, emptyList(), todayEpochDay = 20007, breathworkThisWeek = 3)
        assertEquals(0.6, p.fraction, 0.001)
        assertTrue(p.summary.contains("3 of 5"))
    }

    @Test
    fun goalModifiers_bendThePlanTowardTheGoal() {
        val weight = GoalEngine.planModifiers(goal(GoalType.WEIGHT_LOSS, 10.0, 6), emptyList())
        assertEquals(60, weight.extraZone2MinutesPerWeek)
        assertEquals(2, weight.protectStrengthSessions)
        val sleep = GoalEngine.planModifiers(goal(GoalType.SLEEP_RECOVERY, 10.0, 3), emptyList())
        assertTrue(sleep.emphasizeWindDown)
        val none = GoalEngine.planModifiers(null, emptyList())
        assertEquals(0, none.extraZone2MinutesPerWeek)
        assertFalse(none.emphasizeWindDown)
    }

    // --- StressPatterns ------------------------------------------------------

    /** Snapshots where every Wednesday morning (after Tuesday) runs 20 points low. */
    private fun tuesdayStressedSnapshots(weeks: Int): List<WhoopSnapshot> {
        val start = LocalDate.of(2026, 6, 1) // a Monday
        return (0 until weeks * 7).map { i ->
            val date = start.plusDays(i.toLong())
            val recovery = if (date.dayOfWeek == DayOfWeek.WEDNESDAY) 45 else 65
            WhoopSnapshot(dateEpochDay = date.toEpochDay(), recoveryScore = recovery)
        }
    }

    @Test
    fun weekdayEffect_findsTheStressorDay_withEnoughEvidence() {
        val effects = StressPatterns.weekdayEffects(tuesdayStressedSnapshots(weeks = 4))
        assertEquals(1, effects.size)
        assertEquals(DayOfWeek.TUESDAY, effects.first().stressorDay)
        assertTrue(effects.first().recoveryDelta < -StressPatterns.MIN_EFFECT_RECOVERY_POINTS)
        assertEquals(4, effects.first().sampleWeeks)
        SafetyEngine().sanitize(effects.first().insight)
    }

    @Test
    fun weekdayEffect_staysSilent_onThinOrFlatData() {
        assertTrue(StressPatterns.weekdayEffects(tuesdayStressedSnapshots(weeks = 2)).isEmpty())
        val flat = (0 until 28).map {
            WhoopSnapshot(dateEpochDay = 20000L + it, recoveryScore = 60 + (it % 3))
        }
        assertTrue(StressPatterns.weekdayEffects(flat).isEmpty())
    }

    @Test
    fun pressureWindows_matchDays_andNudgeFiresFiveMinutesEarly() {
        // Tue+Thu 11:00–12:00 (mask bits: Mon=0 … Sun=6).
        val window = PressureWindow(
            id = "w1",
            daysOfWeekMask = (1 shl DayOfWeek.TUESDAY.ordinal) or (1 shl DayOfWeek.THURSDAY.ordinal),
            startMinuteOfDay = 11 * 60, endMinuteOfDay = 12 * 60, label = "Standup",
        )
        val tue = LocalDate.of(2026, 7, 21)
        val wed = LocalDate.of(2026, 7, 22)
        assertEquals(listOf(window), StressPatterns.windowsForDay(listOf(window), tue))
        assertTrue(StressPatterns.windowsForDay(listOf(window), wed).isEmpty())
        assertEquals(11 * 60 - 5, StressPatterns.nudgeMinuteFor(window))
        assertEquals("Tue, Thu 11:00–12:00", StressPatterns.describeWindow(window))
    }

    @Test
    fun pressureWindow_sundayBit_andMidnightClamp() {
        val sunday = PressureWindow("w2", 1 shl DayOfWeek.SUNDAY.ordinal, 2, 60)
        assertEquals(
            listOf(sunday),
            StressPatterns.windowsForDay(listOf(sunday), LocalDate.of(2026, 7, 26)),
        )
        assertEquals(0, StressPatterns.nudgeMinuteFor(sunday)) // 2-min start clamps to midnight
    }
}
