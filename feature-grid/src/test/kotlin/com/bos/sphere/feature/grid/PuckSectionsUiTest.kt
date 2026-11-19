package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.WheelGrouping
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.quality.MotionPrefs
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

/**
 * CFG-15, INP-19, NFR-13, NFR-14 (wave 30, puckSections): the four wedge sections around the all-apps disc's hub and its Back button, in a Robolectric
 * window. The PNGs in build/screenshots are what the status note judges against the owner's Concepts screenshot.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class PuckSectionsUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @After fun reset() = ReducedMotionTesting.force(null)
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private val tints = longArrayOf(0xFFE1306C, 0xFF2F6BFF, 0xFF25C04A, 0xFFFF9A3C, 0xFF8E7BFF, 0xFF3A9BFF)
    private fun app(i: Int, label: String = "${('A' + (i % 22))}app$i") =
        AppEntry("demo.w$i", ComponentName("demo.w$i", "M"), label, Process.myUserHandle()) { AdaptiveIconDrawable(ColorDrawable(tints[i % tints.size].toInt()), ColorDrawable(tints[(i + 2) % tints.size].toInt())) }
    private val apps = (0 until 60).map { app(it) }

    private val log = mutableListOf<String>()
    private val four = listOf(
        PuckAction("Flashlight", Glyphs.Bolt) { log += "flash" },
        PuckAction("Alarm", Glyphs.Clock) { log += "alarm" },
        PuckAction("Notifications", Glyphs.Bell) { log += "bell" },
        PuckAction("Do not disturb", Glyphs.Dial, available = false) { log += "dnd" },
    )
    private var dismissed = 0
    private var searched = 0

    private fun show(anchor: Corner, sections: List<PuckAction> = four, dir: LayoutDirection? = null) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val body = @androidx.compose.runtime.Composable {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    WheelDrawer(
                        visible = true, anchor = anchor, apps = apps, grouping = WheelGrouping.Letter, onGroupingChange = {}, recentKeys = emptyList(),
                        onLaunch = {}, onLongPressApp = {}, onSearch = { searched++ }, onDismiss = { dismissed++ }, actions = sections, modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (dir != null) CompositionLocalProvider(LocalLayoutDirection provides dir) { body() } else body()
        }
        rule.mainClock.advanceTimeBy(2_000)
    }

    /** Where a wedge's centroid is, in the node's own coordinates. */
    private fun centroidOf(slot: Int, anchor: Corner, node: SemanticsNodeInteraction): Offset {
        val d = rule.activity.resources.displayMetrics.density
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val inset = 63f * d
        val cx = if (anchor == Corner.TopRight || anchor == Corner.BottomRight) root.width - inset else inset
        val cy = if (anchor == Corner.BottomRight || anchor == Corner.BottomLeft) root.height - inset else inset
        val hub = com.bos.sphere.core.input.PuckLayout.compute(minOf(root.width, root.height) / d, 0)
        val ring = PuckSectionsGeometry.ring(hub.hubRadiusDp, hub.chipRadiusDp)
        val (gx, gy) = PuckSectionsGeometry.centroid(PuckSectionsGeometry.wedges(anchor, ring)[slot], ring)
        val b = node.fetchSemanticsNode().boundsInRoot
        return Offset(cx + gx * d - b.left, cy + gy * d - b.top)
    }

    private fun tap(label: String, slot: Int, anchor: Corner) {
        val n = rule.onNodeWithContentDescription(label)
        val at = centroidOf(slot, anchor, n)
        n.performTouchInput { click(at) }
    }

    @Test fun CFG_15_w30_theFourSectionsAreOnTheDiscWithTheirLabelsAndNoRoundButtonsFloat() {
        show(Corner.BottomLeft)
        for (a in four) assertEquals(a.label, 1, rule.onAllNodesWithContentDescription(a.label).fetchSemanticsNodes().size)
        assertEquals("the centre is Back", 1, rule.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().size)
    }

    @Test fun CFG_15_w30_tappingASectionRunsItsAction() {
        show(Corner.BottomLeft)
        tap("Alarm", 1, Corner.BottomLeft); tap("Notifications", 2, Corner.BottomLeft); tap("Flashlight", 0, Corner.BottomLeft)
        assertEquals(listOf("alarm", "bell", "flash"), log)
    }

    @Test fun CFG_15_w30_tappingBetweenTwoWedgesInTheGapStillRunsOneOfThem() {
        show(Corner.BottomLeft)
        val n = rule.onNodeWithContentDescription("Alarm")
        val d = rule.activity.resources.displayMetrics.density
        // the tap area is the whole wedge: the middle of a wedge, a quarter of the way along it and near its outer edge all run it
        val at = centroidOf(1, Corner.BottomLeft, n)
        n.performTouchInput { click(Offset(at.x, at.y + 6f * d)) }
        assertEquals(1, log.size)
    }

    @Test fun CFG_15_w30_anAppSectionLaunchesThatApp() {
        val target = app(7, "Notes")
        val launched = mutableListOf<String>()
        show(Corner.BottomLeft, four.take(3) + PuckAction("Notes", app = target) { launched += target.key })
        tap("Notes", 3, Corner.BottomLeft)
        assertEquals(listOf(target.key), launched)
    }

    @Test fun CFG_15_w30_longPressShowsTheNameAndDoesNotRunTheAction() {
        show(Corner.BottomLeft)
        val n = rule.onNodeWithContentDescription("Flashlight")
        val at = centroidOf(0, Corner.BottomLeft, n)
        assertEquals(0, rule.onAllNodesWithText("Flashlight").fetchSemanticsNodes().size)
        n.performTouchInput { longClick(at) }
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("Flashlight").assertExists()
        assertTrue("a long press only names it", log.isEmpty())
    }

    @Test fun CFG_15_w30_anEmptySectionIsFaintDoesNothingOnTapAndOpensTheEditorOnLongPress() {
        var edited = 0
        show(Corner.BottomLeft, four.take(3) + PuckAction("Drawer shortcut 4, empty", onLongClick = { edited++ }) { log += "empty" })
        val n = rule.onNodeWithContentDescription("Drawer shortcut 4, empty")
        val at = centroidOf(3, Corner.BottomLeft, n)
        n.performTouchInput { click(at) }
        assertTrue(log.isEmpty()); assertEquals(0, edited)
        n.performTouchInput { longClick(at) }
        assertEquals("the long press opens the editor", 1, edited)
    }

    @Test fun CFG_15_w30_aSectionTheDeviceCannotRunIsMarkedUnavailable() {
        show(Corner.BottomLeft)
        val state = rule.onNodeWithContentDescription("Do not disturb").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
        assertEquals("Not available on this device", state)
        tap("Do not disturb", 3, Corner.BottomLeft)
        assertEquals("the tap still runs it, so the action can explain itself", listOf("dnd"), log)
    }

    @Test fun CFG_15_w30_theCentreButtonIsBackAndTheHubHasNoSearchGlyph() {
        show(Corner.BottomLeft)
        assertEquals(0, rule.onAllNodesWithContentDescription("Search apps").fetchSemanticsNodes().size)
        rule.onNodeWithContentDescription("Back").performTouchInput { click() }
        assertEquals(1, dismissed); assertEquals(0, searched)
    }

    @Test fun CFG_15_w30_backHasTheFirstTraversalIndexThenTheSectionsInOrder() {
        show(Corner.BottomLeft)
        fun idx(l: String) = rule.onNodeWithContentDescription(l).fetchSemanticsNode().config.getOrNull(SemanticsProperties.TraversalIndex)
        assertEquals(0f, idx("Back")!!, 0f)
        assertEquals(listOf(1f, 2f, 3f, 4f), four.map { idx(it.label)!! })
        assertTrue("Back is a button", rule.onNodeWithContentDescription("Back").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role) != null)
    }

    @Test fun CFG_15_w30_theSectionsMirrorOnTheOtherCornerAndKeepTheirOrderInARightToLeftLocale() {
        show(Corner.BottomRight)
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val xs = four.map { rule.onNodeWithContentDescription(it.label).fetchSemanticsNode().boundsInRoot.center.x }
        assertTrue("the sections sit in the right-hand quadrant: $xs", xs.all { it > root.width / 2f })
        assertTrue("slot 1 is the outer-most along the bottom: $xs", xs.first() < xs.last())
        tap("Alarm", 1, Corner.BottomRight)
        assertEquals(listOf("alarm"), log)
    }

    @Test fun CFG_15_w30_aRightToLeftLocaleDoesNotReorderThePhysicalSections() {
        show(Corner.BottomLeft, dir = LayoutDirection.Rtl)
        val ys = four.map { rule.onNodeWithContentDescription(it.label).fetchSemanticsNode().boundsInRoot.center }
        assertTrue("slot 1 is lowest, slot 4 highest: $ys", ys.first().y > ys.last().y)
        tap("Notifications", 2, Corner.BottomLeft)
        assertEquals(listOf("bell"), log)
    }

    @Test fun NFR_14_w30_withAnimationsOffTheSectionsAreStillThereAndWork() {
        ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f))
        show(Corner.BottomLeft)
        tap("Flashlight", 0, Corner.BottomLeft)
        assertEquals(listOf("flash"), log)
    }

    @Test fun INP_19_w30_theSectionsAreAtLeast48dpDeepAndTheBackButton48dpWide() {
        show(Corner.BottomLeft)
        val d = rule.activity.resources.displayMetrics.density
        val back = rule.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot
        assertTrue(back.width / d >= 47.5f && back.height / d >= 47.5f)
        for (a in four) {
            val b = rule.onNodeWithContentDescription(a.label).fetchSemanticsNode().boundsInRoot
            assertTrue("${a.label} is ${b.width / d} x ${b.height / d} dp", maxOf(b.width, b.height) / d >= 48f)
        }
    }

    private fun shoot(name: String, anchor: Corner, sections: List<PuckAction> = four) {
        show(anchor, sections)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun CFG_15_w30_shotPortraitLeftPressed() {
        show(Corner.BottomLeft)
        val n = rule.onNodeWithContentDescription("Alarm")
        val at = centroidOf(1, Corner.BottomLeft, n)
        n.performTouchInput { down(at) }
        rule.mainClock.advanceTimeBy(200)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "w30-sections-portrait-left-pressed.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        n.performTouchInput { up() }
    }

    private val withEmpty get() = four.take(1) + PuckAction("Drawer shortcut 2, empty") {} + four.drop(2).map { it }

    @Test fun CFG_15_w30_shotPortraitLeft() = shoot("w30-sections-portrait-left", Corner.BottomLeft)
    @Test fun CFG_15_w30_shotPortraitRight() = shoot("w30-sections-portrait-right", Corner.BottomRight)
    @Test fun CFG_15_w30_shotPortraitLeftWithAnEmptySlot() = shoot("w30-sections-portrait-left-empty", Corner.BottomLeft, withEmpty)
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi") fun CFG_15_w30_shotLandscapeLeft() = shoot("w30-sections-landscape-left", Corner.BottomLeft)
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi") fun CFG_15_w30_shotLandscapeRightWithAnEmptySlot() = shoot("w30-sections-landscape-right-empty", Corner.BottomRight, withEmpty)
}

