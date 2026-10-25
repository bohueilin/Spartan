package com.spartan.ui.screens

import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spartan.R
import com.spartan.domain.engine.ActionPlaybook
import com.spartan.domain.engine.MetricGlossary
import com.spartan.domain.engine.RecoveryZone
import com.spartan.domain.engine.Signal
import com.spartan.domain.engine.SignalEngine
import com.spartan.domain.engine.SignalPosition
import com.spartan.domain.engine.SleepNeed
import com.spartan.domain.engine.StrainZone
import com.spartan.domain.engine.SuggestedAction
import com.spartan.domain.engine.Trend
import com.spartan.domain.engine.formatHoursMinutes
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
import com.spartan.ui.components.WellnessFooter
import com.spartan.ui.components.metricIcon
import com.spartan.ui.components.toneColor
import com.spartan.ui.theme.Spacing
import com.spartan.ui.theme.rememberReducedMotion
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** Metrics that come from the wearable rather than a lab or a tape measure. */
private val WEARABLE = SignalEngine.DAILY.toSet() + MetricType.ENERGY_KCAL + MetricType.SLEEP_DURATION

/**
 * Days the glossary trend looks back over ("rising over 7 days"; fewer when the readings cover
 * fewer days), and readings the card's sparkline shows.
 */
private const val TREND_DAYS = 7

/** A previous reading within this many days of today is named by its weekday ("on Tue"), else by its date. */
private const val WEEKDAY_NAME_DAYS = 6L

/**
 * Metrics whose population reference is shown, second and labelled. Never HRV (there is no
 * population "good" HRV), and strain's zone scale is not a population range.
 */
private val POPULATION_NOTE_METRICS = setOf(MetricType.RESTING_HEART_RATE, MetricType.RESPIRATORY_RATE)

/**
 * Insights: every signal in one human sentence first, then its number against the user's own
 * usual and trend, and the single most useful thing to do about it — one tap from today's plan.
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
                    SignalCard(signal, state, onOpenMetric, onAddAction)
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
            // Index keys: titles repeat (systolic and diastolic once both said "Blood pressure").
            items(state.insights) { LabInsight(it) }
            item(key = "footer") {
                Text(
                    stringResource(R.string.insights_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.lg),
                )
                WellnessFooter()
            }
        }
    }
}

/**
 * One signal, human language before numbers (research §1.5.4): the glossary name and its one
 * sentence; the value and status; the user's usual, the trend and metric-specific context; the
 * week's sparkline; then what it means today and what to do. TalkBack hears the numbers as one
 * summary sentence — the sparkline stays decorative.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignalCard(
    signal: Signal,
    state: MainUiState,
    onOpenMetric: (MetricType) -> Unit,
    onAddAction: (SuggestedAction, MetricType) -> Unit,
) {
    val (status, tone) = signalStatus(signal)
    val color = toneColor(tone)
    val facts = glossaryFacts(signal, state)
    val action = ActionPlaybook.forMetric(signal.metric).firstOrNull()
    SpartanCard(
        onClick = { onOpenMetric(signal.metric) },
        onClickLabel = stringResource(R.string.checkin_open_metric, facts.name),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(metricIcon(signal.metric), color)
            Spacer(Modifier.width(Spacing.md))
            Text(facts.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        }
        facts.sentence?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.md))
        }
        Column(Modifier.padding(top = Spacing.lg).clearAndSetSemantics { contentDescription = facts.summary }) {
            // Wraps rather than squeezes: at large font sizes the pill drops under the value.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    facts.value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                StatusPill(status, tone, Modifier.align(Alignment.CenterVertically))
            }
            GlossaryFactLines(facts)
        }
        facts.populationNote?.let { GlossaryPopulationNote(it, Modifier.padding(top = Spacing.md)) }
        Sparkline(
            signal.history.takeLast(TREND_DAYS),
            color,
            Modifier.padding(top = Spacing.md),
            signal.normalLow,
            signal.normalHigh,
            height = 56.dp,
        )
        Text(
            SignalEngine.meaning(signal),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.md),
        )
        if (action != null) {
            HorizontalDivider(Modifier.padding(vertical = Spacing.lg), color = MaterialTheme.colorScheme.outlineVariant)
            ActionLine(action, ActionPlaybook.isPlanned(action, state.todayActivities)) { onAddAction(action, signal.metric) }
        }
    }
}

/**
 * What a coached metric's number is always shown with (research §3.1–3.3), ready to render:
 * [usualLine] is the user's usual plus the trend, [contextLine] the metric-specific addition
 * ("58% yesterday", "7h 40m of about 8h 10m needed"), [populationNote] the labelled population
 * reference for resting heart rate and respiratory rate only, and [summary] the one TalkBack
 * sentence for all of the numbers.
 */
