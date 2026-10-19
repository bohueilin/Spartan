package com.spartan.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spartan.R
import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.MetricType
import com.spartan.ui.theme.Radius
import com.spartan.ui.theme.Spacing
import com.spartan.ui.theme.SpartanBands

/** Page gutter shared by every screen, so edges line up when switching tabs. */
val PageGutter = Spacing.xxl

/**
 * The one card. Light mode lifts it with a soft, wide shadow (depth without lines); dark mode,
 * where shadows vanish, uses a lifted surface plus a hairline instead.
 */
@Composable
fun SpartanCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.xl),
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(Radius.card)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (dark) Modifier
                else Modifier.shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.10f)),
            )
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** Top-of-tab title block: a confident headline, an optional supporting line, optional trailing slot. */
@Composable
fun ScreenTitle(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.xxl, bottom = Spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
        }
        trailing?.invoke()
    }
}

/** Section title with an optional trailing text action ("See all"). */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = Spacing.section, bottom = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** How a status should read: good, neutral, worth watching, or needs attention. */
enum class Tone { POSITIVE, NEUTRAL, CAUTION, ALERT, INFO }

/** Theme-aware tone colours, reusing the contrast-audited readiness band palette. */
@Composable
fun toneColor(tone: Tone): Color {
    val dark = isSystemInDarkTheme()
    return when (tone) {
        Tone.POSITIVE -> if (dark) SpartanBands.primedDark else SpartanBands.primedLight
        Tone.CAUTION -> if (dark) SpartanBands.easyDark else SpartanBands.easyLight
        Tone.ALERT -> if (dark) SpartanBands.restDark else SpartanBands.restLight
        Tone.INFO -> MaterialTheme.colorScheme.primary
        Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** A compact status label on a tinted pill. Text carries the meaning, colour only reinforces it. */
@Composable
fun StatusPill(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val color = toneColor(tone)
    Surface(shape = CircleShape, color = color.copy(alpha = 0.14f), modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** An icon on a softly tinted rounded square — the leading visual for rows and cards. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(Radius.tile)).background(tint.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** A tappable settings-style row: leading tile, title, optional subtitle, chevron. */
@Composable
fun ListRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(icon, MaterialTheme.colorScheme.onSurface, size = 40.dp)
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** The single primary call to action: full-width, 52dp, pill-shaped. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 52.dp), shape = CircleShape) {
        icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(Spacing.sm)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Secondary action: same footprint as [PrimaryButton], outlined. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(Spacing.sm)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Every pushed (non-tab) screen: a quiet back arrow, the page title set large in the content, a
 * scrolling body, and an optional sticky bottom bar for the page's one primary action — the
 * pattern travel apps use for "Reserve". Pages never draw their own title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (bottomBar != null) {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Box(Modifier.navigationBarsPadding().padding(horizontal = PageGutter, vertical = Spacing.md)) { bottomBar() }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 600.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = PageGutter, end = PageGutter, bottom = Spacing.section),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item(key = "page-title") {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = Spacing.xs).semantics { heading() },
                    )
                }
                content()
            }
        }
    }
}

/** A [DetailScaffold] whose body is one simple column — for forms and short settings pages. */
@Composable
fun DetailPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DetailScaffold(title, onBack) {
        item { Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), content = content) }
    }
}

/**
 * A trend line drawn oldest → newest with a gradient area fill, an optional shaded band for the
 * user's personal normal range, and a dot on the newest value. Decorative to TalkBack — callers
 * state the takeaway in text.
 */
@Composable
fun Sparkline(
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    normalLow: Double? = null,
    normalHigh: Double? = null,
    height: Dp = 56.dp,
) {
    if (values.size < 2) return
    val bandColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val dotRing = MaterialTheme.colorScheme.surface
    Canvas(modifier.fillMaxWidth().height(height).clearAndSetSemantics {}) {
        val all = values + listOfNotNull(normalLow, normalHigh)
        val min = all.min()
        val max = all.max()
        val span = (max - min).takeIf { it > 0.0 } ?: 1.0
        val pad = 6.dp.toPx()
        val h = size.height - pad * 2
        fun y(v: Double) = pad + (h - ((v - min) / span).toFloat() * h)
        if (normalLow != null && normalHigh != null) {
            drawRect(bandColor, topLeft = Offset(0f, y(normalHigh)), size = androidx.compose.ui.geometry.Size(size.width, y(normalLow) - y(normalHigh)))
        }
        val points = values.mapIndexed { i, v -> Offset(size.width * i / values.lastIndex, y(v)) }
        val line = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        val area = Path().apply {
            addPath(line)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(dotRing, radius = 6.dp.toPx(), center = points.last())
        drawCircle(color, radius = 4.dp.toPx(), center = points.last())
    }
}

/** The leading icon for an activity, by what kind of activity it is. */
fun categoryIcon(category: ActivityCategory): ImageVector = when (category) {
    ActivityCategory.RECOVERY -> Icons.Outlined.Spa
    ActivityCategory.MOVEMENT, ActivityCategory.ZONE2 -> Icons.AutoMirrored.Outlined.DirectionsWalk
    ActivityCategory.STRENGTH -> Icons.Outlined.FitnessCenter
    ActivityCategory.MOBILITY -> Icons.Outlined.SelfImprovement
    ActivityCategory.BREATHWORK -> Icons.Outlined.Air
    ActivityCategory.SLEEP -> Icons.Outlined.Bedtime
    ActivityCategory.HYDRATION -> Icons.Outlined.WaterDrop
    ActivityCategory.NUTRITION -> Icons.Outlined.Restaurant
    ActivityCategory.MINDSET -> Icons.Outlined.Psychology
}

/** The leading icon for a metric. */
fun metricIcon(metric: MetricType): ImageVector = when (metric) {
    MetricType.RECOVERY_SCORE -> Icons.Outlined.FavoriteBorder
    MetricType.HRV_RMSSD -> Icons.Outlined.MonitorHeart
    MetricType.RESTING_HEART_RATE -> Icons.Outlined.MonitorHeart
    MetricType.SLEEP_PERFORMANCE, MetricType.SLEEP_DURATION, MetricType.SLEEP_DEBT -> Icons.Outlined.Bedtime
    MetricType.RESPIRATORY_RATE -> Icons.Outlined.Air
    MetricType.DAY_STRAIN -> Icons.Outlined.Bolt
    MetricType.ENERGY_KCAL -> Icons.Outlined.LocalFireDepartment
    MetricType.EXERCISE_MINUTES, MetricType.STRENGTH_SESSIONS -> Icons.Outlined.Timer
    MetricType.WEIGHT, MetricType.WAIST_CIRCUMFERENCE, MetricType.BMI, MetricType.WAIST_TO_HEIGHT_RATIO -> Icons.Outlined.Straighten
    else -> Icons.Outlined.Bloodtype
}
