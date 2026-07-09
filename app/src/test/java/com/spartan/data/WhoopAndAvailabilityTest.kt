package com.spartan.data

import com.spartan.data.calendar.AvailabilityService
import com.spartan.data.whoop.MockWhoopClient
import com.spartan.data.whoop.WhoopMapper
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.TimeWindow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhoopAndAvailabilityTest {

    @Test
    fun mockWhoopClient_isLabeledSampleData_andReturnsSeries() = runTest {
        val client = MockWhoopClient()
        assertTrue(client.isMock)
        val days = client.fetchRecentDays(7)
        assertEquals(7, days.size)
        assertTrue(days.all { it.isMock })
        // oldest first, today last, strictly increasing dates
        assertTrue(days.zipWithNext().all { (a, b) -> b.dateEpochDay == a.dateEpochDay + 1 })
    }

    @Test
    fun whoopMapper_normalizesSnapshotIntoMetricReadings() = runTest {
        val today = MockWhoopClient().fetchRecentDays(1).single()
        val readings = WhoopMapper.toReadings(today)
        val types = readings.map { it.type }.toSet()
        assertTrue(types.contains(MetricType.RECOVERY_SCORE))
        assertTrue(types.contains(MetricType.HRV_RMSSD))
        assertTrue(types.contains(MetricType.SLEEP_PERFORMANCE))
        assertTrue(types.contains(MetricType.RESTING_HEART_RATE))
        // recovery value round-trips as a Double
        val recovery = readings.first { it.type == MetricType.RECOVERY_SCORE }.value
        assertEquals(42.0, recovery!!, 0.0)
        assertTrue(readings.all { it.note.contains("sample", ignoreCase = true) })
    }

    private val svc = AvailabilityService()

    @Test
    fun openWindows_subtractsAndMergesBusyBlocks() {
        // Working window 0..600 (minutes). Busy: 60-120, 100-180 (overlap -> merge), 300-360.
        val open = svc.openWindows(0, 600, listOf(TimeWindow(60, 120), TimeWindow(100, 180), TimeWindow(300, 360)))
        assertEquals(listOf(TimeWindow(0, 60), TimeWindow(180, 300), TimeWindow(360, 600)), open)
    }

    @Test
    fun suggestSlot_returnsEarliestFittingGapTrimmedToLength() {
        val slot = svc.suggestSlot(30, 0, 600, listOf(TimeWindow(0, 50), TimeWindow(60, 180)))
        // first gap 50..60 is only 10 min; next gap 180..600 fits -> 180..210
        assertNotNull(slot)
        assertEquals(TimeWindow(180, 210), slot)
    }

    @Test
    fun suggestSlot_returnsNullWhenNothingFits() {
        val slot = svc.suggestSlot(120, 0, 100, emptyList())
        assertNull(slot)
    }
}
