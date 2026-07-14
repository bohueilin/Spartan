package com.spartan.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.MetricType
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthDao {
    @Query("SELECT * FROM user_profiles WHERE id = 0")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM metric_entries ORDER BY recordedAtEpochDay DESC, createdAtMillis DESC")
    fun observeMetrics(): Flow<List<MetricEntryEntity>>

    @Query("SELECT * FROM metric_entries WHERE type = :type ORDER BY recordedAtEpochDay DESC, createdAtMillis DESC")
    fun observeMetric(type: MetricType): Flow<List<MetricEntryEntity>>

    @Insert
    suspend fun insertMetric(entry: MetricEntryEntity)

    @Query("UPDATE metric_entries SET type = :type, value = :value, unit = :unit, note = :note WHERE id = :id")
    suspend fun updateMetric(id: Long, type: MetricType, value: Double?, unit: String, note: String)

    @Query("DELETE FROM metric_entries WHERE id = :id")
    suspend fun deleteMetric(id: Long)

    @Query("SELECT * FROM targets")
    fun observeTargets(): Flow<List<TargetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTarget(target: TargetEntity)

    @Query("SELECT * FROM workout_sessions ORDER BY completedAtEpochDay DESC")
    fun observeWorkouts(): Flow<List<WorkoutSessionEntity>>

    @Insert
    suspend fun insertWorkout(session: WorkoutSessionEntity)

    @Query("SELECT * FROM plan_workout_overrides")
    fun observePlanOverrides(): Flow<List<PlanWorkoutOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlanOverride(override: PlanWorkoutOverrideEntity)

    @Query("SELECT * FROM reminders")
    fun observeReminders(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminder(id: String)

    @Query("DELETE FROM user_profiles")
    suspend fun deleteProfiles()

    @Query("DELETE FROM metric_entries")
    suspend fun deleteMetrics()

    @Query("DELETE FROM targets")
    suspend fun deleteTargets()

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteWorkouts()

    @Query("DELETE FROM plan_workout_overrides")
    suspend fun deletePlanOverrides()

    @Query("DELETE FROM reminders")
    suspend fun deleteReminders()

    @Query("DELETE FROM weekly_reviews")
    suspend fun deleteReviews()

    @Query("SELECT COUNT(*) FROM metric_entries")
    suspend fun metricCount(): Int

    /**
     * Clears WHOOP-sourced readings for a day so a re-sync is idempotent. Matches the exact
     * notes WhoopMapper/the CSV importer write — never a user's own note that begins "WHOOP".
     */
    @Query("DELETE FROM metric_entries WHERE recordedAtEpochDay = :day AND note IN ('WHOOP', 'WHOOP (sample)')")
    suspend fun deleteWhoopMetricsForDay(day: Long)

    // --- Daily activities (the Spartan check-in) ---
    @Query("SELECT * FROM daily_activities WHERE dateEpochDay = :day ORDER BY priority ASC, estimatedMinutes ASC")
    fun observeActivitiesForDay(day: Long): Flow<List<DailyActivityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivity(activity: DailyActivityEntity)

    /** Seeds a generated activity only if it does not already exist, preserving user state. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertActivityIfAbsent(activity: DailyActivityEntity): Long

    @Query(
        "UPDATE daily_activities SET status = :status, completedAtMillis = :completedAt, " +
            "snoozedUntilMillis = :snoozedUntil, scheduledEpochMinute = :scheduled, updatedAtMillis = :now WHERE id = :id",
    )
    suspend fun updateActivityState(
        id: String,
        status: ActivityStatus,
        completedAt: Long?,
        snoozedUntil: Long?,
        scheduled: Long?,
        now: Long,
    )

    @Query("SELECT COUNT(*) FROM daily_activities WHERE dateEpochDay = :day")
    suspend fun activityCountForDay(day: Long): Int

    /** Wakes snoozed activities whose snooze window has passed back to PLANNED. */
    @Query(
        "UPDATE daily_activities SET status = 'PLANNED', snoozedUntilMillis = NULL, updatedAtMillis = :now " +
            "WHERE status = 'SNOOZED' AND snoozedUntilMillis IS NOT NULL AND snoozedUntilMillis <= :now",
    )
    suspend fun reactivateExpiredSnoozes(now: Long)

    @Query("DELETE FROM daily_activities")
    suspend fun deleteActivities()

    // --- Integration connections (consent) ---
    @Query("SELECT * FROM integration_connections")
    fun observeConnections(): Flow<List<IntegrationConnectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConnection(connection: IntegrationConnectionEntity)

    @Query("DELETE FROM integration_connections")
    suspend fun deleteConnections()

    // --- Audit trail (append-only, non-PHI) ---
    @Insert
    suspend fun insertAuditEvent(event: AuditEventEntity)

    @Query("SELECT * FROM audit_events ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeAuditEvents(limit: Int = 100): Flow<List<AuditEventEntity>>

    @Query("DELETE FROM audit_events")
    suspend fun deleteAuditEvents()

    /** Days in [startDay, endDay] with at least one completed activity — consistency signal. */
    @Query(
        "SELECT COUNT(DISTINCT dateEpochDay) FROM daily_activities " +
            "WHERE status = 'DONE' AND dateEpochDay BETWEEN :startDay AND :endDay",
    )
    suspend fun daysWithCompletedActivity(startDay: Long, endDay: Long): Int

    // --- Imported WHOOP data cleanup (disconnect / delete-all) ---
    @Query("DELETE FROM whoop_cycles")
    suspend fun deleteWhoopCycles()

    @Query("DELETE FROM whoop_workouts")
    suspend fun deleteWhoopWorkouts()

    /** Clears a day's imported exercise-minutes rows (tagged 'WHOOP workouts') for re-import. */
    @Query("DELETE FROM metric_entries WHERE recordedAtEpochDay = :day AND note = 'WHOOP workouts'")
    suspend fun deleteWhoopWorkoutMetricsForDay(day: Long)

    /**
     * Removes every sample reading across all days. Called when real data arrives (CSV import)
     * so stale sample rows for days the export doesn't cover can never pose as real data.
     */
    @Query("DELETE FROM metric_entries WHERE note = 'WHOOP (sample)'")
    suspend fun deleteSampleWhoopMetrics()

    /**
     * Clears a day's not-yet-completed plan so it can be regenerated after the data source
     * changes (e.g. a CSV import replacing sample data). Completed items are history — kept.
     */
    @Query("DELETE FROM daily_activities WHERE dateEpochDay = :day AND status != 'DONE'")
    suspend fun deleteNonCompletedActivitiesForDay(day: Long)
}

/**
 * Imported WHOOP data (CSV export). Separate from [HealthDao] so the import pipeline and the
 * [com.spartan.data.whoop.LocalFirstWhoopClient] depend on a seam small enough to fake in tests.
 */
@Dao
interface WhoopCycleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCycles(cycles: List<WhoopCycleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkouts(workouts: List<WhoopWorkoutEntity>)

    /** The most recent [limit] imported days, newest first. */
    @Query("SELECT * FROM whoop_cycles ORDER BY dateEpochDay DESC LIMIT :limit")
    suspend fun latestCycles(limit: Int): List<WhoopCycleEntity>

    @Query("SELECT COUNT(*) FROM whoop_cycles")
    suspend fun cycleCount(): Int

    @Query("SELECT * FROM whoop_workouts ORDER BY dateEpochDay DESC, startMinuteOfDay DESC")
    fun observeWorkouts(): Flow<List<WhoopWorkoutEntity>>
}
