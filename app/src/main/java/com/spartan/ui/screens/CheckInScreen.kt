package com.spartan.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass
import com.spartan.R
import com.spartan.domain.engine.ActionPlaybook
import com.spartan.domain.engine.BedtimePlan
import com.spartan.domain.engine.MetricBenefits
import com.spartan.domain.engine.MorningVerdict
import com.spartan.domain.engine.PendingReason
import com.spartan.domain.engine.PlanClock
import com.spartan.domain.engine.PlanUrgency
import com.spartan.domain.engine.RuleIds
import com.spartan.domain.engine.Signal
import com.spartan.domain.engine.SignalPosition
import com.spartan.domain.engine.SleepPlanner
import com.spartan.domain.engine.StrainZone
import com.spartan.domain.engine.SuggestedAction
import com.spartan.domain.engine.TrainingProfile
import com.spartan.domain.engine.VerdictEngine
import com.spartan.domain.engine.VerdictHeadline
import com.spartan.domain.engine.VideoGuide
import com.spartan.domain.engine.VideoLibrary
import com.spartan.domain.engine.WhyToday
import com.spartan.domain.engine.formatHoursMinutes
import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.ActivityPriority
import com.spartan.domain.model.ActivityStatus
import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.MetricType
import com.spartan.ui.components.CompletionCheck
import com.spartan.ui.components.IconTile
import com.spartan.ui.components.MorningReveal
import com.spartan.ui.components.PageGutter
import com.spartan.ui.components.PrimaryButton
import com.spartan.ui.components.SpartanCard
import com.spartan.ui.components.StatusPill
import com.spartan.ui.components.Tone
import com.spartan.ui.components.WellnessFooter
import com.spartan.ui.components.categoryIcon
import com.spartan.ui.components.rememberCompletionMorph
import com.spartan.ui.components.toneColor
import com.spartan.ui.theme.Motion
import com.spartan.ui.theme.Radius
import com.spartan.ui.theme.Spacing
import com.spartan.ui.theme.rememberReducedMotion
import com.spartan.ui.theme.zoneColor
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The four signals that explain a readiness score at a glance, in reading order. */
private val DRIVERS = listOf(MetricType.HRV_RMSSD, MetricType.RESTING_HEART_RATE, MetricType.SLEEP_PERFORMANCE, MetricType.DAY_STRAIN)

/** Training activities: the user's own limits note from plan settings is shown back on these. */
private val LIMITS_NOTE_CATEGORIES = setOf(
    ActivityCategory.ZONE2, ActivityCategory.STRENGTH, ActivityCategory.MOBILITY, ActivityCategory.MOVEMENT, ActivityCategory.RECOVERY,
)

/** Level 1 shows at most this many actions (research §4.2); the rest sit behind "Show N more". */
private const val VISIBLE_ACTIONS = 5

/**
 * The ids the collapsed plan shows, in [ordered] order: the first [VISIBLE_ACTIONS], the next open
 * action wherever it sits, and every row already shown this visit ([kept]). Ids no longer in the
 * plan drop out, so a re-seeded plan starts clean.
 */
internal fun collapsedActionIds(ordered: List<DailyActivity>, nextId: String?, kept: Set<String>): Set<String> =
    ordered.filterIndexed { index, activity -> index < VISIBLE_ACTIONS || activity.id == nextId || activity.id in kept }
        .mapTo(LinkedHashSet()) { it.id }

/**
 * An action's time-of-day urgency pill. The sleep anchor ("In bed by 10:40 PM") is an evening
 * target, not a chore that slips past midday, so it never reads as Due or Overdue.
 */
private fun urgencyOf(activity: DailyActivity, nowMinuteOfDay: Int): PlanUrgency =
    if (activity.ruleId == RuleIds.SLEEP_ANCHOR) {
        PlanUrgency.NONE
    } else {
        PlanClock.urgencyFor(activity.priority, activity.status, nowMinuteOfDay)
    }

/** Sleep debt below this is noise, not worth a line of its own (the same cut-off as WhyTodayEngine). */
private const val DEBT_WORTH_MENTIONING_HOURS = 0.25

