package com.bos.sphere

import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomeOrder
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.PuckActionsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOM-01, HOM-23, INP-18, CFG-15 (wave 29, drawerRevert): the apps hotspot opens the restored wheel disc (hub with its search button, ring actions)
 * on Glance, on a page and in the right to left order; Back closes it; a ring action still runs. Not a device run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class DrawerRevertW29Test : TourBase() {
    private val stores get() = LauncherStores.get(AppSmokeHarness.app)
    private fun key(i: Int) = AppSmokeHarness.appKey("App %02d".format(i))
    private fun wheelShown() = descShown("Search apps")

    private fun start(order: HomeOrder = HomeOrder.LeftToRight, pages: HomePages = HomePages.Paginated, glance: Boolean = true) {
        AppSmokeHarness.seedApps(*Array(12) { "App %02d".format(it) })
        AppSmokeHarness.configureFresh { it.copy(glanceEnabled = glance, homeOrder = order, homePages = pages) }
        stores.layout.replace(HomeLayout(pages = List(2) { HomePage(keys = listOf(key(it)), id = it + 1L) }, seeded = true))
        launch()
    }

    private fun appsHotspot() = rule.onNodeWithContentDescription("Bottom left hotspot", substring = true)

    @Test fun HOM_01_w29_theAppsHotspotOnGlanceOpensTheWheelDiscAndBackClosesIt() {
        start(); assertTrue(glanceShown())
        appsHotspot().performClick(); settle()
        assertTrue("the restored disc (hub with its search button) is open", wheelShown())
        assertFalse("not the list drawer", drawerShown())
        back(); assertFalse("Back closes the disc", wheelShown())
    }

    @Test fun HOM_01_w29_theAppsHotspotOnAPageOpensTheWheelDisc() {
        start(glance = false); assertFalse(glanceShown())
        rule.onNodeWithContentDescription(CornerActionId.AllApps.label, substring = true).performClick(); settle()
        assertTrue(wheelShown()); assertFalse(drawerShown())
        back(); assertFalse(wheelShown())
    }

    @Test fun HOM_23_w29_inTheRightToLeftOrderTheAppsHotspotStillOpensTheWheelDisc() {
        start(order = HomeOrder.RightToLeft); assertTrue(glanceShown())
        val roles = listOf("Bottom left hotspot", "Bottom right hotspot")
        // the hotspot roles swap in right to left: whichever corner holds the apps role opens the disc
        var opened = false
        for (d in roles) { rule.onNodeWithContentDescription(d, substring = true).performClick(); settle(); if (wheelShown()) { opened = true; back(); break }; if (radarShown()) back() }
        assertTrue("one of the bottom hotspots opens the wheel disc in right to left order", opened)
    }

    @Test fun CFG_15_w29_aPuckRingActionOnTheRestoredDiscStillRuns() {
        start()
        PuckActionsStore.get(AppSmokeHarness.app).set(listOf(CornerActionId.OpenRecents)); settle()
        appsHotspot().performClick(); settle()
        assertTrue(wheelShown())
        rule.onAllNodesWithContentDescription(CornerActionId.OpenRecents.label).onLast().performClick(); settle() // the wheel is drawn last, over the home's own Recents handle
        assertFalse("an internal ring action closes the disc", wheelShown())
        assertTrue("and ran: the Recents panel is open", recentsShown())
        assertEquals(setOf(Surface.Recents), open() - Surface.Home)
    }
}
