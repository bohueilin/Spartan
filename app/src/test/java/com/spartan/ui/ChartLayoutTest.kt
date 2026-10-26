package com.spartan.ui

import com.spartan.ui.components.ChartDrawSession
import com.spartan.ui.components.visibleLabelIndices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The chart's day labels never overlap at any text size, and each chart draws in once per session. */
class ChartLayoutTest {

    private val centers = (0..6).map { 10f + 50f * it } // seven points across a 320px row

    @Test
    fun labelsThatFit_allShow() {
        assertEquals((0..6).toSet(), visibleLabelIndices(centers, List(7) { 30 }, totalWidth = 320, gap = 8))
    }

    @Test
    fun largeText_thinsTheRow_keepingTheNewestDay() {
        val kept = visibleLabelIndices(centers, List(7) { 70 }, totalWidth = 320, gap = 8)
        assertTrue("today always shows", 6 in kept)
        assertTrue("thinned, not overlapped", kept.size < 7)
        val sorted = kept.sorted()
        // Evenly thinned from the newest day: one stride between every kept label.
        assertEquals(1, sorted.zipWithNext { a, b -> b - a }.toSet().size)
        sorted.zipWithNext().forEach { (a, b) ->
            // Each kept label (clamped into the row) ends before the next one starts.
            val rightA = (centers[a] - 35f).coerceIn(0f, 250f) + 70f
            val leftB = (centers[b] - 35f).coerceIn(0f, 250f)
            assertTrue("labels $a and $b overlap", rightA + 8 <= leftB)
        }
    }

    @Test
    fun aLabelWiderThanTheRow_stillShowsTheNewestDay() {
        assertEquals(setOf(6), visibleLabelIndices(centers, List(7) { 400 }, totalWidth = 320, gap = 8))
    }

    @Test
    fun eachChartDrawsInOncePerSession() {
        ChartDrawSession.reset()
        assertTrue(ChartDrawSession.claim("insights-HRV"))
        assertFalse(ChartDrawSession.claim("insights-HRV"))
        assertTrue(ChartDrawSession.claim("insights-Sleep"))
        ChartDrawSession.reset()
    }
}
