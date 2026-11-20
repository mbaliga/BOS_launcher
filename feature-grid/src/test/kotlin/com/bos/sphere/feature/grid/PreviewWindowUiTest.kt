package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.center
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.click
import com.bos.sphere.core.data.TolerantPrefs
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.core.quality.MotionPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * CFG-18 (wave 39): the preview as a floating window inside the real Ground Control screen: move by the bar, resize by the corner
 * handle and by pinch, minimise to a pill and restore, remembered per orientation in the preference file, keys, screen reader
 * actions, right-to-left, landscape, reduced motion, large font. Screenshots go to build/screenshots (read by hand, see the status file).
 * Still device-only: how the drag and the pinch feel, and gesture conflicts with the settings list scroll.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class PreviewWindowUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val none = MotionPrefs(0f, 0f, 0f)
    private val G = PreviewWindowGeometry

    @After fun reset() { ReducedMotionTesting.force(null); Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) } }

    private fun categories() = listOf(
        SettingCategory("Home", Glyphs.Apps, listOf(
            SettingItem.Toggle("Labels under icons", Glyphs.Apps, true) {},
            SettingItem.Toggle("Badges", Glyphs.Apps, false) {},
            SettingItem.Toggle("Plate", Glyphs.Apps, false) {},
        )),
    )

    private var showWindow by mutableStateOf(true)
    /** Robolectric's ldrtl qualifier does not reach Configuration.layoutDirection here, so right-to-left is injected (the app passes nothing and reads the configuration). */
    private var rtl: Boolean? = null

    private fun show(reduced: Boolean = true) {
        ReducedMotionTesting.force(if (reduced) none else MotionPrefs(1f, 1f, 1f))
        showWindow = true
        rule.mainClock.autoAdvance = true
        rule.setContent {
            val capture = rememberHomeCapture()
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.fillMaxSize().captureHome(capture).background(Color(0xFF3F6FD8)))
                SettingsSpace(
                    visible = true, categories = categories(), initialCategory = 0, sounds = null, onClose = {},
                    modifier = Modifier.fillMaxSize(),
                    previewDock = { if (showWindow) SettingsPreviewDock(capture, rtl = rtl) },
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_500)
        rule.waitForIdle()
        drawOnce()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
    }

    private fun drawOnce() {
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        runCatching { rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) } }
    }

    private fun bar(): DpRect = rule.onNodeWithTag(PreviewWindowTags.BAR).getUnclippedBoundsInRoot().let { DpRect(it.left, it.top, it.right, it.bottom) }
    private fun content(): DpRect = rule.onNodeWithTag(PreviewWindowTags.CONTENT).getUnclippedBoundsInRoot().let { DpRect(it.left, it.top, it.right, it.bottom) }
    private val d get() = rule.activity.resources.displayMetrics.density
    private fun areaW() = rule.activity.findViewById<android.view.View>(android.R.id.content).width / d
    private fun areaH() = rule.activity.findViewById<android.view.View>(android.R.id.content).height / d
    private fun prefs() = TolerantPrefs.of(rule.activity.applicationContext, PreviewWindowStore.FILE)
    private fun settle() { rule.mainClock.advanceTimeBy(600); rule.waitForIdle() }

    private fun dragBar(dxPx: Float, dyPx: Float) {
        rule.onNodeWithTag(PreviewWindowTags.BAR).performTouchInput { down(Offset(width * 0.3f, height / 2f)); moveBy(Offset(dxPx, dyPx)); up() }
        settle()
    }

    @Test fun CFG_18_byDefaultTheWindowIsTopEndUnderTheHeaderWithTheOldSizeAndNoPill() {
        show()
        val b = bar()
        assertEquals(areaW() - 12f, b.right.value, 1.5f)
        assertEquals(64f, b.top.value, 1.5f)
        assertEquals(G.BAR_DP, b.bottom.value - b.top.value, 1.5f)
        val old = DockGeometry.size(areaW(), areaH(), rule.activity.findViewById<android.view.View>(android.R.id.content).width, rule.activity.findViewById<android.view.View>(android.R.id.content).height)
        val c = content()
        assertEquals(old.widthDp, c.right.value - c.left.value, 1.5f)
        assertEquals(old.heightDp, c.bottom.value - c.top.value, 1.5f)
        rule.onAllNodesWithTag(PreviewWindowTags.PILL).assertCountEquals(0)
    }

    @Test fun CFG_18_draggingTheBarMovesTheWholeWindowAndItStaysInsideTheArea() {
        show()
        val before = bar()
        dragBar(-150f, 200f)
        val after = bar()
        assertEquals("moved by 75dp to the left", before.left.value - 75f, after.left.value, 2f)
        assertEquals("moved by 100dp down", before.top.value + 100f, after.top.value, 2f)
        assertEquals("the picture moved with the bar", content().top.value, after.bottom.value, 1.5f)
        dragBar(-5000f, -5000f)
        val corner = bar()
        assertTrue("never left of the area: ${corner.left}", corner.left.value >= -0.5f)
        assertTrue("never above the area: ${corner.top}", corner.top.value >= -0.5f)
        dragBar(9000f, 9000f)
        val far = bar()
        assertTrue(far.right.value <= areaW() + 0.5f)
        assertTrue("the bar stays reachable at the bottom", far.bottom.value <= areaH() + 0.5f)
    }

    @Test fun CFG_18_releasingNearAnEdgeSnapsToItsMarginInstantlyUnderReducedMotion() {
        show(reduced = true)
        val b = bar()
        // end 20dp from the right edge: the end margin is 12dp
        rule.onNodeWithTag(PreviewWindowTags.BAR).performTouchInput { down(Offset(width * 0.3f, height / 2f)); moveBy(Offset(-40f, 0f)); up() }
        assertEquals("snapped without advancing the clock", areaW() - 12f, bar().right.value, 1.5f)
        assertTrue(b.right.value > 0)
    }

    @Test fun CFG_18_withMotionOnTheSnapIsAnimatedAndStillEndsOnTheMargin() {
        show(reduced = false)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag(PreviewWindowTags.BAR).performTouchInput { down(Offset(width * 0.3f, height / 2f)); moveBy(Offset(-40f, 0f)); up() }
        rule.mainClock.advanceTimeBy(40)
        val mid = bar().right.value
        rule.mainClock.advanceTimeBy(600)
        val end = bar().right.value
        assertEquals(areaW() - 12f, end, 1.5f)
        assertTrue("it was still on its way after 40ms ($mid, $end)", mid < end - 0.2f)
    }

    @Test fun CFG_18_aDoubleTapOnTheBarResetsThePositionButKeepsTheSize() {
        show()
        val start = bar()
        dragBar(-300f, 400f)
        assertTrue(bar().left.value < start.left.value - 100f)
        rule.onNodeWithTag(PreviewWindowTags.BAR).performTouchInput { doubleClick(Offset(width * 0.3f, height / 2f)) }
        settle()
        assertEquals(start.left.value, bar().left.value, 1.5f)
        assertEquals(start.top.value, bar().top.value, 1.5f)
    }

    @Test fun CFG_18_theCornerHandleResizesKeepingTheAspectBetweenTheMinimumAndTheMaximum() {
        show()
        val c0 = content()
        val aspect0 = (c0.right.value - c0.left.value) / (c0.bottom.value - c0.top.value)
        val w0 = c0.right.value - c0.left.value
        rule.onNodeWithTag(PreviewWindowTags.RESIZE).performTouchInput { down(center); moveBy(Offset(-60f, 200f)); up() } // diagonal pull, mostly down
        settle()
        val c1 = content()
        val w1 = c1.right.value - c1.left.value
        assertTrue("grew from $w0 to $w1", w1 > w0 + 5f)
        assertEquals("aspect kept", aspect0, w1 / (c1.bottom.value - c1.top.value), 0.03f)
        rule.onNodeWithTag(PreviewWindowTags.RESIZE).performTouchInput { down(center); moveBy(Offset(-3000f, -3000f)); up() }
        settle()
        val c2 = content()
        assertEquals("never below the 72dp long side", 72f, maxOf(c2.right.value - c2.left.value, c2.bottom.value - c2.top.value), 1f)
        rule.onNodeWithTag(PreviewWindowTags.RESIZE).performTouchInput { down(center); moveBy(Offset(3000f, 3000f)); up() }
        settle()
        val c3 = content()
        val long3 = maxOf(c3.right.value - c3.left.value, c3.bottom.value - c3.top.value)
        assertEquals("never above 70 percent of the short side", minOf(areaW(), areaH()) * 0.7f, long3, 1.5f)
    }

    @Test fun CFG_18_twoFingerPinchOnThePictureResizesIt() {
        show()
        val w0 = content().let { it.right.value - it.left.value }
        rule.onNodeWithTag(PreviewWindowTags.CONTENT).performTouchInput {
            pinch(Offset(centerX - 10f, centerY), Offset(centerX - 40f, centerY), Offset(centerX + 10f, centerY), Offset(centerX + 40f, centerY))
        }
        settle()
        val w1 = content().let { it.right.value - it.left.value }
        assertTrue("pinch out grew it from $w0 to $w1", w1 > w0 + 5f)
    }

    @Test fun CFG_18_minimiseShowsAPillAndNoPictureAndRestoreBringsTheWholeWindowBack() {
        show()
        rule.onNodeWithContentDescription("Minimise").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithContentDescription("Live preview of your home").assertCountEquals(0)
        rule.onAllNodesWithTag(PreviewWindowTags.CONTENT).assertCountEquals(0)
        val pill = rule.onNodeWithTag(PreviewWindowTags.PILL).getUnclippedBoundsInRoot()
        assertEquals(112f, pill.right.value - pill.left.value, 1.5f)
        assertEquals(40f, pill.bottom.value - pill.top.value, 1.5f)
        assertEquals("the pill keeps the end edge", areaW() - 12f, pill.right.value, 1.5f)
        rule.onNodeWithTag(PreviewWindowTags.PILL).performClick()
        rule.mainClock.advanceTimeBy(600) // a single tap waits for a possible second tap
        rule.waitForIdle()
        rule.onAllNodesWithTag(PreviewWindowTags.PILL).assertCountEquals(0)
        rule.onNodeWithContentDescription("Live preview of your home").assertExists()
    }

    @Test fun CFG_18_thePillCanBeDraggedToo() {
        show()
        rule.onNodeWithContentDescription("Minimise").performClick()
        rule.waitForIdle()
        val before = rule.onNodeWithTag(PreviewWindowTags.PILL).getUnclippedBoundsInRoot()
        rule.onNodeWithTag(PreviewWindowTags.PILL).performTouchInput { down(Offset(width * 0.3f, height / 2f)); moveBy(Offset(-200f, 240f)); up() }
        settle()
        val after = rule.onNodeWithTag(PreviewWindowTags.PILL).getUnclippedBoundsInRoot()
        assertEquals(before.left.value - 100f, after.left.value, 2f)
        assertEquals(before.top.value + 120f, after.top.value, 2f)
        rule.onAllNodesWithTag(PreviewWindowTags.PILL).assertCountEquals(1)
    }

    @Test fun CFG_18_positionSizeAndMinimisedAreRememberedInThePreferenceFileAndComeBackAfterTheWindowIsRecomposedFromScratch() {
        show()
        dragBar(-150f, 200f)
        rule.onNodeWithTag(PreviewWindowTags.RESIZE).performTouchInput { down(center); moveBy(Offset(60f, 120f)); up() }
        settle()
        val b = bar(); val c = content()
        assertNotNull("portrait entry written", prefs().getString("port", null))
        assertNull("landscape untouched", prefs().getString("land", null))
        rule.runOnUiThread { showWindow = false }; rule.waitForIdle()
        rule.onAllNodesWithTag(PreviewWindowTags.BAR).assertCountEquals(0)
        rule.runOnUiThread { showWindow = true }; rule.waitForIdle()
        assertEquals(b.left.value, bar().left.value, 1f)
        assertEquals(b.top.value, bar().top.value, 1f)
        assertEquals(c.right.value - c.left.value, content().let { it.right.value - it.left.value }, 1f)
        rule.onNodeWithContentDescription("Minimise").performClick(); rule.waitForIdle()
        assertTrue(prefs().getBoolean("minimised", false))
        rule.runOnUiThread { showWindow = false }; rule.waitForIdle()
        rule.runOnUiThread { showWindow = true }; rule.waitForIdle()
        rule.onNodeWithTag(PreviewWindowTags.PILL).assertExists()
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun CFG_18_inLandscapeTheWindowHasItsOwnRememberedPlaceAndTheSameDefaultAnchor() {
        show()
        val b0 = bar()
        assertEquals(areaW() - 12f, b0.right.value, 1.5f)
        dragBar(-300f, 100f)
        assertNotNull(prefs().getString("land", null))
        assertNull(prefs().getString("port", null))
        assertTrue("fully inside", bar().right.value <= areaW() + 0.5f && content().bottom.value <= areaH() + 0.5f)
    }

    @Test @Config(sdk = [34], qualifiers = "ldrtl-w411dp-h891dp-port-xhdpi")
    fun CFG_18_inARightToLeftLocaleTheWindowStartsTopLeftAndTheResizeHandleIsAtTheBottomLeft() {
        rtl = true
        show()
        assertEquals(12f, bar().left.value, 1.5f)
        val h = rule.onNodeWithTag(PreviewWindowTags.RESIZE).getUnclippedBoundsInRoot()
        val c = content()
        assertTrue("handle at the left: ${h.left} vs window ${bar().left}", h.left.value <= bar().left.value + 1f)
        assertTrue("handle at the bottom: ${h.bottom}", h.bottom.value >= c.bottom.value - 1f)
        val w0 = c.right.value - c.left.value
        rule.onNodeWithTag(PreviewWindowTags.RESIZE).performTouchInput { down(center); moveBy(Offset(-80f, 160f)); up() } // left and down grows
        settle()
        assertTrue(content().let { it.right.value - it.left.value } > w0 + 5f)
    }

    @Test fun CFG_18_arrowKeysMoveByTheStepAndShiftArrowsResize() {
        show()
        rule.onNodeWithTag(PreviewWindowTags.BAR).requestFocus()
        val b = bar()
        rule.onNodeWithTag(PreviewWindowTags.BAR).performKeyInput { pressKey(Key.DirectionLeft) }
        assertEquals(b.left.value - 16f, bar().left.value, 1f)
        rule.onNodeWithTag(PreviewWindowTags.BAR).performKeyInput { pressKey(Key.DirectionDown) }
        assertEquals(b.top.value + 16f, bar().top.value, 1f)
        val w0 = content().let { it.right.value - it.left.value }
        val h0 = content().let { it.bottom.value - it.top.value }
        rule.onNodeWithTag(PreviewWindowTags.BAR).performKeyInput { keyDown(Key.ShiftLeft); pressKey(Key.DirectionRight); keyUp(Key.ShiftLeft) }
        assertTrue("shift+arrow made the picture larger", content().let { it.bottom.value - it.top.value } > h0 + 8f)
        rule.onNodeWithTag(PreviewWindowTags.BAR).performKeyInput { keyDown(Key.ShiftLeft); pressKey(Key.DirectionLeft); pressKey(Key.DirectionLeft); keyUp(Key.ShiftLeft) }
        assertTrue(content().let { it.right.value - it.left.value } < w0)
    }

    @Test fun CFG_18_escapeAndEnterOnTheMinimiseButtonToggleTheWindow() {
        show()
        fun pills() = rule.onAllNodesWithTag(PreviewWindowTags.PILL).fetchSemanticsNodes().size
        rule.onNodeWithTag(PreviewWindowTags.MINIMISE).requestFocus()
        rule.onNodeWithTag(PreviewWindowTags.MINIMISE).performKeyInput { pressKey(Key.Escape) }
        rule.waitForIdle()
        assertEquals("Escape on the button minimises", 1, pills())
        rule.onNodeWithTag(PreviewWindowTags.PILL).requestFocus()
        rule.onNodeWithTag(PreviewWindowTags.PILL).performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()
        assertEquals("Enter on the pill restores", 0, pills())
        rule.onNodeWithTag(PreviewWindowTags.MINIMISE).requestFocus()
        rule.onNodeWithTag(PreviewWindowTags.MINIMISE).performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()
        assertEquals("Enter on the button minimises", 1, pills())
    }

    private fun act(tag: String, label: String) {
        val list = rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        val a = list.firstOrNull { it.label == label } ?: error("no action '$label' in ${list.map { it.label }}")
        rule.runOnUiThread { a.action() }
        rule.waitForIdle()
    }

    @Test fun CFG_18_theScreenReaderGetsMoveLargerSmallerMinimiseRestoreAndResetActions() {
        show()
        val labels = rule.onNodeWithTag(PreviewWindowTags.BAR).fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf("Move up", "Move down", "Move left", "Move right", "Larger", "Smaller", "Minimise", "Reset position"), labels)
        val b = bar()
        act(PreviewWindowTags.BAR, "Move left"); assertEquals(b.left.value - 16f, bar().left.value, 1f)
        act(PreviewWindowTags.BAR, "Move down"); assertEquals(b.top.value + 16f, bar().top.value, 1f)
        act(PreviewWindowTags.BAR, "Move up"); act(PreviewWindowTags.BAR, "Move right")
        assertEquals(b.left.value, bar().left.value, 1f)
        val w0 = content().let { it.bottom.value - it.top.value }
        act(PreviewWindowTags.BAR, "Larger"); assertTrue(content().let { it.bottom.value - it.top.value } > w0 + 8f)
        act(PreviewWindowTags.BAR, "Smaller"); assertEquals(w0, content().let { it.bottom.value - it.top.value }, 1f)
        act(PreviewWindowTags.BAR, "Move left"); act(PreviewWindowTags.BAR, "Reset position")
        assertEquals(b.left.value, bar().left.value, 1.5f)
        act(PreviewWindowTags.BAR, "Minimise")
        val pillLabels = rule.onNodeWithTag(PreviewWindowTags.PILL).fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertTrue("Restore" in pillLabels && "Minimise" !in pillLabels)
        act(PreviewWindowTags.PILL, "Restore")
        rule.onNodeWithTag(PreviewWindowTags.CONTENT).assertExists()
    }

    @Test fun CFG_18_theHandleAndButtonsHaveA48dpTouchTargetAndAName() {
        show()
        val h = rule.onNodeWithTag(PreviewWindowTags.RESIZE).getUnclippedBoundsInRoot()
        assertTrue(h.right.value - h.left.value >= 47.5f && h.bottom.value - h.top.value >= 47.5f)
        val m = rule.onNodeWithTag(PreviewWindowTags.MINIMISE).getUnclippedBoundsInRoot()
        assertTrue(m.right.value - m.left.value >= 47.5f && m.bottom.value - m.top.value >= 47.5f)
        rule.onNodeWithContentDescription("Resize preview").assertExists()
    }

    @Test fun CFG_18_touchesOnThePictureAndTheBarNeverReachTheSettingsUnderneath() {
        ReducedMotionTesting.force(none)
        var underneath by mutableIntStateOf(0)
        rule.setContent {
            val capture = rememberHomeCapture()
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.fillMaxSize().captureHome(capture).background(Color(0xFF3F6FD8)))
                Box(Modifier.fillMaxSize().clickable { underneath++ })
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) { SettingsPreviewDock(capture) }
            }
        }
        rule.mainClock.advanceTimeBy(500); drawOnce(); rule.waitForIdle(); rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag(PreviewWindowTags.CONTENT).performTouchInput { click(center) }
        rule.onNodeWithTag(PreviewWindowTags.BAR).performTouchInput { click(Offset(width * 0.3f, height / 2f)) }
        assertEquals("nothing leaks through the window", 0, underneath)
        rule.onNodeWithTag(PreviewWindowTags.CONTENT).performTouchInput { click(Offset(-300f, centerY)) } // well outside the window, on the underlay
        assertEquals("outside the window the underlay still works", 1, underneath)
    }

    @Test fun CFG_18_thePictureIsStillOneLabelledNodeWithNoClickNoFocusAndNoChildren() {
        show()
        val nodes = rule.onAllNodesWithContentDescription("Live preview of your home")
        nodes.assertCountEquals(1)
        val n = nodes[0].fetchSemanticsNode()
        assertTrue(!n.config.contains(SemanticsActions.OnClick))
        assertTrue(!n.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Focused))
        assertEquals(0, n.children.size)
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi", fontScale = 2f)
    fun CFG_18_atTwiceTheFontSizeTheBarKeepsItsHeightAndTheWindowStaysInsideTheArea() {
        show()
        val b = bar()
        assertEquals(G.BAR_DP, b.bottom.value - b.top.value, 1.5f)
        assertTrue(b.right.value <= areaW() + 0.5f && b.left.value >= -0.5f)
        rule.onNodeWithContentDescription("Minimise").assertExists()
    }

    // ---- screenshots (read by hand) ----

    private fun shot(name: String) {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val latch = java.util.concurrent.CountDownLatch(1)
        val thread = android.os.HandlerThread("copy").also { it.start() }
        rule.runOnUiThread { android.view.PixelCopy.request(rule.activity.window, bmp, { latch.countDown() }, android.os.Handler(thread.looper)) }
        latch.await(10, java.util.concurrent.TimeUnit.SECONDS); thread.quit()
        val dir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shots(prefix: String) {
        show()
        shot("${prefix}_default")
        dragBar(-400f, 500f)
        rule.onNodeWithTag(PreviewWindowTags.RESIZE).performTouchInput { down(center); moveBy(Offset(60f, 120f)); up() }
        settle()
        shot("${prefix}_moved_resized")
        rule.onNodeWithContentDescription("Minimise").performClick(); rule.waitForIdle()
        shot("${prefix}_minimised")
    }

    @Test fun CFG_18_screenshotsPortraitDark() = shots("preview_portrait_dark")

    @Test fun CFG_18_screenshotsPortraitLight() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF) }
        shots("preview_portrait_light")
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun CFG_18_screenshotsLandscapeDark() = shots("preview_landscape_dark")

    @Test @Config(sdk = [34], qualifiers = "ldrtl-w411dp-h891dp-port-xhdpi")
    fun CFG_18_screenshotsRtlDefault() { rtl = true; show(); shot("preview_rtl_default") }
}
