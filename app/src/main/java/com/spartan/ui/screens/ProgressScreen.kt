package com.spartan.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spartan.R
import com.spartan.domain.engine.Goal
import com.spartan.domain.engine.GoalPlanning
import com.spartan.domain.engine.GoalPreset
import com.spartan.domain.engine.GoalProgress
import com.spartan.domain.engine.GoalType
import com.spartan.domain.engine.MetricProjection
import com.spartan.domain.engine.PlanConstraints
import com.spartan.domain.engine.ProjectionEngine
import com.spartan.domain.engine.RecoveryZone
import com.spartan.domain.engine.ReviewEngine
import com.spartan.domain.engine.Signal
import com.spartan.domain.engine.StreakSummary
import com.spartan.domain.engine.Streaks
import com.spartan.domain.engine.WeeklyDecision
import com.spartan.domain.model.MetricType
import com.spartan.ui.components.HeroCard
import com.spartan.ui.components.HeroTextButton
import com.spartan.ui.components.IconTile
import com.spartan.ui.components.ListRow
import com.spartan.ui.components.MeasureRing
import com.spartan.ui.components.PageGutter
import com.spartan.ui.components.PrimaryButton
import com.spartan.ui.components.ScreenTitle
import com.spartan.ui.components.SecondaryButton
import com.spartan.ui.components.SectionHeader
import com.spartan.ui.components.SpartanCard
import com.spartan.ui.components.Tone
import com.spartan.ui.components.metricIcon
import com.spartan.ui.components.toneColor
import com.spartan.ui.theme.MinTouchTarget
import com.spartan.ui.theme.Spacing
import com.spartan.ui.theme.heroColors
import com.spartan.ui.theme.metricAccent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** The signals worth a link from Progress to their detail; their charts live on Insights. */
private val TRENDS = listOf(MetricType.RECOVERY_SCORE, MetricType.HRV_RMSSD, MetricType.SLEEP_PERFORMANCE)

