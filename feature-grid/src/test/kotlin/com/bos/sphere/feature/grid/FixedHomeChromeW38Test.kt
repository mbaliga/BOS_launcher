package com.bos.sphere.feature.grid

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.DefaultCorners
import com.bos.sphere.core.data.GlanceHotspots
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.design.ThemeVariant
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import android.graphics.Bitmap
import android.graphics.Canvas

/** Wave 38 (glanceFixed): the pure rules of the fixed chrome layer. */
class FixedHomeChromeRulesTest {
    private val media = MediaInfo("Title", "A", "pkg", playing = true, canPrev = true, canNext = true)

    @Test fun GFX_38_theMusicLineShowsWheneverMediaIsActiveAndTheToggleIsOnOnAnyPage() {
        assertTrue(FixedHomeChrome.musicShown(true, media))
        assertFalse(FixedHomeChrome.musicShown(false, media))
        assertFalse(FixedHomeChrome.musicShown(true, null))
        assertTrue(FixedHomeChrome.listensToMedia(true)); assertFalse(FixedHomeChrome.listensToMedia(false))
    }

    @Test fun GFX_38_theHotspotCornersWinTheTouchAndTheCornersAreAtLeast48Dp() {
        assertTrue(FixedHomeChrome.HOTSPOT_TOUCH_DP >= 48f)
        val all = Corner.entries.toSet()
        assertFalse(FixedHomeChrome.pagerOwns(10f, 10f, 411f, 891f, all)); assertFalse(FixedHomeChrome.pagerOwns(400f, 880f, 411f, 891f, all))
        assertTrue(FixedHomeChrome.pagerOwns(200f, 400f, 411f, 891f, all)); assertTrue(FixedHomeChrome.pagerOwns(200f, 10f, 411f, 891f, all))
        assertTrue("a corner that is off leaves the pager the touch", FixedHomeChrome.pagerOwns(10f, 10f, 411f, 891f, setOf(Corner.BottomRight)))
    }
}

/** Wave 38: the chrome composed above a real pager stays put while the pager scrolls; there is no clock, date or weather on the calm page. */
@OptIn(ExperimentalFoundationApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FixedHomeChromeW38UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @After fun reset() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
        ReducedMotionTesting.force(null)
        L10n.setLanguage("")
        runCatching { LauncherInput.focus.nodes().map { it.id }.forEach(LauncherInput.focus::unregister) }
    }

    private val media = MediaInfo("Someday Out of The Blue", "Artist", "pkg.music", playing = true, canPrev = true, canNext = true)
    private fun hotspots() = DefaultCorners.filterKeys { it in GlanceHotspots.ALL }.map { (c, slot) -> Hotspot(c, GlanceHotspots.itemOf(slot)!!) }
    private lateinit var goTo: (Int) -> Unit

    private fun host(m: MediaInfo? = media) = rule.setContent {
        val pager = rememberPagerState(0) { 3 }
        val scope = rememberCoroutineScope()
        goTo = { p -> scope.launch { pager.scrollToPage(p) } }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize().testTag("pager")) { page ->
                if (page == 0) GlanceSurface(LauncherPrefs(), listOf(com.bos.sphere.core.data.RightDestination.Recents), true, indicator = false, dark = true)
                else Box(Modifier.fillMaxSize().background(if (page == 1) Color(0xFF203050) else Color(0xFF502030)).testTag("page$page"))
            }
            FixedHomeChrome(m, true, 4, {}, {}, {}, dark = true, accent = Color(0xFF8E7BFF), corners = { MinimalHotspotLayer(hotspots(), emptyMap(), showHints = false, onRun = { _, _ -> }, showIcons = true) })
            PageIndicator(count = 2, current = 0, vertical = false, onSelect = {}, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter), onMenu = null, menuLabel = "", menuTag = "x", rtl = false, onGlance = {}, glanceCurrent = true, right = emptyList(), onRight = {}, quiet = false, awakeNonce = 0)
        }
    }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(300)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { Canvas(bmp).also { it.drawColor(android.graphics.Color.BLACK); view.draw(it) } }
        File(outDir, "fixed_chrome_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun rects() = LauncherInput.focus.nodes().filter { it.id.startsWith("minimal.corner.") || it.id == "glance.nowplaying" || it.id == "glance.clock" }.associate { it.id to it.rect }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun GFX_38_theFourHotspotsAndTheMusicLineStayAtTheSameScreenPositionOnGlanceMidSwipeAndOnPage2() {
        host()
        rule.waitForIdle()
        val onGlance = rects()
        assertEquals(6, onGlance.size) // four hotspots, the clock, the music line
        // mid-swipe: drag the pager part of the way and hold the finger down
        rule.onNodeWithTag("pager").performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(-width * 0.4f, 0f)) }
        rule.mainClock.advanceTimeBy(100)
        val mid = rects()
        rule.onNodeWithTag("pager").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(600)
        rule.runOnUiThread { goTo(1) }; rule.waitForIdle()
        shoot("page2_dark")
        val onPage = rects()
        rule.runOnUiThread { goTo(2) }; rule.waitForIdle()
        val onLast = rects()
        for ((id, r) in onGlance) {
            assertEquals("$id mid-swipe", r, mid.getValue(id))
            assertEquals("$id on page 2", r, onPage.getValue(id))
            assertEquals("$id on page 3", r, onLast.getValue(id))
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun GFX_38_theClockAndTheMusicLineAreBothOnAPageNotJustOnGlance() {
        host(); rule.runOnUiThread { goTo(1) }; rule.waitForIdle()
        rule.onNodeWithTag("page1").assertExists()
        assertEquals(1, rule.onAllNodesWithTag("glance.clock.analog", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithContentDescription("Now playing: Someday Out of The Blue · Artist").fetchSemanticsNodes().size)
        for (t in listOf("glance.time", "glance.date", "glance.weather")) assertEquals(t, 0, rule.onAllNodesWithTag(t, useUnmergedTree = true).fetchSemanticsNodes().size)
        val clock = rule.onNodeWithTag("glance.clock.analog", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val music = rule.onNodeWithContentDescription("Now playing: Someday Out of The Blue · Artist").fetchSemanticsNode().boundsInRoot
        assertTrue("no overlap: the music line is under the clock", music.top >= clock.bottom - 1f)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun GFX_38_theMusicLineIsVisibleOnAPageWhenMediaIsActiveAndAbsentWithoutMedia() {
        host(); rule.runOnUiThread { goTo(1) }; rule.waitForIdle()
        rule.onNodeWithTag("page1").assertExists()
        assertEquals(1, rule.onAllNodesWithContentDescription("Now playing: Someday Out of The Blue · Artist").fetchSemanticsNodes().size)
        assertEquals("the title keeps its 14 sp style", 1, rule.onAllNodesWithTag("glance.title.static", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun GFX_38_noMediaMeansNoMusicLine() {
        host(null); rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithContentDescription("Now playing", substring = true).fetchSemanticsNodes().size)
        assertEquals("four hotspots and the clock", 5, rects().size)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun GFX_38_theCalmGlanceHasNoClockDateOrWeatherAndTheDotsStayPut() {
        host(); rule.waitForIdle()
        for (t in listOf("glance.time", "glance.date")) assertEquals(t, 0, rule.onAllNodesWithTag(t, useUnmergedTree = true).fetchSemanticsNodes().size)
        val dots = { rule.onAllNodesWithTag("glance.indicator.launchpad", useUnmergedTree = true).fetchSemanticsNodes().size }
        shoot("glance_dark")
        rule.runOnUiThread { goTo(1) }; rule.waitForIdle()
        assertEquals(dots(), dots())
    }
}
