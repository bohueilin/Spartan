package com.spartan.robolectric

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spartan.data.local.AppDatabase
import com.spartan.data.local.DailyActivityEntity
import com.spartan.data.local.DailyReflectionEntity
import com.spartan.data.local.HealthDao
import com.spartan.data.local.MetricEntryEntity
import com.spartan.data.local.PreferencesStore
import com.spartan.data.repository.HealthRepository
import com.spartan.data.whoop.WhoopClient
import com.spartan.domain.engine.ActionPlaybook
import com.spartan.domain.engine.CoachingEngine
import com.spartan.domain.engine.RuleIds
import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.DailyPlan
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.ReadinessBand
import com.spartan.domain.model.ReflectionMood
import com.spartan.domain.model.TimeOfDay
import com.spartan.domain.model.WhoopSnapshot
import com.spartan.domain.usecase.DailyPlanSync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The real queries behind goal progress, the sync's saved-plan check, the export and plan
 * regeneration, run against a real (in-memory) Room database — plus full erasure racing a sync
 * that was already running: whatever the sync fetched or wrote, the erased store stays erased.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthDaoTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: HealthDao
    private lateinit var repository: HealthRepository
    private val today = 20_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.healthDao()
        repository = HealthRepository(dao, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun activity(
        id: String,
        day: Long = today,
        category: ActivityCategory = ActivityCategory.ZONE2,
        status: ActivityStatus = ActivityStatus.PLANNED,
        ruleId: String = RuleIds.LOW_RECOVERY,
        instructions: String = "",
    ) = DailyActivityEntity(
        id = id,
        dateEpochDay = day,
        title = "Activity $id",
        category = category,
        priority = ActivityPriority.RECOMMENDED,
        whyItMatters = "Steady work helps.",
        relatedMetric = null,
        instructions = instructions,
        estimatedMinutes = 15,
        intensity = Intensity.EASY,
        bestTimeOfDay = TimeOfDay.MIDDAY,
        status = status,
        ruleId = ruleId,
    )

    private fun planned(id: String) = DailyActivity(
        id = id,
        title = "Activity $id",
        category = ActivityCategory.ZONE2,
        priority = ActivityPriority.RECOMMENDED,
        whyItMatters = "Steady work helps.",
        estimatedMinutes = 15,
        intensity = Intensity.EASY,
        bestTimeOfDay = TimeOfDay.MIDDAY,
        ruleId = RuleIds.LOW_RECOVERY,
    )

    // --- completedActivityCount ---------------------------------------------------------------

    @Test
    fun completedActivityCount_countsDoneSessionsInTheCategoriesAndWindowOnly() = runBlocking<Unit> {
        listOf(
            activity("z2-today", category = ActivityCategory.ZONE2, status = ActivityStatus.DONE),
            activity(
                "strength-6d",
                day = today - 6,
                category = ActivityCategory.STRENGTH,
                status = ActivityStatus.DONE,
            ),
            activity("z2-7d", day = today - 7, category = ActivityCategory.ZONE2, status = ActivityStatus.DONE),
            activity("z2-planned", category = ActivityCategory.ZONE2, status = ActivityStatus.PLANNED),
            activity("z2-skipped", category = ActivityCategory.ZONE2, status = ActivityStatus.SKIPPED),
            activity("mobility", category = ActivityCategory.MOBILITY, status = ActivityStatus.DONE),
            activity("wind-down", category = ActivityCategory.SLEEP, status = ActivityStatus.DONE),
            // Not sessions, whatever their category: the bedtime anchor and the clinician check-in.
            activity(
                "anchor",
                category = ActivityCategory.SLEEP,
                status = ActivityStatus.DONE,
                ruleId = RuleIds.SLEEP_ANCHOR,
            ),
            activity(
                "clinician",
                category = ActivityCategory.RECOVERY,
                status = ActivityStatus.DONE,
                ruleId = RuleIds.CLINICIAN_REFERRAL,
            ),
        ).forEach { dao.upsertActivity(it) }

        val training = setOf(ActivityCategory.ZONE2, ActivityCategory.STRENGTH)
        val recovery = setOf(ActivityCategory.MOBILITY, ActivityCategory.RECOVERY, ActivityCategory.SLEEP)
        assertEquals(2, repository.completedActivityCount(training, today - 6, today))
        assertEquals(2, repository.completedActivityCount(recovery, today - 6, today))
        assertEquals(3, repository.completedActivityCount(training, today - 7, today))
        // No categories (a goal type with no sessions) is zero without a query.
        assertEquals(0, repository.completedActivityCount(emptySet(), today - 6, today))
    }

    // --- activitiesForDay ---------------------------------------------------------------------

    @Test
    fun activitiesForDay_isThatDaysGeneratedAndUserAddedActivities_asDomain() = runBlocking<Unit> {
        dao.upsertActivity(activity("gen", instructions = "Warm up.\nWalk briskly."))
        dao.upsertActivity(activity("mine", ruleId = ActionPlaybook.USER_ADDED_RULE))
        dao.upsertActivity(activity("yesterday", day = today - 1))

        val day = repository.activitiesForDay(today)

        assertEquals(setOf("gen", "mine"), day.map { it.id }.toSet())
        assertEquals(listOf("Warm up.", "Walk briskly."), day.single { it.id == "gen" }.instructions)
        assertEquals(emptyList<String>(), day.single { it.id == "mine" }.instructions)
        assertTrue(repository.activitiesForDay(today + 1).isEmpty())
    }

    // --- observeAllReflections ----------------------------------------------------------------

    @Test
    fun allReflections_isEveryReflectionNewestFirst_whereTheRecentPageStopsAtThirty() = runBlocking<Unit> {
        (0L until 35L).forEach { back ->
            dao.upsertReflection(DailyReflectionEntity(dateEpochDay = today - back, mood = ReflectionMood.OKAY.name))
        }
        // A second save for a day overwrites it rather than adding a row.
        repository.saveReflection(today, ReflectionMood.STRONG, "  good day  ")

        val all = repository.allReflections.first()
        assertEquals(35, all.size)
        assertEquals((0L until 35L).map { today - it }, all.map { it.dateEpochDay })
        assertEquals(ReflectionMood.STRONG.name, all.first().mood)
        assertEquals("good day", all.first().note)
        assertEquals(30, repository.reflections.first().size)
    }

    // --- reseedDailyPlan ----------------------------------------------------------------------

    @Test
    fun reseedDailyPlan_returnsTheArmedGeneratedActivities_andKeepsDoneAndUserAdded() = runBlocking<Unit> {
        listOf(
            activity("p", status = ActivityStatus.PLANNED),
            activity("s", status = ActivityStatus.SNOOZED),
            activity("r", status = ActivityStatus.RESCHEDULED),
            activity("d", status = ActivityStatus.DONE),
            activity("u", status = ActivityStatus.SNOOZED, ruleId = ActionPlaybook.USER_ADDED_RULE),
            activity("other-day", day = today - 1, status = ActivityStatus.SNOOZED),
        ).forEach { dao.upsertActivity(it) }
        val plan = DailyPlan(
            dateEpochDay = today,
            headline = "Steady day",
            band = ReadinessBand.BALANCED,
            activities = listOf(planned("n"), planned("d")),
        )

        val armed = repository.reseedDailyPlan(plan)

        // Only the replaced generated items that had a reminder armed; the user's own stays.
        assertEquals(setOf("s", "r"), armed.toSet())
        val after = dao.activitiesForDay(today).associateBy { it.id }
        assertEquals(setOf("d", "u", "n"), after.keys)
        // A completed item is history: the regenerated plan never resets it.
        assertEquals(ActivityStatus.DONE, after.getValue("d").status)
        assertEquals(ActivityStatus.SNOOZED, after.getValue("u").status)
        assertEquals(1, dao.activitiesForDay(today - 1).size)
        // Nothing armed to cancel when the day held none.
        assertTrue(repository.reseedDailyPlan(plan.copy(dateEpochDay = today + 1)).isEmpty())
    }

    // --- The widget's one-row read ------------------------------------------------------------

    @Test
    fun latestReadingOnDay_isTheNewestValuedRowOfThatTypeAndDay() = runBlocking<Unit> {
        fun row(type: MetricType, value: Double?, day: Long, createdAt: Long) = MetricEntryEntity(
            type = type,
            value = value,
            unit = type.unit,
            note = "WHOOP",
            recordedAtEpochDay = day,
            createdAtMillis = createdAt,
        )
        dao.insertMetric(row(MetricType.RECOVERY_SCORE, 40.0, today, createdAt = 1))
        dao.insertMetric(row(MetricType.RECOVERY_SCORE, 55.0, today, createdAt = 2))
        dao.insertMetric(row(MetricType.RECOVERY_SCORE, null, today, createdAt = 3))
        dao.insertMetric(row(MetricType.RECOVERY_SCORE, 90.0, today - 1, createdAt = 4))
        dao.insertMetric(row(MetricType.HRV_RMSSD, 70.0, today, createdAt = 5))

        assertEquals(55.0, dao.latestReadingOnDay(MetricType.RECOVERY_SCORE, today)?.value!!, 0.0)
        assertEquals(90.0, dao.latestReadingOnDay(MetricType.RECOVERY_SCORE, today - 1)?.value!!, 0.0)
        assertNull(dao.latestReadingOnDay(MetricType.RECOVERY_SCORE, today + 1))
    }

    // --- Erasure vs a sync already in flight (PR-1) -------------------------------------------

    @Test
    fun writeUnlessErasedSince_runsUntilAnErasure_thenRefusesWritersFromBeforeIt() = runBlocking<Unit> {
        val before = repository.erasureGeneration
        assertEquals("ran", repository.writeUnlessErasedSince(before) { "ran" })

        repository.deleteAllLocalData()

        assertNotEquals(before, repository.erasureGeneration)
        var ran = false
        assertNull(repository.writeUnlessErasedSince(before) { ran = true })
        assertFalse(ran)
        // A writer that started after the erasure is a fresh start and runs.
        assertEquals("fresh", repository.writeUnlessErasedSince(repository.erasureGeneration) { "fresh" })
    }

    @Test
    fun erasure_waitsForAWriterAlreadyHoldingTheStore_thenRemovesWhatItWrote() = runBlocking<Unit> {
        val generation = repository.erasureGeneration
        val holding = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writer = async {
            repository.writeUnlessErasedSince(generation) {
                repository.addMetric(MetricType.WEIGHT, 80.0, date = LocalDate.ofEpochDay(today))
                holding.complete(Unit)
                release.await()
                "written"
            }
        }
        holding.await()

        val erasure = async { repository.deleteAllLocalData() }
        yield()
        // The writer still holds the store, so the erasure cannot have run yet.
        assertFalse(erasure.isCompleted)
        assertEquals(1, dao.metricCount())

        release.complete(Unit)
        assertEquals("written", writer.await())
        erasure.await()
        assertEquals(0, dao.metricCount())
    }

    /** A WHOOP feed that, mid-fetch, has the user erase everything — the race finding PR-1 names. */
    private class ErasedMidFetch(
        private val repository: HealthRepository,
        private val snapshots: List<WhoopSnapshot>,
    ) : WhoopClient {
        override val isMock: Boolean = true

        override suspend fun fetchRecentDays(days: Int): List<WhoopSnapshot> {
            repository.deleteAllLocalData()
            return snapshots
        }
    }

    private class FixedFeed(private val snapshots: List<WhoopSnapshot>) : WhoopClient {
        override val isMock: Boolean = true

        override suspend fun fetchRecentDays(days: Int): List<WhoopSnapshot> = snapshots
    }

    private val night = WhoopSnapshot(
        dateEpochDay = today,
        recoveryScore = 72,
        hrvMs = 60.0,
        restingHeartRate = 55.0,
        sleepPerformance = 88,
        sleepDurationHours = 7.0,
        wakeMinuteOfDay = 6 * 60 + 30,
        isMock = true,
    )

    private fun syncWith(client: WhoopClient) =
        DailyPlanSync(repository, client, CoachingEngine(), PreferencesStore(context), context)

    @Test
    fun sync_erasedWhileFetching_writesNothing_andIsSuperseded() = runBlocking<Unit> {
        dao.upsertActivity(activity("yesterdays-plan", status = ActivityStatus.DONE))

        val outcome = syncWith(ErasedMidFetch(repository, listOf(night))).sync(today)

        assertTrue(outcome.superseded)
        assertFalse(outcome.failed)
        assertNull(outcome.plan)
        assertNull(outcome.readiness)
        // The fetched day was never written back, and the erased plan stayed erased.
        assertTrue(dao.observeMetrics().first().isEmpty())
        assertTrue(dao.activitiesForDay(today).isEmpty())
    }

    @Test
    fun sync_withoutAnErasure_persistsAndSeedsAsBefore() = runBlocking<Unit> {
        val outcome = syncWith(FixedFeed(listOf(night))).sync(today)

        assertFalse(outcome.superseded)
        assertFalse(outcome.failed)
        assertNotNull(outcome.plan)
        assertEquals(today, outcome.plan!!.dateEpochDay)
        assertEquals(72.0, dao.latestReadingOnDay(MetricType.RECOVERY_SCORE, today)?.value!!, 0.0)
        assertEquals(
            HealthRepository.SAMPLE_WHOOP_NOTE,
            dao.latestReadingOnDay(MetricType.RECOVERY_SCORE, today)?.note,
        )
        assertEquals(outcome.plan!!.activities.size, dao.activitiesForDay(today).size)
    }

    @Test
    fun sync_startedBeforeAnErasure_isSupersededEvenWhenItRunsAfterIt() = runBlocking<Unit> {
        val startedAt = repository.erasureGeneration
        repository.deleteAllLocalData()

        val outcome = syncWith(FixedFeed(listOf(night))).sync(today, erasureGeneration = startedAt)

        assertTrue(outcome.superseded)
        assertEquals(0, dao.metricCount())
    }
}
