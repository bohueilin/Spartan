package com.spartan.data.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.glance.appwidget.updateAll
import com.spartan.data.repository.HealthRepository
import com.spartan.diagnostics.DebugLog
import com.spartan.domain.model.DailyPlan
import com.spartan.domain.usecase.DailyPlanSync
import com.spartan.ui.widget.NextActivityWidget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Background daily-plan refresh (~04:00 local, inexact by design — see the exact-alarm decision in
 * docs/Spartan_Enhancements.md §2): a user who only ever taps the morning reminder still wakes up
 * to a fresh, readiness-correct plan. Fully local; no constraints. Idempotent — seeding never
 * overwrites the user's check-in state, and WHOOP persistence is per-day replace.
 */
@HiltWorker
class DailyPlanRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dailyPlanSync: DailyPlanSync,
    private val reminderScheduler: ReminderScheduler,
    private val repository: HealthRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Read before the sync: once an erasure begins after this, what this run fetched and
        // planned is data the user deleted, and nothing of it may be written or announced.
        val erasureGeneration = repository.erasureGeneration
        val outcome = dailyPlanSync.sync(LocalDate.now().toEpochDay(), erasureGeneration = erasureGeneration)
        DebugLog.log("worker", "daily refresh ${if (outcome.failed) "failed" else "ok"}")
        // A replaced holding plan's snoozed / rescheduled rows are gone; their alarms must not fire.
        outcome.replacedReminderIds.forEach(reminderScheduler::cancelActivityReminder)
        // Keep the home-screen widget showing the fresh plan.
        ignoringFailures { NextActivityWidget().updateAll(applicationContext) }
        // Proactive morning digest at 07:15 (just after default quiet hours end): the plan headline
        // plus size, so the day starts with a glance, not an app launch. Skipped automatically if
        // the worker ran late (past trigger) or notifications are off. Checked right before the
        // enqueue; a digest armed in the instant an erasure begins is swept by its second purge
        // (ReminderScheduler.purgeRemindersAfterErasure).
        digestPlan(outcome.plan, startedAt = erasureGeneration, current = repository.erasureGeneration)?.let { plan ->
            val digestAt = LocalDate.now().atTime(LocalTime.of(7, 15))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            reminderScheduler.scheduleActivityReminder(
                activityId = "morning-digest",
                title = "Today: ${plan.headline}",
                body = "${plan.activities.size} activities, about ${plan.totalEstimatedMinutes} min. Tap to see your plan.",
                triggerAtMillis = digestAt,
            )
        }
        // Mock/local data can't transiently fail; a real-network failure is worth one retry cycle.
        return if (outcome.failed && runAttemptCount < 2) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_PERIODIC = "daily_plan_refresh"
        private const val UNIQUE_ONE_SHOT = "post_update_plan_refresh"

        /** Enqueue the daily ~04:00 refresh. KEEP policy: re-calling is a harmless no-op. */
        fun schedule(workManager: WorkManager) {
            val now = LocalDateTime.now()
            var target = now.toLocalDate().atTime(LocalTime.of(4, 0))
            if (!target.isAfter(now)) target = target.plusDays(1)
            val request = PeriodicWorkRequestBuilder<DailyPlanRefreshWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, target))
                .build()
            workManager.enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** One-shot refresh right now (used after an app update to re-arm reminders). */
        fun refreshNow(workManager: WorkManager) {
            workManager.enqueueUniqueWork(
                UNIQUE_ONE_SHOT,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<DailyPlanRefreshWorker>().build(),
            )
        }

        /** Disarm both refresh jobs — part of full data erasure, or the DB would repopulate. */
        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(UNIQUE_PERIODIC)
            workManager.cancelUniqueWork(UNIQUE_ONE_SHOT)
        }
    }
}

/**
 * Runs [block] and swallows its failure — a widget that fails to redraw must never fail the
 * refresh — but never a cancellation: erasure stops this worker, and a stopped run must stop
 * there rather than go on to arm the morning digest.
 */
internal suspend fun ignoringFailures(block: suspend () -> Unit) {
    runCatching { block() }.onFailure { if (it is CancellationException) throw it }
}

/**
 * The plan the morning digest announces: none when the sync produced none, and none once an
 * erasure has begun since the worker read the erasure count [startedAt] ([current] is the count
 * now) — its headline would be a plan the user just deleted.
 */
internal fun digestPlan(plan: DailyPlan?, startedAt: Long, current: Long): DailyPlan? =
    plan?.takeIf { current == startedAt }
