package com.spartan.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spartan.R
import com.spartan.domain.model.ClinicalStatus
import com.spartan.data.local.ReminderFrequency
import com.spartan.domain.model.InsightCard
import com.spartan.domain.model.MetricAssessment
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.PlannedWorkout
import com.spartan.domain.model.TargetStatus
import com.spartan.domain.model.WorkoutType
import kotlin.math.roundToInt

@Composable
fun OnboardingScreen(onComplete: (String, Double?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    Column(
        // Rendered outside the Scaffold, so it handles its own edge-to-edge insets.
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.common_brand),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.onboarding_tagline),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.onboarding_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            name, { name = it },
            label = { Text(stringResource(R.string.onboarding_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            height,
            { height = it },
            label = { Text(stringResource(R.string.onboarding_height_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onComplete(name, height.toDoubleOrNull()) },
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.onboarding_begin), fontWeight = FontWeight.SemiBold)
        }
        Text(
            stringResource(R.string.onboarding_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}

@Composable
fun MetricsScreen(state: MainUiState, onAdd: () -> Unit, onMetricClick: (MetricType) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.metrics_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.metrics_add_metric)) }
            }
        }
        items(state.insights.take(2)) { InsightCardView(it) }
        items(state.assessments) { MetricRow(it, onMetricClick) }
    }
}

@Composable
fun MetricDetailScreen(state: MainUiState, type: MetricType, onAdd: () -> Unit, onEdit: (MetricReading) -> Unit) {
    val assessment = state.assessments.firstOrNull { it.reading.type == type }
    val history = state.readings.filter { it.type == type }
    ScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(type.label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onAdd) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.metrics_add_entry)) }
        }
        assessment?.let {
            StatusChips(it)
            Text(it.clinicalMessage, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
            Text(it.targetMessage, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
        TrendCard(stringResource(R.string.metrics_history), history.mapNotNull { it.value })
        // Plain-language education for WHOOP metrics (renders nothing for lab metrics).
        MetricExplainerSection(type)
        Text(stringResource(R.string.metrics_entries), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        history.forEach {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${it.recordedAt}: ${it.value ?: stringResource(R.string.common_pending)} ${type.unit}", modifier = Modifier.weight(1f))
                    IconButton(onClick = { onEdit(it) }) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.metrics_edit_entry))
                    }
                }
            }
        }
    }
}

