package com.spartan.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spartan.R
import com.spartan.domain.engine.ActionPlaybook
import com.spartan.domain.engine.BedtimePlan
import com.spartan.domain.engine.MetricBenefits
import com.spartan.domain.engine.PlanClock
import com.spartan.domain.engine.PlanUrgency
import com.spartan.domain.engine.Signal
import com.spartan.domain.engine.SignalPosition
import com.spartan.domain.engine.SleepPlanner
import com.spartan.domain.engine.SuggestedAction
import com.spartan.domain.engine.TrainingProfile
import com.spartan.domain.engine.VideoGuide
import com.spartan.domain.engine.VideoLibrary
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.MetricType
import com.spartan.ui.components.IconTile
import com.spartan.ui.components.PageGutter
import com.spartan.ui.components.PrimaryButton
import com.spartan.ui.components.SpartanCard
import com.spartan.ui.components.StatusPill
import com.spartan.ui.components.Tone
import com.spartan.ui.components.categoryIcon
import com.spartan.ui.components.toneColor
import com.spartan.ui.theme.Motion
import com.spartan.ui.theme.Radius
import com.spartan.ui.theme.Spacing
import com.spartan.ui.theme.bandColor
import com.spartan.ui.theme.bandLabel
import com.spartan.ui.theme.rememberReducedMotion
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** The four signals that explain a readiness score at a glance, in reading order. */
private val DRIVERS = listOf(MetricType.HRV_RMSSD, MetricType.RESTING_HEART_RATE, MetricType.SLEEP_PERFORMANCE, MetricType.DAY_STRAIN)

