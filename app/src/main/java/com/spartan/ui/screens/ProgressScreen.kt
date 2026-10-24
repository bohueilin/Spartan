package com.spartan.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spartan.R
import com.spartan.domain.engine.Goal
import com.spartan.domain.engine.GoalPlanning
import com.spartan.domain.engine.GoalPreset
import com.spartan.domain.engine.GoalProgress
import com.spartan.domain.engine.GoalType
import com.spartan.domain.engine.PlanConstraints
import com.spartan.domain.engine.ProjectionEngine
import com.spartan.domain.engine.StreakSummary
import com.spartan.domain.engine.Streaks
import com.spartan.domain.engine.WeeklyDecision
import com.spartan.domain.model.MetricType
import com.spartan.ui.components.IconTile
import com.spartan.ui.components.ListRow
import com.spartan.ui.components.PageGutter
import com.spartan.ui.components.PrimaryButton
import com.spartan.ui.components.ScreenTitle
import com.spartan.ui.components.SecondaryButton
import com.spartan.ui.components.SectionHeader
import com.spartan.ui.components.SpartanCard
import com.spartan.ui.components.Sparkline
import com.spartan.ui.components.StatusPill
import com.spartan.ui.components.Tone
import com.spartan.ui.components.WellnessFooter
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
 * Progress: the weekly check-in when one is due, the goal you are working toward, how this week
 * went, where your signals are heading, what your own reflections revealed, and the one thing to
 * focus on next week. Goals are set and changed in [GoalWizardSheet]; a custom goal still opens
 * [GoalSetupSheet]. A trends screen, so it ends with the wellness disclaimer. Consistency is
 * forgiving by design (research §4.4): never a big 0, never "streak lost".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(
    state: MainUiState,
    onSaveGoal: (GoalType, Double, Int) -> Unit,
    onAbandonGoal: (String) -> Unit,
    onDismissGoalNotice: () -> Unit,
    onOpenMetric: (MetricType) -> Unit,
    onOpenTrainingWeek: () -> Unit,
    onStartGoalPlan: (GoalPreset, Double, Int, PlanConstraints) -> Unit = { _, _, _, _ -> },
    onWeeklyDecision: (WeeklyDecision) -> Unit = {},
    onResumeGoal: (String) -> Unit = {},
) {
    var showGoalSheet by rememberSaveable { mutableStateOf(false) }
    var showWizard by rememberSaveable { mutableStateOf(false) }
    var wizardPreset by rememberSaveable { mutableStateOf<GoalPreset?>(null) }
    fun openWizard(preset: GoalPreset?) {
        wizardPreset = preset
        showWizard = true
    }
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
            val dueGoal = state.activeGoal?.takeIf { state.weeklyReviewDue }
            if (dueGoal != null) {
                item(key = "weekly-check-in") {
                    WeeklyCheckInCard(dueGoal, state.goalProgress?.summary) { decision ->
                        // "Change it" only opens the wizard: the check-in counts once a new goal starts,
                        // so backing out of the sheet keeps this card for when the user is ready.
                        if (decision == WeeklyDecision.CHANGE) openWizard(GoalPreset.forGoalType(dueGoal.type)) else onWeeklyDecision(decision)
                    }
                }
            }
            item(key = "goal") {
                GoalCard(state, onOpenWizard = { openWizard(it) }, onAbandon = onAbandonGoal, onResume = onResumeGoal)
            }

            item(key = "week-header") { SectionHeader(stringResource(R.string.progress_this_week)) }
            item(key = "stats") {
                // A quiet week reads as a start, not a big 0.
                val activeDays = if (state.consistencyDays7 == 0) stringResource(R.string.more_active_days_starting)
                else stringResource(R.string.progress_of_seven, state.consistencyDays7)
                val tiles = buildList {
                    add(stringResource(R.string.progress_active_days) to activeDays)
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
            item(key = "consistency") {
                val streak = remember(state.streakFlags, state.consistencyFlags) { consistencyStreak(state) }
                ConsistencyCard(streak)
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
                    // The same value + usual + trend as Insights; the sparkline stays decorative, so
                    // TalkBack hears the numbers as one sentence.
                    val facts = glossaryFacts(signal, state)
                    SpartanCard(onClick = { onOpenMetric(signal.metric) }, onClickLabel = stringResource(R.string.checkin_open_metric, facts.name)) {
                        Column(Modifier.clearAndSetSemantics { contentDescription = facts.summary }) {
                            Text(facts.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            // Wraps rather than squeezes: at large font sizes the pill drops under the value.
                            FlowRow(
                                Modifier.padding(top = Spacing.xs),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                Text(
                                    facts.value,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.align(Alignment.CenterVertically),
                                )
                                StatusPill(status, tone, Modifier.align(Alignment.CenterVertically))
                            }
                            GlossaryFactLines(facts)
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

            item(key = "wellness") { WellnessFooter() }
        }
    }

    if (showWizard) {
        GoalWizardSheet(
            state = state,
            initialPreset = wizardPreset,
            onStart = { preset, target, weeks, constraints ->
                onStartGoalPlan(preset, target, weeks, constraints)
                showWizard = false
            },
            onCustomGoal = {
                showWizard = false
                showGoalSheet = true
            },
            onDismiss = { showWizard = false },
        )
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

/**
 * The Consistency card's runs, read over the trailing two weeks ([MainUiState.streakFlags]) so a
 * return after a long gap still finds the earlier active days, and the grace window has the week
 * before it. Falls back to the seven-day strip until the longer window has loaded.
 */
internal fun consistencyStreak(state: MainUiState): StreakSummary =
    Streaks.summarize(state.streakFlags.ifEmpty { state.consistencyFlags })

/**
 * A habit goal's weekly ring in words: the real sessions logged this week — uncapped, so 7 of a
 * 5-session target reads "7 of 5" rather than the ring's full "5 of 5" — and the weekly target.
 * Null for goals that are not counted in sessions, or whose stored target is not a number.
 */
internal fun weeklyRingCount(goal: Goal, progress: GoalProgress): Pair<Int, Int>? {
    val sessions = progress.sessionsThisWeek ?: return null
    val target = goal.targetValue.takeIf { it.isFinite() } ?: return null
    return sessions to target.coerceAtLeast(1.0).roundToInt()
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

/**
 * How steady the last two weeks have been, from [Streaks]: the longest recent run, "back on track"
 * after a gap, and a note when the built-in grace day kept a run going. Runs only ever grow or
 * rest — no reset — and an empty window invites the next run, never a "first" one.
 */
@Composable
private fun ConsistencyCard(streak: StreakSummary) {
    SpartanCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.CalendarMonth, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.lg))
            Text(
                stringResource(R.string.more_consistency_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        Text(
            if (streak.longestRun == 0) stringResource(R.string.more_consistency_empty)
            else pluralStringResource(R.plurals.more_longest_run, streak.longestRun, streak.longestRun),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.md),
        )
        if (streak.currentRun in 1 until streak.longestRun) {
            Text(pluralStringResource(R.plurals.more_current_run, streak.currentRun, streak.currentRun), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
        }
        if (streak.backOnTrack) {
            StatusPill(stringResource(R.string.more_back_on_track), Tone.POSITIVE, Modifier.padding(top = Spacing.md))
            Text(stringResource(R.string.more_back_on_track_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
        }
        if (streak.graceUsed) {
            Text(stringResource(R.string.more_grace_used), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
        }
    }
}

/**
 * The weekly re-plan (research §4.1, §4.4): keep it, change it, or pause it. No score and no
 * streak reset — a missed week is a goal-design problem, never the user's failure.
 */
@Composable
private fun WeeklyCheckInCard(goal: Goal, summary: String?, onDecision: (WeeklyDecision) -> Unit) {
    SpartanCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.EventRepeat, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.lg))
            Text(
                stringResource(R.string.wizard_checkin_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        Text(goalTitle(goal), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.md))
        summary?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
        }
        Text(stringResource(R.string.wizard_checkin_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.md))
        PrimaryButton(stringResource(R.string.wizard_checkin_keep), { onDecision(WeeklyDecision.KEEP) }, Modifier.fillMaxWidth().padding(top = Spacing.lg))
        SecondaryButton(stringResource(R.string.wizard_checkin_change), { onDecision(WeeklyDecision.CHANGE) }, Modifier.fillMaxWidth().padding(top = Spacing.sm))
        TextButton(onClick = { onDecision(WeeklyDecision.PAUSE) }, modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
            Text(stringResource(R.string.wizard_checkin_pause), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * The goal you are working toward, a paused goal waiting for you, or a clear invitation to set
 * one. [onOpenWizard] opens the goal wizard, pre-selected on a preset when changing a goal.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GoalCard(
    state: MainUiState,
    onOpenWizard: (GoalPreset?) -> Unit,
    onAbandon: (String) -> Unit,
    onResume: (String) -> Unit,
) {
    val goal = state.activeGoal
    val paused = state.pausedGoal
    SpartanCard {
        when {
            goal != null -> {
                Text(stringResource(R.string.coach_goal_section), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Text(goalTitle(goal), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    state.goalProgress?.let { p ->
                        StatusPill(stringResource(if (p.onTrack) R.string.coach_goal_on_track else R.string.coach_goal_behind), if (p.onTrack) Tone.POSITIVE else Tone.CAUTION)
                    }
                }
                state.goalProgress?.let { p ->
                    val fraction = p.fraction.coerceIn(0.0, 1.0)
                    val ring = weeklyRingCount(goal, p)
                    if (ring != null) {
                        // Habit goals score the week (research §4.4): a decorative ring and the real count in words.
                        val (sessions, weeklyTarget) = ring
                        Row(Modifier.padding(top = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                progress = { fraction.toFloat() },
                                modifier = Modifier.size(56.dp).clearAndSetSemantics {},
                                strokeWidth = 6.dp,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                strokeCap = StrokeCap.Round,
                                gapSize = 0.dp,
                            )
                            Spacer(Modifier.width(Spacing.lg))
                            Text(
                                stringResource(R.string.more_ring_this_week, sessions, weeklyTarget),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        LinearProgressIndicator(
                            progress = { fraction.toFloat() },
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg).height(8.dp).clip(CircleShape).clearAndSetSemantics {},
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            drawStopIndicator = {},
                        )
                    }
                    // Under either: for a habit goal the summary adds what is counted and the body-signal context.
                    Text(p.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
                }
                FlowRow(Modifier.padding(top = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TextButton(onClick = { onOpenWizard(GoalPreset.forGoalType(goal.type)) }) {
                        Text(stringResource(R.string.wizard_goal_change), style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(onClick = { onAbandon(goal.id) }) {
                        Text(stringResource(R.string.coach_goal_abandon), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            paused != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Outlined.PauseCircleOutline, MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(Spacing.lg))
                    Text(
                        stringResource(R.string.wizard_goal_paused, goalTitle(paused)),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(stringResource(R.string.wizard_goal_paused_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.md))
                PrimaryButton(stringResource(R.string.wizard_goal_resume), { onResume(paused.id) }, Modifier.fillMaxWidth().padding(top = Spacing.lg))
                SecondaryButton(stringResource(R.string.wizard_goal_new), { onOpenWizard(null) }, Modifier.fillMaxWidth().padding(top = Spacing.sm))
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Outlined.Flag, MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(Spacing.lg))
                    Text(stringResource(R.string.progress_goal_empty_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(stringResource(R.string.coach_goal_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.md))
                PrimaryButton(stringResource(R.string.coach_set_goal), { onOpenWizard(null) }, Modifier.fillMaxWidth().padding(top = Spacing.lg))
            }
        }
    }
}

/**
 * "Get fitter · 3 training sessions a week": a goal's name and target, for every goal type. A sleep
 * goal shows the absolute target the wizard promised ("85% of your sleep need"), and the stored gain
 * only when its baseline is unknown.
 */
@Composable
internal fun goalTitle(goal: Goal): String {
    // roundToInt() throws on NaN; a corrupt stored target must not crash the card.
    val n = if (goal.targetValue.isNaN()) 0 else goal.targetValue.roundToInt()
    return when (goal.type) {
        GoalType.WEIGHT_LOSS -> stringResource(R.string.wizard_goal_title_weight, n)
        GoalType.SLEEP_RECOVERY -> GoalPlanning.sleepTargetPercent(goal)?.takeIf { it.isFinite() }
            ?.let { stringResource(R.string.wizard_goal_title_sleep_target, it.roundToInt()) }
            ?: stringResource(R.string.wizard_goal_title_sleep, n)
        GoalType.STRESS_RESILIENCE -> pluralStringResource(R.plurals.wizard_goal_title_stress, n, n)
        GoalType.FITNESS -> pluralStringResource(R.plurals.wizard_goal_title_fitness, n, n)
        GoalType.RECOVERY -> pluralStringResource(R.plurals.wizard_goal_title_recovery, n, n)
    }
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
