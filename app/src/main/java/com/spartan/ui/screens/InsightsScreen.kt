package com.spartan.ui.screens

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spartan.R
import com.spartan.domain.engine.ActionPlaybook
import com.spartan.domain.engine.Signal
import com.spartan.domain.engine.SignalEngine
import com.spartan.domain.engine.SignalPosition
import com.spartan.domain.engine.SuggestedAction
import com.spartan.domain.model.ClinicalStatus
import com.spartan.domain.model.DailyActivity
import com.spartan.domain.model.InsightCard
import com.spartan.domain.model.MetricAssessment
import com.spartan.domain.model.MetricType
import com.spartan.ui.components.IconTile
import com.spartan.ui.components.PageGutter
import com.spartan.ui.components.ScreenTitle
import com.spartan.ui.components.SectionHeader
import com.spartan.ui.components.SecondaryButton
import com.spartan.ui.components.SpartanCard
import com.spartan.ui.components.Sparkline
import com.spartan.ui.components.StatusPill
import com.spartan.ui.components.Tone
import com.spartan.ui.components.metricIcon
import com.spartan.ui.components.toneColor
import com.spartan.ui.theme.Spacing
import java.util.Locale
import kotlin.math.roundToInt

/** Metrics that come from the wearable rather than a lab or a tape measure. */
private val WEARABLE = SignalEngine.DAILY.toSet() + MetricType.ENERGY_KCAL + MetricType.SLEEP_DURATION

/**
 * Insights: every signal read against the user's own normal, what it means in one sentence, and
 * the single most useful thing to do about it — one tap from today's plan.
 */
@Composable
fun InsightsScreen(
    state: MainUiState,
    onOpenMetric: (MetricType) -> Unit,
    onAddAction: (SuggestedAction, MetricType) -> Unit,
    onAddLabResult: () -> Unit,
) {
    val labs = state.assessments.filter { it.reading.type !in WEARABLE }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 600.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = PageGutter, end = PageGutter, bottom = Spacing.section),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "title") {
                ScreenTitle(
                    stringResource(R.string.insights_title),
                    stringResource(R.string.insights_subtitle),
                    trailing = { if (state.whoopIsMock) SampleDataChip() },
                )
            }
            state.whoopImportInfo?.let { info -> item(key = "import") { WhoopImportBanner(info) } }

            if (state.signals.isEmpty() && state.profile == null && !state.syncFailed) {
                item(key = "loading") { TabLoadingSkeleton() }
            } else if (state.syncFailed && state.signals.isEmpty()) {
                item(key = "failed") { SafetyBanner(stringResource(R.string.checkin_sync_failed)) }
            } else {
                item(key = "signals-header") { SectionHeader(stringResource(R.string.insights_signals)) }
                items(state.signals, key = { it.metric.name }) { signal ->
                    SignalCard(signal, state.todayActivities, onOpenMetric, onAddAction)
                }
            }

            if (state.weekdayEffects.isNotEmpty()) {
                item(key = "patterns-header") { SectionHeader(stringResource(R.string.insights_patterns)) }
                items(state.weekdayEffects, key = { it.stressorDay.name }) { effect ->
                    SpartanCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Outlined.CalendarMonth, toneColor(Tone.CAUTION))
                            Spacer(Modifier.width(Spacing.lg))
                            Text(effect.insight, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            item(key = "labs-header") {
                SectionHeader(stringResource(R.string.insights_labs), action = stringResource(R.string.insights_add_result), onAction = onAddLabResult)
            }
            if (labs.isEmpty()) {
                item(key = "labs-empty") {
                    SpartanCard {
                        Text(stringResource(R.string.insights_labs_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Spacing.lg))
                        SecondaryButton(stringResource(R.string.insights_add_result), onAddLabResult, Modifier.fillMaxWidth(), icon = Icons.Outlined.Add)
                    }
                }
            } else {
                items(labs, key = { "lab-" + it.reading.type.name }) { LabRow(it, onOpenMetric) }
            }
            items(state.insights, key = { "insight-" + it.title }) { LabInsight(it) }
            item(key = "footer") {
                Text(
                    stringResource(R.string.insights_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.lg),
                )
            }
        }
    }
}

