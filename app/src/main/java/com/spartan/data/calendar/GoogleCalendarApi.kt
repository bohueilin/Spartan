package com.spartan.data.calendar

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Google Calendar API v3 (base https://www.googleapis.com/calendar/v3/). Bearer auth is added by
 * the OkHttp interceptor. Reads use free/busy only; `insertEvent` is used only after explicit
 * user opt-in to event creation (requires the write scope).
 */
interface GoogleCalendarApi {
    @POST("freeBusy")
    suspend fun freeBusy(@Body request: FreeBusyRequest): FreeBusyResponse

    @POST("calendars/{calendarId}/events")
    suspend fun insertEvent(
        @Path("calendarId") calendarId: String,
        @Body event: CalendarEvent,
    ): CalendarEventResponse
}
