package com.bos.sphere

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.RightSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOM-23 (wave 28, homeUnify, navigation and indicator review): what the one pager does at its edges and in edit mode, and how big the one indicator's
 * stops are, through the REAL UI under Robolectric. Not a device run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class HomeUnifyReviewFlowTest : TourBase() {
    private val stores get() = LauncherStores.get(AppSmokeHarness.app)
    private fun key(i: Int) = AppSmokeHarness.appKey("App %02d".format(i))

    private fun start(glance: Boolean = true, edit: (com.bos.sphere.core.data.LauncherPrefs) -> com.bos.sphere.core.data.LauncherPrefs = { it }) {
        AppSmokeHarness.seedApps(*Array(12) { "App %02d".format(it) })
        AppSmokeHarness.configureFresh { edit(it.copy(glanceEnabled = glance, homePages = HomePages.Paginated)) }
        stores.layout.replace(HomeLayout(pages = List(3) { HomePage(keys = listOf(key(it)), id = it + 1L) }, seeded = true))
        launch()
    }

    private fun swipeAt(fromX: Float, toX: Float) {
        rule.onNodeWithTag("home.pager").performTouchInput { val y = height * 0.5f; swipe(Offset(width * fromX, y), Offset(width * toX, y), 300) }; settle()
    }

    @Test fun HOM_23_w28_everyStopOfTheOneIndicatorIsAtLeast48DpToTap() {
        start()
        val dp = AppSmokeHarness.app.resources.displayMetrics.density
        val tags = listOf("pages.glancedot", "pages.dot.0", "pages.dot.1", "pages.dot.2")
        for (t in tags) {
            val b = rule.onNodeWithTag(t, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$t is ${b.width / dp} x ${b.height / dp} dp, under 48", b.width / dp >= 47.5f && b.height / dp >= 47.5f)
        }
    }

    @Test fun HOM_23_w28_withGlanceOffASwipeInTheMiddleOfPageOneNeverOpensComms() {
        start(glance = false)
        assertFalse(launchpadShown()); assertFalse(glanceShown())
        swipeAt(0.3f, 0.8f) // towards the Comms edge, starting well inside the screen
        assertFalse("Comms opens from its screen edge only", launchpadShown())
    }

    @Test fun HOM_23_w28_aSwipeBackFromPageOneInEditModeStaysInEditMode() {
        start()
        swipeAt(0.75f, 0.25f); assertFalse(glanceShown())
        longPressAt(0.5f, 0.8f); assertTrue("a long press on empty space opens the quick sheet", quickSheetShown())
        rule.onNodeWithTag("quick.edit").performScrollTo().performClick(); settle(); assertTrue("edit mode is on", editShown())
        swipeAt(0.3f, 0.8f)
        assertTrue("dragging toward the start edge in edit mode does not leave it", editShown()); assertFalse("and does not go to Glance", glanceShown())
    }

    @Test fun HOM_23_w28_anEdgeSwipeThatOpensTheDockRailOnGlanceDoesNotAlsoPageTheHome() {
        start { it.copy(rightSide = RightSide.DockRail) }
        assertTrue(glanceShown())
        swipeAt(0.985f, 0.4f) // from the right edge (the dock rail's edge)
        assertTrue("the edge swipe belongs to the dock rail, the pager stays on Glance", glanceShown())
    }
}
