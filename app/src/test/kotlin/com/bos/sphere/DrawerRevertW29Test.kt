package com.bos.sphere

import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
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
 * HOM-01, HOM-23, INP-18, CFG-15 (wave 29, drawerRevert): the apps hotspot opens the restored wheel disc (hub with its Back button, ring actions)
 * on Glance, on a page and in the right to left order; Back closes it; a ring action still runs. Not a device run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class DrawerRevertW29Test : TourBase() {
    private val stores get() = LauncherStores.get(AppSmokeHarness.app)
    private fun appKeyOf(i: Int) = AppSmokeHarness.appKey("App %02d".format(i))
    private fun wheelShown() = descShown("Back")

    private fun start(order: HomeOrder = HomeOrder.LeftToRight, pages: HomePages = HomePages.Paginated, glance: Boolean = true) {
        AppSmokeHarness.seedApps(*Array(12) { "App %02d".format(it) })
        AppSmokeHarness.configureFresh { it.copy(glanceEnabled = glance, homeOrder = order, homePages = pages) }
        stores.layout.replace(HomeLayout(pages = List(2) { HomePage(keys = listOf(appKeyOf(it)), id = it + 1L) }, seeded = true))
        launch()
    }

    private fun appsHotspot() = rule.onNodeWithContentDescription("All apps", substring = true)

    @Test fun HOM_01_w29_theAppsHotspotOnGlanceOpensTheWheelDiscAndBackClosesIt() {
        start(); assertTrue(glanceShown())
        appsHotspot().performClick(); settle()
        assertTrue("the restored disc (hub with its Back button) is open", wheelShown())
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
        val roles = listOf("All apps", "Bottom right hotspot")
        // the hotspot roles swap in right to left: whichever corner holds the apps role opens the disc
        var opened = false
        for (d in roles) { rule.onNodeWithContentDescription(d, substring = true).performClick(); settle(); if (wheelShown()) { opened = true; back(); break }; if (radarShown()) back() }
        assertTrue("one of the bottom hotspots opens the wheel disc in right to left order", opened)
    }

    /** The disc fans out of the corner whose hotspot was tapped (the Back button, the hub's centre, sits on that side), in both orders. */
    private fun openedFromTheTappedSide(order: HomeOrder) {
        start(order = order); assertTrue(glanceShown())
        val width = rule.onRoot().fetchSemanticsNode().boundsInRoot.width
        for (d in listOf("All apps", "Bottom right hotspot")) {
            val spot = rule.onNodeWithContentDescription(d, substring = true)
            val spotX = spot.fetchSemanticsNode().boundsInRoot.center.x
            spot.performClick(); settle()
            if (!wheelShown()) { if (radarShown()) back(); continue } // the other bottom hotspot holds a different role
            val hubX = rule.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot.center.x
            assertEquals("$d ($order): the hub sits on the tapped side", spotX < width / 2, hubX < width / 2)
            back(); return
        }
        throw AssertionError("no bottom hotspot opened the wheel disc in $order")
    }

    @Test fun HOM_23_w29_theDiscFansOutOfTheTappedCornerLeftToRight() = openedFromTheTappedSide(HomeOrder.LeftToRight)
    @Test fun HOM_23_w29_theDiscFansOutOfTheTappedCornerRightToLeft() = openedFromTheTappedSide(HomeOrder.RightToLeft)

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_01_w29_theAppsHotspotOpensTheWheelDiscInLandscapeAndBackClosesIt() {
        start(); assertTrue(glanceShown())
        var opened = false
        for (d in listOf("All apps", "Bottom right hotspot", "Top left hotspot", "Top right hotspot")) {
            if (rule.onAllNodesWithContentDescription(d, substring = true).fetchSemanticsNodes().isEmpty()) continue
            rule.onNodeWithContentDescription(d, substring = true).performClick(); settle()
            if (wheelShown()) { opened = true; break }
            back()
        }
        assertTrue("one hotspot opens the disc in landscape", opened); assertFalse(drawerShown())
        val hub = rule.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("the hub's Back button is fully on screen: $hub in $root", hub.left >= root.left && hub.top >= root.top && hub.right <= root.right && hub.bottom <= root.bottom)
        back(); assertFalse(wheelShown())
    }

    @Test fun INP_18_w29_theOpenPuckChordOpensTheWheelDiscAndEscapeClosesIt() {
        start(); assertTrue(glanceShown())
        key(android.view.KeyEvent.KEYCODE_P, ctrl = true)
        assertTrue("Ctrl+P opens the restored disc", wheelShown())
        key(ESC); settle(); assertFalse("Esc closes it", wheelShown())
    }

    @Test fun INP_18_w29_theOpenDrawerChordStillOpensTheListNotTheDisc() {
        start(); assertTrue(glanceShown())
        key(android.view.KeyEvent.KEYCODE_D, ctrl = true)
        assertTrue("Ctrl+D opens the list drawer", drawerShown()); assertFalse(wheelShown())
    }

    @Test fun CFG_15_w29_aPuckRingActionOnTheRestoredDiscStillRuns() {
        start()
        PuckActionsStore.get(AppSmokeHarness.app).set(0, com.bos.sphere.core.data.PuckSlot.Action(CornerActionId.OpenRecents)); settle()
        appsHotspot().performClick(); settle()
        assertTrue(wheelShown())
        rule.onAllNodesWithContentDescription(CornerActionId.OpenRecents.label).onLast().performClick(); settle() // the wheel is drawn last, over the home's own Recents handle
        assertFalse("an internal ring action closes the disc", wheelShown())
        assertTrue("and ran: the Recents panel is open", recentsShown())
        assertEquals(setOf(Surface.Recents), open() - Surface.Home)
    }
}
