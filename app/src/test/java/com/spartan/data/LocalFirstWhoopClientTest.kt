package com.spartan.data

import com.spartan.data.local.WhoopCycleDao
import com.spartan.data.local.WhoopCycleEntity
import com.spartan.data.local.WhoopImportInfoRow
import com.spartan.data.local.WhoopWorkoutEntity
import com.spartan.data.whoop.LocalFirstWhoopClient
import com.spartan.data.whoop.MockWhoopClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFirstWhoopClientTest {

    private class FakeWhoopCycleDao : WhoopCycleDao {
        val cycles = mutableListOf<WhoopCycleEntity>()
        override suspend fun upsertCycles(cycles: List<WhoopCycleEntity>) { this.cycles += cycles }
        override suspend fun upsertWorkouts(workouts: List<WhoopWorkoutEntity>) = Unit
        override suspend fun latestCycles(limit: Int): List<WhoopCycleEntity> =
            cycles.sortedByDescending { it.dateEpochDay }.take(limit)
        override suspend fun cycleCount(): Int = cycles.size
        override fun observeWorkouts(): Flow<List<WhoopWorkoutEntity>> = MutableStateFlow(emptyList())
        override fun observeImportInfo(): Flow<WhoopImportInfoRow> = MutableStateFlow(
            WhoopImportInfoRow(
                dayCount = cycles.size,
                firstDay = cycles.minOfOrNull { it.dateEpochDay },
                lastDay = cycles.maxOfOrNull { it.dateEpochDay },
            ),
        )
    }

    private fun cycle(day: Long, recovery: Int) = WhoopCycleEntity(dateEpochDay = day, recoveryScore = recovery)

    @Test
    fun withoutImportedData_delegatesToMock_andStaysLabeledSample() = runTest {
        val client = LocalFirstWhoopClient(MockWhoopClient(), FakeWhoopCycleDao())
        assertTrue(client.isMock)
        val days = client.fetchRecentDays(7)
        assertEquals(7, days.size)
        assertTrue(days.all { it.isMock })
        assertTrue(client.isMock)
    }

    @Test
    fun withImportedData_servesRealData_oldestFirst_neverLabeledSample() = runTest {
        val dao = FakeWhoopCycleDao()
        dao.upsertCycles((1L..20L).map { cycle(day = 1000 + it, recovery = 50 + it.toInt()) })
        val client = LocalFirstWhoopClient(MockWhoopClient(), dao)

        val days = client.fetchRecentDays(7)
        assertEquals(7, days.size)
        // The most recent 7 imported days, oldest first.
        assertEquals((1014L..1020L).toList(), days.map { it.dateEpochDay })
        assertTrue(days.zipWithNext().all { (a, b) -> a.dateEpochDay < b.dateEpochDay })
        assertTrue(days.none { it.isMock })
        assertFalse(client.isMock)
        assertEquals(70, days.last().recoveryScore)
    }

    @Test
    fun importArrivingBetweenFetches_switchesSourceOnNextFetch() = runTest {
        val dao = FakeWhoopCycleDao()
        val client = LocalFirstWhoopClient(MockWhoopClient(), dao)
        assertTrue(client.fetchRecentDays(7).all { it.isMock })

        dao.upsertCycles(listOf(cycle(day = 2000, recovery = 80)))
        val afterImport = client.fetchRecentDays(7)
        assertEquals(1, afterImport.size)
        assertFalse(afterImport.single().isMock)
        assertFalse(client.isMock)
    }
}
