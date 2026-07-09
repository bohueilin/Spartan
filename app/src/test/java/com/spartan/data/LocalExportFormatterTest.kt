package com.spartan.data

import com.spartan.data.export.LocalExportFormatter
import com.spartan.data.local.MetricEntryEntity
import com.spartan.data.local.ReminderEntity
import com.spartan.data.local.ReminderFrequency
import com.spartan.data.local.TargetEntity
import com.spartan.data.local.UserProfileEntity
import com.spartan.data.local.WorkoutSessionEntity
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.WorkoutType
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
}
