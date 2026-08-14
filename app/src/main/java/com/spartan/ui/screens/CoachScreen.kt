package com.spartan.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spartan.R
import com.spartan.domain.engine.GoalType
import com.spartan.domain.engine.PressureWindow
import com.spartan.domain.engine.ReferenceRanges
import com.spartan.domain.engine.SexAtBirth
import com.spartan.domain.engine.StressPatterns
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.PlannedWorkout
import com.spartan.ui.theme.Radius
import com.spartan.ui.theme.Spacing
import java.time.DayOfWeek

/**
 * The Coach hub: one focused goal, declared high-pressure windows, what the user's own data says,
 * age/sex-aware healthy-range education, and the weekly plan — the personalized training hub in
 * docs/COACH_DESIGN.md. Buckets are age+sex only, never race (§2 of the design doc).
 */
@Composable
fun CoachScreen(
    state: MainUiState,
    onSaveGoal: (GoalType, Double, Int) -> Unit,
    onAbandonGoal: (String) -> Unit,
    onDismissGoalNotice: () -> Unit,
    onAddWindow: (Int, Int, Int, String) -> Unit,
    onRemoveWindow: (String) -> Unit,
    onSaveDemographics: (Int?, SexAtBirth) -> Unit,
    onEditMinutes: (String, Int) -> Unit,
    onComplete: (PlannedWorkout) -> Unit,
    onMetricClick: (MetricType) -> Unit,
) {
    var showGoalSheet by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
        modifier = Modifier.widthIn(max = 600.dp).fillMaxSize().padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { Spacer(Modifier.height(Spacing.sm)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.coach_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (state.whoopIsMock) SampleDataChip()
            }
        }

        // Skeleton only on first load: post-onboarding a profile always exists once the health
        // bundle emits, so a null profile means data is still streaming in. Never on sync failure.
        if (state.profile == null && !state.syncFailed) {
            item { SkeletonRow(0.4f) }
            items(3) { Skeleton(Modifier.fillMaxWidth().height(76.dp)) }
        } else {
            state.goalNotice?.let { notice ->
                item {
                    Surface(
                        shape = RoundedCornerShape(Radius.card),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(Spacing.md)) {
                            Text(notice, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = onDismissGoalNotice) {
                                Text(stringResource(R.string.connections_import_dismiss))
                            }
                        }
                    }
                }
            }

            item { CoachSectionLabel(stringResource(R.string.coach_goal_section)) }
            item {
                GoalCard(
                    state = state,
                    onSetGoal = { showGoalSheet = true },
                    onAbandon = onAbandonGoal,
                )
            }

            item { CoachSectionLabel(stringResource(R.string.coach_windows_section)) }
            item {
                PressureWindowsCard(
                    windows = state.pressureWindows,
                    onAddWindow = onAddWindow,
                    onRemoveWindow = onRemoveWindow,
                )
            }

            if (state.weekdayEffects.isNotEmpty()) {
                item { CoachSectionLabel(stringResource(R.string.coach_stress_section)) }
                items(state.weekdayEffects.size) { i ->
                    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.card)) {
                        Text(
                            state.weekdayEffects[i].insight,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(Spacing.md),
                        )
                    }
                }
            }

            item { CoachSectionLabel(stringResource(R.string.coach_ranges_section)) }
            item {
                HealthyRangesCard(
                    state = state,
                    onEditProfile = { showProfileSheet = true },
                    onMetricClick = onMetricClick,
                )
            }

            item { CoachSectionLabel(stringResource(R.string.plan_title).uppercase()) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    WeeklyPlanSection(state, onEditMinutes, onComplete)
                }
            }
        }
        item { Spacer(Modifier.height(Spacing.lg)) }
    }
    }

    if (showGoalSheet) {
        GoalSetupSheet(
            onSave = { type, target, weeks ->
                onSaveGoal(type, target, weeks)
                showGoalSheet = false
            },
            onDismiss = { showGoalSheet = false },
        )
    }
    if (showProfileSheet) {
        ProfileSheet(
            state = state,
            onSave = { age, sex ->
                onSaveDemographics(age, sex)
                showProfileSheet = false
            },
            onDismiss = { showProfileSheet = false },
        )
    }
}

@Composable
private fun CoachSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
    )
}

