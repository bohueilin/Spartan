package com.spartan.domain.engine

data class ReminderRequest(
    val id: String,
    val title: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
)

class ReminderEngine {
    fun deduplicate(existing: List<ReminderRequest>, incoming: ReminderRequest): List<ReminderRequest> {
        if (!incoming.enabled) return existing.filterNot { it.id == incoming.id }
        return existing.filterNot { it.id == incoming.id } + incoming
    }

    fun canSchedule(permissionGranted: Boolean, request: ReminderRequest): Boolean {
        return permissionGranted && request.enabled && request.hour in 0..23 && request.minute in 0..59
    }

    /**
     * Whether [minuteOfDay] (0..1439) falls inside a quiet-hours window, handling windows that
     * wrap past midnight (e.g. 22:00 -> 07:00). Used to avoid notifying during rest windows.
     */
    fun isQuietHours(
        minuteOfDay: Int,
        quietStartMinuteOfDay: Int = DEFAULT_QUIET_START,
        quietEndMinuteOfDay: Int = DEFAULT_QUIET_END,
    ): Boolean {
        if (quietStartMinuteOfDay == quietEndMinuteOfDay) return false
        return if (quietStartMinuteOfDay < quietEndMinuteOfDay) {
            minuteOfDay in quietStartMinuteOfDay until quietEndMinuteOfDay
        } else {
            minuteOfDay >= quietStartMinuteOfDay || minuteOfDay < quietEndMinuteOfDay
        }
    }

    companion object {
        const val DEFAULT_QUIET_START = 22 * 60 // 22:00
        const val DEFAULT_QUIET_END = 7 * 60 // 07:00
    }
}