internal data class GlossaryFacts(
    val name: String,
    val sentence: String?,
    val value: String,
    val usualLine: String,
    val contextLine: String?,
    val populationNote: String?,
    val summary: String,
)

/**
 * The glossary presentation of [signal]: personal usual first, a direction-only trend (never better
 * or worse), metric-specific context, and a population reference second where one is meaningful.
 */
@Composable
internal fun glossaryFacts(signal: Signal, state: MainUiState): GlossaryFacts {
    val metric = signal.metric
    val entry = MetricGlossary.entry(metric)
    val name = entry?.name ?: metric.label
    val value = glossaryValue(signal.latest, metric)
    val low = signal.normalLow
    val high = signal.normalHigh
    // The calendar days the trend's readings actually cover, so gaps in wear are never called "7 days".
    val trendDays = MetricGlossary.trendSpanDays(signal, TREND_DAYS)
    val trend = when (MetricGlossary.trend(signal, TREND_DAYS)) {
        Trend.RISING -> pluralStringResource(R.plurals.glossary_trend_rising, trendDays, trendDays)
        Trend.FALLING -> pluralStringResource(R.plurals.glossary_trend_falling, trendDays, trendDays)
        Trend.STEADY -> pluralStringResource(R.plurals.glossary_trend_steady, trendDays, trendDays)
        Trend.LEARNING -> null
    }
    val needed = SignalEngine.MIN_BASELINE_DAYS
    val (usual, comparison) = if (low == null || high == null) {
        pluralStringResource(R.plurals.glossary_usual_learning, needed, signal.baselineDays, needed) to
            pluralStringResource(R.plurals.glossary_a11y_learning, needed, name, value, signal.baselineDays, needed)
    } else {
        val lowText = if (metric == MetricType.SLEEP_DEBT) formatHoursMinutes(low) else formatNumber(low, metric)
        val highText = glossaryValue(high, metric)
        val comparisonRes = when (signal.position) {
            SignalPosition.ABOVE -> R.string.glossary_a11y_above
            SignalPosition.BELOW -> R.string.glossary_a11y_below
            else -> R.string.glossary_a11y_within
        }
        stringResource(R.string.glossary_usual, stringResource(R.string.glossary_range, lowText, highText)) to
            stringResource(comparisonRes, name, value, lowText, highText)
    }
    val context = glossaryContext(signal, state)
    val dot = stringResource(R.string.glossary_separator)
    return GlossaryFacts(
        name = name,
        sentence = entry?.sentence,
        value = value,
        usualLine = listOfNotNull(usual, trend).joinToString(dot),
        contextLine = context.takeIf { it.isNotEmpty() }?.joinToString(dot),
        populationNote = entry?.populationNote?.takeIf { metric in POPULATION_NOTE_METRICS },
        summary = (listOfNotNull(comparison, trend) + context).joinToString(stringResource(R.string.glossary_a11y_separator)),
    )
}

/** The metric-specific addition next to a coached number; empty when the metric has none. */
@Composable
private fun glossaryContext(signal: Signal, state: MainUiState): List<String> = when (signal.metric) {
    MetricType.RECOVERY_SCORE -> listOfNotNull(
        stringResource(R.string.glossary_zone, RecoveryZone.of(signal.latest.roundToInt()).label),
        MetricGlossary.previousValue(signal)?.let { previousReadingLine(signal, formatMetricValue(it, signal.metric)) },
    )
    MetricType.SLEEP_PERFORMANCE -> listOfNotNull(
        sleepGotVsNeeded(signal, state)?.let { (slept, need) ->
            stringResource(R.string.glossary_sleep_got_of_need, formatHoursMinutes(slept), formatHoursMinutes(need))
        },
    )
    MetricType.SLEEP_DEBT -> listOf(stringResource(R.string.glossary_debt_payback))
    MetricType.DAY_STRAIN -> listOfNotNull(
        stringResource(R.string.glossary_zone, StrainZone.of(signal.latest).label),
        state.strainTarget?.let { stringResource(R.string.glossary_strain_target, it.label, it.low, it.high) },
    )
    else -> emptyList()
}