/**
 * Progress (spec §2): the goal hero first — the goal and its weekly ring, a paused goal waiting
 * for you, or goal setup as the screen's one primary action — then the weekly check-in when one is
 * due, this week's stats (each saying what it counts), forgiving consistency, where steady weeks
 * can take you as a shaded range, the weekly review, links to the trends, and the training week.
 * Goals are set and changed in [GoalWizardSheet]; a custom goal still opens [GoalSetupSheet].
 * Consistency is forgiving by design (research §4.4): never a big 0, never "streak lost". The full
 * disclaimer lives in You → About; the projections keep their own illustrative-estimate line.
 */
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
    val today = rememberCurrentDay()
    val range = remember(today) {
        val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
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

            item(key = "goal") {
                GoalCard(state, onOpenWizard = { openWizard(it) }, onAbandon = onAbandonGoal, onResume = onResumeGoal)
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
            state.goalNotice?.let { notice ->
                item(key = "goal-notice") {
                    SpartanCard {
                        Text(notice, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        TextButton(onClick = onDismissGoalNotice, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                            Text(stringResource(R.string.connections_import_dismiss), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // The subtitle already names the week and its dates, so the header does not say it again.
            item(key = "week-header") { SectionHeader(stringResource(R.string.progress_at_a_glance)) }
            item(key = "stats") {
                val stats = remember(state.consistencyDays7, state.signals, review, today) { weekStats(state, today) }
                WeekStatsGrid(stats)
            }
            item(key = "consistency") {
                val streak = remember(state.streakFlags, state.consistencyFlags) { consistencyStreak(state) }
                // The active days are the stat tile's and a habit goal's weekly count the hero ring's:
                // this card shows runs, never either count again.
                ProgressConsistencyCard(streak)
            }

            if (state.projections.isNotEmpty()) {
                item(key = "trajectory") { TrajectoryCard(state.projections, state.consistencyDays7, state.signals) }
            }

            item(key = "review-header") { SectionHeader(stringResource(R.string.progress_review_header)) }
            if (review != null) {
                item(key = "focus") {
                    SpartanCard {
                        Text(stringResource(R.string.progress_next_week), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(review.nextWeekFocus, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.xs))
                    }
                }
                if (review.fromReflections.isNotEmpty()) {
                    item(key = "reflections") {
                        SpartanCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconTile(Icons.Outlined.Psychology, MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(Spacing.lg))
                                Text(
                                    stringResource(R.string.review_reflections_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f).semantics { heading() },
                                )
                            }
                            review.fromReflections.forEach {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.md))
                            }
                        }
                    }
                }
                item(key = "review") {
                    SpartanCard {
                        ReviewList(stringResource(R.string.review_what_improved), review.improved) { Tone.POSITIVE }
                        Spacer(Modifier.height(Spacing.lg))
                        ReviewList(stringResource(R.string.review_needs_attention), review.needsAttention, ::needsAttentionTone)
                    }
                }
            } else {
                item(key = "review-empty") {
                    SpartanCard {
                        Text(stringResource(R.string.progress_review_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // The charts live on Insights; here each signal is one row into its detail, so Progress
            // never repeats a chart without its usual band and labels.
            val trends = TRENDS.mapNotNull { metric -> state.signals.firstOrNull { it.metric == metric && it.history.size >= 2 } }
            if (trends.isNotEmpty()) {
                item(key = "trends") {
                    SectionHeader(stringResource(R.string.progress_trends))
                    SpartanCard(contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.xs)) {
                        trends.forEachIndexed { index, signal ->
                            val facts = glossaryFacts(signal, state)
                            ListRow(
                                metricIcon(signal.metric),
                                facts.name,
                                stringResource(R.string.progress_trend_row, facts.value, facts.usualLine),
                                { onOpenMetric(signal.metric) },
                                showDivider = index < trends.lastIndex,
                                onClickLabel = stringResource(R.string.checkin_open_metric, facts.name),
                            )
                        }
                    }
                }
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
 * Today's date, re-read each minute ([DAY_CHECK_MILLIS]) while Progress stays open, so the week's
 * range and the stats keyed to it follow the clock across midnight instead of keeping the day the
 * screen opened on. [now] is the clock (a test seam).
 */
@Composable
internal fun rememberCurrentDay(now: () -> LocalDate = LocalDate::now): LocalDate {
    val clock by rememberUpdatedState(now)
    val day by produceState(initialValue = clock()) {
        while (true) {
            delay(DAY_CHECK_MILLIS)
            value = clock()
        }
    }
    return day
}

/** How often Progress re-reads the date: a new day shows within a minute of midnight. */
internal const val DAY_CHECK_MILLIS = 60_000L

// --- Pure helpers (unit-tested) ---

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

/**
 * The line under the goal ring. A habit goal's ring already states this week's count, so the line
 * is only the engine's body-signal context ("Resting HR -1.5 bpm since your goal started") — never the count a
 * second time, and nothing without that context. An outcome goal keeps the engine's summary.
 */
internal fun goalHeroNote(progress: GoalProgress): String? =
    if (progress.sessionsThisWeek != null) progress.contextNote?.replaceFirstChar { it.uppercaseChar() } else progress.summary

/** What the goal ring shows inside: a count, the sessions still to go (never a big 0), a share, or a mark. */
internal sealed interface GoalRingFace {
    data class Count(val done: Int, val target: Int) : GoalRingFace
    data class ToGo(val target: Int) : GoalRingFace
    data class Percent(val percent: Int) : GoalRingFace
    data object Mark : GoalRingFace
}

/**
 * The ring's face for [goal]: habit goals show this week's sessions against the weekly target —
 * or, before the first one, how many are to go — and outcome goals the engine's real share of the
 * target (uncapped in words, the ring itself stops full). No progress yet shows only the mark.
 */
internal fun goalRingFace(goal: Goal, progress: GoalProgress): GoalRingFace {
    weeklyRingCount(goal, progress)?.let { (done, target) ->
        return if (done > 0) GoalRingFace.Count(done, target) else GoalRingFace.ToGo(target)
    }
    val percent = (progress.fraction * 100).takeIf { it.isFinite() }?.roundToInt() ?: 0
    return if (percent > 0) GoalRingFace.Percent(percent) else GoalRingFace.Mark
}

/** The arc's share of the circle: the engine's fraction, clamped to 0–1; a corrupt value draws empty. */
internal fun goalRingFraction(progress: GoalProgress): Float =
    progress.fraction.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)?.toFloat() ?: 0f

/** The goal ring's diameter: it grows with the font scale (up to 2×) so its numeral always fits. */
internal fun goalRingSize(fontScale: Float): Dp = GOAL_RING_SIZE * fontScale.coerceIn(1f, 2f)

/**
 * True when the ring fits beside the goal's words and still leaves them [MIN_HERO_TEXT_WIDTH]
 * (scaled with the text); otherwise the ring stacks under them, so 200% text never squeezes.
 */
internal fun goalRingBeside(availableWidth: Dp, fontScale: Float): Boolean =
    availableWidth - goalRingSize(fontScale) - Spacing.lg >= MIN_HERO_TEXT_WIDTH * fontScale.coerceAtLeast(1f)

private val GOAL_RING_SIZE = 104.dp

/** The goal ring's stroke: fixed, so the ring's inner circle grows faster than its text. */
internal val GOAL_RING_STROKE = 10.dp
private val MIN_HERO_TEXT_WIDTH = 128.dp

/** What a week stat counts. */
internal enum class WeekStatKind { ACTIVE_DAYS, AVG_RECOVERY, AVG_SLEEP_PERFORMANCE, AVG_HRV, STRENGTH_SESSIONS }

/** One week stat: its real [value], and for a mean the [days] of data behind it. */
internal data class WeekStat(val kind: WeekStatKind, val value: Int, val days: Int = 0)

/** A rounded mean over the trailing week and how many days of readings it covers. */
internal data class WeekMean(val value: Int, val days: Int)

/**
 * The mean of [signal]'s readings dated in the seven days ending [today] — the week the subtitle
 * names — so a gap in wear shortens the count instead of reaching back past the week. A signal
 * without dates falls back to its last seven readings. Null when there is nothing to average.
 */
internal fun weekMean(signal: Signal?, today: LocalDate): WeekMean? {
    signal ?: return null
    val values = if (signal.dates.size == signal.history.size) {
        val window = today.minusDays(6)..today
        signal.history.filterIndexed { i, _ -> signal.dates[i] in window }
    } else {
        signal.history.takeLast(7)
    }.filter { it.isFinite() }
    if (values.isEmpty()) return null
    return WeekMean(values.average().roundToInt(), values.size)
}

/**
 * This week's stats, in order: active days, the recovery / sleep-performance / HRV means that have
 * data, and strength sessions once the weekly review exists. Every value is the user's own.
 */
internal fun weekStats(state: MainUiState, today: LocalDate): List<WeekStat> = buildList {
    add(WeekStat(WeekStatKind.ACTIVE_DAYS, state.consistencyDays7.coerceIn(0, 7)))
    fun mean(kind: WeekStatKind, metric: MetricType) {
        weekMean(state.signals.firstOrNull { it.metric == metric }, today)?.let { add(WeekStat(kind, it.value, it.days)) }
    }
    mean(WeekStatKind.AVG_RECOVERY, MetricType.RECOVERY_SCORE)
    mean(WeekStatKind.AVG_SLEEP_PERFORMANCE, MetricType.SLEEP_PERFORMANCE)
    mean(WeekStatKind.AVG_HRV, MetricType.HRV_RMSSD)
    state.review?.let { add(WeekStat(WeekStatKind.STRENGTH_SESSIONS, it.strengthSessions.coerceAtLeast(0))) }
}

/**
 * Stat tiles per row: two while each still gets [MIN_STAT_TILE_WIDTH] scaled by the text size —
 * on a 360dp phone, up to 150% text, since labels and definitions wrap — otherwise one, so at 200%
 * text the grid becomes a single column instead of clipping words.
 */
internal fun statTileColumns(availableWidth: Dp, fontScale: Float): Int =
    if ((availableWidth - Spacing.md) / 2 >= MIN_STAT_TILE_WIDTH * fontScale.coerceAtLeast(1f)) 2 else 1

private val MIN_STAT_TILE_WIDTH = 100.dp

/** One line of the Consistency card, headline first. */
internal sealed interface ConsistencyLine {
    data object BackOnTrack : ConsistencyLine
    data class CurrentRun(val days: Int) : ConsistencyLine
    data object RunStarted : ConsistencyLine
    data object StartNext : ConsistencyLine
    data class LongestRun(val days: Int) : ConsistencyLine
    data object GraceUsed : ConsistencyLine
}

/**
 * The Consistency card's lines, the forgiving runs framing only: "Back on track — no reset" first
 * when the streak engine says so; then the current run once it is named ([runNotes]), or a run just
 * started, or — with no run going — an invitation to the next one; the longest recent run when it
 * is longer; and the grace-day note. Never a 0 as the headline, and never a count of active days:
 * that is the stat tile's, as a habit goal's weekly count is the hero ring's.
 */
internal fun consistencyLines(streak: StreakSummary): List<ConsistencyLine> = buildList {
    val runs = runNotes(streak)
    if (streak.backOnTrack) add(ConsistencyLine.BackOnTrack)
    when {
        runs.current != null -> add(ConsistencyLine.CurrentRun(runs.current))
        streak.currentRun <= 0 -> add(ConsistencyLine.StartNext)
        !streak.backOnTrack -> add(ConsistencyLine.RunStarted)
    }
    runs.longest?.let { add(ConsistencyLine.LongestRun(it)) }
    if (streak.graceUsed) add(ConsistencyLine.GraceUsed)
}

/** The runs worth naming: [current] and [longest] only from two days, so a 1-day "run" never reads as failure. */
internal data class RunNotes(val current: Int?, val longest: Int?)

internal fun runNotes(streak: StreakSummary): RunNotes = RunNotes(
    current = streak.currentRun.takeIf { it >= MIN_RUN_TO_NAME },
    longest = streak.longestRun.takeIf { it >= MIN_RUN_TO_NAME && it > streak.currentRun },
)

private const val MIN_RUN_TO_NAME = 2

/**
 * A "Needs attention" line's dot: amber for a real flag, neutral for the engine's line saying
 * nothing needs attention, so the colour never signals a caution the words deny.
 */
internal fun needsAttentionTone(line: String): Tone =
    if (line == ReviewEngine.NOTHING_NEEDS_ATTENTION) Tone.NEUTRAL else Tone.CAUTION

/**
 * At or below this many active days in the last seven, the projection card leads with the lever —
 * consistency itself — rather than the narrow ranges that pace produces.
 */
internal const val LEVER_MAX_ACTIVE_DAYS = 2

internal fun consistencyIsTheLever(activeDays7: Int): Boolean = activeDays7 <= LEVER_MAX_ACTIVE_DAYS

/** True when a projection has no range to draw: every point is the current value. */
internal fun projectionIsFlat(projection: MetricProjection): Boolean =
    projection.points.size < 2 || projection.points.all { it.low == projection.currentValue && it.high == projection.currentValue }

/**
 * The value axis of a projection's band chart. Its span never shrinks to the band: it is the widest
 * of the user's usual range for the metric ([usual]'s normalLow–normalHigh, when it is that
 * metric's signal), [MIN_AXIS_FRACTION] of the current value, and [BAND_AXIS_MULTIPLE] times the
 * projected range — so the whole range from now to week 8 uses at most half the chart's height,
 * and a 1–3 ms HRV change is drawn as the small change it is, never a full-height fan. The axis is
 * centred on the projected range, so everything drawn stays inside it.
 */
internal fun projectionDomain(projection: MetricProjection, usual: Signal?): ClosedFloatingPointRange<Double> {
    val values = (projection.points.flatMap { listOf(it.low, it.high) } + projection.currentValue).filter { it.isFinite() }
    if (values.isEmpty()) return 0.0..1.0
    val low = values.min()
    val high = values.max()
    val usualWidth = usual?.takeIf { it.metric == projection.metric }?.let { signal ->
        val usualLow = signal.normalLow
        val usualHigh = signal.normalHigh
        if (usualLow != null && usualHigh != null) (usualHigh - usualLow).takeIf { it.isFinite() } else null
    } ?: 0.0
    val currentShare = abs(projection.currentValue).takeIf { it.isFinite() }?.times(MIN_AXIS_FRACTION) ?: 0.0
    val span = maxOf(usualWidth, currentShare, (high - low) * BAND_AXIS_MULTIPLE, 1.0)
    val middle = (low + high) / 2
    return (middle - span / 2)..(middle + span / 2)
}

/** The axis spans at least this share of the current value (about 7 ms for an HRV of 33 ms). */
internal const val MIN_AXIS_FRACTION = 0.20

/** The axis spans at least this many times the projected range, so the band fills half the height at most. */
internal const val BAND_AXIS_MULTIPLE = 2.0

// --- Goal hero ---

/**
 * The screen's one hero, first: the goal you are working toward with its ring (sessions against
 * the weekly target, or an outcome goal's share), on track or not in words, and Change / Retire as
 * quiet actions; a paused goal with Resume as the one primary action; or, with no goal, what a
 * focused goal does and the one primary action that opens the wizard ([onOpenWizard], pre-selected
 * on a preset when changing a goal). The ring re-settles only when its value changes.
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
    val hero = heroColors()
    HeroCard {
        when {
            goal != null -> {
                val progress = state.goalProgress
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val fontScale = LocalDensity.current.fontScale
                    val ringSize = goalRingSize(fontScale)
                    when {
                        progress == null -> Column { GoalHeroHeading(goal, null) }
                        goalRingBeside(maxWidth, fontScale) -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { GoalHeroHeading(goal, progress) }
                            Spacer(Modifier.width(Spacing.lg))
                            GoalHeroRing(goal, progress, ringSize)
                        }
                        else -> Column(Modifier.fillMaxWidth()) {
                            GoalHeroHeading(goal, progress)
                            GoalHeroRing(goal, progress, ringSize, Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.lg))
                        }
                    }
                }
                // The ring states a habit goal's count; this line adds only its body-signal context.
                progress?.let(::goalHeroNote)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = hero.contentVariant, modifier = Modifier.padding(top = Spacing.md))
                }
                FlowRow(Modifier.padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    HeroTextButton(stringResource(R.string.wizard_goal_change), { onOpenWizard(GoalPreset.forGoalType(goal.type)) })
                    HeroTextButton(stringResource(R.string.coach_goal_abandon), { onAbandon(goal.id) })
                }
            }
            paused != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PauseCircleOutline, contentDescription = null, tint = hero.contentVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.coach_goal_section), style = MaterialTheme.typography.labelMedium, color = hero.contentVariant)
                }
                Text(
                    stringResource(R.string.wizard_goal_paused, goalTitle(paused)),
                    style = MaterialTheme.typography.titleLarge,
                    color = hero.content,
                    modifier = Modifier.padding(top = Spacing.xs).semantics { heading() },
                )
                Text(stringResource(R.string.wizard_goal_paused_body), style = MaterialTheme.typography.bodyLarge, color = hero.contentVariant, modifier = Modifier.padding(top = Spacing.sm))
                PrimaryButton(stringResource(R.string.wizard_goal_resume), { onResume(paused.id) }, Modifier.fillMaxWidth().padding(top = Spacing.xl), onHero = true)
                HeroTextButton(stringResource(R.string.wizard_goal_new), { onOpenWizard(null) }, Modifier.fillMaxWidth().padding(top = Spacing.xs))
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Flag, contentDescription = null, tint = hero.contentVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.coach_goal_section), style = MaterialTheme.typography.labelMedium, color = hero.contentVariant)
                }
                Text(
                    stringResource(R.string.progress_goal_hero_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = hero.content,
                    modifier = Modifier.padding(top = Spacing.xs).semantics { heading() },
                )
                Text(stringResource(R.string.progress_goal_hero_empty_body), style = MaterialTheme.typography.bodyLarge, color = hero.contentVariant, modifier = Modifier.padding(top = Spacing.sm))
                PrimaryButton(stringResource(R.string.progress_goal_empty_title), { onOpenWizard(null) }, Modifier.fillMaxWidth().padding(top = Spacing.xl), onHero = true)
            }
        }
    }
}

/** "YOUR GOAL", the goal's name and target (the hero's heading), and on track or not in words. */
@Composable
private fun GoalHeroHeading(goal: Goal, progress: GoalProgress?) {
    val hero = heroColors()
    Text(stringResource(R.string.coach_goal_section), style = MaterialTheme.typography.labelMedium, color = hero.contentVariant)
    Text(
        goalTitle(goal),
        style = MaterialTheme.typography.titleLarge,
        color = hero.content,
        modifier = Modifier.padding(top = Spacing.xs).semantics { heading() },
    )
    progress?.let { p ->
        Surface(shape = MaterialTheme.shapes.small, color = hero.content.copy(alpha = 0.14f), contentColor = hero.content, modifier = Modifier.padding(top = Spacing.sm)) {
            Text(
                stringResource(if (p.onTrack) R.string.coach_goal_on_track else R.string.coach_goal_behind),
                style = MaterialTheme.typography.labelLarge,
                color = hero.content,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * The goal ring on the hero, [ringSize] across: a white arc on the hero track — progress, never a
 * zone — filled to the engine's real fraction ([goalRingFraction]). A habit goal's count and its
 * short caption ("of 3", "to go") sit inside the ring; an outcome goal's share sits inside and its
 * caption ("of the way there", wider than the ring's inner circle) under the ring, centred and
 * wrapping within its width. Ring and words are one TalkBack node ("2 of 3 this week"); the arc
 * itself is decorative. A changed value re-settles the arc with a no-overshoot spring; reduce-motion
 * and the calmer theme snap it (MeasureRing).
 */
@Composable
private fun GoalHeroRing(goal: Goal, progress: GoalProgress, ringSize: Dp, modifier: Modifier = Modifier) {
    val hero = heroColors()
    val face = goalRingFace(goal, progress)
    val description = when (face) {
        is GoalRingFace.Count -> stringResource(R.string.more_ring_this_week, face.done, face.target)
        is GoalRingFace.ToGo -> stringResource(R.string.progress_goal_ring_none_a11y, face.target)
        is GoalRingFace.Percent -> stringResource(R.string.progress_goal_ring_percent_a11y, face.percent)
        GoalRingFace.Mark -> null
    }
    val (numeral, caption) = when (face) {
        is GoalRingFace.Count -> face.done.toString() to stringResource(R.string.progress_goal_ring_of, face.target)
        is GoalRingFace.ToGo -> face.target.toString() to stringResource(R.string.progress_goal_ring_to_go)
        is GoalRingFace.Percent -> stringResource(R.string.progress_goal_ring_percent, face.percent) to stringResource(R.string.progress_goal_ring_percent_label)
        GoalRingFace.Mark -> null to null
    }
    val captionUnder = face is GoalRingFace.Percent
    Column(
        modifier.width(ringSize).clearAndSetSemantics { description?.let { contentDescription = it } },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MeasureRing(goalRingFraction(progress), arcColor = hero.content, trackColor = hero.track, modifier = Modifier.size(ringSize), strokeWidth = GOAL_RING_STROKE) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (numeral != null) {
                    Text(numeral, style = MaterialTheme.typography.headlineMedium, color = hero.content)
                    if (!captionUnder) caption?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = hero.contentVariant) }
                } else {
                    Icon(Icons.Outlined.Flag, contentDescription = null, tint = hero.contentVariant, modifier = Modifier.size(28.dp))
                }
            }
        }
        if (captionUnder && caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.labelMedium,
                color = hero.contentVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xs),
            )
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

/**
 * The weekly re-plan (research §4.1, §4.4): keep it, change it, or pause it. No score and no
 * streak reset — a missed week is a goal-design problem, never the user's failure. Due only with an
 * active goal, whose hero then has no primary button, so "Keep it" is the screen's one.
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
        Text(goalTitle(goal), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.md))
        summary?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
        }
        Text(stringResource(R.string.wizard_checkin_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.md))
        PrimaryButton(stringResource(R.string.wizard_checkin_keep), { onDecision(WeeklyDecision.KEEP) }, Modifier.fillMaxWidth().padding(top = Spacing.lg))
        SecondaryButton(stringResource(R.string.wizard_checkin_change), { onDecision(WeeklyDecision.CHANGE) }, Modifier.fillMaxWidth().padding(top = Spacing.sm))
        TextButton(onClick = { onDecision(WeeklyDecision.PAUSE) }, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).padding(top = Spacing.xs)) {
            Text(stringResource(R.string.wizard_checkin_pause), style = MaterialTheme.typography.labelLarge)
        }
    }
}

// --- Week stats ---

/** A week stat as it reads: label, value, and the one line that says what it counts. */
internal data class WeekStatCopy(val label: String, val value: String, val definition: String, val numeric: Boolean)

/** Words for [stat]: a forgiving word, never a lone 0, where a count is still empty. */
@Composable
internal fun weekStatCopy(stat: WeekStat): WeekStatCopy = when (stat.kind) {
    WeekStatKind.ACTIVE_DAYS -> WeekStatCopy(
        stringResource(R.string.progress_active_days),
        if (stat.value == 0) stringResource(R.string.more_active_days_starting) else stringResource(R.string.progress_of_seven, stat.value),
        stringResource(R.string.progress_active_days_def),
        numeric = stat.value > 0,
    )
    WeekStatKind.AVG_RECOVERY -> WeekStatCopy(
        stringResource(R.string.progress_avg_recovery),
        formatMetricValue(stat.value.toDouble(), MetricType.RECOVERY_SCORE),
        pluralStringResource(R.plurals.progress_avg_recovery_def, stat.days, stat.days),
        numeric = true,
    )
    WeekStatKind.AVG_SLEEP_PERFORMANCE -> WeekStatCopy(
        stringResource(R.string.progress_avg_sleep),
        formatMetricValue(stat.value.toDouble(), MetricType.SLEEP_PERFORMANCE),
        pluralStringResource(R.plurals.progress_avg_sleep_def, stat.days, stat.days),
        numeric = true,
    )
    WeekStatKind.AVG_HRV -> WeekStatCopy(
        stringResource(R.string.progress_avg_hrv),
        formatMetricValue(stat.value.toDouble(), MetricType.HRV_RMSSD),
        pluralStringResource(R.plurals.progress_avg_hrv_def, stat.days, stat.days),
        numeric = true,
    )
    WeekStatKind.STRENGTH_SESSIONS -> WeekStatCopy(
        stringResource(R.string.review_strength),
        if (stat.value == 0) stringResource(R.string.progress_strength_none) else stat.value.toString(),
        stringResource(R.string.progress_strength_def),
        numeric = stat.value > 0,
    )
}

/**
 * This week's stats as tiles, two to a row while they fit and one at large text
 * ([statTileColumns]); tiles in a row share the taller one's height.
 */
@Composable
internal fun WeekStatsGrid(stats: List<WeekStat>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = statTileColumns(maxWidth, LocalDensity.current.fontScale)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            stats.chunked(columns).forEach { row ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    row.forEach { StatTile(weekStatCopy(it), Modifier.weight(1f).fillMaxHeight()) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** One stat: what it is, its value, and what it counts — one TalkBack node. */
@Composable
private fun StatTile(copy: WeekStatCopy, modifier: Modifier = Modifier) {
    SpartanCard(modifier.semantics(mergeDescendants = true) {}, contentPadding = PaddingValues(Spacing.lg)) {
        Text(copy.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            copy.value,
            style = if (copy.numeric) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Text(copy.definition, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
    }
}

/** A review list: its heading, then each line behind a dot in that line's [tone]. */
@Composable
private fun ReviewList(title: String, lines: List<String>, tone: (String) -> Tone) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
    lines.forEach { line ->
        Row(Modifier.padding(top = Spacing.sm), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(toneColor(tone(line))))
            Spacer(Modifier.width(Spacing.md))
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// --- Consistency ---

/**
 * How steady the last two weeks have been, from [Streaks] ([consistencyLines]): "Back on track —
 * no reset" after a gap, the current and longest runs (named from two days, [runNotes]), a run just
 * started, the grace-day note, and an invitation when no run is going. The active-day count stays on
 * the stat tile and a habit goal's weekly count in the hero ring, so neither is said again here.
 */
@Composable
internal fun ProgressConsistencyCard(streak: StreakSummary) {
    val lines = consistencyLines(streak)
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
        lines.forEachIndexed { index, line ->
            val text = when (line) {
                ConsistencyLine.BackOnTrack -> stringResource(R.string.more_back_on_track)
                is ConsistencyLine.CurrentRun -> pluralStringResource(R.plurals.more_current_run, line.days, line.days)
                ConsistencyLine.RunStarted -> stringResource(R.string.progress_consistency_run_started)
                ConsistencyLine.StartNext -> stringResource(R.string.more_consistency_empty)
                is ConsistencyLine.LongestRun -> pluralStringResource(R.plurals.more_longest_run, line.days, line.days)
                ConsistencyLine.GraceUsed -> stringResource(R.string.more_grace_used)
            }
            when {
                index == 0 -> Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.md))
                line == ConsistencyLine.GraceUsed -> Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
                else -> Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = if (line is ConsistencyLine.CurrentRun) Spacing.sm else Spacing.xs),
                )
            }
            if (line == ConsistencyLine.BackOnTrack) {
                Text(stringResource(R.string.more_back_on_track_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
            }
        }
    }
}

// --- Projections ---

/**
 * "Where this can take you": each illustrative eight-week projection as a shaded range band, always
 * with [ProjectionEngine.DISCLAIMER]. When consistency is low ([consistencyIsTheLever]) the card
 * leads with the lever — consistency itself — in place of the engine's pace line (the stat tile
 * already counts the active days, once for the screen), so the narrow ranges that pace produces are
 * never presented as the payoff, nor any 1–2 ms HRV change as a goal. [signals] give each band's
 * axis the user's usual range ([projectionDomain]).
 */
@Composable
internal fun TrajectoryCard(projections: List<MetricProjection>, consistencyDays7: Int, signals: List<Signal> = emptyList()) {
    val lever = consistencyIsTheLever(consistencyDays7)
    SpartanCard {
        Text(stringResource(R.string.trajectory_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
        if (lever) {
            Text(stringResource(R.string.progress_lever_title), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
            Text(
                stringResource(if (consistencyDays7 <= 0) R.string.progress_lever_body_none else R.string.progress_lever_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        } else {
            projections.firstOrNull()?.let {
                Text(it.assumption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.md))
            }
        }
        projections.forEach { projection ->
            ProjectionBand(projection, signals.firstOrNull { it.metric == projection.metric }, lever, Modifier.padding(top = Spacing.lg))
        }
        Text(ProjectionEngine.DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.lg))
    }
}

/**
 * One projection: its name, the band from now to week 8 ([ProjectionBandCanvas]) and "Now · 66 bpm"
 * / "Week 8 · 64–65 bpm" under it, in the metric's own precision. The real value now is the
 * emphasised label; the projected end is always quieter, and smaller still while the [lever] leads.
 * TalkBack hears one sentence, never the marks. Recovery is drawn in its zone's colour, so the zone
 * is said in words beside it. A projection with no range shows only its current value.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectionBand(projection: MetricProjection, usual: Signal?, lever: Boolean, modifier: Modifier = Modifier) {
    val metric = projection.metric
    val zone = if (metric == MetricType.RECOVERY_SCORE) RecoveryZone.of(projection.currentValue.roundToInt()) else null
    val color = metricAccent(metric, zone)
    val now = formatMetricValue(projection.currentValue, metric)
    val nowLine = zone?.let { stringResource(R.string.progress_projection_now_zone, now, it.label) } ?: stringResource(R.string.progress_projection_now, now)
    val nowSpoken = zone?.let { stringResource(R.string.progress_projection_value_zone_a11y, now, it.label) } ?: now
    val flat = projectionIsFlat(projection)
    val week8 = projection.points.last()
    val high = formatMetricValue(week8.high, metric)
    val single = formatMetricValue(week8.low, metric) == high
    val summary = when {
        flat -> stringResource(R.string.progress_projection_a11y_flat, projection.label, nowSpoken)
        single -> stringResource(R.string.progress_projection_a11y_single, projection.label, nowSpoken, high)
        else -> stringResource(R.string.progress_projection_a11y, projection.label, nowSpoken, formatMetricValue(week8.low, metric), high)
    }
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = summary }) {
        Text(projection.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        if (flat) {
            Text(nowLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
        } else {
            ProjectionBandCanvas(projection, usual, color, Modifier.fillMaxWidth().padding(top = Spacing.sm).height(64.dp))
            val end = if (single) high else stringResource(R.string.progress_projection_range, formatNumber(week8.low, metric), high)
            // Wraps rather than squeezes: at large text "Week 8" drops under "Now".
            FlowRow(
                Modifier.fillMaxWidth().padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(nowLine, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    stringResource(R.string.progress_projection_week8, end),
                    style = if (lever) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The band itself, static (no draw-in): the low–high range at weeks 0–8 as a shaded fan in [color],
 * a dashed guide at the current value, and the current value marked at "Now". The value axis is
 * [projectionDomain] — at least the user's [usual] range — so a small change stays visibly small.
 */
@Composable
private fun ProjectionBandCanvas(projection: MetricProjection, usual: Signal?, color: Color, modifier: Modifier = Modifier) {
    val domain = projectionDomain(projection, usual)
    val guide = MaterialTheme.colorScheme.outline
    val dotRing = MaterialTheme.colorScheme.surface
    Canvas(modifier) {
        val points = projection.points
        val lastWeek = points.last().week.coerceAtLeast(1)
        val inset = 6.dp.toPx()
        val span = (domain.endInclusive - domain.start).takeIf { it > 0.0 } ?: 1.0
        fun x(week: Int) = inset + (size.width - inset * 2) * week / lastWeek
        fun y(value: Double) = size.height - ((value - domain.start) / span).toFloat() * size.height

        val nowY = y(projection.currentValue)
        drawLine(
            guide.copy(alpha = 0.6f),
            Offset(0f, nowY),
            Offset(size.width, nowY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        )
        val band = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.week), y(p.high)) else lineTo(x(p.week), y(p.high)) }
            points.asReversed().forEach { p -> lineTo(x(p.week), y(p.low)) }
            close()
        }
        drawPath(band, color.copy(alpha = 0.22f))
        val edge = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val highEdge = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.week), y(p.high)) else lineTo(x(p.week), y(p.high)) } }
        val lowEdge = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.week), y(p.low)) else lineTo(x(p.week), y(p.low)) } }
        drawPath(highEdge, color, style = edge)
        drawPath(lowEdge, color, style = edge)
        val now = Offset(x(points.first().week), nowY)
        drawCircle(dotRing, radius = 6.dp.toPx(), center = now)
        drawCircle(color, radius = 4.dp.toPx(), center = now)
    }
}
