package com.spartan.data.calendar

import com.spartan.domain.model.TimeWindow

/**
 * The seam Spartan depends on for calendar availability. Implementations:
 *  - [StubCalendarClient]    — mock free/busy; the default when no credentials exist.
 *  - GoogleCalendarClient    — Phase-2, backed by the Google Calendar API.
 *
 * Least privilege: reads use free/busy only (never event contents). Event creation is a separate,
 * strictly opt-in capability.
 */
interface CalendarClient {
    val isStub: Boolean

    /** Busy blocks overlapping [startEpochMinute, endEpochMinute). Free/busy only — no event details. */
    suspend fun freeBusy(startEpochMinute: Long, endEpochMinute: Long): List<TimeWindow>

    /** Create a calendar event for a scheduled activity. Opt-in; requires the write scope. */
    suspend fun createEvent(title: String, startEpochMinute: Long, durationMinutes: Int): Result<String>
}
