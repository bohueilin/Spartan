package com.spartan.ui.screens

import android.content.Context
import android.net.Uri
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spartan.R
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
import com.spartan.data.local.toEntity
import com.spartan.data.whoop.csv.WhoopCsvImporter
import com.spartan.data.whoop.csv.toWhoopSnapshot
import com.spartan.domain.engine.Goal
import com.spartan.domain.engine.GoalEngine
import com.spartan.domain.engine.GoalPlanModifiers
import com.spartan.domain.engine.GoalProgress
import com.spartan.domain.engine.GoalStatus
import com.spartan.domain.engine.GoalType
import com.spartan.domain.engine.GoalValidation
import com.spartan.domain.engine.InsightEngine
import com.spartan.domain.engine.PressureWindow
import com.spartan.domain.engine.SexAtBirth
import com.spartan.domain.engine.StressPatterns
import com.spartan.domain.engine.WeekdayEffect
import com.spartan.domain.engine.MetricEngine
import com.spartan.domain.engine.PlanEngine
import com.spartan.domain.engine.ReviewEngine
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.ClinicalStatus
import com.spartan.domain.model.TargetStatus
import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.DailyPlan
import com.spartan.domain.model.InsightCard
import com.spartan.domain.model.MetricAssessment
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.PlannedWorkout
import com.spartan.domain.model.ReadinessBand
import com.spartan.domain.model.ReflectionMood
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class MainUiState(
    val onboardingComplete: Boolean = false,
    val notificationDenied: Boolean = false,
    val notificationsAvailable: Boolean = false,
    /**
     * True when Today should offer to turn on reminders: the plan is on screen (value delivered
     * first), permission is not granted, and the user has not already answered the offer.
     */
    val showRemindersOffer: Boolean = false,
    val profile: UserProfileEntity? = null,
    /** From the profile's birth year; tailors follow-along video picks to the user's age. */
    val userAgeYears: Int? = null,
    /** Metrics whose latest reading is outside its clinical range or personal target. */
    val offTargetMetrics: Set<MetricType> = emptySet(),
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
    /** True when the calendar integration is the offline stub — its data is sample, not the user's. */
    val calendarIsStub: Boolean = true,
    val syncFailed: Boolean = false,
    /** Days with ≥1 completed activity in the trailing week — calm consistency, not gamification. */
    val consistencyDays7: Int = 0,
    /** True when the in-app review prompt should be shown (rate-limited; positive moments only). */
    val requestReview: Boolean = false,
    /** Expected-improvement ranges at the current consistency (typical ranges, never guarantees). */
    val projections: List<MetricProjection> = emptyList(),
    /** Oldest→today: which of the trailing 7 days had a completed activity (consistency strip). */
    val consistencyFlags: List<Boolean> = emptyList(),
    /** True while a user-initiated refresh (pull-to-refresh) is in flight. */
    val isRefreshing: Boolean = false,
    /** State of an in-flight or finished WHOOP CSV import (null when none this session). */
    val whoopImport: WhoopImportUiState? = null,
    /** Persistent summary of imported WHOOP data, for the Metrics-tab banner (null when none). */
    val whoopImportInfo: WhoopImportInfo? = null,
    // Coach hub
    val activeGoal: Goal? = null,
    val goalProgress: GoalProgress? = null,
    /** Counter-offer / confirmation copy from the last goal save (transient, dismissible). */
    val goalNotice: String? = null,
    val pressureWindows: List<PressureWindow> = emptyList(),
    val weekdayEffects: List<WeekdayEffect> = emptyList(),
    val userSexAtBirth: SexAtBirth = SexAtBirth.UNSPECIFIED,
)

/**
 * Whether Today should offer to turn on reminders. Value first: the offer appears only once a plan
 * is actually on screen, never when permission is already granted, and never after the user has
 * answered it once (the system dialog is one-shot per install — it must not be spent at launch).
 */
internal fun shouldOfferReminders(
    hasActivities: Boolean,
    offerSettled: Boolean,
    permissionGranted: Boolean,
): Boolean = hasActivities && !offerSettled && !permissionGranted

/** The hour after which the day is done enough to reflect on it. */
internal const val REFLECTION_HOUR = 18

