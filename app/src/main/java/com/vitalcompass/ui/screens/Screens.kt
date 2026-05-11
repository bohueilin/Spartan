package com.vitalcompass.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vitalcompass.domain.model.ClinicalStatus
import com.vitalcompass.domain.model.InsightCard
import com.vitalcompass.domain.model.MetricAssessment
import com.vitalcompass.domain.model.MetricReading
import com.vitalcompass.domain.model.MetricType
import com.vitalcompass.domain.model.PlannedWorkout
import com.vitalcompass.domain.model.TargetStatus
import com.vitalcompass.domain.model.WorkoutType
import kotlin.math.roundToInt

@Composable
fun OnboardingScreen(onComplete: (String, Double?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Vital Compass", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Local-first health tracking with separate clinical ranges and personal targets.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            height,
            { height = it },
            label = { Text("Height in cm") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onComplete(name, height.toDoubleOrNull()) },
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) {
            Text("Start")
        }
    }
}

@Composable
fun TodayScreen(state: MainUiState, onMetricClick: (MetricType) -> Unit) {
    ScreenColumn {
        Text("Today", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text("A calm snapshot of your current signals.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryCard("Adherence", "${state.review?.adherencePercent ?: 0}%", Modifier.weight(1f))
            SummaryCard("Zone 2", "${state.review?.zone2Minutes ?: 0} min", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        TrendCard("Weight trend", state.readings.filter { it.type == MetricType.WEIGHT }.mapNotNull { it.value })
        Spacer(Modifier.height(12.dp))
        state.insights.take(3).forEach { InsightCardView(it) }
        Spacer(Modifier.height(12.dp))
        Text("Key metrics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        state.assessments.take(6).forEach { MetricRow(it, onMetricClick) }
    }
}

@Composable
fun MetricsScreen(state: MainUiState, onAdd: () -> Unit, onMetricClick: (MetricType) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Metrics", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, contentDescription = "Add metric") }
            }
        }
        items(state.assessments) { MetricRow(it, onMetricClick) }
    }
}

@Composable
fun MetricDetailScreen(state: MainUiState, type: MetricType, onAdd: () -> Unit) {
    val assessment = state.assessments.firstOrNull { it.reading.type == type }
    val history = state.readings.filter { it.type == type }
    ScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(type.label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onAdd) { Icon(Icons.Outlined.Edit, contentDescription = "Add entry") }
        }
        assessment?.let {
            StatusChips(it)
            Text(it.clinicalMessage, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
            Text(it.targetMessage, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
        TrendCard("History", history.mapNotNull { it.value })
        Text("Entries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        history.forEach {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Text("${it.recordedAt}: ${it.value ?: "pending"} ${type.unit}", modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
fun AddMetricScreen(onSave: (MetricType, String, String) -> Unit, onDone: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var type by rememberSaveable { mutableStateOf(MetricType.FASTING_GLUCOSE) }
    var value by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    ScreenColumn {
        Text("Add metric", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
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
            onValueChange = { value = it },
            label = { Text("Value; leave blank if pending") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            suffix = { Text(type.unit) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onSave(type, value, note); onDone() }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
    }
}

@Composable
fun PlanScreen(state: MainUiState, onComplete: (PlannedWorkout) -> Unit) {
    ScreenColumn {
        Text("Weekly plan", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text(state.weeklyPlan?.focus.orEmpty(), style = MaterialTheme.typography.bodyLarge)
        state.weeklyPlan?.workouts.orEmpty().forEach { workout ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${workout.day} - ${workout.type.name.replace('_', ' ')}", fontWeight = FontWeight.SemiBold)
                        Text("${workout.minutes} min - ${workout.intensity}")
                        Text(workout.guidance, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { onComplete(workout) }) { Text("Log") }
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
        Text("Workout completion", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text(type.name.replace('_', ' '))
        OutlinedTextField(
            completed,
            { completed = it },
            label = { Text("Completed minutes") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("RPE ${rpe.roundToInt()}")
        Slider(value = rpe, onValueChange = { rpe = it }, valueRange = 1f..10f, steps = 8)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pain or concerning symptoms", modifier = Modifier.weight(1f))
            Switch(checked = pain, onCheckedChange = { pain = it })
        }
        Button(
            onClick = {
                onSave(type, planned, completed.toIntOrNull() ?: 0, rpe.roundToInt(), pain)
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save workout") }
    }
}

@Composable
fun ReviewScreen(state: MainUiState) {
    val review = state.review
    ScreenColumn {
        Text("Weekly review", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryCard("Adherence", "${review?.adherencePercent ?: 0}%", Modifier.weight(1f))
            SummaryCard("Strength", "${review?.strengthSessions ?: 0}", Modifier.weight(1f))
        }
        SummaryCard("Latest BP", review?.latestBp ?: "No entry", Modifier.fillMaxWidth())
        SummaryCard("Fasting glucose", review?.latestFastingGlucose?.toString() ?: "No entry", Modifier.fillMaxWidth())
        Text("What improved", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        review?.improved.orEmpty().forEach { Text("- $it") }
        Text("Needs attention", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        review?.needsAttention.orEmpty().forEach { Text("- $it") }
        Text("Next week focus", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(review?.nextWeekFocus.orEmpty())
    }
}

@Composable
fun SettingsScreen(onReminders: () -> Unit, onPrivacy: () -> Unit) {
    ScreenColumn {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        SettingsCard("Reminders", "Local workout and logging notifications.", Icons.Outlined.Notifications, onReminders)
        SettingsCard("Privacy", "Export or delete local data.", Icons.Outlined.PrivacyTip, onPrivacy)
    }
}

@Composable
fun ReminderSettingsScreen(state: MainUiState, onSave: (String, String, String, Int, Int, Boolean) -> Unit) {
    ScreenColumn {
        Text("Reminders", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        if (state.notificationDenied) Text("Notifications are currently denied in system settings.")
        ReminderEditor("exercise", "Exercise", "Time for your planned movement.", 8, 0, onSave)
        ReminderEditor("logging", "Health logging", "Log today's health signals.", 20, 30, onSave)
    }
}

@Composable
fun PrivacyScreen(state: MainUiState, onDelete: () -> Unit) {
    ScreenColumn {
        Text("Privacy", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text("All MVP data is stored on this device. No analytics SDKs, cloud backend, API keys, or network calls are included.")
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Local export preview", fontWeight = FontWeight.SemiBold)
                }
                Text(state.exportText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
            }
        }
        Button(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Delete, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Delete local data")
        }
    }
}

@Composable
private fun ScreenColumn(content: @Composable Column.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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
                Text("${assessment.reading.value ?: "pending"} ${assessment.reading.type.unit}", style = MaterialTheme.typography.bodyLarge)
                Text(assessment.clinicalMessage, style = MaterialTheme.typography.bodySmall)
            }
            StatusChips(assessment)
        }
    }
}

@Composable
private fun StatusChips(assessment: MetricAssessment) {
    Column(horizontalAlignment = Alignment.End) {
        AssistChip(onClick = {}, label = { Text(statusLabel(assessment.clinicalStatus)) })
        AssistChip(onClick = {}, label = { Text(targetLabel(assessment.targetStatus)) })
    }
}

@Composable
private fun InsightCardView(card: InsightCard) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(card.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(card.explanation)
            Divider()
            Text(card.whyItMatters, style = MaterialTheme.typography.bodyMedium)
            card.safeActions.forEach { Text("- $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun TrendCard(title: String, values: List<Double>) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (values.size < 2) {
                Text("Add more entries to see a trend.", style = MaterialTheme.typography.bodySmall)
            } else {
                val color = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                    val min = values.minOrNull() ?: 0.0
                    val max = values.maxOrNull() ?: 1.0
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
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(8.dp)) {
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
private fun ReminderEditor(id: String, title: String, body: String, hour: Int, minute: Int, onSave: (String, String, String, Int, Int, Boolean) -> Unit) {
    var enabled by rememberSaveable { mutableStateOf(true) }
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text("%02d:%02d".format(hour, minute), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = enabled, onCheckedChange = {
                enabled = it
                onSave(id, title, body, hour, minute, enabled)
            })
        }
    }
}

private fun statusLabel(status: ClinicalStatus): String = when (status) {
    ClinicalStatus.BELOW_RANGE -> "Below range"
    ClinicalStatus.NORMAL -> "Clinical normal"
    ClinicalStatus.ABOVE_RANGE -> "Above range"
    ClinicalStatus.PENDING -> "Pending"
    ClinicalStatus.UNKNOWN -> "Reference hidden"
}

private fun targetLabel(status: TargetStatus): String = when (status) {
    TargetStatus.MEETS_TARGET -> "On target"
    TargetStatus.ABOVE_PERSONAL_TARGET -> "Above target"
    TargetStatus.BELOW_PERSONAL_TARGET -> "Below target"
    TargetStatus.NO_TARGET -> "No target"
    TargetStatus.PENDING -> "Pending"
}
