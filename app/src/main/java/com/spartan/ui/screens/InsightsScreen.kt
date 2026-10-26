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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
import com.spartan.ui.components.StatusPill
import com.spartan.ui.components.Tone
import com.spartan.ui.components.TrendChart
import com.spartan.ui.components.metricIcon
import com.spartan.ui.components.rememberChartDayLabels
import com.spartan.ui.components.toneColor
import com.spartan.ui.theme.Spacing
import com.spartan.ui.theme.metricAccent
import com.spartan.ui.theme.rememberReducedMotion
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** Metrics that come from the wearable rather than a lab or a tape measure. */
private val WEARABLE = SignalEngine.DAILY.toSet() + MetricType.ENERGY_KCAL + MetricType.SLEEP_DURATION

/**
 * Metrics Spartan keeps but does not show — the owner's decision under spec §5 (no blood-pressure
 * data in the UI). Insights leaves out their lab rows and insight cards and Add a result does not
 * offer them; stored readings, the engines and the export are untouched.
 */
internal val HIDDEN_METRICS: Set<MetricType> = setOf(MetricType.SYSTOLIC_BP, MetricType.DIASTOLIC_BP)

/**
 * Days the glossary trend looks back over ("rising over 7 days"; fewer when the readings cover
 * fewer days), and the days a signal card charts.
 */
private const val TREND_DAYS = 7

/** Readings a chart needs before it draws a line ([TrendChart] draws nothing with fewer). */
private const val MIN_CHART_POINTS = 2

/** A previous reading within this many days of today is named by its weekday ("on Tue"), else by its date. */
private const val WEEKDAY_NAME_DAYS = 6L

/**
 * Metrics whose population reference is shown, second and labelled. Never HRV (there is no
 * population "good" HRV), and strain's zone scale is not a population range.
 */
private val POPULATION_NOTE_METRICS = setOf(MetricType.RESTING_HEART_RATE, MetricType.RESPIRATORY_RATE)

/**
 * Insights: the screen's one headline, then one signal per card — each in its own colour, with its
 * own labelled chart, one plain-language line and at most one action — and, quieter below, the
 * weekday patterns and lab results. No disclaimer footer (the full one lives in You → About): only
 * the one line under the signals header that says where "your usual" comes from, next to the bands
 * it explains.
 */
@Composable
fun InsightsScreen(
    state: MainUiState,
    onOpenMetric: (MetricType) -> Unit,
    onAddAction: (SuggestedAction, MetricType) -> Unit,
    onAddLabResult: () -> Unit,
) {
    val labs = state.assessments.filter { it.reading.type !in WEARABLE && it.reading.type !in HIDDEN_METRICS }
    val labInsights = shownLabInsights(state.insights)
    // One action per card in the order the cards appear, never the same one twice on the screen.
    val actions = ActionPlaybook.uniqueFirstActions(state.signals.map { it.metric })
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
                if (state.signals.isNotEmpty()) {
                    item(key = "usual-note") {
                        Text(
                            stringResource(R.string.insights_usual_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag(INSIGHTS_USUAL_NOTE_TAG),
                        )
                    }
                }
                items(state.signals, key = { it.metric.name }) { signal ->
                    SignalCard(signal, state, actions[signal.metric], onOpenMetric, onAddAction)
                }
            }

            if (state.weekdayEffects.isNotEmpty()) {
                item(key = "patterns-header") { SectionHeader(stringResource(R.string.insights_patterns)) }
                items(state.weekdayEffects, key = { it.stressorDay.name }) { effect ->
                    SpartanCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Quieter than the signals, and no status colour without a status word.
                            IconTile(Icons.Outlined.CalendarMonth, toneColor(Tone.NEUTRAL))
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
            // Index keys: lab insight titles are not guaranteed unique.
            items(labInsights) { LabInsight(it) }
        }
    }
}

