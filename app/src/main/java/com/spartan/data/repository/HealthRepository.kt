package com.spartan.data.repository

import androidx.room.withTransaction
import com.spartan.data.local.AppDatabase
import com.spartan.data.local.AuditEventEntity
import com.spartan.data.local.ConnectionStatus
import com.spartan.data.local.DailyActivityEntity
import com.spartan.data.local.HealthDao
import com.spartan.data.local.IntegrationConnectionEntity
import com.spartan.data.local.IntegrationProvider
import com.spartan.data.local.MetricEntryEntity
import com.spartan.data.local.PlanWorkoutOverrideEntity
import com.spartan.data.local.ReminderEntity
import com.spartan.data.local.TargetEntity
import com.spartan.data.local.UserProfileEntity
import com.spartan.data.local.WorkoutSessionEntity
import com.spartan.data.local.toEntity
import com.spartan.data.whoop.WhoopClient
import com.spartan.data.whoop.WhoopMapper
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.DailyPlan
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.TargetValue
import com.spartan.domain.model.WhoopSnapshot
import com.spartan.domain.model.WorkoutLog
import com.spartan.domain.model.WorkoutType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthRepository @Inject constructor(
    private val dao: HealthDao,
    private val database: AppDatabase,
) {
    val profile: Flow<UserProfileEntity?> = dao.observeProfile()
    val metrics: Flow<List<MetricEntryEntity>> = dao.observeMetrics()
    val targets: Flow<List<TargetEntity>> = dao.observeTargets()
    val workouts: Flow<List<WorkoutSessionEntity>> = dao.observeWorkouts()
    val planOverrides: Flow<List<PlanWorkoutOverrideEntity>> = dao.observePlanOverrides()
    val reminders: Flow<List<ReminderEntity>> = dao.observeReminders()
    val connections: Flow<List<IntegrationConnectionEntity>> = dao.observeConnections()

    fun dailyActivities(dateEpochDay: Long): Flow<List<DailyActivityEntity>> =
        dao.observeActivitiesForDay(dateEpochDay)

    fun metricReadings(): Flow<List<MetricReading>> = metrics.map { entries ->
        entries.map {
            MetricReading(
                type = it.type,
                value = it.value,
                recordedAt = LocalDate.ofEpochDay(it.recordedAtEpochDay),
                note = it.note,
                id = it.id,
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
                completedAt = LocalDate.ofEpochDay(it.completedAtEpochDay),
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

    suspend fun updateMetric(id: Long, type: MetricType, value: Double?, note: String = "") {
        dao.updateMetric(id, type, value, type.unit, note)
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

    suspend fun savePlanOverride(slotKey: String, minutes: Int) {
        dao.upsertPlanOverride(PlanWorkoutOverrideEntity(slotKey = slotKey, minutes = minutes.coerceIn(5, 180)))
    }

    suspend fun upsertReminder(reminder: ReminderEntity) = dao.upsertReminder(reminder)

    suspend fun deleteReminder(id: String) = dao.deleteReminder(id)

    // --- Audit trail (append-only; actions + timestamps only, never PHI) ---
    suspend fun logAudit(category: String, action: String, detail: String = "") {
        dao.insertAuditEvent(
            AuditEventEntity(
                timestampMillis = System.currentTimeMillis(),
                category = category,
                action = action,
                detail = detail,
            ),
        )
    }

    fun auditEvents() = dao.observeAuditEvents()

    /** Distinct days with at least one completed activity in the trailing [days]-day window. */
    suspend fun consistencyDays(days: Int, todayEpochDay: Long): Int =
        dao.daysWithCompletedActivity(todayEpochDay - (days - 1), todayEpochDay)

    // --- WHOOP sync (normalize snapshots into metric_entries; idempotent per day) ---
    /**
     * [extraReadings] lets the CSV import add per-day exercise minutes (tagged 'WHOOP workouts',
     * distinct from the snapshot tag so a later snapshot-only sync can never wipe them). Each
     * source clears only its own tag for its own days, all inside one transaction so a background
     * daily sync can never observe (or interleave with) a half-applied import.
     */
    suspend fun persistWhoopReadings(
        snapshots: List<WhoopSnapshot>,
        extraReadings: List<MetricReading> = emptyList(),
    ) {
        database.withTransaction {
            snapshots.map { it.dateEpochDay }.toSet().forEach { dao.deleteWhoopMetricsForDay(it) }
            extraReadings.map { it.recordedAt.toEpochDay() }.toSet()
                .forEach { dao.deleteWhoopWorkoutMetricsForDay(it) }
            (WhoopMapper.toReadings(snapshots) + extraReadings).forEach { reading ->
                dao.insertMetric(
                    MetricEntryEntity(
                        type = reading.type,
                        value = reading.value,
                        unit = reading.type.unit,
                        note = reading.note,
                        recordedAtEpochDay = reading.recordedAt.toEpochDay(),
                    ),
                )
            }
        }
        logAudit("SYNC", "WHOOP_READINGS_PERSISTED", "days=${snapshots.size}")
    }

    /**
     * Removes every sample WHOOP reading. Called when real data arrives so a leftover sample row
     * on a day the export doesn't cover (e.g. today, synced before the import) can never be shown
     * as if it were the user's real data.
     */
    suspend fun clearSampleWhoopReadings() {
        dao.deleteSampleWhoopMetrics()
    }

    /**
     * Regenerates a day's plan after the data source changed (e.g. real CSV data replacing
     * sample data): not-yet-completed activities are replaced, completed ones stay as history.
     */
    suspend fun reseedDailyPlan(plan: DailyPlan) {
        dao.deleteNonCompletedActivitiesForDay(plan.dateEpochDay)
        plan.activities.forEach { dao.insertActivityIfAbsent(it.toEntity(plan.dateEpochDay)) }
        logAudit("PLAN", "PLAN_REGENERATED", "activities=${plan.activities.size}")
    }

    /**
     * Disconnect = stop using the imported source: the raw cycle/workout tables are removed so
     * syncs fall back to labeled sample data. Already-normalized readings remain the user's
     * history (disconnecting never silently destroys data — deletion lives in Privacy).
     */
    suspend fun clearImportedWhoopSource() {
        dao.deleteWhoopCycles()
        dao.deleteWhoopWorkouts()
        logAudit("DATA", "WHOOP_IMPORT_SOURCE_CLEARED")
    }

    // --- Daily plan / check-in ---
    /** Seeds the day's generated activities once; never overwrites the user's check-in state. */
    suspend fun seedDailyPlanIfNeeded(plan: DailyPlan) {
        if (dao.activityCountForDay(plan.dateEpochDay) > 0) return
        plan.activities.forEach { dao.insertActivityIfAbsent(it.toEntity(plan.dateEpochDay)) }
        logAudit("PLAN", "PLAN_GENERATED", "activities=${plan.activities.size}")
    }

    /** Returns expired snoozes to PLANNED so the plan never silently loses activities. */
    suspend fun reactivateExpiredSnoozes() {
        dao.reactivateExpiredSnoozes(System.currentTimeMillis())
    }

    suspend fun updateActivityStatus(
        id: String,
        status: ActivityStatus,
        completedAtMillis: Long? = null,
        snoozedUntilMillis: Long? = null,
        scheduledEpochMinute: Long? = null,
    ) {
        dao.updateActivityState(
            id = id,
            status = status,
            completedAt = completedAtMillis,
            snoozedUntil = snoozedUntilMillis,
            scheduled = scheduledEpochMinute,
            now = System.currentTimeMillis(),
        )
    }

    // --- Integration connections (consent) ---
    suspend fun setConnection(
        provider: IntegrationProvider,
        status: ConnectionStatus,
        scopes: String = "",
        accountLabel: String? = null,
    ) {
        val existing = connections.first().firstOrNull { it.provider == provider }
        dao.upsertConnection(
            IntegrationConnectionEntity(
                provider = provider,
                status = status,
                consentGrantedAtMillis = if (status == ConnectionStatus.CONNECTED)
                    existing?.consentGrantedAtMillis ?: System.currentTimeMillis() else existing?.consentGrantedAtMillis,
                scopes = scopes.ifBlank { existing?.scopes ?: "" },
                lastSyncMillis = existing?.lastSyncMillis,
                accountLabel = accountLabel ?: existing?.accountLabel,
            ),
        )
        logAudit("CONSENT", "${provider.name}_${status.name}")
    }

    suspend fun markSynced(provider: IntegrationProvider) {
        val existing = connections.first().firstOrNull { it.provider == provider } ?: return
        dao.upsertConnection(existing.copy(lastSyncMillis = System.currentTimeMillis()))
    }

    suspend fun deleteAllLocalData() {
        dao.deleteProfiles()
        dao.deleteMetrics()
        dao.deleteTargets()
        dao.deleteWorkouts()
        dao.deletePlanOverrides()
        dao.deleteReminders()
        dao.deleteReviews()
        dao.deleteActivities()
        dao.deleteConnections()
        dao.deleteWhoopCycles()
        dao.deleteWhoopWorkouts()
        // The user's right to erase includes the audit trail itself; leave a single fresh marker.
        dao.deleteAuditEvents()
        logAudit("DATA", "ALL_DATA_DELETED")
    }

    suspend fun exportTextSnapshot(): String {
        return "Spartan export is available in-app as a local text summary. Use Android share/save in the next phase for file output."
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