@Composable
fun AddMetricScreen(
    initialReading: MetricReading? = null,
    onSave: (MetricType, String, String) -> Boolean,
    onDone: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var type by rememberSaveable(initialReading?.id) { mutableStateOf(initialReading?.type ?: MetricType.FASTING_GLUCOSE) }
    var value by rememberSaveable(initialReading?.id) { mutableStateOf(initialReading?.value?.toString().orEmpty()) }
    var note by rememberSaveable(initialReading?.id) { mutableStateOf(initialReading?.note.orEmpty()) }
    var showError by rememberSaveable { mutableStateOf(false) }
    val pendingAllowed = type in setOf(MetricType.APOB, MetricType.LPA, MetricType.CAC)
    ScreenColumn {
        Text(if (initialReading == null) stringResource(R.string.metrics_add_metric) else stringResource(R.string.metrics_edit_metric), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Box {
            FilledTonalButton(onClick = { expanded = true }) { Text(type.label) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                MetricType.entries.forEach {
                    DropdownMenuItem(text = { Text(it.label) }, onClick = { type = it; expanded = false })
                }
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = {
                value = it
                showError = false
            },
            label = { Text(if (pendingAllowed) stringResource(R.string.metrics_value_pending_label) else stringResource(R.string.metrics_value_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            suffix = { Text(type.unit) },
            isError = showError,
            supportingText = {
                if (showError) {
                    Text(if (pendingAllowed) stringResource(R.string.metrics_value_error_pending) else stringResource(R.string.metrics_value_error, type.label))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.metrics_note_label)) }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                if (onSave(type, value, note)) {
                    onDone()
                } else {
                    showError = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.common_save)) }
    }
}

@Composable
fun PlanScreen(state: MainUiState, onEditMinutes: (String, Int) -> Unit, onComplete: (PlannedWorkout) -> Unit) {
    ScreenColumn {
        Text(stringResource(R.string.plan_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text(state.weeklyPlan?.focus.orEmpty(), style = MaterialTheme.typography.bodyLarge)
        state.weeklyPlan?.workouts.orEmpty().forEach { workout ->
            val key = "${workout.day}-${workout.type.name}"
            val minutes = workout.minutes
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${workout.day} - ${workout.type.name.replace('_', ' ')}", fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.plan_minutes_intensity, minutes, workout.intensity))
                        Text(workout.guidance, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            TextButton(onClick = { onEditMinutes(key, (minutes - 5).coerceAtLeast(5)) }) {
                                Text(stringResource(R.string.plan_minus_five))
                            }
                            TextButton(onClick = { onEditMinutes(key, (minutes + 5).coerceAtMost(180)) }) {
                                Text(stringResource(R.string.plan_plus_five))
                            }
                        }
                    }
                    Button(onClick = { onComplete(workout.copy(minutes = minutes)) }) { Text(stringResource(R.string.plan_log)) }
                }
            }
        }
        Text(state.weeklyPlan?.safetyNote.orEmpty(), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun WorkoutCompletionScreen(
    type: WorkoutType,
    planned: Int,
    onSave: (WorkoutType, Int, Int, Int, Boolean) -> Unit,
    onDone: () -> Unit,
) {
    var completed by rememberSaveable { mutableStateOf(planned.toString()) }
    var rpe by rememberSaveable { mutableStateOf(5f) }
    var pain by rememberSaveable { mutableStateOf(false) }
    ScreenColumn {
        Text(stringResource(R.string.workout_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text(type.name.replace('_', ' '))
        OutlinedTextField(
            completed,
            { completed = it },
            label = { Text(stringResource(R.string.workout_completed_minutes)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.workout_rpe, rpe.roundToInt()))
        Slider(value = rpe, onValueChange = { rpe = it }, valueRange = 1f..10f, steps = 8)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.workout_pain_label), modifier = Modifier.weight(1f))
            Switch(checked = pain, onCheckedChange = { pain = it })
        }
        Button(
            onClick = {
                onSave(type, planned, completed.toIntOrNull() ?: 0, rpe.roundToInt(), pain)
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.workout_save)) }
    }
}

@Composable
fun ReviewScreen(state: MainUiState) {
    val review = state.review
    ScreenColumn {
        Text(stringResource(R.string.review_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryCard(stringResource(R.string.review_adherence), stringResource(R.string.review_percent_value, review?.adherencePercent ?: 0), Modifier.weight(1f))
            SummaryCard(stringResource(R.string.review_strength), "${review?.strengthSessions ?: 0}", Modifier.weight(1f))
        }
        // Calm consistency — descriptive, no streak-loss anxiety.
        SummaryCard(stringResource(R.string.review_consistency), stringResource(R.string.review_consistency_value, state.consistencyDays7), Modifier.fillMaxWidth())
        // Expected-improvement ranges at the current consistency (honest ranges, not promises).
        TrajectoryCard(state.projections)
        // Longitudinal readiness trends from the WHOOP-normalized metric history.
        TrendCard(stringResource(R.string.review_recovery_trend), recentValues(state, MetricType.RECOVERY_SCORE))
        TrendCard(stringResource(R.string.review_hrv_trend), recentValues(state, MetricType.HRV_RMSSD))
        TrendCard(stringResource(R.string.review_sleep_trend), recentValues(state, MetricType.SLEEP_PERFORMANCE))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryCard(stringResource(R.string.review_seven_day_weight), review?.sevenDayWeightAverage?.let { "%.1f".format(it) } ?: stringResource(R.string.review_no_entry), Modifier.weight(1f))
            SummaryCard(stringResource(R.string.review_seven_day_rhr), review?.sevenDayRhrAverage?.let { "%.0f".format(it) } ?: stringResource(R.string.review_no_entry), Modifier.weight(1f))
        }
        Text(stringResource(R.string.review_what_improved), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        review?.improved.orEmpty().forEach { Text("- $it") }
        Text(stringResource(R.string.review_needs_attention), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        review?.needsAttention.orEmpty().forEach { Text("- $it") }
        Text(stringResource(R.string.review_next_week_focus), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(review?.nextWeekFocus.orEmpty())
    }
}

/** Last 14 readings for a metric, oldest first, for the trend sparkline. */
private fun recentValues(state: MainUiState, type: MetricType): List<Double> =
    state.readings.filter { it.type == type && it.value != null }
        .sortedBy { it.recordedAt }
        .takeLast(14)
        .mapNotNull { it.value }

@Composable
fun SettingsScreen(
    onConnections: () -> Unit,
    onReminders: () -> Unit,
    onPrivacy: () -> Unit,
    onDiagnostics: (() -> Unit)? = null,
) {
    ScreenColumn {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        SettingsCard(stringResource(R.string.settings_connections), stringResource(R.string.settings_connections_subtitle), Icons.Outlined.Link, onConnections)
        SettingsCard(stringResource(R.string.settings_reminders), stringResource(R.string.settings_reminders_subtitle), Icons.Outlined.Notifications, onReminders)
        SettingsCard(stringResource(R.string.settings_privacy), stringResource(R.string.settings_privacy_subtitle), Icons.Outlined.PrivacyTip, onPrivacy)
        if (onDiagnostics != null) {
            SettingsCard(stringResource(R.string.settings_diagnostics), stringResource(R.string.settings_diagnostics_subtitle), Icons.Outlined.Edit, onDiagnostics)
        }
        OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.settings_about), fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.settings_version, com.spartan.BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    stringResource(R.string.settings_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
fun ReminderSettingsScreen(
    state: MainUiState,
    onRequestNotifications: () -> Unit,
    onSave: (String, String, String, Int, Int, Boolean, ReminderFrequency, Int) -> Unit,
) {
    ScreenColumn {
        Text(stringResource(R.string.reminders_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        if (state.notificationDenied) Text(stringResource(R.string.reminders_notifications_denied))
        val exerciseReminder = state.reminders.firstOrNull { it.id == "exercise" }
        ReminderEditor(
            id = "exercise",
            title = stringResource(R.string.reminders_exercise_title),
            body = stringResource(R.string.reminders_exercise_body),
            hour = exerciseReminder?.hour ?: 8,
            minute = exerciseReminder?.minute ?: 0,
            enabled = exerciseReminder?.enabled ?: false,
            frequency = exerciseReminder?.frequency ?: ReminderFrequency.DAILY,
            daysOfWeekMask = exerciseReminder?.daysOfWeekMask ?: 127,
            onRequestNotifications = onRequestNotifications,
            onSave = onSave,
        )
        val loggingReminder = state.reminders.firstOrNull { it.id == "logging" }
        ReminderEditor(
            id = "logging",
            title = stringResource(R.string.reminders_logging_title),
            body = stringResource(R.string.reminders_logging_body),
            hour = loggingReminder?.hour ?: 20,
            minute = loggingReminder?.minute ?: 30,
            enabled = loggingReminder?.enabled ?: false,
            frequency = loggingReminder?.frequency ?: ReminderFrequency.DAILY,
            daysOfWeekMask = loggingReminder?.daysOfWeekMask ?: 127,
            onRequestNotifications = onRequestNotifications,
            onSave = onSave,
        )
    }
}

@Composable
fun PrivacyScreen(state: MainUiState, onShare: (String) -> Unit, onDelete: () -> Unit) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    ScreenColumn {
        Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.privacy_body))
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.privacy_export_preview), fontWeight = FontWeight.SemiBold)
                }
                Text(state.exportText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
            }
        }
        Button(onClick = { onShare(state.exportText) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.privacy_share_export))
        }
        Text(stringResource(R.string.privacy_share_note), style = MaterialTheme.typography.bodySmall)
        Button(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Delete, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.privacy_delete_data))
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.privacy_delete_dialog_title)) },
            text = { Text(stringResource(R.string.privacy_delete_dialog_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.privacy_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MetricRow(assessment: MetricAssessment, onClick: (MetricType) -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().clickable { onClick(assessment.reading.type) },
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(assessment.reading.type.label, fontWeight = FontWeight.SemiBold)
                Text("${assessment.reading.value ?: stringResource(R.string.common_pending)} ${assessment.reading.type.unit}", style = MaterialTheme.typography.bodyLarge)
                Text(assessment.clinicalMessage, style = MaterialTheme.typography.bodySmall)
            }
            StatusChips(assessment)
        }
    }
}

@Composable
private fun StatusChips(assessment: MetricAssessment) {
    Column(horizontalAlignment = Alignment.End) {
        StatusBadge(statusLabel(assessment.clinicalStatus))
        Spacer(Modifier.height(6.dp))
        StatusBadge(targetLabel(assessment.targetStatus))
    }
}

@Composable
private fun StatusBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun InsightCardView(card: InsightCard) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(card.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(card.explanation)
            HorizontalDivider()
            Text(card.whyItMatters, style = MaterialTheme.typography.bodyMedium)
            card.safeActions.forEach { Text("- $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun TrendCard(title: String, values: List<Double>) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (values.size < 2) {
                Text(stringResource(R.string.common_trend_empty), style = MaterialTheme.typography.bodySmall)
            } else {
                val latest = values.last()
                val average = values.average()
                val min = values.minOrNull() ?: 0.0
                val max = values.maxOrNull() ?: 0.0
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.common_trend_latest, formatTrendValue(latest)), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.common_trend_avg, formatTrendValue(average)), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.common_trend_range, formatTrendValue(min), formatTrendValue(max)), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                val color = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                    val span = (max - min).takeIf { it > 0.0 } ?: 1.0
                    val path = Path()
                    values.forEachIndexed { index, value ->
                        val x = size.width * index / (values.lastIndex.coerceAtLeast(1)).toFloat()
                        val y = size.height - (((value - min) / span).toFloat() * size.height)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        drawCircle(color, radius = 4.dp.toPx(), center = Offset(x, y))
                    }
                    drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ReminderEditor(
    id: String,
    title: String,
    body: String,
    hour: Int,
    minute: Int,
    enabled: Boolean,
    frequency: ReminderFrequency,
    daysOfWeekMask: Int,
    onRequestNotifications: () -> Unit,
    onSave: (String, String, String, Int, Int, Boolean, ReminderFrequency, Int) -> Unit,
) {
    var hourText by rememberSaveable(id, hour) { mutableStateOf(hour.toString()) }
    var minuteText by rememberSaveable(id, minute) { mutableStateOf(minute.toString()) }
    var selectedFrequency by rememberSaveable(id, frequency) { mutableStateOf(frequency) }
    val parsedHour = hourText.toIntOrNull()
    val parsedMinute = minuteText.toIntOrNull()
    val validTime = parsedHour in 0..23 && parsedMinute in 0..59
    val selectedDaysMask = daysMaskFor(selectedFrequency, daysOfWeekMask)

    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text("%02d:%02d".format(hour, minute), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = enabled, onCheckedChange = { checked ->
                    if (validTime && checked) onRequestNotifications()
                    if (validTime) onSave(id, title, body, parsedHour ?: hour, parsedMinute ?: minute, checked, selectedFrequency, selectedDaysMask)
                })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = hourText,
                    onValueChange = { hourText = it },
                    label = { Text(stringResource(R.string.reminders_hour_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = parsedHour !in 0..23,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = minuteText,
                    onValueChange = { minuteText = it },
                    label = { Text(stringResource(R.string.reminders_minute_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = parsedMinute !in 0..59,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ReminderFrequency.entries.forEach { option ->
                    val label = when (option) {
                        ReminderFrequency.DAILY -> stringResource(R.string.reminders_frequency_daily)
                        ReminderFrequency.WEEKDAYS -> stringResource(R.string.reminders_frequency_weekdays)
                        ReminderFrequency.WEEKENDS -> stringResource(R.string.reminders_frequency_weekends)
                        ReminderFrequency.CUSTOM_DAYS -> stringResource(R.string.reminders_frequency_custom)
                    }
                    TextButton(onClick = { selectedFrequency = option }, enabled = option != ReminderFrequency.CUSTOM_DAYS) {
                        Text(if (selectedFrequency == option) stringResource(R.string.reminders_frequency_selected, label) else label)
                    }
                }
            }
            Button(
                onClick = {
                    if (enabled) onRequestNotifications()
                    onSave(id, title, body, parsedHour ?: hour, parsedMinute ?: minute, enabled, selectedFrequency, selectedDaysMask)
                },
                enabled = validTime,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.reminders_save))
            }
        }
    }
}

private fun formatTrendValue(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

private fun daysMaskFor(frequency: ReminderFrequency, customMask: Int): Int = when (frequency) {
    ReminderFrequency.DAILY -> 127
    ReminderFrequency.WEEKDAYS -> 31
    ReminderFrequency.WEEKENDS -> 96
    ReminderFrequency.CUSTOM_DAYS -> customMask
}

private fun statusLabel(status: ClinicalStatus): String = when (status) {
    ClinicalStatus.BELOW_RANGE -> "Below range"
    ClinicalStatus.NORMAL -> "Clinical normal"
    ClinicalStatus.ABOVE_RANGE -> "Above range"
    ClinicalStatus.PENDING -> "Pending"
    ClinicalStatus.UNKNOWN -> "Reference hidden"
}

private fun targetLabel(status: TargetStatus): String = when (status) {
    TargetStatus.MEETS_TARGET -> "Meets personal target"
    TargetStatus.ABOVE_PERSONAL_TARGET -> "Above personal target"
    TargetStatus.BELOW_PERSONAL_TARGET -> "Below personal target"
    TargetStatus.NO_TARGET -> "No target"
    TargetStatus.PENDING -> "Pending"
}
