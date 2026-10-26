package com.spartan.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieClipSpec
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.spartan.R
import com.spartan.ui.theme.Radius
import com.spartan.ui.theme.Spacing
import com.spartan.ui.theme.SpartanTheme
import com.spartan.ui.theme.completionGreen
import com.spartan.ui.theme.onCompletionGreen
import com.spartan.ui.theme.rememberReducedMotion
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * What a one-shot state-change animation shows: its first frame, one play-through, or its last
 * frame. The moments — the completion morph when an action is checked (res/raw/completion_morph,
 * see SPARTAN_LOTTIE_USAGE.md) and the verdict ring sweep ([RecoveryRing], drawn in Compose) — play
 * once on the change itself, never on a loop or on every screen visit. They are feedback that
 * something changed, never a medical result.
 */
enum class OneShot { SHOW_START, PLAY_ONCE, SHOW_FINAL }

/**
 * What a one-shot animation shows: its start until the end state is reached, one play-through when
 * the end state arrived while the user was looking, otherwise (a screen visit, reduce-motion) the
 * final frame straight away.
 */
fun oneShotPlayback(endState: Boolean, changedWhileShown: Boolean, reducedMotion: Boolean): OneShot = when {
    !endState -> OneShot.SHOW_START
    changedWhileShown && !reducedMotion -> OneShot.PLAY_ONCE
    else -> OneShot.SHOW_FINAL
}

/** Test hook: the [OneShot] an animation chose, set on its container. Not read by TalkBack. */
val OneShotKey = SemanticsPropertyKey<OneShot>("OneShot")
var SemanticsPropertyReceiver.oneShot: OneShot by OneShotKey

// Layer and group names in res/raw, as Lottie KeyPaths; "**" reaches the unnamed fill or stroke.
internal val MorphCardLayer = arrayOf("Action card")
internal val MorphCircleFill = arrayOf("Check circle", "Circle", "**")
internal val MorphCheckStroke = arrayOf("Check mark", "Check", "**")

// completion_morph.json geometry, in composition units (240 x 160): the check circle is 44 across,
// centred at (52, 80), and overshoots to 110% while it pops in.
private const val MorphWidthUnits = 240f
private const val MorphHeightUnits = 160f
private const val CircleCentreX = 52f
private const val CircleCentreY = 80f
// The 32dp check region shows a window this many units wide, so the 44-unit circle rests at 28dp
// and its 110% overshoot (about 30.8dp) still fits inside the clip.
private const val CheckWindowUnits = 44f * 32f / 28f
private val CheckRegion = 32.dp
private val CheckCircle = 28.dp
// Frames 0-11 of the morph only move the card, which the row draws itself — skip the empty lead-in.
private val CirclePopsIn = LottieClipSpec.Frame(min = 12)

// The card half of the morph: the action card's own radius (20dp) to the Lottie's 28dp end corner,
// over the Lottie's frames 0-36 at 60 fps. The same tokens back MaterialTheme.shapes.large /
// extraLarge, so the two cannot drift.
private val RestCardRadius = Radius.card
private val DoneCardRadius = Radius.extraLarge
private const val MorphMillis = 600

/**
 * The visual inside an action row's 48dp check target. The caller keeps the toggleable 48dp box
 * (Role.Checkbox) and its state description; this adds no semantics beyond [OneShotKey]. Unchecked
 * is an outline circle in onSurfaceVariant — the role M3's Checkbox uses for its unchecked border,
 * 7.77:1 light / 7.90:1 dark on the row's surface (WCAG 1.4.11 asks 3:1 for the only cue of the
 * control). Done is a filled zone-green circle ([completionGreen], 5.33:1 light / 8.98:1 dark) with
 * its check mark ([onCompletionGreen]); checking it while shown pops the filled check in once.
 */
