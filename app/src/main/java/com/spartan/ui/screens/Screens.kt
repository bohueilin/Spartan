package com.spartan.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spartan.R
import com.spartan.data.local.ReminderFrequency
import com.spartan.domain.model.ClinicalStatus
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.PlannedWorkout
import com.spartan.domain.model.TargetStatus
import com.spartan.domain.model.WorkoutType
import com.spartan.ui.components.DetailPage
import com.spartan.ui.components.IconTile
import com.spartan.ui.components.PrimaryButton
import com.spartan.ui.components.SecondaryButton
import com.spartan.ui.components.SpartanCard
import com.spartan.ui.theme.Motion
import com.spartan.ui.theme.Radius
import com.spartan.ui.theme.Spacing
import com.spartan.ui.theme.SpartanBands
import com.spartan.ui.theme.rememberReducedMotion
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun OnboardingScreen(onComplete: (String, Double?, Int?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }
    // A Surface, not a background modifier: this screen renders outside the Scaffold, so nothing
    // else provides LocalContentColor here and unstyled text would fall back to plain black —
    // invisible against the dark theme's background (verified on-device).
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(
        // Paints its own insets too (the window can show the wallpaper through on some devices).
        modifier = Modifier.fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
        OnboardingHero()
        Spacer(Modifier.height(Spacing.xxl))
        Text(
            stringResource(R.string.common_brand).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 3.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            stringResource(R.string.onboarding_tagline),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
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
        Spacer(Modifier.height(12.dp))
        // Optional: tailors follow-along video picks toward age-appropriate, joint-friendly sessions.
        OutlinedTextField(
            age,
            { age = it.filter(Char::isDigit).take(3) },
            label = { Text(stringResource(R.string.onboarding_age_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(
            stringResource(R.string.onboarding_begin),
            { onComplete(name, height.toDoubleOrNull(), age.toIntOrNull()?.takeIf { it in 13..100 }) },
            Modifier.fillMaxWidth().padding(top = Spacing.xl),
        )
        Text(
            stringResource(R.string.onboarding_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 20.dp),
        )
        }
    }
    }
}


/**
 * The first thing a new user sees: three concentric arcs sweeping in, in the readiness palette.
 *
 * Deliberately abstract — no number, no percentage. A ring with a score on it would read as a
 * health reading, and at this point in the flow Spartan has no data about this person; the
 * provenance rules forbid anything that could be mistaken for one.
 */
@Composable
private fun OnboardingHero() {
    val reduced = rememberReducedMotion()
    val primed = if (isSystemInDarkTheme()) SpartanBands.primedDark else SpartanBands.primedLight
    val easy = if (isSystemInDarkTheme()) SpartanBands.easyDark else SpartanBands.easyLight
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outline

    var started by rememberSaveable { mutableStateOf(false) }
    val sweep by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = if (reduced) snap() else tween(Motion.slow),
        label = "heroSweep",
    )
    LaunchedEffect(Unit) { started = true }

    Box(
        Modifier.fillMaxWidth().height(104.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(104.dp)) {
            val stroke = 10.dp.toPx()
            val rings = listOf(
                Triple(accent, 0.72f, 0f),
                Triple(primed, 0.48f, 1f),
                Triple(easy, 0.30f, 2f),
            )
            rings.forEach { (ringColor, fraction, step) ->
                val inset = stroke * 1.9f * step
                val diameter = size.minDimension - inset * 2 - stroke
                val topLeft = Offset(inset + stroke / 2, inset + stroke / 2)
                val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
                drawArc(
                    color = track,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction * sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}

/**
 * Persistent "your real WHOOP data is in" banner at the top of the Metrics tab — the same message
 * the Connections import success card shows, kept visible so the numbers below always carry their
 * provenance and date span. Accent-tinted so it reads as a positive state, not a warning.
 */
@Composable
internal fun WhoopImportBanner(info: WhoopImportInfo) {
    val fmt = remember { java.time.format.DateTimeFormatter.ofPattern("MMM d") }
    val range = "${fmt.format(java.time.LocalDate.ofEpochDay(info.firstDayEpoch))} – " +
        fmt.format(java.time.LocalDate.ofEpochDay(info.lastDayEpoch))
    Surface(
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.metrics_import_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.metrics_import_banner_body, info.days, range),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    DetailPage(if (initialReading == null) stringResource(R.string.metrics_add_metric) else stringResource(R.string.metrics_edit_metric), onDone) {
        Box {
            FilledTonalButton(onClick = { expanded = true }) { Text(type.label) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                // Blood pressure is not offered (HIDDEN_METRICS); readings already stored keep their type.
                MetricType.entries.filterNot { it in HIDDEN_METRICS }.forEach {
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
        PrimaryButton(
            stringResource(R.string.common_save),
            { if (onSave(type, value, note)) onDone() else showError = true },
            Modifier.fillMaxWidth(),
        )
    }
}

/** The weekly-plan cards, rendered inside the Coach hub. */
/**
 * The weekly training template, one card per session: what, how hard, how long (adjustable in
 * five-minute steps), and a Log button. The page above it already states the week's focus.
 */
@Composable
fun WeeklyPlanSection(state: MainUiState, onEditMinutes: (String, Int) -> Unit, onComplete: (PlannedWorkout) -> Unit) {
    state.weeklyPlan?.workouts.orEmpty().forEach { workout ->
        val key = "${workout.day}-${workout.type.name}"
        val minutes = workout.minutes
        SpartanCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(Radius.tile)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Text(workout.day, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(Spacing.lg))
                Column(Modifier.weight(1f)) {
                    Text(workoutTypeLabel(workout.type), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(workout.intensity, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(workout.guidance, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.md))
            Row(Modifier.padding(top = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                // Stepper: the two 48dp buttons carry the spoken labels; the number between them is the value.
                Row(
                    Modifier.clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onEditMinutes(key, (minutes - 5).coerceAtLeast(5)) }) {
                        Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.plan_minus_a11y, workoutTypeLabel(workout.type)))
                    }
                    Text(stringResource(R.string.insights_minutes, minutes), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = { onEditMinutes(key, (minutes + 5).coerceAtMost(180)) }) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.plan_plus_a11y, workoutTypeLabel(workout.type)))
                    }
                }
                Spacer(Modifier.weight(1f))
                // Tonal, not filled: seven-plus per-card actions must defer to the page's primary.
                FilledTonalButton(onClick = { onComplete(workout.copy(minutes = minutes)) }, shape = CircleShape) {
                    Text(stringResource(R.string.plan_log), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    state.weeklyPlan?.safetyNote?.takeIf { it.isNotBlank() }?.let { SafetyBanner(it) }
}

@Composable
fun WorkoutCompletionScreen(
    type: WorkoutType,
    planned: Int,
    alreadyLoggedToday: Boolean,
    onSave: (WorkoutType, Int, Int, Int, Boolean) -> Unit,
    onDone: () -> Unit,
) {
    var completed by rememberSaveable { mutableStateOf(planned.toString()) }
    var rpe by rememberSaveable { mutableStateOf(5f) }
    var pain by rememberSaveable { mutableStateOf(false) }
    DetailPage(stringResource(R.string.workout_title), onDone) {
        Text(workoutTypeLabel(type))
        OutlinedTextField(
            completed,
            { completed = it },
            label = { Text(stringResource(R.string.workout_completed_minutes)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.workout_rpe, rpe.roundToInt()))
        val effortDescription = stringResource(R.string.a11y_effort_slider)
        Slider(
            value = rpe,
            onValueChange = { rpe = it },
            valueRange = 1f..10f,
            steps = 8,
            // The visible "RPE N" text is a separate node; without this TalkBack announces an
            // anonymous slider — and this value feeds the deload rules.
            modifier = Modifier.semantics { contentDescription = effortDescription },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // Row-level toggleable merges label + role + state into one TalkBack announcement
            // and makes the whole row the target — this switch drives the pain deload.
            modifier = Modifier.toggleable(value = pain, role = Role.Switch, onValueChange = { pain = it }),
        ) {
            Text(stringResource(R.string.workout_pain_label), modifier = Modifier.weight(1f))
            Switch(checked = pain, onCheckedChange = null)
        }
        if (alreadyLoggedToday) {
            Text(
                stringResource(R.string.workout_already_logged, workoutTypeLabel(type)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PrimaryButton(
            stringResource(R.string.workout_save),
            {
                onSave(type, planned, completed.toIntOrNull() ?: 0, rpe.roundToInt(), pain)
                onDone()
            },
            Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun ReminderSettingsScreen(
    onBack: () -> Unit,
    state: MainUiState,
    onRequestNotifications: () -> Unit,
    onSave: (String, String, String, Int, Int, Boolean, ReminderFrequency, Int) -> Unit,
) {
    DetailPage(stringResource(R.string.reminders_title), onBack) {
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
fun PrivacyScreen(state: MainUiState, onShare: (String) -> Unit, onDelete: () -> Unit, onBack: () -> Unit) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    DetailPage(stringResource(R.string.privacy_title), onBack) {
        Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SpartanCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Outlined.Download, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Spacing.md))
                Text(stringResource(R.string.privacy_export_preview), style = MaterialTheme.typography.titleMedium)
            }
            // A preview, not the file: a year of readings is thousands of lines in one Text. Share
            // still sends everything, and the count says so.
            val lines = state.exportText.trimEnd('\n').lines() // the file ends in a newline, not a 31st line
            Text(
                lines.take(30).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md),
            )
            if (lines.size > 30) {
                Text(
                    pluralStringResource(R.plurals.privacy_export_more, lines.size - 30, lines.size - 30),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
        PrimaryButton(stringResource(R.string.privacy_share_export), { onShare(state.exportText) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Share)
        Text(stringResource(R.string.privacy_share_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Outlined + error content: the destructive action must not share the primary style of
        // "Share local export" above it.
        OutlinedButton(
            onClick = { confirmDelete = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg).heightIn(min = 52.dp),
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(R.string.privacy_delete_data), style = MaterialTheme.typography.labelLarge)
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

@OptIn(ExperimentalLayoutApi::class)
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

    SpartanCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text("%02d:%02d".format(hour, minute), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        if (validTime && checked) onRequestNotifications()
                        if (validTime) onSave(id, title, body, parsedHour ?: hour, parsedMinute ?: minute, checked, selectedFrequency, selectedDaysMask)
                    },
                    // Names which reminder this switch controls — TalkBack otherwise hears "off, switch".
                    modifier = Modifier.semantics { contentDescription = title },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = hourText,
                    onValueChange = { hourText = it },
                    label = { Text(stringResource(R.string.reminders_hour_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = parsedHour !in 0..23,
                    supportingText = { if (parsedHour !in 0..23) Text(stringResource(R.string.reminders_hour_error)) },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = minuteText,
                    onValueChange = { minuteText = it },
                    label = { Text(stringResource(R.string.reminders_minute_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = parsedMinute !in 0..59,
                    supportingText = { if (parsedMinute !in 0..59) Text(stringResource(R.string.reminders_minute_error)) },
                    modifier = Modifier.weight(1f),
                )
            }
            // FilterChips in a FlowRow: selection carries real semantics (no width-jumping
            // "selected" suffix) and the options wrap instead of clipping on narrow phones.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                ReminderFrequency.entries.forEach { option ->
                    val label = when (option) {
                        ReminderFrequency.DAILY -> stringResource(R.string.reminders_frequency_daily)
                        ReminderFrequency.WEEKDAYS -> stringResource(R.string.reminders_frequency_weekdays)
                        ReminderFrequency.WEEKENDS -> stringResource(R.string.reminders_frequency_weekends)
                        ReminderFrequency.CUSTOM_DAYS -> stringResource(R.string.reminders_frequency_custom)
                    }
                    FilterChip(
                        selected = selectedFrequency == option,
                        onClick = { selectedFrequency = option },
                        enabled = option != ReminderFrequency.CUSTOM_DAYS,
                        label = { Text(label) },
                    )
                }
            }
            SecondaryButton(
                stringResource(R.string.reminders_save),
                {
                    if (enabled) onRequestNotifications()
                    onSave(id, title, body, parsedHour ?: hour, parsedMinute ?: minute, enabled, selectedFrequency, selectedDaysMask)
                },
                Modifier.fillMaxWidth(),
                enabled = validTime,
            )
        }
    }
}

/** "ZONE_2" → "Zone 2": enum names never appear raw in user copy. */
internal fun workoutTypeLabel(type: WorkoutType): String =
    // ROOT for the ASCII enum constant; default locale only for the display titlecase.
    type.name.replace('_', ' ').lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.getDefault()) }

private fun daysMaskFor(frequency: ReminderFrequency, customMask: Int): Int = when (frequency) {
    ReminderFrequency.DAILY -> 127
    ReminderFrequency.WEEKDAYS -> 31
    ReminderFrequency.WEEKENDS -> 96
    ReminderFrequency.CUSTOM_DAYS -> customMask
}

internal fun statusLabelRes(status: ClinicalStatus): Int = when (status) {
    ClinicalStatus.BELOW_RANGE -> R.string.status_below_range
    ClinicalStatus.NORMAL -> R.string.status_clinical_normal
    ClinicalStatus.ABOVE_RANGE -> R.string.status_above_range
    ClinicalStatus.PENDING -> R.string.status_pending
    ClinicalStatus.UNKNOWN -> R.string.status_reference_hidden
}

internal fun targetLabelRes(status: TargetStatus): Int = when (status) {
    TargetStatus.MEETS_TARGET -> R.string.target_meets
    TargetStatus.ABOVE_PERSONAL_TARGET -> R.string.target_above
    TargetStatus.BELOW_PERSONAL_TARGET -> R.string.target_below
    TargetStatus.NO_TARGET -> R.string.target_none
    TargetStatus.PENDING -> R.string.status_pending
}
