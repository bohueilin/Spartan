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
import com.spartan.data.local.GoalEntity
import com.spartan.data.local.IntegrationProvider
import com.spartan.data.local.MetricEntryEntity
import com.spartan.data.local.PlanWorkoutOverrideEntity
import com.spartan.data.local.PreferencesStore
import com.spartan.data.local.PressureWindowEntity
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
import com.spartan.domain.engine.ActionPlaybook
import com.spartan.domain.engine.AvoidOption
import com.spartan.domain.engine.BedtimePlan
import com.spartan.domain.engine.Signal
import com.spartan.domain.engine.SignalEngine
import com.spartan.domain.engine.SleepNeed
import com.spartan.domain.engine.SuggestedAction
import com.spartan.domain.engine.Goal
import com.spartan.domain.engine.GoalEngine
import com.spartan.domain.engine.GoalPlanModifiers
import com.spartan.domain.engine.GoalProgress
import com.spartan.domain.engine.GoalStatus
import com.spartan.domain.engine.GoalType
import com.spartan.domain.engine.GoalValidation
import com.spartan.domain.engine.GoalBaselines
import com.spartan.domain.engine.GoalPlanning
import com.spartan.domain.engine.GoalPreset
import com.spartan.domain.engine.MorningVerdict
import com.spartan.domain.engine.PlanConstraints
import com.spartan.domain.engine.StrainZone
import com.spartan.domain.engine.VerdictEngine
import com.spartan.domain.engine.WeeklyDecision
import com.spartan.domain.engine.WhyToday
import com.spartan.domain.engine.WhyTodayEngine
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
import com.spartan.domain.model.DailyReflection
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
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.flow.update
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
    /** Sessions already logged today per workout type, so a repeat log is flagged first. */
    val workoutsLoggedToday: Map<WorkoutType, Int> = emptyMap(),
    val planHeadline: String = "",
    val readinessBand: ReadinessBand? = null,
    val recoveryScore: Int? = null,
    /** Plain-language reasons the engine derived from the user's own baselines (may be empty). */
    val readinessNotes: List<String> = emptyList(),
    /** Each daily wearable signal read against the user's own normal range (see [SignalEngine]). */
    val signals: List<Signal> = emptyList(),
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
    /**
     * Oldest→today: the same flags over the trailing [STREAK_WINDOW_DAYS] (two weeks), for the
     * streak summary — so a run or a gap that began last week gives this week's figures context.
     * Its last seven entries are [consistencyFlags].
     */
    val streakFlags: List<Boolean> = emptyList(),
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
    // Today: one verdict, one target, why today
    /** Today's morning verdict, or Pending — never fabricated. Null only until the first sync settles. */
    val verdict: MorningVerdict? = null,
    /** The expandable "Why today?" explanation; null until the first sync settles. */
    val whyToday: WhyToday? = null,
    /** Today's strain target zone from the plan; null when the plan sets none (pending/holding plan). */
    val strainTarget: StrainZone? = null,
    /**
     * Last night's hours asleep and the estimated need behind them, for "7h 40m of about 8h 10m".
     * Only when the newest snapshot is today's night ([DailyPlanSync.lastNight]); null otherwise,
     * so an older night is never labelled "Last night".
     */
    val sleptHours: Double? = null,
    val sleepNeedHours: Double? = null,
    /** Wake day of the newest cycle the plan is built on. */
    val dataThroughEpochDay: Long? = null,
    /**
     * When WHOOP data was last imported — only the CSV import stamps this, so the UI labels it
     * "Imported". Null when WHOOP is not connected or the data on screen is sample ([importedAtMillis]).
     */
    val lastSyncedAtMillis: Long? = null,
    /** The cycle day the morning reveal last played for: it plays once per verdict, never on every visit. */
    val morningRevealPlayedForDay: Long? = null,
    // Goal wizard
    /** Bed / wake minute of day from the newest WHOOP sleep, to pre-fill the wizard's sleep window. */
    val observedBedMinute: Int? = null,
    val observedWakeMinute: Int? = null,
    /** What the user told the wizard about their time, limits, sleep window, and won't-dos. */
    val planConstraints: PlanConstraints = PlanConstraints(),
    /** The user's own recent averages the goal presets anchor to. */
    val goalBaselines: GoalBaselines = GoalBaselines(),
    /** A goal paused at a weekly check-in: it no longer steers the plan until resumed. */
    val pausedGoal: Goal? = null,
    /** True when the active goal has run a week since it started or since the last check-in. */
    val weeklyReviewDue: Boolean = false,
    // Display (You → Display)
    /** The quieter colour scheme; it also stills rememberReducedMotion motion. Off until the user opts in. */
    val calmerTheme: Boolean = false,
    /** Colours from the wallpaper on Android 12+. Off until the user opts in. */
    val wallpaperColours: Boolean = false,
    /**
     * The newest saved reflection's mood, to pre-fill the evening sheet — only while that answer is
     * recent ([REFLECTION_PREFILL_DAYS]); null before the first one or after a longer gap.
     */
    val lastReflectionMood: ReflectionMood? = null,
)

/** Days of check-off history behind [MainUiState.streakFlags]: two weeks of runs; the strip shows the last 7. */
internal const val STREAK_WINDOW_DAYS = 14

/** Days of check-off history behind [MainUiState.consistencyFlags] and the weekly count. */
internal const val CONSISTENCY_WINDOW_DAYS = 7

/** How many days old the newest reflection may be and still pre-fill tonight's (yesterday's or so). */
internal const val REFLECTION_PREFILL_DAYS = 2

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

/**
 * The newest saved reflection's mood — one tap to answer "same as last time". Null when there is
 * none, or when it is more than [REFLECTION_PREFILL_DAYS] before [todayEpochDay]: an answer from
 * weeks ago is not "last time", and pre-selecting it would make saving it a single tap.
 */
