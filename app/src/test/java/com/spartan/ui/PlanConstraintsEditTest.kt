package com.spartan.ui

import androidx.compose.runtime.saveable.SaverScope
import com.spartan.domain.engine.AvoidOption
import com.spartan.domain.engine.GoalPreset
import com.spartan.domain.engine.PlanConstraints
import com.spartan.ui.screens.ConstraintsSaver
import com.spartan.ui.screens.blockedAvoidOptions
import com.spartan.ui.screens.forPlan
import org.junit.Assert.assertEquals
import org.junit.Test

/** The constraints editor shared by the goal wizard and Plan settings: saved state, blocked won't-dos, output. */
class PlanConstraintsEditTest {
    private val scope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    private fun roundTrip(constraints: PlanConstraints): PlanConstraints? {
        val saved = with(ConstraintsSaver) { scope.save(constraints) } ?: return null
        return ConstraintsSaver.restore(saved)
    }

    @Test
    fun saver_roundTripsEveryField() {
        val edited = PlanConstraints(
            minutesPerDay = 20,
            limitsNote = "left knee ",
            bedtimeMinute = 0,
            wakeMinute = 6 * 60 + 45,
            avoid = setOf(AvoidOption.STRENGTH_TRAINING, AvoidOption.MORNING_SESSIONS),
        )
        // The note is kept exactly as typed while editing; only the plan's copy is trimmed.
        assertEquals(edited, roundTrip(edited))
    }

    @Test
    fun saver_keepsNotGivenAsNotGiven() {
        assertEquals(PlanConstraints(), roundTrip(PlanConstraints()))
    }

    @Test
    fun feelSteadier_blocksOnlyBreathing() {
        assertEquals(setOf(AvoidOption.BREATHWORK), blockedAvoidOptions(GoalPreset.FEEL_STEADIER))
        GoalPreset.entries.filter { it != GoalPreset.FEEL_STEADIER }.forEach {
            assertEquals(emptySet<AvoidOption>(), blockedAvoidOptions(it))
        }
        assertEquals(emptySet<AvoidOption>(), blockedAvoidOptions(null))
    }

    @Test
    fun forPlan_trimsTheNoteAndDropsWontDosTheGoalIsBuiltOn() {
        val edited = PlanConstraints(limitsNote = "  sore shoulder  ", avoid = setOf(AvoidOption.BREATHWORK, AvoidOption.MORNING_SESSIONS))
        val planned = edited.forPlan(setOf(AvoidOption.BREATHWORK))
        assertEquals("sore shoulder", planned.limitsNote)
        assertEquals(setOf(AvoidOption.MORNING_SESSIONS), planned.avoid)
        assertEquals(edited.copy(limitsNote = "sore shoulder"), edited.forPlan(emptySet()))
    }
}