/**
 * Today — the hero screen. Readiness and what is driving it, today's plan as calm cards that each
 * open into everything needed to do them, and tonight's sleep target. Every interactive element
 * carries TalkBack semantics and a 48dp target.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    onRefresh: () -> Unit = {},
    onEnableReminders: () -> Unit = {},
    onDismissRemindersOffer: () -> Unit = {},
    bedtime: BedtimePlan = SleepPlanner.tonight(null, null),
    onAddAction: (SuggestedAction, MetricType) -> Unit = { _, _ -> },
) {
    // Device-local minute of day, re-read each minute so an untouched plan escalates live.
    val nowMinuteOfDay by produceState(initialValue = LocalTime.now().let { it.hour * 60 + it.minute }) {
        while (true) {
            LocalTime.now().let { value = it.hour * 60 + it.minute }
            kotlinx.coroutines.delay(60_000)
        }
    }
    val trainingProfile = remember(state.userAgeYears, state.offTargetMetrics) {
        TrainingProfile(ageYears = state.userAgeYears, offTargetMetrics = state.offTargetMetrics)
    }
    // Checking off a training activity opens a short debrief (minutes/effort/pain) for the adaptive rules.
    var debriefFor by remember { mutableStateOf<DailyActivity?>(null) }
    var openActivityId by rememberSaveable { mutableStateOf<String?>(null) }
    val completeWithDebrief: (String) -> Unit = { id ->
        onComplete(id)
        state.todayActivities.firstOrNull { it.id == id && it.category in DEBRIEF_CATEGORIES }?.let { debriefFor = it }
    }
    // Skeleton only while a first sync is genuinely in flight — a failed sync shows the banner instead.
    val loading = state.planHeadline.isBlank() && state.todayActivities.isEmpty() &&
        state.readinessBand == null && !state.syncFailed
    val reducedMotion = rememberReducedMotion()
    PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 600.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = PageGutter, end = PageGutter, bottom = Spacing.section),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "header") { TodayHeader(state) }
            item(key = "readiness") { ReadinessCard(state, onOpenRecoveryExplainer, onOpenMetric) }

            if (loading) {
                item(key = "loading") { TabLoadingSkeleton() }
            } else {
                if (state.syncFailed) {
                    item(key = "sync-banner") { SafetyBanner(stringResource(R.string.checkin_sync_failed), animatedItem(reducedMotion)) }
                }
                if (planFinished(state)) {
                    item(key = "day-complete") { DayCompleteCard(animatedItem(reducedMotion)) }
                }
                item(key = "plan-header") { PlanHeader(state) }
                if (state.todayActivities.isEmpty()) {
                    item(key = "empty-plan") { EmptyPlan() }
                } else {
                    // Priority first, then the natural order of the day (morning → evening).
                    val ordered = state.todayActivities.sortedWith(compareBy({ it.priority.ordinal }, { it.bestTimeOfDay.ordinal }))
                    items(ordered, key = { it.id }) { activity ->
                        PlanRow(
                            activity = activity,
                            nowMinuteOfDay = nowMinuteOfDay,
                            calendarIsStub = state.calendarIsStub,
                            onToggle = { if (activity.status == ActivityStatus.DONE) onUncomplete(activity.id) else completeWithDebrief(activity.id) },
                            onOpen = { openActivityId = activity.id },
                            modifier = animatedItem(reducedMotion),
                        )
                    }
                }
                state.planSafetyBanner?.let { banner -> item(key = "safety-banner") { SafetyBanner(banner, animatedItem(reducedMotion)) } }
                item(key = "tonight") { TonightCard(bedtime, nowMinuteOfDay, state.todayActivities, onAddAction) }
                if (state.consistencyFlags.any { it }) {
                    item(key = "consistency") { ConsistencyCard(state.consistencyFlags) }
                }
                if (!state.whoopConnected) {
                    item(key = "connect-prompt") { ConnectPrompt(state.whoopIsMock, onManageConnections, animatedItem(reducedMotion)) }
                }
                if (state.showRemindersOffer) {
                    item(key = "reminders-offer") { RemindersOffer(onEnableReminders, onDismissRemindersOffer, animatedItem(reducedMotion)) }
                }
            }
            item(key = "footer") { Footer() }
        }
    }

    state.todayActivities.firstOrNull { it.id == openActivityId }?.let { activity ->
        ActivitySheet(
            activity = activity,
            nowMinuteOfDay = nowMinuteOfDay,
            calendarIsStub = state.calendarIsStub,
            trainingProfile = trainingProfile,
            onDismiss = { openActivityId = null },
            onToggleDone = {
                openActivityId = null
                if (activity.status == ActivityStatus.DONE) onUncomplete(activity.id) else completeWithDebrief(activity.id)
            },
            onSchedule = { openActivityId = null; onSchedule(activity.id) },
            onSnooze = { openActivityId = null; onSnooze(activity.id) },
            onSkip = { openActivityId = null; onSkip(activity.id) },
            onOpenMetric = { openActivityId = null; onOpenMetric(it) },
        )
    }

    debriefFor?.let { activity ->
        ExerciseDebriefSheet(
            activity = activity,
            alreadyLoggedToday = debriefRepeatsLog(activity, state.todayActivities, state.workoutsLoggedToday),
            onSave = { minutes, rpe, pain ->
                onLogExercise(activity, minutes, rpe, pain)
                debriefFor = null
            },
            onSkip = { debriefFor = null },
        )
    }
}

/**
 * Placement/appearance animation for list items that insert, leave, or shift. Reduced motion drops
 * the movement; the opacity fades stay.
 */
private fun LazyItemScope.animatedItem(reducedMotion: Boolean): Modifier = Modifier.animateItem(
    fadeInSpec = tween(Motion.medium),
    placementSpec = if (reducedMotion) null else tween(Motion.medium),
    fadeOutSpec = tween(Motion.medium),
)

@Composable
private fun TodayHeader(state: MainUiState) {
    val hour = remember { LocalTime.now().hour }
    val base = stringResource(
        when {
            hour < 12 -> R.string.checkin_good_morning
            hour < 17 -> R.string.checkin_good_afternoon
            else -> R.string.checkin_good_evening
        },
    )
    val name = state.profile?.displayName?.takeIf { it.isNotBlank() && it != "You" }
    val date = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())) }
    Column(Modifier.fillMaxWidth().padding(top = Spacing.xxl, bottom = Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (state.whoopIsMock) SampleDataChip()
        }
        Text(
            if (name != null) "$base, $name" else base,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.xs).semantics { heading() },
        )
    }
}