internal fun lastReflectionMood(reflections: List<DailyReflection>, todayEpochDay: Long): ReflectionMood? =
    reflections.maxByOrNull { it.dateEpochDay }
        ?.takeIf { todayEpochDay - it.dateEpochDay <= REFLECTION_PREFILL_DAYS }
        ?.mood

/** The shortest a weekly slot is ever trimmed to, however little time a day the user gave. */
internal const val MIN_WEEKLY_SLOT_MINUTES = 10

// Zone 2 copy for a swapped strength slot when the week has no Zone 2 slot to borrow it from —
// the same words the weekly template uses (PlanEngine).
private const val ZONE2_INTENSITY = "Easy conversational pace"
private const val ZONE2_GUIDANCE = "Keep it repeatable."

/**
 * The weekly template works around a won't-do the way the daily plan does (finding SCO-17): with
 * strength training on the user's won't-do list, its slots become Zone 2 sessions. A swapped slot
 * takes the week's own Zone 2 minutes and copy, so a deload week stays a deload week.
 */
internal fun swapAvoidedSessions(plan: WeeklyPlan, constraints: PlanConstraints): WeeklyPlan {
    if (AvoidOption.STRENGTH_TRAINING !in constraints.avoid) return plan
    val zone2 = plan.workouts.firstOrNull { it.type == WorkoutType.ZONE_2 }
    return plan.copy(
        workouts = plan.workouts.map { slot ->
            if (slot.type != WorkoutType.STRENGTH) {
                slot
            } else {
                slot.copy(
                    type = WorkoutType.ZONE_2,
                    minutes = zone2?.minutes ?: slot.minutes,
                    intensity = zone2?.intensity ?: ZONE2_INTENSITY,
                    guidance = zone2?.guidance ?: ZONE2_GUIDANCE,
                )
            }
        },
    )
}

/**
 * Caps every weekly slot at the minutes a day the user said they have — never below
 * [MIN_WEEKLY_SLOT_MINUTES], so a slot stays worth doing. No limit given (null) changes nothing.
 */
internal fun capSlotMinutes(plan: WeeklyPlan, minutesPerDay: Int?): WeeklyPlan {
    val cap = minutesPerDay?.coerceAtLeast(MIN_WEEKLY_SLOT_MINUTES) ?: return plan
    return plan.copy(workouts = plan.workouts.map { if (it.minutes > cap) it.copy(minutes = cap) else it })
}

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
    firstDay: Boolean,
): Boolean = hourOfDay >= REFLECTION_HOUR && hasActivities && !alreadyAnsweredToday && !firstDay

/** The workout type a Today training check-off is logged as. */
internal fun workoutTypeFor(category: ActivityCategory): WorkoutType = when (category) {
    ActivityCategory.STRENGTH -> WorkoutType.STRENGTH
    ActivityCategory.MOBILITY, ActivityCategory.RECOVERY -> WorkoutType.MOBILITY
    else -> WorkoutType.ZONE_2 // ZONE2 / MOVEMENT map to easy aerobic work
}

/** Today check-offs in these categories open the exercise debrief, which logs a session. */
internal val DEBRIEF_CATEGORIES = setOf(
    ActivityCategory.ZONE2, ActivityCategory.STRENGTH, ActivityCategory.MOBILITY, ActivityCategory.MOVEMENT,
)

/**
 * Sessions logged on [day] per type. Today and Coach can both log the same real session, so a
 * repeat is flagged before saving — never silently merged or dropped.
 */
internal fun workoutsLoggedOn(logs: List<WorkoutLog>, day: LocalDate): Map<WorkoutType, Int> =
    logs.filter { it.completedAt == day }.groupingBy { it.type }.eachCount()

/**
 * Whether debriefing [activity] would log its type again. Logs explained by today's other
 * checked-off activities don't count: two planned mobility sessions are not a double log, but one
 * also logged from Coach (or an activity checked off twice) is.
 */
internal fun debriefRepeatsLog(
    activity: DailyActivity,
    todayActivities: List<DailyActivity>,
    loggedToday: Map<WorkoutType, Int>,
): Boolean {
    val type = workoutTypeFor(activity.category)
    val otherCheckOffs = todayActivities.count {
        it.id != activity.id && it.status == ActivityStatus.DONE &&
            it.category in DEBRIEF_CATEGORIES && workoutTypeFor(it.category) == type
    }
    return (loggedToday[type] ?: 0) > otherCheckOffs
}

/**
 * Today's verdict, or null while this session's first sync is still in flight — Today shows its
 * loading state, never a guess. Once a sync has settled, success or failure, there is always an
 * answer: with no readiness at all it is Pending(NO_DATA), or Pending(SYNC_FAILED) when the sync
 * failed — a connected user offline is never told they have no data. [readinessToday] must already
 * be carried forward to today, so a snapshot left over from yesterday reads as stale, not as today's.
 */
internal fun todayVerdict(
    settled: Boolean,
    readinessToday: ReadinessSnapshot?,
    strainTarget: StrainZone?,
    syncFailed: Boolean = false,
): MorningVerdict? =
    if (settled) VerdictEngine.verdict(readinessToday, strainTarget, syncFailed = syncFailed) else null

/**
 * When WHOOP data was last imported, for Today's freshness line. Only the CSV import stamps this
 * time, so it is shown only while WHOOP is connected and the data on screen is real — never beside
 * sample data, and never after a disconnect, where it would describe data no longer in use.
 */
internal fun importedAtMillis(lastSyncMillis: Long?, whoopConnected: Boolean, isSample: Boolean): Long? =
    lastSyncMillis?.takeIf { whoopConnected && !isSample }

