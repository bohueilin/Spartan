package com.spartan.domain

import com.spartan.domain.engine.ReminderEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {
    private val engine = ReminderEngine()

    @Test
    fun defaultQuietHours_wrapAroundMidnight() {
        // Default window 22:00 -> 07:00.
        assertTrue(engine.isQuietHours(23 * 60))      // 23:00 quiet
        assertTrue(engine.isQuietHours(2 * 60))       // 02:00 quiet
        assertTrue(engine.isQuietHours(6 * 60 + 59))  // 06:59 quiet
        assertFalse(engine.isQuietHours(7 * 60))      // 07:00 awake
        assertFalse(engine.isQuietHours(12 * 60))     // noon awake
        assertFalse(engine.isQuietHours(21 * 60 + 59))// 21:59 awake
    }

    @Test
    fun nonWrappingWindow_isHandled() {
        // Window 13:00 -> 14:00.
        assertTrue(engine.isQuietHours(13 * 60 + 30, 13 * 60, 14 * 60))
        assertFalse(engine.isQuietHours(12 * 60, 13 * 60, 14 * 60))
    }
}
