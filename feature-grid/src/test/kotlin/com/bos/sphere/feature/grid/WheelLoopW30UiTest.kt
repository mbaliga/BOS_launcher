package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.content.pm.ApplicationInfo
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.WheelGrouping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Wave 30 (drawerLoop), HOM-01 and INP-18: the sections of the app drawer disc loop. Rotating past Z lands on '#' then 'A'; rotating back from
 * A shows '#' before it and reaches Z; keyboard, scroll wheel and TalkBack actions wrap; the neighbours are drawn on both sides of the selected
 * section (asserted on the section nodes and their positions, not only on pixels); the same holds from the left corner and for categories.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class WheelLoopW30UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private val tints = longArrayOf(0xFFE1306C, 0xFF2F6BFF, 0xFF25C04A, 0xFFFF9A3C, 0xFF8E7BFF, 0xFF3A9BFF)
    private fun app(i: Int, label: String, pkg: String = "demo.l$i") =
        AppEntry(pkg, ComponentName(pkg, "M"), label, Process.myUserHandle()) {
            AdaptiveIconDrawable(ColorDrawable(tints[i % tints.size].toInt()), ColorDrawable(tints[(i + 2) % tints.size].toInt()))
        }

    /** One app per letter A..Z plus one starting with a digit: groups A, B, ... Z, '#'. */
    private val letterApps = ('A'..'Z').mapIndexed { i, c -> app(i, "${c}pp $i") } + app(26, "1password")
    private val actions = listOf("Settings" to Glyphs.Dial).map { (l, g) -> PuckAction(l, g) {} }
    private val order = ('A'..'Z').map { it.toString() } + "#"

    private fun show(anchor: Corner, apps: List<AppEntry> = letterApps, grouping: WheelGrouping = WheelGrouping.Letter) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                WheelDrawer(
                    visible = true, anchor = anchor, apps = apps, grouping = grouping, onGroupingChange = {}, recentKeys = emptyList(),
                    onLaunch = {}, onLongPressApp = {}, onSearch = {}, onDismiss = {}, actions = actions, modifier = Modifier.fillMaxSize(),
                )
            }
        }
        settle()
    }

    private fun settle() { rule.mainClock.advanceTimeBy(3_000) }

    private fun sections() = rule.onAllNodesWithContentDescription("Section", substring = true, useUnmergedTree = true)

    private fun labelOf(desc: String) = desc.removePrefix("Section ")

    /** The label of the section the wheel has in the middle (the one marked selected). */
    private fun selected(): String {
        val nodes = sections().fetchSemanticsNodes().filter { it.config.getOrNull(SemanticsProperties.Selected) == true }
        assertEquals("exactly one section is selected", 1, nodes.size)
        return labelOf(nodes.single().config[SemanticsProperties.ContentDescription].single())
    }

    /** Clockwise angle (0..360) of each drawn section from the wheel's own start of quadrant, keyed by label. */
    private fun angles(anchor: Corner): Map<String, Float> {
        val size = rule.onRoot().fetchSemanticsNode().size
        return sections().fetchSemanticsNodes().associate { n ->
            val c = n.boundsInRoot.center
            val polar = WheelGeometry.screenToPolar(anchor, size.width.toFloat(), size.height.toFloat(), c.x, c.y)
            labelOf(n.config[SemanticsProperties.ContentDescription].single()) to WheelGeometry.normalize360(polar.angleDeg - WheelGeometry.quadrantStart(anchor))
        }
    }

    private fun key(code: Int) {
        rule.runOnUiThread {
            LauncherInput.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            LauncherInput.onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        settle()
    }

    private fun assertNeighbours(anchor: Corner, label: String, prev: String, next: String) {
        val a = angles(anchor)
        val here = a.getValue(label)
        assertNotNull("$prev is drawn", a[prev]); assertNotNull("$next is drawn", a[next])
        assertTrue("$prev (${a[prev]}) precedes $label ($here)", a.getValue(prev) < here)
        assertTrue("$next (${a[next]}) follows $label ($here)", a.getValue(next) > here)
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_01_w30_atA_the_hash_precedes_it_and_Z_before_that() {
        show(Corner.BottomRight)
        assertEquals("A", selected())
        val a = angles(Corner.BottomRight)
        assertTrue("# is drawn before A: $a", a.getValue("#") < a.getValue("A"))
        assertTrue("Z is drawn before #: $a", a.getValue("Z") < a.getValue("#"))
        assertTrue("B follows A", a.getValue("B") > a.getValue("A"))
        assertNeighbours(Corner.BottomRight, "A", "#", "B")
        shot("w30-at-A-portrait-right")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_01_w30_keyboard_left_from_A_reaches_hash_then_Z_and_right_wraps_back() {
        show(Corner.BottomRight)
        key(KeyEvent.KEYCODE_DPAD_LEFT); assertEquals("#", selected()); assertNeighbours(Corner.BottomRight, "#", "Z", "A")
        key(KeyEvent.KEYCODE_DPAD_LEFT); assertEquals("Z", selected()); assertNeighbours(Corner.BottomRight, "Z", "Y", "#")
        shot("w30-at-Z-portrait-right")
        key(KeyEvent.KEYCODE_DPAD_RIGHT); assertEquals("#", selected())
        shot("w30-at-hash-portrait-right")
        key(KeyEvent.KEYCODE_DPAD_RIGHT); assertEquals("A", selected())
        key(KeyEvent.KEYCODE_DPAD_RIGHT); assertEquals("B", selected())
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_01_w30_rotating_past_Z_lands_on_hash_then_A_then_B() {
        show(Corner.BottomRight)
        key(KeyEvent.KEYCODE_MOVE_END)           // '#', the last group: one step before A across the seam
        assertEquals("#", selected())
        key(KeyEvent.KEYCODE_DPAD_RIGHT); assertEquals("A", selected())
        key(KeyEvent.KEYCODE_DPAD_RIGHT); assertEquals("B", selected())
        key(KeyEvent.KEYCODE_MOVE_HOME); assertEquals("A", selected())
        assertNeighbours(Corner.BottomRight, "A", "#", "B")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_01_w30_a_typed_letter_takes_the_short_way_across_the_seam() {
        show(Corner.BottomRight)
        key(KeyEvent.KEYCODE_Z)                  // A -> Z is two groups back (# between), not 25 forward
        assertEquals("Z", selected())
        assertNeighbours(Corner.BottomRight, "Z", "Y", "#")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun INP_18_w30_scroll_wheel_notches_wrap() {
        show(Corner.BottomRight)
        rule.onRoot().performMouseInput { scroll(-1f) }; settle()
        val first = selected()
        rule.onRoot().performMouseInput { scroll(1f) }; settle()
        assertEquals("a notch each way returns to A", "A", selected())
        // Whichever direction the first notch went, the other one passes the seam: from A it lands on '#'.
        assertTrue(first == "#" || first == "B")
        if (first == "B") { rule.onRoot().performMouseInput { scroll(1f) }; settle(); assertEquals("#", selected()) }
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun INP_18_w30_talkback_next_and_previous_section_wrap() {
        show(Corner.BottomRight)
        fun act(label: String) {
            val node = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)).fetchSemanticsNodes().first()
            val action = node.config[SemanticsActions.CustomActions].first { it.label == label }
            rule.runOnUiThread { action.action() }
            settle()
        }
        act("Previous section"); assertEquals("#", selected())
        act("Previous section"); assertEquals("Z", selected())
        act("Next section"); assertEquals("#", selected())
        act("Next section"); assertEquals("A", selected())
        act("Next section"); assertEquals("B", selected())
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun INP_18_w30_a_section_node_selects_its_group_and_is_at_least_48dp() {
        show(Corner.BottomRight)
        val node = rule.onNodeWithContentDescription("Section B", useUnmergedTree = true)
        val b = node.fetchSemanticsNode().boundsInRoot
        val dp = rule.activity.resources.displayMetrics.density
        assertTrue("48 dp target, was ${b.width / dp}", b.width / dp >= 47.9f && b.height / dp >= 47.9f)
        node.performSemanticsAction(SemanticsActions.OnClick); settle()
        assertEquals("B", selected())
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_01_w30_mirrored_corner_has_the_same_cyclic_neighbours() {
        show(Corner.BottomLeft)
        assertEquals("A", selected())
        assertNeighbours(Corner.BottomLeft, "A", "#", "B")
        key(KeyEvent.KEYCODE_DPAD_LEFT); assertEquals("#", selected())
        assertNeighbours(Corner.BottomLeft, "#", "Z", "A")
        shot("w30-at-hash-portrait-left")
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_01_w30_landscape_right_and_left_show_the_hash_before_A_and_after_Z() {
        show(Corner.BottomRight)
        assertNeighbours(Corner.BottomRight, "A", "#", "B")
        shot("w30-at-A-landscape-right")
        key(KeyEvent.KEYCODE_MOVE_END); key(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("Z", selected())
        assertNeighbours(Corner.BottomRight, "Z", "Y", "#")
        shot("w30-at-Z-landscape-right")
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun HOM_01_w30_landscape_left_handed() {
        show(Corner.BottomLeft)
        assertNeighbours(Corner.BottomLeft, "A", "#", "B")
        shot("w30-at-A-landscape-left")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_01_w30_category_grouping_wraps_too() {
        val cats = listOf(
            ApplicationInfo.CATEGORY_GAME, ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE,
            ApplicationInfo.CATEGORY_SOCIAL, ApplicationInfo.CATEGORY_NEWS, ApplicationInfo.CATEGORY_MAPS, ApplicationInfo.CATEGORY_PRODUCTIVITY,
            ApplicationInfo.CATEGORY_ACCESSIBILITY, ApplicationInfo.CATEGORY_UNDEFINED,
        )
        val apps = cats.flatMapIndexed { ci, cat -> (0 until 5).map { k -> app(ci * 5 + k, "C$ci app$k", "demo.cat$ci.$k").also { WheelCategoryCache.seed(it.packageName, cat) } } }
        show(Corner.BottomRight, apps, WheelGrouping.Category)
        assertEquals("Games", selected())
        assertNeighbours(Corner.BottomRight, "Games", "Other", "Audio")
        key(KeyEvent.KEYCODE_DPAD_LEFT); assertEquals("Other", selected())
        assertNeighbours(Corner.BottomRight, "Other", "Accessibility", "Games")
        key(KeyEvent.KEYCODE_DPAD_RIGHT); assertEquals("Games", selected())
        shot("w30-category-at-first-portrait-right")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_01_w30_mid_fling_across_the_seam_has_no_empty_stretch() {
        show(Corner.BottomRight)
        // Hold the wheel half way between A and # and look: both bands and their app icons are on screen, nothing blank at the seam.
        rule.onRoot().performTouchInputDrag()
        val a = angles(Corner.BottomRight)
        assertTrue("both sides of the seam are on screen mid-drag: $a", a.containsKey("#") && a.containsKey("A"))
        assertTrue("no gap between neighbours at the seam: $a", a.size >= 3)
        shot("w30-mid-fling-portrait-right")
    }

    /** Wave 30 review: a second key press while the ring is still turning steps on from the group it is heading for. */
    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun INP_18_w30r_two_quick_Right_presses_move_two_sections_and_a_quick_reversal_comes_back() {
        show(Corner.BottomRight)
        fun tap(code: Int) { rule.runOnUiThread { LauncherInput.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); LauncherInput.onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)) } }
        tap(KeyEvent.KEYCODE_DPAD_RIGHT); rule.mainClock.advanceTimeBy(48)
        tap(KeyEvent.KEYCODE_DPAD_RIGHT); rule.mainClock.advanceTimeBy(48)
        settle()
        assertEquals("C", selected())
        tap(KeyEvent.KEYCODE_DPAD_LEFT); rule.mainClock.advanceTimeBy(48)
        tap(KeyEvent.KEYCODE_DPAD_LEFT); rule.mainClock.advanceTimeBy(48)
        tap(KeyEvent.KEYCODE_DPAD_LEFT); rule.mainClock.advanceTimeBy(48)
        settle()
        assertEquals("#", selected())
    }

    /** Wave 30 review: a ring too narrow to loop is drawn once, so it gets one accessibility node per section (no ghost copies). */
    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_01_w30r_a_small_ring_has_one_node_per_section() {
        show(Corner.BottomRight, letterApps.take(6))
        val labels = sections().fetchSemanticsNodes().map { labelOf(it.config[SemanticsProperties.ContentDescription].single()) }
        assertEquals("each section once: $labels", labels.size, labels.toSet().size)
    }

    /** Wave 30 review: with animations off the ring snaps (no spring in between) across the seam too. */
    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_01_w30r_reduced_motion_snaps_across_the_seam_without_a_spring() {
        com.bos.sphere.core.design.ReducedMotionTesting.force(com.bos.sphere.core.quality.MotionPrefs(0f, 0f, 0f))
        try {
            show(Corner.BottomRight)
            rule.runOnUiThread { LauncherInput.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)) }
            rule.mainClock.advanceTimeBy(32)
            assertEquals("a single frame or two is enough: no spring", "#", selected())
            val a = angles(Corner.BottomRight)
            assertEquals("the new section is centred at once: $a", 45f, a.getValue("#"), 2f)
        } finally { com.bos.sphere.core.design.ReducedMotionTesting.force(null) }
    }

    /** Wave 30 review: a section node is a button for TalkBack (it has a selected state and a click, so it must say what it is). */
    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun INP_18_w30r_section_nodes_are_buttons() {
        show(Corner.BottomRight)
        val nodes = sections().fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty())
        assertTrue(nodes.all { it.config.getOrNull(SemanticsProperties.Role) == androidx.compose.ui.semantics.Role.Button })
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performTouchInputDrag() {
        performTouchInput {
            val from = androidx.compose.ui.geometry.Offset(width * 0.85f, height * 0.55f)
            val to = androidx.compose.ui.geometry.Offset(width * 0.55f, height * 0.85f)
            down(from); moveTo(androidx.compose.ui.geometry.Offset((from.x + to.x) / 2, (from.y + to.y) / 2))
        }
        rule.mainClock.advanceTimeBy(400)
    }

    private fun shot(name: String) {
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
