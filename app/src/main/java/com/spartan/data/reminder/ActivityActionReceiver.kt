package com.spartan.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.spartan.data.repository.HealthRepository
import com.spartan.diagnostics.DebugLog
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles "Done" / "Snooze 1 hour" taps on activity notifications — the plan can be worked without
 * opening the app. Thin shell over [ActivityActionHandler]; uses goAsync so the DB write survives
 * the broadcast window.
 */
class ActivityActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun repository(): HealthRepository
        fun reminderScheduler(): ReminderScheduler
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ActivityActionHandler.ACTION_DONE && action != ActivityActionHandler.ACTION_SNOOZE) return
        val activityId = intent.getStringExtra(ActivityActionHandler.EXTRA_ACTIVITY_ID) ?: return
        val title = intent.getStringExtra(ActivityActionHandler.EXTRA_ACTIVITY_TITLE) ?: ""
        val notificationId = intent.getIntExtra(ActivityActionHandler.EXTRA_NOTIFICATION_ID, 0)

        // Dismiss the notification immediately — feedback first.
        if (notificationId != 0) NotificationManagerCompat.from(context).cancel(notificationId)

        val entryPoint = EntryPointAccessors.fromApplication(context, ReceiverEntryPoint::class.java)
        val handler = ActivityActionHandler(entryPoint.repository(), entryPoint.reminderScheduler())
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handler.handle(action, activityId, title)
                DebugLog.log("receiver", "notification action $action for activity")
            } finally {
                pending.finish()
            }
        }
    }
}
