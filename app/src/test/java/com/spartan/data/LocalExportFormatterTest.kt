package com.spartan.data

import com.spartan.data.export.LocalExportFormatter
import com.spartan.data.local.DailyReflectionEntity
import com.spartan.data.local.GoalEntity
import com.spartan.data.local.MetricEntryEntity
import com.spartan.data.local.PressureWindowEntity
import com.spartan.data.local.ReminderEntity
import com.spartan.data.local.ReminderFrequency
import com.spartan.data.local.TargetEntity
import com.spartan.data.local.UserProfileEntity
import com.spartan.data.local.WorkoutSessionEntity
import com.spartan.domain.engine.AvoidOption
import com.spartan.domain.engine.GoalStatus
import com.spartan.domain.engine.GoalType
import com.spartan.domain.engine.PlanConstraints
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.WorkoutType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LocalExportFormatterTest {
    @Test
    fun format_escapesCommasQuotesAndNewlines() {
        val export = LocalExportFormatter.format(
            profile = UserProfileEntity(displayName = "Ada, Local"),
            metrics = listOf(
                MetricEntryEntity(
                    id = 1,
                    type = MetricType.FASTING_GLUCOSE,
                    value = 108.0,
                    unit = "mg/dL",
                    note = "before breakfast,\nrepeat \"soon\"",
                    recordedAtEpochDay = LocalDate.of(2026, 5, 11).toEpochDay(),
                    createdAtMillis = 42,
                ),
            ),
            targets = listOf(TargetEntity(metricType = MetricType.FASTING_GLUCOSE, maxValue = 99.0, note = "clinical separate")),
            workouts = listOf(WorkoutSessionEntity(type = WorkoutType.ZONE_2, plannedMinutes = 30, completedMinutes = 30, rpe = 5, painFlag = false, completedAtEpochDay = LocalDate.of(2026, 5, 11).toEpochDay())),
            reminders = listOf(ReminderEntity("logging", "Log", "Local reminder", 20, 30, true, ReminderFrequency.WEEKDAYS, 31)),
        )

        assertTrue(export.contains("\"Ada, Local\""))
        assertTrue(export.contains("\"before breakfast,\nrepeat \"\"soon\"\"\""))
        assertTrue(export.contains("frequency,daysOfWeekMask"))
        assertTrue(export.contains("WEEKDAYS,31"))
    }

    private fun exportWith(
        goals: List<GoalEntity> = emptyList(),
        windows: List<PressureWindowEntity> = emptyList(),
        constraints: PlanConstraints = PlanConstraints(),
        reflections: List<DailyReflectionEntity> = emptyList(),
        lastGoalReviewDay: Long? = null,
    ) = LocalExportFormatter.format(
        profile = null,
        metrics = emptyList(),
        targets = emptyList(),
        workouts = emptyList(),
        reminders = emptyList(),
        goals = goals,
        pressureWindows = windows,
        planConstraints = constraints,
        reflections = reflections,
        lastGoalReviewDay = lastGoalReviewDay,
    )

    @Test
    fun format_includesGoalsAndPressureWindows() {
        val export = exportWith(
            goals = listOf(
                GoalEntity(
                    id = "g1", type = GoalType.FITNESS, targetValue = 3.0, baselineValue = 62.0,
                    startEpochDay = 20000, targetEpochDay = 20028, status = GoalStatus.PAUSED,
                ),
            ),
            windows = listOf(PressureWindowEntity(id = "w1", daysOfWeekMask = 10, startMinuteOfDay = 660, endMinuteOfDay = 720, label = "Standup")),
        )

        assertTrue(export.contains("[Goals]"))
        assertTrue(export.contains("FITNESS target=3.0 baseline=62.0 days=20000..20028 status=PAUSED"))
        assertTrue(export.contains("[Pressure windows]"))
        assertTrue(export.contains("mask=10 660-720 Standup"))
    }

    @Test
    fun format_includesPlanSettingsAsTheirOwnSection() {
        val export = exportWith(
            constraints = PlanConstraints(
                minutesPerDay = 30,
                limitsNote = "left knee, no jumping",
                bedtimeMinute = 22 * 60 + 30,
                wakeMinute = 6 * 60 + 5,
                avoid = setOf(AvoidOption.MORNING_SESSIONS, AvoidOption.STRENGTH_TRAINING),
            ),
        )

        assertTrue(export.contains("[Plan settings]"))
        assertTrue(export.contains("minutesPerDay,bedtime,wakeTime,wontDo,limitsNote"))
        // Won't-dos in a stable order; the free-text note is CSV-escaped like every other field.
        assertTrue(export.contains("30,22:30,06:05,STRENGTH_TRAINING;MORNING_SESSIONS,\"left knee, no jumping\""))
    }

    @Test
    fun format_omitsPlanSettingsWhenNothingWasGiven() {
        assertFalse(exportWith().contains("[Plan settings]"))
        assertFalse(exportWith(constraints = PlanConstraints(limitsNote = "   ")).contains("[Plan settings]"))
    }

    @Test
    fun format_partialPlanSettingsLeaveTheMissingFieldsBlank() {
        val export = exportWith(constraints = PlanConstraints(wakeMinute = 7 * 60))

        assertTrue(export.contains("[Plan settings]"))
        assertTrue(export.contains("\n,,07:00,,\n"))
    }

    @Test
    fun format_includesEveryReflection_withTheNoteCsvEscaped() {
        val export = exportWith(
            reflections = listOf(
                DailyReflectionEntity(LocalDate.of(2026, 10, 21).toEpochDay(), "STRONG", "Slept well, \"finally\"\nfelt calm"),
                DailyReflectionEntity(LocalDate.of(2026, 10, 20).toEpochDay(), "TOUGH", ""),
            ),
        )

        assertTrue(export.contains("[Reflections]"))
        assertTrue(export.contains("date,mood,note"))
        // Oldest first, like a diary; the user's own words survive commas, quotes and line breaks.
        assertTrue(export.contains("2026-10-20,TOUGH,\n2026-10-21,STRONG,\"Slept well, \"\"finally\"\"\nfelt calm\""))
    }

    @Test
    fun format_reflectionsSectionIsPresentEvenWhenEmpty() {
        assertTrue(exportWith().contains("[Reflections]\ndate,mood,note\n"))
    }

    @Test
    fun format_weeklyCheckInDay_onlyWhenThereHasBeenOne() {
        val export = exportWith(lastGoalReviewDay = LocalDate.of(2026, 10, 18).toEpochDay())

        assertTrue(export.contains("[Weekly check-in]\nlastReviewDate\n2026-10-18\n"))
        assertFalse(exportWith().contains("[Weekly check-in]"))
    }
}
