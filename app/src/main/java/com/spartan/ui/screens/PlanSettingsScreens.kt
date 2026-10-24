package com.spartan.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spartan.R
import com.spartan.domain.engine.AvoidOption
import com.spartan.domain.engine.GoalBaselines
import com.spartan.domain.engine.GoalDraft
import com.spartan.domain.engine.GoalPlanning
import com.spartan.domain.engine.GoalPreset
import com.spartan.domain.engine.GoalType
import com.spartan.domain.engine.PlanConstraints
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
import com.spartan.ui.components.WellnessFooter
import com.spartan.ui.theme.Radius
import com.spartan.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/*
 * The configure-once parts of the plan — high-pressure windows, the healthy-ranges profile, the
 * weekly training template — each as its own focused page reached from You, plus the goal wizard
 * sheet. Buckets are age and sex only, never race (docs/COACH_DESIGN.md §2).
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
        item { WellnessFooter() }
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
                    CheckFilterChip(
                        label = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        selected = mask and bit != 0,
                        onClick = { mask = mask xor bit },
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
                CheckFilterChip(stringResource(R.string.coach_goal_type_weight), type == GoalType.WEIGHT_LOSS, onClick = { type = GoalType.WEIGHT_LOSS; target = "10"; weeks = "6" })
                CheckFilterChip(stringResource(R.string.coach_goal_type_sleep), type == GoalType.SLEEP_RECOVERY, onClick = { type = GoalType.SLEEP_RECOVERY; target = "10"; weeks = "3" })
                CheckFilterChip(stringResource(R.string.coach_goal_type_stress), type == GoalType.STRESS_RESILIENCE, onClick = { type = GoalType.STRESS_RESILIENCE; target = "5"; weeks = "4" })
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
                                // Sessions-a-week goals come from the goal wizard; this form never offers them.
                                GoalType.FITNESS, GoalType.RECOVERY -> R.string.wizard_custom_target_sessions
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
                CheckFilterChip(stringResource(R.string.coach_sex_female), sex == SexAtBirth.FEMALE, onClick = { sex = SexAtBirth.FEMALE })
                CheckFilterChip(stringResource(R.string.coach_sex_male), sex == SexAtBirth.MALE, onClick = { sex = SexAtBirth.MALE })
                CheckFilterChip(stringResource(R.string.coach_sex_unspecified), sex == SexAtBirth.UNSPECIFIED, onClick = { sex = SexAtBirth.UNSPECIFIED })
            }
            Spacer(Modifier.width(Spacing.xs))
            PrimaryButton(stringResource(R.string.coach_profile_save), { onSave(age.toIntOrNull(), sex) }, Modifier.fillMaxWidth())
        }
    }
}

/** Pick, make it specific, constraints, preview. */
private const val WIZARD_STEPS = 4

/** Time-a-day choices in minutes; "No limit" is null. */
private val WIZARD_MINUTE_CHOICES = listOf(10, 20, 30, 45, 60)

/** Sleep-window steppers move in quarter hours and wrap past midnight. */
private const val WIZARD_CLOCK_STEP = 15
private const val WIZARD_MINUTES_PER_DAY = 24 * 60

/** Where "Add" starts a sleep-window time without a real WHOOP night to start from (none, or sample data). */
private const val WIZARD_DEFAULT_BED_MINUTE = 22 * 60 + 30
private const val WIZARD_DEFAULT_WAKE_MINUTE = 7 * 60

/** Sleep targets move five points of sleep performance a tap; session targets, one session. */
private const val WIZARD_SLEEP_TARGET_STEP = 5.0
private const val WIZARD_SESSION_TARGET_STEP = 1.0

private const val WIZARD_LIMITS_MAX_CHARS = 200

/** Avoid options are saved as a bitmask so the selection survives rotation. */
private val AvoidOption.bit: Int get() = 1 shl ordinal

/** Stands in for "not given" in [ConstraintsSaver]: a minutes budget or a clock time is never negative. */
private const val NOT_GIVEN = -1

/**
 * The constraints being edited, kept across rotation and process recreation. The limits note is
 * kept as typed; [forPlan] trims it on the way out.
 */
internal val ConstraintsSaver: Saver<PlanConstraints, Any> = listSaver<PlanConstraints, Any>(
    save = {
        listOf(
            it.minutesPerDay ?: NOT_GIVEN,
            it.limitsNote,
            it.bedtimeMinute ?: NOT_GIVEN,
            it.wakeMinute ?: NOT_GIVEN,
            it.avoid.fold(0) { mask, option -> mask or option.bit },
        )
    },
    restore = { saved ->
        fun given(index: Int): Int? = (saved[index] as Int).takeIf { it != NOT_GIVEN }
        val mask = saved[4] as Int
        PlanConstraints(
            minutesPerDay = given(0),
            limitsNote = saved[1] as String,
            bedtimeMinute = given(2),
            wakeMinute = given(3),
            avoid = AvoidOption.entries.filter { mask and it.bit != 0 }.toSet(),
        )
    },
)