/**
 * Whether answering the weekly check-in with [decision] completes this week's review. Keep and
 * pause do. Change does not: the review completes when the wizard saves the new goal (a fresh goal
 * is its own check-in), so backing out of the wizard leaves the check-in due.
 */
internal fun completesWeeklyReview(decision: WeeklyDecision): Boolean = decision != WeeklyDecision.CHANGE

/** Logged workout types that count toward a fitness goal's sessions. */
internal val TRAINING_WORKOUTS: Set<WorkoutType> = setOf(WorkoutType.ZONE_2, WorkoutType.STRENGTH)

/** Logged workout types that count toward a recovery goal's sessions. */
internal val RECOVERY_WORKOUTS: Set<WorkoutType> = setOf(WorkoutType.MOBILITY)

/**
 * Sessions of [types] logged in the trailing week — today and the six days before, the same window
 * the calm-session count uses — for a habit goal's "2 of 3 this week".
 */
internal fun sessionsThisWeek(logs: List<WorkoutLog>, todayEpochDay: Long, types: Set<WorkoutType>): Int =
    logs.count { it.type in types && it.completedAt.toEpochDay() in (todayEpochDay - 6)..todayEpochDay }

/** Today check-offs in these categories count toward a fitness goal's sessions. */
internal val TRAINING_CATEGORIES: Set<ActivityCategory> = setOf(ActivityCategory.ZONE2, ActivityCategory.STRENGTH)

/**
 * Today check-offs in these categories count toward a recovery goal's sessions: mobility, the
 * gentle recovery cards and the sleep wind-down. The "In bed by" anchor is not a session; the
 * count itself leaves it out ([HealthRepository.completedActivityCount]).
 */
internal val RECOVERY_CATEGORIES: Set<ActivityCategory> =
    setOf(ActivityCategory.MOBILITY, ActivityCategory.RECOVERY, ActivityCategory.SLEEP)

/**
 * A habit goal's sessions this week from the two places a session is recorded: Today check-offs
 * ([checkOffs]) and logged workouts ([logged]). A debriefed check-off is in both, so the two are
 * never added — the larger wins. That credits check-offs left un-debriefed (and wind-downs, which
 * have no debrief) and sessions logged outside Today without counting one session twice; it can
 * only undercount a week that mixes the two on different sessions.
 */
internal fun habitSessionsThisWeek(checkOffs: Int, logged: Int): Int = maxOf(checkOffs, logged)

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
    /** Undo means "take it back out": the action added this activity rather than changing it. */
    val removeOnUndo: Boolean = false,
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
    val reflections: List<DailyReflection> = emptyList(),
)

/** Coach-hub state derived from persisted goals/windows + imported cycles. */
data class CoachBundle(
    val activeGoal: Goal? = null,
    val goalProgress: GoalProgress? = null,
    val windows: List<PressureWindow> = emptyList(),
    val weekdayEffects: List<WeekdayEffect> = emptyList(),
    val pausedGoal: Goal? = null,
    val goalBaselines: GoalBaselines = GoalBaselines(),
    val weeklyReviewDue: Boolean = false,
    val planConstraints: PlanConstraints = PlanConstraints(),
    /** Every saved goal and window as stored, for the export (which carries all of them). */
    val goalRows: List<GoalEntity> = emptyList(),
    val windowRows: List<PressureWindowEntity> = emptyList(),
    /** The day of the last weekly goal check-in, for the export; null when there has been none. */
    val lastGoalReviewDay: Long? = null,
)

