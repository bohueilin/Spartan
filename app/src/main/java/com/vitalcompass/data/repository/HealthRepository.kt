package com.vitalcompass.data.repository

import com.vitalcompass.data.local.HealthDao
import com.vitalcompass.data.local.MetricEntryEntity
import com.vitalcompass.data.local.ReminderEntity
import com.vitalcompass.data.local.TargetEntity
import com.vitalcompass.data.local.UserProfileEntity
import com.vitalcompass.data.local.WorkoutSessionEntity
import com.vitalcompass.domain.model.MetricReading
import com.vitalcompass.domain.model.MetricType
import com.vitalcompass.domain.model.TargetValue
import com.vitalcompass.domain.model.WorkoutLog
import com.vitalcompass.domain.model.WorkoutType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthRepository @Inject constructor(
    private val dao: HealthDao,
) {
    val profile: Flow<UserProfileEntity?> = dao.observeProfile()
    val metrics: Flow<List<MetricEntryEntity>> = dao.observeMetrics()
    val targets: Flow<List<TargetEntity>> = dao.observeTargets()
    val workouts: Flow<List<WorkoutSessionEntity>> = dao.observeWorkouts()
    val reminders: Flow<List<ReminderEntity>> = dao.observeReminders()

    fun metricReadings(): Flow<List<MetricReading>> = metrics.map { entries ->
        entries.map {
            MetricReading(
                type = it.type,
                value = it.value,
                recordedAt = LocalDate.ofEpochDay(it.recordedAtEpochDay),
                note = it.note,
            )
        }
    }

    fun targetValues(): Flow<List<TargetValue>> = targets.map { entries ->
        entries.map {
            TargetValue(
                metricType = it.metricType,
                minValue = it.minValue,
                maxValue = it.maxValue,
                note = it.note,
            )
        }
    }

    fun workoutLogs(): Flow<List<WorkoutLog>> = workouts.map { entries ->
        entries.map {
            WorkoutLog(
                type = it.type,
                plannedMinutes = it.plannedMinutes,
                completedMinutes = it.completedMinutes,
                rpe = it.rpe,
                painFlag = it.painFlag,
            )
        }
    }

    suspend fun upsertProfile(profile: UserProfileEntity) = dao.upsertProfile(profile)

    suspend fun addMetric(type: MetricType, value: Double?, note: String = "", date: LocalDate = LocalDate.now()) {
        dao.insertMetric(
            MetricEntryEntity(
                type = type,
                value = value,
                unit = type.unit,
                note = note,
                recordedAtEpochDay = date.toEpochDay(),
            )
        )
    }

    suspend fun addWorkout(type: WorkoutType, planned: Int, completed: Int, rpe: Int, pain: Boolean) {
        dao.insertWorkout(
            WorkoutSessionEntity(
                type = type,
                plannedMinutes = planned,
                completedMinutes = completed,
                rpe = rpe,
                painFlag = pain,
                completedAtEpochDay = LocalDate.now().toEpochDay(),
            )
        )
    }

    suspend fun upsertReminder(reminder: ReminderEntity) = dao.upsertReminder(reminder)

    suspend fun deleteReminder(id: String) = dao.deleteReminder(id)

    suspend fun deleteAllLocalData() {
        dao.deleteProfiles()
        dao.deleteMetrics()
        dao.deleteTargets()
        dao.deleteWorkouts()
        dao.deleteReminders()
        dao.deleteReviews()
    }

    suspend fun exportTextSnapshot(): String {
        return "Vital Compass export is available in-app as a local text summary. Use Android share/save in the next phase for file output."
    }

    suspend fun seedIfEmpty() {
        if (dao.metricCount() > 0) return
        dao.upsertProfile(UserProfileEntity(displayName = "You", heightCm = 177.0))
        val today = LocalDate.now()
        addMetric(MetricType.FASTING_GLUCOSE, 108.0, "Seed demo data", today)
        addMetric(MetricType.TRIGLYCERIDES, 134.0, "Seed demo data", today)
        addMetric(MetricType.HDL_C, 41.0, "Seed demo data", today)
        addMetric(MetricType.TG_HDL_RATIO, 3.26, "Seed demo data", today)
        addMetric(MetricType.VITAMIN_D_25OH, 23.0, "Seed demo data", today)
        addMetric(MetricType.RESTING_HEART_RATE, 68.0, "Seed demo data", today)
        addMetric(MetricType.WEIGHT, 81.16, "Seed demo data", today)
        addMetric(MetricType.BMI, 25.9, "Seed demo data", today)
        addMetric(MetricType.SYSTOLIC_BP, 102.0, "Seed demo data", today)
        addMetric(MetricType.DIASTOLIC_BP, 67.0, "Seed demo data", today)
        addMetric(MetricType.APOB, null, "Pending", today)
        addMetric(MetricType.LPA, null, "Pending", today)
        addMetric(MetricType.CAC, null, "Pending scheduling", today)

        dao.upsertTarget(TargetEntity(metricType = MetricType.VITAMIN_D_25OH, minValue = 30.0, note = "Personal target"))
        dao.upsertTarget(TargetEntity(metricType = MetricType.RESTING_HEART_RATE, maxValue = 60.0, note = "Personal fitness target"))
        dao.upsertTarget(TargetEntity(metricType = MetricType.TG_HDL_RATIO, maxValue = 2.5, note = "Personal optimization target"))
    }
}
