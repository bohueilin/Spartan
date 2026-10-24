package com.spartan.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.spartan.domain.model.ReadinessBand
import com.spartan.ui.theme.Radius
import com.spartan.ui.theme.Spacing
import com.spartan.ui.theme.SpartanTheme
import com.spartan.ui.theme.bandColor
import com.spartan.ui.theme.rememberReducedMotion
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * What a one-shot state-change animation shows: its first frame, one play-through, or its last
 * frame. The moments here (res/raw, see SPARTAN_LOTTIE_USAGE.md) — the completion morph when an
 * action is checked, the morning reveal when the verdict is ready — play once on the change itself,
 * never on a loop or on every screen visit. They are feedback that something changed, never a
 * medical result.
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
private const val RingLayer = "Recovery ring"
private const val RingProgressGroup = "Ring progress"
internal val MorphCardLayer = arrayOf("Action card")
internal val MorphCircleFill = arrayOf("Check circle", "Circle", "**")
internal val MorphCheckStroke = arrayOf("Check mark", "Check", "**")
internal val RevealHorizonFill = arrayOf("Horizon", "Horizon line", "**")
internal val RevealSunFill = arrayOf("Sun", "Sun disc", "**")
internal val RevealRingTrack = arrayOf(RingLayer, "Ring track", "**")
internal val RevealRingProgress = arrayOf(RingLayer, RingProgressGroup, "**")

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

// The card half of the morph, matching the Lottie's own 16 -> 28 corner over frames 0-36 at 60 fps.
// The same tokens back MaterialTheme.shapes.medium / extraLarge, so the two cannot drift.
private val RestCardRadius = Radius.medium
private val DoneCardRadius = Radius.extraLarge
private const val MorphMillis = 600

// morning_reveal.json is 320 x 240; its ring is authored at this example score.
private const val RevealAspectRatio = 320f / 240f
private const val ExampleRingEnd = 72.0

/**
 * The visual inside an action row's 48dp check target. The caller keeps the toggleable 48dp box
 * (Role.Checkbox) and its state description; this adds no semantics beyond [OneShotKey]. Unchecked
 * is an outline circle in onSurfaceVariant — the role M3's Checkbox uses for its unchecked border,
 * 7.64:1 light / 7.90:1 dark on the row's surface (outline was 2.35 / 2.06:1, under WCAG 1.4.11's
 * 3:1 for the only cue of the control). Checking it while shown pops in the filled check once.
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
        rememberLottieDynamicProperty(LottieProperty.COLOR, colors.primary.toArgb(), *MorphCircleFill),
        rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, colors.onPrimary.toArgb(), *MorphCheckStroke),
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
 * Corner [MaterialTheme.shapes] medium -> extraLarge and container surface -> primaryContainer
 * when [done]. Only a check while shown animates (in step with [CompletionCheck]); the first
 * composition, reduce-motion and un-checking snap straight to the target.
 */
@Composable
fun rememberCompletionMorph(done: Boolean): CompletionMorph {
    val colors = MaterialTheme.colorScheme
    val animate = rememberOneShot(done) == OneShot.PLAY_ONCE
    val radius by animateDpAsState(
        if (done) DoneCardRadius else RestCardRadius,
        if (animate) tween(MorphMillis) else snap(),
        label = "completionRadius",
    )
    val container by animateColorAsState(
        if (done) colors.primaryContainer else colors.surface,
        if (animate) tween(MorphMillis) else snap(),
        label = "completionContainer",
    )
    return CompletionMorph(radius, container)
}

/**
 * The morning verdict visual: the sun rises, then the ring draws to the real [recoveryScore].
 * Plays once when [playOnce] and motion is allowed, calling [onPlayed] as it starts so leaving
 * mid-way never replays it; otherwise shows the final frame. Decorative to TalkBack — the caller
 * states colour, word and number in text.
 */
