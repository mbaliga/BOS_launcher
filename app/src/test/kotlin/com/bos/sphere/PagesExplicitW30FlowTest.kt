package com.bos.sphere

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.bos.sphere.core.data.FreshHome
import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.LegacyHomeBackup
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
 * Wave 30 (pagesExplicit), through the REAL UI under Robolectric: pages exist because the user added them, an app or tile is on a page because the
 * user placed it, an install adds neither a page nor an icon, deleting a page really removes it (and Undo brings it back), and the one-time reset of
 * an auto-filled legacy home with its Restore. The widget of the owner's script is stood in for by a home tile (the Files panel), because there is
 * no widget host under Robolectric. Not a device run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class PagesExplicitW30FlowTest : TourBase() {
    private val stores get() = LauncherStores.get(AppSmokeHarness.app)
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    private fun key(i: Int) = AppSmokeHarness.appKey("App %02d".format(i))
    private fun pages() = stores.layout.layout.value.pages
    private fun pageKeys() = pages().flatMap { it.keys }
    private fun dots() = (0..9).count { tagShown("pages.dot.$it") }

    private fun shoot(name: String) {
        settle(400)
        var bmp: Bitmap? = null
        sc.onActivity { a ->
            val view = a.window.decorView
            val b = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            android.view.PixelCopy.request(a.window, b, { }, android.os.Handler(android.os.Looper.getMainLooper()))
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            bmp = b
        }
        File(outDir, "$name.png").outputStream().use { bmp!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun start(apps: Int = 30, layout: HomeLayout? = null, pagesMode: HomePages = HomePages.Paginated) {
        AppSmokeHarness.seedApps(*Array(apps) { "App %02d".format(it) })
        AppSmokeHarness.configureFresh { it.copy(glanceEnabled = true, homePages = pagesMode) }
        AppSmokeHarness.app.getSharedPreferences("liftoff_misc", android.content.Context.MODE_PRIVATE).edit().putBoolean("defaultHomeSeeded", true).commit()
        stores.layout.replace(layout ?: FreshHome.emptyLayout())
        launch()
    }

    private fun swipePager(forward: Boolean) {
        rule.onNodeWithTag("home.pager").performTouchInput { val y = height * 0.5f; val a = width * 0.75f; val b = width * 0.25f; swipe(Offset(if (forward) a else b, y), Offset(if (forward) b else a, y), 300) }; settle()
    }
    private fun openQuickSheet(fy: Float = 0.3f) { longPressAt(0.5f, fy); if (!quickSheetShown()) { longPressAt(0.5f, 0.45f) } ; assertTrue("the quick sheet", quickSheetShown()) }
    private fun toGlance() { if (!glanceShown()) { swipePager(false); if (!glanceShown()) swipePager(false) } }

    @Test fun PGX_pagesAreAddedOneEmptyPageAtATimeIconsAreOnlyThePlacedOnesAndDeleteRemovesThePageForGood() {
        start(pagesMode = HomePages.Off)
        assertTrue(glanceShown())
        assertEquals("a fresh home is one empty page", 1, pages().size); assertTrue(pageKeys().isEmpty())
        // Pages Off: only Glance is a stop, so the indicator is just the house
        assertEquals(0, dots())

        // turning the first page on (the Pages setting) shows the one empty page; nothing is filled
        openQuickSheet()
        rule.onNodeWithTag("quick.pages.Paginated").performScrollTo().performClick(); closeSheet(); settle()
        assertEquals(1, pages().size); assertTrue(pageKeys().isEmpty())
        assertTrue(tagShown("pages.glancedot")); assertEquals("house + 1 dot", 1, dots())
        shoot("w30-glance-house-1dot")
        swipePager(true); assertFalse(glanceShown()); assertTrue("the first page is empty: no apps, no tiles", pageKeys().isEmpty())
        shoot("w30-empty-page-1")
        toGlance()

        // Add page adds exactly one more empty page
        openQuickSheet()
        quickGoTo("Add page")
        assertEquals("two pages", 2, pages().size); assertTrue("both empty: nothing was auto-filled", pageKeys().isEmpty())
        toGlance(); settle()
        assertEquals("house + 2 dots", 2, dots()); assertTrue(tagShown("pages.glancedot"))
        shoot("w30-glance-house-2dots")

        // the user places an app and a tile on page 1
        openQuickSheet()
        quickGoTo("Edit home")
        assertTrue(editShown())
        rule.onNodeWithContentDescription("Add to home: App 03").performClick(); settle()
        assertEquals(listOf(key(3)), pages()[0].keys); assertTrue(pages()[1].keys.isEmpty())
        shoot("w30-edit-page1-one-app")
        back()
        toGlance(); openQuickSheet()
        quickGoTo("Widgets, folders and icons")
        rule.onNodeWithText("Files panel").performClick(); settle()
        assertEquals("the app and the tile are on page 1", 2, pages()[0].keys.size); assertTrue(pages()[1].keys.isEmpty())

        // an install adds no page and no icon
        AppSmokeHarness.installApp("Brand New")
        settle(2500)
        assertEquals(2, pages().size); assertFalse(AppSmokeHarness.appKey("Brand New") in pageKeys()); assertEquals(2, pages()[0].keys.size)
        assertFalse("not drawn on any page either", descShown("Brand New"))

        // delete page 2 (empty) from its quick sheet: it is gone at once, the indicator shows house + 1 dot
        toGlance(); swipePager(true); swipePager(true) // page 1, then page 2
        openQuickSheet(0.45f)
        rule.onNodeWithText("Delete page").performScrollTo().performClick(); settle()
        assertEquals("page 2 is really gone", 1, pages().size)
        assertEquals(2, pages()[0].keys.size)
        settle(2500)
        assertEquals("nothing refilled it: still the placed items only", 2, pageKeys().size)
        assertEquals("house + 1 dot", 1, dots())
        shoot("w30-page1-house-1dot")
    }

    @Test fun PGX_deletingAPageThatHoldsItemsAsksThenRemovesThemAndUndoBringsThePageBack() {
        start(layout = HomeLayout(pages = listOf(HomePage(listOf(key(1), key(2)), id = 1L), HomePage(listOf(key(5), key(6), key(7)), id = 2L)), seeded = true))
        stores.layout.markExplicit()
        swipePager(true); swipePager(true) // onto page 2
        openQuickSheet()
        rule.onNodeWithText("Delete page").performScrollTo().performClick(); settle()
        assertTrue("it asks first", textShown("Delete this page?"))
        assertTrue(textShown("3 items will be removed from this page. They stay in the drawer."))
        shoot("w30-delete-page-confirm")
        rule.onNodeWithText("Cancel").performClick(); settle()
        assertEquals("cancel keeps it", 2, pages().size)
        openQuickSheet()
        rule.onNodeWithText("Delete page").performScrollTo().performClick(); settle()
        confirmDeletePage()
        assertEquals(1, pages().size); assertEquals(listOf(key(1), key(2)), pageKeys())
        settle(2000)
        assertEquals("the removed apps did not come back onto a page", listOf(key(1), key(2)), pageKeys())
    }

    @Test fun PGX_anAutoFilledLegacyHomeIsResetToOneEmptyPageWithANoticeAndRestoreBringsItBack() {
        val all = (0 until 30).map { key(it) }
        val legacyPages = all.chunked(6).mapIndexed { i, ks -> HomePage(ks, id = i + 1L) } // 5 full pages of the apps in catalog order
        AppSmokeHarness.seedApps(*Array(30) { "App %02d".format(it) })
        AppSmokeHarness.configureFresh { it.copy(glanceEnabled = true, homePages = HomePages.Paginated, appsOnHome = com.bos.sphere.core.data.AppsOnHome.Everything, portraitGrid = com.bos.sphere.core.data.GridSpec(3, 2)) }
        AppSmokeHarness.app.getSharedPreferences("liftoff_misc", android.content.Context.MODE_PRIVATE).edit().putBoolean("defaultHomeSeeded", true).commit()
        stores.layout.replace(HomeLayout(pages = legacyPages, seeded = true))
        launch()
        waitFor("the reset notice", 8000) { textShown("Home pages were reset") }
        assertEquals("one empty page", 1, pages().size); assertTrue(pageKeys().isEmpty())
        assertTrue(textShown("Home pages were reset: they held every app automatically. Add your own in Edit home."))
        assertTrue("the old pages are kept in a file", File(AppSmokeHarness.app.filesDir, LegacyHomeBackup.FILE).exists())
        shoot("w30-reset-notice")
        rule.onNodeWithText("OK").performClick(); settle()
        assertFalse(stores.layout.noticePending())
        assertEquals("house + 1 dot", 1, dots())
        // Settings, Home screen has Restore previous pages
        key(android.view.KeyEvent.KEYCODE_COMMA, ctrl = true); check("ground control", Surface.Settings)
        rule.onNodeWithText("Home screen", useUnmergedTree = true).performClick(); settle()
        try { rule.onNodeWithText("Restore previous pages", useUnmergedTree = true).performScrollTo().performClick() } catch (e: AssertionError) { throw AssertionError("legacy backup exists=" + File(AppSmokeHarness.app.filesDir, LegacyHomeBackup.FILE).exists() + " available=" + (LegacyHomeBackup(AppSmokeHarness.app.filesDir).available(System.currentTimeMillis()) != null) + " at=" + LegacyHomeBackup(AppSmokeHarness.app.filesDir).load()?.atMillis + " now=" + System.currentTimeMillis() + " texts=" + rule.onAllNodes(androidx.compose.ui.test.hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNodes().mapNotNull { (if (androidx.compose.ui.semantics.SemanticsProperties.Text in it.config) it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString() else null) }.take(80), e) }
        waitFor("the previous pages come back (the restore reads the backup off the main thread)", 8000) { pages().size == legacyPages.size }
        assertEquals("the previous pages are back", legacyPages.map { it.keys }, pages().map { it.keys })
        waitFor("the backup is used up", 4000) { !File(AppSmokeHarness.app.filesDir, LegacyHomeBackup.FILE).exists() }
    }

    @Test fun PGX_aHandBuiltHomeIsKeptAndShowsNoNotice() {
        start(layout = HomeLayout(pages = listOf(HomePage(listOf(key(4), key(9)), id = 1L), HomePage(listOf(key(2)), id = 2L)), seeded = true))
        settle(2500)
        assertEquals(2, pages().size); assertEquals(listOf(key(4), key(9), key(2)), pageKeys())
        assertFalse(textShown("Home pages were reset"))
    }
}