/** Won't-dos [preset]'s goal is built on, so they can't be picked alongside it; none without a goal. */
internal fun blockedAvoidOptions(preset: GoalPreset?): Set<AvoidOption> =
    if (preset == null) {
        emptySet()
    } else {
        AvoidOption.entries.filter { GoalPlanning.conflicts(preset, PlanConstraints(avoid = setOf(it))) }.toSet()
    }

/** What the plan is given: the limits note trimmed, and no won't-do the goal is built on. */
internal fun PlanConstraints.forPlan(blocked: Set<AvoidOption>): PlanConstraints =
    copy(limitsNote = limitsNote.trim(), avoid = avoid - blocked)

/**
 * The goal wizard (research §4.1): one primary goal from four presets, made SMART against the
 * user's own recent average, fitted to their constraints, and previewed for week one only. A
 * bottom sheet rather than a new tab, and pre-filled at every step — never a blank form — except
 * the sleep window, which starts from what the user saved and is never filled in for them.
 * [initialPreset] pre-selects a goal (the weekly check-in's "Change it"); "Set a custom goal"
 * hands over to [GoalSetupSheet], which keeps the weight / sleep / stress custom goals.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalWizardSheet(
    state: MainUiState,
    initialPreset: GoalPreset? = null,
    onStart: (GoalPreset, Double, Int, PlanConstraints) -> Unit,
    onCustomGoal: () -> Unit,
    onDismiss: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    var preset by rememberSaveable { mutableStateOf(initialPreset) }
    // The user's nudges to Spartan's suggestion; null keeps the suggestion.
    var targetOverride by rememberSaveable { mutableStateOf<Double?>(null) }
    var weeksOverride by rememberSaveable { mutableStateOf<Int?>(null) }
    // Constraints start from what the user told Spartan before — never from observed or sample
    // sleep — so Start saves nothing the user did not set or change.
    var edited by rememberSaveable(stateSaver = ConstraintsSaver) { mutableStateOf(state.planConstraints) }

    val draft = preset?.let { GoalPlanning.draft(it, state.goalBaselines, targetOverride, weeksOverride) }
    val blocked = blockedAvoidOptions(preset)
    val constraints = edited.forPlan(blocked)
    // Steps 2–4 build on a chosen goal, so without one the wizard is on step 1.
    val shownStep = if (draft == null) 1 else step
    val scroll = rememberScrollState()
    LaunchedEffect(shownStep) { scroll.scrollTo(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = PageGutter).padding(bottom = Spacing.xxl)) {
            // The body scrolls on its own so large fonts reflow while Back / Next stay in reach.
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.wizard_step_of, shownStep, WIZARD_STEPS),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    // The sheet covers the screen's own chip: its averages are sample data until WHOOP connects.
                    if (state.whoopIsMock) SampleDataChip()
                }
                if (draft == null) {
                    WizardPickStep(selected = null, onSelect = { preset = it }, onCustomGoal = onCustomGoal)
                } else {
                    when (shownStep) {
                        1 -> WizardPickStep(
                            selected = preset,
                            onSelect = { picked ->
                                if (picked != preset) {
                                    preset = picked
                                    targetOverride = null
                                    weeksOverride = null
                                }
                            },
                            onCustomGoal = onCustomGoal,
                        )
                        2 -> WizardSmartStep(
                            draft = draft,
                            baselines = state.goalBaselines,
                            isSample = state.whoopIsMock,
                            targetOverride = targetOverride,
                            weeksOverride = weeksOverride,
                            onTarget = { targetOverride = it },
                            onWeeks = { weeksOverride = it },
                        )
                        3 -> WizardConstraintsStep(
                            title = stringResource(R.string.wizard_constraints_title),
                            edited = edited,
                            onEdit = { change -> edited = change(edited) },
                            blocked = blocked,
                            goalPreset = preset,
                            state = state,
                        )
                        else -> WizardPreviewStep(draft, constraints)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (draft != null && shownStep == WIZARD_STEPS) {
                    PrimaryButton(
                        stringResource(R.string.wizard_start),
                        onClick = { onStart(draft.preset, draft.targetValue, draft.weeks, constraints) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    PrimaryButton(
                        stringResource(R.string.wizard_next),
                        onClick = { step = shownStep + 1 },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = draft != null,
                    )
                }
                if (shownStep > 1) {
                    SecondaryButton(stringResource(R.string.common_back), { step = shownStep - 1 }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/**
 * Plan settings, opened from You: the wizard's constraints step on its own. Saves only the
 * constraints — the goal and its progress are untouched — and a won't-do the active goal is built
 * on stays unavailable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanConstraintsSheet(state: MainUiState, onSave: (PlanConstraints) -> Unit, onDismiss: () -> Unit) {
    var edited by rememberSaveable(stateSaver = ConstraintsSaver) { mutableStateOf(state.planConstraints) }
    val goalPreset = state.activeGoal?.let { GoalPreset.forGoalType(it.type) }
    val blocked = blockedAvoidOptions(goalPreset)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = PageGutter).padding(bottom = Spacing.xxl)) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                WizardConstraintsStep(
                    title = stringResource(R.string.wizard_plan_settings_title),
                    edited = edited,
                    onEdit = { change -> edited = change(edited) },
                    blocked = blocked,
                    goalPreset = goalPreset,
                    state = state,
                )
            }
            Column(Modifier.fillMaxWidth().padding(top = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PrimaryButton(stringResource(R.string.wizard_plan_settings_save), { onSave(edited.forPlan(blocked)) }, Modifier.fillMaxWidth())
                SecondaryButton(stringResource(R.string.common_cancel), onDismiss, Modifier.fillMaxWidth())
            }
        }
    }
}

/** Step 1: one primary goal, each preset in plain words. */
@Composable
private fun WizardPickStep(selected: GoalPreset?, onSelect: (GoalPreset) -> Unit, onCustomGoal: () -> Unit) {
    WizardHeading(stringResource(R.string.wizard_pick_title))
    WizardBody(stringResource(R.string.wizard_pick_body))
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        GoalPreset.entries.forEach { preset ->
            WizardPresetOption(preset, selected = preset == selected, onClick = { onSelect(preset) })
        }
    }
    TextButton(onClick = onCustomGoal) {
        Text(stringResource(R.string.wizard_custom_goal), style = MaterialTheme.typography.labelLarge)
    }
}

