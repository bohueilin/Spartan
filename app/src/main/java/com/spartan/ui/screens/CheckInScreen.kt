package com.spartan.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spartan.R
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.DailyActivity
import com.spartan.ui.theme.Motion
import com.spartan.ui.theme.Radius
import com.spartan.ui.theme.Spacing
import com.spartan.ui.theme.bandColor
import com.spartan.ui.theme.bandLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The Spartan daily check-in — the hero screen. Frontier-lab intent: one calm column, a single
 * accent, instant hierarchy, tactile check-off, and honest loading/empty states. Every interactive
 * element carries semantics for TalkBack and meets the 48dp touch-target minimum.
 */
@Composable
fun CheckInScreen(
    state: MainUiState,
    onComplete: (String) -> Unit,
    onUncomplete: (String) -> Unit,
    onSnooze: (String) -> Unit,
    onSkip: (String) -> Unit,
    onSchedule: (String) -> Unit,
    onManageConnections: () -> Unit,
) {
    // Skeleton only while a first sync is genuinely in flight — a failed sync falls through to
    // the empty state + banner instead of looping the skeleton forever.
    val loading = state.planHeadline.isBlank() && state.todayActivities.isEmpty() &&
        state.readinessBand == null && !state.syncFailed
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { Spacer(Modifier.height(Spacing.lg)) }
        item { ReadinessHeader(state) }

        if (loading) {
            item { LoadingPlan() }
        } else {
            item { PlanProgress(state) }
            if (state.syncFailed) {
                item { SafetyBanner(stringResource(R.string.checkin_sync_failed)) }
            }
            state.planSafetyBanner?.let { banner -> item { SafetyBanner(banner) } }
            if (!state.whoopConnected) {
                item { ConnectPrompt(isMock = state.whoopIsMock, onManageConnections = onManageConnections) }
            }
            item { SectionLabel(stringResource(R.string.checkin_todays_plan)) }
            if (state.todayActivities.isEmpty()) {
                item { EmptyPlan() }
            } else {
                // Priority first, then the natural order of the day (morning → evening).
                val ordered = state.todayActivities.sortedWith(
                    compareBy({ it.priority.ordinal }, { it.bestTimeOfDay.ordinal }),
                )
                items(ordered, key = { it.id }) { activity ->
                    ActivityCard(activity, onComplete, onUncomplete, onSnooze, onSkip, onSchedule)
                }
            }
        }
        item { Footer() }
    }
}

