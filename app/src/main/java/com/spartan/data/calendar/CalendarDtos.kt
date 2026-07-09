package com.spartan.data.calendar

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for the Google Calendar API free/busy query and event insert. Free/busy returns only opaque
 * busy intervals — never event titles or details — matching Spartan's least-privilege read scope.
 */

@Serializable
data class FreeBusyRequest(
    val timeMin: String,
    val timeMax: String,
    val items: List<FreeBusyItem> = listOf(FreeBusyItem("primary")),
)

@Serializable
data class FreeBusyItem(val id: String)

@Serializable
data class FreeBusyResponse(
    val calendars: Map<String, FreeBusyCalendar> = emptyMap(),
)

@Serializable
data class FreeBusyCalendar(
    val busy: List<FreeBusyPeriod> = emptyList(),
)

@Serializable
data class FreeBusyPeriod(
    val start: String? = null,
    val end: String? = null,
)

// --- event insert (opt-in write) ---
@Serializable
data class CalendarEvent(
    val summary: String,
    val description: String? = null,
    val start: EventDateTime,
    val end: EventDateTime,
)

@Serializable
data class EventDateTime(
    @SerialName("dateTime") val dateTime: String,
)

@Serializable
data class CalendarEventResponse(
    val id: String? = null,
)
