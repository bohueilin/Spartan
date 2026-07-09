package com.spartan.data.calendar

import com.spartan.domain.model.TimeWindow
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * SAMPLE DATA ONLY. Returns a plausible set of busy blocks for today so the scheduling flow works
 * with no Google credentials and no network. Reads are free/busy-shaped (opaque busy ranges, never
 * event titles or details), mirroring the least-privilege contract of the real client.
 */
class StubCalendarClient @Inject constructor() : CalendarClient {

    override val isStub: Boolean = true

    override suspend fun freeBusy(startEpochMinute: Long, endEpochMinute: Long): List<TimeWindow> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        fun blockAt(startH: Int, startM: Int, endH: Int, endM: Int): TimeWindow {
            val s = today.atTime(LocalTime.of(startH, startM)).atZone(zone).toEpochSecond() / 60
            val e = today.atTime(LocalTime.of(endH, endM)).atZone(zone).toEpochSecond() / 60
            return TimeWindow(s, e)
        }
        val busy = listOf(
            blockAt(9, 0, 10, 0),    // stand-up + focus
            blockAt(12, 30, 13, 0),  // lunch call
            blockAt(14, 0, 15, 30),  // meetings
            blockAt(17, 0, 17, 30),  // wrap-up
        )
        return busy.filter { it.endEpochMinute > startEpochMinute && it.startEpochMinute < endEpochMinute }
    }

    override suspend fun createEvent(title: String, startEpochMinute: Long, durationMinutes: Int): Result<String> {
        // No-op in stub mode: report success with a synthetic id so the confirm-flow can be tested.
        return Result.success("stub-event-$startEpochMinute")
    }
}
