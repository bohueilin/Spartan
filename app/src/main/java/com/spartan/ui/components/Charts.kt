package com.spartan.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spartan.R
import com.spartan.domain.engine.RecoveryZone
import com.spartan.domain.model.MetricType
import com.spartan.ui.theme.Motion
import com.spartan.ui.theme.Spacing
import com.spartan.ui.theme.SpartanTheme
import com.spartan.ui.theme.heroColors
import com.spartan.ui.theme.metricAccent
import com.spartan.ui.theme.rememberReducedMotion
import com.spartan.ui.theme.zoneOnHeroColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The share of the ring the arc covers: exactly the recovery score, clamped to 0–100%. */
internal fun ringFraction(score: Int): Float = score.coerceIn(0, 100) / 100f

/**
 * The verdict ring: a track and an arc that fills to exactly [score] percent — the real recovery,
 * never an example value ([ringFraction]). It sweeps in once, on the first verdict of the day
 * ([playOnce]), calling [onPlayed] as it starts so leaving mid-way never replays it; a screen
 * visit, reduce-motion and the calmer theme show the final arc straight away (a reduced-motion
 * first sight still calls [onPlayed], so turning motion back on later never replays the day's). Decorative to
 * TalkBack, with [OneShotKey] as the test hook: the caller states colour, word and number in text
 * ([content] is centred inside the ring).
 */
@Composable
fun RecoveryRing(
    score: Int,
    arcColor: Color,
    trackColor: Color,
    playOnce: Boolean,
    onPlayed: () -> Unit,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit = {},
) = MeasureRing(ringFraction(score), arcColor, trackColor, modifier, playOnce, onPlayed, strokeWidth, content)

/**
 * A ring whose arc covers exactly [fraction] (clamped to 0–1) of the circle — a recovery score, or a
 * goal's count against its weekly target. Butt caps, so the arc ends on the value's own angle
 * rather than a rounded cap past it. With [playOnce] it sweeps in from empty once ([onPlayed] is
 * called as it starts; under reduced motion it shows the value at once and still calls [onPlayed],
 * so the moment is used up); otherwise it shows the value at once. A later change of [fraction] (a
 * re-sync, a session logged) re-settles the arc with Motion.measure — never overshooting — and
 * snaps under reduce-motion or the calmer theme. Decorative to TalkBack, with [OneShotKey] as the
 * test hook; [content] is centred inside.
 */
@Composable
fun MeasureRing(
    fraction: Float,
    arcColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    playOnce: Boolean = false,
    onPlayed: () -> Unit = {},
    strokeWidth: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val reducedMotion = rememberReducedMotion()
    // Decided once per visit: the caller turning playOnce off after onPlayed must not cut it short.
    val playback = remember { oneShotPlayback(endState = true, changedWhileShown = playOnce, reducedMotion = reducedMotion) }
    val latestOnPlayed by rememberUpdatedState(onPlayed)
    val target = fraction.coerceIn(0f, 1f)
    val sweep = remember { Animatable(if (playback == OneShot.PLAY_ONCE) 0f else target) }
    var reported by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        // First sight of a fresh verdict uses the moment up, animated or not.
        if (playOnce && !reported) {
            reported = true
            latestOnPlayed()
        }
        if (reducedMotion) sweep.snapTo(target) else sweep.animateTo(target, Motion.measure())
    }
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize().clearAndSetSemantics { oneShot = playback }) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            if (sweep.value > 0f) {
                drawArc(
                    arcColor,
                    startAngle = -90f,
                    sweepAngle = 360f * sweep.value,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Butt),
                )
            }
        }
        content()
    }
}

/**
 * Charts that already drew in during this app session: each chart draws in once — the chart-draw
 * moment — never again on a revisit, a scroll back or a rotation.
 */
internal object ChartDrawSession {
    private val drawn = mutableSetOf<String>()

    @Synchronized
    fun claim(key: String): Boolean = drawn.add(key)

    @Synchronized
    fun reset() = drawn.clear()
}

/**
 * Which of a row of labels fit without overlapping: every label when they fit, otherwise every
 * second, third, … counted back from the newest (rightmost), so "Today" always shows and the kept
 * days stay evenly spaced. Label i is [widths] wide, centred on [centers] i but kept inside
 * [totalWidth], and needs [gap] clear of its neighbour. At large font sizes this thins the row
 * instead of overlapping or clipping words; a label too wide for the row still shows alone.
 */
