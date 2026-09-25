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
        assertEquals("settings", selectedTabRoute(listOf(null, "today", "settings", "connections")))
        assertEquals("today", selectedTabRoute(listOf(null, "today", "connections")))
        assertEquals("today", selectedTabRoute(listOf(null, "today", "detail/{type}")))
        assertEquals("plan", selectedTabRoute(listOf(null, "today", "plan", "detail/{type}")))
    }

    @Test
    fun nestedPushesKeepTheirOriginTab() {
        assertEquals("metrics", selectedTabRoute(listOf(null, "today", "metrics", "detail/{type}", "editMetric/{id}")))
        assertEquals("plan", selectedTabRoute(listOf(null, "today", "plan", "complete/{type}/{minutes}")))
        assertEquals("settings", selectedTabRoute(listOf(null, "today", "settings", "connections", "privacy")))
    }

    @Test
    fun tabRoutesSelectThemselves() {
        assertEquals("today", selectedTabRoute(listOf(null, "today")))
        listOf("metrics", "plan", "review", "settings").forEach { tab ->
            assertEquals(tab, selectedTabRoute(listOf(null, "today", tab)))
        }
    }

    @Test
    fun screenOpenedOutsideAnyTabLightsNone() {
        // An external spartan://connections link lands with no tab beneath it.
        assertNull(selectedTabRoute(listOf(null, "connections")))
    }
}
