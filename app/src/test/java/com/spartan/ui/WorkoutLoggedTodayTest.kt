package com.spartan.ui

import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.TimeOfDay
import com.spartan.domain.model.WorkoutLog
import com.spartan.domain.model.WorkoutType
import com.spartan.ui.screens.debriefRepeatsLog
import com.spartan.ui.screens.workoutTypeFor
import com.spartan.ui.screens.workoutsLoggedOn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Today's check-off debrief and Coach's workout completion both log sessions. Both seams share one
 * category→type mapping and flag a repeat log of the same real session before saving it.
 */
class WorkoutLoggedTodayTest {

    private val today = LocalDate.of(2026, 9, 25)

    private fun log(type: WorkoutType, day: LocalDate = today) =
        WorkoutLog(type, plannedMinutes = 30, completedMinutes = 30, rpe = 5, painFlag = false, completedAt = day)

    private fun activity(id: String, category: ActivityCategory, status: ActivityStatus = ActivityStatus.PLANNED) =
        DailyActivity(
            id = id,
            title = id,
            category = category,
            priority = ActivityPriority.RECOMMENDED,
            whyItMatters = "",
            estimatedMinutes = 10,
            intensity = Intensity.EASY,
            bestTimeOfDay = TimeOfDay.ANYTIME,
            status = status,
            ruleId = "test",
        )

    @Test
    fun checkOffCategoriesMapToTheirWorkoutType() {
        assertEquals(WorkoutType.STRENGTH, workoutTypeFor(ActivityCategory.STRENGTH))
        assertEquals(WorkoutType.MOBILITY, workoutTypeFor(ActivityCategory.MOBILITY))
        assertEquals(WorkoutType.MOBILITY, workoutTypeFor(ActivityCategory.RECOVERY))
        assertEquals(WorkoutType.ZONE_2, workoutTypeFor(ActivityCategory.ZONE2))
        assertEquals(WorkoutType.ZONE_2, workoutTypeFor(ActivityCategory.MOVEMENT))
    }

    @Test
    fun onlyTodaysLogsCount() {
        val logs = listOf(log(WorkoutType.STRENGTH), log(WorkoutType.STRENGTH), log(WorkoutType.ZONE_2, today.minusDays(1)))
        assertEquals(mapOf(WorkoutType.STRENGTH to 2), workoutsLoggedOn(logs, today))
        assertEquals(emptyMap<WorkoutType, Int>(), workoutsLoggedOn(emptyList(), today))
    }

    @Test
    fun secondPlannedSessionOfTheSameTypeIsNotADoubleLog() {
        // Low-recovery plans can hold two mobility items; the first was checked off and debriefed.
        val flow = activity("flow", ActivityCategory.MOBILITY, ActivityStatus.DONE)
        val stretch = activity("stretch", ActivityCategory.MOBILITY, ActivityStatus.DONE)
        val logged = mapOf(WorkoutType.MOBILITY to 1)
        assertFalse(debriefRepeatsLog(stretch, listOf(flow, stretch), logged))
    }

    @Test
    fun sessionAlreadyLoggedFromCoachIsFlagged() {
        val walk = activity("walk", ActivityCategory.ZONE2, ActivityStatus.DONE)
        assertTrue(debriefRepeatsLog(walk, listOf(walk), mapOf(WorkoutType.ZONE_2 to 1)))
        assertFalse(debriefRepeatsLog(walk, listOf(walk), emptyMap()))
    }

    @Test
    fun reCheckingAnAlreadyDebriefedActivityIsFlagged() {
        // Unchecked and checked again: its earlier log is still there and nothing else explains it.
        val strength = activity("strength", ActivityCategory.STRENGTH, ActivityStatus.DONE)
        assertTrue(debriefRepeatsLog(strength, listOf(strength), mapOf(WorkoutType.STRENGTH to 1)))
    }

    @Test
    fun recoveryCheckOffsNeverExplainALog() {
        // RECOVERY items don't open the debrief, so they can't account for a mobility log.
        val breathwork = activity("breathwork", ActivityCategory.RECOVERY, ActivityStatus.DONE)
        val flow = activity("flow", ActivityCategory.MOBILITY, ActivityStatus.DONE)
        assertTrue(debriefRepeatsLog(flow, listOf(breathwork, flow), mapOf(WorkoutType.MOBILITY to 1)))
    }
}