/** One radio-style goal card: the radio dot and border carry the selection, not colour alone. */
@Composable
private fun WizardPresetOption(preset: GoalPreset, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Radius.tile)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, shape)
            .background(if (selected) colors.primaryContainer else colors.surface)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .heightIn(min = 64.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                preset.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            )
            Text(
                presetDescription(preset),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun presetDescription(preset: GoalPreset): String = stringResource(
    when (preset) {
        GoalPreset.SLEEP_BETTER -> R.string.wizard_preset_sleep_body
        GoalPreset.GET_FITTER -> R.string.wizard_preset_fitter_body
        GoalPreset.FEEL_STEADIER -> R.string.wizard_preset_steadier_body
        GoalPreset.RECOVER_WELL -> R.string.wizard_preset_recover_body
    },
)

/**
 * Step 2: the SMART sentence, anchored to the user's own recent average (said to be the sample
 * data's when [isSample]), with steppers for the target and the review date. The steppers ask
 * [GoalPlanning.draft] what a tap would give rather than copying its safe limits, so a button is
 * live only when tapping it changes the goal.
 */
@Composable
private fun WizardSmartStep(
    draft: GoalDraft,
    baselines: GoalBaselines,
    isSample: Boolean,
    targetOverride: Double?,
    weeksOverride: Int?,
    onTarget: (Double) -> Unit,
    onWeeks: (Int) -> Unit,
) {
    val sleep = draft.preset == GoalPreset.SLEEP_BETTER
    val targetStep = if (sleep) WIZARD_SLEEP_TARGET_STEP else WIZARD_SESSION_TARGET_STEP
    fun targetAt(value: Double) = GoalPlanning.draft(draft.preset, baselines, value, weeksOverride).targetDisplayValue
    fun weeksAt(value: Int) = GoalPlanning.draft(draft.preset, baselines, targetOverride, value).weeks
    val lowerTarget = targetAt(draft.targetDisplayValue - targetStep)
    val higherTarget = targetAt(draft.targetDisplayValue + targetStep)
    val fewerWeeks = weeksAt(draft.weeks - 1)
    val moreWeeks = weeksAt(draft.weeks + 1)
    val target = draft.targetDisplayValue.roundToInt()

    WizardHeading(stringResource(R.string.wizard_smart_title))
    Surface(
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            draft.smartSentence,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(Spacing.xl),
        )
    }
    WizardBody(
        stringResource(
            when {
                draft.baselineDisplayValue == null -> R.string.wizard_smart_learning
                // Sample data is never presented as the user's own average.
                isSample -> R.string.wizard_smart_anchored_sample
                else -> R.string.wizard_smart_anchored
            },
        ),
    )
    if (sleep) WizardBody(stringResource(R.string.wizard_smart_sleep_note))
    WizardStepper(
        label = stringResource(R.string.wizard_target_label),
        value = if (sleep) {
            stringResource(R.string.wizard_target_sleep_value, target)
        } else {
            pluralStringResource(R.plurals.wizard_sessions_week, target, target)
        },
        decreaseLabel = stringResource(R.string.wizard_target_lower),
        increaseLabel = stringResource(R.string.wizard_target_higher),
        canDecrease = lowerTarget < draft.targetDisplayValue,
        canIncrease = higherTarget > draft.targetDisplayValue,
        onDecrease = { onTarget(lowerTarget) },
        onIncrease = { onTarget(higherTarget) },
    )
    WizardStepper(
        label = stringResource(R.string.wizard_weeks_label),
        value = pluralStringResource(R.plurals.wizard_weeks, draft.weeks, draft.weeks),
        decreaseLabel = stringResource(R.string.wizard_weeks_fewer),
        increaseLabel = stringResource(R.string.wizard_weeks_more),
        canDecrease = fewerWeeks < draft.weeks,
        canIncrease = moreWeeks > draft.weeks,
        onDecrease = { onWeeks(fewerWeeks) },
        onIncrease = { onWeeks(moreWeeks) },
    )
}