/**
 * One signal, one card (spec §2 "Insights"). Its own colour ([signalAccent] — Recovery in its
 * zone's mark colour, so a Yellow recovery is never teal or green); the value with its pills
 * ([signalPills]: the position against the user's usual and, for Recovery, the zone as a word); the
 * trend and the metric's numeric context; the week's chart with the usual band labelled; one
 * plain-language line; and [action], at most one, never one another card already shows. The
 * metric's "what is this" sentence lives in metric detail, one tap away. TalkBack hears the numbers
 * once — as the chart's summary, or the value block's when there is no chart yet — and the marks
 * stay silent. The parts carry [signalCardTag] test tags.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignalCard(
    signal: Signal,
    state: MainUiState,
    action: SuggestedAction?,
    onOpenMetric: (MetricType) -> Unit,
    onAddAction: (SuggestedAction, MetricType) -> Unit,
) {
    val facts = glossaryFacts(signal, state)
    val accent = signalAccent(signal)
    val series = chartSeries(signal)
    val meaning = stringResource(insightMeaningRes(signal))
    SpartanCard(
        onClick = { onOpenMetric(signal.metric) },
        onClickLabel = stringResource(R.string.checkin_open_metric, facts.name),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(metricIcon(signal.metric), accent)
            Spacer(Modifier.width(Spacing.md))
            Text(facts.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        }
        Column(Modifier.padding(top = Spacing.lg).clearAndSetSemantics { if (!series.charted) contentDescription = facts.cardSummary }) {
            // Wraps rather than squeezes: at large font sizes the pills drop under the value.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                // A number, not a headline: the screen title is the one headline on Insights.
                Text(
                    facts.value,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.CenterVertically).testTag(signalCardTag(signal.metric, SIGNAL_VALUE)),
                )
                signalPills(signal).forEach { pill ->
                    StatusPill(
                        pill.zone?.let { stringResource(pill.labelRes, it.label) } ?: stringResource(pill.labelRes),
                        pill.tone,
                        Modifier.align(Alignment.CenterVertically).testTag(signalCardTag(signal.metric, SIGNAL_PILL)),
                    )
                }
            }
            facts.factsLine?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm).testTag(signalCardTag(signal.metric, SIGNAL_FACTS)),
                )
            }
        }
        if (series.charted) {
            TrendChart(
                values = series.values,
                dayLabels = rememberChartDayLabels(series.dates),
                color = accent,
                summary = facts.cardSummary,
                latestLabel = facts.value,
                modifier = Modifier.padding(top = Spacing.lg),
                usualLow = signal.normalLow,
                usualHigh = signal.normalHigh,
                usualLabel = facts.usualLabel,
                drawKey = "insights-${signal.metric.name}",
            )
        }
        Text(
            facts.advice?.let { stringResource(R.string.insights_sentences, meaning, it) } ?: meaning,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.lg).testTag(signalCardTag(signal.metric, SIGNAL_LINE)),
        )
        if (action != null) {
            HorizontalDivider(Modifier.padding(vertical = Spacing.lg), color = MaterialTheme.colorScheme.outlineVariant)
            ActionLine(action, ActionPlaybook.isPlanned(action, state.todayActivities)) { onAddAction(action, signal.metric) }
        }
    }
}

/** A signal card's parts, as test tags: "insights-RECOVERY_SCORE-pill" and so on. */
internal fun signalCardTag(metric: MetricType, part: String): String = "insights-${metric.name}-$part"

/** [signalCardTag] parts: the value, each status pill, the line under the value, the plain-language line. */
internal const val SIGNAL_VALUE = "value"
internal const val SIGNAL_PILL = "pill"
internal const val SIGNAL_FACTS = "facts"
internal const val SIGNAL_LINE = "line"

/** Test tag on the line under "Your signals" that says where "your usual" comes from. */
internal const val INSIGHTS_USUAL_NOTE_TAG = "insights-usual-note"

/**
 * The readings a signal card charts, oldest first; [dates] holds each one's day, or is empty when
 * the days are unknown. [charted] is false when there are too few to draw a line.
 */
internal data class ChartSeries(val values: List<Double>, val dates: List<LocalDate>) {
    val charted: Boolean get() = values.size >= MIN_CHART_POINTS
}

/**
 * Exactly the readings the trend sentence counts ([MetricGlossary.recentIndices], the window behind
 * [MetricGlossary.trend] and "rising over N days"): those dated within [days] days of the latest, so
 * a gap in wear is never charted as a full week. When the dates do not line up one per value
 * (missing, or fewer than the values), the last [days] values with no dates: the line still draws,
 * and no point is ever given another day's label.
 */
internal fun chartSeries(signal: Signal, days: Int = TREND_DAYS): ChartSeries {
    val shown = MetricGlossary.recentIndices(signal, days)
    val dates = if (MetricGlossary.hasDates(signal)) shown.map { signal.dates[it] } else emptyList()
    return ChartSeries(shown.map { signal.history[it] }, dates)
}