@Composable
private fun GoalCard(state: MainUiState, onSetGoal: () -> Unit, onAbandon: (String) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.card)) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val goal = state.activeGoal
            if (goal == null) {
                Text(stringResource(R.string.coach_goal_intro), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onSetGoal, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.coach_set_goal))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        goalTitle(goal.type, goal.targetValue),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    state.goalProgress?.let { p ->
                        val (label, color) =
                            if (p.onTrack) stringResource(R.string.coach_goal_on_track) to MaterialTheme.colorScheme.primary
                            else stringResource(R.string.coach_goal_behind) to MaterialTheme.colorScheme.tertiary
                        Surface(shape = RoundedCornerShape(Radius.chip), color = color.copy(alpha = 0.14f)) {
                            Text(
                                label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                color = color, modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
                            )
                        }
                    }
                }
                state.goalProgress?.let { p ->
                    LinearProgressIndicator(
                        progress = { p.fraction.coerceIn(0.0, 1.0).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Text(p.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onAbandon(goal.id) }) {
                    Text(stringResource(R.string.coach_goal_abandon))
                }
            }
        }
    }
}

@Composable
private fun goalTitle(type: GoalType, target: Double): String = when (type) {
    GoalType.WEIGHT_LOSS -> "${stringResource(R.string.coach_goal_type_weight)} · ${target.toInt()} lb"
    GoalType.SLEEP_RECOVERY -> "${stringResource(R.string.coach_goal_type_sleep)} · +${target.toInt()}%"
    GoalType.STRESS_RESILIENCE -> "${stringResource(R.string.coach_goal_type_stress)} · ${target.toInt()}/wk"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PressureWindowsCard(
    windows: List<PressureWindow>,
    onAddWindow: (Int, Int, Int, String) -> Unit,
    onRemoveWindow: (String) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }
    var startHour by remember { mutableStateOf("11") }
    var endHour by remember { mutableStateOf("12") }
    var mask by remember { mutableStateOf(0) }

    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.card)) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.coach_windows_intro), style = MaterialTheme.typography.bodyMedium)
            windows.forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            w.label.ifBlank { stringResource(R.string.coach_windows_section).lowercase() },
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            StressPatterns.describeWindow(w),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onRemoveWindow(w.id) }) {
                        Text(stringResource(R.string.coach_window_remove))
                    }
                }
            }
            if (!adding) {
                OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.coach_window_add))
                }
            } else {
                // FlowRow: seven day chips need ~400dp — on a 360dp phone a fixed Row pushed
                // Saturday and Sunday off-screen entirely.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    DayOfWeek.entries.forEach { day ->
                        val bit = 1 shl day.ordinal
                        FilterChip(
                            selected = mask and bit != 0,
                            onClick = { mask = mask xor bit },
                            label = { Text(day.name.take(2)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text(stringResource(R.string.coach_window_label)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = startHour, onValueChange = { startHour = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.coach_window_start)) },
                        modifier = Modifier.weight(1f), singleLine = true,
                    )
                    OutlinedTextField(
                        value = endHour, onValueChange = { endHour = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.coach_window_end)) },
                        modifier = Modifier.weight(1f), singleLine = true,
                    )
                }
                Button(
                    onClick = {
                        val start = (startHour.toIntOrNull() ?: 0).coerceIn(0, 23) * 60
                        val end = (endHour.toIntOrNull() ?: 0).coerceIn(1, 24) * 60
                        onAddWindow(mask, start, end, label)
                        adding = false; label = ""; mask = 0
                    },
                    enabled = mask != 0 &&
                        (endHour.toIntOrNull() ?: 0) > (startHour.toIntOrNull() ?: 24),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.coach_window_add)) }
            }
        }
    }
}

@Composable
private fun HealthyRangesCard(
    state: MainUiState,
    onEditProfile: () -> Unit,
    onMetricClick: (MetricType) -> Unit,
) {
    val age = state.userAgeYears
    val sex = state.userSexAtBirth
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.card)) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                stringResource(
                    R.string.coach_ranges_intro,
                    bracketNoun(age, sex),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            ReferenceRanges.topFive.forEach { metric ->
                val band = ReferenceRanges.bandFor(metric, age, sex) ?: return@forEach
                val latest = state.readings
                    .filter { it.type == metric && it.value != null }
                    .maxByOrNull { it.recordedAt }?.value
                Column(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            metric.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onMetricClick(metric) }) {
                            Text(
                                latest?.let { stringResource(R.string.coach_range_yours, formatValue(it, metric)) }
                                    ?: stringResource(R.string.common_pending),
                            )
                        }
                    }
                    bandText(band.typicalLow, band.typicalHigh, metric)?.let {
                        Text(
                            stringResource(R.string.coach_range_typical, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        band.education,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(onClick = onEditProfile, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (age == null || sex == SexAtBirth.UNSPECIFIED)
                        stringResource(R.string.coach_ranges_profile_cta)
                    else stringResource(R.string.coach_ranges_edit),
                )
            }
        }
    }
}

