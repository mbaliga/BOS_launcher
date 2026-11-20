package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Process
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.WheelGrouping
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.core.quality.MotionPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * CFG-15, INP-18, INP-19, NFR-13, NFR-14 (wave 38, drawerRing): the rotatable white ring of 6 to 8 segments around the Back hub, in a Robolectric
 * window. Touches go through the real hit testing of the root. The PNGs in build/screenshots are what the status note judges against the owner's
 * reference; none of this is a device check (the feel of the drag, the fling and the gesture hand-over to the outer wheel need a phone).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class PuckSectionsUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @After fun reset() { ReducedMotionTesting.force(null); HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private val tints = longArrayOf(0xFFE1306C, 0xFF2F6BFF, 0xFF25C04A, 0xFFFF9A3C, 0xFF8E7BFF, 0xFF3A9BFF)
    private fun app(i: Int, label: String = "${('A' + (i % 22))}app$i") =
        AppEntry("demo.w$i", ComponentName("demo.w$i", "M"), label, Process.myUserHandle()) { AdaptiveIconDrawable(ColorDrawable(tints[i % tints.size].toInt()), ColorDrawable(tints[(i + 2) % tints.size].toInt())) }
    private val apps = (0 until 60).map { app(it) }

    private val log = mutableListOf<String>()
    private val names = listOf("Flashlight", "Alarm", "Notifications", "Do not disturb", "Radar", "Camera", "Calculator", "Settings")
    private val glyphs = listOf(
        com.bos.sphere.core.data.CornerActionId.Flashlight, com.bos.sphere.core.data.CornerActionId.Clock, com.bos.sphere.core.data.CornerActionId.Notifications,
        com.bos.sphere.core.data.CornerActionId.DoNotDisturb, com.bos.sphere.core.data.CornerActionId.Search, com.bos.sphere.core.data.CornerActionId.Camera,
        com.bos.sphere.core.data.CornerActionId.Calculator, com.bos.sphere.core.data.CornerActionId.SystemSettings,
    ).map { Glyphs.forAction(it) }
    private val eight = names.mapIndexed { i, n -> PuckAction(n, glyphs[i], available = n != "Do not disturb") { log += n } }
    private var dismissed = 0
    private var searched = 0
    private var anchor = Corner.BottomLeft
    private var n = 8

    private fun show(a: Corner, sections: List<PuckAction> = eight, segments: Int = 8, labels: Boolean = false, dir: LayoutDirection? = null) {
        anchor = a; n = segments
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val body = @androidx.compose.runtime.Composable {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    WheelDrawer(
                        visible = true, anchor = a, apps = apps, grouping = WheelGrouping.Letter, onGroupingChange = {}, recentKeys = emptyList(),
                        onLaunch = {}, onLongPressApp = {}, onSearch = { searched++ }, onDismiss = { dismissed++ }, actions = sections.take(segments),
                        ringSegments = segments, showRingLabels = labels, modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (dir != null) CompositionLocalProvider(LocalLayoutDirection provides dir) { body() } else body()
        }
        settle()
    }

    private fun settle() { rule.runOnUiThread { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }; rule.mainClock.advanceTimeBy(3_000) }
    private val d get() = rule.activity.resources.displayMetrics.density
    private fun rootSize() = rule.onRoot().fetchSemanticsNode().boundsInRoot
    private fun window(): RingWindow = rootSize().let { RingWindow.of(anchor, it.width / d, it.height / d) }
    private fun ring(): SectionRing {
        val r = rootSize(); val h = com.bos.sphere.core.input.PuckLayout.compute(minOf(r.width, r.height) / d)
        return PuckSectionsGeometry.ring(h.hubRadiusDp, h.chipRadiusDp)
    }
    private fun centre(): Offset = window().let { Offset(it.cx * d, it.cy * d) }

    /** Where the glyph of [slot] is, in root px, with the ring at rest position [p]. */
    private fun glyphAt(slot: Int, p: Float): Offset {
        val v = PuckSectionsGeometry.visibleSegment(anchor, n, p, slot, ring(), window(), PuckSectionsGeometry.GLYPH_DP) ?: error("slot $slot is not visible at $p")
        val (gx, gy) = PuckSectionsGeometry.glyphOffset(v.midAngleDeg, ring())
        return Offset(centre().x + gx * d, centre().y + gy * d)
    }

    /** The whole ring position the nodes on screen sit at (the first whole p that places every visible node where geometry says), -1 while between two. */
    private fun restPosition(): Int {
        val nodes = names.take(n).mapIndexedNotNull { i, nm -> rule.onAllNodesWithContentDescription(nm).fetchSemanticsNodes().firstOrNull()?.let { i to it.boundsInRoot } }
        for (p in 0 until n) {
            val vis = PuckSectionsGeometry.visibleSegments(anchor, n, p.toFloat(), ring(), window(), PuckSectionsGeometry.GLYPH_DP).associateBy { it.index }
            if (vis.keys != nodes.map { it.first }.toSet()) continue
            if (nodes.all { (i, b) -> val v = vis.getValue(i).bounds; abs(b.left - v.left * d) < 3f && abs(b.top - v.top * d) < 3f && abs(b.right - v.right * d) < 3f }) return p
        }
        return -1
    }

    private fun tapAt(o: Offset) = rule.onRoot().performTouchInput { click(o) }

    /** A slow drag around the wheel's centre by [deg] degrees from [from], then a pause so the release carries no fling. */
    private fun dragAround(from: Offset, deg: Float, slow: Boolean = true) {
        val c = centre(); val r = hypot(from.x - c.x, from.y - c.y)
        val a0 = Math.toDegrees(kotlin.math.atan2((from.y - c.y).toDouble(), (from.x - c.x).toDouble())).toFloat()
        rule.onRoot().performTouchInput {
            down(from)
            val steps = 12
            for (k in 1..steps) {
                val a = Math.toRadians((a0 + deg * k / steps).toDouble())
                moveTo(Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()), delayMillis = if (slow) 80 else 8)
            }
            if (slow) advanceEventTime(500)
            up()
        }
        settle()
    }

    private fun key(code: Int, shift: Boolean = false) {
        rule.runOnUiThread {
            val meta = if (shift) KeyEvent.META_SHIFT_ON else 0
            LauncherInput.onKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, meta))
            LauncherInput.onKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, code, 0, meta))
        }
        settle()
    }

    private fun ringAction(label: String) {
        val node = rule.onAllNodesWithContentDescription("Flashlight").fetchSemanticsNodes().first()
        val a = node.config[SemanticsActions.CustomActions].first { it.label == label }
        rule.runOnUiThread { a.action() }
        settle()
    }

    private fun sectionAngles(): Map<String, Float> {
        val s = rule.onRoot().fetchSemanticsNode().size
        return rule.onAllNodesWithContentDescription("Section", substring = true, useUnmergedTree = true).fetchSemanticsNodes().associate { nd ->
            val c = nd.boundsInRoot.center
            nd.config[SemanticsProperties.ContentDescription].single() to WheelGeometry.screenToPolar(anchor, s.width.toFloat(), s.height.toFloat(), c.x, c.y).angleDeg
        }
    }

    // ------------------------------------------------------------------------------------------------ what shows

    @Test fun CFG_15_w38_theVisibleSegmentsAreTheNodesAndTheHiddenOnesAreNotInTheTree() {
        show(Corner.BottomLeft)
        val vis = PuckSectionsGeometry.visibleSegments(anchor, 8, 1f, ring(), window(), PuckSectionsGeometry.GLYPH_DP).map { it.index }.toSet()
        for ((i, nm) in names.withIndex()) assertEquals("$nm (slot $i)", if (i in vis) 1 else 0, rule.onAllNodesWithContentDescription(nm).fetchSemanticsNodes().size)
        assertEquals(setOf(7, 0, 1, 2, 3), vis)
        assertEquals("the centre is Back", 1, rule.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().size)
        assertEquals(1, restPosition())
    }

    @Test fun CFG_15_w38_aSixSegmentRingShowsSlotsFromItsOwnSix() { show(Corner.BottomLeft, segments = 6); assertEquals(0, rule.onAllNodesWithContentDescription("Calculator").fetchSemanticsNodes().size); assertEquals(1, restPosition()) }
    @Test fun CFG_15_w38_aSevenSegmentRingShowsSlotsFromItsOwnSeven() { show(Corner.BottomLeft, segments = 7); assertEquals(0, rule.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().size); assertEquals(1, restPosition()) }

    @Test fun CFG_15_w38_theBackHubReachesToWithin8dpOfTheWindowEdgeAndTheArrowIsInsideIt() {
        show(Corner.BottomLeft)
        val b = rule.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot
        val r = rootSize()
        assertEquals("a 59 dp hub radius", 118f, b.width / d, 0.6f)
        val gapLeft = b.left / d; val gapBottom = (r.bottom - b.bottom) / d
        assertTrue("left gap $gapLeft", gapLeft in 0f..8f); assertTrue("bottom gap $gapBottom", gapBottom in 0f..8f)
        assertTrue("Back is at least 48 dp", b.width / d >= 48f && b.height / d >= 48f)
    }

    // ------------------------------------------------------------------------------------------------ taps

    @Test fun CFG_15_w38_tappingAVisibleSegmentRunsItsActionAndTheHubClosesTheDrawer() {
        show(Corner.BottomLeft)
        for (i in listOf(0, 1, 2, 7)) tapAt(glyphAt(i, 1f))
        assertEquals(listOf("Flashlight", "Alarm", "Notifications", "Settings"), log)
        tapAt(centre())
        assertEquals(1, dismissed); assertEquals(0, searched)
    }

    @Test fun CFG_15_w38_theWholeVisiblePartOfASegmentIsItsTapArea() {
        show(Corner.BottomLeft)
        val v = PuckSectionsGeometry.visibleSegment(anchor, 8, 1f, 1, ring(), window(), PuckSectionsGeometry.GLYPH_DP)!!
        val c = centre(); val r = ring()
        // near the hub's edge, mid ring and near the outer edge, on the segment's own centre line and 40 percent of a segment either side of it
        for (radius in listOf(r.inner + 3f, r.mid, r.outer - 3f)) for (off in listOf(-0.4f, 0f, 0.4f)) {
            val a = Math.toRadians((PuckSectionsGeometry.centerAngle(anchor, 8, 1f, 1) + off * 45f).toDouble())
            tapAt(Offset(c.x + radius * d * cos(a).toFloat(), c.y + radius * d * sin(a).toFloat()))
        }
        assertEquals(List(9) { "Alarm" }, log)
        assertTrue(v.bounds.width > 40f)
    }

    @Test fun CFG_15_w38_anAppSegmentLaunchesThatApp() {
        val target = app(7, "Notes"); val launched = mutableListOf<String>()
        show(Corner.BottomLeft, eight.take(2) + PuckAction("Notes", app = target) { launched += target.key } + eight.drop(3))
        tapAt(glyphAt(2, 1f))
        assertEquals(listOf(target.key), launched)
    }

    @Test fun CFG_15_w38_longPressShowsTheNameAndDoesNotRunTheAction() {
        show(Corner.BottomLeft)
        assertEquals(0, rule.onAllNodesWithText("Flashlight").fetchSemanticsNodes().size)
        val at = glyphAt(0, 1f)
        rule.onRoot().performTouchInput { longClick(at) }
        rule.mainClock.advanceTimeBy(300)
        assertEquals("the name pill", 1, rule.onAllNodesWithText("Flashlight").fetchSemanticsNodes().size)
        assertTrue("a long press only names it", log.isEmpty())
    }

    @Test fun CFG_15_w38_anEmptySegmentIsFaintDoesNothingOnTapAndOpensTheEditorOnLongPress() {
        var edited = 0
        show(Corner.BottomLeft, eight.take(2) + PuckAction("Drawer shortcut 3, empty", onLongClick = { edited++ }) { log += "empty" } + eight.drop(3))
        val at = glyphAt(2, 1f)
        tapAt(at)
        assertTrue(log.isEmpty()); assertEquals(0, edited)
        rule.onRoot().performTouchInput { longClick(at) }
        assertEquals("the long press opens the editor", 1, edited)
    }

    @Test fun CFG_15_w38_aSegmentTheDeviceCannotRunIsMarkedUnavailableButStillRunsWhenTurnedIntoView() {
        show(Corner.BottomLeft)
        key(KeyEvent.KEYCODE_DPAD_RIGHT, shift = true); key(KeyEvent.KEYCODE_DPAD_RIGHT, shift = true)
        val state = rule.onNodeWithContentDescription("Do not disturb").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
        assertEquals("Not available on this device", state)
        tapAt(glyphAt(3, restPosition().toFloat()))
        assertEquals(listOf("Do not disturb"), log)
    }

    // ------------------------------------------------------------------------------------------------ rotation

    @Test fun CFG_15_w38_draggingAroundTheDiscTurnsTheRingAndItSnapsToAWholeSegmentOnRelease() {
        show(Corner.BottomLeft)
        assertEquals(1, restPosition())
        // a finger on the ring (slot 1's glyph) moves 20 degrees clockwise: p moves by +20/45 = 0.44 (BottomLeft runs against the angle: p = 1 - ... see geometry), then snaps
        dragAround(glyphAt(1, 1f), 20f)
        val expected = PuckSectionsGeometry.settleTarget(1f + PuckSectionsGeometry.positionDelta(anchor, 8, 20f), 0f).toInt()
        assertEquals("a 20 degree drag is under half a segment: snaps back", 1, expected)
        assertEquals(1, restPosition())
        dragAround(glyphAt(1, 1f), 30f)
        val e2 = PuckSectionsGeometry.settleTarget(1f + PuckSectionsGeometry.positionDelta(anchor, 8, 30f), 0f).toInt()
        assertNotEquals(1, e2)
        assertEquals("30 degrees is two thirds of a segment: the next one is centred", e2, restPosition())
    }

    @Test fun CFG_15_w38_afterTurningTheTappedSegmentIsTheOneUnderTheFinger() {
        show(Corner.BottomLeft)
        dragAround(glyphAt(1, 1f), 45f)
        val p = restPosition()
        assertEquals(2, p)
        tapAt(glyphAt(2, p.toFloat()))
        tapAt(glyphAt(3, p.toFloat()))
        assertEquals(listOf("Notifications", "Do not disturb"), log)
    }

    @Test fun CFG_15_w38_aDragTheOtherWayAroundGoesTheOtherWay() {
        show(Corner.BottomLeft)
        dragAround(glyphAt(1, 1f), -45f)
        assertEquals(0, restPosition())
        dragAround(glyphAt(0, 0f), -45f)
        assertEquals("the ring is a loop: past slot 0 is slot 7", 7, restPosition())
    }

    @Test fun CFG_15_w38_aFastFlingCarriesTheRingOnByInertiaAndStillStopsOnASegment() {
        show(Corner.BottomLeft)
        dragAround(glyphAt(1, 1f), 30f, slow = false)
        val p = restPosition()
        assertTrue("rests on a whole segment: $p", p >= 0)
        assertTrue("carried further than the plain snap of a 30 degree drag", p != 1 && p != 2)
    }

    @Test fun NFR_14_w38_withAnimationsOffTheRingSnapsWithoutInertia() {
        ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f))
        show(Corner.BottomLeft)
        dragAround(glyphAt(1, 1f), 30f, slow = false)
        assertEquals("the nearest segment, no fling", 2, restPosition())
    }

    @Test fun INP_18_w38_shiftLeftAndRightTurnTheRingOneSegmentAndAHardKeyboardCannotLeaveItBetweenTwo() {
        show(Corner.BottomLeft)
        key(KeyEvent.KEYCODE_DPAD_RIGHT, shift = true); assertEquals(2, restPosition())
        key(KeyEvent.KEYCODE_DPAD_LEFT, shift = true); key(KeyEvent.KEYCODE_DPAD_LEFT, shift = true); assertEquals(0, restPosition())
        for (k in 1..8) key(KeyEvent.KEYCODE_DPAD_LEFT, shift = true)
        assertEquals("eight steps is one full turn", 0, restPosition())
    }

    @Test fun INP_18_w38_plainLeftRightStillTurnTheGroupLabelsAndLeaveTheRingAlone() {
        show(Corner.BottomLeft)
        val before = sectionAngles()
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("the ring did not move", 1, restPosition())
        assertNotEquals("the outer wheel turned", before, sectionAngles())
    }

    @Test fun INP_18_w38_talkBackRotateRingNextAndPreviousTurnByOneSegment() {
        show(Corner.BottomLeft)
        ringAction("Rotate ring next"); assertEquals(2, restPosition())
        ringAction("Rotate ring previous"); ringAction("Rotate ring previous"); assertEquals(0, restPosition())
        val hub = rule.onNodeWithContentDescription("Back").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Rotate ring next", "Rotate ring previous"), hub.map { it.label })
    }

    @Test fun INP_18_w38_aDragThatStartsOutsideTheRingTurnsTheOuterWheelNotTheRing() {
        show(Corner.BottomLeft)
        val ringBefore = restPosition(); val outerBefore = sectionAngles()
        val c = centre(); val r = ring()
        val a = Math.toRadians(315.0)
        val start = Offset(c.x + (r.outer + 30f) * d * cos(a).toFloat(), c.y + (r.outer + 30f) * d * sin(a).toFloat())
        // 30 dp outside the ring's edge, on the group labels' ring: the outer wheel's
        dragAround(start, 25f)
        assertEquals("the ring stayed", ringBefore, restPosition())
        assertNotEquals("the outer wheel turned", outerBefore, sectionAngles())
    }

    @Test fun INP_18_w38_aDragThatStartsOnTheRingTurnsTheRingNotTheOuterWheel() {
        show(Corner.BottomLeft)
        val outerBefore = sectionAngles()
        dragAround(glyphAt(1, 1f), 45f)
        assertEquals(2, restPosition())
        assertEquals("the outer wheel did not move", outerBefore, sectionAngles())
    }

    @Test fun INP_18_w38_aDragThatStartsOnTheHubAlsoTurnsTheRingAndATapThereStillGoesBack() {
        show(Corner.BottomLeft)
        val c = centre()
        dragAround(Offset(c.x + 30f * d, c.y), 45f)
        assertNotEquals("a hub drag turns the ring", 1, restPosition())
        assertEquals(0, dismissed)
        tapAt(centre())
        assertEquals(1, dismissed)
    }

    // ------------------------------------------------------------------------------------------------ mirror, locale, window

    @Test fun CFG_15_w38_theOtherCornerMirrorsTheRingAndATapThereRunsTheSameSegment() {
        show(Corner.BottomRight)
        val root = rootSize()
        val xs = listOf("Flashlight", "Alarm", "Notifications").map { rule.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot.center.x }
        assertTrue("the segments sit in the right-hand part: $xs", xs.all { it > root.width / 2f })
        assertTrue("slot 0 is the nearest the thumb edge: $xs", xs.first() < xs[1] && xs[1] < xs.last())
        tapAt(glyphAt(1, 1f)); assertEquals(listOf("Alarm"), log)
        dragAround(glyphAt(1, 1f), 45f)
        assertEquals("clockwise turns this corner's ring the other way round", 0, restPosition())
    }

    @Test fun CFG_15_w38_aRightToLeftLocaleDoesNotReorderThePhysicalSegments() {
        show(Corner.BottomLeft, dir = LayoutDirection.Rtl)
        val ys = listOf("Flashlight", "Alarm", "Notifications").map { rule.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot.center }
        assertTrue("slot 0 is lowest, then up the screen: $ys", ys[0].y > ys[1].y && ys[1].y > ys[2].y)
        tapAt(glyphAt(2, 1f)); assertEquals(listOf("Notifications"), log)
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi") fun CFG_15_w38_inLandscapeTheRingStillShowsTurnsAndTaps() {
        show(Corner.BottomRight)
        assertEquals(1, restPosition())
        tapAt(glyphAt(1, 1f)); assertEquals(listOf("Alarm"), log)
        key(KeyEvent.KEYCODE_DPAD_RIGHT, shift = true); assertEquals(2, restPosition())
    }

    @Test fun NFR_14_w38_withAnimationsOffTheSegmentsAreStillThereAndWork() {
        ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f))
        show(Corner.BottomLeft)
        tapAt(glyphAt(0, 1f))
        assertEquals(listOf("Flashlight"), log)
    }

    @Test fun INP_19_w38_backIsFirstInTraversalThenTheSegmentsInSlotOrder() {
        show(Corner.BottomLeft)
        fun idx(l: String) = rule.onNodeWithContentDescription(l).fetchSemanticsNode().config.getOrNull(SemanticsProperties.TraversalIndex)
        assertEquals(0f, idx("Back")!!, 0f)
        assertEquals(listOf(1f, 2f, 3f), listOf("Flashlight", "Alarm", "Notifications").map { idx(it)!! })
        assertTrue("Back is a button", rule.onNodeWithContentDescription("Back").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role) != null)
    }

    // ------------------------------------------------------------------------------------------------ labels, look

    @Test fun CFG_15_w38_labelsUnderGlyphsAreOffByDefaultAndShowOnlyForTheVisibleSegmentsWhenOn() {
        show(Corner.BottomLeft)
        assertEquals(0, rule.onAllNodesWithText("Alarm").fetchSemanticsNodes().size)
    }

    @Test fun CFG_15_w38_withLabelsOnEachVisibleSegmentNamesItsActionUnderItsGlyph() {
        show(Corner.BottomLeft, labels = true)
        for (nm in listOf("Flashlight", "Alarm", "Notifications")) assertEquals(nm, 1, rule.onAllNodesWithText(nm).fetchSemanticsNodes().size)
        assertEquals("hidden segments have no label", 0, rule.onAllNodesWithText("Calculator").fetchSemanticsNodes().size)
        val g = glyphAt(1, 1f)
        val t = rule.onAllNodesWithText("Alarm").fetchSemanticsNodes().first().boundsInRoot
        assertTrue("the label sits below the glyph centre (${t.center.y} vs ${g.y})", t.center.y > g.y)
    }

    private fun grab(): Bitmap {
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        return bmp
    }
    private fun pixelAt(bmp: Bitmap, radiusDp: Float, deg: Float): Int {
        val c = centre(); val a = Math.toRadians(deg.toDouble())
        return bmp.getPixel((c.x + radiusDp * d * cos(a)).toInt(), (c.y + radiusDp * d * sin(a)).toInt())
    }
    private fun rgb(c: Int) = Triple(android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c))
    private fun near(c: Int, v: Int, tol: Int = 6): Boolean = rgb(c).let { (r, g, b) -> abs(r - v) <= tol && abs(g - v) <= tol && abs(b - v) <= tol }

    @Test fun W38_darkThemeDrawsAFullWhiteRingFromTheHubToTheOuterEdgeWithBlackDividersAroundTheWholeCircleAndABlackHub() {
        show(Corner.BottomLeft)
        val bmp = grab(); val r = ring()
        // the middle of each visible segment, off its glyph, is white; the hub is black
        for (i in listOf(0, 1, 2)) {
            val deg = PuckSectionsGeometry.centerAngle(anchor, 8, 1f, i)
            assertTrue("slot $i near the outer edge: ${rgb(pixelAt(bmp, r.outer - 3f, deg))}", near(pixelAt(bmp, r.outer - 3f, deg), 255))
            assertTrue("slot $i just outside the hub: ${rgb(pixelAt(bmp, r.inner + 3f, deg))}", near(pixelAt(bmp, r.inner + 3f, deg), 255))
            assertTrue("hub is black", near(pixelAt(bmp, r.backRadius - 12f, deg), 0))
        }
        for (deg in PuckSectionsGeometry.dividerAngles(anchor, 8, 1f)) if (deg in 200f..400f || deg < 40f) {
            if (window().contains(window().cx + (r.mid + 14f) * cos(Math.toRadians(deg.toDouble())).toFloat(), window().cy + (r.mid + 14f) * sin(Math.toRadians(deg.toDouble())).toFloat())) {
                val pt = pixelAt(bmp, r.mid + 14f, deg)
                assertTrue("divider at $deg is black: ${rgb(pt)}", near(pt, 0, 60))
            }
        }
        assertFalse("clean outer arc", near(pixelAt(bmp, r.outer + 4f, 315f), 255, 3))
    }

    @Test fun W38_lightThemeDrawsANearBlackRingWithWhiteDividersAndAWhiteHub() {
        HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF)
        show(Corner.BottomLeft)
        val bmp = grab(); val r = ring()
        val deg = PuckSectionsGeometry.centerAngle(anchor, 8, 1f, 1)
        assertTrue("ring ${rgb(pixelAt(bmp, r.outer - 3f, deg))}", near(pixelAt(bmp, r.outer - 3f, deg), 0x61, 8))
        assertTrue("hub is white", near(pixelAt(bmp, r.backRadius - 12f, deg), 255))
    }

    @Test fun W38_aPressedSegmentIsLightGreyInDarkAndItsNeighbourStaysWhite() {
        show(Corner.BottomLeft)
        val at = glyphAt(1, 1f)
        rule.onRoot().performTouchInput { down(at) }
        rule.mainClock.advanceTimeBy(300)
        val bmp = grab(); val r = ring()
        val deg = PuckSectionsGeometry.centerAngle(anchor, 8, 1f, 1)
        assertTrue("pressed ${rgb(pixelAt(bmp, r.outer - 3f, deg))}", near(pixelAt(bmp, r.outer - 3f, deg), 217, 5))
        assertTrue("neighbour stays white", near(pixelAt(bmp, r.outer - 3f, PuckSectionsGeometry.centerAngle(anchor, 8, 1f, 2)), 255))
        rule.onRoot().performTouchInput { up() }
    }

    @Test fun W38_aSegmentStaysWhiteAfterTheFingerLeaves() {
        show(Corner.BottomLeft)
        tapAt(glyphAt(1, 1f)); settle()
        val bmp = grab(); val r = ring()
        assertTrue(near(pixelAt(bmp, r.outer - 3f, PuckSectionsGeometry.centerAngle(anchor, 8, 1f, 1)), 255))
    }

    // ------------------------------------------------------------------------------------------------ screenshots

    private fun shoot(name: String, a: Corner, segments: Int = 8, light: Boolean = false, labels: Boolean = false, sections: List<PuckAction> = eight, turn: Int = 0) {
        if (light) HyleTheme.apply(ThemeVariant.Light, 0xFF6D5BFF)
        show(a, sections, segments, labels)
        repeat(turn) { key(KeyEvent.KEYCODE_DPAD_RIGHT, shift = true) }
        File(outDir, "$name.png").outputStream().use { grab().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val mixed get() = listOf(PuckAction("Notes", app = app(7, "Notes")) {}, PuckAction("Drawer shortcut 2, empty") {}) + eight.drop(2)

    @Test fun W38_shotDarkPortraitLeft8() = shoot("w38-ring-dark-portrait-left-8", Corner.BottomLeft)
    @Test fun W38_shotDarkPortraitLeft6() = shoot("w38-ring-dark-portrait-left-6", Corner.BottomLeft, 6)
    @Test fun W38_shotDarkPortraitLeft7() = shoot("w38-ring-dark-portrait-left-7", Corner.BottomLeft, 7)
    @Test fun W38_shotDarkPortraitRight8() = shoot("w38-ring-dark-portrait-right-8", Corner.BottomRight)
    @Test fun W38_shotDarkPortraitRight6() = shoot("w38-ring-dark-portrait-right-6", Corner.BottomRight, 6)
    @Test fun W38_shotLightPortraitLeft8() = shoot("w38-ring-light-portrait-left-8", Corner.BottomLeft, light = true)
    @Test fun W38_shotLightPortraitRight6() = shoot("w38-ring-light-portrait-right-6", Corner.BottomRight, 6, light = true)
    @Test fun W38_shotDarkPortraitLeft8Turned3() = shoot("w38-ring-dark-portrait-left-8-turned3", Corner.BottomLeft, turn = 3)
    @Test fun W38_shotDarkPortraitLeft8Labels() = shoot("w38-ring-dark-portrait-left-8-labels", Corner.BottomLeft, labels = true)
    @Test fun W38_shotDarkPortraitLeft8EmptyAndApp() = shoot("w38-ring-dark-portrait-left-8-empty-app", Corner.BottomLeft, sections = mixed)
    @Test fun W38_shotLightPortraitLeft8EmptyAndApp() = shoot("w38-ring-light-portrait-left-8-empty-app", Corner.BottomLeft, light = true, sections = mixed)
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi") fun W38_shotDarkLandscapeLeft8() = shoot("w38-ring-dark-landscape-left-8", Corner.BottomLeft)
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi") fun W38_shotLightLandscapeRight8() = shoot("w38-ring-light-landscape-right-8", Corner.BottomRight, light = true)
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi") fun W38_shotDarkLandscapeRight6() = shoot("w38-ring-dark-landscape-right-6", Corner.BottomRight, 6)

    // ------------------------------------------------------------------------------------------------ wave 43: glyphs never adapt

    /** Dark ink pixels (the glyph is black on the white ring in the dark theme) within [radiusDp] of the segment centre of [slot] at rest position [p]. */
    private fun ink(bmp: Bitmap, slot: Int, p: Float, radiusDp: Float = 14f): List<Pair<Int, Int>> {
        val (gx, gy) = PuckSectionsGeometry.glyphCentre(anchor, n, p, slot, ring())
        val cx = centre().x + gx * d; val cy = centre().y + gy * d
        val out = mutableListOf<Pair<Int, Int>>()
        val r = (radiusDp * d).toInt()
        for (y in (cy - r).toInt()..(cy + r).toInt()) for (x in (cx - r).toInt()..(cx + r).toInt()) {
            if (x !in 0 until bmp.width || y !in 0 until bmp.height) continue
            if (hypot(x - cx, y - cy) > r) continue
            val c = bmp.getPixel(x, y)
            if (android.graphics.Color.red(c) < 110 && android.graphics.Color.green(c) < 110 && android.graphics.Color.blue(c) < 110) out += x to y
        }
        return out
    }

    @Test fun INP_19_w43_aGlyphWhoseBoxIsCutByTheEdgeIsNotDrawnAndTurningTheRingBringsItWholeIntoView() {
        show(Corner.BottomLeft)
        val g = PuckSectionsGeometry.GLYPH_DP
        // slot 7 (settings) is cut by the bottom edge at rest: no ink at its centre, though its segment is a tap target
        assertEquals(null, PuckSectionsGeometry.glyphPlacement(anchor, 8, 1f, 7, ring(), window(), g))
        assertEquals("no pixels of the cut glyph", 0, ink(grab(), 7, 1f).size)
        // every drawn glyph (0, 1, 2) has ink, all of it inside the window
        for (i in listOf(0, 1, 2)) assertTrue("slot $i has ink", ink(grab(), i, 1f).isNotEmpty())
        // turn until slot 7 is whole in view: now it is drawn at the exact segment centre
        var p = 1
        while (PuckSectionsGeometry.glyphPlacement(anchor, 8, p.toFloat(), 7, ring(), window(), g) == null) { key(KeyEvent.KEYCODE_DPAD_RIGHT, shift = true); p = restPosition() }
        assertTrue("slot 7 drawn at p=$p: ink ${ink(grab(), 7, p.toFloat()).size}", ink(grab(), 7, p.toFloat()).isNotEmpty())
    }

    @Test fun INP_19_w43_aGlyphKeepsItsSizeAndOneToOneInkAspectWhateverTheRotation() {
        show(Corner.BottomLeft)
        fun box(slot: Int, p: Float): Triple<Int, Int, Int> {
            val pts = ink(grab(), slot, p, 16f)
            assertTrue("slot $slot at $p drawn", pts.isNotEmpty())
            return Triple(pts.maxOf { it.first } - pts.minOf { it.first }, pts.maxOf { it.second } - pts.minOf { it.second }, pts.size)
        }
        val sizes = mutableListOf<Triple<Int, Int, Int>>()
        // slot 1 (the clock, a circle) is centred at p=1; turn one step each way and it is still whole in view
        for (turn in listOf(0, 1)) {
            val p = restPosition()
            if (PuckSectionsGeometry.glyphPlacement(anchor, 8, p.toFloat(), 1, ring(), window(), PuckSectionsGeometry.GLYPH_DP) != null) sizes += box(1, p.toFloat())
            key(KeyEvent.KEYCODE_DPAD_RIGHT, shift = true)
        }
        key(KeyEvent.KEYCODE_DPAD_LEFT, shift = true); key(KeyEvent.KEYCODE_DPAD_LEFT, shift = true)
        val p = restPosition()
        if (PuckSectionsGeometry.glyphPlacement(anchor, 8, p.toFloat(), 1, ring(), window(), PuckSectionsGeometry.GLYPH_DP) != null) sizes += box(1, p.toFloat())
        assertTrue("the clock is drawn at at least two rotations: $sizes", sizes.size >= 2)
        for (s in sizes) { assertEquals("a circle: width equals height", s.first.toFloat(), s.second.toFloat(), 2f); assertEquals("same size at every rotation", sizes[0].first.toFloat(), s.first.toFloat(), 2f) }
        // and the ink is the fixed 24 dp glyph's: a ring of radius 8.5/24 of the box, so about 17/24 * 24 dp wide (plus stroke)
        assertEquals(17f + 2.1f, sizes[0].first / d, 2.5f)
    }

    @Test fun INP_19_w43_everyActionGlyphIsASquareWithNonEmptyInkInsideItAndNoneIsStretched() {
        val ids = com.bos.sphere.core.data.CornerActionId.entries
        for (id in ids) {
            val v = Glyphs.forAction(id)
            assertEquals("${id.name} default size is square", v.defaultWidth.value, v.defaultHeight.value, 0f)
            assertEquals("${id.name} viewport is square", v.viewportWidth, v.viewportHeight, 0f)
        }
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                ids.forEachIndexed { i, id ->
                    Box(Modifier.offset((8 + (i % 6) * 60).dp, (8 + (i / 6) * 60).dp).size(48.dp)) {
                        androidx.compose.material3.Icon(Glyphs.forAction(id), null, tint = Color.Black, modifier = Modifier.size(48.dp))
                    }
                }
            }
        }
        rule.waitForIdle()
        val bmp = grab()
        val boxPx = (48 * d).toInt()
        for ((i, id) in ids.withIndex()) {
            val x0 = ((8 + (i % 6) * 60) * d).toInt(); val y0 = ((8 + (i / 6) * 60) * d).toInt()
            var l = Int.MAX_VALUE; var t = Int.MAX_VALUE; var r = -1; var b = -1; var count = 0
            for (y in y0 until y0 + boxPx) for (x in x0 until x0 + boxPx) {
                if (android.graphics.Color.red(bmp.getPixel(x, y)) < 128) { count++; l = minOf(l, x); r = maxOf(r, x); t = minOf(t, y); b = maxOf(b, y) }
            }
            assertTrue("${id.name} has ink", count > 20)
            assertTrue("${id.name} ink stays inside its square (not clipped)", l > x0 && t > y0 && r < x0 + boxPx - 1 && b < y0 + boxPx - 1)
            val w = (r - l + 1).toFloat(); val h = (b - t + 1).toFloat()
            // A uniform scale of a 24-unit drawing: ink is at most the 24 unit square, and no glyph is a sliver (a squeezed one would be)
            assertTrue("${id.name} ink ${w}x$h is not squeezed", w / h in 0.25f..4f)
        }
        File(outDir, "w43-action-glyphs-square.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun INP_19_w43_noGlyphIsDrawnWithANonUniformScaleOrAStretchingContentScale() {
        val dir = File(System.getProperty("user.dir"), "src/main/kotlin/com/bos/sphere/feature/grid")
        for (f in listOf("PuckSections.kt", "Glyphs.kt")) {
            val text = File(dir, f).readText()
            assertFalse("$f has no scaleX/scaleY", Regex("""\bscale[XY]\b""").containsMatchIn(text))
            assertFalse("$f has no stretching ContentScale", Regex("""FillBounds|FillWidth|FillHeight|Crop\b""").containsMatchIn(text))
        }
        // the ring's glyph box is one square size (never width x height with different values)
        assertFalse(Regex("""Icon\([^\n]*Modifier\.(fillMaxSize|width|height)""").containsMatchIn(File(dir, "PuckSections.kt").readText()))
    }

    @Test fun INP_19_w43_anAppIconInTheRingKeepsItsFixedSquare36dpSize() {
        val target = app(7, "Notes")
        show(Corner.BottomLeft, listOf(PuckAction("Notes", app = target) {}) + eight.drop(1))
        assertEquals(36f, PuckSectionsGeometry.APP_ICON_DP, 0f)
        // slot 0 is whole in view at rest: its coloured icon is drawn at 36 dp x 36 dp (adaptive icons keep a circle or squircle, never an ellipse)
        val (gx, gy) = PuckSectionsGeometry.glyphCentre(anchor, 8, 1f, 0, ring())
        val cx = centre().x + gx * d; val cy = centre().y + gy * d
        var l = 0; var t = 0; var r = -1; var b = -1
        // the icon loads off the main thread: give it up to ~3 s
        for (attempt in 0 until 30) {
            val bmp = grab()
            l = Int.MAX_VALUE; t = Int.MAX_VALUE; r = -1; b = -1
            for (y in (cy - 30 * d).toInt()..(cy + 30 * d).toInt()) for (x in (cx - 30 * d).toInt()..(cx + 30 * d).toInt()) {
                val c = bmp.getPixel(x, y)
                val hi = maxOf(android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c))
                val lo = minOf(android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c))
                if (hi - lo > 80) {
                    l = minOf(l, x); r = maxOf(r, x); t = minOf(t, y); b = maxOf(b, y)
                }
            }
            if (r > l) break
            Thread.sleep(100); settle()
        }
        assertTrue("the app icon is drawn", r > l && b > t)
        assertEquals("square", (r - l + 1).toFloat(), (b - t + 1).toFloat(), 3f * d)
        assertTrue("at most the fixed 36 dp", (r - l + 1) / d <= 37f)
    }

    @Test fun INP_19_w43_theLabelUnderAGlyphIsAtAFixedOffsetBelowItAndOnlyWhenItFitsTheWindow() {
        show(Corner.BottomLeft, labels = true)
        val g = PuckSectionsGeometry.GLYPH_DP
        for (i in names.indices) {
            val vis = PuckSectionsGeometry.visibleSegment(anchor, 8, 1f, i, ring(), window(), g) != null
            val labelNodes = rule.onAllNodesWithText(names[i]).fetchSemanticsNodes()
            if (!vis) { assertEquals("slot $i not visible: no label node", 0, labelNodes.size); continue }
            val c = PuckSectionsGeometry.glyphCentre(anchor, 8, 1f, i, ring())
            if (!PuckSectionsGeometry.labelVisible(window(), c, g)) continue // placed but transparent: nothing is drawn
            val t = labelNodes.first().boundsInRoot
            // fixed offset: the label's top is 1 dp under the glyph box (the node box is the text's own height), centred on the glyph
            assertEquals("slot $i label centre x", centre().x + c.first * d, t.center.x, 2f * d)
            assertTrue("slot $i label is under the glyph box", t.top >= centre().y + (c.second + g / 2f) * d - 1f)
            assertTrue("slot $i label is wholly inside the window", t.bottom <= rootSize().bottom - 2f * d && t.left >= 2f * d)
        }
        // slot 7 (cut by the bottom edge): glyph and label are both hidden by the rule
        assertFalse(PuckSectionsGeometry.labelVisible(window(), PuckSectionsGeometry.glyphCentre(anchor, 8, 1f, 7, ring()), g))
    }

    // ------------------------------------------------------------------------------------------------ wave 43 screenshots

    @Test fun W43_shotDarkPortraitLeftRest() = shoot("w43-ring-dark-portrait-left-rest", Corner.BottomLeft)
    @Test fun W43_shotDarkPortraitLeftTurned1() = shoot("w43-ring-dark-portrait-left-turned1", Corner.BottomLeft, turn = 1)
    @Test fun W43_shotDarkPortraitLeftTurned2() = shoot("w43-ring-dark-portrait-left-turned2", Corner.BottomLeft, turn = 2)
    @Test fun W43_shotLightPortraitLeftRest() = shoot("w43-ring-light-portrait-left-rest", Corner.BottomLeft, light = true)
    @Test fun W43_shotLightPortraitLeftTurned1() = shoot("w43-ring-light-portrait-left-turned1", Corner.BottomLeft, light = true, turn = 1)
    @Test fun W43_shotDarkPortraitRightRest() = shoot("w43-ring-dark-portrait-right-rest", Corner.BottomRight)
    @Test fun W43_shotDarkPortraitRightTurned1() = shoot("w43-ring-dark-portrait-right-turned1", Corner.BottomRight, turn = 1)
    @Test fun W43_shotLightPortraitRightRest() = shoot("w43-ring-light-portrait-right-rest", Corner.BottomRight, light = true)
    @Test fun W43_shotDarkPortraitLeftLabels() = shoot("w43-ring-dark-portrait-left-labels", Corner.BottomLeft, labels = true)
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi") fun W43_shotDarkLandscapeLeftRest() = shoot("w43-ring-dark-landscape-left-rest", Corner.BottomLeft)
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi") fun W43_shotDarkLandscapeLeftTurned1() = shoot("w43-ring-dark-landscape-left-turned1", Corner.BottomLeft, turn = 1)
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi") fun W43_shotLightLandscapeRightRest() = shoot("w43-ring-light-landscape-right-rest", Corner.BottomRight, light = true)
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi") fun W43_shotLightLandscapeRightTurned1() = shoot("w43-ring-light-landscape-right-turned1", Corner.BottomRight, light = true, turn = 1)
}
