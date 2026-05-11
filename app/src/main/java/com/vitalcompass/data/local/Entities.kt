package com.vitalcompass.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vitalcompass.domain.model.MetricType
import com.vitalcompass.domain.model.WorkoutType

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

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
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