@Composable
fun MorningReveal(
    recoveryScore: Int,
    ringColor: Color,
    playOnce: Boolean,
    onPlayed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    // Decided once per visit: the caller turning playOnce off after onPlayed must not cut it short.
    val playback = remember { oneShotPlayback(endState = true, changedWhileShown = playOnce, reducedMotion = reducedMotion) }
    val latestOnPlayed by rememberUpdatedState(onPlayed)
    LaunchedEffect(Unit) { if (playback == OneShot.PLAY_ONCE) latestOnPlayed() }

    val resources = LocalContext.current.resources
    val raw = remember { resources.openRawResource(R.raw.morning_reveal).bufferedReader().use { it.readText() } }
    val score = recoveryScore.coerceIn(0, 100)
    val json = remember(raw, score) { morningRevealJson(raw, score) }
    val composition by rememberLottieComposition(LottieCompositionSpec.JsonString(json), cacheKey = "morning_reveal_$score")
    val progress by animateLottieCompositionAsState(composition, isPlaying = playback == OneShot.PLAY_ONCE, iterations = 1)
    val colors = MaterialTheme.colorScheme
    val recolour = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(LottieProperty.COLOR, colors.outline.toArgb(), *RevealHorizonFill),
        rememberLottieDynamicProperty(LottieProperty.COLOR, colors.tertiary.toArgb(), *RevealSunFill),
        rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, colors.surfaceContainerHighest.toArgb(), *RevealRingTrack),
        rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, ringColor.toArgb(), *RevealRingProgress),
    )
    LottieAnimation(
        composition = composition,
        progress = { if (playback == OneShot.PLAY_ONCE) progress else 1f },
        modifier = modifier.aspectRatio(RevealAspectRatio).clearAndSetSemantics { oneShot = playback },
        dynamicProperties = recolour,
    )
}

/**
 * The morning-reveal JSON with its example 72% ring replaced by the real score: every 72 among the
 * keyframe values of the "Recovery ring" -> "Ring progress" trim path end becomes
 * [recoveryScore] (clamped to 0-100); everything else is left as authored. Returns [raw] itself
 * when that path or those values are missing, or the text is not a JSON object.
 */
internal fun morningRevealJson(raw: String, recoveryScore: Int): String {
    val root = try {
        Json.parseToJsonElement(raw) as? JsonObject
    } catch (e: SerializationException) {
        null
    } ?: return raw
    val score = JsonPrimitive(recoveryScore.coerceIn(0, 100))
    var rewrote = false
    fun scored(value: JsonElement): JsonElement =
        if ((value as? JsonPrimitive)?.doubleOrNull == ExampleRingEnd) score.also { rewrote = true } else value

    // layers[nm = Recovery ring] -> shapes[nm = Ring progress] -> it[ty = tm] -> e.k[] keyframes' s / e.
    val updated = root.mapItems("layers", where = { it.text("nm") == RingLayer }) { layer ->
        layer.mapItems("shapes", where = { it.text("nm") == RingProgressGroup }) { group ->
            group.mapItems("it", where = { it.text("ty") == "tm" && it["e"] is JsonObject }) { trim ->
                val end = (trim.getValue("e") as JsonObject).mapItems("k") { keyframe ->
                    JsonObject(
                        keyframe.mapValues { (key, values) ->
                            if ((key == "s" || key == "e") && values is JsonArray) JsonArray(values.map(::scored)) else values
                        },
                    )
                }
                JsonObject(trim + ("e" to end))
            }
        }
    }
    return if (rewrote) updated.toString() else raw
}

/** This object with the array at [key] rebuilt: object items matching [where] go through [transform]. */
private fun JsonObject.mapItems(
    key: String,
    where: (JsonObject) -> Boolean = { true },
    transform: (JsonObject) -> JsonObject,
): JsonObject {
    val items = this[key] as? JsonArray ?: return this
    return JsonObject(this + (key to JsonArray(items.map { if (it is JsonObject && where(it)) transform(it) else it })))
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

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

@Preview(name = "Morning reveal", showBackground = true, widthDp = 360)
@Composable
private fun MorningRevealPreview() {
    SpartanTheme {
        Row(
            Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            MorningReveal(28, bandColor(ReadinessBand.REST), playOnce = false, onPlayed = {}, modifier = Modifier.weight(1f))
            MorningReveal(86, bandColor(ReadinessBand.PRIMED), playOnce = false, onPlayed = {}, modifier = Modifier.weight(1f))
        }
    }
}
