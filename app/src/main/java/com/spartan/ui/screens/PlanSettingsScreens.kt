package com.spartan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.spartan.R
import com.spartan.domain.engine.GoalType
import com.spartan.domain.engine.PressureWindow
import com.spartan.domain.engine.ReferenceRanges
import com.spartan.domain.engine.SexAtBirth
import com.spartan.domain.engine.StressPatterns
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.PlannedWorkout
import com.spartan.ui.components.DetailScaffold
import com.spartan.ui.components.PageGutter
import com.spartan.ui.components.PrimaryButton
import com.spartan.ui.components.SecondaryButton
import com.spartan.ui.components.SpartanCard
import com.spartan.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/*
 * The configure-once parts of the plan — high-pressure windows, the healthy-ranges profile, the
 * weekly training template — each as its own focused page reached from You. Buckets are age and
 * sex only, never race (docs/COACH_DESIGN.md §2).
 */

@Composable
fun PressureWindowsScreen(
    state: MainUiState,
    onAddWindow: (Int, Int, Int, String) -> Unit,
    onRemoveWindow: (String) -> Unit,
    onBack: () -> Unit,
) {
    DetailScaffold(stringResource(R.string.you_windows), onBack) {
        item { PageIntro(stringResource(R.string.coach_windows_intro)) }
        item { PressureWindowsCard(state.pressureWindows, onAddWindow, onRemoveWindow) }
    }
}

@Composable
fun HealthyRangesScreen(
    state: MainUiState,
    onSaveDemographics: (Int?, SexAtBirth) -> Unit,
    onMetricClick: (MetricType) -> Unit,
    onBack: () -> Unit,
) {
    var showProfileSheet by remember { mutableStateOf(false) }
    DetailScaffold(stringResource(R.string.you_ranges), onBack) {
        item {
            PageIntro(stringResource(R.string.coach_ranges_intro, ReferenceRanges.cohortPhrase(state.userAgeYears, state.userSexAtBirth)))
        }
        item { HealthyRangesCard(state, onEditProfile = { showProfileSheet = true }, onMetricClick = onMetricClick) }
    }
    if (showProfileSheet) {
        ProfileSheet(state, onSave = { age, sex -> onSaveDemographics(age, sex); showProfileSheet = false }, onDismiss = { showProfileSheet = false })
    }
}

@Composable
fun TrainingWeekScreen(
    state: MainUiState,
    onEditMinutes: (String, Int) -> Unit,
    onComplete: (PlannedWorkout) -> Unit,
    onBack: () -> Unit,
) {
    DetailScaffold(stringResource(R.string.plan_title), onBack) {
        item { PageIntro(state.weeklyPlan?.focus.orEmpty()) }
        item { Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) { WeeklyPlanSection(state, onEditMinutes, onComplete) } }
    }
}