/** The single-column list's width cap — a readable measure on any screen. */
private val LIST_MAX_WIDTH = 600.dp

/** The two-pane layout's width cap on expanded windows: the plan list plus the "Why today?" pane. */
private val TWO_PANE_MAX_WIDTH = 1080.dp

/**
 * Today — the hero screen, answering one question: what should I do today? The morning verdict
 * (colour + word + number, one strain target, tonight's bedtime) or a calm "plan pending", then
 * 3–5 checkable actions with the next one dominant, an expandable "Why today?", and tonight's sleep
 * target. Every interactive element carries TalkBack semantics and a 48dp target.
 *
 * On an expanded window (840dp+, M3's breakpoint) with a Ready verdict, "Why today?" becomes a
 * supporting pane beside the plan, always open with its driver tiles; everything else keeps its
 * order in the left column. Compact and medium widths keep the single column.
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
    bedtime: BedtimePlan? = null,
    onAddAction: (SuggestedAction, MetricType) -> Unit = { _, _ -> },
    onMorningRevealPlayed: (Long) -> Unit = {},
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
    var showAllActions by rememberSaveable { mutableStateOf(false) }
    val completeWithDebrief: (String) -> Unit = { id ->
        onComplete(id)
        state.todayActivities.firstOrNull { it.id == id && it.category in DEBRIEF_CATEGORIES }?.let { debriefFor = it }
    }
    // Skeleton only while this session's first sync is genuinely in flight: the verdict is null
    // exactly until a sync settles (success, failure, or the known-empty state after erase-all).
    val loading = state.verdict == null && !state.syncFailed
    // Priority first, then the natural order of the day (morning → evening).
    val ordered = remember(state.todayActivities) {
        state.todayActivities.sortedWith(compareBy({ it.priority.ordinal }, { it.bestTimeOfDay.ordinal }))
    }
    // The first action still open is the one to do next — always on screen, even past the fold.
    val nextId = ordered.firstOrNull { it.status != ActivityStatus.DONE && it.status != ActivityStatus.SKIPPED }?.id
    // Rows the collapsed list has shown stay shown for this visit, so checking the next action past
    // the fold never pulls its row away mid-morph. Only an explicit "Show less" resets them.
    var keptActionIds by remember { mutableStateOf(emptySet<String>()) }
    val collapsedIds = collapsedActionIds(ordered, nextId, keptActionIds)
    SideEffect { if (!showAllActions && collapsedIds != keptActionIds) keptActionIds = collapsedIds }
    val reducedMotion = rememberReducedMotion()
    val verdict = state.verdict
    val drivers = remember(state.signals) { DRIVERS.mapNotNull { metric -> state.signals.firstOrNull { it.metric == metric } } }
    // A pending verdict already explains itself (and the holding plan) on its own card.
    val why = state.whyToday?.takeIf { verdict is MorningVerdict.Ready }
    val expandedWidth = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
    val whyPane = why?.takeIf { expandedWidth }
    // One list state for both layouts, so a window resize across the breakpoint keeps the scroll.
    val listState = rememberLazyListState()
    val listContent: LazyListScope.() -> Unit = {
        item(key = "header") { TodayHeader(state) }
        // The verdict is never fabricated: Ready, Pending, or (before the first sync settles) the
        // shared loading skeleton below, which stands in for the whole tab.
        when {
            verdict is MorningVerdict.Ready -> item(key = "verdict") {
                ReadyVerdictCard(verdict, state, bedtime, onOpenRecoveryExplainer, onMorningRevealPlayed)
            }
            verdict is MorningVerdict.Pending || state.syncFailed -> item(key = "verdict") { PendingVerdictCard(state) }
        }

        if (loading) {
            item(key = "loading") { TabLoadingSkeleton() }
        } else {
            // With nothing cached there is no "most recent sync" to show: the Pending card
            // already says the refresh failed, so the banner would only contradict it.
            val nothingCached = verdict is MorningVerdict.Pending && verdict.reason == PendingReason.SYNC_FAILED
            if (state.syncFailed && !nothingCached) {
                item(key = "sync-banner") { SafetyBanner(stringResource(R.string.checkin_sync_failed), animatedItem(reducedMotion)) }
            }
            if (planFinished(state)) {
                item(key = "day-complete") { DayCompleteCard(animatedItem(reducedMotion)) }
            }
            item(key = "plan-header") { PlanHeader(state) }
            if (state.todayActivities.isEmpty()) {
                item(key = "empty-plan") { EmptyPlan() }
            } else {
                val visible = if (showAllActions) ordered else ordered.filter { it.id in collapsedIds }
                items(visible, key = { it.id }) { activity ->
                    PlanRow(
                        activity = activity,
                        isNext = activity.id == nextId,
                        nowMinuteOfDay = nowMinuteOfDay,
                        calendarIsStub = state.calendarIsStub,
                        onToggle = { if (activity.status == ActivityStatus.DONE) onUncomplete(activity.id) else completeWithDebrief(activity.id) },
                        onOpen = { openActivityId = activity.id },
                        modifier = animatedItem(reducedMotion),
                    )
                }
                val extra = ordered.size - collapsedIds.size
                if (extra > 0) {
                    item(key = "show-more") {
                        ShowMoreToggle(showAllActions, extra) {
                            // "Show less" is the one explicit collapse: back to the first few + next.
                            if (showAllActions) keptActionIds = emptySet()
                            showAllActions = !showAllActions
                        }
                    }
                }
            }
            // In the two-pane layout the same content lives, expanded, in the pane beside the plan.
            if (why != null && whyPane == null) {
                item(key = "why-today") {
                    WhyTodayCard(
                        why = why,
                        drivers = drivers,
                        onOpenMetric = onOpenMetric,
                        onOpenRecoveryExplainer = onOpenRecoveryExplainer,
                    )
                }
            }
            state.planSafetyBanner?.let { banner -> item(key = "safety-banner") { SafetyBanner(banner, animatedItem(reducedMotion)) } }
            item(key = "tonight") { TonightCard(bedtime, nowMinuteOfDay, state, onAddAction) }
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
        item(key = "footer") { WellnessFooter() }
    }
    PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        if (whyPane != null) {
            Row(Modifier.align(Alignment.TopCenter).widthIn(max = TWO_PANE_MAX_WIDTH).fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    state = listState,
                    contentPadding = PaddingValues(start = PageGutter, end = Spacing.md, bottom = Spacing.section),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    content = listContent,
                )
                WhyTodayPane(
                    why = whyPane,
                    drivers = drivers,
                    onOpenMetric = onOpenMetric,
                    onOpenRecoveryExplainer = onOpenRecoveryExplainer,
                    modifier = Modifier.weight(0.7f).fillMaxHeight(),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.align(Alignment.TopCenter).widthIn(max = LIST_MAX_WIDTH).fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(start = PageGutter, end = PageGutter, bottom = Spacing.section),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                content = listContent,
            )
        }
    }

    state.todayActivities.firstOrNull { it.id == openActivityId }?.let { activity ->
        ActivitySheet(
            activity = activity,
            nowMinuteOfDay = nowMinuteOfDay,
            calendarIsStub = state.calendarIsStub,
            trainingProfile = trainingProfile,
            limitsNote = state.planConstraints.limitsNote,
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

/** Date, provenance chip, and a quiet greeting — the verdict below is the screen's one headline. */
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
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/**
 * Today's verdict: the morning reveal (once per cycle), one headline, then colour + word +
 * number in text and the day's two targets. The whole card is one TalkBack stop and opens Recovery.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadyVerdictCard(
    verdict: MorningVerdict.Ready,
    state: MainUiState,
    bedtime: BedtimePlan?,
    onOpenRecoveryExplainer: () -> Unit,
    onMorningRevealPlayed: (Long) -> Unit,
) {
    val color = zoneColor(verdict.zone)
    // From the verdict AND the plan: a pain flag or a check-in prompt makes the day gentle, whatever the band.
    val headline = stringResource(verdictHeadline(VerdictEngine.headline(verdict, state.todayActivities)))
    val zoneText = stringResource(R.string.today_zone_value, verdict.zone.label, verdict.recoveryScore)
    val comparison = verdict.previousRecoveryScore?.let { previous ->
        when {
            verdict.recoveryScore > previous -> stringResource(R.string.today_vs_up, previous)
            verdict.recoveryScore < previous -> stringResource(R.string.today_vs_down, previous)
            else -> stringResource(R.string.today_vs_same)
        }
    }
    val strainZone = verdict.strainTarget ?: state.strainTarget
    val strainChip = strainZone?.let { strainTargetLabel(it, spoken = false) }
    val clock = clockFormatter()
    val bedChip = bedtime?.let { stringResource(R.string.today_bed_by, minuteLabel(it.lightsOutMinute, clock)) }
    val freshness = freshnessParts(state)
    // In sample mode the freshness parts open with "Sample data", so TalkBack hears the provenance too.
    val description = joinParts(
        listOfNotNull(
            headline,
            stringResource(R.string.today_zone_a11y, verdict.zone.label, verdict.recoveryScore),
            comparison,
            strainZone?.let { strainTargetLabel(it, spoken = true) },
            bedChip,
        ) + freshness,
        stringResource(R.string.today_join_sentence),
    )
    SpartanCard(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        onClick = onOpenRecoveryExplainer,
        onClickLabel = stringResource(R.string.checkin_open_metric, MetricType.RECOVERY_SCORE.label),
    ) {
        if (freshness.isNotEmpty()) FreshnessLine(joinParts(freshness, stringResource(R.string.today_join_dot)))
        MorningReveal(
            recoveryScore = verdict.recoveryScore,
            ringColor = color,
            playOnce = verdict.cycleEpochDay != state.morningRevealPlayedForDay,
            onPlayed = { onMorningRevealPlayed(verdict.cycleEpochDay) },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = Spacing.sm)
                .widthIn(max = 240.dp)
                .fillMaxWidth(0.66f),
        )
        Text(
            headline,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.md).semantics { heading() },
        )
        // Colour + word + number — the dot only reinforces what the text already says.
        Row(Modifier.padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(Spacing.sm))
            Text(zoneText, style = MaterialTheme.typography.titleMedium, color = color)
        }
        comparison?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
        }
        if (strainChip != null || bedChip != null) {
            FlowRow(
                Modifier.padding(top = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                strainChip?.let { TargetChip(it) }
                bedChip?.let { TargetChip(it) }
            }
        }
    }
}

/**
 * No verdict yet: no ring, no colour, no number — what Spartan is waiting for, the cycle rule, and
 * a pointer to the holding plan below. Never a guess at the score.
 */
