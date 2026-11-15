package com.bos.sphere.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-04: a folder opened from Radar must not leave the drawer or the search overlay open over the home. */
class HomeIntentRoutingTest {
    private val known = setOf("Search", "AllApps")

    @Test
    fun HOM_04_aFolderRequestFromRadarClosesTheOverlaysLikeTheHomeButton() {
        val r = HomeIntentRouting.route("f1", null) { it in known }
        assertEquals(HomeIntentRoute.OpenFolder("f1"), r)
        assertTrue(HomeIntentRouting.closesOverlays(r))
    }

    @Test
    fun HOM_04_aFolderRequestWinsOverAnActionInTheSameIntent() {
        assertEquals(HomeIntentRoute.OpenFolder("f1"), HomeIntentRouting.route("f1", "Search") { it in known })
    }

    @Test
    fun HOM_04_aForwardedCornerActionKeepsTheOverlayItAsksFor() {
        val r = HomeIntentRouting.route(null, "Search") { it in known }
        assertEquals(HomeIntentRoute.CornerAction("Search"), r)
        assertFalse(HomeIntentRouting.closesOverlays(r))
    }

    @Test
    fun HOM_04_anUnknownActionOrAPlainLaunchIsTheHomeSignal() {
        val unknown = HomeIntentRouting.route(null, "Nope") { it in known }
        assertEquals(HomeIntentRoute.Home, unknown)
        assertTrue(HomeIntentRouting.closesOverlays(unknown))
        assertEquals(HomeIntentRoute.Home, HomeIntentRouting.route(null, null) { true })
        assertEquals(HomeIntentRoute.Home, HomeIntentRouting.route("", null) { true })
    }
}
