package com.vitalcompass.domain.engine

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
}
