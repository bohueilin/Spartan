package com.spartan.ui

import com.spartan.ui.components.OneShot
import com.spartan.ui.components.morningRevealJson
import com.spartan.ui.components.oneShotPlayback
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The one-shot contract: an animation plays once only on a change the user saw, reduce-motion and
 * screen visits get the final frame, and the morning ring is driven by the real score.
 */
class OneShotPlaybackTest {

    // --- oneShotPlayback: the full truth table ---

    @Test
    fun beforeTheEndState_showsTheStart_whateverElseHolds() {
        for (changed in listOf(false, true)) {
            for (reduced in listOf(false, true)) {
                assertEquals(OneShot.SHOW_START, oneShotPlayback(endState = false, changedWhileShown = changed, reducedMotion = reduced))
            }
        }
    }

    @Test
    fun aChangeTheUserSaw_playsOnce() {
        assertEquals(OneShot.PLAY_ONCE, oneShotPlayback(endState = true, changedWhileShown = true, reducedMotion = false))
    }

    @Test
    fun reducedMotionOrAScreenVisit_showsTheFinalFrame() {
        assertEquals(OneShot.SHOW_FINAL, oneShotPlayback(endState = true, changedWhileShown = true, reducedMotion = true))
        assertEquals(OneShot.SHOW_FINAL, oneShotPlayback(endState = true, changedWhileShown = false, reducedMotion = false))
        assertEquals(OneShot.SHOW_FINAL, oneShotPlayback(endState = true, changedWhileShown = false, reducedMotion = true))
    }

    // --- morningRevealJson: the ring follows the real score ---

    /**
     * A trimmed-down morning_reveal.json whose ring trim end animates 0 -> [ringEnd]. The Sun's
     * position, the progress stroke width and the track's own trim also hold 72 — decoys that
     * must never change.
     */
    private fun reveal(ringEnd: Int, ringLayer: String = "Recovery ring") = """
        {"fr":60,"layers":[
          {"nm":"Sun","ks":{"p":{"a":1,"k":[{"e":[160,72,0],"s":[160,196,0],"t":0},{"s":[160,72,0],"t":90}]}}},
          {"nm":"$ringLayer","shapes":[
            {"nm":"Ring track","it":[{"ty":"st","w":{"a":0,"k":8}},{"ty":"tm","e":{"a":1,"k":[{"s":[72],"t":0}]}}]},
            {"nm":"Ring progress","it":[
              {"ty":"el","s":{"a":0,"k":[110,110]}},
              {"ty":"st","w":{"a":0,"k":72}},
              {"ty":"tm","s":{"a":0,"k":0},"e":{"a":1,"k":[{"e":[$ringEnd],"s":[0],"t":60},{"s":[$ringEnd],"t":135},{"s":[$ringEnd],"t":150}]}}
            ]}
          ]}
        ]}
    """.trimIndent()

    private fun parse(json: String) = Json.parseToJsonElement(json)

    @Test
    fun rewritesOnlyTheRingTrimEnd_toTheScore() {
        // Whole-tree equality: the trim end keyframes become 40, every decoy 72 stays as authored.
        assertEquals(parse(reveal(40)), parse(morningRevealJson(reveal(72), 40)))
        assertEquals(parse(reveal(72)), parse(morningRevealJson(reveal(72), 72)))
    }

    @Test
    fun clampsTheScoreToAPercentage() {
        assertEquals(parse(reveal(100)), parse(morningRevealJson(reveal(72), 150)))
        assertEquals(parse(reveal(0)), parse(morningRevealJson(reveal(72), -5)))
    }

    @Test
    fun unexpectedShapes_returnTheInputUnchanged() {
        listOf(
            reveal(72, ringLayer = "Some other ring"), // no ring layer
            reveal(40), // no example value left to replace
            """{"layers":"none"}""",
            """{"layers":[{"nm":"Recovery ring","shapes":{"nm":"Ring progress"}}]}""",
            """{"layers":[{"nm":"Recovery ring","shapes":[{"nm":"Ring progress","it":[{"ty":"tm","e":72}]}]}]}""",
            """[72]""",
            """{not json""",
        ).forEach { raw -> assertSame(raw, morningRevealJson(raw, 50)) }
    }
}
