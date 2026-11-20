package com.bos.sphere

import android.graphics.Bitmap
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertCountEquals
import androidx.test.core.app.ActivityScenario
import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.RightSide
import com.bos.sphere.feature.dock.DockItemRef
import com.bos.sphere.feature.dock.DockPresence
import com.bos.sphere.feature.dock.HomeBottomStack
import com.bos.sphere.feature.dock.WorkspaceRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Wave 26, owner build 54: "The dock overlaps with the position indicator." The real home with a dock and an indicator (the Glance
 * indicator, or the page dots of a two-page home): the dock's and the indicator's bounds never intersect and keep at least 8 dp
 * between them, at five window sizes. And an empty dock draws nothing and leaves the space to the home. Robolectric, not a device run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class DockStackW26SmokeTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Before fun freshProcess() { AppSmokeHarness.resetProcess() }
    @After fun closeApp() {
        if (scenario != null) { rule.waitForIdle(); assertFalse("a test left a global Compose snapshot write pending", Snapshot.current.hasPendingChanges()) }
        scenario?.close(); scenario = null
        AppSmokeHarness.resetProcess()
    }

    private val density get() = AppSmokeHarness.app.resources.displayMetrics.density

    private fun settle() { rule.waitForIdle(); rule.mainClock.advanceTimeBy(600); rule.waitForIdle() }

    private fun shoot(name: String) {
        rule.mainClock.advanceTimeBy(300)
        var bmp: Bitmap? = null
        scenario!!.onActivity { a ->
            val view = a.window.decorView
            val b = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val latch = java.util.concurrent.CountDownLatch(1)
            android.view.PixelCopy.request(a.window, b, { latch.countDown() }, android.os.Handler(android.os.Looper.getMainLooper()))
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            bmp = b
        }
        File(outDir, "$name.png").outputStream().use { bmp!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun startGlance(pins: Boolean = true) {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        AppSmokeHarness.configureFresh { it.copy(rightSide = RightSide.DockRail, glanceIndicator = true) }
        WorkspaceRepository.get(AppSmokeHarness.app).setPins(if (pins) listOf(DockItemRef.app(AppSmokeHarness.appKey("Alpha")), DockItemRef.app(AppSmokeHarness.appKey("Bravo"))) else emptyList())
        scenario = AppSmokeHarness.launch(); settle()
    }

    private fun startPages(pins: Boolean = true) {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        AppSmokeHarness.configure { it }
        val stores = LauncherStores.get(AppSmokeHarness.app)
        stores.layout.replace(HomeLayout(pages = listOf(HomePage(keys = listOf(AppSmokeHarness.appKey("Camera")), id = 7L), HomePage(keys = listOf(AppSmokeHarness.appKey("Delta")), id = 8L)), seeded = true))
        val wm = WorkspaceRepository.get(AppSmokeHarness.app)
        wm.setDock { it.copy(enabled = true) }
        wm.setPins(if (pins) listOf(DockItemRef.app(AppSmokeHarness.appKey("Alpha"))) else emptyList())
        scenario = AppSmokeHarness.launch(); settle()
    }

    private fun boundsDp(tagOrDesc: String, tag: Boolean): Rect {
        val n = (if (tag) rule.onNodeWithTag(tagOrDesc, useUnmergedTree = true) else rule.onNodeWithContentDescription(tagOrDesc, useUnmergedTree = true)).fetchSemanticsNode()
        val r = n.boundsInRoot
        return Rect(r.left / density, r.top / density, r.right / density, r.bottom / density)
    }

    private fun union(vararg r: Rect) = r.reduce { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }

    private fun check(label: String, dock: Rect, indicator: Rect, heightDp: Float, widthDp: Float) {
        assertFalse("dock and indicator intersect at $label: $dock vs $indicator", dock.overlaps(indicator))
        val vertical = indicator.top - dock.bottom
        val horizontallyApart = dock.left >= indicator.right + 8f || indicator.left >= dock.right + 8f
        assertTrue("dock under 8 dp from the indicator at $label: dock $dock indicator $indicator", horizontallyApart || vertical >= 8f - 0.5f)
        assertTrue("dock off the window at $label: $dock", dock.left >= -0.5f && dock.right <= widthDp + 0.5f && dock.bottom <= heightDp + 0.5f && dock.top >= 0f)
    }

    private fun glanceCheck(label: String, w: Float, h: Float) {
        startGlance()
        val dock = boundsDp("Dock", false)
        val indicator = union(boundsDp("Glance home", false), boundsDp("glance.indicator.launchpad", true))
        check(label, dock, indicator, h, w)
        shoot("w26-stack-glance-$label")
    }

    private fun pagesCheck(label: String, w: Float, h: Float) {
        startPages()
        val dock = boundsDp("Dock", false)
        val indicator = union(boundsDp("Home page", false), boundsDp("Page 2 of 2", false))
        check(label, dock, indicator, h, w)
        shoot("w26-stack-pages-$label")
    }

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun HOM_19_W26_glanceDockAndIndicator_360x640() = glanceCheck("360x640", 360f, 640f)
    @Test @Config(qualifiers = "w411dp-h891dp-xhdpi") fun HOM_19_W26_glanceDockAndIndicator_411x891() = glanceCheck("411x891", 411f, 891f)
    @Test @Config(qualifiers = "w891dp-h411dp-land-xhdpi") fun HOM_19_W26_glanceDockAndIndicator_891x411() = glanceCheck("891x411", 891f, 411f)
    @Test @Config(qualifiers = "w1280dp-h800dp-land-xhdpi") fun HOM_19_W26_glanceDockAndIndicator_1280x800() = glanceCheck("1280x800", 1280f, 800f)
    @Test @Config(qualifiers = "w280dp-h653dp-xhdpi") fun HOM_19_W26_glanceDockAndIndicator_280x653() = glanceCheck("280x653", 280f, 653f)

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun HOM_19_W26_pagesDockAndDots_360x640() = pagesCheck("360x640", 360f, 640f)
    @Test @Config(qualifiers = "w411dp-h891dp-xhdpi") fun HOM_19_W26_pagesDockAndDots_411x891() = pagesCheck("411x891", 411f, 891f)
    @Test @Config(qualifiers = "w891dp-h411dp-land-xhdpi") fun HOM_19_W26_pagesDockAndDots_891x411() = pagesCheck("891x411", 891f, 411f)
    @Test @Config(qualifiers = "w1280dp-h800dp-land-xhdpi") fun HOM_19_W26_pagesDockAndDots_1280x800() = pagesCheck("1280x800", 1280f, 800f)
    @Test @Config(qualifiers = "w280dp-h653dp-xhdpi") fun HOM_19_W26_pagesDockAndDots_280x653() = pagesCheck("280x653", 280f, 653f)

    @Test fun DCK_10_anEmptyDockDrawsNothingOnGlanceAndTheStackHasNoRoomForIt() {
        startGlance(pins = false)
        rule.onAllNodesWithContentDescription("Dock").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Workspace Home").assertCountEquals(0)
        assertFalse(DockPresence.hasItems)
        // The indicator keeps its own place.
        val ind = boundsDp("Glance home", false)
        assertTrue(ind.bottom <= 640f && ind.top >= 640f - HomeBottomStack.GLANCE_INDICATOR_TOP_DP - 1f)
        shoot("w26-stack-glance-empty-dock")
    }

    @Test fun DCK_10_anEmptyDockLeavesTheSpaceToThePagesAndItsFirstPinBringsTheRailBack() {
        startPages(pins = false)
        rule.onAllNodesWithContentDescription("Dock").assertCountEquals(0)
        val emptyPage = boundsDp("Page 2 of 2", false)
        shoot("w26-stack-pages-empty-dock")
        WorkspaceRepository.get(AppSmokeHarness.app).setPins(listOf(DockItemRef.app(AppSmokeHarness.appKey("Alpha"))))
        settle()
        rule.onNodeWithContentDescription("Dock").assertExists()
        assertTrue(DockPresence.hasItems)
        val dock = boundsDp("Dock", false)
        check("pin added", dock, union(boundsDp("Home page", false), boundsDp("Page 2 of 2", false)), 640f, 360f)
        assertEquals("the dots do not move for the dock", emptyPage.top, boundsDp("Page 2 of 2", false).top, 0.5f)
        shoot("w26-stack-pages-first-pin")
    }
}
