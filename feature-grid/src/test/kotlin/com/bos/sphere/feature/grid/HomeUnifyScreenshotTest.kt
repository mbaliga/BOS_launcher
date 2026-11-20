package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HomeDeck
import com.bos.sphere.core.data.HomeOrder
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.RightDestination
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.ThemeVariant
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * HOM-01, HOM-23 (wave 28, homeUnify): the unified home as the app composes it (one pager: Glance, then the grid pages or the scroll; ONE PageIndicator
 * above it), shot standalone because the whole app never idles for a window capture under Robolectric. PNGs in `feature-grid/build/screenshots/w28-*.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeUnifyScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    private val apps: List<AppEntry> = List(30) { i ->
        AppEntry("demo.app$i", ComponentName("demo.app$i", "demo.app$i.Main"), "App $i", Process.myUserHandle()) { ColorDrawable(android.graphics.Color.HSVToColor(floatArrayOf((i * 41f) % 360f, 0.55f, 0.9f))) }
    }
    private val byKey = apps.associateBy { it.key }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(1_500)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        Canvas(out).also { it.drawColor(0xFF000000.toInt()); it.drawBitmap(bmp, 0f, 0f, null) }
        File(outDir, "$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The home the way HomeScreen composes it: one pager, Glance as stop 0, one indicator at one place. */
    @Composable
    private fun UnifiedHome(prefs: LauncherPrefs, startStop: Int, pageGroups: List<List<AppEntry>>) {
        val glanceOn = prefs.glanceEnabled
        val order = prefs.homeOrder
        val pager = rememberPagerState(startStop) { HomeDeck.outerCount(glanceOn, prefs.homePages) }
        val grid = rememberGridHomeState()
        val scope = rememberCoroutineScope()
        val pages = pageGroups.mapIndexed { i, g -> HomePage(g.map { it.key }, id = i + 1L) }
        val onGlance = glanceOn && pager.currentPage == 0
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                HorizontalPager(state = pager, reverseLayout = order.rtl, modifier = Modifier.fillMaxSize().testTag("home.pager")) { stop ->
                    if (HomeDeck.surfaceAt(glanceOn, stop) == com.bos.sphere.core.data.HomeShown.Glance) {
                        CompositionLocalProvider(LocalLayoutDirection provides if (order.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                            GlanceHomeForTest(
                                prefs = prefs, media = null, hotspots = emptyList(), appsByKey = emptyMap(),
                                rightDestinations = listOf(RightDestination.Recents), hasLaunchpad = true,
                                onRun = { _, _ -> }, onPrevious = {}, onPlayPause = {}, onNext = {}, indicator = false, modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else if (prefs.homePages == HomePages.Continuous) {
                        ColumnScrollHome(keys = apps.map { it.key }, appsByKey = byKey, extras = null, order = order, topBand = 56.dp, bottomBand = 84.dp, onLaunch = {}, onAppLongPress = {})
                    } else {
                        GridHome(
                            pages = pages, appsByKey = byKey, prefs = prefs, isLandscape = false, state = grid, gesturesBound = emptySet(),
                            capacityOf = { it.capacity(false, prefs) }, onLaunch = {}, onAppLongPress = {}, onPagesChange = {}, onEmptyLongPress = {}, onGesture = {}, onClockTap = {},
                            onTileLongPress = { _, _ -> }, drawIndicator = false,
                        )
                    }
                }
            }
            val dots = HomeDeck.stops(glanceOn, prefs.homePages, pages.size) - (if (glanceOn) 1 else 0)
            PageIndicator(
                count = dots, current = grid.currentPage.coerceIn(0, (dots - 1).coerceAtLeast(0)), vertical = false,
                onSelect = { pg -> val (stop, page) = HomeDeck.target(glanceOn, prefs.homePages, pg + (if (glanceOn) 1 else 0)); scope.launch { pager.scrollToPage(stop); if (page != null) grid.goToPage(page, animate = false) } },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), rtl = order.rtl,
                onMenu = {}, menuLabel = "Open Comms", menuTag = "glance.indicator.launchpad",
                onGlance = if (glanceOn) ({ scope.launch { pager.scrollToPage(0) } }) else null, glanceCurrent = onGlance,
                right = listOf(RightDestination.Recents), quiet = onGlance,
            )
        }
        LaunchedEffect(Unit) { grid.goToPage(0, animate = false) }
    }

    private fun indicatorCount() = rule.onAllNodesWithTag("page.indicator", useUnmergedTree = true).fetchSemanticsNodes().size
    private fun indicatorBounds() = rule.onNodeWithTag("page.indicator", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private val twoPages get() = listOf(apps.take(9), apps.drop(9).take(9))

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_w28_glanceAndPageOneShareOneIndicatorInOnePlaceInLeftToRight() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
        rule.mainClock.autoAdvance = false
        val prefs = LauncherPrefs(homePages = HomePages.Paginated)
        rule.setContent { UnifiedHome(prefs, 0, twoPages) }
        rule.mainClock.advanceTimeBy(600)
        assertEquals(1, indicatorCount()); val onGlance = indicatorBounds()
        shoot("w28-unified-glance-ltr")
        rule.runOnUiThread { }; rule.onNodeWithTag("pages.dot.0", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.mainClock.advanceTimeBy(2_000)
        assertTrue("Glance is out of the window", rule.onAllNodesWithTag("glance.surface", useUnmergedTree = true).fetchSemanticsNodes().none { it.boundsInRoot.right > 0f && it.boundsInRoot.left < 822f })
        assertEquals("still exactly one indicator on page 1", 1, indicatorCount()); val onPage = indicatorBounds()
        assertEquals("same bottom", onGlance.bottom, onPage.bottom, 1f); assertEquals("same height", onGlance.height, onPage.height, 1f); assertEquals("same centre", onGlance.center.x, onPage.center.x, 1f)
        shoot("w28-unified-page1-ltr")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_w28_rightToLeftOrderPutsGlanceFirstAtTheRightOnBothStops() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
        rule.mainClock.autoAdvance = false
        val prefs = LauncherPrefs(homePages = HomePages.Paginated, homeOrder = HomeOrder.RightToLeft)
        rule.setContent { UnifiedHome(prefs, 1, twoPages) }
        rule.mainClock.advanceTimeBy(600)
        assertEquals(1, indicatorCount())
        val house = rule.onNodeWithTag("pages.glancedot", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val dot0 = rule.onNodeWithTag("pages.dot.0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Glance is the first stop, at the right", house.left > dot0.left)
        shoot("w28-unified-page1-rtl")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_01_w28_continuousPortraitThirtyIconsAfterGlanceWithTheSameIndicator() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
        rule.mainClock.autoAdvance = false
        rule.setContent { UnifiedHome(LauncherPrefs(homePages = HomePages.Continuous), 1, listOf(apps)) }
        rule.mainClock.advanceTimeBy(600)
        assertEquals(1, indicatorCount())
        assertTrue(rule.onAllNodesWithTag("pages.dot.0", useUnmergedTree = true).fetchSemanticsNodes().size == 1 && rule.onAllNodesWithTag("pages.dot.1", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        shoot("w28-unified-continuous-portrait-30")
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_01_w28_continuousLandscapeThirtyIconsAfterGlanceWithTheSameIndicator() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
        rule.mainClock.autoAdvance = false
        rule.setContent { UnifiedHome(LauncherPrefs(homePages = HomePages.Continuous), 1, listOf(apps)) }
        rule.mainClock.advanceTimeBy(600)
        assertEquals(1, indicatorCount())
        shoot("w28-unified-continuous-landscape-30")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_01_w28_continuousRightToLeftStartsAtTheRight() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
        rule.mainClock.autoAdvance = false
        rule.setContent { UnifiedHome(LauncherPrefs(homePages = HomePages.Continuous, homeOrder = HomeOrder.RightToLeft), 1, listOf(apps)) }
        rule.mainClock.advanceTimeBy(900)
        shoot("w28-unified-continuous-rtl-30")
    }
}