@Composable
private fun PendingVerdictCard(state: MainUiState) {
    val why = state.whyToday
    val freshness = freshnessParts(state)
    SpartanCard(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        if (freshness.isNotEmpty()) FreshnessLine(joinParts(freshness, stringResource(R.string.today_join_dot)))
        Row(Modifier.padding(top = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.HourglassEmpty, MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Spacing.lg))
            Text(
                stringResource(R.string.today_pending_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(
            why?.summary ?: stringResource(R.string.today_pending_fallback),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.md),
        )
        // The engine's reasons carry the cycle rule (or, with no data at all, how to get some).
        (why?.reasons ?: listOf(stringResource(R.string.today_pending_cycle_rule))).forEach { reason ->
            Text(reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
        }
        why?.conclusion?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
        }
    }
}

@Composable
private fun FreshnessLine(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A non-interactive target chip ("Strain target 14–17 · High", "Bed by 10:40 PM"). */
@Composable
private fun TargetChip(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }
}

/** The one emphasised headline for the verdict, as [VerdictEngine.headline] picked it. */
private fun verdictHeadline(headline: VerdictHeadline): Int = when (headline) {
    VerdictHeadline.BIG_DAY -> R.string.today_verdict_primed
    VerdictHeadline.STEADY -> R.string.today_verdict_balanced
    VerdictHeadline.EASIER -> R.string.today_verdict_easy
    VerdictHeadline.RECOVERY -> R.string.today_verdict_rest
    VerdictHeadline.GENTLE -> R.string.today_verdict_gentle
}

/** "Strain target 14–17 · High"; Light reads "under 10 · Light". [spoken] drops the symbols for TalkBack. */
@Composable
private fun strainTargetLabel(zone: StrainZone, spoken: Boolean): String = when (zone) {
    StrainZone.LIGHT -> stringResource(
        if (spoken) R.string.today_strain_under_a11y else R.string.today_strain_under,
        StrainZone.MODERATE.low,
        zone.label,
    )
    else -> stringResource(
        if (spoken) R.string.today_strain_range_a11y else R.string.today_strain_range,
        zone.low,
        zone.high,
        zone.label,
    )
}

/**
 * Where today's numbers come from (research §1.5.6): "Sample data" or "Imported 6:42 AM" (only a
 * WHOOP CSV import stamps a time; none is shown without one), then how far the data reaches, read
 * against the verdict so the line never contradicts the card under it: "Data through today's
 * cycle" for a Ready verdict, "Today's cycle is still in progress" while it is unscored, "Data
 * through <that day>" when stale, and a neutral "Couldn't refresh just now" when a sync failed
 * with nothing cached. Empty when nothing is known yet.
 */
@Composable
private fun freshnessParts(state: MainUiState): List<String> {
    val clock = clockFormatter()
    val today = LocalDate.now()
    val source = if (state.whoopIsMock) {
        stringResource(R.string.today_fresh_sample)
    } else {
        state.lastSyncedAtMillis?.let { millis ->
            val imported = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
            val at = if (imported.toLocalDate() == today) {
                imported.toLocalTime().format(clock)
            } else {
                imported.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
            }
            stringResource(R.string.today_fresh_imported, at)
        }
    }
    val verdict = state.verdict
    val through = if (verdict is MorningVerdict.Pending) {
        when (verdict.reason) {
            PendingReason.NO_COMPLETED_CYCLE -> stringResource(R.string.today_fresh_cycle_in_progress)
            PendingReason.STALE -> (verdict.lastCycleEpochDay ?: state.dataThroughEpochDay)?.let { day ->
                dataThroughLabel(day, today, staleOnly = true)
            }
            PendingReason.SYNC_FAILED -> stringResource(R.string.today_fresh_refresh_failed)
            PendingReason.NO_DATA -> null
        }
    } else {
        state.dataThroughEpochDay?.let { day -> dataThroughLabel(day, today, staleOnly = false) }
    }
    return listOfNotNull(source, through)
}

/**
 * "Data through today's cycle" / "… yesterday" / "… Sat, Oct 22". A stale cycle ([staleOnly]) is
 * never called today's, so it names its day even if the dates disagree.
 */
@Composable
private fun dataThroughLabel(day: Long, today: LocalDate, staleOnly: Boolean): String {
    val age = today.toEpochDay() - day
    return when {
        age <= 0L && !staleOnly -> stringResource(R.string.today_fresh_through_today)
        age == 1L -> stringResource(R.string.today_fresh_through_yesterday)
        else -> stringResource(
            R.string.today_fresh_through_date,
            LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())),
        )
    }
}

