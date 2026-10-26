package com.spartan.ui

import com.spartan.ui.components.OneShot
import com.spartan.ui.components.oneShotPlayback
import com.spartan.ui.components.ringFraction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one-shot contract: an animation plays once only on a change the user saw, reduce-motion and
 * screen visits get the final frame, and the verdict ring is driven by the real score.
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

    // --- ringFraction: the verdict ring follows the real score ---

    @Test
    fun theRingFillsToExactlyTheScore() {
        assertEquals(0.64f, ringFraction(64), 0f)
        assertEquals(0f, ringFraction(0), 0f)
        assertEquals(1f, ringFraction(100), 0f)
    }

    @Test
    fun theRingClampsToAPercentage() {
        assertEquals(1f, ringFraction(150), 0f)
        assertEquals(0f, ringFraction(-5), 0f)
    }
}
