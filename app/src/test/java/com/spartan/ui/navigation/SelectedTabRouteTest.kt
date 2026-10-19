package com.spartan.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The bottom bar lights the tab a screen was opened from. Back stacks mirror real navigation: the
 * graph entry (null route), the start tab, the selected tab, then any pushed screens.
 */
class SelectedTabRouteTest {

    @Test
    fun sharedScreensLightTheTabTheyWereOpenedFrom() {
        assertEquals("you", selectedTabRoute(listOf(null, "today", "you", "connections")))
        assertEquals("today", selectedTabRoute(listOf(null, "today", "connections")))
        assertEquals("today", selectedTabRoute(listOf(null, "today", "detail/{type}")))
        assertEquals("insights", selectedTabRoute(listOf(null, "today", "insights", "detail/{type}")))
    }

    @Test
    fun nestedPushesKeepTheirOriginTab() {
        assertEquals("insights", selectedTabRoute(listOf(null, "today", "insights", "detail/{type}", "editMetric/{id}")))
        assertEquals("progress", selectedTabRoute(listOf(null, "today", "progress", "training", "complete/{type}/{minutes}")))
        assertEquals("you", selectedTabRoute(listOf(null, "today", "you", "connections", "privacy")))
    }

    @Test
    fun tabRoutesSelectThemselves() {
        assertEquals("today", selectedTabRoute(listOf(null, "today")))
        listOf("insights", "progress", "you").forEach { tab ->
            assertEquals(tab, selectedTabRoute(listOf(null, "today", tab)))
        }
    }

    @Test
    fun screenOpenedOutsideAnyTabLightsNone() {
        // An external spartan://connections link lands with no tab beneath it.
        assertNull(selectedTabRoute(listOf(null, "connections")))
    }
}