/**
 * Whether to offer the end-of-day reflection. Evening only, only when there was a plan to reflect
 * on, and never twice for the same day — an offer the user has already answered stays answered.
 */
internal fun shouldOfferReflection(
    hourOfDay: Int,
    hasActivities: Boolean,
    alreadyAnsweredToday: Boolean,
): Boolean = hourOfDay >= REFLECTION_HOUR && hasActivities && !alreadyAnsweredToday

/**
 * Everything needed to put an activity back exactly as it was before a snooze/skip/reschedule.
 * Captured before the write so Undo is a true restore, not a guess at the prior state.
 */
data class UndoToken(
    val activityId: String,
    val previousStatus: ActivityStatus,
    val previousSnoozedUntilMillis: Long? = null,
    val previousScheduledEpochMinute: Long? = null,
    val previousCompletedAtMillis: Long? = null,
)

/**
 * A one-shot, user-facing message (snackbar). An event rather than UI state: it is delivered once
 * and must not replay on rotation or tab switch. [undo] is present only for reversible actions.
 */
data class UserMessage(
    val textRes: Int,
    val formatArg: String? = null,
    val undo: UndoToken? = null,
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
    val coach: CoachBundle = CoachBundle(),
)

/** Coach-hub state derived from persisted goals/windows + imported cycles. */
data class CoachBundle(
    val activeGoal: Goal? = null,
    val goalProgress: GoalProgress? = null,
    val windows: List<PressureWindow> = emptyList(),
    val weekdayEffects: List<WeekdayEffect> = emptyList(),
)

private data class CheckInBundle(
    val activities: List<DailyActivity>,
    val plan: DailyPlan?,
    val readiness: ReadinessSnapshot?,
    val whoopConnected: Boolean,
    val calendarConnected: Boolean,
    val consistencyDays7: Int,
    val consistencyFlags: List<Boolean>,
)