/** [parts] folded through a two-argument [format] ("%1$s · %2$s"), so separators stay in resources. */
private fun joinParts(parts: List<String>, format: String): String =
    if (parts.isEmpty()) "" else parts.reduce { joined, part -> String.format(format, joined, part) }

/** A minute of day on the device's 12/24-hour clock. */
private fun minuteLabel(minuteOfDay: Int, clock: DateTimeFormatter): String =
    LocalTime.of(minuteOfDay / 60 % 24, minuteOfDay % 60).format(clock)

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

/**
 * One plan item: what it is, how long, when, and a check. Tapping opens everything else. The next
 * action to do is the visually dominant row; a checked row morphs its card (corner + container).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanRow(
    activity: DailyActivity,
    isNext: Boolean,
    nowMinuteOfDay: Int,
    calendarIsStub: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val done = activity.status == ActivityStatus.DONE
    val dimmed = done || activity.status == ActivityStatus.SKIPPED
    val urgency = urgencyOf(activity, nowMinuteOfDay)
    val morph = rememberCompletionMorph(done)
    val colors = MaterialTheme.colorScheme
    // A done row sits on primaryContainer, so all of its content pairs with onPrimaryContainer.
    val titleColor = when {
        done -> colors.onPrimaryContainer
        dimmed -> colors.onSurfaceVariant
        else -> colors.onSurface
    }
    val metaColor = if (done) colors.onPrimaryContainer else colors.onSurfaceVariant
    val iconTint = when {
        done -> colors.onPrimaryContainer
        dimmed -> colors.onSurfaceVariant
        else -> colors.primary
    }
    val verticalPadding = if (isNext) Spacing.lg else Spacing.md
    ActionCardSurface(
        cornerRadius = morph.cornerRadius,
        containerColor = morph.containerColor,
        contentColor = if (done) colors.onPrimaryContainer else colors.onSurface,
        onClick = onOpen,
        onClickLabel = stringResource(R.string.checkin_open_activity, activity.title),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(start = Spacing.lg, top = verticalPadding, bottom = verticalPadding, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(categoryIcon(activity.category), iconTint, size = if (isNext) 52.dp else 44.dp)
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f)) {
                if (isNext) {
                    Text(stringResource(R.string.today_plan_next), style = MaterialTheme.typography.labelMedium, color = colors.primary)
                }
                Text(
                    activity.title,
                    style = if (isNext) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    textDecoration = if (dimmed) TextDecoration.LineThrough else null,
                    color = titleColor,
                )
                Text(
                    stringResource(R.string.checkin_minutes_time_of_day, activity.estimatedMinutes, timeOfDayLabel(activity)),
                    style = if (isNext) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                    color = metaColor,
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
            PlanCheck(done = done, label = activity.title, onToggle = onToggle)
        }
    }
}

/**
 * An action row's card: [SpartanCard]'s look (soft, wide shadow in light mode; hairline on a lifted
 * surface in dark mode) carrying the completion morph's corner radius and container colour.
 */
