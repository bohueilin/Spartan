package com.spartan.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spartan.R
import com.spartan.domain.model.ActivityCategory
import com.spartan.domain.model.MetricType
import com.spartan.ui.theme.MinTouchTarget
import com.spartan.ui.theme.Radius
import com.spartan.ui.theme.Spacing
import com.spartan.ui.theme.SpartanBands
import com.spartan.ui.theme.heroColors

/** Page gutter shared by every screen, so edges line up when switching tabs. */
val PageGutter = Spacing.xxl

/**
 * The one card: flat (0dp, no stacked shadows) with a hairline outline, in both themes — the hero
 * ([HeroCard]) is the only filled, emphasised surface on a screen. Metric and action cards share
 * the card radius (20dp).
 */
@Composable
fun SpartanCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.xl),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.card)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/**
 * The one hero per screen: a filled deep-teal surface ([heroColors]) at the hero radius (28dp),
 * flat. Text inside defaults to the hero's content colour (white, 8.0:1); use
 * [HeroColors.contentVariant] for secondary lines and zoneOnHeroColor for the zone.
 */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.xxl),
    content: @Composable ColumnScope.() -> Unit,
) {
    val hero = heroColors()
    val shape = RoundedCornerShape(Radius.hero)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier),
        shape = shape,
        color = hero.container,
        contentColor = hero.content,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/**
 * The full general-wellness disclaimer: what Spartan is, what it is not, and when to talk to a
 * clinician. Shown once, in You → About — every other screen carries no footer, and Today keeps
 * only its short in-context line. One string, so it never drifts.
 */
@Composable
fun WellnessFooter(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.wellness_disclaimer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = Spacing.xl),
    )
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

/**
 * A compact status label on a tinted pill. Text carries the meaning, colour only reinforces it.
 * Long labels ("Scheduled for 5:30 PM (sample calendar)") and large font sizes wrap rather than
 * clip, so the shape is shapes.small: pill-like on one line, a soft rectangle on two.
 */
@Composable
fun StatusPill(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val color = toneColor(tone)
    Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = 0.14f), modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
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

/**
 * A tappable settings-style row: leading tile, title, optional subtitle, chevron. [onClickLabel]
 * names the destination for TalkBack ("Open Recovery") when the title alone does not.
 */
@Composable
fun ListRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    showDivider: Boolean = true,
    onClickLabel: String? = null,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = onClickLabel, onClick = onClick).heightIn(min = 64.dp).padding(vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(icon, MaterialTheme.colorScheme.onSurface, size = 40.dp)
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                // Wraps, never ellipsizes: goal state and progress must stay readable at 200% font.
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * The single primary call to action: full-width, 52dp, pill-shaped. Its label is centred, so a
 * label that wraps at large text stays balanced. [onHero] inverts it (hero content on the hero
 * container) for the one action that sits on a [HeroCard] — the standard deep teal would vanish on
 * the deep-teal hero. 8.0:1 / 9.0:1 either way (TokenContrastTest).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onHero: Boolean = false,
) {
    val hero = heroColors()
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = CircleShape,
        colors = if (onHero) ButtonDefaults.buttonColors(containerColor = hero.content, contentColor = hero.container) else ButtonDefaults.buttonColors(),
    ) {
        icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(Spacing.sm)) }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** A quiet action on the hero: a text button in the hero's content colour, at least 48dp tall. */
@Composable
fun HeroTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinTouchTarget),
        colors = ButtonDefaults.textButtonColors(contentColor = heroColors().content),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
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
