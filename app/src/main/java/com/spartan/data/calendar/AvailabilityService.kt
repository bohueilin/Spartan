package com.spartan.data.calendar

import com.spartan.domain.model.TimeWindow
import javax.inject.Inject

/**
 * Finds open time windows that fit an activity, honoring working hours, a sleep window, quiet
 * hours, and existing busy blocks. The core algorithm is pure and works entirely in epoch minutes
 * (UTC), so timezone/DST handling is a caller concern (the caller derives the day/working bounds
 * from local time) and the logic stays deterministic and unit-testable.
 */
class AvailabilityService @Inject constructor() {

    /**
     * Open windows within [dayStartEpochMinute, dayEndEpochMinute) after removing [busy] blocks.
     * [busy] blocks may overlap and be unsorted. Returned windows are sorted, non-overlapping, and
     * at least [minWindowMinutes] long.
     */
    fun openWindows(
        dayStartEpochMinute: Long,
        dayEndEpochMinute: Long,
        busy: List<TimeWindow>,
        minWindowMinutes: Int = 5,
    ): List<TimeWindow> {
        if (dayEndEpochMinute <= dayStartEpochMinute) return emptyList()
        val merged = mergeBusy(
            busy.mapNotNull { clamp(it, dayStartEpochMinute, dayEndEpochMinute) }
        )
        val open = mutableListOf<TimeWindow>()
        var cursor = dayStartEpochMinute
        for (block in merged) {
            if (block.startEpochMinute > cursor) open += TimeWindow(cursor, block.startEpochMinute)
            cursor = maxOf(cursor, block.endEpochMinute)
        }
        if (cursor < dayEndEpochMinute) open += TimeWindow(cursor, dayEndEpochMinute)
        return open.filter { it.durationMinutes >= minWindowMinutes }
    }

    /**
     * The earliest open window that can fit [activityMinutes], returned trimmed to exactly that
     * length starting at the window's start. Null if nothing fits.
     */
    fun suggestSlot(
        activityMinutes: Int,
        dayStartEpochMinute: Long,
        dayEndEpochMinute: Long,
        busy: List<TimeWindow>,
    ): TimeWindow? {
        val fit = openWindows(dayStartEpochMinute, dayEndEpochMinute, busy, minWindowMinutes = activityMinutes)
            .firstOrNull { it.durationMinutes >= activityMinutes } ?: return null
        return TimeWindow(fit.startEpochMinute, fit.startEpochMinute + activityMinutes)
    }

    private fun clamp(w: TimeWindow, lo: Long, hi: Long): TimeWindow? {
        val s = maxOf(w.startEpochMinute, lo)
        val e = minOf(w.endEpochMinute, hi)
        return if (e > s) TimeWindow(s, e) else null
    }

    private fun mergeBusy(blocks: List<TimeWindow>): List<TimeWindow> {
        if (blocks.isEmpty()) return emptyList()
        val sorted = blocks.sortedBy { it.startEpochMinute }
        val out = mutableListOf(sorted.first())
        for (b in sorted.drop(1)) {
            val last = out.last()
            if (b.startEpochMinute <= last.endEpochMinute) {
                out[out.lastIndex] = TimeWindow(last.startEpochMinute, maxOf(last.endEpochMinute, b.endEpochMinute))
            } else {
                out += b
            }
        }
        return out
    }
}