internal fun visibleLabelIndices(centers: List<Float>, widths: List<Int>, totalWidth: Int, gap: Int): Set<Int> {
    if (centers.isEmpty()) return emptySet()
    val last = centers.lastIndex
    fun left(i: Int) = (centers[i] - widths[i] / 2f).coerceIn(0f, (totalWidth - widths[i]).toFloat().coerceAtLeast(0f))
    for (stride in 1..centers.size) {
        val kept = (last downTo 0 step stride).toList() // newest first
        val fits = kept.zipWithNext().all { (right, leftNeighbour) -> left(leftNeighbour) + widths[leftNeighbour] + gap <= left(right) }
        if (fits) return kept.toSet()
    }
    return setOf(last)
}

/** "Mon", "Tue", … for each reading's date, with today's reading called "Today". */
@Composable
fun rememberChartDayLabels(dates: List<LocalDate>, today: LocalDate = LocalDate.now()): List<String> {
    val todayLabel = stringResource(R.string.chart_today)
    val locale = Locale.getDefault()
    return remember(dates, today, todayLabel, locale) {
        val weekday = DateTimeFormatter.ofPattern("EEE", locale)
        dates.map { if (it == today) todayLabel else it.format(weekday) }
    }
}

/**
 * A metric's recent days as one chart: the user's usual range as a shaded band labelled with its
 * numbers ([usualLabel], e.g. "Your usual 49–78%"), the line drawn oldest → newest in [color],
 * [dayLabels] under it, and the latest point labelled with its value ([latestLabel]). TalkBack hears
 * the whole chart as [summary] — the value, the usual and the trend in one sentence — never the
 * marks. With [drawKey] the line draws in once per app session (Motion.measure, no overshoot);
 * reduce-motion and the calmer theme show it whole and still use the draw-in up, so it never plays
 * on a later ordinary visit. Fewer than two values draws nothing.
 */
@Composable
fun TrendChart(
    values: List<Double>,
    dayLabels: List<String>,
    color: Color,
    summary: String,
    latestLabel: String,
    modifier: Modifier = Modifier,
    usualLow: Double? = null,
    usualHigh: Double? = null,
    usualLabel: String? = null,
    drawKey: String? = null,
    chartHeight: Dp = 96.dp,
) {
    if (values.size < 2) return
    val reducedMotion = rememberReducedMotion()
    // Claimed first, whatever the motion setting: a reduced-motion sight uses the draw-in up.
    val animate = remember(drawKey) { drawKey != null && ChartDrawSession.claim(drawKey) && !reducedMotion }
    val progress = remember(drawKey) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(drawKey) { if (animate) progress.animateTo(1f, Motion.measure()) }

    val colors = MaterialTheme.colorScheme
    val bandFill = color.copy(alpha = 0.14f)
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(color = colors.onSurface)
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = summary }) {
        if (usualLabel != null && usualLow != null && usualHigh != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(width = 16.dp, height = 10.dp)
                        .background(bandFill, RoundedCornerShape(2.dp))
                        .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(usualLabel, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
        Canvas(Modifier.fillMaxWidth().padding(top = Spacing.sm).height(chartHeight)) {
            val dot = 6.dp.toPx()
            val padY = 8.dp.toPx()
            val all = values + listOfNotNull(usualLow, usualHigh)
            val min = all.min()
            val max = all.max()
            val span = (max - min).takeIf { it > 0.0 } ?: 1.0
            val plotH = size.height - padY * 2
            fun y(v: Double) = padY + (plotH - ((v - min) / span).toFloat() * plotH)
            fun x(i: Int) = dot + (size.width - dot * 2) * i / values.lastIndex

            if (usualLow != null && usualHigh != null) {
                val top = y(usualHigh)
                val bottom = y(usualLow)
                drawRect(bandFill, topLeft = Offset(0f, top), size = Size(size.width, bottom - top))
                val edge = color.copy(alpha = 0.35f)
                drawLine(edge, Offset(0f, top), Offset(size.width, top), strokeWidth = 1.dp.toPx())
                drawLine(edge, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = 1.dp.toPx())
            }

            val points = values.mapIndexed { i, v -> Offset(x(i), y(v)) }
            val line = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
            val shown = progress.value.coerceIn(0f, 1f)
            val drawn = if (shown >= 1f) {
                line
            } else {
                Path().also { segment -> PathMeasure().apply { setPath(line, false) }.let { it.getSegment(0f, it.length * shown, segment, true) } }
            }
            // The area under the line follows the line in, so nothing fills ahead of the data.
            val reach = points.first().x + (points.last().x - points.first().x) * shown
            val area = Path().apply {
                addPath(drawn)
                lineTo(reach, size.height)
                lineTo(points.first().x, size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0f))))
            drawPath(drawn, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            // The latest point and its value arrive with the end of the line.
            val reveal = ((shown - 0.85f) / 0.15f).coerceIn(0f, 1f)
            if (reveal > 0f) {
                val last = points.last()
                drawCircle(colors.surface.copy(alpha = reveal), radius = dot, center = last)
                drawCircle(color.copy(alpha = reveal), radius = 4.dp.toPx(), center = last)
                val text = measurer.measure(latestLabel, labelStyle)
                val pad = 4.dp.toPx()
                val w = text.size.width + pad * 2
                val h = text.size.height.toFloat()
                val left = (last.x - dot - Spacing.xs.toPx() - w).coerceAtLeast(0f)
                val above = last.y - dot - h
                val top = if (above >= 0f) above else (last.y + dot).coerceAtMost(size.height - h).coerceAtLeast(0f)
                drawRoundRect(colors.surface.copy(alpha = 0.92f * reveal), Offset(left, top), Size(w, h), CornerRadius(pad, pad))
                drawText(text, topLeft = Offset(left + pad, top), alpha = reveal)
            }
        }
        DayLabels(dayLabels, inset = 6.dp, modifier = Modifier.padding(top = Spacing.xs))
    }
}