@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest: day-scoped flows re-subscribe at midnight
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
    /**
     * The day the app is currently planning. The process routinely outlives local midnight, so
     * this is a flow that is re-read on a ticker rather than a value captured at construction —
     * otherwise Today keeps showing yesterday's plan and check-offs are written to yesterday's row.
     */
    private val todayFlow = MutableStateFlow(LocalDate.now().toEpochDay())

    /** The current day for imperative reads/writes; always the live value, never a stale capture. */
    private val today: Long get() = todayFlow.value

    /** Day-scoped activity flow that re-subscribes when the date rolls over. */
    private val dailyActivitiesFlow = todayFlow.flatMapLatest { repository.dailyActivities(it) }
    private val projectionEngine = ProjectionEngine()
    private val generatedPlan = MutableStateFlow<DailyPlan?>(null)
    private val readinessState = MutableStateFlow<ReadinessSnapshot?>(null)
    private val latestSnapshot = MutableStateFlow<WhoopSnapshot?>(null)
    private val syncFailed = MutableStateFlow(false)
    private val reviewRequested = MutableStateFlow(false)
    private val refreshing = MutableStateFlow(false)
    private val whoopImportState = MutableStateFlow<WhoopImportUiState?>(null)

    /** Persistent imported-data summary for the Metrics banner; null when nothing is imported. */
    private val whoopImportInfoFlow = whoopCycleDao.observeImportInfo().map { row ->
        if (row.dayCount == 0 || row.firstDay == null || row.lastDay == null) null
        else WhoopImportInfo(days = row.dayCount, firstDayEpoch = row.firstDay, lastDayEpoch = row.lastDay)
    }

    /** Goals + pressure windows + stress patterns for the Coach hub, all reactive. */
    private val coachFlow = combine(
        repository.goals,
        repository.pressureWindows,
        whoopCycleDao.observeRecentCycles(),
        repository.metrics,
        // Completing a calm session must move a stress goal's progress immediately: a one-shot
        // read here would only refresh when some other Coach input happened to change.
        dailyActivitiesFlow,
    ) { goals, windows, cycles, metrics, _ ->
        val activeGoal = goals.firstOrNull { it.status == GoalStatus.ACTIVE }?.toDomain()
        val readings = metrics.map {
            MetricReading(it.type, it.value, LocalDate.ofEpochDay(it.recordedAtEpochDay), it.note, it.id)
        }
        val breathworkThisWeek = repository.completedBreathworkCount(today - 6, today)
        CoachBundle(
            activeGoal = activeGoal,
            goalProgress = activeGoal?.let {
                GoalEngine.progress(it, readings, today, breathworkThisWeek)
            },
            windows = windows.map { it.toDomain() },
            weekdayEffects = StressPatterns.weekdayEffects(
                cycles.sortedBy { it.dateEpochDay }.map { it.toWhoopSnapshot() },
            ),
        )
    }

    /** Transient signals folded into one flow (combine caps at five inputs). */
    private val goalNotice = MutableStateFlow<String?>(null)

    /**
     * One-shot user messages (snackbars). A Channel, not UiState: a snackbar must fire once and
     * never replay when the state flow re-emits on rotation or a tab switch.
     */
    private val _messages = Channel<UserMessage>(Channel.BUFFERED)
    val messages: Flow<UserMessage> = _messages.receiveAsFlow()

    /** The day the user waved off the reflection offer; keeps "not tonight" honest until tomorrow. */
    private val reflectionDismissedDay = MutableStateFlow<Long?>(null)

    /** Ticks the local hour so the evening offer can appear while the app is already open. */
    private val hourTicker = flow {
        while (true) {
            emit(LocalTime.now().hour)
            delay(60_000)
        }
    }

    /**
     * Whether Today should present the reflection sheet. Kept out of [MainUiState] so the offer
     * rules stay readable and the uiState combine stays within its five inputs.
     */
    val showReflectionSheet: StateFlow<Boolean> = combine(
        dailyActivitiesFlow,
        repository.reflections,
        reflectionDismissedDay,
        hourTicker,
    ) { activities, reflections, dismissedDay, hour ->
        shouldOfferReflection(
            hourOfDay = hour,
            hasActivities = activities.isNotEmpty(),
            alreadyAnsweredToday = reflections.any { it.dateEpochDay == today } || dismissedDay == today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private data class Transients(
        val syncFailed: Boolean,
        val reviewWanted: Boolean,
        val whoopImport: WhoopImportUiState?,
        val goalNotice: String?,
        val refreshing: Boolean,
    )

    private val transientFlags =
        combine(syncFailed, reviewRequested, whoopImportState, goalNotice, refreshing) { s, r, i, g, f ->
            Transients(s, r, i, g, f)
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
        combine(baseFlow, repository.planOverrides, whoopImportInfoFlow, coachFlow) { base, overrides, importInfo, coach ->
            base.copy(
                planOverrides = overrides,
                whoopImportInfo = importInfo,
                coach = coach,
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

    private val consistencyFlow = todayFlow.flatMapLatest { day ->
        repository.dailyActivities(day)
            .map { repository.consistencyDays(7, day) to repository.consistencyFlags(7, day) }
    }

    private val checkInBundle = combine(
        dailyActivitiesFlow,
        repository.connections,
        generatedPlan,
        readinessState,
        consistencyFlow,
    ) { entities, connections, plan, readiness, (consistencyCount, consistencyBits) ->
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
            consistencyDays7 = consistencyCount,
            consistencyFlags = consistencyBits,
        )
    }

    /** The two notification preferences, paired so the uiState combine stays within its five inputs. */
    private data class NotificationPrefs(val denied: Boolean, val offerSettled: Boolean)

    private val notificationPrefs = combine(
        preferencesStore.notificationPermissionDenied,
        preferencesStore.remindersOfferSettled,
    ) { denied, settled -> NotificationPrefs(denied, settled) }

    val uiState: StateFlow<MainUiState> = combine(
        preferencesStore.onboardingComplete,
        notificationPrefs,
        healthBundle,
        checkInBundle,
        transientFlags,
    ) { onboardingComplete, notifPrefs, health, checkIn, transients ->
        val notificationDenied = notifPrefs.denied
        val (syncDidFail, reviewWanted, whoopImport, goalNoticeText, refreshBusy) = transients
        val latest = latestReadings(health.readings)
        val targetMap = health.targets.associateBy(TargetValue::metricType)
        // Assess only values the engine considers valid: one out-of-range persisted row
        // (e.g. from an imported export) must never be able to crash the whole UI state.
        val assessments = latest
            .filter { metricEngine.validate(it.type, it.value) }
            .map { metricEngine.assess(it, targetMap[it.type]) }
        // Goal emphasis bends the weekly plan (extra Zone-2 volume for a weight goal); the
        // recovery-gated daily engine still owns the intensity floor.
        val modifiers = GoalEngine.planModifiers(health.coach.activeGoal, health.coach.windows)
        val plan = applyGoalEmphasis(
            applyPlanOverrides(planEngine.defaultPlan(health.logs), health.planOverrides),
            modifiers,
        )
        // No sessions logged yet means there is no week to review — hand the UI null so it shows
        // the designed empty state instead of a fabricated "Adherence 0%" the user never earned.
        val review = reviewEngine.summarize(health.readings, health.logs)
            .takeIf { health.logs.isNotEmpty() }
        val offTarget = assessments.filter { a ->
            a.clinicalStatus == ClinicalStatus.ABOVE_RANGE || a.clinicalStatus == ClinicalStatus.BELOW_RANGE ||
                a.targetStatus == TargetStatus.ABOVE_PERSONAL_TARGET || a.targetStatus == TargetStatus.BELOW_PERSONAL_TARGET
        }.map { it.reading.type }.toSet()
        val isMockData = checkIn.plan?.isMock ?: whoopClient.isMock
        MainUiState(
            onboardingComplete = onboardingComplete,
            notificationDenied = notificationDenied,
            notificationsAvailable = reminderScheduler.hasNotificationPermission(),
            // Ask for reminders only once the plan has proved its worth on screen — never at
            // launch, and never again once the user has answered either way.
            showRemindersOffer = shouldOfferReminders(
                hasActivities = checkIn.activities.isNotEmpty(),
                offerSettled = notifPrefs.offerSettled,
                permissionGranted = reminderScheduler.hasNotificationPermission(),
            ),
            profile = health.profile,
            userAgeYears = health.profile?.birthYear?.let { java.time.Year.now().value - it }?.takeIf { it in 13..100 },
            offTargetMetrics = offTarget,
            readings = health.readings,
            assessments = assessments,
            insights = insightEngine.generate(assessments),
            weeklyPlan = plan,
            review = review,
            reminders = health.reminders,
            // Exported text must carry the same provenance as the screens that show it.
            exportText = if (isMockData) {
                appContext.getString(R.string.privacy_export_sample_line) + "\n" + health.exportText
            } else {
                health.exportText
            },
            todayActivities = checkIn.activities,
            planHeadline = checkIn.plan?.headline ?: "",
            readinessBand = checkIn.readiness?.band,
            recoveryScore = checkIn.readiness?.recoveryScore,
            planSafetyBanner = checkIn.plan?.safetyBanner,
            whoopIsMock = isMockData,
            whoopConnected = checkIn.whoopConnected,
            calendarConnected = checkIn.calendarConnected,
            calendarIsStub = calendarClient.isStub,
            syncFailed = syncDidFail,
            consistencyDays7 = checkIn.consistencyDays7,
            requestReview = reviewWanted,
            consistencyFlags = checkIn.consistencyFlags,
            isRefreshing = refreshBusy,
            whoopImport = whoopImport,
            whoopImportInfo = health.whoopImportInfo,
            activeGoal = health.coach.activeGoal,
            goalProgress = health.coach.goalProgress,
            goalNotice = goalNoticeText,
            pressureWindows = health.coach.windows,
            weekdayEffects = health.coach.weekdayEffects,
            // Locale-invariant: parsing a stored value into an enum must not vary with the
            // device locale (Turkish dotted/dotless i would break default-locale mapping).
            userSexAtBirth = when (health.profile?.sexAtBirth?.uppercase(java.util.Locale.ROOT)) {
                "FEMALE" -> SexAtBirth.FEMALE
                "MALE" -> SexAtBirth.MALE
                else -> SexAtBirth.UNSPECIFIED
            },
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
        // Watch for local midnight while the app is alive: advance the day pointer so every
        // day-scoped flow re-subscribes, then build the new day's plan. Without this the Today tab
        // serves yesterday until the process is killed.
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                val currentDay = LocalDate.now().toEpochDay()
                if (currentDay != todayFlow.value) {
                    todayFlow.value = currentDay
                    reflectionDismissedDay.value = null
                    loadToday()
                }
            }
        }
    }

    /**
     * Run the shared daily sync ([DailyPlanSync] — same path the background worker uses). A failed
     * sync (offline, expired token) never crashes or wipes the last-known plan — it just sets
     * [syncFailed] so the UI can say so quietly.
     */
    fun loadToday() {
        viewModelScope.launch {
            refreshing.value = true
            preferencesStore.recordFirstOpenIfNeeded(System.currentTimeMillis())
            try {
                refreshPlan(forceReseed = false)
            } finally {
                refreshing.value = false
            }
        }
    }

    private suspend fun refreshPlan(forceReseed: Boolean) {
        val outcome = dailyPlanSync.sync(today, forceReseed = forceReseed)
        // Pressure-window nudges come from windows the user declared locally — they owe nothing to
        // WHOOP, so an offline sync must not silently disarm them.
        schedulePressureNudges()
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
     * Arms today's pre-window breathwork nudges (5 minutes before each declared high-pressure
     * window). Past times and quiet hours are filtered by the scheduler; re-arming replaces the
     * previous one-shot for the same window, so re-syncing never duplicates.
     */
    private suspend fun schedulePressureNudges() {
        val windows = repository.pressureWindows.first().map { it.toDomain() }
        if (windows.isEmpty()) return
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now()
        StressPatterns.windowsForDay(windows, date).forEach { window ->
            val trigger = date.atStartOfDay(zone)
                .plusMinutes(StressPatterns.nudgeMinuteFor(window).toLong())
                .toInstant().toEpochMilli()
            reminderScheduler.scheduleActivityReminder(
                activityId = "pressure-${window.id}",
                title = "5-minute breathing before ${window.label.ifBlank { "your next block" }}",
                body = "Inhale 4 seconds, exhale 6. A calm start beats a rushed one.",
                triggerAtMillis = trigger,
            )
        }
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

    // --- Coach hub: goals, pressure windows, demographics ---

    /**
     * Validates and saves a goal. A too-aggressive ask is never refused — GoalEngine
     * counter-offers the nearest safe version, which is what gets saved, and [MainUiState.goalNotice]
     * explains the adjustment. Any previously active goal is retired (one active goal at a time).
     */
    fun saveGoal(type: GoalType, targetValue: Double, weeks: Int) {
        viewModelScope.launch {
            val readings = uiState.value.readings
            val requested = Goal(
                id = java.util.UUID.randomUUID().toString(),
                type = type,
                targetValue = targetValue,
                baselineValue = GoalEngine.baselineFor(type, readings, today),
                startEpochDay = today,
                targetEpochDay = today + weeks * 7L,
            )
            val (toSave, notice) = when (val v = GoalEngine.validate(requested)) {
                is GoalValidation.Ok -> v.goal to "Goal set. The weekly plan now leans toward it."
                is GoalValidation.Adjusted -> v.adjusted to v.why
                is GoalValidation.Invalid -> {
                    goalNotice.value = v.why
                    return@launch
                }
            }
            repository.goals.first()
                .filter { it.status == GoalStatus.ACTIVE }
                .forEach { repository.updateGoalStatus(it.id, GoalStatus.ABANDONED) }
            repository.upsertGoal(toSave.toEntity())
            goalNotice.value = notice
        }
    }

    fun abandonGoal(id: String) {
        viewModelScope.launch { repository.updateGoalStatus(id, GoalStatus.ABANDONED) }
    }

    fun dismissGoalNotice() {
        goalNotice.value = null
    }

    fun addPressureWindow(daysOfWeekMask: Int, startMinuteOfDay: Int, endMinuteOfDay: Int, label: String) {
        if (daysOfWeekMask == 0 || endMinuteOfDay <= startMinuteOfDay) return
        viewModelScope.launch {
            repository.upsertPressureWindow(
                PressureWindow(
                    id = java.util.UUID.randomUUID().toString(),
                    daysOfWeekMask = daysOfWeekMask,
                    startMinuteOfDay = startMinuteOfDay,
                    endMinuteOfDay = endMinuteOfDay,
                    label = label.trim(),
                ).toEntity(),
            )
            schedulePressureNudges()
        }
    }

    fun removePressureWindow(id: String) {
        viewModelScope.launch {
            repository.deletePressureWindow(id)
            reminderScheduler.cancelActivityReminder("pressure-$id")
        }
    }

    /** Optional demographics for reference-range education; stored on-device only. */
    fun updateDemographics(ageYears: Int?, sexAtBirth: SexAtBirth) {
        viewModelScope.launch {
            val current = repository.profile.first() ?: UserProfileEntity()
            repository.upsertProfile(
                current.copy(
                    birthYear = ageYears?.takeIf { it in 13..100 }
                        ?.let { java.time.Year.now().value - it } ?: current.birthYear,
                    sexAtBirth = when (sexAtBirth) {
                        SexAtBirth.UNSPECIFIED -> null
                        else -> sexAtBirth.name
                    },
                ),
            )
        }
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

    fun completeOnboarding(name: String, heightCm: Double?, ageYears: Int?) {
        viewModelScope.launch {
            // Store age as a birth year so it stays correct as time passes; used only to bias
            // follow-along video picks toward age-appropriate, joint-friendly sessions.
            val birthYear = ageYears?.let { java.time.Year.now().value - it }
            repository.upsertProfile(
                UserProfileEntity(displayName = name.ifBlank { "You" }, heightCm = heightCm, birthYear = birthYear),
            )
            preferencesStore.setOnboardingComplete(true)
        }
    }

    fun setNotificationDenied(denied: Boolean) {
        viewModelScope.launch { preferencesStore.setNotificationPermissionDenied(denied) }
    }

    /**
     * The user answered Today's reminders offer. Settled either way — accepting hands off to the
     * system dialog, declining must never re-ask; Reminder settings remains the way back in.
     */
    fun settleRemindersOffer() {
        viewModelScope.launch { preferencesStore.setRemindersOfferSettled(true) }
    }

    /** Saves today's reflection. Overwrites any earlier answer for the same day. */
    fun saveReflection(mood: ReflectionMood, note: String) {
        viewModelScope.launch { repository.saveReflection(today, mood, note) }
    }

    /** "Not tonight" — no reflection is stored, and the offer stays away until tomorrow. */
    fun dismissReflection() {
        reflectionDismissedDay.value = today
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
                _messages.send(
                    UserMessage(
                        textRes = R.string.snackbar_snoozed_until,
                        formatArg = clockLabel(wakeAtMillis),
                        undo = activity.undoToken(),
                    ),
                )
            }
        }
    }

    fun skipActivity(id: String) {
        val activity = uiState.value.todayActivities.firstOrNull { it.id == id }
        viewModelScope.launch {
            repository.updateActivityStatus(id, ActivityStatus.SKIPPED)
            if (activity != null) {
                _messages.send(
                    UserMessage(
                        textRes = R.string.snackbar_skipped,
                        formatArg = activity.title,
                        undo = activity.undoToken(),
                    ),
                )
            }
        }
    }

    /** The state an activity was in before the current action, for a true Undo restore. */
    private fun DailyActivity.undoToken() = UndoToken(
        activityId = id,
        previousStatus = status,
        previousSnoozedUntilMillis = snoozedUntilMillis,
        previousScheduledEpochMinute = scheduledEpochMinute,
        previousCompletedAtMillis = completedAtMillis,
    )

    /**
     * Puts an activity back exactly as it was and disarms any reminder the undone action armed.
     * Reversibility is a product requirement: snooze/skip/reschedule are one tap from a menu.
     */
    fun undoActivityAction(token: UndoToken) {
        viewModelScope.launch {
            reminderScheduler.cancelActivityReminder(token.activityId)
            repository.updateActivityStatus(
                id = token.activityId,
                status = token.previousStatus,
                completedAtMillis = token.previousCompletedAtMillis,
                snoozedUntilMillis = token.previousSnoozedUntilMillis,
                scheduledEpochMinute = token.previousScheduledEpochMinute,
            )
        }
    }

    private fun clockLabel(epochMillis: Long): String =
        java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault()))

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
                // A very late imported wake time must not push the window start past the day's end;
                // an inverted coerceIn range below would throw and take the process down.
                .coerceAtMost(23 * 60 - 30)
            val endMinuteOfDay = ((snap?.bedMinuteOfDay ?: (22 * 60)) - 60)
                .coerceIn(startMinuteOfDay + 30, 23 * 60)
            val dayStartEpochMinute = date.atStartOfDay(zone).toEpochSecond() / 60
            // "Find a time" means a time still to come: searching from the wake window would
            // happily book 8:00 AM at 7:00 PM, and the scheduler then drops the past trigger.
            val nowEpochMinute = System.currentTimeMillis() / 60_000
            val start = maxOf(dayStartEpochMinute + startMinuteOfDay, nowEpochMinute + 5)
            val end = dayStartEpochMinute + endMinuteOfDay
            val slot = if (end - start < activity.estimatedMinutes) {
                null // No usable window left today.
            } else {
                val busy = calendarClient.freeBusy(start, end)
                availabilityService.suggestSlot(activity.estimatedMinutes, start, end, busy)
            }
            if (slot == null) {
                // A tap must never do nothing: say why no time was booked and leave the plan as-is.
                _messages.send(UserMessage(textRes = R.string.snackbar_no_slot_found))
                return@launch
            }
            val previous = activity.undoToken()
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
            _messages.send(
                UserMessage(
                    textRes = if (calendarClient.isStub) {
                        R.string.snackbar_scheduled_for_sample
                    } else {
                        R.string.snackbar_scheduled_for
                    },
                    formatArg = clockLabel(slot.startEpochMinute * 60_000L),
                    undo = previous,
                ),
            )
            // No calendar write here: the consent copy promises event creation is "a separate,
            // optional step you confirm each time" (PRD CR-3). Until an opt-in + per-event
            // confirmation flow exists, "Find a time" only reads free/busy and schedules the
            // local reminder above.
        }
    }

    // --- Integration consent ---
    fun connectWhoop() {
        viewModelScope.launch {
            repository.setConnection(
                provider = IntegrationProvider.WHOOP,
                status = ConnectionStatus.CONNECTED,
                scopes = "read:recovery read:sleep read:workout read:cycles read:profile offline",
                accountLabel = if (whoopClient.isMock) appContext.getString(R.string.sample_data_a11y) else null,
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
                accountLabel = if (calendarClient.isStub) appContext.getString(R.string.sample_data_a11y) else null,
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
            // The erased numbers must leave the screen too: these caches live in the ViewModel,
            // not the database, so without this the readiness ring keeps drawing the recovery
            // score the user just deleted.
            resetTransientState()
            // A shared export is the user's own copy, but the staging file we wrote into cacheDir
            // is ours — erasure includes it.
            runCatching { java.io.File(appContext.cacheDir, "exports").deleteRecursively() }
            // The home-screen widget must not keep rendering the deleted plan; with the DB empty
            // it falls back to its neutral state. Glance failure must never break erasure.
            runCatching { NextActivityWidget().updateAll(appContext) }
        }
    }

    /** Clears the in-memory plan/readiness caches so erased data cannot survive on screen. */
    private fun resetTransientState() {
        generatedPlan.value = null
        readinessState.value = null
        latestSnapshot.value = null
        syncFailed.value = false
        whoopImportState.value = null
        goalNotice.value = null
        reflectionDismissedDay.value = null
    }

    private fun latestReadings(readings: List<MetricReading>): List<MetricReading> =
        MetricType.entries.mapNotNull { type ->
            readings.filter { it.type == type }.maxByOrNull { it.recordedAt }
        }

    /**
     * Weekly-plan emphasis from the active goal: extra Zone-2 volume is spread across the plan's
     * aerobic slots. Intensity and rest days are untouched — the daily engine owns safety.
     */
    private fun applyGoalEmphasis(plan: WeeklyPlan, modifiers: GoalPlanModifiers): WeeklyPlan {
        if (modifiers.extraZone2MinutesPerWeek <= 0) return plan
        val zone2Slots = plan.workouts.count { it.type == WorkoutType.ZONE_2 }
        if (zone2Slots == 0) return plan
        val extraPer = modifiers.extraZone2MinutesPerWeek / zone2Slots
        return plan.copy(
            workouts = plan.workouts.map {
                if (it.type == WorkoutType.ZONE_2) it.copy(minutes = (it.minutes + extraPer).coerceAtMost(90)) else it
            },
        )
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
