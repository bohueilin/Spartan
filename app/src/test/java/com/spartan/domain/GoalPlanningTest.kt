package com.spartan.domain

import com.spartan.domain.engine.ActionPlaybook
import com.spartan.domain.engine.AvoidOption
import com.spartan.domain.engine.Goal
import com.spartan.domain.engine.GoalBaselines
import com.spartan.domain.engine.GoalDraft
import com.spartan.domain.engine.GoalEngine
import com.spartan.domain.engine.GoalPlanning
import com.spartan.domain.engine.GoalPreset
import com.spartan.domain.engine.GoalStatus
import com.spartan.domain.engine.GoalType
import com.spartan.domain.engine.GoalValidation
import com.spartan.domain.engine.PlanConstraints
import com.spartan.domain.engine.PressureWindow
import com.spartan.domain.engine.SafetyEngine
import com.spartan.domain.engine.WeeklyDecision
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.WorkoutLog
import com.spartan.domain.model.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalPlanningTest {

    private val today = 20000L
    private val safety = SafetyEngine()

    private fun reading(type: MetricType, value: Double?, daysAgo: Long) =
        MetricReading(type, value, LocalDate.ofEpochDay(today - daysAgo))

    private fun week(type: MetricType, value: Double) = (0L..6L).map { reading(type, value, it) }

    private fun log(type: WorkoutType, daysAgo: Long) = WorkoutLog(
        type = type, plannedMinutes = 25, completedMinutes = 25, rpe = 4, painFlag = false,
        completedAt = LocalDate.ofEpochDay(today - daysAgo),
    )

    private fun assertSafe(copy: String) = assertTrue("unsafe copy: $copy", safety.validateCopy(copy))

    /** The playbook's own title for [key], so a renamed action fails here rather than drifting. */
    private fun title(metric: MetricType, key: String) = ActionPlaybook.forMetric(metric).first { it.key == key }.title

    private fun goalFrom(draft: GoalDraft) = Goal(
        id = "g", type = draft.preset.goalType, targetValue = draft.targetValue,
        baselineValue = draft.baselineDisplayValue, startEpochDay = today, targetEpochDay = today + draft.weeks * 7L,
    )

    private fun goal(type: GoalType, target: Double, weeks: Int = 3, baseline: Double? = null, start: Long = today) = Goal(
        id = "g1", type = type, targetValue = target, baselineValue = baseline,
        startEpochDay = start, targetEpochDay = start + weeks * 7L,
    )

    // --- Presets and constraints ---------------------------------------------

    @Test
    fun presets_mapOneToOneOntoGoalTypes_andWeightHasNone() {
        GoalPreset.entries.forEach { assertEquals(it, GoalPreset.forGoalType(it.goalType)) }
        assertNull(GoalPreset.forGoalType(GoalType.WEIGHT_LOSS))
        assertEquals(listOf("Sleep better", "Get fitter", "Feel steadier", "Recover well"), GoalPreset.entries.map { it.label })
        assertEquals("Strength training", AvoidOption.STRENGTH_TRAINING.label)
        assertEquals(3, WeeklyDecision.entries.size)
    }

    @Test
    fun constraints_areEmptyUntilAnythingIsSaid() {
        assertTrue(PlanConstraints().isEmpty)
        assertTrue(PlanConstraints(limitsNote = "   ").isEmpty)
        assertFalse(PlanConstraints(minutesPerDay = 20).isEmpty)
        assertFalse(PlanConstraints(limitsNote = "Left knee").isEmpty)
        assertFalse(PlanConstraints(bedtimeMinute = 22 * 60).isEmpty)
        assertFalse(PlanConstraints(wakeMinute = 7 * 60).isEmpty)
        assertFalse(PlanConstraints(avoid = setOf(AvoidOption.MORNING_SESSIONS)).isEmpty)
    }

    // --- Baselines -----------------------------------------------------------

    @Test
    fun baselines_sleepAveragesTheLast14Days_todayIncluded() {
        val readings = listOf(
            reading(MetricType.SLEEP_PERFORMANCE, 80.0, 0),
            reading(MetricType.SLEEP_PERFORMANCE, 70.0, 13),
            reading(MetricType.SLEEP_PERFORMANCE, 10.0, 14), // 15 days back: outside the window
            reading(MetricType.SLEEP_PERFORMANCE, null, 1), // an empty entry is not a zero
            reading(MetricType.SLEEP_PERFORMANCE, 0.0, -1), // tomorrow
            reading(MetricType.HRV_RMSSD, 50.0, 0),
            reading(MetricType.SLEEP_DURATION, 7.0, 0),
            reading(MetricType.SLEEP_DURATION, 6.0, 5),
        )
        val b = GoalPlanning.baselines(readings, emptyList(), calmSessionsLast28Days = 0, todayEpochDay = today)
        assertEquals(75.0, b.sleepPerformancePercent!!, 0.0001)
        assertEquals(6.5, b.sleepHours!!, 0.0001)
    }

    @Test
    fun baselines_sessionsAreWeeklyAveragesOverThe28Days() {
        val logs = listOf(
            log(WorkoutType.ZONE_2, 0),
            log(WorkoutType.STRENGTH, 3),
            log(WorkoutType.STRENGTH, 27),
            log(WorkoutType.ZONE_2, 28), // 29 days back: outside the window
            log(WorkoutType.MOBILITY, 1),
            log(WorkoutType.MOBILITY, 20),
            log(WorkoutType.RECOVERY, 2),
            log(WorkoutType.REVIEW, 4),
        )
        val b = GoalPlanning.baselines(emptyList(), logs, calmSessionsLast28Days = 6, todayEpochDay = today)
        assertEquals(0.75, b.trainingSessionsPerWeek!!, 0.0001)
        assertEquals(0.5, b.recoverySessionsPerWeek!!, 0.0001)
        assertEquals(1.5, b.calmSessionsPerWeek!!, 0.0001)
    }

    @Test
    fun baselines_brandNewUser_isStillLearning_notZero() {
        val empty = GoalPlanning.baselines(emptyList(), emptyList(), calmSessionsLast28Days = 0, todayEpochDay = today)
        assertEquals(GoalBaselines(), empty)
        // Only stale data: still learning.
        val stale = GoalPlanning.baselines(
            readings = listOf(reading(MetricType.SLEEP_PERFORMANCE, 80.0, 20)),
            logs = listOf(log(WorkoutType.ZONE_2, 30)),
            calmSessionsLast28Days = 0,
            todayEpochDay = today,
        )
        assertEquals(GoalBaselines(), stale)
    }

    @Test
    fun baselines_onceAnythingIsLogged_aZeroIsReal() {
        val b = GoalPlanning.baselines(emptyList(), listOf(log(WorkoutType.MOBILITY, 2)), 0, today)
        assertEquals(0.0, b.trainingSessionsPerWeek!!, 0.0)
        assertEquals(0.25, b.recoverySessionsPerWeek!!, 0.0001)
        assertNull(b.calmSessionsPerWeek)
    }

    // --- Sleep better --------------------------------------------------------

    @Test
    fun sleepDraft_targetsTheNextWhoopChoiceAboveTheUsual() {
        val d = GoalPlanning.draft(GoalPreset.SLEEP_BETTER, GoalBaselines(sleepPerformancePercent = 78.0))
        assertEquals(85.0, d.targetDisplayValue, 0.0)
        assertEquals(78.0, d.baselineDisplayValue!!, 0.0)
        assertEquals("%", d.displayUnit)
        assertEquals(7.0 / 78.0 * 100.0, d.targetValue, 0.0001) // % gained over the usual
        assertEquals(2, d.weeks) // 9% fits in two weeks at <= 5%/week
        assertEquals(
            "Get 85% of the sleep you need on most nights — up from your usual 78% — and review in 2 weeks.",
            d.smartSentence,
        )
        val threeWeeks = GoalPlanning.draft(GoalPreset.SLEEP_BETTER, GoalBaselines(sleepPerformancePercent = 78.0), weeks = 3)
        assertEquals(
            "Get 85% of the sleep you need on most nights — up from your usual 78% — and review in 3 weeks.",
            threeWeeks.smartSentence,
        )

        fun targetFor(usual: Double) =
            GoalPlanning.draft(GoalPreset.SLEEP_BETTER, GoalBaselines(sleepPerformancePercent = usual)).targetDisplayValue
        assertEquals(70.0, targetFor(60.0), 0.0)
        assertEquals(85.0, targetFor(70.0), 0.0) // strictly above the usual
        assertEquals(100.0, targetFor(85.0), 0.0)
        assertEquals(100.0, targetFor(97.5), 0.0)
        assertEquals(100.0, targetFor(100.0), 0.0)
        // 60 → 70 is a 16.7% gain: four weeks at the safe rate.
        assertEquals(4, GoalPlanning.draft(GoalPreset.SLEEP_BETTER, GoalBaselines(sleepPerformancePercent = 60.0)).weeks)
        // A sliver of a gain is still saved as a meaningful 2%.
        val sliver = GoalPlanning.draft(GoalPreset.SLEEP_BETTER, GoalBaselines(sleepPerformancePercent = 69.5))
        assertEquals(70.0, sliver.targetDisplayValue, 0.0)
        assertEquals(2.0, sliver.targetValue, 0.0)
    }

    @Test
    fun sleepDraft_withoutABaseline_usesALabelledDefault_andDropsTheUsualClause() {
        val d = GoalPlanning.draft(GoalPreset.SLEEP_BETTER, GoalBaselines())
        assertNull(d.baselineDisplayValue)
        assertEquals(85.0, d.targetDisplayValue, 0.0)
        assertEquals(10.0 / 75.0 * 100.0, d.targetValue, 0.0001)
        assertEquals(3, d.weeks)
        assertEquals("Get 85% of the sleep you need on most nights, and review in 3 weeks.", d.smartSentence)
        // A broken reading is treated as unknown, not as a number.
        assertNull(GoalPlanning.draft(GoalPreset.SLEEP_BETTER, GoalBaselines(sleepPerformancePercent = Double.NaN)).baselineDisplayValue)
    }

    @Test
    fun sleepDraft_overridesAreClampedToSafeRealisticValues() {
        val usual = GoalBaselines(sleepPerformancePercent = 78.0)
        val low = GoalPlanning.draft(GoalPreset.SLEEP_BETTER, usual, targetDisplayValue = 50.0)
        assertEquals(79.0, low.targetDisplayValue, 0.0)
        assertEquals(2.0, low.targetValue, 0.0)

        val high = GoalPlanning.draft(GoalPreset.SLEEP_BETTER, usual, targetDisplayValue = 120.0, weeks = 3)
        assertEquals(100.0, high.targetDisplayValue, 0.0)
        assertEquals(6, high.weeks) // 28% needs six weeks; three is never accepted
        assertEquals(
            "Get 100% of the sleep you need on most nights — up from your usual 78% — and review in 6 weeks.",
            high.smartSentence,
        )
        assertEquals(12, GoalPlanning.draft(GoalPreset.SLEEP_BETTER, usual, targetDisplayValue = 100.0, weeks = 20).weeks)
        assertEquals(2, GoalPlanning.draft(GoalPreset.SLEEP_BETTER, usual, weeks = 1).weeks)
        assertEquals(12, GoalPlanning.draft(GoalPreset.SLEEP_BETTER, usual, weeks = 20).weeks)
        assertEquals(85.0, GoalPlanning.draft(GoalPreset.SLEEP_BETTER, usual, targetDisplayValue = Double.NaN).targetDisplayValue, 0.0)
        assertEquals(
            85.0,
            GoalPlanning.draft(GoalPreset.SLEEP_BETTER, usual, targetDisplayValue = Double.POSITIVE_INFINITY).targetDisplayValue,
            0.0,
        )
    }

    @Test
    fun sleepDraft_atTheTop_keepsTheUsualRatherThanInventingAGain() {
        val d = GoalPlanning.draft(GoalPreset.SLEEP_BETTER, GoalBaselines(sleepPerformancePercent = 100.0))
        assertEquals(100.0, d.targetDisplayValue, 0.0)
        // No headroom above 100%: a token gain keeps the goal valid, and the goal still reads 100%.
        assertEquals(0.1, d.targetValue, 0.0)
        assertEquals(100.0, GoalPlanning.sleepTargetPercent(goalFrom(d))!!, 0.0)
        assertEquals(
            "Get 100% of the sleep you need on most nights — keeping up your usual 100% — and review in 2 weeks.",
            d.smartSentence,
        )
        // Out-of-range data is held to 0..100 rather than trusted.
        assertEquals(
            100.0,
            GoalPlanning.draft(GoalPreset.SLEEP_BETTER, GoalBaselines(sleepPerformancePercent = 150.0)).baselineDisplayValue!!,
            0.0,
        )
    }

    @Test
    fun sleepDraft_nearTheTop_neverFloorsTheTargetPast100() {
        // A usual of 99%: the 2% floor would need 101%. The gain stops at the headroom instead.
        val d = GoalPlanning.draft(GoalPreset.SLEEP_BETTER, GoalBaselines(sleepPerformancePercent = 99.0))
        assertEquals(100.0, d.targetDisplayValue, 0.0)
        assertEquals(1.0 / 99.0 * 100.0, d.targetValue, 1e-9)
        assertEquals(100.0, 99.0 * (1 + d.targetValue / 100.0), 1e-9)
        assertTrue(GoalEngine.validate(goalFrom(d)) is GoalValidation.Ok)
        // With room for it (97 → 98 is a 1% gain, 3% headroom), the 2% floor still applies.
        val sliver = GoalPlanning.draft(GoalPreset.SLEEP_BETTER, GoalBaselines(sleepPerformancePercent = 97.0), targetDisplayValue = 98.0)
        assertEquals(2.0, sliver.targetValue, 0.0)
    }

    @Test
    fun sleepTargetPercent_isTheAbsoluteTargetTheWizardOffered() {
        val d = GoalPlanning.draft(GoalPreset.SLEEP_BETTER, GoalBaselines(sleepPerformancePercent = 75.0))
        assertEquals(85.0, d.targetDisplayValue, 0.0)
        assertEquals(85.0, GoalPlanning.sleepTargetPercent(goalFrom(d))!!, 1e-9) // not "+13%"
        // Capped at 100, even if a stored relative gain would point past it.
        assertEquals(100.0, GoalPlanning.sleepTargetPercent(goal(GoalType.SLEEP_RECOVERY, 10.0, baseline = 95.0))!!, 0.0)
        // No baseline to anchor to, an unusable one, or another goal type: nothing to show.
        assertNull(GoalPlanning.sleepTargetPercent(goal(GoalType.SLEEP_RECOVERY, 10.0)))
        assertNull(GoalPlanning.sleepTargetPercent(goal(GoalType.SLEEP_RECOVERY, 10.0, baseline = 0.0)))
        assertNull(GoalPlanning.sleepTargetPercent(goal(GoalType.SLEEP_RECOVERY, 10.0, baseline = Double.NaN)))
        assertNull(GoalPlanning.sleepTargetPercent(goal(GoalType.FITNESS, 3.0, baseline = 62.0)))
    }

    @Test
    fun sleepDraft_neverUsesAFixedHourTarget() {
        val hours = Regex("""\d\s*h\b|hour""")
        listOf(null, 60.0, 78.0, 97.5).forEach { usual ->
            val d = GoalPlanning.draft(GoalPreset.SLEEP_BETTER, GoalBaselines(sleepPerformancePercent = usual, sleepHours = 6.5))
            assertFalse(d.smartSentence, hours.containsMatchIn(d.smartSentence))
            assertEquals("%", d.displayUnit)
        }
    }

    // --- Get fitter ----------------------------------------------------------

    @Test
    fun fitterDraft_isOneMoreThanTheUsual() {
        val d = GoalPlanning.draft(GoalPreset.GET_FITTER, GoalBaselines(trainingSessionsPerWeek = 1.75))
        assertEquals(3.0, d.targetValue, 0.0)
        assertEquals(3.0, d.targetDisplayValue, 0.0)
        assertEquals(2.0, d.baselineDisplayValue!!, 0.0)
        assertEquals(4, d.weeks)
        assertEquals("sessions a week", d.displayUnit)
        assertEquals("Do 3 Zone 2 or strength sessions a week — one more than your usual 2 — and review in 4 weeks.", d.smartSentence)

        val unknown = GoalPlanning.draft(GoalPreset.GET_FITTER, GoalBaselines())
        assertNull(unknown.baselineDisplayValue)
        assertEquals(3.0, unknown.targetValue, 0.0)
        assertEquals("Do 3 Zone 2 or strength sessions a week and review in 4 weeks.", unknown.smartSentence)

        fun sentenceFor(usual: Double, target: Double? = null) =
            GoalPlanning.draft(GoalPreset.GET_FITTER, GoalBaselines(trainingSessionsPerWeek = usual), target).smartSentence
        assertEquals("Do 2 Zone 2 or strength sessions a week — a gentle start — and review in 4 weeks.", sentenceFor(0.0))
        assertEquals("Do 6 Zone 2 or strength sessions a week — the same as your usual 6 — and review in 4 weeks.", sentenceFor(6.0))
        assertTrue(sentenceFor(7.0).contains("Do 6 Zone 2 or strength sessions a week — one fewer than your usual 7, leaving room for rest —"))
        assertTrue(sentenceFor(4.0, target = 2.0).contains("— two fewer than your usual 4, leaving room for rest —"))
        assertTrue(sentenceFor(20.0).contains("— 14 fewer than your usual 20, leaving room for rest —"))
    }

    @Test
    fun fitterDraft_overridesAreClamped() {
        val none = GoalBaselines()
        val one = GoalPlanning.draft(GoalPreset.GET_FITTER, none, targetDisplayValue = 0.4)
        assertEquals(1.0, one.targetValue, 0.0)
        assertEquals("Do 1 Zone 2 or strength session a week and review in 4 weeks.", one.smartSentence)
        assertEquals(6.0, GoalPlanning.draft(GoalPreset.GET_FITTER, none, targetDisplayValue = 9.0).targetValue, 0.0)
        assertEquals(4.0, GoalPlanning.draft(GoalPreset.GET_FITTER, none, targetDisplayValue = 3.6).targetValue, 0.0)
        assertEquals(2, GoalPlanning.draft(GoalPreset.GET_FITTER, none, weeks = 1).weeks)
        assertEquals(12, GoalPlanning.draft(GoalPreset.GET_FITTER, none, weeks = 30).weeks)
        assertEquals(6, GoalPlanning.draft(GoalPreset.GET_FITTER, none, weeks = 6).weeks)
    }

    // --- Feel steadier / Recover well ----------------------------------------

    @Test
    fun steadierDraft_isTwoMoreCalmSessionsThanTheUsual() {
        val d = GoalPlanning.draft(GoalPreset.FEEL_STEADIER, GoalBaselines(calmSessionsPerWeek = 2.0))
        assertEquals(4.0, d.targetValue, 0.0)
        assertEquals(2.0, d.baselineDisplayValue!!, 0.0)
        assertEquals(3, d.weeks)
        assertEquals("Do a 5-minute breathing session 4 times a week and review in 3 weeks.", d.smartSentence)

        val unknown = GoalPlanning.draft(GoalPreset.FEEL_STEADIER, GoalBaselines())
        assertNull(unknown.baselineDisplayValue)
        assertEquals(3.0, unknown.targetValue, 0.0)
        assertEquals(3.0, GoalPlanning.draft(GoalPreset.FEEL_STEADIER, GoalBaselines(calmSessionsPerWeek = 0.25)).targetValue, 0.0)
        assertEquals(7.0, GoalPlanning.draft(GoalPreset.FEEL_STEADIER, GoalBaselines(calmSessionsPerWeek = 6.0)).targetValue, 0.0)

        fun sentenceFor(target: Double) =
            GoalPlanning.draft(GoalPreset.FEEL_STEADIER, GoalBaselines(), targetDisplayValue = target).smartSentence
        assertEquals("Do a 5-minute breathing session once a week and review in 3 weeks.", sentenceFor(1.0))
        assertEquals("Do a 5-minute breathing session twice a week and review in 3 weeks.", sentenceFor(2.0))
        assertEquals("Do a 5-minute breathing session 7 times a week and review in 3 weeks.", sentenceFor(10.0))
    }

    @Test
    fun recoverDraft_isTwoMoreRecoverySessionsThanTheUsual() {
        val d = GoalPlanning.draft(GoalPreset.RECOVER_WELL, GoalBaselines(recoverySessionsPerWeek = 2.0))
        assertEquals(4.0, d.targetValue, 0.0)
        assertEquals(GoalType.RECOVERY, d.preset.goalType)
        assertEquals("Do a 10-minute mobility or wind-down session 4 times a week and review in 3 weeks.", d.smartSentence)
        assertEquals(3.0, GoalPlanning.draft(GoalPreset.RECOVER_WELL, GoalBaselines()).targetValue, 0.0)
        assertEquals(7.0, GoalPlanning.draft(GoalPreset.RECOVER_WELL, GoalBaselines(recoverySessionsPerWeek = 9.0)).targetValue, 0.0)
        assertEquals(7.0, GoalPlanning.draft(GoalPreset.RECOVER_WELL, GoalBaselines(), targetDisplayValue = 12.0).targetValue, 0.0)
        assertEquals(5, GoalPlanning.draft(GoalPreset.RECOVER_WELL, GoalBaselines(), weeks = 5).weeks)
    }

    @Test
    fun everyDraft_validatesOk_forAnyInput() {
        val usuals = listOf(null, Double.NaN, -5.0, 0.0, 0.5, 1.0, 2.0, 3.4, 50.0, 69.5, 78.0, 85.0, 99.5, 100.0, 150.0, 1e12)
        val targets = listOf(
            null, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
            -10.0, 0.0, 1.0, 3.0, 6.5, 50.0, 85.0, 100.0, 1000.0,
        )
        val weekOptions = listOf(null, -1, 0, 1, 2, 5, 12, 100)
        GoalPreset.entries.forEach { preset ->
            usuals.forEach { usual ->
                val baselines = GoalBaselines(usual, 7.0, usual, usual, usual)
                targets.forEach { target ->
                    weekOptions.forEach { weeks ->
                        val d = GoalPlanning.draft(preset, baselines, target, weeks)
                        val v = GoalEngine.validate(goalFrom(d))
                        assertTrue("$preset usual=$usual target=$target weeks=$weeks → $v", v is GoalValidation.Ok)
                        assertEquals(preset, d.preset)
                        assertTrue(d.weeks >= 2)
                        assertSafe(d.smartSentence)
                        // The saved gain never asks for more than 100% of the sleep you need.
                        val usualSleep = d.baselineDisplayValue
                        if (preset == GoalPreset.SLEEP_BETTER && usualSleep != null && usualSleep < 100.0) {
                            assertTrue("usual=$usual target=$target", usualSleep * (1 + d.targetValue / 100.0) <= 100.0 + 1e-9)
                        }
                    }
                }
            }
        }
    }

    // --- Week-one preview ----------------------------------------------------

    @Test
    fun sleepPreview_weekOne_usesThePlaybooksOwnActions() {
        val d = GoalPlanning.draft(GoalPreset.SLEEP_BETTER, GoalBaselines(sleepPerformancePercent = 78.0))
        val p = GoalPlanning.preview(d, PlanConstraints())
        assertEquals(
            listOf(
                "In bed at the same time each night",
                title(MetricType.SLEEP_PERFORMANCE, "wind-down"),
                title(MetricType.SLEEP_PERFORMANCE, "morning-light"),
                title(MetricType.SLEEP_PERFORMANCE, "caffeine-cutoff"),
            ),
            p.items.map { it.title },
        )
        assertEquals("Sleep wind-down routine", p.items[1].title)
        assertEquals(listOf(5, 5, 3, 5), p.items.map { it.timesThisWeek })
        assertEquals(listOf(1, 20, 10, 1), p.items.map { it.minutes })
        assertEquals(3, p.actionsPerDay)
        assertEquals(20, p.minutesPerDay) // 140 min over 7 days
        assertEquals("This week: about 3 actions a day, ~20 min a day. Review in a week.", p.summary)
    }

    @Test
    fun sleepPreview_usesTheUsersOwnBedtime() {
        val d = GoalPlanning.draft(GoalPreset.SLEEP_BETTER, GoalBaselines())
        fun firstTitle(bedtime: Int) = GoalPlanning.preview(d, PlanConstraints(bedtimeMinute = bedtime)).items.first().title
        assertEquals("In bed by 10:30 PM", firstTitle(22 * 60 + 30))
        assertEquals("In bed by 12:00 AM", firstTitle(0))
        assertEquals("In bed by 12:05 PM", firstTitle(12 * 60 + 5))
        assertEquals("In bed by 9:00 AM", firstTitle(9 * 60))
        assertEquals("In bed by 11:30 PM", firstTitle(-30)) // out-of-range input wraps instead of crashing
        // The same five-minute rounding the plan's lights-out uses, so the preview never promises 10:33.
        assertEquals("In bed by 10:30 PM", firstTitle(22 * 60 + 33))
    }

    @Test
    fun sleepPreview_followsThe24HourClock() {
        val d = GoalPlanning.draft(GoalPreset.SLEEP_BETTER, GoalBaselines())
        fun firstTitle(bedtime: Int) =
            GoalPlanning.preview(d, PlanConstraints(bedtimeMinute = bedtime), use24HourClock = true).items.first().title
        assertEquals("In bed by 22:30", firstTitle(22 * 60 + 30))
        assertEquals("In bed by 00:00", firstTitle(0))
        assertEquals("In bed by 12:05", firstTitle(12 * 60 + 5))
        assertEquals("In bed by 09:00", firstTitle(9 * 60))
        assertEquals("In bed by 23:30", firstTitle(-30))
        // No bedtime given: the same words on either clock.
        assertEquals(
            "In bed at the same time each night",
            GoalPlanning.preview(d, PlanConstraints(), use24HourClock = true).items.first().title,
        )
    }

    @Test
    fun fitterPreview_splitsZone2AndStrength_plusMobility() {
        val d = GoalPlanning.draft(GoalPreset.GET_FITTER, GoalBaselines(trainingSessionsPerWeek = 2.0))
        val p = GoalPlanning.preview(d, PlanConstraints())
        val zone2 = title(MetricType.HRV_RMSSD, "zone2")
        val strength = title(MetricType.STRENGTH_SESSIONS, "strength")
        val mobility = title(MetricType.RECOVERY_SCORE, "mobility")
        assertEquals(listOf(zone2, strength, mobility), p.items.map { it.title })
        assertEquals(listOf(2, 1, 2), p.items.map { it.timesThisWeek })
        assertEquals(listOf(25, 30, 10), p.items.map { it.minutes })
        assertEquals(1, p.actionsPerDay)
        assertEquals(15, p.minutesPerDay) // 100 min over 7 days ≈ 14 → 15
        assertEquals("This week: about 1 action a day, ~15 min a day. Review in a week.", p.summary)

        val four = GoalPlanning.preview(GoalPlanning.draft(GoalPreset.GET_FITTER, GoalBaselines(), 4.0), PlanConstraints())
        assertEquals(listOf(2, 2, 2), four.items.map { it.timesThisWeek })
        // One session: no empty strength line.
        val single = GoalPlanning.preview(GoalPlanning.draft(GoalPreset.GET_FITTER, GoalBaselines(), 1.0), PlanConstraints())
        assertEquals(listOf(zone2, mobility), single.items.map { it.title })
        assertEquals(listOf(1, 2), single.items.map { it.timesThisWeek })
    }

    @Test
    fun fitterPreview_avoidingStrength_isZone2Only() {
        val d = GoalPlanning.draft(GoalPreset.GET_FITTER, GoalBaselines(), targetDisplayValue = 4.0)
        val p = GoalPlanning.preview(d, PlanConstraints(avoid = setOf(AvoidOption.STRENGTH_TRAINING)))
        assertEquals(listOf(title(MetricType.HRV_RMSSD, "zone2"), title(MetricType.RECOVERY_SCORE, "mobility")), p.items.map { it.title })
        assertEquals(listOf(4, 2), p.items.map { it.timesThisWeek })
        assertTrue(p.items.none { it.title == title(MetricType.STRENGTH_SESSIONS, "strength") })
    }

    @Test
    fun steadierPreview_breathingFirst_orWalksWhenBreathworkIsOff() {
        val d = GoalPlanning.draft(GoalPreset.FEEL_STEADIER, GoalBaselines(calmSessionsPerWeek = 2.0))
        val breathing = title(MetricType.HRV_RMSSD, "breathing")
        val walk = title(MetricType.RECOVERY_SCORE, "easy-walk")
        val windDown = title(MetricType.SLEEP_PERFORMANCE, "wind-down")

        val p = GoalPlanning.preview(d, PlanConstraints())
        assertEquals(listOf(breathing, walk, windDown), p.items.map { it.title })
        assertEquals(listOf(4, 3, 3), p.items.map { it.timesThisWeek })
        assertEquals(2, p.actionsPerDay)
        assertEquals(20, p.minutesPerDay) // 125 min over 7 days ≈ 18 → 20

        assertFalse(p.summary.contains("counts breathing sessions"))

        // The walks standing in for breathing join the other walks as one line.
        val noBreathing = GoalPlanning.preview(d, PlanConstraints(avoid = setOf(AvoidOption.BREATHWORK)))
        assertEquals(listOf(walk, windDown), noBreathing.items.map { it.title })
        assertEquals(listOf(7, 3), noBreathing.items.map { it.timesThisWeek })
        assertEquals(25, noBreathing.minutesPerDay)
        // ...but they never count toward a breathing goal, and the preview says so.
        assertEquals(
            "This week: about 2 actions a day, ~25 min a day. Review in a week. This goal counts breathing " +
                "sessions, which you'd rather skip — the walks won't count toward it, so a different goal may fit better.",
            noBreathing.summary,
        )
    }

    @Test
    fun conflicts_onlyWhenTheGoalCountsSessionsTheUserRuledOut() {
        val noBreathing = PlanConstraints(avoid = setOf(AvoidOption.BREATHWORK))
        assertTrue(GoalPlanning.conflicts(GoalPreset.FEEL_STEADIER, noBreathing))
        assertTrue(GoalPlanning.conflicts(GoalPreset.FEEL_STEADIER, PlanConstraints(avoid = AvoidOption.entries.toSet())))
        assertFalse(GoalPlanning.conflicts(GoalPreset.FEEL_STEADIER, PlanConstraints()))
        assertFalse(GoalPlanning.conflicts(GoalPreset.FEEL_STEADIER, PlanConstraints(avoid = setOf(AvoidOption.STRENGTH_TRAINING))))
        // Other goals count sessions that never rely on breathing exercises.
        GoalPreset.entries.filterNot { it == GoalPreset.FEEL_STEADIER }.forEach {
            assertFalse(it.name, GoalPlanning.conflicts(it, noBreathing))
        }
    }

    @Test
    fun recoverPreview_mobilityWindDownAndWalks() {
        val d = GoalPlanning.draft(GoalPreset.RECOVER_WELL, GoalBaselines(recoverySessionsPerWeek = 2.0))
        val p = GoalPlanning.preview(d, PlanConstraints())
        assertEquals(
            listOf(
                title(MetricType.RECOVERY_SCORE, "mobility"),
                title(MetricType.SLEEP_PERFORMANCE, "wind-down"),
                title(MetricType.RECOVERY_SCORE, "easy-walk"),
            ),
            p.items.map { it.title },
        )
        assertEquals(listOf(4, 4, 3), p.items.map { it.timesThisWeek })
        assertEquals(2, p.actionsPerDay)
        assertEquals(25, p.minutesPerDay)
        assertEquals("This week: about 2 actions a day, ~25 min a day. Review in a week.", p.summary)
    }

    @Test
    fun preview_trimsFromTheEnd_toFitTheTimeBudget() {
        val d = GoalPlanning.draft(GoalPreset.RECOVER_WELL, GoalBaselines(recoverySessionsPerWeek = 2.0))
        val p = GoalPlanning.preview(d, PlanConstraints(minutesPerDay = 20))
        assertEquals(
            listOf(title(MetricType.RECOVERY_SCORE, "mobility"), title(MetricType.SLEEP_PERFORMANCE, "wind-down")),
            p.items.map { it.title },
        )
        assertEquals(15, p.minutesPerDay)
        assertEquals(
            "This week: about 2 actions a day, ~15 min a day. Review in a week. Trimmed to fit your 20 minutes a day.",
            p.summary,
        )
        // Within budget (or exactly on it), and a zero budget, change nothing.
        listOf(25, 30, 0).forEach { budget ->
            val untouched = GoalPlanning.preview(d, PlanConstraints(minutesPerDay = budget))
            assertEquals(3, untouched.items.size)
            assertFalse(untouched.summary.contains("Trimmed"))
        }
        // Sleep trims down to the bedtime anchor, which is never dropped.
        val sleep = GoalPlanning.preview(GoalPlanning.draft(GoalPreset.SLEEP_BETTER, GoalBaselines()), PlanConstraints(minutesPerDay = 10))
        assertEquals(listOf("In bed at the same time each night"), sleep.items.map { it.title })
        assertEquals(5, sleep.minutesPerDay) // never below five
        assertTrue(sleep.summary.endsWith(" Trimmed to fit your 10 minutes a day."))
    }

    @Test
    fun preview_neverDropsTheFirstAction_andSaysWhenTheTargetIsTooBig() {
        val d = GoalPlanning.draft(GoalPreset.GET_FITTER, GoalBaselines(), targetDisplayValue = 6.0)
        val p = GoalPlanning.preview(d, PlanConstraints(minutesPerDay = 10, avoid = setOf(AvoidOption.STRENGTH_TRAINING)))
        assertEquals(listOf(title(MetricType.HRV_RMSSD, "zone2")), p.items.map { it.title })
        assertEquals(6, p.items.single().timesThisWeek)
        assertEquals(20, p.minutesPerDay)
        assertEquals(
            "This week: about 1 action a day, ~20 min a day. Review in a week. Trimmed to the main action — " +
                "it still takes more than your 10 minutes a day, so a smaller target may fit better.",
            p.summary,
        )
        val tiny = GoalPlanning.preview(GoalPlanning.draft(GoalPreset.SLEEP_BETTER, GoalBaselines()), PlanConstraints(minutesPerDay = 1))
        assertEquals(1, tiny.items.size)
        assertTrue(tiny.summary.contains("your 1 minute a day"))
    }

    @Test
    fun preview_copyIsSafe_andAlwaysAPlan() {
        val avoids = listOf(emptySet(), setOf(AvoidOption.STRENGTH_TRAINING), setOf(AvoidOption.BREATHWORK), AvoidOption.entries.toSet())
        GoalPreset.entries.forEach { preset ->
            listOf(1.0, 3.0, 7.0).forEach { target ->
                val d = GoalPlanning.draft(preset, GoalBaselines(), targetDisplayValue = target)
                avoids.forEach { avoid ->
                    listOf(null, 10, 20, 45).forEach { budget ->
                        val p = GoalPlanning.preview(d, PlanConstraints(minutesPerDay = budget, bedtimeMinute = 22 * 60, avoid = avoid))
                        assertTrue(p.items.isNotEmpty())
                        assertTrue(p.actionsPerDay >= 1)
                        assertTrue(p.minutesPerDay >= 5 && p.minutesPerDay % 5 == 0)
                        assertSafe(p.summary)
                        p.items.forEach { assertSafe(it.title); assertTrue(it.timesThisWeek > 0) }
                    }
                }
            }
        }
    }

    // --- Weekly re-plan ------------------------------------------------------

    @Test
    fun weeklyReview_isDueAWeekAfterTheStartOrTheLastCheckIn() {
        val started7 = goal(GoalType.FITNESS, 3.0, weeks = 4, start = today - 7)
        assertTrue(GoalPlanning.isWeeklyReviewDue(started7, lastReviewEpochDay = null, todayEpochDay = today))
        assertFalse(GoalPlanning.isWeeklyReviewDue(goal(GoalType.FITNESS, 3.0, start = today - 6), null, today))
        assertFalse(GoalPlanning.isWeeklyReviewDue(null, null, today))

        val started10 = goal(GoalType.SLEEP_RECOVERY, 10.0, start = today - 10)
        assertFalse(GoalPlanning.isWeeklyReviewDue(started10, lastReviewEpochDay = today - 3, todayEpochDay = today))
        assertTrue(GoalPlanning.isWeeklyReviewDue(started10, lastReviewEpochDay = today - 7, todayEpochDay = today))
    }

    @Test
    fun weeklyReview_skipsPausedOrFinishedGoals_andIgnoresAnEarlierGoalsCheckIn() {
        val base = goal(GoalType.RECOVERY, 4.0, start = today - 14)
        assertFalse(GoalPlanning.isWeeklyReviewDue(base.copy(status = GoalStatus.PAUSED), null, today))
        assertFalse(GoalPlanning.isWeeklyReviewDue(base.copy(status = GoalStatus.COMPLETED), null, today))
        assertFalse(GoalPlanning.isWeeklyReviewDue(base.copy(status = GoalStatus.ABANDONED), null, today))
        // A check-in from a goal that ended a month ago does not make a 3-day-old goal due.
        val fresh = goal(GoalType.RECOVERY, 4.0, start = today - 3)
        assertFalse(GoalPlanning.isWeeklyReviewDue(fresh, lastReviewEpochDay = today - 30, todayEpochDay = today))
        val weekOld = goal(GoalType.RECOVERY, 4.0, start = today - 7)
        assertTrue(GoalPlanning.isWeeklyReviewDue(weekOld, lastReviewEpochDay = today - 30, todayEpochDay = today))
    }

    // --- GoalEngine: fitness and recovery goals ------------------------------

    @Test
    fun fitnessGoal_aboveSixSessions_isCounterOfferedWithRestDays() {
        val v = GoalEngine.validate(goal(GoalType.FITNESS, 8.0))
        assertTrue(v is GoalValidation.Adjusted)
        v as GoalValidation.Adjusted
        assertEquals(6.0, v.adjusted.targetValue, 0.0)
        assertEquals(today + 21, v.adjusted.targetEpochDay)
        assertEquals("Rest days are where fitness is built — Spartan plans at most 6 sessions a week.", v.why)
        assertSafe(v.why)
        assertTrue(GoalEngine.validate(goal(GoalType.FITNESS, 6.0)) is GoalValidation.Ok)
        assertTrue(GoalEngine.validate(goal(GoalType.FITNESS, 0.0)) is GoalValidation.Invalid)
    }

    @Test
    fun recoveryGoal_aboveSevenSessions_isCounterOfferedAtOneADay() {
        val v = GoalEngine.validate(goal(GoalType.RECOVERY, 9.0))
        assertTrue(v is GoalValidation.Adjusted)
        v as GoalValidation.Adjusted
        assertEquals(7.0, v.adjusted.targetValue, 0.0)
        assertEquals("One recovery session a day is plenty — Spartan plans at most 7 a week.", v.why)
        assertSafe(v.why)
        assertTrue(GoalEngine.validate(goal(GoalType.RECOVERY, 7.0)) is GoalValidation.Ok)
    }

    @Test
    fun goalModifiers_fitnessAddsZone2AndStrength_recoveryLeansOnWindDown() {
        val windows = listOf(PressureWindow("w1", daysOfWeekMask = 0b0001010, startMinuteOfDay = 660, endMinuteOfDay = 720))
        val fitness = GoalEngine.planModifiers(goal(GoalType.FITNESS, 3.0), windows)
        assertEquals(30, fitness.extraZone2MinutesPerWeek)
        assertEquals(2, fitness.protectStrengthSessions)
        assertFalse(fitness.emphasizeWindDown)
        assertEquals(windows, fitness.preWindowBreathwork)

        val recovery = GoalEngine.planModifiers(goal(GoalType.RECOVERY, 4.0), windows)
        assertTrue(recovery.emphasizeWindDown)
        assertEquals(0, recovery.extraZone2MinutesPerWeek)
        assertEquals(0, recovery.protectStrengthSessions)
        assertEquals(windows, recovery.preWindowBreathwork)

        assertEquals(windows, GoalEngine.planModifiers(goal(GoalType.STRESS_RESILIENCE, 5.0), windows).preWindowBreathwork)
    }

    @Test
    fun fitnessProgress_countsTrainingSessions_withRestingHrAsContextOnly() {
        val noBaseline = goal(GoalType.FITNESS, 3.0, weeks = 4, start = today - 7)
        val plain = GoalEngine.progress(noBaseline, emptyList(), today, trainingSessionsThisWeek = 2)
        assertEquals(2.0 / 3.0, plain.fraction, 0.0001)
        assertTrue(plain.onTrack)
        assertEquals("2 of 3 training sessions this week", plain.summary)

        val withBaseline = noBaseline.copy(baselineValue = 62.0)
        val down = GoalEngine.progress(withBaseline, week(MetricType.RESTING_HEART_RATE, 60.0), today, trainingSessionsThisWeek = 2)
        assertEquals("2 of 3 training sessions this week · resting HR -2.0 bpm vs baseline", down.summary)
        assertEquals(plain.fraction, down.fraction, 0.0) // context never moves the score
        val up = GoalEngine.progress(withBaseline, week(MetricType.RESTING_HEART_RATE, 63.5), today, trainingSessionsThisWeek = 2)
        assertEquals("2 of 3 training sessions this week · resting HR +1.5 bpm vs baseline", up.summary)
        assertEquals(plain.onTrack, up.onTrack)
        // A baseline without fresh readings: no note rather than a guess.
        assertEquals("2 of 3 training sessions this week", GoalEngine.progress(withBaseline, emptyList(), today, trainingSessionsThisWeek = 2).summary)

        // Other habits' counts never leak into this goal.
        val none = GoalEngine.progress(noBaseline, emptyList(), today, breathworkThisWeek = 5, recoverySessionsThisWeek = 5)
        assertEquals(0.0, none.fraction, 0.0)
        assertFalse(none.onTrack) // a quarter of the way in with nothing logged yet
        assertEquals("0 of 3 training sessions this week", none.summary)
        assertSafe(down.summary)
    }

    @Test
    fun recoveryProgress_countsRecoverySessions_withRecoveryAsContextOnly() {
        val g = goal(GoalType.RECOVERY, 4.0, weeks = 3, baseline = 55.0)
        val p = GoalEngine.progress(g, week(MetricType.RECOVERY_SCORE, 60.0), today, recoverySessionsThisWeek = 3)
        assertEquals(0.75, p.fraction, 0.0001)
        assertTrue(p.onTrack)
        assertEquals("3 of 4 recovery sessions this week · recovery +5.0% vs baseline", p.summary)
        assertSafe(p.summary)
        assertEquals(1.0, GoalEngine.progress(g, emptyList(), today, recoverySessionsThisWeek = 6).fraction, 0.0)
        assertEquals(
            "3 of 4 recovery sessions this week",
            GoalEngine.progress(g.copy(baselineValue = null), week(MetricType.RECOVERY_SCORE, 60.0), today, recoverySessionsThisWeek = 3).summary,
        )
    }

    @Test
    fun stressProgress_positionalCallersStillWork_andKeepTheHrvNote() {
        val g = goal(GoalType.STRESS_RESILIENCE, 5.0, baseline = 35.0)
        val p = GoalEngine.progress(g, week(MetricType.HRV_RMSSD, 40.0), today, 3)
        assertEquals(0.6, p.fraction, 0.0001)
        assertEquals("3 of 5 calm sessions this week · HRV +5.0 ms vs baseline", p.summary)
        // Outcome goals are untouched by the habit counters.
        val sleep = goal(GoalType.SLEEP_RECOVERY, 10.0, baseline = 70.0, start = today - 7)
        val s = GoalEngine.progress(sleep, week(MetricType.SLEEP_PERFORMANCE, 77.0), today, 3, 3, 3)
        assertEquals(1.0, s.fraction, 0.01)
    }

    @Test
    fun habitGoals_measureContextMetrics() {
        assertEquals(MetricType.RESTING_HEART_RATE, GoalEngine.metricFor(GoalType.FITNESS))
        assertEquals(MetricType.RECOVERY_SCORE, GoalEngine.metricFor(GoalType.RECOVERY))
        val rhr = (0L..13L).map { reading(MetricType.RESTING_HEART_RATE, 61.0, it) }
        assertEquals(61.0, GoalEngine.baselineFor(GoalType.FITNESS, rhr, today)!!, 0.0001)
        assertNull(GoalEngine.baselineFor(GoalType.RECOVERY, rhr, today))
    }
}