@Composable
private fun ActionCardSurface(
    cornerRadius: Dp,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    onClickLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val dark = isSystemInDarkTheme()
    val shadowTint = MaterialTheme.colorScheme.scrim
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (dark) Modifier
                else Modifier.shadow(10.dp, shape, ambientColor = shadowTint.copy(alpha = 0.06f), spotColor = shadowTint.copy(alpha = 0.10f)),
            )
            .clip(shape)
            .clickable(onClickLabel = onClickLabel, onClick = onClick),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

/**
 * The action's check: [CompletionCheck] inside a 48dp checkbox target with a haptic on completion.
 * The press ripple is round and fills the 48dp target, like M3's Checkbox, not a square highlight.
 * The animation plays only when checked while shown — never on a screen visit.
 */
@Composable
private fun PlanCheck(done: Boolean, label: String, onToggle: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val completedDescription = stringResource(R.string.checkin_completed)
    val notCompletedDescription = stringResource(R.string.checkin_not_completed)
    Box(
        Modifier
            .size(48.dp)
            .toggleable(
                value = done,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp),
                role = Role.Checkbox,
                onValueChange = {
                    if (!done) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                },
            )
            .semantics { stateDescription = if (done) completedDescription else notCompletedDescription; contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CompletionCheck(done)
    }
}

