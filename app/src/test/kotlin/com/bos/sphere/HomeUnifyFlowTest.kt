package com.bos.sphere

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomeOrder
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.LauncherStores
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
 * HOM-01, HOM-22, HOM-23 (wave 28, homeUnify): the home is ONE pager, Glance its first stop, with ONE page indicator, driven through the REAL UI under
 * Robolectric: the Pages setting (Off, Paginated, Continuous) from the long-press quick sheet, long press to edit mode to add apps and a tile, swiping
 * between Glance and page 1, and the right to left order. Replaces the wave 26 flow test that encoded the separate Pages surface and the Scroll or
 * Paginated question. Not a device run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class HomeUnifyFlowTest : TourBase() {
    private val stores get() = LauncherStores.get(AppSmokeHarness.app)
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private fun start(apps: Int = 30, layout: List<HomePage>? = null, edit: (LauncherPrefs) -> LauncherPrefs = { it }) {
        AppSmokeHarness.seedApps(*Array(apps) { "App %02d".format(it) })
        AppSmokeHarness.configureFresh { edit(it.copy(glanceEnabled = true)) }
        if (layout != null) stores.layout.replace(HomeLayout(pages = layout, seeded = true))
        launch()
    }

    private fun shoot(name: String) {
        settle(400)
        runCatching {
            rule.mainClock.autoAdvance = true
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        rule.mainClock.autoAdvance = false
    }

    private fun indicators() = rule.onAllNodesWithTag("page.indicator", useUnmergedTree = true).fetchSemanticsNodes()
    private fun oneIndicator(): androidx.compose.ui.geometry.Rect {
        val n = indicators()
        assertEquals("exactly one page indicator exists", 1, n.size)
        assertFalse("no second set of dots (deck dots)", tagShown("glance.deckdots"))
        return n[0].boundsInRoot
    }
    private fun key(i: Int) = AppSmokeHarness.appKey("App %02d".format(i))
    private fun pageKeys() = stores.layout.layout.value.pages.flatMap { it.keys }

    /** A swipe that goes to the NEXT stop in the configured order (away from the edges: an edge drag belongs to Comms and Recents). */
    private fun forward(rtl: Boolean = false) {
        rule.onNodeWithTag("home.pager").performTouchInput { val y = height * 0.5f; val a = width * 0.75f; val b = width * 0.25f; swipe(Offset(if (rtl) b else a, y), Offset(if (rtl) a else b, y), 300) }; settle()
    }
    private fun backward(rtl: Boolean = false) = forward(!rtl)

    private fun quickSheetOnGlance() { longPressAt(0.5f, 0.75f); assertTrue("a long press on Glance opens the quick sheet", quickSheetShown()) }

    @Test fun HOM_01_w28_fromGlanceWithPagesOffTheQuickSheetTurnsPagedOnEditHomeAddsAnAppAndATileAndOneIndicatorStaysInPlaceWhileSwiping() {
        start()
        assertTrue("home starts on Glance", glanceShown()); assertEquals("Pages is Off on a fresh install", HomePages.Off, stores.prefs.prefs.value.homePages)
        val onGlance = oneIndicator()
        shoot("w28-glance-indicator")
        forward(); assertTrue("with Pages Off a forward swipe has nowhere to go", glanceShown())
        quickSheetOnGlance()
        rule.onNodeWithTag("quick.pages.Paginated").performScrollTo().performClick(); settle()
        assertEquals(HomePages.Paginated, stores.prefs.prefs.value.homePages)
        quickGoTo("Edit home")
        assertTrue("Edit home goes to the first page in edit mode", editShown()); assertFalse(glanceShown())
        // pick an app from the add carousel (wave 45: the carousel is in the page editor, one tap on the edit bar's title)
        openPageEditorFromEdit()
        rule.onNodeWithContentDescription("Add to home: App 03").performClick(); settle()
        assertTrue("the app is on page 1", key(3) in pageKeys()); val before = pageKeys().size
        rule.onNodeWithTag("edit.done", useUnmergedTree = true).performClick(); settle(); assertFalse("Done leaves edit mode keeping the app", editShown()) // W47: Back in the page editor is Cancel (it would ask and undo the app)
        // a tile (the Files panel) from the quick sheet's "Widgets, folders and icons"
        backward(); assertTrue(glanceShown()); quickSheetOnGlance()
        quickGoTo("Widgets, folders and icons")
        rule.onNodeWithText("Files panel").performClick(); settle()
        assertEquals("the tile joins the app on page 1", before + 1, pageKeys().size)
        settle()
        val onPage = oneIndicator()
        assertFalse("placing an item from Glance shows the page it went to", glanceShown())
        shoot("w28-page1-indicator")
        // the indicator is one component at one place: same bottom, same centre whether Glance or page 1 shows
        backward(); assertTrue("a backward swipe returns to Glance", glanceShown())
        val backOnGlance = oneIndicator()
        assertEquals("bottom", onGlance.bottom, backOnGlance.bottom, 2f); assertEquals("bottom", onGlance.bottom, onPage.bottom, 2f)
        assertEquals("height", onGlance.height, onPage.height, 2f)
        assertEquals("centre", onGlance.center.x, onPage.center.x, 2f)
        forward(); assertFalse("a forward swipe reaches page 1", glanceShown())
        val again = oneIndicator(); assertEquals(onPage.bottom, again.bottom, 2f)
        // the indicator counts every stop: the house (Glance) and the page dot; both are real controls that move the one pager
        assertTrue(tagShown("pages.glancedot")); assertTrue(tagShown("pages.dot.0"))
        rule.onNodeWithTag("pages.glancedot", useUnmergedTree = true).performClick(); settle()
        assertTrue("the house dot returns to Glance", glanceShown()); oneIndicator()
        rule.onNodeWithTag("pages.dot.0", useUnmergedTree = true).performClick(); settle()
        assertFalse("the page dot goes to page 1", glanceShown()); oneIndicator()
        // Pages Off removes the extra stop and keeps the items stored
        backward(); quickSheetOnGlance()
        rule.onNodeWithTag("quick.pages.Off").performScrollTo().performClick(); closeSheet(); settle()
        forward(); assertTrue("no pages stop with Pages Off", glanceShown())
        assertEquals("the items stay stored", before + 1, pageKeys().size); assertEquals(1, indicators().size)
        stores.prefs.update { it.copy(homePages = HomePages.Paginated) }; settle()
        forward(); assertFalse("turning Pages on again brings the page back", glanceShown()); assertTrue(descShown("App 03"))
    }

    @Test fun HOM_01_w28_continuousModeFollowsGlanceInTheSamePagerWithOneIndicatorAndTheColumnFill() {
        start(layout = listOf(HomePage(keys = List(30) { key(it) }, id = 1L))) { it.copy(homePages = HomePages.Continuous) }
        assertTrue(glanceShown()); val g = oneIndicator()
        assertFalse("the scroll is the next stop of the pager, not on screen yet", descShown("App 29"))
        forward(); assertFalse(glanceShown()); assertTrue("the scroll surface follows Glance", tagShown("scrollhome"))
        val s = oneIndicator()
        assertEquals(g.bottom, s.bottom, 2f); assertEquals(g.center.x, s.center.x, 2f)
        assertTrue("Glance dot then the scroll dot", tagShown("pages.glancedot") && tagShown("pages.dot.0") && !tagShown("pages.dot.1"))
        shoot("w28-continuous-30-portrait")
        backward(); assertTrue("back to Glance from the start of the scroll", glanceShown())
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_01_w28_continuousLandscapeShowsMoreColumnsAndTheSameSingleIndicator() {
        start(layout = listOf(HomePage(keys = List(30) { key(it) }, id = 1L))) { it.copy(homePages = HomePages.Continuous) }
        forward(); assertFalse(glanceShown()); oneIndicator()
        shoot("w28-continuous-30-landscape")
    }

    @Test fun HOM_23_w28_rightToLeftOrderMirrorsTheSwipesAndTheIndicator() {
        start(layout = List(3) { HomePage(keys = listOf(key(it)), id = it + 1L) }) { it.copy(homeOrder = HomeOrder.RightToLeft, homePages = HomePages.Paginated) }
        assertTrue(glanceShown()); oneIndicator()
        shoot("w28-glance-rtl")
        forward(rtl = true); assertFalse("in right to left the forward swipe moves right", glanceShown()); oneIndicator()
        val house = rule.onNodeWithTag("pages.glancedot", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val first = rule.onNodeWithTag("pages.dot.0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Glance is the first stop, at the right", house.left > first.left)
        shoot("w28-page1-rtl")
        backward(rtl = true); assertTrue(glanceShown())
    }

    @Test fun HOM_01_w28_withGlanceAndPagesBothOffHomeIsOneEditableFirstPageAndNoIndicator() {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta"); launch()
        stores.prefs.update { it.copy(onboardingDone = true, glanceEnabled = false, homePages = HomePages.Off) }; settle()
        assertFalse(glanceShown()); assertEquals("no indicator for a single page", 0, indicators().size)
        rule.onNodeWithText("Long press to add apps").performClick(); settle()
        assertTrue("the one page is editable", editShown())
    }

    @Test fun HOM_23_w28_theHomeButtonReturnsToGlanceWhenOnElseToThePageAndTalkBackHearsStopPositionsIncludingGlance() {
        start(layout = List(3) { HomePage(keys = listOf(key(it)), id = it + 1L) }) { it.copy(homePages = HomePages.Paginated) }
        forward(); assertFalse(glanceShown())
        assertTrue("page 1 of the pages is stop 2 of 4 because Glance is stop 1", descShown("Page 2 of 4"))
        assertTrue(descShown("Page 4 of 4")); assertTrue(descShown("Glance home", substring = true))
        pressHome(); assertTrue("Home returns to Glance when it is on", glanceShown())
        stores.prefs.update { it.copy(glanceEnabled = false) }; settle()
        assertFalse(glanceShown()); assertTrue(descShown("Page 3 of 3")); assertFalse("no Glance stop with Glance off", tagShown("pages.glancedot"))
        pressHome(); assertFalse("with Glance off Home shows the first page", glanceShown())
    }

    @Test fun HOM_01_w28_groundControlAddPageAddsAPageWhenPaginatedAndNeverAsksScrollOrPaginated() {
        start(edit = { it.copy(homePages = HomePages.Paginated) })
        key(android.view.KeyEvent.KEYCODE_COMMA, ctrl = true); check("ground control", Surface.Settings)
        SettingsTestNav.goTo(rule, "Home", "Add page"); settle(300) // wave 57: the Home tab
        rule.onNodeWithText("Add page", useUnmergedTree = true).performClick(); settle()
        assertFalse("there is no question any more", tagShown("addpage.scroll") || tagShown("addpage.paginated"))
        assertEquals(HomePages.Paginated, stores.prefs.prefs.value.homePages)
        assertTrue("home shows the page", !glanceShown() && !settingsShown())
        oneIndicator()
    }
}
