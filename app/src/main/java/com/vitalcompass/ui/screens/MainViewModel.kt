package com.vitalcompass.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitalcompass.data.local.PreferencesStore
import com.vitalcompass.data.local.ReminderEntity
import com.vitalcompass.data.local.UserProfileEntity
import com.vitalcompass.data.reminder.ReminderScheduler
import com.vitalcompass.data.repository.HealthRepository
import com.vitalcompass.domain.engine.InsightEngine
import com.vitalcompass.domain.engine.MetricEngine
import com.vitalcompass.domain.engine.PlanEngine
import com.vitalcompass.domain.engine.ReviewEngine
import com.vitalcompass.domain.model.InsightCard
import com.vitalcompass.domain.model.MetricAssessment
import com.vitalcompass.domain.model.MetricReading
import com.vitalcompass.domain.model.MetricType
import com.vitalcompass.domain.model.TargetValue
import com.vitalcompass.domain.model.WeeklyPlan
import com.vitalcompass.domain.model.WeeklyReviewSummary
import com.vitalcompass.domain.model.WorkoutLog
import com.vitalcompass.domain.model.WorkoutType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val onboardingComplete: Boolean = false,
    val notificationDenied: Boolean = false,
    val profile: UserProfileEntity? = null,
    val readings: List<MetricReading> = emptyList(),
    val assessments: List<MetricAssessment> = emptyList(),
    val insights: List<InsightCard> = emptyList(),
    val weeklyPlan: WeeklyPlan? = null,
    val review: WeeklyReviewSummary? = null,
    val reminders: List<ReminderEntity> = emptyList(),
    val exportText: String = "",
)

private data class HealthBundle(
    val profile: UserProfileEntity?,
    val readings: List<MetricReading>,
    val targets: List<TargetValue>,
    val logs: List<WorkoutLog>,
    val reminders: List<ReminderEntity>,
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
) : ViewModel() {
    private val healthBundle = combine(
        repository.profile,
        repository.metricReadings(),
        repository.targetValues(),
        repository.workoutLogs(),
        repository.reminders,
    ) { profile, readings, targets, logs, reminders ->
        HealthBundle(profile, readings, targets, logs, reminders)
    }

    val uiState: StateFlow<MainUiState> = combine(
        preferencesStore.onboardingComplete,
        preferencesStore.notificationPermissionDenied,
        healthBundle,
    ) { onboardingComplete, notificationDenied, health ->
        val latest = latestReadings(health.readings)
        val targetMap = health.targets.associateBy(TargetValue::metricType)
        val assessments = latest.map { metricEngine.assess(it, targetMap[it.type]) }
        val plan = planEngine.defaultPlan(health.logs)
        val review = reviewEngine.summarize(health.readings, health.logs)
        MainUiState(
            onboardingComplete = onboardingComplete,
            notificationDenied = notificationDenied,
            profile = health.profile,
            readings = health.readings,
            assessments = assessments,
            insights = insightEngine.generate(assessments),
            weeklyPlan = plan,
            review = review,
            reminders = health.reminders,
            exportText = buildExport(health.profile, health.readings, health.reminders),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun seed() {
        viewModelScope.launch { repository.seedIfEmpty() }
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

    fun addMetric(type: MetricType, rawValue: String, note: String) {
        viewModelScope.launch {
            val value = rawValue.toDoubleOrNull()
            if (metricEngine.validate(type, value)) repository.addMetric(type, value, note)
        }
    }

    fun completeWorkout(type: WorkoutType, planned: Int, completed: Int, rpe: Int, pain: Boolean) {
        viewModelScope.launch { repository.addWorkout(type, planned, completed, rpe, pain) }
    }

    fun saveReminder(id: String, title: String, body: String, hour: Int, minute: Int, enabled: Boolean) {
        viewModelScope.launch {
            val reminder = ReminderEntity(id, title, body, hour, minute, enabled)
            repository.upsertReminder(reminder)
            reminderScheduler.schedule(reminder)
        }
    }

    fun deleteAllLocalData() {
        viewModelScope.launch {
            reminderScheduler.cancelAll()
            repository.deleteAllLocalData()
            preferencesStore.clear()
        }
    }

    private fun latestReadings(readings: List<MetricReading>): List<MetricReading> =
        MetricType.entries.mapNotNull { type ->
            readings.filter { it.type == type }.maxByOrNull { it.recordedAt }
        }

    private fun buildExport(profile: UserProfileEntity?, readings: List<MetricReading>, reminders: List<ReminderEntity>): String {
        val metrics = readings.joinToString("\n") { "${it.recordedAt},${it.type.name},${it.value ?: "pending"},${it.note}" }
        val reminderLines = reminders.joinToString("\n") { "${it.id},${it.title},${it.hour}:${it.minute},enabled=${it.enabled}" }
        return buildString {
            appendLine("Vital Compass Local Export")
            appendLine("Profile: ${profile?.displayName ?: "Not set"}, heightCm=${profile?.heightCm ?: "not set"}")
            appendLine("Metrics:")
            appendLine(metrics)
            appendLine("Reminders:")
            appendLine(reminderLines)
        }
    }
}
