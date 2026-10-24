package com.spartan.robolectric

import android.Manifest
import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.spartan.data.local.AppDatabase
import com.spartan.data.local.ConnectionStatus
import com.spartan.data.local.DailyActivityEntity
import com.spartan.data.local.HealthDao
import com.spartan.data.local.IntegrationProvider
import com.spartan.data.reminder.ReminderScheduler
import com.spartan.data.reminder.digestPlan
import com.spartan.data.reminder.ignoringFailures
import com.spartan.data.repository.HealthRepository
import com.spartan.data.whoop.csv.WhoopCsvImporter
import com.spartan.domain.engine.ReminderEngine
import com.spartan.domain.engine.RuleIds
import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.DailyPlan
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.ReadinessBand
import com.spartan.domain.model.TimeOfDay
import com.spartan.ui.screens.CONSISTENCY_WINDOW_DAYS
import com.spartan.ui.screens.STREAK_WINDOW_DAYS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The window between "Delete all" and the wipe itself: erasure is marked the moment it begins, so
 * an earlier writer is refused before the wipe takes the store lock; a CSV import reads its files
 * outside that lock, so an erasure never waits on a slow provider; and a reminder armed in the
 * window is swept once the store is empty. Also the background refresh's digest guard and the two
 * weeks of check-off flags behind the streak summary.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ErasureWindowTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: HealthDao
    private lateinit var repository: HealthRepository
    private lateinit var importer: WhoopCsvImporter
    private val today = 20_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.healthDao()
        repository = HealthRepository(dao, db)
        importer = WhoopCsvImporter(context, db.whoopCycleDao(), repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- The erasure marker ---------------------------------------------------------------------

    @Test
    fun beginErasure_refusesEarlierWriters_beforeTheWipeTakesTheLock() = runBlocking<Unit> {
        val before = repository.erasureGeneration

        repository.beginErasure()

        assertNotEquals(before, repository.erasureGeneration)
        var ran = false
        assertNull(repository.writeUnlessErasedSince(before) { ran = true })
        assertFalse(ran)

        // A writer that read the count between the mark and the wipe is refused by the wipe's own bump.
        val inBetween = repository.erasureGeneration
        repository.deleteAllLocalData()
        assertNull(repository.writeUnlessErasedSince(inBetween) { "late" })
        // After the erasure, a fresh start writes as usual.
        assertEquals("fresh", repository.writeUnlessErasedSince(repository.erasureGeneration) { "fresh" })
    }

    // --- CSV import: read outside the lock, persist under it -------------------------------------

    private val cyclesHeader =
        "Cycle start time,Cycle end time,Cycle timezone,Recovery score %,Resting heart rate (bpm)," +
            "Heart rate variability (ms),Skin temp (celsius),Blood oxygen %,Day Strain,Energy burned (cal)," +
            "Max HR (bpm),Average HR (bpm),Sleep onset,Wake onset,Sleep performance %,Respiratory rate (rpm)," +
            "Asleep duration (min),In bed duration (min),Light sleep duration (min),Deep (SWS) duration (min)," +
            "REM duration (min),Awake duration (min),Sleep need (min),Sleep debt (min),Sleep efficiency %,Sleep consistency %"

    private val cyclesCsv = cyclesHeader + "\n" +
        "2030-01-10 00:40:00,2030-01-11 01:00:00,UTC-07:00,75,58,82,33.50,96.00,9.5,2100,168,88," +
        "2030-01-10 00:40:00,2030-01-10 07:40:00,80,14.8,390,420,180,90,120,30,540,90,93,85"

    private val importedDay = LocalDate.parse("2030-01-10").toEpochDay()

    private fun picked(name: String, text: String): Uri {
        val uri = Uri.parse("content://com.spartan.test/$name")
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { text.byteInputStream() }
        return uri
    }

    @Test
    fun importRead_writesNothing_andPersistSavesWhatItStaged() = runBlocking<Unit> {
        val cycles = picked("physiological_cycles.csv", cyclesCsv)
        val notes = picked("notes.txt", "Date,Steps\n2030-01-10,9000")

        val staged = importer.read(listOf(cycles, notes)).getOrThrow()

        // Reading is only reading: nothing reached the store yet.
        assertEquals(0, db.whoopCycleDao().cycleCount())
        assertEquals(0, dao.metricCount())
        assertEquals(listOf("physiological_cycles.csv"), staged.recognized)
        assertEquals(listOf("notes.txt"), staged.skipped)

        val summary = importer.persist(staged).getOrThrow()

        assertEquals(1, summary.days)
        assertEquals(importedDay, summary.firstDayEpoch)
        assertEquals(importedDay, summary.lastDayEpoch)
        assertEquals(listOf("physiological_cycles.csv"), summary.recognizedFiles)
        assertEquals(listOf("notes.txt"), summary.skippedFiles)
        assertEquals(1, db.whoopCycleDao().cycleCount())
        assertEquals(75.0, dao.latestReadingOnDay(MetricType.RECOVERY_SCORE, importedDay)?.value!!, 0.0)
        val whoop = repository.connections.first().single { it.provider == IntegrationProvider.WHOOP }
        assertEquals(ConnectionStatus.CONNECTED, whoop.status)
        assertEquals("csv:cycles", whoop.scopes)
    }

    @Test
    fun importRead_ofNoWhoopFile_failsWithoutWriting() = runBlocking<Unit> {
        val notes = picked("steps.csv", "Date,Steps\n2030-01-10,9000")

        val failure = importer.read(listOf(notes)).exceptionOrNull()

        assertTrue(failure is WhoopCsvImporter.ImportError.NoWhoopData)
        assertEquals(listOf("steps.csv"), (failure as WhoopCsvImporter.ImportError.NoWhoopData).skipped)
        assertEquals(0, db.whoopCycleDao().cycleCount())
    }

    /** A picked file on a slow provider: the first read blocks until the test lets it through. */
    private class SlowStream(
        private val bytes: ByteArray,
        private val started: CountDownLatch,
        private val release: CountDownLatch,
    ) : InputStream() {
        private var position = 0

        override fun read(): Int {
            if (position == 0) {
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
            return if (position < bytes.size) bytes[position++].toInt() and 0xFF else -1
        }
    }

    @Test
    fun importRead_holdsNoLock_soAnErasureFinishesMidRead_andTheStagedWriteIsRefused() = runBlocking<Unit> {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val slow = Uri.parse("content://com.spartan.test/slow/physiological_cycles.csv")
        shadowOf(context.contentResolver).registerInputStreamSupplier(slow) {
            SlowStream(cyclesCsv.toByteArray(), started, release)
        }
        val generation = repository.erasureGeneration

        val reading = async(Dispatchers.Default) { importer.read(listOf(slow)) }
        assertTrue(started.await(5, TimeUnit.SECONDS))

        // The file is still being read, and the erasure runs to the end all the same.
        withTimeout(5_000) {
            repository.beginErasure()
            repository.deleteAllLocalData()
        }
        release.countDown()
        val staged = reading.await().getOrThrow()

        // What was read before the erasure is the user's deleted data: it is never written.
        assertNull(repository.writeUnlessErasedSince(generation) { importer.persist(staged) })
        assertEquals(0, db.whoopCycleDao().cycleCount())
        assertEquals(0, dao.metricCount())
    }

    // --- The sweep once the store is empty -------------------------------------------------------

    @Test
    fun purgeRemindersAfterErasure_cancelsAReminderArmedDuringTheTeardown() = runBlocking<Unit> {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor { it.run() }.build(),
        )
        val workManager = WorkManager.getInstance(context)
        val scheduler = ReminderScheduler(context, workManager, ReminderEngine())
        val noonTomorrow = LocalDate.now().plusDays(1).atTime(12, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        scheduler.scheduleActivityReminder(
            activityId = "pressure-w1",
            title = "5-minute breathing before Board review",
            body = "Inhale 4 seconds, exhale 6.",
            triggerAtMillis = noonTomorrow,
        )
        assertEquals(1, workManager.getWorkInfosForUniqueWork("activity-pressure-w1").get().size)

        scheduler.purgeRemindersAfterErasure()

        val left = workManager.getWorkInfosForUniqueWork("activity-pressure-w1").get()
        assertTrue(left.all { it.state == WorkInfo.State.CANCELLED })
    }

    // --- The background refresh -----------------------------------------------------------------

    @Test
    fun digestPlan_isAnnouncedOnlyWhenNoErasureBeganSinceTheWorkerStarted() {
        val plan = DailyPlan(
            dateEpochDay = today,
            headline = "Steady day",
            band = ReadinessBand.BALANCED,
            activities = emptyList(),
        )

        assertEquals(plan, digestPlan(plan, startedAt = 3, current = 3))
        assertNull(digestPlan(plan, startedAt = 3, current = 4))
        assertNull(digestPlan(null, startedAt = 3, current = 3))
    }

    @Test
    fun ignoringFailures_swallowsAFailedRedraw_butRethrowsAStop() = runBlocking<Unit> {
        ignoringFailures { error("widget host gone") }

        val stop = runCatching { ignoringFailures { throw CancellationException("worker stopped") } }

        assertTrue(stop.exceptionOrNull() is CancellationException)
    }

    // --- Two weeks of check-offs behind the streak summary ---------------------------------------

    private fun done(day: Long) = DailyActivityEntity(
        id = "done-$day",
        dateEpochDay = day,
        title = "Walk",
        category = ActivityCategory.ZONE2,
        priority = ActivityPriority.RECOMMENDED,
        whyItMatters = "Steady work helps.",
        relatedMetric = null,
        instructions = "",
        estimatedMinutes = 15,
        intensity = Intensity.EASY,
        bestTimeOfDay = TimeOfDay.MIDDAY,
        status = ActivityStatus.DONE,
        ruleId = RuleIds.LOW_RECOVERY,
    )

    @Test
    fun streakFlags_spanTwoWeeksOldestFirst_andEndInTheWeekStrip() = runBlocking<Unit> {
        // Active today, two days ago, last week, and the window's first day — and one day before it.
        val active = setOf(today, today - 2, today - 8, today - 13)
        (active + (today - 14)).forEach { dao.upsertActivity(done(it)) }

        val twoWeeks = repository.consistencyFlags(STREAK_WINDOW_DAYS, today)

        assertEquals(14, twoWeeks.size)
        assertEquals((13L downTo 0L).map { back -> (today - back) in active }, twoWeeks)
        // The week strip is the last seven of the same flags, so Today and Progress agree.
        assertEquals(repository.consistencyFlags(CONSISTENCY_WINDOW_DAYS, today), twoWeeks.takeLast(CONSISTENCY_WINDOW_DAYS))
    }
}
