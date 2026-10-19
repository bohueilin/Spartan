package com.spartan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spartan.R
import com.spartan.domain.engine.GoalType
import com.spartan.domain.engine.ProjectionEngine
import com.spartan.domain.model.MetricType
import com.spartan.ui.components.IconTile
import com.spartan.ui.components.ListRow
import com.spartan.ui.components.PageGutter
import com.spartan.ui.components.PrimaryButton
import com.spartan.ui.components.ScreenTitle
import com.spartan.ui.components.SectionHeader
import com.spartan.ui.components.SpartanCard
import com.spartan.ui.components.Sparkline
import com.spartan.ui.components.StatusPill
import com.spartan.ui.components.Tone
import com.spartan.ui.components.toneColor
import com.spartan.ui.theme.Radius
import com.spartan.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** The signals worth watching week over week. */
private val TRENDS = listOf(MetricType.RECOVERY_SCORE, MetricType.HRV_RMSSD, MetricType.SLEEP_PERFORMANCE)

/**
 * Progress: the goal you are working toward, how this week went, where your signals are heading,
 * what your own reflections revealed, and the one thing to focus on next week.
 */
@Composable
fun ProgressScreen(
    state: MainUiState,
    onSaveGoal: (GoalType, Double, Int) -> Unit,
    onAbandonGoal: (String) -> Unit,
    onDismissGoalNotice: () -> Unit,
    onOpenMetric: (MetricType) -> Unit,
    onOpenTrainingWeek: () -> Unit,
) {
    var showGoalSheet by remember { mutableStateOf(false) }
    val review = state.review
    val range = remember {
        val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        val today = LocalDate.now()
        "${today.minusDays(6).format(fmt)} – ${today.format(fmt)}"
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 600.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = PageGutter, end = PageGutter, bottom = Spacing.section),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "title") {
                ScreenTitle(
                    stringResource(R.string.progress_title),
                    stringResource(R.string.progress_subtitle, range),
                    trailing = { if (state.whoopIsMock) SampleDataChip() },
                )
            }
            if (state.profile == null && !state.syncFailed) {
                item(key = "loading") { TabLoadingSkeleton() }
                return@LazyColumn
            }

            state.goalNotice?.let { notice ->
                item(key = "goal-notice") {
                    Surface(shape = RoundedCornerShape(Radius.card), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.lg)) {
                            Text(notice, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            TextButton(onClick = onDismissGoalNotice) { Text(stringResource(R.string.connections_import_dismiss)) }
                        }
                    }
                }
            }
            item(key = "goal") { GoalCard(state, onSetGoal = { showGoalSheet = true }, onAbandon = onAbandonGoal) }

            item(key = "week-header") { SectionHeader(stringResource(R.string.progress_this_week)) }
            item(key = "stats") {
                val tiles = buildList {
                    add(stringResource(R.string.progress_active_days) to stringResource(R.string.progress_of_seven, state.consistencyDays7))
                    weekAverage(state, MetricType.RECOVERY_SCORE)?.let { add(stringResource(R.string.progress_avg_recovery) to "$it%") }
                    weekAverage(state, MetricType.SLEEP_PERFORMANCE)?.let { add(stringResource(R.string.progress_avg_sleep) to "$it%") }
                    if (review != null) add(stringResource(R.string.review_strength) to review.strengthSessions.toString())
                    else weekAverage(state, MetricType.HRV_RMSSD)?.let { add(stringResource(R.string.progress_avg_hrv) to "$it ms") }
                }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    tiles.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            pair.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            if (review != null) {
                item(key = "focus") {
                    Surface(shape = RoundedCornerShape(Radius.card), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.xl)) {
                            Text(stringResource(R.string.progress_next_week), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(review.nextWeekFocus, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(top = Spacing.xs))
                        }
                    }
                }
                if (review.fromReflections.isNotEmpty()) {
                    item(key = "reflections") {
                        SpartanCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconTile(Icons.Outlined.Psychology, MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(Spacing.lg))
                                Text(stringResource(R.string.review_reflections_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                            review.fromReflections.forEach {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.md))
                            }
                        }
                    }
                }
                item(key = "review") {
                    SpartanCard {
                        ReviewList(stringResource(R.string.review_what_improved), review.improved, Tone.POSITIVE)
                        Spacer(Modifier.height(Spacing.lg))
                        ReviewList(stringResource(R.string.review_needs_attention), review.needsAttention, Tone.CAUTION)
                    }
                }
            } else {
                item(key = "review-empty") {
                    SpartanCard {
                        Text(stringResource(R.string.progress_review_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            val trends = TRENDS.mapNotNull { metric -> state.signals.firstOrNull { it.metric == metric && it.history.size >= 2 } }
            if (trends.isNotEmpty()) {
                item(key = "trends-header") { SectionHeader(stringResource(R.string.progress_trends)) }
                items(trends, key = { "trend-" + it.metric.name }) { signal ->
                    val (status, tone) = signalStatus(signal)
                    SpartanCard(onClick = { onOpenMetric(signal.metric) }, onClickLabel = stringResource(R.string.checkin_open_metric, signal.metric.label)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(signal.metric.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(formatMetricValue(signal.latest, signal.metric), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                            }
                            StatusPill(status, tone)
                        }
                        Sparkline(signal.history, toneColor(tone), Modifier.padding(top = Spacing.md), signal.normalLow, signal.normalHigh, height = 72.dp)
                    }
                }
            }

            if (state.projections.isNotEmpty()) {
                item(key = "trajectory") { TrajectoryCard(state.projections) }
            }

            item(key = "training") {
                SectionHeader(stringResource(R.string.progress_plan_header))
                SpartanCard(contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.xs)) {
                    ListRow(
                        Icons.AutoMirrored.Outlined.EventNote,
                        stringResource(R.string.plan_title),
                        state.weeklyPlan?.focus,
                        onOpenTrainingWeek,
                        showDivider = false,
                    )
                }
            }
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
}

/** Mean of the last seven days of a signal, rounded; null when there is no data this week. */
private fun weekAverage(state: MainUiState, metric: MetricType): Int? =
    state.signals.firstOrNull { it.metric == metric }?.history?.takeLast(7)?.takeIf { it.isNotEmpty() }?.average()?.roundToInt()

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    SpartanCard(modifier, contentPadding = PaddingValues(Spacing.lg)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.xs))
    }
}

