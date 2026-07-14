package com.spartan.data.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spartan.data.repository.HealthRepository
import com.spartan.diagnostics.DebugLog
import com.spartan.domain.engine.ReminderEngine
import com.spartan.domain.model.ActivityStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Proactive evening guidance (~19:00 local): if today's plan still has open activities, send one
 * calm nudge with what's left and how little time it takes — an easy win, never a guilt trip.
 * Silent when the plan is done, when nothing was planned, or during quiet hours. At most one
 * notification per evening by construction (single periodic slot).
 */
@HiltWorker
class EveningNudgeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: HealthRepository,
    private val reminderEngine: ReminderEngine,
    private val reminderScheduler: ReminderScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val today = LocalDate.now().toEpochDay()
        val open = repository.dailyActivities(today).first()
            .filter { it.status == ActivityStatus.PLANNED || it.status == ActivityStatus.SNOOZED || it.status == ActivityStatus.RESCHEDULED }
        if (open.isEmpty()) {
            DebugLog.log("worker", "evening nudge: plan complete or empty — silent")
            return Result.success()
        }
        val now = LocalDateTime.now()
        if (reminderEngine.isQuietHours(now.hour * 60 + now.minute)) {
            DebugLog.log("worker", "evening nudge: quiet hours — silent")
            return Result.success()
        }
        val minutes = open.sumOf { it.estimatedMinutes }
        val title = if (open.size == 1) "1 activity left today" else "${open.size} activities left today"
        val body = "About $minutes min total. ${open.first().title} is a good place to start."
        reminderScheduler.notifyNow(id = "evening-nudge", title = title, body = body)
        DebugLog.log("worker", "evening nudge: notified (${open.size} open)")
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "evening_nudge"

        /** Enqueue the daily ~19:00 check. KEEP policy — harmless to call repeatedly. */
        fun schedule(workManager: WorkManager) {
            val now = LocalDateTime.now()
            var target = now.toLocalDate().atTime(LocalTime.of(19, 0))
            if (!target.isAfter(now)) target = target.plusDays(1)
            val request = PeriodicWorkRequestBuilder<EveningNudgeWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, target))
                .build()
            workManager.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Disarm the nudge — part of full data erasure. */
        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
