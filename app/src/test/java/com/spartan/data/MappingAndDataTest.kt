package com.spartan.data

import com.spartan.data.calendar.AvailabilityService
import com.spartan.data.local.toDomain
import com.spartan.data.local.toEntity
import com.spartan.data.whoop.WhoopMapper
import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.DailyPlan
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.ReadinessBand
import com.spartan.domain.model.ReadinessSnapshot
import com.spartan.domain.model.TimeOfDay
import com.spartan.domain.model.TimeWindow
import com.spartan.domain.model.WhoopSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MappingAndDataTest {

    @Test
    fun dailyActivity_roundTripsThroughPersistence() {
        val original = DailyActivity(
            id = "20000:mobility",
            title = "10-minute mobility flow",
            category = ActivityCategory.MOBILITY,
            priority = ActivityPriority.REQUIRED,
            whyItMatters = "Gentle movement supports recovery.",
            relatedMetric = MetricType.RECOVERY_SCORE,
            instructions = listOf("Move hips and ankles.", "Breathe easily."),
            estimatedMinutes = 10,
            intensity = Intensity.EASY,
            bestTimeOfDay = TimeOfDay.MORNING,
            status = ActivityStatus.SNOOZED,
            ruleId = "LOW_RECOVERY",
            scheduledEpochMinute = 123_456L,
            completedAtMillis = null,
            snoozedUntilMillis = 999_999L,
            safetyNote = "Stop if painful.",
        )
        val restored = original.toEntity(dateEpochDay = 20_000L).toDomain()
        assertEquals(original, restored)
    }

    @Test
    fun dailyActivity_emptyInstructionsRoundTripToEmptyList() {
        val a = DailyActivity(
            id = "d:x", title = "t", category = ActivityCategory.HYDRATION, priority = ActivityPriority.OPTIONAL,
            whyItMatters = "w", instructions = emptyList(), estimatedMinutes = 1, intensity = Intensity.REST,
            bestTimeOfDay = TimeOfDay.ANYTIME, ruleId = "HYDRATION_BASELINE",
        )
        val entity = a.toEntity(20_000L)
        assertEquals("", entity.instructions)
        assertTrue(entity.toDomain().instructions.isEmpty())
    }

    @Test
    fun whoopMapper_labelsRealVsSample_andSkipsNulls() {
        val real = WhoopSnapshot(dateEpochDay = 20_000L, recoveryScore = 55, hrvMs = 60.0, isMock = false)
        val readings = WhoopMapper.toReadings(real)
        assertTrue(readings.any { it.type == MetricType.RECOVERY_SCORE && it.value == 55.0 })
        assertTrue(readings.all { it.note == "WHOOP" })
        // No sleep/strain provided -> those readings are absent, not zero.
        assertFalse(readings.any { it.type == MetricType.DAY_STRAIN })

        val sample = WhoopSnapshot(dateEpochDay = 20_000L, recoveryScore = 42, isMock = true)
        assertTrue(WhoopMapper.toReadings(sample).all { it.note.contains("sample", ignoreCase = true) })

        val empty = WhoopSnapshot(dateEpochDay = 20_000L)
        assertTrue(WhoopMapper.toReadings(empty).isEmpty())
    }

    // --- Availability edge cases ---
    private val svc = AvailabilityService()

    @Test
    fun availability_fullyBusyDayHasNoOpenWindows() {
        val open = svc.openWindows(0, 480, listOf(TimeWindow(0, 480)))
        assertTrue(open.isEmpty())
        assertEquals(null, svc.suggestSlot(30, 0, 480, listOf(TimeWindow(0, 480))))
    }

    @Test
    fun availability_zeroLengthDayIsEmpty() {
        assertTrue(svc.openWindows(100, 100, emptyList()).isEmpty())
    }

    @Test
    fun availability_exactFitSlotIsFound() {
        val slot = svc.suggestSlot(60, 0, 60, emptyList())
        assertEquals(TimeWindow(0, 60), slot)
    }

    @Test
    fun availability_adjacentBusyBlocksMergeAndDoNotLeaveSlivers() {
        // 0-30 and 30-60 busy back-to-back leave a single open window 60-120.
        val open = svc.openWindows(0, 120, listOf(TimeWindow(0, 30), TimeWindow(30, 60)))
        assertEquals(listOf(TimeWindow(60, 120)), open)
    }

    @Test
    fun availability_respectsMinWindow() {
        // Gaps shorter than the activity are skipped.
        val slot = svc.suggestSlot(40, 0, 200, listOf(TimeWindow(20, 60), TimeWindow(90, 130)))
        // open windows: 0-20 (too short), 60-90 (too short), 130-200 (fits) -> 130..170
        assertEquals(TimeWindow(130, 170), slot)
    }

    // --- ReadinessSnapshot.from ---
    @Test
    fun readiness_withNoHistoryHasNoTrendsAndBalancedFallbackForNullRecovery() {
        val today = WhoopSnapshot(dateEpochDay = 20_000L, recoveryScore = null, hrvMs = 55.0)
        val snap = ReadinessSnapshot.from(today, emptyList())
        assertEquals(ReadinessBand.BALANCED, snap.band)
        assertTrue(snap.isStale)
        assertEquals(null, snap.hrvVsBaseline)
        assertTrue(snap.trendNotes.isEmpty())
    }

    // --- DailyPlan math ---
    @Test
    fun dailyPlan_progressAndTotals() {
        fun act(id: String, min: Int, status: ActivityStatus) = DailyActivity(
            id = id, title = id, category = ActivityCategory.MOVEMENT, priority = ActivityPriority.OPTIONAL,
            whyItMatters = "w", instructions = emptyList(), estimatedMinutes = min, intensity = Intensity.EASY,
            bestTimeOfDay = TimeOfDay.ANYTIME, ruleId = "R", status = status,
        )
        val plan = DailyPlan(
            dateEpochDay = 20_000L, headline = "h", band = ReadinessBand.BALANCED,
            activities = listOf(
                act("a", 10, ActivityStatus.DONE),
                act("b", 15, ActivityStatus.PLANNED),
                act("c", 5, ActivityStatus.SKIPPED),
                act("d", 20, ActivityStatus.PLANNED),
            ),
        )
        assertEquals(50, plan.totalEstimatedMinutes)
        assertEquals(1, plan.completedCount)
        assertEquals(25, plan.progressPercent)
    }
}