/** Recovery's zone for its latest reading — what colours its tile, chart and zone pill — or null for every other metric. */
internal fun signalZone(signal: Signal): RecoveryZone? =
    if (signal.metric == MetricType.RECOVERY_SCORE) RecoveryZone.of(signal.latest.roundToInt()) else null

/**
 * A signal's icon-tile and chart colour, on Insights and in metric detail: its [metricAccent], with
 * Recovery in its zone's mark colour ([signalZone]) — never the brand teal, and never grey while
 * there is a reading.
 */
@Composable
internal fun signalAccent(signal: Signal): Color = metricAccent(signal.metric, signalZone(signal))

/**
 * One status pill on a signal card: [labelRes] in [tone]; [zone] is the recovery zone whose word
 * fills [labelRes] ("Yellow zone"), null for the position pill.
 */
internal data class SignalPill(val labelRes: Int, val tone: Tone, val zone: RecoveryZone? = null)

/**
 * A signal card's pills, in order: the position against the user's usual ([signalStatusRes], in
 * the neutral [POSITION_TONE]), then, for Recovery only, its zone as a word in the zone's own tone
 * ([zoneTone]). The only status colour is the zone's, and it always comes with its word.
 */
internal fun signalPills(signal: Signal): List<SignalPill> =
    listOf(SignalPill(signalStatusRes(signal), POSITION_TONE)) +
        listOfNotNull(signalZone(signal)?.let { SignalPill(R.string.glossary_zone, zoneTone(it), it) })

/**
 * The plain-language line, on an Insights card and in metric detail's "What this means for you":
 * what today's reading means against the user's own usual, in wellness words — observations and a
 * gentle next step, never a claim about the body's systems or what will follow. Recovery's line
 * also follows its zone ([recoveryMeaningRes]).
 */
internal fun insightMeaningRes(signal: Signal): Int {
    val p = signal.position
    if (p == SignalPosition.LEARNING) return R.string.insights_meaning_learning
    signalZone(signal)?.let { return recoveryMeaningRes(p, it) }
    return when (signal.metric) {
        MetricType.HRV_RMSSD ->
            byPosition(p, R.string.insights_meaning_hrv_above, R.string.insights_meaning_hrv_within, R.string.insights_meaning_hrv_below)
        MetricType.RESTING_HEART_RATE ->
            byPosition(p, R.string.insights_meaning_rhr_above, R.string.insights_meaning_rhr_within, R.string.insights_meaning_rhr_below)
        MetricType.SLEEP_PERFORMANCE ->
            byPosition(p, R.string.insights_meaning_sleep_above, R.string.insights_meaning_sleep_within, R.string.insights_meaning_sleep_below)
        MetricType.SLEEP_DEBT ->
            byPosition(p, R.string.insights_meaning_debt_above, R.string.insights_meaning_debt_within, R.string.insights_meaning_debt_below)
        MetricType.RESPIRATORY_RATE ->
            byPosition(p, R.string.insights_meaning_respiratory_above, R.string.insights_meaning_respiratory_within, R.string.insights_meaning_respiratory_below)
        MetricType.DAY_STRAIN ->
            byPosition(p, R.string.insights_meaning_strain_above, R.string.insights_meaning_strain_within, R.string.insights_meaning_strain_below)
        else -> R.string.insights_meaning_other
    }
}

/**
 * Recovery's line from its position against the user's usual AND its zone, so it never contradicts
 * the zone pill beside it: a Yellow or Red day above its usual is never "a good day for a quality
 * session", and a Green day — even one below its usual — is never told to keep it gentle.
 */
internal fun recoveryMeaningRes(position: SignalPosition, zone: RecoveryZone): Int = when (zone) {
    RecoveryZone.GREEN -> byPosition(
        position,
        R.string.insights_meaning_recovery_green_above,
        R.string.insights_meaning_recovery_green_within,
        R.string.insights_meaning_recovery_green_below,
    )
    RecoveryZone.YELLOW -> byPosition(
        position,
        R.string.insights_meaning_recovery_yellow_above,
        R.string.insights_meaning_recovery_yellow_within,
        R.string.insights_meaning_recovery_yellow_below,
    )
    RecoveryZone.RED -> byPosition(
        position,
        R.string.insights_meaning_recovery_red_above,
        R.string.insights_meaning_recovery_red_within,
        R.string.insights_meaning_recovery_red_below,
    )
}

private fun byPosition(position: SignalPosition, above: Int, within: Int, below: Int): Int = when (position) {
    SignalPosition.ABOVE -> above
    SignalPosition.BELOW -> below
    else -> within
}