private data class CheckInBundle(
    val activities: List<DailyActivity>,
    val plan: DailyPlan?,
    val readiness: ReadinessSnapshot?,
    val latestSnapshot: WhoopSnapshot?,
    /** True once this session's first sync has finished (either way); see [todayVerdict]. */
    val syncSettled: Boolean,
    val whoopConnected: Boolean,
    /** When WHOOP data last arrived; null when it never has. */
    val whoopLastSyncMillis: Long?,
    val calendarConnected: Boolean,
    val consistencyDays7: Int,
    val consistencyFlags: List<Boolean>,
    val streakFlags: List<Boolean>,
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

    /**
     * What the last sync delivered, held as one value so Today never renders a half-applied sync (a
     * new readiness beside the old plan). [settled] turns true once the session's first sync has
     * finished, whether it succeeded or failed; until then there is no verdict to show.
     */
    private data class SyncedDay(
        val plan: DailyPlan? = null,
        val readiness: ReadinessSnapshot? = null,
        val latestSnapshot: WhoopSnapshot? = null,
        val bedtime: BedtimePlan? = null,
        val settled: Boolean = false,
    )

    private val synced = MutableStateFlow(SyncedDay())

    /**
     * Tonight's lights-out target from the same inputs the daily sync plans with — last night's
     * wake time, estimated sleep need and debt, and the bed / wake times the user gave — so this
     * card and the plan's sleep anchor agree. The sync's own target wins while it is today's: it is
     * the one today's anchor was built from. An older night never sizes the target (or its debt
     * line); with stale data only the user's own times do. Null until there is enough for a target
     * (never 8 hours).
     */
    val bedtimePlan: StateFlow<BedtimePlan?> = combine(
        synced,
        preferencesStore.planConstraints,
        todayFlow,
    ) { day, constraints, planningDay ->
        day.bedtime?.takeIf { day.plan?.dateEpochDay == planningDay }
            ?: DailyPlanSync.tonightFor(DailyPlanSync.lastNight(day.latestSnapshot, planningDay), constraints)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The latest app-open / pull-to-refresh sync, and the latest CSV import, for erasure to stop. */
    private var refreshJob: Job? = null
    private var importJob: Job? = null

    private val syncFailed = MutableStateFlow(false)
    private val reviewRequested = MutableStateFlow(false)
    private val refreshing = MutableStateFlow(false)
    private val whoopImportState = MutableStateFlow<WhoopImportUiState?>(null)

    /** Persistent imported-data summary for the Metrics banner; null when nothing is imported. */
    private val whoopImportInfoFlow = whoopCycleDao.observeImportInfo().map { row ->
        if (row.dayCount == 0 || row.firstDay == null || row.lastDay == null) null
        else WhoopImportInfo(days = row.dayCount, firstDayEpoch = row.firstDay, lastDayEpoch = row.lastDay)
    }

    /** The goal-side inputs, folded so the coach combine stays within its five. */
    private data class GoalInputs(
        val goals: List<GoalEntity>,
        val logs: List<WorkoutLog>,
        val lastReviewDay: Long?,
        val constraints: PlanConstraints,
    )

    private val goalInputs = combine(
        repository.goals,
        repository.workoutLogs(),
        preferencesStore.lastGoalReviewDay,
        preferencesStore.planConstraints,
    ) { goals, logs, lastReviewDay, constraints -> GoalInputs(goals, logs, lastReviewDay, constraints) }

    /** Goals + pressure windows + stress patterns for the Coach hub, all reactive. */
    private val coachFlow = combine(
        goalInputs,
        repository.pressureWindows,
        whoopCycleDao.observeRecentCycles(),
        repository.metrics,
        // Completing a calm session must move a stress goal's progress immediately: a one-shot
        // read here would only refresh when some other Coach input happened to change.
        dailyActivitiesFlow,
    ) { inputs, windows, cycles, metrics, _ ->
        val day = today
        val goals = inputs.goals.map { it.toDomain() }
        val activeGoal = goals.firstOrNull { it.status == GoalStatus.ACTIVE }
        val readings = metrics.map {
            MetricReading(it.type, it.value, LocalDate.ofEpochDay(it.recordedAtEpochDay), it.note, it.id)
        }
        val breathworkThisWeek = repository.completedBreathworkCount(day - 6, day)
        CoachBundle(
            activeGoal = activeGoal,
            goalProgress = activeGoal?.let {
                GoalEngine.progress(
                    goal = it,
                    readings = readings,
                    todayEpochDay = day,
                    breathworkThisWeek = breathworkThisWeek,
                    // Check-offs and logged workouts, never both for one session (see habitSessionsThisWeek).
                    trainingSessionsThisWeek = habitSessionsThisWeek(
                        checkOffs = repository.completedActivityCount(TRAINING_CATEGORIES, day - 6, day),
                        logged = sessionsThisWeek(inputs.logs, day, TRAINING_WORKOUTS),
                    ),
                    recoverySessionsThisWeek = habitSessionsThisWeek(
                        checkOffs = repository.completedActivityCount(RECOVERY_CATEGORIES, day - 6, day),
                        logged = sessionsThisWeek(inputs.logs, day, RECOVERY_WORKOUTS),
                    ),
                )
            },
            windows = windows.map { it.toDomain() },
            weekdayEffects = StressPatterns.weekdayEffects(
                cycles.sortedBy { it.dateEpochDay }.map { it.toWhoopSnapshot() },
            ),
            // Goals arrive newest first, so the first paused one is the most recent.
            pausedGoal = goals.firstOrNull { it.status == GoalStatus.PAUSED },
            goalBaselines = GoalPlanning.baselines(
                readings = readings,
                logs = inputs.logs,
                calmSessionsLast28Days = repository.completedBreathworkCount(day - 27, day),
                todayEpochDay = day,
            ),
            weeklyReviewDue = GoalPlanning.isWeeklyReviewDue(activeGoal, inputs.lastReviewDay, day),
            planConstraints = inputs.constraints,
            goalRows = inputs.goals,
            windowRows = windows,
            lastGoalReviewDay = inputs.lastReviewDay,
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
        preferencesStore.reflectionDismissedDay,
        hourTicker,
        preferencesStore.firstOpenMillis,
    ) { activities, reflections, dismissedDay, hour, firstOpen ->
        shouldOfferReflection(
            hourOfDay = hour,
            hasActivities = activities.isNotEmpty(),
            alreadyAnsweredToday = reflections.any { it.dateEpochDay == today } || dismissedDay == today,
            // Asking "how did today feel?" on the day of onboarding asks about a day spent without us.
            firstDay = firstOpen == null || epochDayOf(firstOpen) == today,
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
        combine(
            baseFlow,
            repository.planOverrides,
            whoopImportInfoFlow,
            coachFlow,
            // All of them, not the recent page: the export carries every reflection, and the
            // weekly review picks its own window out of them.
            repository.allReflections,
        ) { base, overrides, importInfo, coach, reflections ->
            base.copy(
                planOverrides = overrides,
                whoopImportInfo = importInfo,
                coach = coach,
                // A mood this version doesn't know is left out of the review, never a crash; the
                // export below still carries the row as stored.
                reflections = reflections.mapNotNull { row ->
                    ReflectionMood.entries.firstOrNull { it.name == row.mood }
                        ?.let { DailyReflection(row.dateEpochDay, it, row.note) }
                },
                // Export is a privacy control: everything the user saved leaves with it, goals,
                // pressure windows, the wizard's plan settings, reflections and the last weekly
                // check-in included.
                exportText = LocalExportFormatter.format(
                    profile = base.profile,
                    metrics = base.rawMetrics,
                    targets = base.rawTargets,
                    workouts = base.rawWorkouts,
                    planOverrides = overrides,
                    reminders = base.reminders,
                    goals = coach.goalRows,
                    pressureWindows = coach.windowRows,
                    planConstraints = coach.planConstraints,
                    reflections = reflections,
                    lastGoalReviewDay = coach.lastGoalReviewDay,
                ),
            )
        }
    }

    /** The week's active-day count and the two weeks of flags behind the strip and the streaks. */
    private val consistencyFlow = todayFlow.flatMapLatest { day ->
        repository.dailyActivities(day).map {
            repository.consistencyDays(CONSISTENCY_WINDOW_DAYS, day) to
                repository.consistencyFlags(STREAK_WINDOW_DAYS, day)
        }
    }

    private val checkInBundle = combine(
        dailyActivitiesFlow,
        repository.connections,
        synced,
        consistencyFlow,
    ) { entities, connections, syncedDay, (consistencyCount, streakBits) ->
        CheckInBundle(
            activities = entities.map { it.toDomain() },
            plan = syncedDay.plan,
            readiness = syncedDay.readiness,
            latestSnapshot = syncedDay.latestSnapshot,
            syncSettled = syncedDay.settled,
            whoopConnected = connections.any {
                it.provider == IntegrationProvider.WHOOP && it.status == ConnectionStatus.CONNECTED
            },
            whoopLastSyncMillis = connections.firstOrNull { it.provider == IntegrationProvider.WHOOP }?.lastSyncMillis,
            calendarConnected = connections.any {
                it.provider == IntegrationProvider.GOOGLE_CALENDAR && it.status == ConnectionStatus.CONNECTED
            },
            consistencyDays7 = consistencyCount,
            // The strip's week is the last seven of the same two weeks: one read, so they never disagree.
            consistencyFlags = streakBits.takeLast(CONSISTENCY_WINDOW_DAYS),
            streakFlags = streakBits,
        )
    }

    /** The small preferences Today reads, bundled so the uiState combine stays within its five inputs. */
    private data class PrefsBundle(
        val onboardingComplete: Boolean,
        val notificationDenied: Boolean,
        val offerSettled: Boolean,
        val morningRevealPlayedDay: Long?,
        /** Calmer theme to wallpaper colours, folded so this combine stays within its five. */
        val display: Pair<Boolean, Boolean>,
    )

    private val prefsBundle = combine(
        preferencesStore.onboardingComplete,
        preferencesStore.notificationPermissionDenied,
        preferencesStore.remindersOfferSettled,
        preferencesStore.morningRevealPlayedDay,
        combine(preferencesStore.calmerTheme, preferencesStore.wallpaperColours) { calmer, wallpaper ->
            calmer to wallpaper
        },
    ) { onboarding, denied, offerSettled, revealDay, display ->
        PrefsBundle(onboarding, denied, offerSettled, revealDay, display)
    }

    val uiState: StateFlow<MainUiState> = combine(
        prefsBundle,
        healthBundle,
        checkInBundle,
        transientFlags,
    ) { prefs, health, checkIn, transients ->
        val onboardingComplete = prefs.onboardingComplete
        val notificationDenied = prefs.notificationDenied
        val (syncDidFail, reviewWanted, whoopImport, goalNoticeText, refreshBusy) = transients
        val latest = latestReadings(health.readings)
        val targetMap = health.targets.associateBy(TargetValue::metricType)
        // Assess only values the engine considers valid: one out-of-range persisted row
        // (e.g. from an imported export) must never be able to crash the whole UI state.
        val assessments = latest
            .filter { metricEngine.validate(it.type, it.value) }
            .map { metricEngine.assess(it, targetMap[it.type]) }
        // The weekly template, in order: won't-dos swapped out, goal emphasis (extra Zone-2 volume
        // for a weight goal), the minutes-a-day cap, then the user's own per-slot edits — last, so a
        // minute changed on the Training screen shows exactly as entered. The recovery-gated daily
        // engine still owns the intensity floor.
        val modifiers = GoalEngine.planModifiers(health.coach.activeGoal, health.coach.windows)
        val constraints = health.coach.planConstraints
        val plan = applyPlanOverrides(
            capSlotMinutes(
                applyGoalEmphasis(swapAvoidedSessions(planEngine.defaultPlan(health.logs), constraints), modifiers),
                constraints.minutesPerDay,
            ),
            health.planOverrides,
        )
        // No sessions logged yet means there is no week to review — hand the UI null so it shows
        // the designed empty state instead of a fabricated "Adherence 0%" the user never earned.
        val review = reviewEngine.summarize(health.readings, health.logs, reflections = health.reflections)
            .takeIf { health.logs.isNotEmpty() }
        val offTarget = assessments.filter { a ->
            a.clinicalStatus == ClinicalStatus.ABOVE_RANGE || a.clinicalStatus == ClinicalStatus.BELOW_RANGE ||
                a.targetStatus == TargetStatus.ABOVE_PERSONAL_TARGET || a.targetStatus == TargetStatus.BELOW_PERSONAL_TARGET
        }.map { it.reading.type }.toSet()
        val isMockData = checkIn.plan?.isMock ?: whoopClient.isMock
        // Today's verdict and its "why": built only from a sync that has settled, against today.
        // After a failed sync past midnight the cached snapshot is yesterday's — carried forward it
        // reads as stale (Pending), and yesterday's plan lends it no strain target.
        val day = today
        val readinessToday = checkIn.readiness?.let { DailyPlanSync.carryForward(it, day) }
        // The sync always plans under the day it ran for, so a plan from any other day is not today's.
        val planToday = checkIn.plan?.takeIf { it.dateEpochDay == day }
        val signals = SignalEngine.signals(health.readings)
        // Only today's night is "last night": an older snapshot (lagging sync, old import) is not.
        val lastNight = DailyPlanSync.lastNight(checkIn.latestSnapshot, day)
        val sleptHours = lastNight?.sleepDurationHours
        // WHOOP's own need when the data has it, else the estimate — the same need tonight's target uses.
        val sleepNeedHours = SleepNeed.forSnapshot(lastNight)
        val verdict = todayVerdict(checkIn.syncSettled, readinessToday, planToday?.strainTarget, syncFailed = syncDidFail)
        MainUiState(
            onboardingComplete = onboardingComplete,
            notificationDenied = notificationDenied,
            notificationsAvailable = reminderScheduler.hasNotificationPermission(),
            // Ask for reminders only once the plan has proved its worth on screen — never at
            // launch, and never again once the user has answered either way.
            showRemindersOffer = shouldOfferReminders(
                hasActivities = checkIn.activities.isNotEmpty(),
                offerSettled = prefs.offerSettled,
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
            workoutsLoggedToday = workoutsLoggedOn(health.logs, LocalDate.ofEpochDay(today)),
            planHeadline = checkIn.plan?.headline ?: "",
            readinessBand = checkIn.readiness?.band,
            recoveryScore = checkIn.readiness?.recoveryScore,
            readinessNotes = checkIn.readiness?.trendNotes.orEmpty(),
            signals = signals,
            planSafetyBanner = checkIn.plan?.safetyBanner,
            whoopIsMock = isMockData,
            whoopConnected = checkIn.whoopConnected,
            calendarConnected = checkIn.calendarConnected,
            calendarIsStub = calendarClient.isStub,
            syncFailed = syncDidFail,
            consistencyDays7 = checkIn.consistencyDays7,
            requestReview = reviewWanted,
            consistencyFlags = checkIn.consistencyFlags,
            streakFlags = checkIn.streakFlags,
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
            verdict = verdict,
            whyToday = verdict?.let {
                WhyTodayEngine.explain(readinessToday, it, planToday, signals, sleptHours, sleepNeedHours)
            },
            strainTarget = planToday?.strainTarget,
            sleptHours = sleptHours,
            sleepNeedHours = sleepNeedHours,
            dataThroughEpochDay = checkIn.readiness?.cycleEpochDay,
            lastSyncedAtMillis = importedAtMillis(checkIn.whoopLastSyncMillis, checkIn.whoopConnected, isMockData),
            morningRevealPlayedForDay = prefs.morningRevealPlayedDay,
            observedBedMinute = checkIn.latestSnapshot?.bedMinuteOfDay,
            observedWakeMinute = checkIn.latestSnapshot?.wakeMinuteOfDay,
            planConstraints = health.coach.planConstraints,
            // Goals anchor to the user's own average; sample data is nobody's average, so the
            // wizard falls back to its labelled "still learning" defaults instead.
            goalBaselines = if (isMockData) GoalBaselines() else health.coach.goalBaselines,
            pausedGoal = health.coach.pausedGoal,
            weeklyReviewDue = health.coach.weeklyReviewDue,
            calmerTheme = prefs.display.first,
            wallpaperColours = prefs.display.second,
            lastReflectionMood = lastReflectionMood(health.reflections, day),
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
        refreshJob = viewModelScope.launch {
            // All of it inside the try: erasure cancels this job, and a cancel that lands in the
            // first-open write must still take the pull-to-refresh spinner down.
            try {
                refreshing.value = true
                preferencesStore.recordFirstOpenIfNeeded(System.currentTimeMillis())
                refreshPlan(forceReseed = false)
            } finally {
                refreshing.value = false
            }
        }
    }

    /**
     * [erasureGeneration] is the store's erasure count when this refresh's work began (an import
     * passes the one it started under). If all data is erased meanwhile, nothing is written or shown.
     */
    private suspend fun refreshPlan(forceReseed: Boolean, erasureGeneration: Long = repository.erasureGeneration) {
        val outcome = dailyPlanSync.sync(today, forceReseed = forceReseed, erasureGeneration = erasureGeneration)
        // Erased while this ran: what it fetched is the data the user just deleted, and erasure has
        // already reset what Today shows — publishing now would draw it back on screen.
        if (erasedSince(erasureGeneration, outcome)) return
        // Pressure-window nudges come from windows the user declared locally — they owe nothing to
        // WHOOP, so an offline sync must not silently disarm them.
        schedulePressureNudges(erasureGeneration)
        // Again after that suspension; nothing below suspends before the state is published.
        if (erasedSince(erasureGeneration, outcome)) return
        if (outcome.failed) {
            syncFailed.value = true
            // Settled all the same, keeping the last-known day: a failed first sync with nothing
            // cached reads as "couldn't reach WHOOP" (Pending SYNC_FAILED) — never as "no data
            // yet", never as a verdict and never as endless loading.
            synced.update { it.copy(settled = true) }
            return
        }
        syncFailed.value = false
        // Today's cycle replaced a holding plan: reminders armed for its snoozed or rescheduled
        // items go with them, as after an import, or they would fire for items no longer planned.
        outcome.replacedReminderIds.forEach(reminderScheduler::cancelActivityReminder)
        synced.value = SyncedDay(
            plan = outcome.plan,
            readiness = outcome.readiness,
            latestSnapshot = outcome.latestSnapshot,
            bedtime = outcome.bedtime,
            settled = true,
        )
    }

    private fun erasedSince(generation: Long, outcome: DailyPlanSync.Outcome): Boolean =
        outcome.superseded || repository.erasureGeneration != generation

    /**
     * Arms today's pre-window breathwork nudges (5 minutes before each declared high-pressure
     * window). Past times and quiet hours are filtered by the scheduler; re-arming replaces the
     * previous one-shot for the same window, so re-syncing never duplicates.
     *
     * [erasureGeneration] is the store's erasure count when the caller's work began. Once an
     * erasure has begun since, nothing is armed: a nudge carries the window's label, which the user
     * just deleted, and WorkManager would keep it past the erasure.
     */
    private suspend fun schedulePressureNudges(erasureGeneration: Long) {
        val windows = repository.pressureWindows.first().map { it.toDomain() }
        if (windows.isEmpty()) return
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now()
        for (window in StressPatterns.windowsForDay(windows, date)) {
            // Right before each enqueue, after the suspension above. Erasure marks itself on this
            // same main thread, so it cannot begin between this check and the enqueue.
            if (repository.erasureGeneration != erasureGeneration) return
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
        importJob = viewModelScope.launch {
            val generation = repository.erasureGeneration
            whoopImportState.value = WhoopImportUiState(inProgress = true)
            // Reading the files can take seconds on a slow or cloud-backed provider, so it holds no
            // lock and an erasure never waits on it. Only the write runs under the store lock, so it
            // never interleaves with an erasure; one that lands first means it never writes at all.
            val result = whoopCsvImporter.read(uris).fold(
                onSuccess = { staged ->
                    repository.writeUnlessErasedSince(generation) { whoopCsvImporter.persist(staged) }
                },
                onFailure = { Result.failure<WhoopCsvImporter.Summary>(it) },
            )
            if (result == null) {
                whoopImportState.value = null
                return@launch
            }
            result
                .onSuccess { summary ->
                    cancelPendingActivityReminders()
                    refreshPlan(forceReseed = true, erasureGeneration = generation)
                    // Erased meanwhile: the summary would describe data that no longer exists.
                    whoopImportState.value =
                        if (repository.erasureGeneration == generation) WhoopImportUiState(summary = summary) else null
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
     * explains the adjustment. Any active or paused goal is retired (one primary goal at a time).
     */
    fun saveGoal(type: GoalType, targetValue: Double, weeks: Int) {
        viewModelScope.launch {
            persistGoal(type, targetValue, weeks) { adjustedWhy ->
                adjustedWhy ?: "Goal set. The weekly plan now leans toward it."
            }
        }
    }

    /**
     * The goal wizard's finish: the user's constraints are saved first, then the goal through the
     * same validated path as [saveGoal]. The baseline is the goal metric's own recent average
     * ([GoalEngine.baselineFor]), not the wizard's display anchor. Today's plan is left as it is —
     * reseeding would throw away check-offs — so the notice says the plan leans in from tomorrow.
     */
    fun startGoalPlan(preset: GoalPreset, targetValue: Double, weeks: Int, constraints: PlanConstraints) {
        viewModelScope.launch {
            preferencesStore.setPlanConstraints(constraints)
            val saved = persistGoal(preset.goalType, targetValue, weeks) { adjustedWhy ->
                adjustedWhy?.let { appContext.getString(R.string.vm_goal_plan_started_adjusted, it) }
                    ?: appContext.getString(R.string.vm_goal_plan_started)
            }
            // A fresh goal is its own check-in: the next weekly review is a week from today.
            if (saved) preferencesStore.setLastGoalReviewDay(today)
        }
    }

    /** Plan settings edited outside the wizard (time a day, limits, sleep window, won't-dos). */
    fun savePlanConstraints(constraints: PlanConstraints) {
        viewModelScope.launch { preferencesStore.setPlanConstraints(constraints) }
    }

    /**
     * The weekly keep / change / pause check-in. Keep and pause confirm with a kind snackbar and
     * complete this week's review. Change only hands over to the wizard the UI opens: the review
     * completes when [startGoalPlan] saves the new goal, so backing out of the wizard leaves the
     * check-in due ([completesWeeklyReview]). A missed week is a goal-design problem, never the user's.
     */
    fun recordWeeklyDecision(decision: WeeklyDecision) {
        viewModelScope.launch {
            when (decision) {
                WeeklyDecision.KEEP -> _messages.send(UserMessage(textRes = R.string.vm_weekly_kept))
                WeeklyDecision.PAUSE -> {
                    val active = repository.goals.first().firstOrNull { it.status == GoalStatus.ACTIVE }
                    if (active != null) {
                        repository.updateGoalStatus(active.id, GoalStatus.PAUSED)
                        _messages.send(UserMessage(textRes = R.string.vm_weekly_paused))
                    }
                }
                WeeklyDecision.CHANGE -> Unit
            }
            if (completesWeeklyReview(decision)) preferencesStore.setLastGoalReviewDay(today)
        }
    }

    /** Brings a paused goal back as the one primary goal; any other active goal is retired. */
    fun resumeGoal(id: String) {
        viewModelScope.launch {
            val goals = repository.goals.first()
            if (goals.none { it.id == id }) return@launch
            goals.filter { it.status == GoalStatus.ACTIVE && it.id != id }
                .forEach { repository.updateGoalStatus(it.id, GoalStatus.ABANDONED) }
            repository.updateGoalStatus(id, GoalStatus.ACTIVE)
            preferencesStore.setLastGoalReviewDay(today)
            _messages.send(UserMessage(textRes = R.string.vm_goal_resumed))
        }
    }

    /** The morning reveal has played for [cycleEpochDay]'s verdict; it never replays for that day. */
    fun markMorningRevealPlayed(cycleEpochDay: Long) {
        viewModelScope.launch { preferencesStore.setMorningRevealPlayedDay(cycleEpochDay) }
    }

    /**
     * Validates and saves a goal as the one primary goal; false when it was refused outright. A
     * too-aggressive ask is saved as GoalEngine's nearest safe counter-offer, and any ACTIVE or
     * PAUSED goal is retired. [notice] turns the counter-offer's explanation (null when the ask
     * was saved as-is) into the confirmation copy.
     */
    private suspend fun persistGoal(
        type: GoalType,
        targetValue: Double,
        weeks: Int,
        notice: (adjustedWhy: String?) -> String,
    ): Boolean {
        val day = today
        val requested = Goal(
            id = java.util.UUID.randomUUID().toString(),
            type = type,
            targetValue = targetValue,
            baselineValue = GoalEngine.baselineFor(type, repository.metricReadings().first(), day),
            startEpochDay = day,
            targetEpochDay = day + weeks * 7L,
        )
        val (toSave, adjustedWhy) = when (val v = GoalEngine.validate(requested)) {
            is GoalValidation.Ok -> v.goal to null
            is GoalValidation.Adjusted -> v.adjusted to v.why
            is GoalValidation.Invalid -> {
                goalNotice.value = v.why
                return false
            }
        }
        repository.goals.first()
            .filter { it.status == GoalStatus.ACTIVE || it.status == GoalStatus.PAUSED }
            .forEach { repository.updateGoalStatus(it.id, GoalStatus.ABANDONED) }
        repository.upsertGoal(toSave.toEntity())
        goalNotice.value = notice(adjustedWhy)
        return true
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
            val generation = repository.erasureGeneration
            repository.upsertPressureWindow(
                PressureWindow(
                    id = java.util.UUID.randomUUID().toString(),
                    daysOfWeekMask = daysOfWeekMask,
                    startMinuteOfDay = startMinuteOfDay,
                    endMinuteOfDay = endMinuteOfDay,
                    label = label.trim(),
                ).toEntity(),
            )
            schedulePressureNudges(generation)
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
            preferencesStore.setFirstOpenMillis(System.currentTimeMillis())
            preferencesStore.setOnboardingComplete(true)
        }
    }

    /** You → Display: the calmer colour scheme (it also stills rememberReducedMotion motion). */
    fun setCalmerTheme(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setCalmerTheme(enabled) }
    }

    /** You → Display: colours from the wallpaper on Android 12+. */
    fun setWallpaperColours(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setWallpaperColours(enabled) }
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
        viewModelScope.launch { preferencesStore.setReflectionDismissedDay(today) }
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
        viewModelScope.launch {
            repository.addWorkout(
                type = workoutTypeFor(activity.category),
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

    /**
     * Puts a suggested action into today's plan — the step that turns an insight into something
     * done. Confirms with a snackbar whose Undo takes it back out.
     */
    fun addAction(action: SuggestedAction, metric: MetricType) {
        val activity = ActionPlaybook.toActivity(action, metric, today)
        viewModelScope.launch {
            repository.addActivity(activity, today)
            _messages.send(
                UserMessage(
                    textRes = R.string.snackbar_added_to_plan,
                    formatArg = action.title,
                    undo = UndoToken(activity.id, ActivityStatus.PLANNED, removeOnUndo = true),
                ),
            )
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
            if (token.removeOnUndo) {
                repository.removeActivity(token.activityId)
                return@launch
            }
            repository.updateActivityStatus(
                id = token.activityId,
                status = token.previousStatus,
                completedAtMillis = token.previousCompletedAtMillis,
                snoozedUntilMillis = token.previousSnoozedUntilMillis,
                scheduledEpochMinute = token.previousScheduledEpochMinute,
            )
        }
    }

    private fun epochDayOf(epochMillis: Long): Long =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

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
            val snap = synced.value.latestSnapshot
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
            val generation = repository.erasureGeneration
            whoopAuthManager.disconnect() // clears stored tokens, matching the privacy policy
            // Disconnect stops the source: imported cycles/workouts are removed so syncs fall
            // back to labeled sample data. Normalized readings stay as the user's history —
            // disconnect never silently destroys data; deletion lives in Privacy.
            val cleared = repository.writeUnlessErasedSince(generation) {
                repository.clearImportedWhoopSource()
                repository.setConnection(IntegrationProvider.WHOOP, ConnectionStatus.NOT_CONNECTED)
            }
            // Erased meanwhile: erasure already removed the source and the connection, and wrote
            // its own audit marker — writing a fresh connection row now would undo part of it.
            if (cleared == null) return@launch
            cancelPendingActivityReminders()
            refreshPlan(forceReseed = true, erasureGeneration = generation)
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
        // Marked first, synchronously, before the slow teardown below: from here every sync,
        // import, disconnect or worker that began earlier is refused — its writes by the store,
        // its reminders by the check right before each one — not only once the wipe takes the lock.
        repository.beginErasure()
        // A sync or import already running must not refill the store once it is emptied: stop
        // them now. The store lock and erasure generation catch whatever slips past — the
        // background worker's sync, a refresh started meanwhile (HealthRepository.writeUnlessErasedSince).
        refreshJob?.cancel()
        importJob?.cancel()
        viewModelScope.launch {
            // Erasure-grade: disarm every scheduled job (or the plan-refresh worker would
            // repopulate the emptied DB), purge WorkManager's own DB, dismiss shown notifications.
            reminderScheduler.purgeAllForErasure()
            // Full deletion includes any OAuth tokens, per the privacy policy.
            whoopAuthManager.disconnect()
            calendarAuthManager.disconnect()
            repository.deleteAllLocalData()
            // Once more now the store is empty: a writer already past its last check when the
            // erasure began (the worker runs off the main thread) may have armed a reminder above.
            reminderScheduler.purgeRemindersAfterErasure()
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
        // Known-empty rather than loading: erasure has disarmed the background sync, so Today says
        // "no data yet" (Pending) instead of the deleted verdict or a wait no sync will end.
        synced.value = SyncedDay(settled = true)
        syncFailed.value = false
        // The cancelled refresh may have been stopped before it could take its spinner down.
        refreshing.value = false
        whoopImportState.value = null
        goalNotice.value = null
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
