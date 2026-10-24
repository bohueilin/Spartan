package com.spartan.ui

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.spartan.R
import com.spartan.ui.components.CompletionCheck
import com.spartan.ui.components.CompletionMorph
import com.spartan.ui.components.MorningReveal
import com.spartan.ui.components.MorphCardLayer
import com.spartan.ui.components.MorphCheckStroke
import com.spartan.ui.components.MorphCircleFill
import com.spartan.ui.components.OneShot
import com.spartan.ui.components.OneShotKey
import com.spartan.ui.components.RevealHorizonFill
import com.spartan.ui.components.RevealRingProgress
import com.spartan.ui.components.RevealRingTrack
import com.spartan.ui.components.RevealSunFill
import com.spartan.ui.components.morningRevealJson
import com.spartan.ui.components.rememberCompletionMorph
import com.spartan.ui.theme.SpartanTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The one-shot Lottie moments on the JVM: the res/raw files parse with their documented timeline,
 * the recolour KeyPaths reach real layers, frames actually draw (native graphics), and each
 * component plays once only on a change the user saw — never on a screen visit or with
 * reduce-motion on. A plain Application keeps app start-up (Hilt, WorkManager) out of UI tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LottieMomentsTest {

    private val composeRule = createComposeRule()

    /**
     * ComponentActivity, which the compose rule launches, is declared by ui-test-manifest — a
     * debugImplementation dependency. Release-variant unit tests (Kover's report runs them) have no
     * such entry, so register it with Robolectric's package manager before the rule starts.
     */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Context>()
            Shadows.shadowOf(app.packageManager).addOrUpdateActivity(
                ActivityInfo().apply {
                    name = ComponentActivity::class.java.name
                    packageName = app.packageName
                },
            )
        }
    }).around(composeRule)

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun restoreAnimatorScale() {
        setAnimatorScale(1f)
    }

    /** Reduce-motion as the system expresses it: animator duration scale 0. */
    private fun setAnimatorScale(scale: Float) {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun load(rawRes: Int): LottieComposition {
        val result = LottieCompositionFactory.fromRawResSync(context, rawRes, null)
        return requireNotNull(result.value) { "res/raw failed to parse: ${result.exception}" }
    }

    private fun readRaw(rawRes: Int): String =
        context.resources.openRawResource(rawRes).bufferedReader().use { it.readText() }

    private fun LottieComposition.markerFrame(name: String): Float =
        requireNotNull(getMarker(name)) { "missing marker $name" }.startFrame

    private fun assertPlayback(expected: OneShot) {
        composeRule.onNode(SemanticsMatcher.keyIsDefined(OneShotKey), useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(OneShotKey, expected))
    }

    /** Draws [composition] at [progress] into a bitmap of its own size, after [setUp] (recolouring). */
    private fun render(composition: LottieComposition, progress: Float, setUp: LottieDrawable.() -> Unit): Bitmap {
        val bounds = composition.bounds
        val bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
        val drawable = LottieDrawable()
        drawable.setComposition(composition)
        drawable.setUp()
        drawable.setBounds(0, 0, bounds.width(), bounds.height())
        drawable.progress = progress
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    /** The pixel at composition-unit coordinates, for a composition [unitWidth] units wide. */
    private fun Bitmap.unitPixel(x: Float, y: Float, unitWidth: Float): Int {
        val pxPerUnit = width / unitWidth
        return getPixel((x * pxPerUnit).toInt(), (y * pxPerUnit).toInt())
    }

    // --- The shipped files ---

    @Test
    fun rawAnimations_parseWithTheDocumentedTimeline() {
        // Lottie ends a composition 0.01 frame before its out point, hence the small delta.
        val morph = load(R.raw.completion_morph)
        assertEquals(60f, morph.frameRate, 0f)
        assertEquals(72f, morph.durationFrames, 0.02f)
        assertEquals(0f, morph.markerFrame("unchecked"), 0f)
        assertEquals(36f, morph.markerFrame("morphed"), 0f)
        assertEquals(48f, morph.markerFrame("checked"), 0f)

        val reveal = load(R.raw.morning_reveal)
        assertEquals(60f, reveal.frameRate, 0f)
        assertEquals(150f, reveal.durationFrames, 0.02f)
        assertEquals(0f, reveal.markerFrame("dawn"), 0f)
        assertEquals(90f, reveal.markerFrame("sun-up"), 0f)
        assertEquals(135f, reveal.markerFrame("verdict-ready"), 0f)
    }

    @Test
    fun recolourKeyPaths_matchTheShippedLayerNames() {
        mapOf(
            R.raw.completion_morph to listOf(MorphCardLayer, MorphCircleFill, MorphCheckStroke),
            R.raw.morning_reveal to listOf(RevealHorizonFill, RevealSunFill, RevealRingTrack, RevealRingProgress),
        ).forEach { (rawRes, keyPaths) ->
            val drawable = LottieDrawable()
            drawable.setComposition(load(rawRes))
            keyPaths.forEach { path ->
                assertTrue(path.joinToString(" > "), drawable.resolveKeyPath(KeyPath(*path)).isNotEmpty())
            }
        }
    }

    @Test
    fun shippedMorningReveal_takesTheRealScore_andStillParses() {
        val raw = readRaw(R.raw.morning_reveal)
        val scored = morningRevealJson(raw, 64)
        assertNotEquals("the shipped ring must carry the 72 example to replace", raw, scored)
        val result = LottieCompositionFactory.fromJsonStringSync(scored, null)
        assertNotNull(result.exception?.toString(), result.value)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real pixels; the default legacy canvas draws nothing
    fun completionMorph_drawsTheRecolouredCheck_onlyOnceItHasPlayed() {
        val morph = load(R.raw.completion_morph)
        fun frame(progress: Float) = render(morph, progress) {
            addValueCallback(KeyPath(*MorphCardLayer), LottieProperty.TRANSFORM_OPACITY, LottieValueCallback(0))
            addValueCallback(KeyPath(*MorphCircleFill), LottieProperty.COLOR, LottieValueCallback(Color.RED))
            addValueCallback(KeyPath(*MorphCheckStroke), LottieProperty.STROKE_COLOR, LottieValueCallback(Color.BLUE))
        }
        val start = frame(0f)
        val end = frame(1f)
        // Composition units (240 wide): circle centred at (52, 80), radius 22; the check's corner at (49, 88).
        assertEquals("circle fill", Color.RED, end.unitPixel(52f, 66f, 240f))
        assertEquals("check stroke, above the circle", Color.BLUE, end.unitPixel(49f, 88f, 240f))
        assertEquals("card layer hidden", 0, Color.alpha(end.unitPixel(200f, 80f, 240f)))
        assertEquals("nothing has popped in at frame 0", 0, Color.alpha(start.unitPixel(52f, 66f, 240f)))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real pixels; the default legacy canvas draws nothing
    fun morningReveal_ringDrawsToTheRealScore() {
        val raw = readRaw(R.raw.morning_reveal)
        fun ringPixel(score: Int, progress: Float): Int {
            val reveal = requireNotNull(LottieCompositionFactory.fromJsonStringSync(morningRevealJson(raw, score), null).value)
            return render(reveal, progress) {
                addValueCallback(KeyPath(*RevealRingTrack), LottieProperty.STROKE_COLOR, LottieValueCallback(Color.BLUE))
                addValueCallback(KeyPath(*RevealRingProgress), LottieProperty.STROKE_COLOR, LottieValueCallback(Color.RED))
            }.unitPixel(215f, 108f, 320f) // the ring's 3 o'clock point: centre (160, 108), radius 55, stroke 8
        }
        assertEquals("a full score closes the ring", Color.RED, ringPixel(100, 1f))
        assertEquals("a zero score draws no arc", Color.BLUE, ringPixel(0, 1f))
        assertEquals("at dawn the arc has not started", Color.BLUE, ringPixel(100, 0f))
    }

    // --- CompletionCheck ---

    @Test
    fun completionCheck_alreadyDoneOnAVisit_showsTheFinalFrame_withoutAutoplay() {
        composeRule.setContent { SpartanTheme { CompletionCheck(done = true) } }
        assertPlayback(OneShot.SHOW_FINAL)
        // A visual inside the caller's 48dp target, never the target itself.
        composeRule.onNode(SemanticsMatcher.keyIsDefined(OneShotKey), useUnmergedTree = true).assertWidthIsEqualTo(32.dp)
    }

    @Test
    fun completionCheck_checkedWhileShown_playsOnce_andUncheckingShowsTheStart() {
        var done by mutableStateOf(false)
        composeRule.setContent { SpartanTheme { CompletionCheck(done = done) } }
        assertPlayback(OneShot.SHOW_START)
        done = true
        assertPlayback(OneShot.PLAY_ONCE)
        done = false
        assertPlayback(OneShot.SHOW_START)
    }

    @Test
    fun completionCheck_reducedMotion_showsTheFinalFrame() {
        setAnimatorScale(0f)
        var done by mutableStateOf(false)
        composeRule.setContent { SpartanTheme { CompletionCheck(done = done) } }
        done = true
        assertPlayback(OneShot.SHOW_FINAL)
    }

    // --- rememberCompletionMorph ---

    @Test
    fun completionMorph_animatesOnlyACheckTheUserSaw() {
        var done by mutableStateOf(false)
        lateinit var colors: ColorScheme
        lateinit var morph: CompletionMorph
        lateinit var visited: CompletionMorph
        composeRule.setContent {
            SpartanTheme {
                colors = MaterialTheme.colorScheme
                morph = rememberCompletionMorph(done)
                visited = rememberCompletionMorph(true)
            }
        }
        assertEquals(CompletionMorph(16.dp, colors.surface), morph)
        // Already done on a screen visit: the final shape from the first frame, no replay.
        assertEquals(CompletionMorph(28.dp, colors.primaryContainer), visited)

        composeRule.mainClock.autoAdvance = false
        done = true
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(300)
        assertTrue("mid-morph radius ${morph.cornerRadius}", morph.cornerRadius > 16.dp && morph.cornerRadius < 28.dp)
        composeRule.mainClock.advanceTimeBy(600)
        assertEquals(CompletionMorph(28.dp, colors.primaryContainer), morph)

        done = false // un-checking snaps straight back
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(100)
        assertEquals(CompletionMorph(16.dp, colors.surface), morph)
    }

    @Test
    fun completionMorph_reducedMotion_snapsTheCheck() {
        setAnimatorScale(0f)
        var done by mutableStateOf(false)
        lateinit var colors: ColorScheme
        lateinit var morph: CompletionMorph
        composeRule.setContent {
            SpartanTheme {
                colors = MaterialTheme.colorScheme
                morph = rememberCompletionMorph(done)
            }
        }

        composeRule.mainClock.autoAdvance = false
        done = true
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(100) // a tween would still be near 16dp here
        assertEquals(CompletionMorph(28.dp, colors.primaryContainer), morph)
    }

    // --- MorningReveal ---

    @Test
    fun morningReveal_firstVerdictOfTheDay_playsOnce_andReportsItOnce() {
        var playOnce by mutableStateOf(true)
        var played = 0
        composeRule.setContent {
            SpartanTheme {
                MorningReveal(64, MaterialTheme.colorScheme.primary, playOnce = playOnce, onPlayed = { played++; playOnce = false })
            }
        }
        // Latched: the caller turning playOnce off after onPlayed neither stops nor repeats it.
        assertPlayback(OneShot.PLAY_ONCE)
        assertEquals(1, played)
    }

    @Test
    fun morningReveal_reducedMotion_showsTheFinalFrame_withoutReportingAPlay() {
        setAnimatorScale(0f)
        var played = 0
        composeRule.setContent {
            SpartanTheme { MorningReveal(64, MaterialTheme.colorScheme.primary, playOnce = true, onPlayed = { played++ }) }
        }
        assertPlayback(OneShot.SHOW_FINAL)
        assertEquals(0, played)
    }

    @Test
    fun morningReveal_alreadySeenToday_showsTheFinalFrame() {
        var played = 0
        composeRule.setContent {
            SpartanTheme { MorningReveal(64, MaterialTheme.colorScheme.primary, playOnce = false, onPlayed = { played++ }) }
        }
        assertPlayback(OneShot.SHOW_FINAL)
        assertEquals(0, played)
    }
}
