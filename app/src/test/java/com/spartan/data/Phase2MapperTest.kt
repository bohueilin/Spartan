package com.spartan.data

import com.spartan.data.calendar.CalendarResponseMapper
import com.spartan.data.calendar.FreeBusyCalendar
import com.spartan.data.calendar.FreeBusyPeriod
import com.spartan.data.calendar.FreeBusyResponse
import com.spartan.data.whoop.WhoopCycleRecord
import com.spartan.data.whoop.WhoopCycleScore
import com.spartan.data.whoop.WhoopRecoveryRecord
import com.spartan.data.whoop.WhoopRecoveryScore
import com.spartan.data.whoop.WhoopResponseMapper
import com.spartan.data.whoop.WhoopSleepNeeded
import com.spartan.data.whoop.WhoopSleepRecord
import com.spartan.data.whoop.WhoopSleepScore
import com.spartan.data.whoop.WhoopStageSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class Phase2MapperTest {

    private val mapper = WhoopResponseMapper(ZoneId.of("UTC"))

    @Test
    fun whoopResponseMapper_joinsRecoverySleepCycleByDay() {
        val recovery = listOf(
            WhoopRecoveryRecord(
                createdAt = "2024-01-02T06:00:00Z", scoreState = "SCORED",
                score = WhoopRecoveryScore(recoveryScore = 44.0, restingHeartRate = 60.0, hrvRmssdMilli = 45.0),
            ),
        )
        val sleep = listOf(
            WhoopSleepRecord(
                start = "2024-01-01T23:00:00Z", end = "2024-01-02T06:30:00Z", nap = false, scoreState = "SCORED",
                score = WhoopSleepScore(
                    sleepPerformancePercentage = 80.0, respiratoryRate = 15.0,
                    stageSummary = WhoopStageSummary(totalInBedMilli = 28_800_000, totalAwakeMilli = 1_800_000),
                    sleepNeeded = WhoopSleepNeeded(needFromSleepDebtMilli = 3_600_000),
                ),
            ),
        )
        val cycle = listOf(
            WhoopCycleRecord(
                start = "2024-01-02T00:00:00Z", scoreState = "SCORED",
                score = WhoopCycleScore(strain = 12.5, kilojoule = 8368.0),
            ),
        )

        val snaps = mapper.toSnapshots(recovery, sleep, cycle)
        assertEquals(1, snaps.size)
        val s = snaps.single()
        assertEquals(LocalDate.of(2024, 1, 2).toEpochDay(), s.dateEpochDay)
        assertEquals(44, s.recoveryScore)
        assertEquals(45.0, s.hrvMs!!, 0.001)
        assertEquals(60.0, s.restingHeartRate!!, 0.001)
        assertEquals(80, s.sleepPerformance)
        assertEquals(7.5, s.sleepDurationHours!!, 0.001)   // (8h in bed - 0.5h awake)
        assertEquals(1.0, s.sleepDebtHours!!, 0.001)
        assertEquals(15.0, s.respiratoryRate!!, 0.001)
        assertEquals(12.5, s.dayStrain!!, 0.001)
        assertEquals(2000.0, s.energyKcal!!, 1.0)          // 8368 kJ * 0.239006
        assertFalse(s.isMock)
    }

    @Test
    fun calendarResponseMapper_parsesBusyPeriodsToTimeWindows() {
        val response = FreeBusyResponse(
            calendars = mapOf(
                "primary" to FreeBusyCalendar(
                    busy = listOf(FreeBusyPeriod(start = "2024-01-02T09:00:00Z", end = "2024-01-02T10:00:00Z")),
                ),
            ),
        )
        val windows = CalendarResponseMapper.toBusyWindows(response)
        assertEquals(1, windows.size)
        val expectedStart = Instant.parse("2024-01-02T09:00:00Z").epochSecond / 60
        assertEquals(expectedStart, windows.single().startEpochMinute)
        assertEquals(60L, windows.single().durationMinutes)
    }
}