/**
 * Time, sleep window, limits and won't-dos — every one optional. The wizard's step 3 and the Plan
 * settings sheet. Bed and wake times are never filled in for the user: they start as saved (or
 * "Not set"), and "Add" starts the stepper from the most recent real WHOOP night — a neutral
 * 22:30 / 07:00 on sample data. A won't-do in [blocked] can't be picked with [goalPreset], and a
 * line says why. [onEdit] applies a change to the latest edit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WizardConstraintsStep(
    title: String,
    edited: PlanConstraints,
    onEdit: ((PlanConstraints) -> PlanConstraints) -> Unit,
    blocked: Set<AvoidOption>,
    goalPreset: GoalPreset?,
    state: MainUiState,
) {
    WizardHeading(title)
    WizardBody(stringResource(R.string.wizard_constraints_body))

    WizardSectionLabel(stringResource(R.string.wizard_time_title))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        WIZARD_MINUTE_CHOICES.forEach { minutes ->
            CheckFilterChip(
                label = stringResource(R.string.wizard_minutes_chip, minutes),
                selected = edited.minutesPerDay == minutes,
                onClick = { onEdit { it.copy(minutesPerDay = minutes) } },
            )
        }
        CheckFilterChip(
            label = stringResource(R.string.wizard_no_limit),
            selected = edited.minutesPerDay == null,
            onClick = { onEdit { it.copy(minutesPerDay = null) } },
        )
    }

    WizardSectionLabel(stringResource(R.string.wizard_sleep_window_title))
    WizardBody(stringResource(R.string.wizard_bedtime_target_note))
    // Sample sleep is never offered as the user's own.
    val realBed = if (state.whoopIsMock) null else state.observedBedMinute
    val realWake = if (state.whoopIsMock) null else state.observedWakeMinute
    if ((edited.bedtimeMinute == null && realBed != null) || (edited.wakeMinute == null && realWake != null)) {
        WizardBody(stringResource(R.string.wizard_sleep_window_add_hint))
    }
    WizardClockSetting(
        label = stringResource(R.string.wizard_bedtime),
        minute = edited.bedtimeMinute,
        addMinute = realBed ?: WIZARD_DEFAULT_BED_MINUTE,
        addDescription = stringResource(R.string.wizard_bedtime_add_a11y),
        clearDescription = stringResource(R.string.wizard_bedtime_clear_a11y),
        earlierDescription = stringResource(R.string.wizard_bedtime_earlier),
        laterDescription = stringResource(R.string.wizard_bedtime_later),
        onChange = { minute -> onEdit { it.copy(bedtimeMinute = minute) } },
    )
    WizardClockSetting(
        label = stringResource(R.string.wizard_wake),
        minute = edited.wakeMinute,
        addMinute = realWake ?: WIZARD_DEFAULT_WAKE_MINUTE,
        addDescription = stringResource(R.string.wizard_wake_add_a11y),
        clearDescription = stringResource(R.string.wizard_wake_clear_a11y),
        earlierDescription = stringResource(R.string.wizard_wake_earlier),
        laterDescription = stringResource(R.string.wizard_wake_later),
        onChange = { minute -> onEdit { it.copy(wakeMinute = minute) } },
    )

    // Shown back on training activities, never interpreted: pain and injuries belong with a clinician.
    OutlinedTextField(
        value = edited.limitsNote,
        onValueChange = { text -> onEdit { it.copy(limitsNote = text.take(WIZARD_LIMITS_MAX_CHARS)) } },
        label = { Text(stringResource(R.string.wizard_limits_label)) },
        supportingText = { Text(stringResource(R.string.wizard_limits_support)) },
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
    )

    WizardSectionLabel(stringResource(R.string.wizard_avoid_title))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        AvoidOption.entries.forEach { option ->
            val available = option !in blocked
            CheckFilterChip(
                label = option.label,
                selected = available && option in edited.avoid,
                onClick = { onEdit { it.copy(avoid = if (option in it.avoid) it.avoid - option else it.avoid + option) } },
                enabled = available,
            )
        }
    }
    // A disabled chip never goes unexplained.
    if (goalPreset != null) {
        blocked.forEach { option -> WizardBody(stringResource(R.string.wizard_avoid_conflict, option.label, goalPreset.label)) }
    }
}

/** A filter chip whose selection shows as a check as well as the fill, never by colour alone. */
@Composable
private fun CheckFilterChip(label: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        enabled = enabled,
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
    )
}

