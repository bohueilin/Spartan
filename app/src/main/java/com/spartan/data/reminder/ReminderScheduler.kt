package com.spartan.data.reminder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import com.spartan.data.local.ReminderEntity
import com.spartan.domain.engine.ReminderEngine
import com.spartan.domain.engine.ReminderRequest
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val reminderEngine: ReminderEngine,
) {
    fun schedule(reminder: ReminderEntity) {
        val request = ReminderRequest(reminder.id, reminder.title, reminder.hour, reminder.minute, reminder.enabled)
        if (!reminderEngine.canSchedule(hasNotificationPermission(), request)) {
            workManager.cancelUniqueWork(reminder.id)
            return
        }
        val work = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntil(reminder.hour, reminder.minute))
            .addTag(REMINDER_TAG)
            .setInputData(
                Data.Builder()
                    .putString(ReminderWorker.KEY_REMINDER_ID, reminder.id)
                    .putString(ReminderWorker.KEY_TITLE, reminder.title)
                    .putString(ReminderWorker.KEY_BODY, reminder.body)
                    .putInt(ReminderWorker.KEY_DAYS_OF_WEEK_MASK, reminder.daysOfWeekMask)
                    .build()
            )
            .build()
        workManager.enqueueUniquePeriodicWork(reminder.id, ExistingPeriodicWorkPolicy.UPDATE, work)
    }

    /**
     * One-shot reminder for a scheduled activity, at [triggerAtMillis]. Skipped if notifications
     * are not permitted, the time has passed, or it falls in quiet hours. Replaces any prior
     * reminder for the same activity so re-scheduling never spams.
     */
    fun scheduleActivityReminder(activityId: String, title: String, body: String, triggerAtMillis: Long) {
        if (!hasNotificationPermission()) return
        val delayMs = triggerAtMillis - System.currentTimeMillis()
        if (delayMs <= 0) return
        val local = java.time.Instant.ofEpochMilli(triggerAtMillis).atZone(java.time.ZoneId.systemDefault())
        val minuteOfDay = local.hour * 60 + local.minute
        if (reminderEngine.isQuietHours(minuteOfDay)) return
        val uniqueName = "activity-$activityId"
        val work = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .addTag(REMINDER_TAG)
            .setInputData(
                Data.Builder()
                    .putString(ReminderWorker.KEY_REMINDER_ID, uniqueName)
                    .putString(ReminderWorker.KEY_TITLE, title)
                    .putString(ReminderWorker.KEY_BODY, body)
                    .putInt(ReminderWorker.KEY_DAYS_OF_WEEK_MASK, 127)
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, work)
    }

    /**
     * Post a notification immediately (permission-checked). Used by workers that have already
     * decided timing themselves (e.g. the evening nudge, which checks quiet hours first).
     */
    fun notifyNow(id: String, title: String, body: String) {
        if (!hasNotificationPermission()) return
        ReminderWorker.postNotification(context, id, title, body)
    }

    fun cancel(id: String) {
        workManager.cancelUniqueWork(id)
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag(REMINDER_TAG)
    }

    fun hasNotificationPermission(): Boolean {
        return android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun delayUntil(hour: Int, minute: Int): Duration {
        val now = LocalDateTime.now()
        var target = now.with(LocalTime.of(hour, minute))
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target)
    }

    private companion object {
        const val REMINDER_TAG = "spartan_reminders"
    }
}