/** "Show 2 more" / "Show less" — the rest of a long plan, one 48dp tap away. */
@Composable
private fun ShowMoreToggle(expanded: Boolean, hiddenCount: Int, onToggle: () -> Unit) {
    TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(
            if (expanded) stringResource(R.string.today_plan_show_less) else stringResource(R.string.today_plan_show_more, hiddenCount),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * "Why today?", collapsed by default: the one-line summary, then on demand the observations behind
 * the plan, what the plan does about them, the four signals, and a way down to the data. The copy
 * comes from the engine — correlational and uncertain, never a cause or a diagnosis.
 */
@Composable
private fun WhyTodayCard(
    why: WhyToday,
    drivers: List<Signal>,
    onOpenMetric: (MetricType) -> Unit,
    onOpenRecoveryExplainer: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotion()
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, if (reducedMotion) snap() else tween(Motion.medium), label = "whyChevron")
    val stateText = stringResource(if (expanded) R.string.today_why_expanded else R.string.today_why_collapsed)
    SpartanCard(contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = stringResource(if (expanded) R.string.today_why_collapse else R.string.today_why_expand),
                    role = Role.Button,
                    onClick = { expanded = !expanded },
                )
                .semantics {
                    heading()
                    stateDescription = stateText
                }
                .heightIn(min = 48.dp)
                .padding(Spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.today_why_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    why.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.rotate(chevron))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (reducedMotion) EnterTransition.None else expandVertically(tween(Motion.medium)) + fadeIn(tween(Motion.medium)),
            exit = if (reducedMotion) ExitTransition.None else shrinkVertically(tween(Motion.medium)) + fadeOut(tween(Motion.medium)),
        ) {
            WhyTodayDetails(
                why = why,
                drivers = drivers,
                onOpenMetric = onOpenMetric,
                onOpenRecoveryExplainer = onOpenRecoveryExplainer,
                modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.lg),
            )
        }
    }
}

