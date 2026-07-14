package com.spartan.domain

import com.spartan.domain.model.ReadinessSnapshot
import com.spartan.domain.model.WhoopSnapshot
import com.spartan.domain.usecase.DailyPlanSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Imported CSV data (and a lagging real API) can end before today. The plan must still land on
 * the day being planned — carried forward and clearly marked stale so the coaching engine eases
 * intensity and says why.
 */
class DailyPlanSyncCarryForwardTest {

    private fun readinessFor(day: Long): ReadinessSnapshot =
        ReadinessSnapshot.from(WhoopSnapshot(dateEpochDay = day, recoveryScore = 80))

    @Test
    fun dataForToday_isUntouched() {
        val readiness = readinessFor(20000)
        val result = DailyPlanSync.carryForward(readiness, targetEpochDay = 20000)
        assertEquals(readiness, result)
        assertFalse(result.isStale)
    }

    @Test
    fun olderData_isCarriedToTargetDay_andMarkedStale() {
        val result = DailyPlanSync.carryForward(readinessFor(19995), targetEpochDay = 20000)
        assertEquals(20000, result.dateEpochDay)
        assertTrue(result.isStale)
        // The underlying signals survive so the plan is still personal, just softened.
        assertEquals(80, result.recoveryScore)
    }
}