/** One signal: status against the user's normal, the trend, what it means, and what to do. */
@Composable
private fun SignalCard(
    signal: Signal,
    today: List<DailyActivity>,
    onOpenMetric: (MetricType) -> Unit,
    onAddAction: (SuggestedAction, MetricType) -> Unit,
) {
    val (status, tone) = signalStatus(signal)
    val color = toneColor(tone)
    val action = ActionPlaybook.forMetric(signal.metric).firstOrNull()
    SpartanCard(
        onClick = { onOpenMetric(signal.metric) },
        onClickLabel = stringResource(R.string.checkin_open_metric, signal.metric.label),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(metricIcon(signal.metric), color)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(signal.metric.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(normalRangeLabel(signal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.padding(top = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    formatMetricValue(signal.latest, signal.metric),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                StatusPill(status, tone, Modifier.padding(top = Spacing.xs))
            }
            Spacer(Modifier.width(Spacing.xl))
            Sparkline(signal.history, color, Modifier.weight(1f), signal.normalLow, signal.normalHigh, height = 56.dp)
        }
        Text(
            SignalEngine.meaning(signal),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.md),
        )
        if (action != null) {
            HorizontalDivider(Modifier.padding(vertical = Spacing.lg), color = MaterialTheme.colorScheme.outlineVariant)
            ActionLine(action, ActionPlaybook.isPlanned(action, today)) { onAddAction(action, signal.metric) }
        }
    }
}

/** "TRY · 5-minute breathing protocol · 5 min" with an Add button, or a calm "In today's plan". */
@Composable
internal fun ActionLine(action: SuggestedAction, planned: Boolean, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.insights_try), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(action.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.insights_minutes, action.minutes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (planned) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.insights_in_plan), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        } else {
            val addLabel = stringResource(R.string.insights_add_a11y, action.title)
            FilledTonalButton(
                onClick = onAdd,
                shape = CircleShape,
                modifier = Modifier.semantics { contentDescription = addLabel },
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.insights_add), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun LabRow(assessment: MetricAssessment, onOpenMetric: (MetricType) -> Unit) {
    val type = assessment.reading.type
    val tone = when (assessment.clinicalStatus) {
        ClinicalStatus.NORMAL -> Tone.POSITIVE
        ClinicalStatus.ABOVE_RANGE, ClinicalStatus.BELOW_RANGE -> Tone.CAUTION
        ClinicalStatus.PENDING, ClinicalStatus.UNKNOWN -> Tone.NEUTRAL
    }
    SpartanCard(onClick = { onOpenMetric(type) }, onClickLabel = stringResource(R.string.checkin_open_metric, type.label)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(metricIcon(type), toneColor(tone))
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(type.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    assessment.reading.value?.let { formatMetricValue(it, type) } ?: stringResource(R.string.common_pending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(stringResource(statusLabelRes(assessment.clinicalStatus)), tone)
        }
    }
}

/** A lab-derived insight: the headline shows, the reasoning and safe actions open on tap. */
@Composable
private fun LabInsight(card: InsightCard) {
    var open by rememberSaveable(card.title) { mutableStateOf(false) }
    SpartanCard(
        modifier = Modifier.animateContentSize(),
        onClick = { open = !open },
        onClickLabel = stringResource(if (open) R.string.insights_show_less else R.string.insights_read_more),
    ) {
        Text(card.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(card.explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
        if (open) {
            Text(card.whyItMatters, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.md))
            card.safeActions.forEach {
                Text("•  $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.xs))
            }
        } else {
            Text(stringResource(R.string.insights_read_more), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = Spacing.sm))
        }
    }
}

/** Status label and tone for a signal — shared by Today, Insights, and metric detail. */
@Composable
internal fun signalStatus(signal: Signal): Pair<String, Tone> {
    val fav = signal.favorable
    val label = stringResource(
        when {
            signal.position == SignalPosition.LEARNING -> R.string.signal_learning
            signal.position == SignalPosition.WITHIN -> R.string.signal_within
            fav == true -> R.string.signal_better
            signal.position == SignalPosition.ABOVE -> R.string.signal_higher
            else -> R.string.signal_lower
        },
    )
    val tone = when {
        signal.position == SignalPosition.LEARNING -> Tone.NEUTRAL
        signal.position == SignalPosition.WITHIN -> Tone.INFO
        fav == true -> Tone.POSITIVE
        fav == false -> Tone.CAUTION
        else -> Tone.INFO
    }
    return label to tone
}

/** "Your normal: 61–71 ms · 6 days", or how long until there is one. */
@Composable
internal fun normalRangeLabel(signal: Signal): String {
    val low = signal.normalLow
    val high = signal.normalHigh
    if (low == null || high == null) return stringResource(R.string.signal_learning_days, signal.baselineDays, SignalEngine.MIN_BASELINE_DAYS)
    val range = "${formatNumber(low, signal.metric)}–${formatMetricValue(high, signal.metric)}"
    return stringResource(R.string.signal_normal_range, range, signal.baselineDays)
}

/** Metrics that read as whole numbers; everything else keeps one decimal when it has one. */
private val WHOLE_NUMBERS = setOf(
    MetricType.RECOVERY_SCORE, MetricType.HRV_RMSSD, MetricType.RESTING_HEART_RATE, MetricType.SLEEP_PERFORMANCE,
    MetricType.ENERGY_KCAL, MetricType.FASTING_GLUCOSE, MetricType.TRIGLYCERIDES, MetricType.HDL_C, MetricType.APOB,
    MetricType.LPA, MetricType.CAC, MetricType.SYSTOLIC_BP, MetricType.DIASTOLIC_BP, MetricType.EXERCISE_MINUTES,
    MetricType.STRENGTH_SESSIONS,
)

private fun formatNumber(value: Double, metric: MetricType): String {
    if (metric in WHOLE_NUMBERS) return value.roundToInt().toString()
    val oneDecimal = String.format(Locale.getDefault(), "%.1f", value)
    return if ((value * 10).roundToInt() % 10 == 0) value.roundToInt().toString() else oneDecimal
}

/** "58 ms", "1.8 h", "42%" — how every metric value is shown. */
internal fun formatMetricValue(value: Double, metric: MetricType): String {
    val number = formatNumber(value, metric)
    return when (metric.unit) {
        "" -> number
        "%" -> "$number%"
        else -> "$number ${metric.unit}"
    }
}