@Composable
private fun ReviewList(title: String, lines: List<String>, tone: Tone) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
    lines.forEach { line ->
        Row(Modifier.padding(top = Spacing.sm), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(toneColor(tone)))
            Spacer(Modifier.width(Spacing.md))
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** The goal you are working toward, or a clear invitation to set one. */
@Composable
internal fun GoalCard(state: MainUiState, onSetGoal: () -> Unit, onAbandon: (String) -> Unit) {
    val goal = state.activeGoal
    SpartanCard {
        if (goal == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Outlined.Flag, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Spacing.lg))
                Text(stringResource(R.string.progress_goal_empty_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(stringResource(R.string.coach_goal_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.md))
            PrimaryButton(stringResource(R.string.coach_set_goal), onSetGoal, Modifier.fillMaxWidth().padding(top = Spacing.lg))
        } else {
            Text(stringResource(R.string.coach_goal_section), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(goalTitle(goal.type, goal.targetValue), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                state.goalProgress?.let { p ->
                    StatusPill(stringResource(if (p.onTrack) R.string.coach_goal_on_track else R.string.coach_goal_behind), if (p.onTrack) Tone.POSITIVE else Tone.CAUTION)
                }
            }
            state.goalProgress?.let { p ->
                LinearProgressIndicator(
                    progress = { p.fraction.coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg).height(8.dp).clip(CircleShape).clearAndSetSemantics {},
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    drawStopIndicator = {},
                )
                Text(p.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
            }
            TextButton(onClick = { onAbandon(goal.id) }, modifier = Modifier.padding(top = Spacing.xs)) {
                Text(stringResource(R.string.coach_goal_abandon), style = MaterialTheme.typography.labelLarge)
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

/** Illustrative eight-week ranges at the current consistency — always with the disclaimer. */
@Composable
internal fun TrajectoryCard(projections: List<com.spartan.domain.engine.MetricProjection>) {
    SpartanCard {
        Text(stringResource(R.string.trajectory_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
        Text(projections.first().assumption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
        projections.forEach { projection ->
            val week8 = projection.points.last()
            val flat = week8.low == week8.high && week8.low == projection.currentValue
            Column(Modifier.fillMaxWidth().padding(top = Spacing.md)) {
                Text(projection.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (flat) "${trajectoryNumber(projection.currentValue)} ${projection.unit}".trim()
                    else "${trajectoryNumber(projection.currentValue)} → ${trajectoryNumber(week8.low)}–${trajectoryNumber(week8.high)} ${projection.unit} in 8 wks".trim(),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (flat) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(ProjectionEngine.DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.md))
    }
}

private fun trajectoryNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.getDefault(), "%.1f", value)
