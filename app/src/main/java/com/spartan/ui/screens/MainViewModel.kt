package com.spartan.ui.screens

import android.content.Context
import android.net.Uri
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spartan.ui.widget.NextActivityWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import com.spartan.data.calendar.AvailabilityService
import com.spartan.data.calendar.CalendarAuthManager
import com.spartan.data.calendar.CalendarClient
import com.spartan.data.export.LocalExportFormatter
import com.spartan.data.local.ConnectionStatus
import com.spartan.data.local.IntegrationConnectionEntity
import com.spartan.data.local.IntegrationProvider
import com.spartan.data.local.MetricEntryEntity
import com.spartan.data.local.PlanWorkoutOverrideEntity
import com.spartan.data.local.PreferencesStore
import com.spartan.data.local.WhoopCycleDao
import com.spartan.data.local.ReminderEntity
import com.spartan.data.local.ReminderFrequency
import com.spartan.data.local.TargetEntity
import com.spartan.data.local.UserProfileEntity
import com.spartan.data.local.WorkoutSessionEntity
import com.spartan.data.local.toDomain
import com.spartan.data.reminder.ReminderScheduler
import com.spartan.data.repository.HealthRepository
import com.spartan.data.whoop.WhoopAuthManager
import com.spartan.data.whoop.WhoopClient
import com.spartan.data.whoop.csv.WhoopCsvImporter
import com.spartan.domain.engine.InsightEngine
import com.spartan.domain.engine.MetricEngine
import com.spartan.domain.engine.PlanEngine
import com.spartan.domain.engine.ReviewEngine
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.DailyPlan
import com.spartan.domain.model.InsightCard
import com.spartan.domain.model.MetricAssessment
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.PlannedWorkout
import com.spartan.domain.model.ReadinessBand
import com.spartan.domain.model.ReadinessSnapshot
import com.spartan.domain.model.TargetValue
import com.spartan.domain.engine.MetricProjection
import com.spartan.domain.engine.ProjectionEngine
import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.WeeklyPlan
import com.spartan.domain.model.WeeklyReviewSummary
import com.spartan.domain.model.WhoopSnapshot
import com.spartan.domain.model.WorkoutLog
import com.spartan.domain.model.WorkoutType
import com.spartan.domain.usecase.DailyPlanSync
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class MainUiState(
    val onboardingComplete: Boolean = false,
    val notificationDenied: Boolean = false,
    val notificationsAvailable: Boolean = false,
    val profile: UserProfileEntity? = null,
    val readings: List<MetricReading> = emptyList(),
    val assessments: List<MetricAssessment> = emptyList(),
    val insights: List<InsightCard> = emptyList(),
    val weeklyPlan: WeeklyPlan? = null,
    val review: WeeklyReviewSummary? = null,
    val reminders: List<ReminderEntity> = emptyList(),
    val exportText: String = "",
    // Spartan daily check-in
    val todayActivities: List<DailyActivity> = emptyList(),
    val planHeadline: String = "",
    val readinessBand: ReadinessBand? = null,
    val recoveryScore: Int? = null,
    val planSafetyBanner: String? = null,
    val whoopIsMock: Boolean = true,
    val whoopConnected: Boolean = false,
    val calendarConnected: Boolean = false,
    val syncFailed: Boolean = false,
    /** Days with ≥1 completed activity in the trailing week — calm consistency, not gamification. */
    val consistencyDays7: Int = 0,
    /** True when the in-app review prompt should be shown (rate-limited; positive moments only). */
    val requestReview: Boolean = false,
    /** Expected-improvement ranges at the current consistency (typical ranges, never guarantees). */
    val projections: List<MetricProjection> = emptyList(),
    /** State of an in-flight or finished WHOOP CSV import (null when none this session). */
    val whoopImport: WhoopImportUiState? = null,
    /** Persistent summary of imported WHOOP data, for the Metrics-tab banner (null when none). */
    val whoopImportInfo: WhoopImportInfo? = null,
)

/** Persistent "your WHOOP data is in" summary derived from the imported cycle table. */
data class WhoopImportInfo(
    val days: Int,
    val firstDayEpoch: Long,
    val lastDayEpoch: Long,
)

