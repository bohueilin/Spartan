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
import com.spartan.ui.components.MorphCardLayer
import com.spartan.ui.components.MorphCheckStroke
import com.spartan.ui.components.MorphCircleFill
import com.spartan.ui.components.OneShot
import com.spartan.ui.components.OneShotKey
import com.spartan.ui.components.completionContainer
import com.spartan.ui.components.rememberCompletionMorph
import com.spartan.ui.theme.SpartanTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
 * The one-shot Lottie moment on the JVM: res/raw/completion_morph parses with its documented
 * timeline, the recolour KeyPaths reach real layers, frames actually draw (native graphics), and
 * the check plays once only on a change the user saw — never on a screen visit or with
 * reduce-motion on. (The verdict ring is Compose; see RecoveryRingTest.) A plain Application keeps
 * app start-up (Hilt, WorkManager) out of UI tests.
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
    }

    @Test
    fun recolourKeyPaths_matchTheShippedLayerNames() {
        mapOf(
            R.raw.completion_morph to listOf(MorphCardLayer, MorphCircleFill, MorphCheckStroke),
        ).forEach { (rawRes, keyPaths) ->
            val drawable = LottieDrawable()
            drawable.setComposition(load(rawRes))
            keyPaths.forEach { path ->
                assertTrue(path.joinToString(" > "), drawable.resolveKeyPath(KeyPath(*path)).isNotEmpty())
            }
        }
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
        var doneContainer = androidx.compose.ui.graphics.Color.Unspecified
        lateinit var morph: CompletionMorph
        lateinit var visited: CompletionMorph
        composeRule.setContent {
            SpartanTheme {
                colors = MaterialTheme.colorScheme
                doneContainer = completionContainer()
                morph = rememberCompletionMorph(done)
                visited = rememberCompletionMorph(true)
            }
        }
        assertEquals(CompletionMorph(20.dp, colors.surface), morph)
        // A done row is a faint green, not the brand mint that read as disabled.
        assertNotEquals(colors.primaryContainer, doneContainer)
        // Already done on a screen visit: the final shape from the first frame, no replay.
        assertEquals(CompletionMorph(28.dp, doneContainer), visited)

        composeRule.mainClock.autoAdvance = false
        done = true
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(300)
        assertTrue("mid-morph radius ${morph.cornerRadius}", morph.cornerRadius > 20.dp && morph.cornerRadius < 28.dp)
        composeRule.mainClock.advanceTimeBy(600)
        assertEquals(CompletionMorph(28.dp, doneContainer), morph)

        done = false // un-checking snaps straight back
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(100)
        assertEquals(CompletionMorph(20.dp, colors.surface), morph)
    }

    @Test
    fun completionMorph_reducedMotion_snapsTheCheck() {
        setAnimatorScale(0f)
        var done by mutableStateOf(false)
        var doneContainer = androidx.compose.ui.graphics.Color.Unspecified
        lateinit var morph: CompletionMorph
        composeRule.setContent {
            SpartanTheme {
                doneContainer = completionContainer()
                morph = rememberCompletionMorph(done)
            }
        }

        composeRule.mainClock.autoAdvance = false
        done = true
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(100) // a tween would still be near 20dp here
        assertEquals(CompletionMorph(28.dp, doneContainer), morph)
    }
}