/** A usual bed or wake time: a ±15-minute stepper with Clear, or "Not set" with Add (which starts at [addMinute]). */
@Composable
private fun WizardClockSetting(
    label: String,
    minute: Int?,
    addMinute: Int,
    addDescription: String,
    clearDescription: String,
    earlierDescription: String,
    laterDescription: String,
    onChange: (Int?) -> Unit,
) {
    if (minute == null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.wizard_time_not_set), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { onChange(Math.floorMod(addMinute, WIZARD_MINUTES_PER_DAY)) }, modifier = Modifier.semantics { contentDescription = addDescription }) {
                Text(stringResource(R.string.wizard_time_add), style = MaterialTheme.typography.labelLarge)
            }
        }
    } else {
        val clock = clockFormatter()
        val m = Math.floorMod(minute, WIZARD_MINUTES_PER_DAY)
        WizardStepper(
            label = label,
            value = LocalTime.of(m / 60, m % 60).format(clock),
            decreaseLabel = earlierDescription,
            increaseLabel = laterDescription,
            canDecrease = true,
            canIncrease = true,
            onDecrease = { onChange(Math.floorMod(m - WIZARD_CLOCK_STEP, WIZARD_MINUTES_PER_DAY)) },
            onIncrease = { onChange(Math.floorMod(m + WIZARD_CLOCK_STEP, WIZARD_MINUTES_PER_DAY)) },
            trailing = {
                TextButton(onClick = { onChange(null) }, modifier = Modifier.semantics { contentDescription = clearDescription }) {
                    Text(stringResource(R.string.wizard_time_clear), style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }
}

/** Step 4: week one only — each action and how often, the daily load, then one tap to start. */
@Composable
private fun WizardPreviewStep(draft: GoalDraft, constraints: PlanConstraints) {
    // The preview's clock times follow the device's 12/24-hour setting, like the rest of the app.
    val use24HourClock = DateFormat.is24HourFormat(LocalContext.current)
    val preview = remember(draft, constraints, use24HourClock) { GoalPlanning.preview(draft, constraints, use24HourClock) }
    WizardHeading(stringResource(R.string.wizard_preview_title))
    WizardBody(draft.smartSentence)
    SpartanCard {
        preview.items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = Spacing.sm), color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                // A one-minute cue (lights out) has no session length worth stating.
                if (item.minutes > 1) {
                    stringResource(R.string.wizard_preview_item, item.title, item.timesThisWeek, item.minutes)
                } else {
                    stringResource(R.string.wizard_preview_item_untimed, item.title, item.timesThisWeek)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    Text(preview.summary, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    WizardBody(stringResource(R.string.wizard_preview_weekly_note))
}

/**
 * A labelled −/+ stepper with 48dp buttons. The value is a polite live region, so TalkBack reads
 * the new value after each tap.
 */
@Composable
private fun WizardStepper(
    label: String,
    value: String,
    decreaseLabel: String,
    increaseLabel: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onDecrease, enabled = canDecrease, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Remove, contentDescription = decreaseLabel)
            }
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.sm)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            FilledTonalIconButton(onClick = onIncrease, enabled = canIncrease, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = increaseLabel)
            }
        }
    }
}

@Composable
private fun WizardHeading(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
}

@Composable
private fun WizardSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
    )
}

@Composable
private fun WizardBody(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
