package com.bos.sphere.feature.grid

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * CFG-18: the live preview dock inside the real Ground Control screen ([SettingsSpace]), at composable level: where it sits, its
 * size and aspect for phone, landscape, tablet and small windows, that it stays clear of the settings rows, that it takes no
 * focus or clicks, that it is one labelled node, and that nothing is drawn before the home was captured. Pixels are read with
 * `PixelCopy` of the window (Robolectric native graphics, no TextureView; `captureToImage` and `View.draw` do not work here).
 * Still a device step: the real home's pixels (this test uses a solid colour) and the live update cadence.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class SettingsPreviewDockUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun categories() = listOf(
        SettingCategory(
            "Home", Glyphs.Apps,
            listOf(
                SettingItem.Toggle("Labels under icons", Glyphs.Apps, true) {},
                SettingItem.Toggle("Badges", Glyphs.Apps, false) {},
            ),
        ),
        SettingCategory("Look", Glyphs.Apps, listOf(SettingItem.Toggle("Plate", Glyphs.Apps, false) {})),
    )

    private fun show() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val capture = rememberHomeCapture()
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.fillMaxSize().captureHome(capture).background(Color(0xFFE53935)))
                SettingsSpace(
                    visible = true, categories = categories(), initialCategory = 0, sounds = null, onClose = {},
                    modifier = Modifier.fillMaxSize(),
                    previewDock = { SettingsPreviewDock(capture) },
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_500)
        rule.waitForIdle()
        drawOnce() // the dock appears once the home has drawn and recorded itself
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
    }

    private fun drawOnce(): Bitmap {
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        runCatching { rule.runOnUiThread { view.draw(Canvas(bmp)) } } // the software canvas may refuse drawLayer after recording
        return bmp
    }

    @Test fun CFG_18_theDockSitsTopEndInsideGroundControlWithTheHomeAspectAndTheGeometrySize() {
        show()
        val dock = rule.onNodeWithContentDescription("Live preview of your home").getUnclippedBoundsInRoot()
        val d = rule.activity.resources.displayMetrics.density
        val content = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val homeW = content.width; val homeH = content.height
        val box = DockGeometry.size(411f, 891f, homeW, homeH)
        assertEquals("dock width follows DockGeometry", box.widthDp, dock.width.value, 1.5f)
        assertEquals("dock height follows DockGeometry", box.heightDp, dock.height.value, 1.5f)
        assertEquals("the dock keeps the home's aspect", homeW.toFloat() / homeH, dock.width.value / dock.height.value, 0.02f)
        assertEquals("top-end anchor: 12dp from the end", homeW / d - 12f, dock.right.value, 1.5f)
        assertEquals("64dp under the top", 64f, dock.top.value, 1.5f)
        assertTrue("never above the 170dp cap", maxOf(dock.width.value, dock.height.value) <= 170f)
    }

    @Test fun CFG_18_theDockNeverCoversTheSettingRowsOrTheirSwitches() {
        show()
        val dock = rule.onNodeWithContentDescription("Live preview of your home").getUnclippedBoundsInRoot()
        for (label in listOf("Labels under icons", "Badges")) {
            val row = rule.onNodeWithText(label).getUnclippedBoundsInRoot()
            val overlapsX = row.left < dock.right && row.right > dock.left
            val overlapsY = row.top < dock.bottom && row.bottom > dock.top
            assertTrue("the dock ($dock) covers the label of '$label' ($row)", !(overlapsX && overlapsY))
        }
    }

    @Test fun CFG_18_theDockIsDisplayedButNeverTakesFocusOrClicksAwayFromTheList() {
        show()
        val node = rule.onNodeWithContentDescription("Live preview of your home")
        node.assertIsDisplayed()
        val cfg = node.fetchSemanticsNode().config
        assertTrue("no click action on the dock", !cfg.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick))
        assertTrue("not focusable", !cfg.contains(androidx.compose.ui.semantics.SemanticsProperties.Focused))
    }

    @Test fun CFG_18_theDockIsAbsentUntilTheHomeHasDrawnItself() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val capture = rememberHomeCapture()
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                SettingsSpace(visible = true, categories = categories(), initialCategory = 0, sounds = null, onClose = {}, modifier = Modifier.fillMaxSize(), previewDock = { SettingsPreviewDock(capture) })
            }
        }
        rule.mainClock.advanceTimeBy(1_500)
        rule.onAllNodesWithContentDescription("Live preview of your home").assertCountEquals(0) // nothing captured: no empty black box
    }

    /** The frame the dock must have for the window this test runs in, from the same geometry the screen uses. */
    private fun assertFrameForThisWindow() {
        show()
        val dock = rule.onNodeWithContentDescription("Live preview of your home").getUnclippedBoundsInRoot()
        val d = rule.activity.resources.displayMetrics.density
        val conf = rule.activity.resources.configuration
        val content = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val box = DockGeometry.size(conf.screenWidthDp.toFloat(), conf.screenHeightDp.toFloat(), content.width, content.height)
        assertEquals(box.widthDp, dock.width.value, 1.5f)
        assertEquals(box.heightDp, dock.height.value, 1.5f)
        assertEquals("aspect of the home", content.width.toFloat() / content.height, dock.width.value / dock.height.value, 0.03f)
        assertTrue("never above 170dp", maxOf(dock.width.value, dock.height.value) <= 170.5f)
        assertTrue("never below 72dp on its longest side", maxOf(dock.width.value, dock.height.value) >= 71.5f)
        // SettingsSpace forces a left-to-right layout (line 190 of SettingsSpace.kt), so "end" is the right edge in every locale.
        assertEquals("12dp from the right edge", content.width / d - 12f, dock.right.value, 1.5f)
        assertEquals(64f, dock.top.value, 1.5f)
        assertTrue("inside the window", dock.bottom.value <= content.height / d && dock.left.value >= 0f)
        for (label in listOf("Labels under icons", "Badges")) {
            val row = rule.onNodeWithText(label).getUnclippedBoundsInRoot()
            assertTrue("the dock covers '$label'", !(row.left < dock.right && row.right > dock.left && row.top < dock.bottom && row.bottom > dock.top))
        }
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun CFG_18_inLandscapeTheDockKeepsTheWideAspectAndStaysInsideTheWindow() = assertFrameForThisWindow()

    @Test @Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
    fun CFG_18_onATabletTheDockIsCappedAt170dpAndTheHomeAspectIsKept() = assertFrameForThisWindow()

    @Test @Config(sdk = [34], qualifiers = "w320dp-h480dp-port-hdpi")
    fun CFG_18_onASmallPhoneTheDockStaysLegibleAndClearOfTheRows() = assertFrameForThisWindow()

    @Test @Config(sdk = [34], qualifiers = "ldrtl-w411dp-h891dp-port-xhdpi")
    fun CFG_18_inARightToLeftLayoutGroundControlStaysLeftToRightSoTheDockStaysOnTheRightEdge() = assertFrameForThisWindow()

    @Test fun CFG_18_theDockIsOneLabelledImageNodeForScreenReadersAndNeverMerged() {
        show()
        val nodes = rule.onAllNodesWithContentDescription("Live preview of your home")
        nodes.assertCountEquals(1)
        val cfg = nodes[0].fetchSemanticsNode().config
        assertTrue(cfg.contains(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription))
        assertTrue("no text to read twice", !cfg.contains(androidx.compose.ui.semantics.SemanticsProperties.Text))
        assertEquals("the dock has no children that a reader could land on", 0, nodes[0].fetchSemanticsNode().children.size)
    }

    /**
     * CFG-18 pixels: a window copy with `PixelCopy` (Robolectric native graphics; no TextureView and no `captureToImage`, which
     * times out) reads what is really drawn. The home in this test is solid red, so the dock must show that red across its
     * area and nothing red may show just outside it (Ground Control's own backdrop is dark).
     */
    @Test fun CFG_18_theDockShowsTheCapturedHomeAcrossItsWholeAreaAndNothingOfItOutsideTheFrame() {
        show()
        val dock = rule.onNodeWithContentDescription("Live preview of your home").getUnclippedBoundsInRoot()
        val d = rule.activity.resources.displayMetrics.density
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val latch = java.util.concurrent.CountDownLatch(1)
        var status = -1
        val thread = android.os.HandlerThread("copy").also { it.start() }
        rule.runOnUiThread { android.view.PixelCopy.request(rule.activity.window, bmp, { status = it; latch.countDown() }, android.os.Handler(thread.looper)) }
        assertTrue("window copy finished", latch.await(10, java.util.concurrent.TimeUnit.SECONDS))
        thread.quit()
        assertEquals(android.view.PixelCopy.SUCCESS, status)
        fun isHomeRed(px: Int) = kotlin.math.abs(android.graphics.Color.red(px) - 0xE5) < 12 && kotlin.math.abs(android.graphics.Color.green(px) - 0x39) < 12 && kotlin.math.abs(android.graphics.Color.blue(px) - 0x35) < 12
        // The compose root sits below the window's own top inset; the copy is in window coordinates.
        val origin = IntArray(2).also { rule.activity.findViewById<android.view.View>(android.R.id.content).getLocationInWindow(it) }
        fun at(xDp: Float, yDp: Float) = bmp.getPixel((xDp * d + origin[0]).toInt().coerceIn(0, bmp.width - 1), (yDp * d + origin[1]).toInt().coerceIn(0, bmp.height - 1))
        val l = dock.left.value; val r = dock.right.value; val t = dock.top.value; val b = dock.bottom.value
        val inside = listOf(0.12f to 0.12f, 0.88f to 0.12f, 0.5f to 0.5f, 0.12f to 0.88f, 0.88f to 0.88f)
        for ((fx, fy) in inside) {
            val px = at(l + (r - l) * fx, t + (b - t) * fy)
            assertTrue("inside the dock at ($fx, $fy) should show the home, was #${Integer.toHexString(px)}", isHomeRed(px))
        }
        for ((x, y) in listOf(l - 10f to (t + b) / 2, (l + r) / 2 to b + 10f, (l + r) / 2 to t - 10f)) {
            assertTrue("outside the dock at ($x, $y) must not show the home", !isHomeRed(at(x, y)))
        }
    }
}
