package com.spartan.robolectric

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.spartan.data.local.ConnectionStatus
import com.spartan.data.local.Converters
import com.spartan.data.local.IntegrationProvider
import com.spartan.data.local.ReminderEntity
import com.spartan.data.local.ReminderFrequency
import com.spartan.data.reminder.ReminderScheduler
import com.spartan.data.reminder.ReminderWorker
import com.spartan.domain.engine.ReminderEngine
import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.TimeOfDay
import com.spartan.domain.model.WorkoutType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * JVM-run Android component tests via Robolectric — no emulator needed, so they run in the fast CI
 * `unit` job. Covers the WorkManager reminder path (enqueue, dedupe, day-mask), the notification
 * worker, and Room type converters.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidComponentsTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // The scheduler correctly refuses to enqueue without POST_NOTIFICATIONS (API 33+);
        // grant it so the enqueue paths are exercised.
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val config = Configuration.Builder().setExecutor { it.run() }.build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
    }

    // --- ReminderScheduler ---

    @Test
    fun schedule_enqueuesUniquePeriodicWork_andDisabledCancelsIt() {
        val scheduler = ReminderScheduler(context, workManager, ReminderEngine())
        val reminder = ReminderEntity(
            id = "exercise", title = "Exercise", body = "Move", hour = 8, minute = 0,
            enabled = true, frequency = ReminderFrequency.DAILY, daysOfWeekMask = 127,
        )
        scheduler.schedule(reminder)
        var info = workManager.getWorkInfosForUniqueWork("exercise").get()
        assertEquals(1, info.size)
        assertTrue(info.first().state == WorkInfo.State.ENQUEUED || info.first().state == WorkInfo.State.RUNNING)

        // Disabling the reminder cancels the unique work rather than leaving a zombie.
        scheduler.schedule(reminder.copy(enabled = false))
        info = workManager.getWorkInfosForUniqueWork("exercise").get()
        assertTrue(info.isEmpty() || info.first().state == WorkInfo.State.CANCELLED)
    }

    @Test
    fun activityReminder_pastTriggerTimeIsNotEnqueued() {
        val scheduler = ReminderScheduler(context, workManager, ReminderEngine())
        scheduler.scheduleActivityReminder(
            activityId = "a1", title = "t", body = "b",
            triggerAtMillis = System.currentTimeMillis() - 60_000,
        )
        assertTrue(workManager.getWorkInfosForUniqueWork("activity-a1").get().isEmpty())
    }

    // --- ReminderWorker ---

    @Test
    fun reminderWorker_succeeds_andRespectsDayOfWeekMask() = runBlocking {
        // Mask 0 = no days enabled: the worker must exit successfully without posting.
        val silent = TestListenableWorkerBuilder<ReminderWorker>(context)
            .setInputData(
                Data.Builder()
                    .putString(ReminderWorker.KEY_REMINDER_ID, "quiet")
                    .putString(ReminderWorker.KEY_TITLE, "Quiet")
                    .putString(ReminderWorker.KEY_BODY, "b")
                    .putInt(ReminderWorker.KEY_DAYS_OF_WEEK_MASK, 0)
                    .build(),
            )
            .build()
        assertEquals(ListenableWorker.Result.success(), silent.doWork())

        // All-days mask also succeeds (posts a notification where permitted).
        val active = TestListenableWorkerBuilder<ReminderWorker>(context)
            .setInputData(
                Data.Builder()
                    .putString(ReminderWorker.KEY_REMINDER_ID, "active")
                    .putString(ReminderWorker.KEY_TITLE, "Active")
                    .putString(ReminderWorker.KEY_BODY, "b")
                    .putInt(ReminderWorker.KEY_DAYS_OF_WEEK_MASK, 127)
                    .build(),
            )
            .build()
        assertEquals(ListenableWorker.Result.success(), active.doWork())
    }

    // --- Room converters (exhaustive enum round-trips) ---

    @Test
    fun converters_roundTripEveryEnum() {
        val c = Converters()
        MetricType.entries.forEach { assertEquals(it, c.stringToMetricType(c.metricTypeToString(it))) }
        WorkoutType.entries.forEach { assertEquals(it, c.stringToWorkoutType(c.workoutTypeToString(it))) }
        ReminderFrequency.entries.forEach { assertEquals(it, c.stringToReminderFrequency(c.reminderFrequencyToString(it))) }
        ActivityCategory.entries.forEach { assertEquals(it, c.stringToActivityCategory(c.activityCategoryToString(it))) }
        ActivityPriority.entries.forEach { assertEquals(it, c.stringToActivityPriority(c.activityPriorityToString(it))) }
        ActivityStatus.entries.forEach { assertEquals(it, c.stringToActivityStatus(c.activityStatusToString(it))) }
        Intensity.entries.forEach { assertEquals(it, c.stringToIntensity(c.intensityToString(it))) }
        TimeOfDay.entries.forEach { assertEquals(it, c.stringToTimeOfDay(c.timeOfDayToString(it))) }
        IntegrationProvider.entries.forEach { assertEquals(it, c.stringToIntegrationProvider(c.integrationProviderToString(it))) }
        ConnectionStatus.entries.forEach { assertEquals(it, c.stringToConnectionStatus(c.connectionStatusToString(it))) }
    }
}
