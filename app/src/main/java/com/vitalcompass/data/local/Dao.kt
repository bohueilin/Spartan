package com.vitalcompass.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vitalcompass.domain.model.MetricType
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

    @Query("DELETE FROM reminders")
    suspend fun deleteReminders()

    @Query("DELETE FROM weekly_reviews")
    suspend fun deleteReviews()

    @Query("SELECT COUNT(*) FROM metric_entries")
    suspend fun metricCount(): Int
}
