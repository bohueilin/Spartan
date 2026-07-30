package com.spartan.robolectric

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.spartan.data.local.AppDatabase
import com.spartan.data.local.DailyActivityEntity
import com.spartan.data.reminder.ActivityActionHandler
import com.spartan.data.reminder.ReminderScheduler
import com.spartan.data.repository.HealthRepository
import com.spartan.domain.engine.ReminderEngine
import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.TimeOfDay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * End-to-end JVM test for the notification action path: the handler behind the "Done" and
 * "Snooze 1 hour" buttons must persist real status changes through the real repository into a
 * real (in-memory) Room database — the plan can be worked without ever opening the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActivityActionHandlerTest {

    private lateinit var db: AppDatabase
    private lateinit var handler: ActivityActionHandler
    private lateinit var repository: HealthRepository
    private val today = java.time.LocalDate.now().toEpochDay()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor { it.run() }.build(),
        )
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HealthRepository(db.healthDao(), db)
        handler = ActivityActionHandler(
            repository,
            ReminderScheduler(context, WorkManager.getInstance(context), ReminderEngine()),
        )
        runBlocking {
            db.healthDao().upsertActivity(
                DailyActivityEntity(
                    id = "$today:walk",
                    dateEpochDay = today,
                    title = "15-minute easy walk",
                    category = ActivityCategory.ZONE2,
                    priority = ActivityPriority.RECOMMENDED,
                    whyItMatters = "Easy movement aids recovery.",
                    relatedMetric = null,
                    instructions = "Walk at a conversational pace.",
                    estimatedMinutes = 15,
                    intensity = Intensity.EASY,
                    bestTimeOfDay = TimeOfDay.MIDDAY,
                    status = ActivityStatus.PLANNED,
                    ruleId = "LOW_RECOVERY",
                ),
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun activity(): DailyActivityEntity = runBlocking {
        db.healthDao().observeActivitiesForDay(today).first().single()
    }

    @Test
    fun doneAction_marksActivityDoneWithTimestamp() = runBlocking {
        handler.handle(ActivityActionHandler.ACTION_DONE, "$today:walk", "15-minute easy walk")
        val updated = activity()
        assertEquals(ActivityStatus.DONE, updated.status)
        assertNotNull(updated.completedAtMillis)
    }

    @Test
    fun snoozeAction_snoozesForAnHour() = runBlocking {
        val before = System.currentTimeMillis()
        handler.handle(ActivityActionHandler.ACTION_SNOOZE, "$today:walk", "15-minute easy walk")
        val updated = activity()
        assertEquals(ActivityStatus.SNOOZED, updated.status)
        val until = updated.snoozedUntilMillis
        assertNotNull(until)
        assertTrue("snooze is ~60 min out", until!! >= before + 59 * 60_000L && until <= before + 61 * 60_000L)
    }

    @Test
    fun unknownAction_changesNothing() = runBlocking {
        handler.handle("com.spartan.action.UNKNOWN", "$today:walk", "x")
        assertEquals(ActivityStatus.PLANNED, activity().status)
    }
}
