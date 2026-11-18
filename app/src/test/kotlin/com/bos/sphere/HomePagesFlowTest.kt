package com.bos.sphere

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.onRoot
import com.bos.sphere.core.data.HomeOrder
import com.bos.sphere.core.data.HomeScroll
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.feature.grid.LauncherInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * HOM-01, HOM-22, HOM-23 (wave 26): the whole home model through the REAL UI of the app under Robolectric: Glance first, "Add page" from the long-press
 * quick sheet and from Ground Control (the owner's build 54 bug: no way to add a page after Glance), the one-time Scroll or Paginated choice, reaching the
 * page by a swipe or the dots, Glance off, and the right to left order. Not a device run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class HomePagesFlowTest : TourBase() {
    private val stores get() = LauncherStores.get(AppSmokeHarness.app)
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private fun startOnGlance(edit: (com.bos.sphere.core.data.LauncherPrefs) -> com.bos.sphere.core.data.LauncherPrefs = { it }) {
        AppSmokeHarness.seedApps(*Array(30) { "App %02d".format(it) })
        AppSmokeHarness.configureFresh(edit)
        launch()
    }

    private fun shoot(name: String) {
        settle(400)
        // Best effort: the whole app keeps animating (ambience, handles), so the compose window capture may never settle; the surfaces are also shot standalone in feature-grid.
        runCatching {
            rule.mainClock.autoAdvance = true
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        rule.mainClock.autoAdvance = false
    }

    private fun openQuickSheetOnGlance() { longPressAt(0.5f, 0.3f); assertTrue("a long press on Glance opens the quick sheet", quickSheetShown()) }
    private fun pageCount() = stores.layout.layout.value.pages.size
    private fun swipeForward(rtl: Boolean = false) {
        // away from the edges: a drag that starts at an edge belongs to the Launchpad and the Recents panel
        rule.onRoot().performTouchInput { val y = height * 0.5f; val a = width * 0.75f; val b = width * 0.25f; swipe(Offset(if (rtl) b else a, y), Offset(if (rtl) a else b, y), 300) }; settle()
    }

    @Test fun HOM_01_ownerBug54_fromGlanceTheQuickSheetAddsPageOneAfterGlanceInTheChosenModeAndItCanBeReached() {
        startOnGlance()
        assertTrue("home starts on Glance", glanceShown())
        shoot("w26-glance")
        openQuickSheetOnGlance()
        rule.onNodeWithTag("quick.addpage").performScrollTo().performClick(); settle()
        // the first time the add-page flow asks Scroll or Paginated, once
        assertFalse("nothing is chosen yet", stores.prefs.prefs.value.pagesModeChosen)
        rule.onNodeWithTag("addpage.paginated").performClick(); settle()
        val p = stores.prefs.prefs.value
        assertTrue("the choice is persisted", p.pagesModeChosen); assertEquals(HomeScroll.Paged, p.homeScroll)
        assertTrue("page 1 exists after Glance", pageCount() >= 1)
        assertFalse("home moved to the new page", glanceShown())
        assertTrue("the page indicator counts Glance as the first stop", tagShown("pages.glancedot"))
        shoot("w26-paginated-page1")
        // back to Glance by the first dot, then forward by a swipe in the configured order (left to right: the finger moves left)
        rule.onNodeWithTag("pages.glancedot", useUnmergedTree = true).performClick(); settle()
        assertTrue(glanceShown())
        swipeForward(); assertFalse("a forward swipe from Glance reaches the page", glanceShown())
        check("pages after swipe", Surface.Home)
    }

    @Test fun HOM_01_aSecondAddPageFromTheSheetAsksNothingAndAddsAnotherPageAfterTheCurrentOne() {
        startOnGlance { it.copy(pagesModeChosen = true, homeScroll = HomeScroll.Paged) }
        stores.layout.replace(com.bos.sphere.core.data.HomeLayout(pages = listOf(com.bos.sphere.core.data.HomePage(keys = listOf(AppSmokeHarness.appKey("App 00")), id = 1L)), seeded = true)); settle()
        openQuickSheetOnGlance()
        rule.onNodeWithTag("quick.addpage").performScrollTo().performClick(); settle()
        assertFalse("no question the second time", tagShown("addpage.scroll"))
        assertEquals("a page was added after the existing one", 2, pageCount())
        assertFalse(glanceShown())
        assertTrue(tagShown("pages.glancedot"))
        shoot("w26-paginated-2pages")
    }

    @Test fun HOM_01_groundControlHasAddPageToAndTheScrollChoiceMakesTheStartScreenScroll() {
        startOnGlance()
        key(android.view.KeyEvent.KEYCODE_COMMA, ctrl = true); check("ground control", Surface.Settings)
        rule.onNodeWithText("Home screen", useUnmergedTree = true).performClick(); settle()
        rule.onNodeWithText("Add page", useUnmergedTree = true).performScrollTo().performClick(); settle()
        rule.onNodeWithTag("addpage.scroll").performClick(); settle()
        assertEquals(HomeScroll.Continuous, stores.prefs.prefs.value.homeScroll)
        assertFalse("Ground Control closed and home shows the pages", settingsShown() || glanceShown())
        assertTrue("the scroll surface is on screen", tagShown("scrollhome"))
        shoot("w26-scroll-app")
    }

    @Test fun HOM_22_withGlanceOffHomeShowsTheFirstPageAndNoGlanceStop() {
        startOnGlance { it.copy(glanceEnabled = false) }
        assertFalse(glanceShown())
        assertFalse("no Glance stop in the indicator", tagShown("pages.glancedot"))
        shoot("w26-glance-off")
    }

    @Test fun HOM_23_rightToLeftOrderMirrorsTheEdgesAndTheForwardSwipe() {
        startOnGlance { it.copy(homeOrder = HomeOrder.RightToLeft, pagesModeChosen = true, homeScroll = HomeScroll.Paged) }
        assertTrue(glanceShown())
        shoot("w26-glance-rtl-order")
        swipeForward(rtl = true); assertFalse("in right to left order the forward swipe moves right", glanceShown())
        shoot("w26-paginated-rtl-order")
    }

    @Test fun HOM_23_hotspotsStayOnTheScrollSurface() {
        startOnGlance { it.copy(glanceEnabled = false, pagesModeChosen = true, homeScroll = HomeScroll.Continuous) }
        assertTrue(tagShown("scrollhome"))
        val ids = LauncherInput.focus.nodes().map { it.id }
        assertTrue("the corner hotspots are still on the focus graph on the scroll: $ids", ids.count { it.startsWith("corner.") } >= 4 || ids.count { it.contains("corner") } >= 4)
    }
}