/**
 * "Why today?" as a supporting pane beside the plan on expanded windows: always open, so the
 * observations, what the plan does about them and the driver tiles sit next to the actions they
 * explain. It scrolls on its own; the copy is the same engine output as the collapsible card.
 */
@Composable
private fun WhyTodayPane(
    why: WhyToday,
    drivers: List<Signal>,
    onOpenMetric: (MetricType) -> Unit,
    onOpenRecoveryExplainer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(start = Spacing.md, end = PageGutter, top = Spacing.xxl, bottom = Spacing.section),
    ) {
        SpartanCard {
            Text(
                stringResource(R.string.today_why_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                why.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.lg),
            )
            WhyTodayDetails(why, drivers, onOpenMetric, onOpenRecoveryExplainer)
        }
    }
}

/**
 * The body of "Why today?" — the observations behind the plan, what the plan does about them, the
 * four driver tiles, and a way down to the data — shared by the collapsible card and the pane.
 */
@Composable
private fun WhyTodayDetails(
    why: WhyToday,
    drivers: List<Signal>,
    onOpenMetric: (MetricType) -> Unit,
    onOpenRecoveryExplainer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        why.reasons.forEach { reason ->
            Row(Modifier.padding(top = Spacing.md), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.padding(top = Spacing.sm).size(6.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
                )
                Spacer(Modifier.width(Spacing.md))
                Text(reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Text(
            why.conclusion,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.md),
        )
        if (drivers.isNotEmpty()) {
            Text(
                stringResource(R.string.checkin_drivers),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.lg),
            )
            Column(Modifier.padding(top = Spacing.xs)) {
                drivers.chunked(2).forEach { pair ->
                    Row {
                        pair.forEach { DriverTile(it, Modifier.weight(1f)) { onOpenMetric(it.metric) } }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        TextButton(onClick = onOpenRecoveryExplainer, modifier = Modifier.padding(top = Spacing.sm)) {
            Icon(Icons.AutoMirrored.Outlined.TrendingUp, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(R.string.today_why_data), style = MaterialTheme.typography.labelLarge)
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

/** Everything about one activity, and every way to act on it, in one sheet. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ActivitySheet(
    activity: DailyActivity,
    nowMinuteOfDay: Int,
    calendarIsStub: Boolean,
    trainingProfile: TrainingProfile,
    limitsNote: String,
    onDismiss: () -> Unit,
    onToggleDone: () -> Unit,
    onSchedule: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
    onOpenMetric: (MetricType) -> Unit,
) {
    val done = activity.status == ActivityStatus.DONE
    val urgency = urgencyOf(activity, nowMinuteOfDay)
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
            // Wraps like the plan row's pills: a long status line or a large font never squeezes the last pill.
            FlowRow(
                Modifier.padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
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
            if (activity.category in LIMITS_NOTE_CATEGORIES && limitsNote.isNotBlank()) {
                LimitsNote(limitsNote)
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

/**
 * The limits the user wrote in plan settings, shown back on training activities as they wrote
 * them — never interpreted, never turned into a training rule — with a clinician prompt.
 */
@Composable
private fun LimitsNote(note: String) {
    Surface(
        modifier = Modifier.padding(top = Spacing.lg).fillMaxWidth(),
        shape = RoundedCornerShape(Radius.tile),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.md))
            Text(
                stringResource(R.string.today_limits_note, note.trim().trimEnd('.')),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
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
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.tile)).clickable(onClick = onClick).heightIn(min = 52.dp).padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(Spacing.lg))
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
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

/**
 * Tonight's lights-out and wind-down from the user's own sleep need (or the bedtime they gave) —
 * never a fixed eight hours. Last night reads as hours got of hours needed; debt is shown alongside,
 * to pay back over a few nights. Without a target, says what Spartan needs to set one.
 */
@Composable
private fun TonightCard(plan: BedtimePlan?, nowMinuteOfDay: Int, state: MainUiState, onAddAction: (SuggestedAction, MetricType) -> Unit) {
    val clock = clockFormatter()
    val windDown = ActionPlaybook.forMetric(MetricType.SLEEP_DEBT).first()
    val title: String
    val body: String
    if (plan == null) {
        title = stringResource(R.string.today_tonight_no_target_title)
        body = stringResource(R.string.today_tonight_no_target_body)
    } else {
        val lightsOut = minuteLabel(plan.lightsOutMinute, clock)
        val windDownAt = minuteLabel(plan.windDownMinute, clock)
        // A user's own bedtime is checked first: it can also carry hours (bedtime to wake), which are
        // time in bed, not the estimated need. Otherwise the planned night is the need itself.
        val plannedSleep = plan.sleepHours
        val late = SleepPlanner.isPastLightsOut(plan, nowMinuteOfDay)
        title = if (late) stringResource(R.string.tonight_title_late) else stringResource(R.string.tonight_title, lightsOut)
        body = when {
            late -> stringResource(R.string.tonight_body_late, lightsOut)
            plan.fromUserBedtime -> stringResource(R.string.today_tonight_wind_down_user, windDownAt)
            plannedSleep != null -> stringResource(R.string.today_tonight_wind_down_need, windDownAt, formatHoursMinutes(plannedSleep))
            else -> stringResource(R.string.today_tonight_wind_down, windDownAt)
        }
    }
    val slept = state.sleptHours
    val need = state.sleepNeedHours
    val lastNight = if (slept != null && need != null) {
        stringResource(R.string.today_tonight_last_night, formatHoursMinutes(slept), formatHoursMinutes(need))
    } else {
        null
    }
    val debt = plan?.debtHours?.takeIf { it >= DEBT_WORTH_MENTIONING_HOURS }?.let {
        stringResource(R.string.today_tonight_debt, formatHoursMinutes(it))
    }
    SpartanCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.Bedtime, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.lg))
            Column {
                Text(stringResource(R.string.tonight_eyebrow), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.md),
        )
        listOfNotNull(lastNight, debt).forEach { line ->
            Text(
                line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
        HorizontalDivider(Modifier.padding(vertical = Spacing.lg), color = MaterialTheme.colorScheme.outlineVariant)
        ActionLine(windDown, ActionPlaybook.isPlanned(windDown, state.todayActivities)) { onAddAction(windDown, MetricType.SLEEP_DEBT) }
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
                // Title and meta wrap rather than ellipsize, so large font sizes keep every word.
                Text(guide.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    stringResource(R.string.checkin_video_meta, guide.channel, guide.minutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

private fun timeOfDayLabel(activity: DailyActivity): String =
    // ROOT for the ASCII enum constant; default locale only for the display titlecase.
    activity.bestTimeOfDay.name.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.getDefault()) }

/** The whole plan is resolved and at least one thing was actually done. */
private fun planFinished(state: MainUiState): Boolean =
    state.todayActivities.isNotEmpty() &&
        state.todayActivities.any { it.status == ActivityStatus.DONE } &&
        state.todayActivities.all { it.status == ActivityStatus.DONE || it.status == ActivityStatus.SKIPPED }