/**
 * Whether Sleep debt's "pay it back over a few nights" tip follows its line: only while the debt is
 * at or above the user's usual — never on a lighter-than-usual night, and never while Spartan is
 * still learning, when the tip would follow no number.
 */
internal fun debtTipShown(signal: Signal): Boolean =
    signal.metric == MetricType.SLEEP_DEBT && (signal.position == SignalPosition.ABOVE || signal.position == SignalPosition.WITHIN)

/**
 * The insight cards "Lab results" shows, known by the metric each card is about: never a wearable
 * metric's — WHOOP's resting heart rate is a signal card, read against the user's usual, and its
 * population reference lives in its metric detail — and never the [HIDDEN_METRICS] (blood pressure).
 */
internal fun shownLabInsights(cards: List<InsightCard>): List<InsightCard> =
    cards.filterNot { it.metric in WEARABLE || it.metric in HIDDEN_METRICS }

/**
 * What a coached metric's number is always shown with (research §3.1–3.3), ready to render:
 * [usualLine] is the user's usual plus the trend, [contextLine] the metric-specific addition
 * ("Yellow zone · 58% on Tue", "7h 40m of about 8h 10m needed"), [populationNote] the labelled
 * population reference for resting heart rate and respiratory rate only, and [summary] the one
 * TalkBack sentence for all of the numbers.
 *
 * The Insights card reads the rest: [usualLabel] is the chart band's legend ("Your usual 49–78%";
 * null while Spartan is still learning), [factsLine] the line under the value — the usual's numbers
 * whenever no band legend shows them (while learning, the days so far; with too few readings this
 * week to chart, "Your usual 49–78%"), then the trend, then the numeric context, without the zone
 * (a pill beside the number) or [advice] (said in the card's plain-language line) — and
 * [cardSummary] the card's one TalkBack sentence: value, zone, usual, trend and numeric context,
 * with the advice left to the line itself.
 */
internal data class GlossaryFacts(
    val name: String,
    val value: String,
    val usualLine: String,
    val contextLine: String?,
    val populationNote: String?,
    val summary: String,
    val usualLabel: String? = null,
    val factsLine: String? = null,
    val advice: String? = null,
    val cardSummary: String = summary,
)

/** The usual as TalkBack says it — "49 to 78%", or "78 ms" when both ends round alike — or null while learning. */
@Composable
internal fun spokenUsual(signal: Signal): String? {
    val low = signal.normalLow ?: return null
    val high = signal.normalHigh ?: return null
    val highText = formatMetricValue(high, signal.metric)
    if (formatMetricValue(low, signal.metric) == highText) return highText
    return stringResource(R.string.glossary_a11y_range, formatNumber(low, signal.metric), highText)
}

/**
 * The glossary presentation of [signal]: personal usual first, a direction-only trend (never better
 * or worse), metric-specific context, and a population reference second where one is meaningful.
 */