/** Readiness, its one-line plan, and the four signals that explain it. */
@Composable
private fun ReadinessCard(state: MainUiState, onOpenRecoveryExplainer: () -> Unit, onOpenMetric: (MetricType) -> Unit) {
    val bandName = state.readinessBand?.let(::bandLabel) ?: stringResource(R.string.checkin_readiness)
    val drivers = DRIVERS.mapNotNull { metric -> state.signals.firstOrNull { it.metric == metric } }
    SpartanCard(contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.checkin_open_metric, MetricType.RECOVERY_SCORE.label), onClick = onOpenRecoveryExplainer)
                .padding(Spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReadinessRing(state.recoveryScore, bandName, state)
            Spacer(Modifier.width(Spacing.xl))
            Column {
                Text(bandName, style = MaterialTheme.typography.titleLarge, color = bandColor(state.readinessBand))
                Text(
                    state.planHeadline.ifBlank { stringResource(R.string.checkin_building_plan) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
        if (drivers.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                stringResource(R.string.checkin_drivers),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xl, top = Spacing.lg),
            )
            Column(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm)) {
                drivers.chunked(2).forEach { pair ->
                    Row {
                        pair.forEach { DriverTile(it, Modifier.weight(1f)) { onOpenMetric(it.metric) } }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DriverTile(signal: Signal, modifier: Modifier, onClick: () -> Unit) {
    val (status, tone) = signalStatus(signal)
    val color = toneColor(tone)
    val label = stringResource(
        when (signal.metric) {
            MetricType.HRV_RMSSD -> R.string.driver_hrv
            MetricType.RESTING_HEART_RATE -> R.string.driver_rhr
            MetricType.SLEEP_PERFORMANCE -> R.string.driver_sleep
            else -> R.string.driver_strain
        },
    )
    val value = formatMetricValue(signal.latest, signal.metric)
    val description = stringResource(R.string.checkin_driver_a11y, label, value, status)
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.tile))
            .clickable(onClickLabel = stringResource(R.string.checkin_open_metric, signal.metric.label), onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        // Wraps rather than truncates: at large font sizes the status is the part that matters.
        Row(verticalAlignment = Alignment.Top) {
            val arrow = when (signal.position) {
                SignalPosition.ABOVE -> Icons.Outlined.ArrowUpward
                SignalPosition.BELOW -> Icons.Outlined.ArrowDownward
                SignalPosition.WITHIN -> Icons.Outlined.Remove
                SignalPosition.LEARNING -> null
            }
            arrow?.let { Icon(it, contentDescription = null, tint = color, modifier = Modifier.padding(top = 2.dp).size(14.dp)) }
            Text(status, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
private fun ReadinessRing(recovery: Int?, bandName: String, state: MainUiState) {
    val color = bandColor(state.readinessBand)
    // Outline, not surfaceVariant: a low score must read as a partial ring, not a broken one.
    val track = MaterialTheme.colorScheme.outlineVariant
    val a11yBase = stringResource(
        R.string.checkin_recovery_a11y,
        recovery?.let { stringResource(R.string.checkin_recovery_percent, it) } ?: stringResource(R.string.checkin_recovery_not_available),
        bandName,
    )
    // TalkBack must hear the same provenance the SAMPLE DATA chip shows sighted users.
    val a11y = if (state.whoopIsMock) stringResource(R.string.checkin_recovery_a11y_sample, a11yBase) else a11yBase
    // The ring grows with the user's font scale so the score and caption never overflow it.
    val ringSize = 100.dp * LocalDensity.current.fontScale
    Box(Modifier.size(ringSize).semantics(mergeDescendants = true) { contentDescription = a11y }, contentAlignment = Alignment.Center) {
        // The arc sweeps in once per fresh reading; the number shows its final value from frame one.
        var revealed by rememberSaveable(recovery) { mutableStateOf(false) }
        val reducedMotion = rememberReducedMotion()
        val revealAnim by animateFloatAsState(if (revealed) 1f else 0f, if (reducedMotion) snap() else tween(Motion.slow), label = "ringReveal")
        LaunchedEffect(recovery) { revealed = true }
        Canvas(Modifier.size(ringSize)) {
            val stroke = 10.dp.toPx()
            drawArc(track, 0f, 360f, false, style = Stroke(width = stroke, cap = StrokeCap.Round))
            val sweep = ((recovery ?: 0).coerceIn(0, 100)) / 100f * 360f * revealAnim
            drawArc(color, -90f, sweep, false, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(recovery?.toString() ?: "--", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.checkin_recovery_caption), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlanHeader(state: MainUiState) {
    val total = state.todayActivities.size
    val done = state.todayActivities.count { it.status == ActivityStatus.DONE }
    val minutes = state.todayActivities.filter { it.status != ActivityStatus.SKIPPED }.sumOf { it.estimatedMinutes }
    val reducedMotion = rememberReducedMotion()
    val progress by animateFloatAsState(if (total == 0) 0f else done.toFloat() / total, if (reducedMotion) snap() else tween(Motion.medium), label = "progress")
    val progressDescription = stringResource(R.string.checkin_progress_a11y, done, total)
    Column(Modifier.padding(top = Spacing.section - Spacing.md, bottom = Spacing.xs)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.checkin_todays_plan),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (total > 0) {
                Text(
                    stringResource(R.string.checkin_plan_summary, done, total, minutes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { stateDescription = progressDescription },
                )
            }
        }
        if (total > 0) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md).height(6.dp).clip(CircleShape).clearAndSetSemantics {},
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
        }
    }
}

/** One plan item: what it is, how long, when, and a check. Tapping opens everything else. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanRow(
    activity: DailyActivity,
    nowMinuteOfDay: Int,
    calendarIsStub: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val done = activity.status == ActivityStatus.DONE
    val dimmed = done || activity.status == ActivityStatus.SKIPPED
    val urgency = PlanClock.urgencyFor(activity.priority, activity.status, nowMinuteOfDay)
    SpartanCard(
        modifier = modifier,
        onClick = onOpen,
        onClickLabel = stringResource(R.string.checkin_open_activity, activity.title),
        contentPadding = PaddingValues(start = Spacing.lg, top = Spacing.md, bottom = Spacing.md, end = Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(categoryIcon(activity.category), if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    activity.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (dimmed) TextDecoration.LineThrough else null,
                    color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.checkin_minutes_time_of_day, activity.estimatedMinutes, timeOfDayLabel(activity)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val statusText = statusLine(activity, calendarIsStub)
                val showRequired = activity.priority == ActivityPriority.REQUIRED && !dimmed
                if (showRequired || urgency != PlanUrgency.NONE || statusText != null) {
                    FlowRow(
                        Modifier.padding(top = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        if (showRequired) StatusPill(stringResource(R.string.checkin_priority_required_label), Tone.INFO)
                        when (urgency) {
                            PlanUrgency.DUE -> StatusPill(stringResource(R.string.checkin_urgency_due), Tone.CAUTION)
                            PlanUrgency.OVERDUE -> StatusPill(stringResource(R.string.checkin_urgency_overdue), Tone.ALERT)
                            PlanUrgency.NONE -> Unit
                        }
                        statusText?.let { StatusPill(it, Tone.NEUTRAL) }
                    }
                }
            }
            SpartanCheck(done = done, label = activity.title, onToggle = onToggle)
        }
    }
}

/** Everything about one activity, and every way to act on it, in one sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivitySheet(
    activity: DailyActivity,
    nowMinuteOfDay: Int,
    calendarIsStub: Boolean,
    trainingProfile: TrainingProfile,
    onDismiss: () -> Unit,
    onToggleDone: () -> Unit,
    onSchedule: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
    onOpenMetric: (MetricType) -> Unit,
) {
    val done = activity.status == ActivityStatus.DONE
    val urgency = PlanClock.urgencyFor(activity.priority, activity.status, nowMinuteOfDay)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = PageGutter, end = PageGutter, bottom = Spacing.xxl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(categoryIcon(activity.category), MaterialTheme.colorScheme.primary, size = 52.dp)
                Spacer(Modifier.width(Spacing.lg))
                Column(Modifier.weight(1f)) {
                    Text(activity.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
                    Text(
                        stringResource(R.string.checkin_minutes_time_of_day, activity.estimatedMinutes, timeOfDayLabel(activity)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (activity.priority == ActivityPriority.REQUIRED) StatusPill(stringResource(R.string.checkin_priority_required_label), Tone.INFO)
                when (urgency) {
                    PlanUrgency.DUE -> StatusPill(stringResource(R.string.checkin_urgency_due), Tone.CAUTION)
                    PlanUrgency.OVERDUE -> StatusPill(stringResource(R.string.checkin_urgency_overdue), Tone.ALERT)
                    PlanUrgency.NONE -> Unit
                }
                statusLine(activity, calendarIsStub)?.let { StatusPill(it, Tone.NEUTRAL) }
            }

            SheetSection(stringResource(R.string.checkin_why_this_matters_title)) {
                Text(activity.whyItMatters, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            activity.relatedMetric?.let { metric ->
                SheetSection(stringResource(R.string.checkin_what_improves_title)) {
                    MetricBenefits.forMetric(metric)?.let {
                        Text(stringResource(R.string.checkin_improves_detail, metric.label, it), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                    TextButton(onClick = { onOpenMetric(metric) }, modifier = Modifier.padding(top = Spacing.xs)) {
                        Icon(Icons.AutoMirrored.Outlined.TrendingUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.sm))
                        Text(stringResource(R.string.checkin_see_metric, metric.label), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (activity.instructions.isNotEmpty()) {
                SheetSection(stringResource(R.string.checkin_how_to)) {
                    activity.instructions.forEachIndexed { i, step ->
                        Row(Modifier.padding(vertical = Spacing.xs), verticalAlignment = Alignment.Top) {
                            Box(Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Spacer(Modifier.width(Spacing.md))
                            Text(step, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
            activity.safetyNote?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = Spacing.lg))
            }
            VideoLibrary.guideForActivity(activity.id, trainingProfile)?.let { guide ->
                Spacer(Modifier.height(Spacing.lg))
                VideoGuideCard(guide)
            }

            Spacer(Modifier.height(Spacing.xxl))
            PrimaryButton(
                stringResource(if (done) R.string.checkin_mark_not_done else R.string.checkin_mark_done),
                onToggleDone,
                Modifier.fillMaxWidth(),
                icon = if (done) null else Icons.Rounded.Check,
            )
            Spacer(Modifier.height(Spacing.sm))
            SheetAction(Icons.Outlined.CalendarMonth, stringResource(R.string.checkin_find_time), onSchedule)
            SheetAction(Icons.Outlined.Snooze, stringResource(R.string.checkin_snooze_hour), onSnooze)
            SheetAction(Icons.Outlined.SkipNext, stringResource(R.string.checkin_skip_today), onSkip)
        }
    }
}

@Composable
private fun SheetSection(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = Spacing.xxl, bottom = Spacing.sm).semantics { heading() },
    )
    content()
}

@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.tile)).clickable(onClick = onClick).heightIn(min = 52.dp).padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(Spacing.lg))
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** A 28dp round check inside a 48dp target, with checkbox semantics and a haptic on completion. */
@Composable
private fun SpartanCheck(done: Boolean, label: String, onToggle: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        if (done) 1f else 0.94f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "checkScale",
    )
    val bg by animateColorAsState(if (done) MaterialTheme.colorScheme.primary else Color.Transparent, tween(Motion.fast), label = "bg")
    val border by animateColorAsState(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, tween(Motion.fast), label = "border")
    val checkAlpha by animateFloatAsState(if (done) 1f else 0f, tween(Motion.fast), label = "check")
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val completedDescription = stringResource(R.string.checkin_completed)
    val notCompletedDescription = stringResource(R.string.checkin_not_completed)
    Box(
        Modifier
            .size(48.dp)
            .toggleable(value = done, role = Role.Checkbox, onValueChange = {
                if (!done) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            })
            .semantics { stateDescription = if (done) completedDescription else notCompletedDescription; contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(28.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape).background(bg).border(2.dp, border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = onPrimary, modifier = Modifier.size(18.dp).graphicsLayer { alpha = checkAlpha })
        }
    }
}

/** "Snoozed until 3:15 PM", "Scheduled for 5:30 PM (sample calendar)", … or null when nothing to say. */
@Composable
private fun statusLine(activity: DailyActivity, calendarIsStub: Boolean): String? {
    val clock = clockFormatter()
    fun time(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(clock)
    return when (activity.status) {
        ActivityStatus.SNOOZED -> activity.snoozedUntilMillis?.let { stringResource(R.string.checkin_snoozed_until, time(it)) } ?: stringResource(R.string.checkin_snoozed)
        ActivityStatus.SKIPPED -> stringResource(R.string.checkin_skipped_today)
        // A slot found against stub free/busy must say so — real-feeling artifacts from fake
        // availability are exactly what the provenance system exists to prevent.
        ActivityStatus.RESCHEDULED -> activity.scheduledEpochMinute?.let {
            if (calendarIsStub) stringResource(R.string.checkin_scheduled_for_sample, time(it * 60_000))
            else stringResource(R.string.checkin_scheduled_for, time(it * 60_000))
        } ?: stringResource(R.string.checkin_rescheduled)
        else -> null
    }
}

/** Clock times follow the device's 12/24-hour setting rather than assuming "h:mm a". */
@Composable
internal fun clockFormatter(): DateTimeFormatter {
    val context = LocalContext.current
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return remember(pattern) { DateTimeFormatter.ofPattern(pattern, Locale.getDefault()) }
}

/** Tonight's lights-out target, and a one-tap wind-down for the plan. */
@Composable
private fun TonightCard(plan: BedtimePlan, nowMinuteOfDay: Int, today: List<DailyActivity>, onAddAction: (SuggestedAction, MetricType) -> Unit) {
    val clock = clockFormatter()
    fun time(minute: Int) = LocalTime.of(minute / 60, minute % 60).format(clock)
    val windDown = ActionPlaybook.forMetric(MetricType.SLEEP_DEBT).first()
    val late = SleepPlanner.isPastLightsOut(plan, nowMinuteOfDay)
    val hours = plan.sleepHours.toInt()
    val minutes = ((plan.sleepHours - hours) * 60).roundToInt()
    val sleep = if (minutes == 0) stringResource(R.string.tonight_duration_hours, hours) else stringResource(R.string.tonight_duration, hours, minutes)
    SpartanCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.Bedtime, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.lg))
            Column {
                Text(stringResource(R.string.tonight_eyebrow), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    if (late) stringResource(R.string.tonight_title_late) else stringResource(R.string.tonight_title, time(plan.lightsOutMinute)),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        Text(
            if (late) {
                stringResource(R.string.tonight_body_late, time(plan.lightsOutMinute))
            } else if (plan.debtHours >= 0.25) {
                stringResource(R.string.tonight_body_debt, time(plan.windDownMinute), sleep, String.format(Locale.getDefault(), "%.1f", plan.debtHours))
            } else {
                stringResource(R.string.tonight_body, time(plan.windDownMinute), sleep)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.md),
        )
        HorizontalDivider(Modifier.padding(vertical = Spacing.lg), color = MaterialTheme.colorScheme.outlineVariant)
        ActionLine(windDown, ActionPlaybook.isPlanned(windDown, today)) { onAddAction(windDown, MetricType.SLEEP_DEBT) }
    }
}

/** Seven calm dots, oldest → today. Consistency shown, never a streak to lose. */
@Composable
private fun ConsistencyCard(flags: List<Boolean>) {
    val activeDays = flags.count { it }
    val description = stringResource(R.string.checkin_consistency_a11y, activeDays)
    SpartanCard(modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.checkin_consistency_label), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.checkin_consistency_a11y, activeDays), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                flags.forEachIndexed { index, active ->
                    Box(
                        Modifier
                            .size(if (index == flags.lastIndex) 12.dp else 10.dp)
                            .clip(CircleShape)
                            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                }
            }
        }
    }
}

/**
 * A follow-along session, presented as media rather than a link. The tile is drawn — Spartan makes
 * no network calls, so there is no remote artwork, and a generated tile is honest about that.
 */
@Composable
private fun VideoGuideCard(guide: VideoGuide) {
    val uriHandler = LocalUriHandler.current
    val accent = MaterialTheme.colorScheme.primary
    val a11y = stringResource(R.string.checkin_video_a11y, guide.title, guide.channel, guide.minutes)
    Surface(
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .clickable(onClickLabel = stringResource(R.string.checkin_video_opens_youtube)) { uriHandler.openUri(guide.url) }
            .semantics(mergeDescendants = true) { contentDescription = a11y },
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(Radius.tile))
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.36f), accent.copy(alpha = 0.10f)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.PlayCircle, contentDescription = null, tint = accent, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.checkin_video_eyebrow), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(guide.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.checkin_video_meta, guide.channel, guide.minutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ConnectPrompt(isMock: Boolean, onManageConnections: () -> Unit, modifier: Modifier = Modifier) {
    SpartanCard(modifier = modifier, onClick = onManageConnections) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.Link, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.checkin_connect_whoop), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    stringResource(if (isMock) R.string.checkin_connect_prompt_body_sample else R.string.checkin_connect_prompt_body_real),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The reminders offer, shown only once a plan is on screen — value before the ask — and never
 * re-asked once declined.
 */
@Composable
private fun RemindersOffer(onEnable: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    SpartanCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.NotificationsActive, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.lg))
            Text(stringResource(R.string.checkin_reminders_offer_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            stringResource(R.string.checkin_reminders_offer_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Row(Modifier.fillMaxWidth().padding(top = Spacing.md), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.checkin_reminders_offer_dismiss), style = MaterialTheme.typography.labelLarge) }
            Spacer(Modifier.width(Spacing.sm))
            FilledTonalButton(onClick = onEnable, shape = CircleShape) { Text(stringResource(R.string.checkin_reminders_offer_enable), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun EmptyPlan() {
    SpartanCard {
        Text(stringResource(R.string.checkin_empty_plan), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** The calm completion moment — an ending, not a firework. */
@Composable
private fun DayCompleteCard(modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.card), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.width(Spacing.lg))
            Column {
                Text(stringResource(R.string.checkin_day_complete), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(stringResource(R.string.checkin_day_complete_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun Footer() {
    Text(
        stringResource(R.string.checkin_footer_disclaimer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xl),
    )
}

private fun timeOfDayLabel(activity: DailyActivity): String =
    // ROOT for the ASCII enum constant; default locale only for the display titlecase.
    activity.bestTimeOfDay.name.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.getDefault()) }

/** The whole plan is resolved and at least one thing was actually done. */
private fun planFinished(state: MainUiState): Boolean =
    state.todayActivities.isNotEmpty() &&
        state.todayActivities.any { it.status == ActivityStatus.DONE } &&
        state.todayActivities.all { it.status == ActivityStatus.DONE || it.status == ActivityStatus.SKIPPED }