@Composable
fun CompletionCheck(done: Boolean, modifier: Modifier = Modifier) {
    val playback = rememberOneShot(done)
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.completion_morph))
    val animation = animateLottieCompositionAsState(
        composition,
        isPlaying = playback == OneShot.PLAY_ONCE,
        clipSpec = CirclePopsIn,
        iterations = 1,
    )
    val colors = MaterialTheme.colorScheme
    val recolour = rememberLottieDynamicProperties(
        // The row's own card does the corner and tint half of the morph (rememberCompletionMorph).
        rememberLottieDynamicProperty(LottieProperty.TRANSFORM_OPACITY, 0, *MorphCardLayer),
        rememberLottieDynamicProperty(LottieProperty.COLOR, completionGreen().toArgb(), *MorphCircleFill),
        rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, onCompletionGreen().toArgb(), *MorphCheckStroke),
    )
    Box(
        modifier.size(CheckRegion).clipToBounds().semantics { oneShot = playback },
        contentAlignment = Alignment.Center,
    ) {
        if (playback == OneShot.SHOW_START) {
            Box(Modifier.size(CheckCircle).border(2.dp, colors.onSurfaceVariant, CircleShape))
        } else {
            LottieAnimation(
                composition = composition,
                progress = { if (playback == OneShot.PLAY_ONCE) animation.progress else 1f },
                modifier = Modifier.fillMaxSize(),
                dynamicProperties = recolour,
                alignment = CheckCrop,
                contentScale = CheckCrop,
            )
        }
    }
}

/** The card half of the completion morph: an action row's corner radius and container colour. */
data class CompletionMorph(val cornerRadius: Dp, val containerColor: Color)

/**
 * Corner [MaterialTheme.shapes] large -> extraLarge (20 -> 28dp) and container surface -> a faint
 * zone-green tint ([completionContainer]) when [done]. Only a check while shown animates (in step
 * with [CompletionCheck]); the first composition, reduce-motion and un-checking snap straight to
 * the target. The tint is deliberately faint: a done row keeps its full-contrast title and no
 * strikethrough — it reads as done, never as disabled.
 */
@Composable
fun rememberCompletionMorph(done: Boolean): CompletionMorph {
    val colors = MaterialTheme.colorScheme
    val doneContainer = completionContainer()
    val animate = rememberOneShot(done) == OneShot.PLAY_ONCE
    val radius by animateDpAsState(
        if (done) DoneCardRadius else RestCardRadius,
        if (animate) tween(MorphMillis) else snap(),
        label = "completionRadius",
    )
    val container by animateColorAsState(
        if (done) doneContainer else colors.surface,
        if (animate) tween(MorphMillis) else snap(),
        label = "completionContainer",
    )
    return CompletionMorph(radius, container)
}

/**
 * A done action's card: zone green at 8% (light) / 12% (dark) over the surface, opaque. onSurface
 * and onSurfaceVariant keep ≥6.3:1 on it in both themes (TokenContrastTest).
 */
@Composable
fun completionContainer(): Color {
    val surface = MaterialTheme.colorScheme.surface
    return completionGreen().copy(alpha = if (isSystemInDarkTheme()) 0.12f else 0.08f).compositeOver(surface)
}

/**
 * The [OneShot] for an end state that can change while shown (an action's check). The first
 * composition never counts as a change, so revisiting a screen with the action already done shows
 * the final state; each later change is decided once and latched.
 */
@Composable
private fun rememberOneShot(endState: Boolean): OneShot {
    val reducedMotion = rememberReducedMotion()
    val shownBefore = remember { AtomicBoolean(false) }
    return remember(endState) {
        oneShotPlayback(endState, changedWhileShown = shownBefore.getAndSet(true), reducedMotion = reducedMotion)
    }
}

/**
 * Crops completion_morph to its check circle: scales the composition so [CheckWindowUnits] span the
 * canvas, then puts the circle centre at the canvas centre. The rest falls outside the clip.
 */
private object CheckCrop : ContentScale, Alignment {
    override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
        // srcSize is the composition's own pixel size, so one unit is srcSize.width / 240 pixels.
        val scale = dstSize.width / (srcSize.width / MorphWidthUnits * CheckWindowUnits)
        return ScaleFactor(scale, scale)
    }

    override fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset = IntOffset(
        x = (space.width / 2f - size.width * CircleCentreX / MorphWidthUnits).roundToInt(),
        y = (space.height / 2f - size.height * CircleCentreY / MorphHeightUnits).roundToInt(),
    )
}

@Preview(name = "Completion check", showBackground = true)
@Composable
private fun CompletionCheckPreview() {
    SpartanTheme {
        Row(Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            CompletionCheck(done = false)
            CompletionCheck(done = true)
        }
    }
}
