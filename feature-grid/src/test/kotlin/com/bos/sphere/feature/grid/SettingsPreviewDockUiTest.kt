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
 * CFG-18: the live preview dock inside the real Ground Control screen ([SettingsSpace]), at composable level. Robolectric's
 * software canvas cannot execute a recorded GraphicsLayer, so the home's pixels inside the dock are device-only; what is
 * checked here is the frame: where it sits, its size and aspect, that it stays clear of the settings rows, that it takes no
 * focus or clicks, and that nothing is drawn before the home was captured. Pixel capture (`captureToImage`) was tried and times
 * out under Robolectric, so the dock's pixels stay a device step (docs/device-checklist.md).
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
}
