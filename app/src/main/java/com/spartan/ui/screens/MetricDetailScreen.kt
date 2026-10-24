package com.spartan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spartan.R
import com.spartan.domain.engine.ActionPlaybook
import com.spartan.domain.engine.MetricExplainers
import com.spartan.domain.engine.MetricGlossary
import com.spartan.domain.engine.SignalEngine
import com.spartan.domain.engine.SuggestedAction
import com.spartan.domain.engine.TrainingProfile
import com.spartan.domain.engine.VideoLibrary
import com.spartan.domain.model.ClinicalStatus
import com.spartan.domain.model.Intensity
import com.spartan.domain.model.MetricReading
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.TargetStatus
import com.spartan.ui.components.DetailScaffold
import com.spartan.ui.components.IconTile
import com.spartan.ui.components.PrimaryButton
import com.spartan.ui.components.SectionHeader
import com.spartan.ui.components.SpartanCard
import com.spartan.ui.components.Sparkline
import com.spartan.ui.components.StatusPill
import com.spartan.ui.components.Tone
import com.spartan.ui.components.WellnessFooter
import com.spartan.ui.components.metricIcon
import com.spartan.ui.components.toneColor
import com.spartan.ui.theme.Spacing
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Wearable metrics are read against the user's own normal; everything else against clinical ranges. */
private val WEARABLE_DETAIL = SignalEngine.DAILY.toSet() + MetricType.ENERGY_KCAL + MetricType.SLEEP_DURATION

/**
 * One metric, laid out like a listing (level 2 → level 3): for a coached WHOOP metric, its one
 * human sentence, then the number against the user's usual and trend; its chart, what it means,
 * how to move it, and the raw entries last. A sticky bar puts the best action into today's plan.
 * Lab metrics keep their clinical-range presentation.
 */
