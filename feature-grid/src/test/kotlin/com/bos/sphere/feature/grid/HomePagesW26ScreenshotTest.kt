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
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.LayoutDirection
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HomeLayoutMath
import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomeOrder
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.ThemeVariant
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
 * HOM-01, HOM-23 (wave 26): Glance with its deck dots, the paginated pages with the Glance stop, Glance off and the right to left order, each drawn in the
 * four combinations of order x language direction where it matters. Writes PNGs to `feature-grid/build/screenshots/w26-*.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomePagesW26ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private val apps: List<AppEntry> = List(18) { i ->
        AppEntry("demo.app$i", ComponentName("demo.app$i", "demo.app$i.Main"), "App $i", Process.myUserHandle()) { ColorDrawable(android.graphics.Color.HSVToColor(floatArrayOf((i * 41f) % 360f, 0.55f, 0.9f))) }
    }
    private val byKey = apps.associateBy { it.key }
    private val twoPages get() = listOf(HomePage(apps.take(9).map { it.key }, id = 1L), HomePage(apps.drop(9).map { it.key }, id = 2L))

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(1_500)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        Canvas(out).also { it.drawColor(0xFF000000.toInt()); it.drawBitmap(bmp, 0f, 0f, null) }
        File(outDir, "$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun glance(order: HomeOrder, languageRtl: Boolean) {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
        rule.setContent {
            // The home sets the layout direction from the ORDER (the language never decides): that is what the app does around the Glance surface.
            CompositionLocalProvider(LocalLayoutDirection provides if (order.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                GlanceHomeForTest(
                    prefs = LauncherPrefs(homeOrder = order), media = null, hotspots = emptyList(), appsByKey = emptyMap(),
                    hasLaunchpad = true,
                    onRun = { _, _ -> }, onPrevious = {}, onPlayPause = {}, onNext = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    @Composable
    private fun Pages(prefs: LauncherPrefs, withGlance: Boolean) {
        val state = rememberGridHomeState()
        val pages = twoPages
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            GridHome(
                pages = pages, appsByKey = byKey, prefs = prefs, isLandscape = false, state = state, gesturesBound = emptySet(),
                capacityOf = { it.capacity(false, prefs) }, onLaunch = {}, onAppLongPress = {}, onPagesChange = {}, onEmptyLongPress = {}, onGesture = {}, onClockTap = {},
                onTileLongPress = { _, _ -> }, drawIndicator = false,
            )
            // wave 28: the home draws the ONE indicator above the pager; Glance is its first stop when it is on
            PageIndicator(
                count = pages.size, current = state.currentPage, vertical = false, onSelect = {}, rtl = prefs.homeOrder.rtl,
                onGlance = if (withGlance) ({}) else null, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 4.dp),
            )
        }
        LaunchedEffect(Unit) { state.goToPage(0, animate = false) }
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_w28_glanceDrawsTheOnePageIndicatorWithTheHouseAsItsLitFirstStopAndNoSecondSetOfDots() {
        rule.mainClock.autoAdvance = false
        glance(HomeOrder.LeftToRight, languageRtl = false)
        rule.onNodeWithTag("pages.glancedot", useUnmergedTree = true).assertIsDisplayed()
        assertTrue("no second set of dots on Glance", rule.onAllNodesWithTagCount("glance.deckdots") == 0)
        val comms = rule.onNodeWithTag("glance.indicator.launchpad", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val house = rule.onNodeWithTag("pages.glancedot", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the row is the Comms glyph then the house", comms.left < house.left)
        assertEquals("no trailing dot for the right panel (it read as a page that does not exist)", 0, rule.onAllNodesWithTag("glance.indicator.right.Recents", useUnmergedTree = true).fetchSemanticsNodes().size + rule.onAllNodesWithTag("glance.indicator.right.Dock", useUnmergedTree = true).fetchSemanticsNodes().size)
        shoot("w28-glance-indicator-ltr")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_rightToLeftOrderMirrorsTheGlanceIndicatorRow() {
        rule.mainClock.autoAdvance = false
        glance(HomeOrder.RightToLeft, languageRtl = false)
        val comms = rule.onNodeWithTag("glance.indicator.launchpad", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val house = rule.onNodeWithTag("pages.glancedot", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("in right to left order the Comms glyph is at the right of the house", comms.left > house.left)
        shoot("w28-glance-indicator-rtl-order")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_01_paginatedPagesShowTheGlanceStopThenThePageDots() {
        rule.mainClock.autoAdvance = false
        val prefs = LauncherPrefs(homePages = HomePages.Paginated)
        rule.setContent { Pages(prefs, withGlance = true) }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag("pages.glancedot", useUnmergedTree = true).assertIsDisplayed()
        shoot("w28-paginated-2pages-glance-on")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_22_withGlanceOffThePagesHaveNoGlanceStop() {
        rule.mainClock.autoAdvance = false
        rule.setContent { Pages(LauncherPrefs(glanceEnabled = false), withGlance = false) }
        rule.mainClock.advanceTimeBy(500)
        assertTrue(rule.onAllNodesWithTagCount("pages.glancedot") == 0)
        shoot("w28-paginated-2pages-glance-off")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_rightToLeftOrderMirrorsThePagesAndStartsAtTheRight() {
        rule.setContent { Pages(LauncherPrefs(homeOrder = HomeOrder.RightToLeft), withGlance = true) }
        rule.waitForIdle()
        val cell0 = rule.onAllNodesWithContentDescription("App 0", substring = true, useUnmergedTree = true)[0]
        cell0.assertIsDisplayed()
        val b0 = cell0.fetchSemanticsNode().boundsInRoot
        assertTrue("App 0 is the right-most icon of the first row: ${b0.left}", b0.left > 411f * 2f * 0.55f)
        shoot("w28-paginated-2pages-rtl-order")
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(t: String) =
        onAllNodesWithTag(t, useUnmergedTree = true).fetchSemanticsNodes().size
}

