package com.spartan.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.TimeOfDay
import com.spartan.domain.model.WorkoutType

enum class ReminderFrequency {
    DAILY,
    WEEKDAYS,
    WEEKENDS,
    CUSTOM_DAYS
}

/** Integration providers Spartan can connect to (WHOOP data source, Google Calendar). */
enum class IntegrationProvider { WHOOP, GOOGLE_CALENDAR }

/** Connection + consent state for an [IntegrationProvider]. */
enum class ConnectionStatus { NOT_CONNECTED, CONNECTED, CONSENT_REVOKED, ERROR }

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val id: Long = 0,
    val displayName: String = "You",
    val heightCm: Double? = null,
    val birthYear: Int? = null,
    val sexAtBirth: String? = null,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "metric_entries")
data class MetricEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: MetricType,
    val value: Double?,
    val unit: String,
    val note: String = "",
    val recordedAtEpochDay: Long,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "targets")
data class TargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metricType: MetricType,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val note: String = "",
)

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: WorkoutType,
    val plannedMinutes: Int,
    val completedMinutes: Int,
    val rpe: Int,
    val painFlag: Boolean,
    val completedAtEpochDay: Long,
)

@Entity(tableName = "plan_workout_overrides")
data class PlanWorkoutOverrideEntity(
    @PrimaryKey val slotKey: String,
    val minutes: Int,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val frequency: ReminderFrequency = ReminderFrequency.DAILY,
    val daysOfWeekMask: Int = 127,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "weekly_reviews")
data class WeeklyReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStartEpochDay: Long,
    val adherencePercent: Int,
    val zone2Minutes: Int,
    val strengthSessions: Int,
    val nextWeekFocus: String,
)

/**
 * A single recommended activity in a day's Spartan plan, with the user's check-in state.
 * The generated plan seeds these rows; the user's status/snooze/reschedule/completion edits
 * persist here and survive process death and regeneration (regeneration never overwrites state).
 */
@Entity(tableName = "daily_activities", indices = [Index(value = ["dateEpochDay"])])
data class DailyActivityEntity(
    @PrimaryKey val id: String, // "<dateEpochDay>:<slug>"
    val dateEpochDay: Long,
    val title: String,
    val category: ActivityCategory,
    val priority: ActivityPriority,
    val whyItMatters: String,
    val relatedMetric: MetricType?,
    val instructions: String, // newline-joined step list
    val estimatedMinutes: Int,
    val intensity: Intensity,
    val bestTimeOfDay: TimeOfDay,
    val status: ActivityStatus,
    val ruleId: String,
    val scheduledEpochMinute: Long? = null,
    val completedAtMillis: Long? = null,
    val snoozedUntilMillis: Long? = null,
    val safetyNote: String? = null,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

/** Connection + consent record for an integration. Single source of truth for connect/disconnect. */
@Entity(tableName = "integration_connections")
data class IntegrationConnectionEntity(
    @PrimaryKey val provider: IntegrationProvider,
    val status: ConnectionStatus,
    val consentGrantedAtMillis: Long? = null,
    val scopes: String = "",
    val lastSyncMillis: Long? = null,
    val accountLabel: String? = null,
)

/**
 * One imported day of WHOOP data (from the user's CSV data export). This is the user's REAL
 * wearable data — stored locally only, replayed through the same WhoopClient seam as the API
 * path, and deleted on disconnect or full data deletion. Journal flags come from
 * journal_entries.csv and are nullable because not every export includes the journal.
 */
@Entity(tableName = "whoop_cycles")
data class WhoopCycleEntity(
    @PrimaryKey val dateEpochDay: Long,
    val recoveryScore: Int? = null,
    val hrvMs: Double? = null,
    val restingHeartRate: Double? = null,
    val sleepPerformance: Int? = null,
    val sleepDurationHours: Double? = null,
    val sleepDebtHours: Double? = null,
    val respiratoryRate: Double? = null,
    val dayStrain: Double? = null,
    val energyKcal: Double? = null,
    val bedMinuteOfDay: Int? = null,
    val wakeMinuteOfDay: Int? = null,
    val journalCaffeine: Boolean? = null,
    val journalAlcohol: Boolean? = null,
    val journalLateMeal: Boolean? = null,
    val source: String = "CSV_IMPORT",
    val importedAtMillis: Long = 0,
)

/** One recorded workout from the WHOOP export (workouts.csv). Local-only, like all health data. */
@Entity(tableName = "whoop_workouts", indices = [Index(value = ["dateEpochDay"])])
data class WhoopWorkoutEntity(
    @PrimaryKey val id: String, // "<dateEpochDay>:<startMinuteOfDay>:<activityName>"
    val dateEpochDay: Long,
    val startMinuteOfDay: Int? = null,
    val durationMinutes: Int,
    val activityName: String,
    val strain: Double? = null,
    val energyKcal: Double? = null,
    val maxHr: Int? = null,
    val averageHr: Int? = null,
    val hrZone1Pct: Double? = null,
    val hrZone2Pct: Double? = null,
    val hrZone3Pct: Double? = null,
    val hrZone4Pct: Double? = null,
    val hrZone5Pct: Double? = null,
    val importedAtMillis: Long = 0,
)

/**
 * Append-only audit trail of privacy-relevant actions (consent granted/revoked, sync runs, data
 * deletion). Carries actions and timestamps ONLY — never metric values, plan text, or any PHI.
 * Local-only today; the seam future coach/HIPAA deployments audit against.
 */
@Entity(tableName = "audit_events")
data class AuditEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val category: String, // CONSENT | SYNC | DATA | PLAN
    val action: String,   // e.g. WHOOP_CONNECTED, SYNC_COMPLETED, ALL_DATA_DELETED
    val detail: String = "", // non-PHI context, e.g. "days=7"
)
