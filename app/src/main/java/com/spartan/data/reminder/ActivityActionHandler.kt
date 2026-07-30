package com.spartan.data.reminder

import com.spartan.data.repository.HealthRepository
import com.spartan.domain.model.ActivityStatus

/**
 * The logic behind notification action buttons ("Done", "Snooze 1 hour"), kept free of Android
 * broadcast plumbing so it is unit-testable on the JVM. The receiver stays a thin shell.
 */
class ActivityActionHandler(
    private val repository: HealthRepository,
    private val reminderScheduler: ReminderScheduler,
) {
    suspend fun handle(action: String, activityId: String, activityTitle: String) {
        when (action) {
            ACTION_DONE -> repository.updateActivityStatus(
                id = activityId,
                status = ActivityStatus.DONE,
                completedAtMillis = System.currentTimeMillis(),
            )
            ACTION_SNOOZE -> {
                val wakeAt = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
                repository.updateActivityStatus(
                    id = activityId,
                    status = ActivityStatus.SNOOZED,
                    snoozedUntilMillis = wakeAt,
                )
                reminderScheduler.scheduleActivityReminder(
                    activityId = activityId,
                    title = "Back on: $activityTitle",
                    body = "Snoozed earlier — still a good time when you're ready.",
                    triggerAtMillis = wakeAt,
                )
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.spartan.action.ACTIVITY_DONE"
        const val ACTION_SNOOZE = "com.spartan.action.ACTIVITY_SNOOZE"
        const val EXTRA_ACTIVITY_ID = "activity_id"
        const val EXTRA_ACTIVITY_TITLE = "activity_title"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val SNOOZE_MINUTES = 60
    }
}