/** Progress + outcome of a WHOOP CSV import, rendered on the Connections screen. */
data class WhoopImportUiState(
    val inProgress: Boolean = false,
    val summary: WhoopCsvImporter.Summary? = null,
    val failed: Boolean = false,
    /** Files that WERE recognized when the import still failed (e.g. journal-only pick). */
    val failedButRecognized: List<String> = emptyList(),
)

private data class HealthBundle(
    val profile: UserProfileEntity?,
    val rawMetrics: List<MetricEntryEntity>,
    val rawTargets: List<TargetEntity>,
    val rawWorkouts: List<WorkoutSessionEntity>,
    val readings: List<MetricReading>,
    val targets: List<TargetValue>,
    val logs: List<WorkoutLog>,
    val reminders: List<ReminderEntity>,
    val exportText: String,
    val planOverrides: List<PlanWorkoutOverrideEntity> = emptyList(),
    val whoopImportInfo: WhoopImportInfo? = null,
)

private data class CheckInBundle(
    val activities: List<DailyActivity>,
    val plan: DailyPlan?,
    val readiness: ReadinessSnapshot?,
    val whoopConnected: Boolean,
    val calendarConnected: Boolean,
    val consistencyDays7: Int,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: HealthRepository,
    private val preferencesStore: PreferencesStore,
    private val metricEngine: MetricEngine,
    private val insightEngine: InsightEngine,
    private val planEngine: PlanEngine,
    private val reviewEngine: ReviewEngine,
    private val reminderScheduler: ReminderScheduler,
    private val whoopClient: WhoopClient,
    private val dailyPlanSync: DailyPlanSync,
    private val calendarClient: CalendarClient,
    private val availabilityService: AvailabilityService,
    private val whoopAuthManager: WhoopAuthManager,
    private val calendarAuthManager: CalendarAuthManager,
    private val whoopCsvImporter: WhoopCsvImporter,
    private val whoopCycleDao: WhoopCycleDao,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val today = LocalDate.now().toEpochDay()
    private val projectionEngine = ProjectionEngine()
    private val generatedPlan = MutableStateFlow<DailyPlan?>(null)
    private val readinessState = MutableStateFlow<ReadinessSnapshot?>(null)
    private val latestSnapshot = MutableStateFlow<WhoopSnapshot?>(null)
    private val syncFailed = MutableStateFlow(false)
    private val reviewRequested = MutableStateFlow(false)
    private val whoopImportState = MutableStateFlow<WhoopImportUiState?>(null)

    /** Persistent imported-data summary for the Metrics banner; null when nothing is imported. */
    private val whoopImportInfoFlow = whoopCycleDao.observeImportInfo().map { row ->
        if (row.dayCount == 0 || row.firstDay == null || row.lastDay == null) null
        else WhoopImportInfo(days = row.dayCount, firstDayEpoch = row.firstDay, lastDayEpoch = row.lastDay)
    }

    /** Transient signals folded into one flow (combine caps at five inputs). */
    private val transientFlags = combine(syncFailed, reviewRequested, whoopImportState) { s, r, i ->
        Triple(s, r, i)
    }

    private val healthBundle = combine(
        repository.profile,
        repository.metrics,
        repository.targets,
        repository.workouts,
        repository.reminders,
    ) { profile, metrics, targets, workouts, reminders ->
        val readings = metrics.map {
            MetricReading(
                type = it.type,
                value = it.value,
                recordedAt = LocalDate.ofEpochDay(it.recordedAtEpochDay),
                note = it.note,
                id = it.id,
            )
        }
        val targetValues = targets.map {
            TargetValue(
                metricType = it.metricType,
                minValue = it.minValue,
                maxValue = it.maxValue,
                note = it.note,
            )
        }
        val logs = workouts.map {
            WorkoutLog(
                type = it.type,
                plannedMinutes = it.plannedMinutes,
                completedMinutes = it.completedMinutes,
                rpe = it.rpe,
                painFlag = it.painFlag,
                completedAt = LocalDate.ofEpochDay(it.completedAtEpochDay),
            )
        }
        HealthBundle(
            profile = profile,
            rawMetrics = metrics,
            rawTargets = targets,
            rawWorkouts = workouts,
            readings = readings,
            targets = targetValues,
            logs = logs,
            reminders = reminders,
            exportText = LocalExportFormatter.format(profile, metrics, targets, workouts, reminders = reminders),
        )
    }.let { baseFlow ->
        combine(baseFlow, repository.planOverrides, whoopImportInfoFlow) { base, overrides, importInfo ->
            base.copy(
                planOverrides = overrides,
                whoopImportInfo = importInfo,
                exportText = LocalExportFormatter.format(
                    profile = base.profile,
                    metrics = base.rawMetrics,
                    targets = base.rawTargets,
                    workouts = base.rawWorkouts,
                    planOverrides = overrides,
                    reminders = base.reminders,
                ),
            )
        }
    }

    private val consistencyFlow = repository.dailyActivities(today)
        .map { repository.consistencyDays(7, today) }

    private val checkInBundle = combine(
        repository.dailyActivities(today),
        repository.connections,
        generatedPlan,
        readinessState,
        consistencyFlow,
    ) { entities, connections, plan, readiness, consistency ->
        CheckInBundle(
            activities = entities.map { it.toDomain() },
            plan = plan,
            readiness = readiness,
            whoopConnected = connections.any {
                it.provider == IntegrationProvider.WHOOP && it.status == ConnectionStatus.CONNECTED
            },
            calendarConnected = connections.any {
                it.provider == IntegrationProvider.GOOGLE_CALENDAR && it.status == ConnectionStatus.CONNECTED
            },
            consistencyDays7 = consistency,
        )
    }

    val uiState: StateFlow<MainUiState> = combine(
        preferencesStore.onboardingComplete,
        preferencesStore.notificationPermissionDenied,
        healthBundle,
        checkInBundle,
        transientFlags,
    ) { onboardingComplete, notificationDenied, health, checkIn, (syncDidFail, reviewWanted, whoopImport) ->
        val latest = latestReadings(health.readings)
        val targetMap = health.targets.associateBy(TargetValue::metricType)
        // Assess only values the engine considers valid: one out-of-range persisted row
        // (e.g. from an imported export) must never be able to crash the whole UI state.
        val assessments = latest
            .filter { metricEngine.validate(it.type, it.value) }
            .map { metricEngine.assess(it, targetMap[it.type]) }
        val plan = applyPlanOverrides(planEngine.defaultPlan(health.logs), health.planOverrides)
        val review = reviewEngine.summarize(health.readings, health.logs)
        MainUiState(
            onboardingComplete = onboardingComplete,
            notificationDenied = notificationDenied,
            notificationsAvailable = reminderScheduler.hasNotificationPermission(),
            profile = health.profile,
            readings = health.readings,
            assessments = assessments,
            insights = insightEngine.generate(assessments),
            weeklyPlan = plan,
            review = review,
            reminders = health.reminders,
            exportText = health.exportText,
            todayActivities = checkIn.activities,
            planHeadline = checkIn.plan?.headline ?: "",
            readinessBand = checkIn.readiness?.band,
            recoveryScore = checkIn.readiness?.recoveryScore,
            planSafetyBanner = checkIn.plan?.safetyBanner,
            whoopIsMock = checkIn.plan?.isMock ?: whoopClient.isMock,
            whoopConnected = checkIn.whoopConnected,
            calendarConnected = checkIn.calendarConnected,
            syncFailed = syncDidFail,
            consistencyDays7 = checkIn.consistencyDays7,
            requestReview = reviewWanted,
            whoopImport = whoopImport,
            whoopImportInfo = health.whoopImportInfo,
            projections = projectionEngine.project(
                restingHeartRate = checkIn.readiness?.restingHeartRate
                    ?: latestValue(health.readings, MetricType.RESTING_HEART_RATE),
                hrvMs = checkIn.readiness?.hrvMs ?: latestValue(health.readings, MetricType.HRV_RMSSD),
                recoveryScore = checkIn.readiness?.recoveryScore
                    ?: latestValue(health.readings, MetricType.RECOVERY_SCORE)?.toInt(),
                consistencyDays7 = checkIn.consistencyDays7,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    private fun latestValue(readings: List<MetricReading>, type: MetricType): Double? =
        readings.filter { it.type == type && it.value != null }.maxByOrNull { it.recordedAt }?.value

    init {
        loadToday()
    }

    /**
     * Run the shared daily sync ([DailyPlanSync] — same path the background worker uses). A failed
     * sync (offline, expired token) never crashes or wipes the last-known plan — it just sets
     * [syncFailed] so the UI can say so quietly.
     */
    fun loadToday() {
        viewModelScope.launch {
            preferencesStore.recordFirstOpenIfNeeded(System.currentTimeMillis())
            refreshPlan(forceReseed = false)
        }
    }

    private suspend fun refreshPlan(forceReseed: Boolean) {
        val outcome = dailyPlanSync.sync(today, forceReseed = forceReseed)
        if (outcome.failed) {
            syncFailed.value = true
            return
        }
        syncFailed.value = false
        readinessState.value = outcome.readiness
        generatedPlan.value = outcome.plan
        latestSnapshot.value = outcome.latestSnapshot
    }

    /**
     * Imports a WHOOP CSV export picked by the user, then rebuilds today's plan from the real
     * data (completed check-ins are kept; pending sample-driven items are replaced).
     */
    fun importWhoopCsv(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (whoopImportState.value?.inProgress == true) return // one import at a time
        viewModelScope.launch {
            whoopImportState.value = WhoopImportUiState(inProgress = true)
            whoopCsvImporter.import(uris)
                .onSuccess { summary ->
                    cancelPendingActivityReminders()
                    refreshPlan(forceReseed = true)
                    whoopImportState.value = WhoopImportUiState(summary = summary)
                }
                .onFailure { failure ->
                    whoopImportState.value = WhoopImportUiState(
                        failed = true,
                        failedButRecognized = (failure as? WhoopCsvImporter.ImportError.NoUsableData)
                            ?.recognized ?: emptyList(),
                    )
                }
        }
    }

    fun dismissWhoopImportResult() {
        whoopImportState.value = null
    }

    /**
     * A force-reseed replaces today's pending activities; any one-shot reminders armed for their
     * snoozed/rescheduled times must die with them or they'd fire for items that no longer exist.
     */
    private suspend fun cancelPendingActivityReminders() {
        repository.dailyActivities(today).first()
            .filter { it.status == ActivityStatus.SNOOZED || it.status == ActivityStatus.RESCHEDULED }
            .forEach { reminderScheduler.cancelActivityReminder(it.id) }
    }

    fun seed() {
        viewModelScope.launch {
            if (!preferencesStore.demoSeedCompleted.first()) {
                repository.seedIfEmpty()
                preferencesStore.setDemoSeedCompleted(true)
            }
        }
    }

    fun completeOnboarding(name: String, heightCm: Double?) {
        viewModelScope.launch {
            repository.upsertProfile(UserProfileEntity(displayName = name.ifBlank { "You" }, heightCm = heightCm))
            preferencesStore.setOnboardingComplete(true)
        }
    }

    fun setNotificationDenied(denied: Boolean) {
        viewModelScope.launch { preferencesStore.setNotificationPermissionDenied(denied) }
    }

    fun addMetric(type: MetricType, rawValue: String, note: String): Boolean {
        val value = rawValue.toDoubleOrNull()
        if (!metricEngine.validate(type, value)) return false
        viewModelScope.launch { repository.addMetric(type, value, note) }
        return true
    }

    fun updateMetric(id: Long, type: MetricType, rawValue: String, note: String): Boolean {
        val value = rawValue.toDoubleOrNull()
        if (!metricEngine.validate(type, value)) return false
        viewModelScope.launch { repository.updateMetric(id, type, value, note) }
        return true
    }

    fun completeWorkout(type: WorkoutType, planned: Int, completed: Int, rpe: Int, pain: Boolean) {
        viewModelScope.launch { repository.addWorkout(type, planned, completed, rpe, pain) }
    }

    /**
     * Exercise-tracking debrief after checking off a training activity: logs a workout session
     * (actual minutes, RPE, pain flag) that the adaptive rules already consume — pain or high RPE
     * deloads next week's plan, closing the coach→do→adapt loop.
     */
    fun logExerciseDebrief(activity: DailyActivity, actualMinutes: Int, rpe: Int, pain: Boolean) {
        val type = when (activity.category) {
            ActivityCategory.STRENGTH -> WorkoutType.STRENGTH
            ActivityCategory.MOBILITY, ActivityCategory.RECOVERY -> WorkoutType.MOBILITY
            else -> WorkoutType.ZONE_2 // ZONE2 / MOVEMENT map to easy aerobic work
        }
        viewModelScope.launch {
            repository.addWorkout(
                type = type,
                planned = activity.estimatedMinutes,
                completed = actualMinutes.coerceIn(1, 300),
                rpe = rpe.coerceIn(1, 10),
                pain = pain,
            )
        }
    }

    fun savePlanMinutes(slotKey: String, minutes: Int) {
        viewModelScope.launch { repository.savePlanOverride(slotKey, minutes) }
    }

    // --- Daily check-in actions ---
    fun completeActivity(id: String) {
        viewModelScope.launch {
            repository.updateActivityStatus(id, ActivityStatus.DONE, completedAtMillis = System.currentTimeMillis())
            maybeRequestReview()
        }
    }

    /**
     * In-app review: only at a positive moment (finished today's whole plan), only after a week of
     * use, and at most every 90 days. Never after an error state.
     */
    private suspend fun maybeRequestReview() {
        val activities = repository.dailyActivities(today).first().map { it.toDomain() }
        val planFinished = activities.isNotEmpty() &&
            activities.any { it.status == ActivityStatus.DONE } &&
            activities.all { it.status == ActivityStatus.DONE || it.status == ActivityStatus.SKIPPED }
        if (!planFinished || syncFailed.value) return
        val now = System.currentTimeMillis()
        val firstOpen = preferencesStore.firstOpenMillis.first() ?: now
        val lastPrompt = preferencesStore.lastReviewPromptMillis.first() ?: 0L
        val week = 7L * 24 * 60 * 60 * 1000
        val ninetyDays = 90L * 24 * 60 * 60 * 1000
        if (now - firstOpen >= week && now - lastPrompt >= ninetyDays) {
            reviewRequested.value = true
        }
    }

    /** Called after the Play review flow has been launched (or declined by the system). */
    fun onReviewPromptShown() {
        reviewRequested.value = false
        viewModelScope.launch { preferencesStore.setLastReviewPromptMillis(System.currentTimeMillis()) }
    }

    fun uncompleteActivity(id: String) {
        viewModelScope.launch { repository.updateActivityStatus(id, ActivityStatus.PLANNED) }
    }

    fun snoozeActivity(id: String, minutes: Int = 60) {
        val activity = uiState.value.todayActivities.firstOrNull { it.id == id }
        viewModelScope.launch {
            val wakeAtMillis = System.currentTimeMillis() + minutes * 60_000L
            repository.updateActivityStatus(id, ActivityStatus.SNOOZED, snoozedUntilMillis = wakeAtMillis)
            // Snooze means "remind me later", so schedule the later.
            if (activity != null) {
                reminderScheduler.scheduleActivityReminder(
                    activityId = id,
                    title = "Back on: ${activity.title}",
                    body = "~${activity.estimatedMinutes} min. ${activity.whyItMatters}",
                    triggerAtMillis = wakeAtMillis,
                )
            }
        }
    }

    fun skipActivity(id: String) {
        viewModelScope.launch { repository.updateActivityStatus(id, ActivityStatus.SKIPPED) }
    }

    /**
     * Find the earliest calendar gap that fits the activity and reschedule it there. The window is
     * anchored to the user's actual sleep pattern from WHOOP (wake + 30 min → bed − 60 min) so a
     * nudge never lands during sleep; static 08:00–21:00 is only the no-data fallback.
     */
    fun scheduleActivity(id: String) {
        val activity = uiState.value.todayActivities.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val date = LocalDate.now()
            val snap = latestSnapshot.value
            val startMinuteOfDay = maxOf(8 * 60, (snap?.wakeMinuteOfDay ?: (8 * 60 - 30)) + 30)
            val endMinuteOfDay = ((snap?.bedMinuteOfDay ?: (22 * 60)) - 60)
                .coerceIn(startMinuteOfDay + 30, 23 * 60)
            val dayStartEpochMinute = date.atStartOfDay(zone).toEpochSecond() / 60
            val start = dayStartEpochMinute + startMinuteOfDay
            val end = dayStartEpochMinute + endMinuteOfDay
            val busy = calendarClient.freeBusy(start, end)
            val slot = availabilityService.suggestSlot(activity.estimatedMinutes, start, end, busy) ?: return@launch
            repository.updateActivityStatus(
                id,
                ActivityStatus.RESCHEDULED,
                scheduledEpochMinute = slot.startEpochMinute,
            )
            reminderScheduler.scheduleActivityReminder(
                activityId = id,
                title = "Time for: ${activity.title}",
                body = "~${activity.estimatedMinutes} min. ${activity.whyItMatters}",
                triggerAtMillis = slot.startEpochMinute * 60_000L,
            )
            if (uiState.value.calendarConnected) {
                calendarClient.createEvent(activity.title, slot.startEpochMinute, activity.estimatedMinutes)
            }
        }
    }

    // --- Integration consent ---
    fun connectWhoop() {
        viewModelScope.launch {
            repository.setConnection(
                provider = IntegrationProvider.WHOOP,
                status = ConnectionStatus.CONNECTED,
                scopes = "read:recovery read:sleep read:workout read:cycles read:profile offline",
                accountLabel = if (whoopClient.isMock) "Sample data" else null,
            )
        }
    }

    fun disconnectWhoop() {
        viewModelScope.launch {
            whoopAuthManager.disconnect() // clears stored tokens, matching the privacy policy
            // Disconnect stops the source: imported cycles/workouts are removed so syncs fall
            // back to labeled sample data. Normalized readings stay as the user's history —
            // disconnect never silently destroys data; deletion lives in Privacy.
            repository.clearImportedWhoopSource()
            repository.setConnection(IntegrationProvider.WHOOP, ConnectionStatus.NOT_CONNECTED)
            cancelPendingActivityReminders()
            refreshPlan(forceReseed = true)
        }
    }

    fun connectCalendar() {
        viewModelScope.launch {
            repository.setConnection(
                provider = IntegrationProvider.GOOGLE_CALENDAR,
                status = ConnectionStatus.CONNECTED,
                scopes = "calendar.freebusy",
                accountLabel = if (calendarClient.isStub) "Sample data" else null,
            )
        }
    }

    fun disconnectCalendar() {
        viewModelScope.launch {
            calendarAuthManager.disconnect() // clears stored tokens, matching the privacy policy
            repository.setConnection(IntegrationProvider.GOOGLE_CALENDAR, ConnectionStatus.NOT_CONNECTED)
        }
    }

    fun saveReminder(
        id: String,
        title: String,
        body: String,
        hour: Int,
        minute: Int,
        enabled: Boolean,
        frequency: ReminderFrequency = ReminderFrequency.DAILY,
        daysOfWeekMask: Int = 127,
    ) {
        viewModelScope.launch {
            val canEnable = !enabled || reminderScheduler.hasNotificationPermission()
            if (!canEnable) preferencesStore.setNotificationPermissionDenied(true)
            val reminder = ReminderEntity(
                id = id,
                title = title,
                body = body,
                hour = hour,
                minute = minute,
                enabled = enabled && canEnable,
                frequency = frequency,
                daysOfWeekMask = daysOfWeekMask,
            )
            repository.upsertReminder(reminder)
            reminderScheduler.schedule(reminder)
        }
    }

    fun deleteAllLocalData() {
        viewModelScope.launch {
            // Erasure-grade: disarm every scheduled job (or the plan-refresh worker would
            // repopulate the emptied DB), purge WorkManager's own DB, dismiss shown notifications.
            reminderScheduler.purgeAllForErasure()
            // Full deletion includes any OAuth tokens, per the privacy policy.
            whoopAuthManager.disconnect()
            calendarAuthManager.disconnect()
            repository.deleteAllLocalData()
            preferencesStore.clear()
            preferencesStore.setDemoSeedCompleted(true)
            // The home-screen widget must not keep rendering the deleted plan; with the DB empty
            // it falls back to its neutral state. Glance failure must never break erasure.
            runCatching { NextActivityWidget().updateAll(appContext) }
        }
    }

    private fun latestReadings(readings: List<MetricReading>): List<MetricReading> =
        MetricType.entries.mapNotNull { type ->
            readings.filter { it.type == type }.maxByOrNull { it.recordedAt }
        }

    private fun applyPlanOverrides(plan: WeeklyPlan, overrides: List<PlanWorkoutOverrideEntity>): WeeklyPlan {
        val overrideMap = overrides.associateBy(PlanWorkoutOverrideEntity::slotKey)
        return plan.copy(
            workouts = plan.workouts.map { workout ->
                overrideMap[planSlotKey(workout)]?.let { workout.copy(minutes = it.minutes) } ?: workout
            },
        )
    }

    companion object {
        fun planSlotKey(workout: PlannedWorkout): String = "${workout.day}-${workout.type.name}"
    }
}