/**
 * Day labels under a chart, each centred on its point (the plot is inset by [inset] each side, as
 * the dots are). Labels that would collide are dropped from the oldest end ([visibleLabelIndices]),
 * so the newest day always shows and large text never overlaps.
 */
@Composable
private fun DayLabels(labels: List<String>, inset: Dp, modifier: Modifier = Modifier) {
    if (labels.size < 2) return
    val gapDp = Spacing.sm
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            labels.forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeables.sumOf { it.width }
        val start = inset.toPx()
        val centers = placeables.indices.map { start + (width - start * 2) * it / placeables.lastIndex }
        val keep = visibleLabelIndices(centers, placeables.map { it.width }, width, gapDp.roundToPx())
        val height = placeables.maxOf { it.height }
        layout(width, height) {
            placeables.forEachIndexed { i, placeable ->
                if (i in keep) {
                    val left = (centers[i] - placeable.width / 2f).coerceIn(0f, (width - placeable.width).toFloat().coerceAtLeast(0f))
                    placeable.place(left.toInt(), 0)
                }
            }
        }
    }
}

// Previews use the real screenshot data (spec §1), never the AI images' invented values.

@Preview(name = "Recovery ring — 64% Yellow on the hero", showBackground = true)
@Composable
private fun RecoveryRingPreview() {
    SpartanTheme {
        val hero = heroColors()
        Box(Modifier.background(hero.container).padding(Spacing.xxl)) {
            RecoveryRing(64, zoneOnHeroColor(RecoveryZone.YELLOW), hero.track, playOnce = false, onPlayed = {}, modifier = Modifier.size(132.dp)) {
                Text("64%", style = MaterialTheme.typography.displaySmall, color = hero.content)
            }
        }
    }
}

@Preview(name = "Trend chart — HRV 33 ms, usual 28–38", showBackground = true, widthDp = 360)
@Composable
private fun TrendChartPreview() {
    SpartanTheme {
        val today = LocalDate.of(2026, 10, 23)
        val dates = (6 downTo 0).map { today.minusDays(it.toLong()) }
        Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            TrendChart(
                values = listOf(29.0, 30.0, 31.0, 30.0, 32.0, 31.0, 33.0),
                dayLabels = rememberChartDayLabels(dates, today),
                color = metricAccent(MetricType.HRV_RMSSD),
                summary = "HRV 33 ms, within your usual of 28 to 38 ms, rising over 7 days",
                latestLabel = "33 ms",
                usualLow = 28.0,
                usualHigh = 38.0,
                usualLabel = "Your usual 28–38 ms",
            )
        }
    }
}
