package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import com.bos.sphere.core.data.GradientSpec
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.Wallpaper
import com.bos.sphere.core.data.WallpaperKind
import com.bos.sphere.core.data.WallpaperSurface
import com.bos.sphere.core.data.withWallpaper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * W55 (fixedBackdrop). Owner: "When I scroll between pages, the background also moves, it shouldn't." The wallpaper is ONE layer below the pager: while the pager is dragged
 * (Glance to page, page to page, Continuous with its inner scroll) its bounds and its drawn pixel columns do not change, the page content does move over it, and when the
 * Pages wallpaper differs the two cross-fade with the pager position instead of sliding. Robolectric proves layout and drawn pixels, not the feel on a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h720dp-xhdpi")
class FixedHomeBackdropW55Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    private lateinit var pager: PagerState

    @Before fun still() = com.bos.sphere.core.design.ReducedMotionTesting.force(com.bos.sphere.core.quality.MotionPrefs(0f, 0f, 0f))
    @After fun reset() = com.bos.sphere.core.design.ReducedMotionTesting.force(null)

    /** A left to right gradient: every pixel column is distinct, so a sliding wallpaper cannot hide. */
    private val stripes = Wallpaper(WallpaperKind.Gradient, gradient = GradientSpec(0xFFD81B60, 0xFF1A237E, 0))
    private val red = Wallpaper(WallpaperKind.Solid, solid = 0xFFFF0000)
    private val blue = Wallpaper(WallpaperKind.Solid, solid = 0xFF0000FF)

    private fun same(w: Wallpaper) = LauncherPrefs(pagesSameAsGlance = true).withWallpaper(WallpaperSurface.Glance, w)
    private fun split() = LauncherPrefs(pagesSameAsGlance = false, glanceEnabled = true).withWallpaper(WallpaperSurface.Glance, red).withWallpaper(WallpaperSurface.Pages, blue)

    private fun host(prefs: LauncherPrefs, stops: Int = 2, glance: Boolean = true, rtl: Boolean = false, innerScroll: Boolean = false) = rule.setContent {
        pager = rememberPagerState(0) { stops }
        Box(Modifier.fillMaxSize()) {
            FixedHomeBackdrop(prefs, position = { pager.currentPage + pager.currentPageOffsetFraction })
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                HorizontalPager(pager, Modifier.fillMaxSize().testTag("pager"), reverseLayout = rtl) { stop ->
                    // the page content: a marker block that must move with the page, over the transparent page
                    if (innerScroll && stop == 1) Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) { Box(Modifier.width(1000.dp).fillMaxSize().testTag("inner")) { Box(Modifier.align(Alignment.Center).size(40.dp).background(Color.White).testTag("marker$stop")) } }
                    else Box(Modifier.fillMaxSize()) { Box(Modifier.align(Alignment.Center).size(40.dp).background(Color.White).testTag("marker$stop")) }
                }
            }
        }
    }

    private fun bitmap(): Bitmap {
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        val view = rule.activity.findViewById<View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        return bmp
    }

    /** The backdrop's pixel row at y = 6 (above the page markers), every 6th column. */
    private fun row(b: Bitmap) = (0 until b.width step 6).map { b.getPixel(it, 6) }

    private fun isRed(c: Int) = android.graphics.Color.red(c) > 120 && android.graphics.Color.blue(c) < 20
    private fun isBlue(c: Int) = android.graphics.Color.blue(c) > 120 && android.graphics.Color.red(c) < 20

    private fun holdDrag(dx: Float) = rule.onNodeWithTag("pager").performTouchInput { down(center); moveBy(Offset(dx, 0f)); moveBy(Offset(dx / 4, 0f)) }
    private fun release() = rule.onNodeWithTag("pager").performTouchInput { up() }

    private fun assertFixedWhileDragging(dx: Float, name: String) {
        val boundsBefore = rule.onNodeWithTag(FixedHomeBackdrop.TAG).getBoundsInRoot()
        val markerBefore = rule.onNodeWithTag("marker0").getBoundsInRoot().left
        val rest = row(bitmap())
        holdDrag(dx)
        val mid = bitmap()
        assertTrue("the pager is mid-swipe (offset ${pager.currentPageOffsetFraction}, page ${pager.currentPage})", pager.currentPageOffsetFraction != 0f || pager.currentPage != 0)
        File(outDir, "fixed-backdrop-$name.png").outputStream().use { mid.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertEquals("the backdrop layer's bounds do not change", boundsBefore, rule.onNodeWithTag(FixedHomeBackdrop.TAG).getBoundsInRoot())
        assertEquals("the wallpaper's pixel columns are identical at rest and mid-swipe", rest, row(mid))
        assertNotEquals("the page content DOES move over it", markerBefore, rule.onNodeWithTag("marker0").getBoundsInRoot().left)
        release()
    }

    @Test fun HOM_16_theWallpaperStaysFixedWhileDraggingFromGlanceToAPage() { host(same(stripes)); assertFixedWhileDragging(-260f, "glance-to-page") }

    @Test fun HOM_16_theWallpaperStaysFixedWhileDraggingFromPageToPage() {
        host(same(stripes), stops = 3)
        rule.runOnUiThread { kotlinx.coroutines.runBlocking { pager.scrollToPage(1) } }
        rule.waitForIdle()
        val b = rule.onNodeWithTag(FixedHomeBackdrop.TAG).getBoundsInRoot(); val rest = row(bitmap())
        holdDrag(-260f)
        val mid = bitmap()
        assertTrue("mid-swipe", pager.currentPageOffsetFraction != 0f || pager.currentPage != 1)
        assertEquals(b, rule.onNodeWithTag(FixedHomeBackdrop.TAG).getBoundsInRoot())
        assertEquals(rest, row(mid))
        release()
    }

    @Test fun HOM_16_theWallpaperStaysFixedWhileTheContinuousScrollMovesOverIt() {
        host(same(stripes), innerScroll = true)
        rule.runOnUiThread { kotlinx.coroutines.runBlocking { pager.scrollToPage(1) } }
        rule.waitForIdle()
        val rest = row(bitmap())
        rule.onNodeWithTag("pager").performTouchInput { down(centerRight - Offset(40f, 0f)); moveBy(Offset(-200f, 0f)); moveBy(Offset(-60f, 0f)) }
        assertEquals("the wallpaper does not move with the scroll surface", rest, row(bitmap()))
        release()
    }

    @Test fun HOM_16_theWallpaperStaysFixedInRightToLeftOrder() {
        host(same(stripes), rtl = true)
        assertFixedWhileDragging(260f, "rtl")
    }

    @Test fun HOM_16_aDifferentPagesWallpaperCrossFadesWithThePagerInsteadOfSliding() {
        host(split())
        val rest = bitmap()
        assertTrue("at the Glance only the Glance wallpaper shows (red)", isRed(rest.getPixel(10, 6)))
        assertTrue("one wallpaper is composed at rest", rule.onAllNodesWithTagCount(FixedHomeBackdrop.PAGES_TAG) == 0)
        holdDrag(-200f)
        val mid = bitmap()
        File(outDir, "fixed-backdrop-crossfade.png").outputStream().use { mid.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val a = mid.getPixel(10, 6); val b = mid.getPixel(mid.width - 10, 6)
        assertEquals("the blend is the same at every column (a fade, not a slide)", a, b)
        assertTrue("a mix of red and blue", android.graphics.Color.red(a) in 15..235 && android.graphics.Color.blue(a) in 15..235)
        release()
        rule.runOnUiThread { kotlinx.coroutines.runBlocking { pager.scrollToPage(1) } }
        val settled = bitmap()
        assertTrue("settled on the page: the Pages wallpaper (blue)", isBlue(settled.getPixel(10, 6)))
        assertTrue("one wallpaper is composed when settled", rule.onAllNodesWithTagCount(FixedHomeBackdrop.TAG) == 1)
    }

    @Test fun HOM_16_theCrossFadeFollowsThePagerInRightToLeftOrderToo() {
        host(split(), rtl = true)
        holdDrag(200f)
        val a = bitmap().getPixel(10, 6)
        assertTrue("mid-fade in RTL: red ${android.graphics.Color.red(a)} blue ${android.graphics.Color.blue(a)}", android.graphics.Color.red(a) in 15..235 && android.graphics.Color.blue(a) in 15..235)
        release()
        rule.runOnUiThread { kotlinx.coroutines.runBlocking { pager.scrollToPage(1) } }
        assertTrue(isBlue(bitmap().getPixel(10, 6)))
    }

    @Test fun HOM_16_withGlanceOffThePagesWallpaperIsShownAtOnce() {
        host(split().copy(glanceEnabled = false), stops = 2, glance = false)
        assertTrue(isBlue(bitmap().getPixel(10, 6)))
    }

    @Test fun HOM_16_pagesAlphaFollowsThePagerPositionAndSaturates() {
        assertEquals(0f, FixedHomeBackdrop.pagesAlpha(true, 0f), 0f)
        assertEquals(0.4f, FixedHomeBackdrop.pagesAlpha(true, 0.4f), 1e-6f)
        assertEquals(1f, FixedHomeBackdrop.pagesAlpha(true, 1.3f), 0f)
        assertEquals(0f, FixedHomeBackdrop.pagesAlpha(true, -0.2f), 0f)
        assertEquals(1f, FixedHomeBackdrop.pagesAlpha(false, 0f), 0f)
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size
}