@Composable
private fun ReadinessHeader(state: MainUiState) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.common_brand),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (state.whoopIsMock) SampleDataChip()
        }
        Spacer(Modifier.height(Spacing.lg))
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.card),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(Modifier.padding(Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
                ReadinessRing(state.recoveryScore, state.readinessBand?.let(::bandLabel) ?: stringResource(R.string.checkin_readiness), state)
                Spacer(Modifier.width(Spacing.xl))
                Column {
                    Text(
                        state.readinessBand?.let(::bandLabel) ?: stringResource(R.string.checkin_readiness),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = bandColor(state.readinessBand),
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        state.planHeadline.ifBlank { stringResource(R.string.checkin_building_plan) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadinessRing(recovery: Int?, bandName: String, state: MainUiState) {
    val color = bandColor(state.readinessBand)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val a11y = stringResource(
        R.string.checkin_recovery_a11y,
        recovery?.let { stringResource(R.string.checkin_recovery_percent, it) }
            ?: stringResource(R.string.checkin_recovery_not_available),
        bandName,
    )
    Box(
        Modifier.size(88.dp).semantics(mergeDescendants = true) { contentDescription = a11y },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(88.dp)) {
            val stroke = 9.dp.toPx()
            drawArc(track, 0f, 360f, false, style = Stroke(width = stroke, cap = StrokeCap.Round))
            val sweep = ((recovery ?: 0).coerceIn(0, 100)) / 100f * 360f
            drawArc(color, -90f, sweep, false, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(recovery?.toString() ?: "--", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.checkin_recovery_caption), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlanProgress(state: MainUiState) {
    val total = state.todayActivities.size
    val done = state.todayActivities.count { it.status == ActivityStatus.DONE }
    val minutes = state.todayActivities.filter { it.status != ActivityStatus.SKIPPED }.sumOf { it.estimatedMinutes }
    val target by animateFloatAsState(if (total == 0) 0f else done.toFloat() / total, tween(Motion.medium), label = "progress")
    val progressDescription = stringResource(R.string.checkin_progress_a11y, done, total)
    Column(Modifier.semantics(mergeDescendants = true) { stateDescription = progressDescription }) {
        Row {
            Text(stringResource(R.string.checkin_progress_done, done, total), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.checkin_minutes_today, minutes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.sm))
        LinearProgressIndicator(
            progress = { target },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(5.dp)).clearAndSetSemantics {},
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun ActivityCard(
    activity: DailyActivity,
    onComplete: (String) -> Unit,
    onUncomplete: (String) -> Unit,
    onSnooze: (String) -> Unit,
    onSkip: (String) -> Unit,
    onSchedule: (String) -> Unit,
) {
    var expanded by remember(activity.id) { mutableStateOf(false) }
    var menuOpen by remember(activity.id) { mutableStateOf(false) }
    val done = activity.status == ActivityStatus.DONE
    val dimmed = done || activity.status == ActivityStatus.SKIPPED
    val borderColor = if (activity.priority == ActivityPriority.REQUIRED)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else MaterialTheme.colorScheme.outline

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        Column(
            Modifier
                .clickable(
                    onClickLabel = if (expanded) stringResource(R.string.checkin_collapse_activity, activity.title) else stringResource(R.string.checkin_expand_activity, activity.title),
                ) { expanded = !expanded }
                .padding(Spacing.md)
                .animateContentSize(tween(Motion.medium)),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                SpartanCheck(
                    done = done,
                    label = activity.title,
                    onToggle = { if (done) onUncomplete(activity.id) else onComplete(activity.id) },
                )
                Spacer(Modifier.width(Spacing.xs))
                Column(Modifier.weight(1f).padding(top = 6.dp)) {
                    Text(
                        activity.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (dimmed) TextDecoration.LineThrough else null,
                        color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        PriorityChip(activity.priority)
                        Text(
                            stringResource(R.string.checkin_minutes_time_of_day, activity.estimatedMinutes, timeOfDayLabel(activity)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusLine(activity)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.checkin_more_options, activity.title))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.checkin_snooze_hour)) }, onClick = { menuOpen = false; onSnooze(activity.id) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.checkin_skip_today)) }, onClick = { menuOpen = false; onSkip(activity.id) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.checkin_find_time)) }, onClick = { menuOpen = false; onSchedule(activity.id) })
                    }
                }
            }
            if (expanded) {
                Spacer(Modifier.height(Spacing.md))
                Label(stringResource(R.string.checkin_why_this_matters))
                Text(activity.whyItMatters, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                if (activity.instructions.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.md))
                    Label(stringResource(R.string.checkin_steps))
                    activity.instructions.forEachIndexed { i, step ->
                        Row(Modifier.padding(top = 3.dp)) {
                            Text("${i + 1}. ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(step, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                activity.safetyNote?.let {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                TextButton(onClick = { onSchedule(activity.id) }, modifier = Modifier.padding(top = Spacing.xs)) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.checkin_find_time))
                }
            }
        }
    }
}

@Composable
private fun SpartanCheck(done: Boolean, label: String, onToggle: () -> Unit) {
    val bg by animateColorAsState(if (done) MaterialTheme.colorScheme.primary else Color.Transparent, tween(Motion.fast), label = "bg")
    val border by animateColorAsState(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, tween(Motion.fast), label = "border")
    val checkAlpha by animateFloatAsState(if (done) 1f else 0f, tween(Motion.fast), label = "check")
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val completedDescription = stringResource(R.string.checkin_completed)
    val notCompletedDescription = stringResource(R.string.checkin_not_completed)
    Box(
        Modifier
            .size(48.dp)
            .toggleable(value = done, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { stateDescription = if (done) completedDescription else notCompletedDescription; contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(bg).border(2.dp, border, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = onPrimary, modifier = Modifier.size(17.dp).graphicsLayer { alpha = checkAlpha })
        }
    }
}

@Composable
private fun StatusLine(activity: DailyActivity) {
    val text = when (activity.status) {
        ActivityStatus.SNOOZED -> activity.snoozedUntilMillis?.let { stringResource(R.string.checkin_snoozed_until, clockTime(it)) } ?: stringResource(R.string.checkin_snoozed)
        ActivityStatus.SKIPPED -> stringResource(R.string.checkin_skipped_today)
        ActivityStatus.RESCHEDULED -> activity.scheduledEpochMinute?.let { stringResource(R.string.checkin_scheduled_for, clockTime(it * 60_000)) } ?: stringResource(R.string.checkin_rescheduled)
        ActivityStatus.DONE -> stringResource(R.string.checkin_completed)
        else -> null
    } ?: return
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = Spacing.xs))
}

@Composable
private fun PriorityChip(priority: ActivityPriority) {
    val (label, color) = when (priority) {
        ActivityPriority.REQUIRED -> stringResource(R.string.checkin_priority_required) to MaterialTheme.colorScheme.primary
        ActivityPriority.RECOMMENDED -> stringResource(R.string.checkin_priority_recommended) to MaterialTheme.colorScheme.secondary
        ActivityPriority.OPTIONAL -> stringResource(R.string.checkin_priority_optional) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(Radius.chip), color = color.copy(alpha = 0.14f)) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp))
    }
}

@Composable
private fun SampleDataChip() {
    Surface(shape = RoundedCornerShape(Radius.chip), color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)) {
        Text(stringResource(R.string.checkin_sample_data), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp))
    }
}

@Composable
private fun SafetyBanner(text: String) {
    Surface(shape = RoundedCornerShape(Radius.card), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(Spacing.md), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConnectPrompt(isMock: Boolean, onManageConnections: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onManageConnections), shape = RoundedCornerShape(Radius.card)) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.checkin_connect_whoop), fontWeight = FontWeight.SemiBold)
                Text(
                    if (isMock) stringResource(R.string.checkin_connect_prompt_body_sample)
                    else stringResource(R.string.checkin_connect_prompt_body_real),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            TextButton(onClick = onManageConnections) { Text(stringResource(R.string.checkin_connect)) }
        }
    }
}

@Composable
private fun LoadingPlan() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SkeletonRow(0.4f)
        repeat(3) { Skeleton(Modifier.fillMaxWidth().height(76.dp)) }
    }
}

@Composable
private fun SkeletonRow(widthFraction: Float) {
    Skeleton(Modifier.fillMaxWidth(widthFraction).height(14.dp))
}

@Composable
private fun Skeleton(modifier: Modifier) {
    val alpha by animateFloatAsState(0.9f, tween(600), label = "sk")
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f * alpha)))
}

@Composable
private fun EmptyPlan() {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.card)) {
        Text(
            stringResource(R.string.checkin_empty_plan),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(Spacing.xl),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Footer() {
    Text(
        stringResource(R.string.checkin_footer_disclaimer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Spacing.lg),
    )
}

private fun timeOfDayLabel(activity: DailyActivity): String =
    activity.bestTimeOfDay.name.lowercase().replaceFirstChar { it.uppercase() }

private val clockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private fun clockTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime().format(clockFormatter)