/** The one explanatory paragraph under a settings page's title. */
@Composable
private fun PageIntro(body: String) {
    if (body.isNotBlank()) {
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = Spacing.lg))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PressureWindowsCard(
    windows: List<PressureWindow>,
    onAddWindow: (Int, Int, Int, String) -> Unit,
    onRemoveWindow: (String) -> Unit,
) {
    var adding by remember { mutableStateOf(windows.isEmpty()) }
    var label by remember { mutableStateOf("") }
    var startHour by remember { mutableStateOf("11") }
    var endHour by remember { mutableStateOf("12") }
    var mask by remember { mutableIntStateOf(0) }

    SpartanCard {
        windows.forEachIndexed { i, w ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(w.label.ifBlank { stringResource(R.string.coach_windows_section_lower) }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(StressPatterns.describeWindow(w), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onRemoveWindow(w.id) }) { Text(stringResource(R.string.coach_window_remove), style = MaterialTheme.typography.labelLarge) }
            }
            if (i < windows.lastIndex || adding) HorizontalDivider(Modifier.padding(vertical = Spacing.md), color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (!adding) {
            SecondaryButton(stringResource(R.string.coach_window_add), { adding = true }, Modifier.fillMaxWidth().padding(top = Spacing.md), icon = Icons.Outlined.Add)
        } else {
            // FlowRow: seven day chips need ~400dp, so on a narrow phone they must wrap.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                DayOfWeek.entries.forEach { day ->
                    val bit = 1 shl day.ordinal
                    FilterChip(
                        selected = mask and bit != 0,
                        onClick = { mask = mask xor bit },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                    )
                }
            }
            OutlinedTextField(
                value = label, onValueChange = { label = it },
                label = { Text(stringResource(R.string.coach_window_label)) },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md), singleLine = true,
            )
            Row(Modifier.padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
            PrimaryButton(
                stringResource(R.string.coach_window_add),
                onClick = {
                    val start = (startHour.toIntOrNull() ?: 0).coerceIn(0, 23) * 60
                    val end = (endHour.toIntOrNull() ?: 0).coerceIn(1, 24) * 60
                    onAddWindow(mask, start, end, label)
                    adding = false; label = ""; mask = 0
                },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
                enabled = mask != 0 && (endHour.toIntOrNull() ?: 0) > (startHour.toIntOrNull() ?: 24),
            )
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
    SpartanCard {
        ReferenceRanges.topFive.forEachIndexed { i, metric ->
            val band = ReferenceRanges.bandFor(metric, age, sex) ?: return@forEachIndexed
            val latest = state.readings.filter { it.type == metric && it.value != null }.maxByOrNull { it.recordedAt }?.value
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = Spacing.md), color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(metric.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                TextButton(onClick = { onMetricClick(metric) }) {
                    Text(
                        latest?.let { stringResource(R.string.coach_range_yours, formatMetricValue(it, metric)) } ?: stringResource(R.string.common_pending),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            bandText(band.typicalLow, band.typicalHigh, metric)?.let {
                Text(stringResource(R.string.coach_range_typical, it), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(band.education, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
        }
        SecondaryButton(
            stringResource(if (age == null || sex == SexAtBirth.UNSPECIFIED) R.string.coach_ranges_profile_cta else R.string.coach_ranges_edit),
            onEditProfile,
            Modifier.fillMaxWidth().padding(top = Spacing.lg),
        )
    }
}

private fun bandText(low: Double?, high: Double?, metric: MetricType): String? = when {
    low != null && high != null -> "${low.toInt()}–${high.toInt()} ${metric.unit}".trim()
    low != null -> "${low.toInt()}${metric.unit.ifBlank { "" }}+".trim()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun GoalSetupSheet(onSave: (GoalType, Double, Int) -> Unit, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(GoalType.WEIGHT_LOSS) }
    var target by remember { mutableStateOf("10") }
    var weeks by remember { mutableStateOf("6") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = PageGutter).padding(bottom = Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(R.string.coach_set_goal), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.coach_goal_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                FilterChip(selected = type == GoalType.WEIGHT_LOSS, onClick = { type = GoalType.WEIGHT_LOSS; target = "10"; weeks = "6" }, label = { Text(stringResource(R.string.coach_goal_type_weight)) })
                FilterChip(selected = type == GoalType.SLEEP_RECOVERY, onClick = { type = GoalType.SLEEP_RECOVERY; target = "10"; weeks = "3" }, label = { Text(stringResource(R.string.coach_goal_type_sleep)) })
                FilterChip(selected = type == GoalType.STRESS_RESILIENCE, onClick = { type = GoalType.STRESS_RESILIENCE; target = "5"; weeks = "4" }, label = { Text(stringResource(R.string.coach_goal_type_stress)) })
            }
            OutlinedTextField(
                value = target,
                onValueChange = { target = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                label = {
                    Text(
                        stringResource(
                            when (type) {
                                GoalType.WEIGHT_LOSS -> R.string.coach_goal_target_weight
                                GoalType.SLEEP_RECOVERY -> R.string.coach_goal_target_sleep
                                GoalType.STRESS_RESILIENCE -> R.string.coach_goal_target_stress
                            },
                        ),
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
            PrimaryButton(
                stringResource(R.string.coach_goal_save),
                onClick = { onSave(type, target.toDoubleOrNull() ?: 0.0, (weeks.toIntOrNull() ?: 0).coerceAtLeast(1)) },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                enabled = (target.toDoubleOrNull() ?: 0.0) > 0.0 && (weeks.toIntOrNull() ?: 0) > 0,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ProfileSheet(state: MainUiState, onSave: (Int?, SexAtBirth) -> Unit, onDismiss: () -> Unit) {
    var age by remember { mutableStateOf(state.userAgeYears?.toString() ?: "") }
    var sex by remember { mutableStateOf(state.userSexAtBirth) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = PageGutter).padding(bottom = Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(R.string.coach_profile_sheet_title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.coach_profile_sheet_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = age,
                onValueChange = { age = it.filter(Char::isDigit).take(3) },
                label = { Text(stringResource(R.string.coach_profile_age)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                FilterChip(selected = sex == SexAtBirth.FEMALE, onClick = { sex = SexAtBirth.FEMALE }, label = { Text(stringResource(R.string.coach_sex_female)) })
                FilterChip(selected = sex == SexAtBirth.MALE, onClick = { sex = SexAtBirth.MALE }, label = { Text(stringResource(R.string.coach_sex_male)) })
                FilterChip(selected = sex == SexAtBirth.UNSPECIFIED, onClick = { sex = SexAtBirth.UNSPECIFIED }, label = { Text(stringResource(R.string.coach_sex_unspecified)) })
            }
            Spacer(Modifier.width(Spacing.xs))
            PrimaryButton(stringResource(R.string.coach_profile_save), { onSave(age.toIntOrNull(), sex) }, Modifier.fillMaxWidth())
        }
    }
}
