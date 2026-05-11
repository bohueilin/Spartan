package com.vitalcompass.data.reminder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import com.vitalcompass.data.local.ReminderEntity
import com.vitalcompass.domain.engine.ReminderEngine
import com.vitalcompass.domain.engine.ReminderRequest
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
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
        val work = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayUntil(reminder.hour, reminder.minute))
            .addTag(REMINDER_TAG)
            .setInputData(
                Data.Builder()
                    .putString(ReminderWorker.KEY_TITLE, reminder.title)
                    .putString(ReminderWorker.KEY_BODY, reminder.body)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(reminder.id, ExistingWorkPolicy.REPLACE, work)
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
        const val REMINDER_TAG = "vital_compass_reminders"
    }
}
