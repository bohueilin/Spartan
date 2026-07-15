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
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayCircle
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spartan.R
import com.spartan.domain.engine.MetricBenefits
import com.spartan.domain.engine.PlanClock
import com.spartan.domain.engine.PlanUrgency
import com.spartan.domain.engine.VideoLibrary
import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.MetricType
import com.spartan.ui.theme.Motion
import com.spartan.ui.theme.Radius
import com.spartan.ui.theme.Spacing
import com.spartan.ui.theme.bandColor
import com.spartan.ui.theme.bandLabel
import com.spartan.ui.theme.planUrgencyColor
import java.time.Instant
import java.time.LocalTime
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
    onLogExercise: (DailyActivity, Int, Int, Boolean) -> Unit = { _, _, _, _ -> },
    onOpenRecoveryExplainer: () -> Unit = {},
    onOpenMetric: (MetricType) -> Unit = {},
) {
    // Device-local minute of day, re-read each minute so an untouched plan escalates to amber at
    // noon and red after 6pm live, without the user having to reopen the screen.
    val nowMinuteOfDay by produceState(initialValue = LocalTime.now().let { it.hour * 60 + it.minute }) {
        while (true) {
            LocalTime.now().let { value = it.hour * 60 + it.minute }
            delay(60_000)
        }
    }
    // Checking off a training activity opens a 5-second debrief (minutes/effort/pain) that feeds
    // the adaptive rules — dismissible with one tap, never required.
    var debriefFor by remember { mutableStateOf<DailyActivity?>(null) }
    val exerciseCategories = remember {
        setOf(ActivityCategory.ZONE2, ActivityCategory.STRENGTH, ActivityCategory.MOBILITY, ActivityCategory.MOVEMENT)
    }
    val completeWithDebrief: (String) -> Unit = { id ->
        onComplete(id)
        state.todayActivities.firstOrNull { it.id == id && it.category in exerciseCategories }
            ?.let { debriefFor = it }
    }
    // Skeleton only while a first sync is genuinely in flight — a failed sync falls through to
    // the empty state + banner instead of looping the skeleton forever.
    val loading = state.planHeadline.isBlank() && state.todayActivities.isEmpty() &&
        state.readinessBand == null && !state.syncFailed
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { Spacer(Modifier.height(Spacing.lg)) }
        item { ReadinessHeader(state, onOpenRecoveryExplainer) }

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
                    ActivityCard(activity, nowMinuteOfDay, completeWithDebrief, onUncomplete, onSnooze, onSkip, onSchedule, onOpenMetric)
                }
            }
        }
        item { Footer() }
    }

    debriefFor?.let { activity ->
        ExerciseDebriefSheet(
            activity = activity,
            onSave = { minutes, rpe, pain ->
                onLogExercise(activity, minutes, rpe, pain)
                debriefFor = null
            },
            onSkip = { debriefFor = null },
        )
    }
}

@Composable
private fun ReadinessHeader(state: MainUiState, onOpenRecoveryExplainer: () -> Unit = {}) {
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
        // Tapping the readiness card opens the recovery explainer — "what is this number?"
        Surface(
            Modifier.fillMaxWidth().clickable(onClick = onOpenRecoveryExplainer),
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
    nowMinuteOfDay: Int,
    onComplete: (String) -> Unit,
    onUncomplete: (String) -> Unit,
    onSnooze: (String) -> Unit,
    onSkip: (String) -> Unit,
    onSchedule: (String) -> Unit,
    onOpenMetric: (MetricType) -> Unit,
) {
    var expanded by remember(activity.id) { mutableStateOf(false) }
    var menuOpen by remember(activity.id) { mutableStateOf(false) }
    val done = activity.status == ActivityStatus.DONE
    val dimmed = done || activity.status == ActivityStatus.SKIPPED
    val urgency = PlanClock.urgencyFor(activity.priority, activity.status, nowMinuteOfDay)
    val urgencyColor = planUrgencyColor(urgency)
    val borderColor = when {
        // An incomplete plan item escalates its border as the day passes (amber, then red).
        urgencyColor != null -> urgencyColor.copy(alpha = if (urgency == PlanUrgency.OVERDUE) 0.9f else 0.6f)
        activity.priority == ActivityPriority.REQUIRED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        else -> MaterialTheme.colorScheme.outline
    }
    val borderWidth = if (urgency == PlanUrgency.OVERDUE) 2.dp else 1.dp

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor),
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
                        if (urgencyColor != null) UrgencyChip(urgency, urgencyColor)
                        Text(
                            stringResource(R.string.checkin_minutes_time_of_day, activity.estimatedMinutes, timeOfDayLabel(activity)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // What this activity improves — names the metric so "why am I doing this?" has
                    // an answer at a glance, and taps through to that metric's detail + training.
                    activity.relatedMetric?.let { metric ->
                        ImprovesChip(metric, dimmed) { onOpenMetric(metric) }
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
                activity.relatedMetric?.let { metric ->
                    MetricBenefits.forMetric(metric)?.let { benefit ->
                        Spacer(Modifier.height(Spacing.sm))
                        Label(stringResource(R.string.checkin_what_improves))
                        Text(
                            stringResource(R.string.checkin_improves_detail, metric.label, benefit),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clickable(onClickLabel = stringResource(R.string.checkin_open_metric, metric.label)) { onOpenMetric(metric) },
                        )
                    }
                }
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
                VideoLibrary.guideForActivity(activity.id)?.let { guide ->
                    val uriHandler = LocalUriHandler.current
                    TextButton(onClick = { uriHandler.openUri(guide.url) }, modifier = Modifier.padding(top = Spacing.xs)) {
                        Icon(Icons.Outlined.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            stringResource(R.string.checkin_follow_along_video, guide.title, guide.minutes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TextButton(onClick = { onSchedule(activity.id) }) {
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
private fun UrgencyChip(urgency: PlanUrgency, color: Color) {
    val label = when (urgency) {
        PlanUrgency.OVERDUE -> stringResource(R.string.checkin_urgency_overdue)
        PlanUrgency.DUE -> stringResource(R.string.checkin_urgency_due)
        PlanUrgency.NONE -> return
    }
    Surface(shape = RoundedCornerShape(Radius.chip), color = color.copy(alpha = 0.18f)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
        )
    }
}

/** "IMPROVES RECOVERY" — a tappable pill that routes to the metric's detail + training. */
@Composable
private fun ImprovesChip(metric: MetricType, dimmed: Boolean, onClick: () -> Unit) {
    val tint = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    val label = stringResource(R.string.checkin_improves_chip, metric.label)
    Surface(
        shape = RoundedCornerShape(Radius.chip),
        color = tint.copy(alpha = 0.12f),
        modifier = Modifier
            .padding(top = Spacing.xs)
            .clickable(onClickLabel = stringResource(R.string.checkin_open_metric, metric.label), onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Outlined.TrendingUp, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tint)
        }
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