/**
 * The reading before the latest, named by its real day: "58% yesterday" only when it is dated the
 * day before the latest reading and the latest is today's; otherwise "58% on Tue" (or "on Oct 3"
 * past a week), and "Previous reading 58%" when the signal carries no dates.
 */
@Composable
private fun previousReadingLine(signal: Signal, value: String): String {
    val previous = MetricGlossary.previousDate(signal)
        ?: return stringResource(R.string.glossary_recovery_previous, value)
    val today = LocalDate.now()
    if (MetricGlossary.previousIsYesterday(signal) && signal.dates.lastOrNull() == today) {
        return stringResource(R.string.glossary_recovery_yesterday, value)
    }
    val pattern = if (ChronoUnit.DAYS.between(previous, today) in 0L..WEEKDAY_NAME_DAYS) "EEE" else "MMM d"
    val day = previous.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    return stringResource(R.string.glossary_recovery_on_day, value, day)
}

/**
 * Hours got and hours needed for the newest night: the synced night when the plan has both,
 * otherwise the newest sleep-duration reading with the need estimated from this [performance].
 * Null when either is unknown — the comparison is never against a fixed 8 hours.
 */
private fun sleepGotVsNeeded(performance: Signal, state: MainUiState): Pair<Double, Double>? {
    val slept = state.sleptHours
    val need = state.sleepNeedHours
    if (slept != null && need != null) return slept to need
    val logged = SignalEngine.signal(MetricType.SLEEP_DURATION, state.readings)?.latest ?: return null
    val estimated = SleepNeed.neededHours(logged, performance.latest.roundToInt()) ?: return null
    return logged to estimated
}

/** The usual-and-trend line, then any metric-specific context. Wraps at any font size, never clipped. */
@Composable
internal fun GlossaryFactLines(facts: GlossaryFacts) {
    Text(
        facts.usualLine,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm),
    )
    facts.contextLine?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.xs))
    }
}

/** A population reference, second and labelled as such, so it never reads as the user's target. */
@Composable
internal fun GlossaryPopulationNote(note: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(stringResource(R.string.glossary_population_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
}

/** A coached value as shown: sleep debt in hours and minutes ("1h 10m"), everything else as [formatMetricValue]. */
private fun glossaryValue(value: Double, metric: MetricType): String =
    if (metric == MetricType.SLEEP_DEBT) formatHoursMinutes(value) else formatMetricValue(value, metric)

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

/**
 * One lab or body-metric entry — a manual blood-pressure reading included. The clinical status is
 * said in words on the pill, and every row keeps the same NEUTRAL tone whatever that status is:
 * colouring an above-range value amber (or an in-range one green) would turn a clinical reference
 * range into an alert.
 */
@Composable
private fun LabRow(assessment: MetricAssessment, onOpenMetric: (MetricType) -> Unit) {
    val type = assessment.reading.type
    val tone = Tone.NEUTRAL
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

/**
 * A lab-derived insight: the headline shows, the reasoning, safe actions and what is worth
 * discussing with a clinician open on tap. All in neutral text colours — the explanation states
 * the clinical status in words, never as an alert colour.
 */
@Composable
private fun LabInsight(card: InsightCard) {
    var open by rememberSaveable(card.title) { mutableStateOf(false) }
    // An expand is movement: it snaps open under reduced motion or the calmer theme.
    val reducedMotion = rememberReducedMotion()
    SpartanCard(
        modifier = if (reducedMotion) Modifier else Modifier.animateContentSize(),
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
            if (card.clinicianTriggers.isNotEmpty()) {
                Text(
                    stringResource(R.string.labs_worth_discussing),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.md),
                )
                card.clinicianTriggers.forEach {
                    Text("•  $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.xs))
                }
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
    if (low == null || high == null) {
        val needed = SignalEngine.MIN_BASELINE_DAYS
        return pluralStringResource(R.plurals.signal_learning_days, needed, signal.baselineDays, needed)
    }
    val range = "${formatNumber(low, signal.metric)}–${formatMetricValue(high, signal.metric)}"
    return pluralStringResource(R.plurals.signal_normal_range, signal.baselineDays, range, signal.baselineDays)
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