@Composable
internal fun glossaryFacts(signal: Signal, state: MainUiState): GlossaryFacts {
    val metric = signal.metric
    val entry = MetricGlossary.entry(metric)
    val name = entry?.name ?: metric.label
    val value = formatMetricValue(signal.latest, metric)
    val spoken = spokenUsual(signal)
    val low = signal.normalLow
    val high = signal.normalHigh
    // The calendar days the trend's readings actually cover, so gaps in wear are never called "7 days".
    val trendDays = MetricGlossary.trendSpanDays(signal, TREND_DAYS)
    val direction = MetricGlossary.trend(signal, TREND_DAYS)
    val trend = when (direction) {
        Trend.RISING -> pluralStringResource(R.plurals.glossary_trend_rising, trendDays, trendDays)
        Trend.FALLING -> pluralStringResource(R.plurals.glossary_trend_falling, trendDays, trendDays)
        Trend.STEADY -> pluralStringResource(R.plurals.glossary_trend_steady, trendDays, trendDays)
        Trend.LEARNING -> null
    }
    // The same trend opening a line: "Rising over 7 days · 81% on Tue".
    val trendLead = when (direction) {
        Trend.RISING -> pluralStringResource(R.plurals.insights_trend_rising, trendDays, trendDays)
        Trend.FALLING -> pluralStringResource(R.plurals.insights_trend_falling, trendDays, trendDays)
        Trend.STEADY -> pluralStringResource(R.plurals.insights_trend_steady, trendDays, trendDays)
        Trend.LEARNING -> null
    }
    val needed = SignalEngine.MIN_BASELINE_DAYS
    val usual: String
    val usualLabel: String?
    val comparison: String
    if (low == null || high == null || spoken == null) {
        usual = pluralStringResource(R.plurals.glossary_usual_learning, needed, signal.baselineDays, needed)
        usualLabel = null
        comparison = pluralStringResource(R.plurals.glossary_a11y_learning, needed, signal.baselineDays, needed)
    } else {
        val highText = formatMetricValue(high, metric)
        // A middle half that rounds to one value reads as that value, never "78–78 ms".
        val single = formatMetricValue(low, metric) == highText
        val comparisonRes = when (signal.position) {
            SignalPosition.ABOVE -> R.string.glossary_a11y_above
            SignalPosition.BELOW -> R.string.glossary_a11y_below
            else -> R.string.glossary_a11y_within
        }
        usual = stringResource(R.string.glossary_usual, if (single) highText else stringResource(R.string.glossary_range, formatNumber(low, metric), highText))
        usualLabel = usual
        comparison = stringResource(comparisonRes, spoken)
    }
    // The usual's numbers lead the line under the value whenever no band legend shows them: while
    // learning, the days so far (beside the "Learning your usual" pill, so "learning" is said once);
    // and when too few readings fall in this week to draw the chart, the usual itself.
    val factsUsual = when {
        usualLabel == null -> pluralStringResource(R.plurals.insights_learning_days, needed, signal.baselineDays, needed)
        !chartSeries(signal).charted -> usualLabel
        else -> null
    }
    val spokenValue = stringResource(R.string.glossary_a11y_value, name, value)
    val context = glossaryContext(signal, state)
    val dot = stringResource(R.string.glossary_separator)
    val comma = stringResource(R.string.glossary_a11y_separator)
    return GlossaryFacts(
        name = name,
        value = value,
        usualLine = listOfNotNull(usual, trend).joinToString(dot),
        contextLine = context.all.takeIf { it.isNotEmpty() }?.joinToString(dot),
        populationNote = entry?.populationNote?.takeIf { metric in POPULATION_NOTE_METRICS },
        summary = (listOfNotNull(spokenValue, comparison, trend) + context.all).joinToString(comma),
        usualLabel = usualLabel,
        factsLine = (listOfNotNull(factsUsual, trendLead) + context.facts).takeIf { it.isNotEmpty() }?.joinToString(dot),
        advice = context.advice,
        cardSummary = (listOfNotNull(spokenValue, context.zoneText, comparison, trend) + context.facts).joinToString(comma),
    )
}

/**
 * A coached number's metric-specific context: Recovery's zone in words ([zoneText]), [facts] —
 * numbers, and words for numbers — and [advice], a plain-language tip. [all] is the order
 * [GlossaryFacts.contextLine] shows them in.
 */
private data class MetricContext(
    val zoneText: String? = null,
    val facts: List<String> = emptyList(),
    val advice: String? = null,
) {
    val all: List<String> get() = listOfNotNull(zoneText) + facts + listOfNotNull(advice)
}

