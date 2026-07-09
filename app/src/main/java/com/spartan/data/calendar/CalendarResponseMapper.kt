package com.spartan.data.calendar

import com.spartan.domain.model.TimeWindow
import java.time.Instant

/**
 * Pure normalization from a Google free/busy response into Spartan's [TimeWindow] busy blocks
 * (epoch minutes). Only the "primary" calendar's busy periods are used. Free of Android/Retrofit
 * types so it is fully unit-testable.
 */
object CalendarResponseMapper {

    fun toBusyWindows(response: FreeBusyResponse, calendarId: String = "primary"): List<TimeWindow> {
        val periods = response.calendars[calendarId]?.busy ?: emptyList()
        return periods.mapNotNull { p ->
            val start = epochMinute(p.start) ?: return@mapNotNull null
            val end = epochMinute(p.end) ?: return@mapNotNull null
            if (end > start) TimeWindow(start, end) else null
        }
    }

    private fun epochMinute(iso: String?): Long? = try {
        if (iso.isNullOrBlank()) null else Instant.parse(iso).epochSecond / 60
    } catch (_: Exception) {
        null
    }
}