@Composable
fun MetricDetailScreen(
    state: MainUiState,
    type: MetricType,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (MetricReading) -> Unit,
    onAddAction: (SuggestedAction, MetricType) -> Unit,
) {
    val signal = if (type in WEARABLE_DETAIL) SignalEngine.signal(type, state.readings) else null
    val assessment = state.assessments.firstOrNull { it.reading.type == type }
    val history = state.readings.filter { it.type == type }.sortedByDescending { it.recordedAt }
    val actions = ActionPlaybook.forMetric(type)
    val topAction = actions.firstOrNull()
    val explainer = MetricExplainers.forMetric(type)
    val training = VideoLibrary.recommend(type, TrainingProfile(ageYears = state.userAgeYears, offTargetMetrics = state.offTargetMetrics))
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    // Coached WHOOP metrics carry a plain-language glossary entry; labs and body metrics do not.
    val glossary = MetricGlossary.entry(type)
    val title = glossary?.name ?: type.label

    DetailScaffold(
        title = title,
        onBack = onBack,
        bottomBar = topAction?.let { action ->
            { StickyActionBar(action, ActionPlaybook.isPlanned(action, state.todayActivities)) { onAddAction(action, type) } }
        },
    ) {
        item(key = "hero") {
            val tone = signal?.let { signalStatus(it).second } ?: assessment?.let { clinicalTone(it.clinicalStatus) } ?: Tone.NEUTRAL
            val facts = if (signal != null && glossary != null) glossaryFacts(signal, state) else null
            Column(Modifier.padding(top = Spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(metricIcon(type), toneColor(tone), size = 52.dp)
                    Spacer(Modifier.weight(1f))
                    if (state.whoopIsMock && type in WEARABLE_DETAIL) SampleDataChip()
                }
                // Human language before numbers: the one sentence leads, even before the first reading.
                glossary?.let {
                    Text(it.sentence, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.lg))
                }
                if (signal != null && facts != null) {
                    val (status, statusTone) = signalStatus(signal)
                    // TalkBack hears the value, usual, trend and context as one sentence.
                    Column(Modifier.padding(top = Spacing.lg).clearAndSetSemantics { contentDescription = facts.summary }) {
                        Text(facts.value, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
                        StatusPill(status, statusTone, Modifier.padding(top = Spacing.sm))
                        GlossaryFactLines(facts)
                    }
                    facts.populationNote?.let { GlossaryPopulationNote(it, Modifier.padding(top = Spacing.md)) }
                } else {
                    Text(
                        (signal?.latest ?: assessment?.reading?.value)?.let { formatMetricValue(it, type) } ?: stringResource(R.string.common_pending),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = Spacing.lg),
                    )
                    if (signal != null) {
                        val (status, statusTone) = signalStatus(signal)
                        Row(Modifier.padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(status, statusTone)
                            Spacer(Modifier.width(Spacing.sm))
                            Text(normalRangeLabel(signal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (assessment != null) {
                        Row(Modifier.padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            StatusPill(stringResource(statusLabelRes(assessment.clinicalStatus)), clinicalTone(assessment.clinicalStatus))
                            if (assessment.targetStatus != TargetStatus.NO_TARGET && assessment.targetStatus != TargetStatus.PENDING) {
                                StatusPill(stringResource(targetLabelRes(assessment.targetStatus)), Tone.NEUTRAL)
                            }
                        }
                    }
                }
            }
        }

        val trend = history.reversed().mapNotNull { it.value }.takeLast(30)
        if (trend.size >= 2) {
            item(key = "chart") {
                SpartanCard(Modifier.padding(top = Spacing.lg)) {
                    Text(stringResource(R.string.detail_trend_title, trend.size), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Sparkline(
                        trend,
                        toneColor(signal?.let { signalStatus(it).second } ?: Tone.INFO),
                        Modifier.padding(top = Spacing.md),
                        signal?.normalLow,
                        signal?.normalHigh,
                        height = 160.dp,
                    )
                    if (signal?.normalLow != null) {
                        Text(stringResource(R.string.detail_band_legend), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
                    }
                }
            }
        }

        item(key = "meaning") {
            SectionHeader(stringResource(R.string.detail_meaning))
            Text(
                signal?.let { SignalEngine.meaning(it) } ?: assessment?.clinicalMessage ?: stringResource(R.string.detail_no_reading),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            assessment?.targetMessage?.takeIf { signal == null && it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
            }
        }

        if (actions.isNotEmpty()) {
            item(key = "improve-header") { SectionHeader(stringResource(R.string.detail_improve)) }
            items(actions, key = { "action-" + it.key }) { action ->
                SpartanCard {
                    ActionLine(action, ActionPlaybook.isPlanned(action, state.todayActivities)) { onAddAction(action, type) }
                    Text(action.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
                }
            }
        } else if (type in setOf(MetricType.APOB, MetricType.LPA, MetricType.CAC)) {
            item(key = "clinician") {
                SectionHeader(stringResource(R.string.detail_improve))
                Text(stringResource(R.string.detail_clinician_first), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        if (training != null) {
            item(key = "videos-header") {
                SectionHeader(stringResource(R.string.metrics_train_title))
                val offTarget = assessment != null && (
                    assessment.clinicalStatus == ClinicalStatus.ABOVE_RANGE || assessment.clinicalStatus == ClinicalStatus.BELOW_RANGE ||
                        assessment.targetStatus == TargetStatus.ABOVE_PERSONAL_TARGET || assessment.targetStatus == TargetStatus.BELOW_PERSONAL_TARGET
                    )
                if (offTarget) {
                    Text(stringResource(R.string.metrics_train_off_target), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = Spacing.sm))
                }
                Text(training.intro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(training.guides, key = { "video-" + it.id }) { guide ->
                val uriHandler = LocalUriHandler.current
                SpartanCard(
                    onClick = { uriHandler.openUri(guide.url) },
                    onClickLabel = stringResource(R.string.metrics_train_open_video, guide.title),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Outlined.PlayCircle, MaterialTheme.colorScheme.primary, size = 56.dp)
                        Spacer(Modifier.width(Spacing.lg))
                        Column(Modifier.weight(1f)) {
                            Text(guide.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                stringResource(R.string.metrics_train_video_meta, guide.channel, guide.minutes, intensityLabel(guide.intensity)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item(key = "videos-note") {
                Text(stringResource(R.string.metrics_train_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (explainer != null) {
            item(key = "about") {
                SectionHeader(stringResource(R.string.detail_about, title))
                Text(explainer.whatItIs, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                AboutBlock(stringResource(R.string.explainer_what_moves_it)) {
                    explainer.whatMovesIt.forEach {
                        Text("•  $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                AboutBlock(stringResource(R.string.explainer_good_pattern)) {
                    Text(explainer.whatGoodLooksLike, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                AboutBlock(stringResource(R.string.explainer_how_used)) {
                    Text(explainer.howSpartanUsesIt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        item(key = "history-header") {
            SectionHeader(stringResource(R.string.metrics_entries), action = stringResource(R.string.detail_add_entry), onAction = onAdd)
        }
        items(history.take(10), key = { "entry-" + it.id }) { reading ->
            Column {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            reading.value?.let { formatMetricValue(it, type) } ?: stringResource(R.string.common_pending),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(reading.recordedAt.format(dateFormat), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onEdit(reading) }) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.metrics_edit_entry))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        item(key = "wellness") { WellnessFooter() }
    }
}

/** The listing-style sticky footer: the best action for this metric, one tap from today's plan. */
@Composable
private fun StickyActionBar(action: SuggestedAction, planned: Boolean, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = Spacing.md)) {
            Text(action.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
            Text(stringResource(R.string.insights_minutes, action.minutes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (planned) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.insights_in_plan), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        } else {
            PrimaryButton(stringResource(R.string.detail_add_to_today), onAdd)
        }
    }
}

@Composable
private fun AboutBlock(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xs).semantics { heading() },
    )
    content()
}

private fun clinicalTone(status: ClinicalStatus): Tone = when (status) {
    ClinicalStatus.NORMAL -> Tone.POSITIVE
    ClinicalStatus.ABOVE_RANGE, ClinicalStatus.BELOW_RANGE -> Tone.CAUTION
    ClinicalStatus.PENDING, ClinicalStatus.UNKNOWN -> Tone.NEUTRAL
}

@Composable
private fun intensityLabel(intensity: Intensity): String = when (intensity) {
    Intensity.REST -> stringResource(R.string.intensity_rest)
    Intensity.EASY -> stringResource(R.string.intensity_easy)
    Intensity.MODERATE -> stringResource(R.string.intensity_moderate)
    Intensity.HARD -> stringResource(R.string.intensity_hard)
}