/** The metric-specific addition next to a coached number; empty when the metric has none. */
@Composable
private fun glossaryContext(signal: Signal, state: MainUiState): MetricContext = when (signal.metric) {
    MetricType.RECOVERY_SCORE -> MetricContext(
        zoneText = signalZone(signal)?.let { stringResource(R.string.glossary_zone, it.label) },
        facts = listOfNotNull(MetricGlossary.previousValue(signal)?.let { previousReadingLine(signal, formatMetricValue(it, signal.metric)) }),
    )
    MetricType.SLEEP_PERFORMANCE -> MetricContext(
        facts = listOfNotNull(
            sleepGotVsNeeded(signal, state)?.let { (slept, need) ->
                stringResource(R.string.glossary_sleep_got_of_need, formatHoursMinutes(slept), formatHoursMinutes(need))
            },
        ),
    )
    MetricType.SLEEP_DEBT -> if (debtTipShown(signal)) MetricContext(advice = stringResource(R.string.glossary_debt_payback)) else MetricContext()
    MetricType.DAY_STRAIN -> MetricContext(
        facts = listOfNotNull(
            stringResource(R.string.glossary_zone, StrainZone.of(signal.latest).label),
            state.strainTarget?.let { stringResource(R.string.glossary_strain_target, it.label, it.low, it.high) },
        ),
    )
    else -> MetricContext()
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
 * One lab or body-metric entry. The clinical status is said in words on the pill, and every row
 * keeps the same NEUTRAL tone whatever that status is: colouring an above-range value amber (or an
 * in-range one green) would turn a clinical reference range into an alert.
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

/**
 * Status label and tone for a signal — shared by Today and metric detail; an Insights card shows
 * the same as its first pill ([signalPills]). The label is the position against the user's own
 * usual and nothing more ([signalStatusRes]), in [POSITION_TONE].
 */
@Composable
internal fun signalStatus(signal: Signal): Pair<String, Tone> = stringResource(signalStatusRes(signal)) to POSITION_TONE

/**
 * "In your usual range" only when the reading is inside it; otherwise "Above your usual" or "Below
 * your usual" — the direction, never a grade — and "Learning your usual" before there is one.
 */
internal fun signalStatusRes(signal: Signal): Int = when (signal.position) {
    SignalPosition.LEARNING -> R.string.signal_learning
    SignalPosition.WITHIN -> R.string.signal_within
    SignalPosition.ABOVE -> R.string.signal_above
    SignalPosition.BELOW -> R.string.signal_below
}

/**
 * The tone of every position pill — above, below, in or still learning the user's usual — on every
 * metric, Recovery included: neutral. The pill names a direction, never a grade, so its colour does
 * not grade it either (green for the "healthy" way, amber for the other). Green, amber and red are
 * Recovery's zone pill's alone ([zoneTone]), always with the zone's word.
 */
internal val POSITION_TONE: Tone = Tone.NEUTRAL

/**
 * The pill tone for a recovery zone. Tones read the same contrast-audited band palette as the zone
 * text colours, so toneColor(zoneTone(zone)) is zoneColor(zone): Green, Yellow (amber) or Red, always
 * with the zone's word.
 */
internal fun zoneTone(zone: RecoveryZone): Tone = when (zone) {
    RecoveryZone.GREEN -> Tone.POSITIVE
    RecoveryZone.YELLOW -> Tone.CAUTION
    RecoveryZone.RED -> Tone.ALERT
}

/** "Your usual 61–71 ms · 6 days", or how long until there is one. */
@Composable
internal fun normalRangeLabel(signal: Signal): String {
    val low = signal.normalLow
    val high = signal.normalHigh
    if (low == null || high == null) {
        val needed = SignalEngine.MIN_BASELINE_DAYS
        return pluralStringResource(R.plurals.signal_learning_days, needed, signal.baselineDays, needed)
    }
    val highText = formatMetricValue(high, signal.metric)
    val range = if (formatMetricValue(low, signal.metric) == highText) highText else "${formatNumber(low, signal.metric)}–$highText"
    return pluralStringResource(R.plurals.signal_normal_range, signal.baselineDays, range, signal.baselineDays)
}

/** Metrics that read as whole numbers; everything else keeps one decimal when it has one. */
private val WHOLE_NUMBERS = setOf(
    MetricType.RECOVERY_SCORE, MetricType.HRV_RMSSD, MetricType.RESTING_HEART_RATE, MetricType.SLEEP_PERFORMANCE,
    MetricType.ENERGY_KCAL, MetricType.FASTING_GLUCOSE, MetricType.TRIGLYCERIDES, MetricType.HDL_C, MetricType.APOB,
    MetricType.LPA, MetricType.CAC, MetricType.SYSTOLIC_BP, MetricType.DIASTOLIC_BP, MetricType.EXERCISE_MINUTES,
    MetricType.STRENGTH_SESSIONS,
)

internal fun formatNumber(value: Double, metric: MetricType): String {
    // "1h 48m" carries its own units, so a sleep-debt range reads "12m–1h 48m", never "0.2–1h 48m".
    if (metric == MetricType.SLEEP_DEBT) return formatHoursMinutes(value)
    if (metric in WHOLE_NUMBERS) return value.roundToInt().toString()
    val oneDecimal = String.format(Locale.getDefault(), "%.1f", value)
    return if ((value * 10).roundToInt() % 10 == 0) value.roundToInt().toString() else oneDecimal
}

/** "58 ms", "1h 48m", "42%" — how every metric value is shown. */
internal fun formatMetricValue(value: Double, metric: MetricType): String {
    val number = formatNumber(value, metric)
    if (metric == MetricType.SLEEP_DEBT) return number
    return when (metric.unit) {
        "" -> number
        "%" -> "$number%"
        "sessions" -> if (number == "1") "1 session" else "$number sessions"
        else -> "$number ${metric.unit}"
    }
}
