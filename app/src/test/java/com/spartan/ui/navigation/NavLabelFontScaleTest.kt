package com.spartan.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nav labels follow the user's text size up to the 1.3x cap, then shrink to fit their item, never
 * below the default size. Widths model "Progress" in Inter 13sp wght 600 (about 4.32em, so 56dp
 * at 1.0x); 66dp is what a bar item leaves the label on a 320dp window.
 */
class NavLabelFontScaleTest {

    private val progressAt: (Float) -> Int = { scale -> (56.2f * scale).toInt() }

    @Test
    fun aDefaultOrSmallerScale_isTheUsersOwn_andNeedsNoMeasuring() {
        val neverMeasured: (Float) -> Int = { error("measured at $it") }
        assertEquals(1f, navLabelFontScale(1f, 66, neverMeasured))
        assertEquals(0.85f, navLabelFontScale(0.85f, 10, neverMeasured))
    }

    @Test
    fun withRoom_theLabelFollowsTheUser_upToTheCap() {
        assertEquals(1.15f, navLabelFontScale(1.15f, 200, progressAt))
        assertEquals(NAV_LABEL_MAX_FONT_SCALE, navLabelFontScale(2f, 200, progressAt))
        // A rail item is unbounded in width: the cap applies, never more.
        assertEquals(NAV_LABEL_MAX_FONT_SCALE, navLabelFontScale(3f, Int.MAX_VALUE, progressAt))
    }

    @Test
    fun aNarrowItem_takesTheLargestScaleThatFits_onOneLine() {
        // At 200% on a 320dp window: 1.3x would need 73dp; 1.15x needs 64dp and fits in 66.
        val scale = navLabelFontScale(2f, 66, progressAt)
        assertEquals(1.15f, scale, 1e-4f)
        assertTrue(progressAt(scale) <= 66)
        assertTrue(progressAt(scale + NAV_LABEL_SCALE_STEP) > 66)
    }

    @Test
    fun whenNothingAboveTheDefaultFits_theLabelStaysAtTheDefaultSize() {
        val measured = mutableListOf<Float>()
        val scale = navLabelFontScale(2f, 40) { measured += it; progressAt(it) }
        // Last resort: the default size (then ellipsized), never smaller.
        assertEquals(1f, scale)
        assertTrue(measured.all { it in 1f..NAV_LABEL_MAX_FONT_SCALE })
        assertTrue(measured.zipWithNext().all { (a, b) -> a > b })
    }

    @Test
    fun justAboveTheDefault_isTriedBeforeFallingBack() {
        assertEquals(1.02f, navLabelFontScale(1.02f, 58, progressAt))
        assertEquals(1f, navLabelFontScale(1.02f, 56, progressAt))
    }
}
