package com.spartan.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.spartan.R
import java.time.LocalDate

class ReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val daysOfWeekMask = inputData.getInt(KEY_DAYS_OF_WEEK_MASK, 127)
        if (!isTodayEnabled(daysOfWeekMask)) return Result.success()
        val reminderId = inputData.getString(KEY_REMINDER_ID) ?: inputData.getString(KEY_TITLE) ?: "spartan"
        val title = inputData.getString(KEY_TITLE) ?: "Spartan"
        val body = inputData.getString(KEY_BODY) ?: "Take a minute to log your health data."
        postNotification(applicationContext, reminderId, title, body)
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "spartan_reminders"
        const val KEY_REMINDER_ID = "reminder_id"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_DAYS_OF_WEEK_MASK = "days_of_week_mask"

        /**
         * Post a Spartan notification. Tapping deep-links to the daily check-in (spartan://today).
         * Swallows SecurityException (permission revoked mid-flight) — reminders must never crash.
         */
        fun postNotification(context: Context, id: String, title: String, body: String) {
            ensureChannel(context)
            val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse("spartan://today")).apply {
                setPackage(context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val contentIntent = PendingIntent.getActivity(
                context,
                id.hashCode(),
                deepLink,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
            } catch (_: SecurityException) {
                // Permission revoked between check and notify — stay silent.
            }
        }

        fun ensureChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Spartan reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Local reminders for workouts and health logging."
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private fun isTodayEnabled(daysOfWeekMask: Int): Boolean {
            val bit = 1 shl (LocalDate.now().dayOfWeek.value - 1)
            return daysOfWeekMask and bit != 0
        }
    }
}