@Composable
private fun bracketNoun(age: Int?, sex: SexAtBirth): String {
    val bracket = ReferenceRanges.bracketLabel(age)
    val noun = when (sex) {
        SexAtBirth.FEMALE -> stringResource(R.string.coach_sex_female).lowercase()
        SexAtBirth.MALE -> stringResource(R.string.coach_sex_male).lowercase()
        SexAtBirth.UNSPECIFIED -> "adult"
    }
    return if (age == null) noun else "$bracket $noun"
}

private fun formatValue(value: Double, metric: MetricType): String {
    val rounded = if (value == value.toInt().toDouble()) value.toInt().toString()
    else ((value * 10).toInt() / 10.0).toString()
    return if (metric.unit.isBlank()) rounded else "$rounded ${metric.unit}"
}

private fun bandText(low: Double?, high: Double?, metric: MetricType): String? = when {
    low != null && high != null -> "${low.toInt()}–${high.toInt()} ${metric.unit}".trim()
    low != null -> "${low.toInt()}${metric.unit.ifBlank { "" }}+".trim()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalSetupSheet(onSave: (GoalType, Double, Int) -> Unit, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(GoalType.WEIGHT_LOSS) }
    var target by remember { mutableStateOf("10") }
    var weeks by remember { mutableStateOf("6") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.coach_set_goal),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = type == GoalType.WEIGHT_LOSS,
                    onClick = { type = GoalType.WEIGHT_LOSS; target = "10"; weeks = "6" },
                    label = { Text(stringResource(R.string.coach_goal_type_weight)) },
                )
                FilterChip(
                    selected = type == GoalType.SLEEP_RECOVERY,
                    onClick = { type = GoalType.SLEEP_RECOVERY; target = "10"; weeks = "3" },
                    label = { Text(stringResource(R.string.coach_goal_type_sleep)) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = type == GoalType.STRESS_RESILIENCE,
                    onClick = { type = GoalType.STRESS_RESILIENCE; target = "5"; weeks = "4" },
                    label = { Text(stringResource(R.string.coach_goal_type_stress)) },
                )
            }
            OutlinedTextField(
                value = target,
                onValueChange = { target = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                label = {
                    Text(
                        when (type) {
                            GoalType.WEIGHT_LOSS -> stringResource(R.string.coach_goal_target_weight)
                            GoalType.SLEEP_RECOVERY -> stringResource(R.string.coach_goal_target_sleep)
                            GoalType.STRESS_RESILIENCE -> stringResource(R.string.coach_goal_target_stress)
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            OutlinedTextField(
                value = weeks,
                onValueChange = { weeks = it.filter(Char::isDigit).take(2) },
                label = { Text(stringResource(R.string.coach_goal_weeks)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Button(
                onClick = {
                    onSave(type, target.toDoubleOrNull() ?: 0.0, (weeks.toIntOrNull() ?: 0).coerceAtLeast(1))
                },
                enabled = (target.toDoubleOrNull() ?: 0.0) > 0.0 && (weeks.toIntOrNull() ?: 0) > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.coach_goal_save)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSheet(
    state: MainUiState,
    onSave: (Int?, SexAtBirth) -> Unit,
    onDismiss: () -> Unit,
) {
    var age by remember { mutableStateOf(state.userAgeYears?.toString() ?: "") }
    var sex by remember { mutableStateOf(state.userSexAtBirth) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.coach_profile_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.coach_profile_sheet_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = age,
                onValueChange = { age = it.filter(Char::isDigit).take(3) },
                label = { Text(stringResource(R.string.coach_profile_age)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = sex == SexAtBirth.FEMALE,
                    onClick = { sex = SexAtBirth.FEMALE },
                    label = { Text(stringResource(R.string.coach_sex_female)) },
                )
                FilterChip(
                    selected = sex == SexAtBirth.MALE,
                    onClick = { sex = SexAtBirth.MALE },
                    label = { Text(stringResource(R.string.coach_sex_male)) },
                )
                FilterChip(
                    selected = sex == SexAtBirth.UNSPECIFIED,
                    onClick = { sex = SexAtBirth.UNSPECIFIED },
                    label = { Text(stringResource(R.string.coach_sex_unspecified)) },
                )
            }
            Button(
                onClick = { onSave(age.toIntOrNull(), sex) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.coach_profile_save)) }
        }
    }
}
