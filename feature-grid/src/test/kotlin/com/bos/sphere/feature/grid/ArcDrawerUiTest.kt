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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.ArcShortcuts
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.ShortcutsShown
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

/** W28 arcShortcuts: the alphabet wheel, its app fan, the shortcuts in their three modes, driven and rendered on the JVM. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArcDrawerUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private val names = listOf(
        "Alarm", "Amazon", "Atlas", "Bank", "Books", "Browser", "Calendar", "Camera", "Chat", "Clock", "Maps", "Mail", "Music", "News", "Notes", "Phone",
        "Photos", "Podcasts", "Radio", "Reader", "Settings", "Spark", "Tasks", "Video", "Weather", "Wallet", "Yoga", "Zen", "1Password", "Zoom",
    )
    private val apps: List<AppEntry> = names.mapIndexed { i, n ->
        AppEntry("demo.a$i", ComponentName("demo.a$i", "M"), n, Process.myUserHandle()) { ColorDrawable(0xFF3070D0.toInt() + (i * 0x090705)) }
    }
    private val byKey = apps.associateBy { it.key }
    /** Apps on letters the wheel does not start on, so a visible one is a shortcut and not part of the fan. */
    private val shortcutApps: List<AppEntry> = listOf("Wallet", "Yoga", "Zen", "Zoom").map { n -> apps.first { it.label == n } }
    private fun appsOf(letter: Char) = apps.filter { arcLetterOf(it.label) == letter.toString() }

    @After fun reset() = ReducedMotionTesting.force(null)

    private fun shoot(name: String, settleMs: Long = 1_500) {
        rule.mainClock.advanceTimeBy(settleMs)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val launched = mutableListOf<String>()
    private val menus = mutableListOf<String>()
    private var dismissed = 0
    private var searched = 0

    private fun wheel(anchor: Corner = Corner.BottomLeft, shortcuts: List<AppEntry> = emptyList(), startLetter: String? = "A", rtl: Boolean = false, list: List<AppEntry> = apps) {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Box(Modifier.fillMaxSize().background(Color(0xFF0B0B10))) {
                    WheelDrawer(
                        visible = true, anchor = anchor, apps = list, shortcuts = shortcuts, startLetter = startLetter,
                        onLaunch = { launched += it.label }, onLongPressApp = { menus += it.label }, onSearch = { searched++ }, onDismiss = { dismissed++ },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_200)
    }

    private val tabRole = SemanticsMatcher("selected letter") {
        it.config.getOrNull(SemanticsProperties.Selected) == true && it.config.getOrNull(SemanticsProperties.Role) == androidx.compose.ui.semantics.Role.Tab
    }

    private fun selectedLetter(): String {
        val n = rule.onAllNodes(tabRole).fetchSemanticsNodes().single()
        return n.config[SemanticsProperties.ContentDescription].first().removePrefix("Letter ").substringBefore(",")
    }

    private fun key(code: Int) {
        rule.runOnUiThread { LauncherInput.onKeyEvent(android.view.KeyEvent(0L, 0L, android.view.KeyEvent.ACTION_DOWN, code, 0)) }
        rule.mainClock.advanceTimeBy(600)
    }

    private fun appNodeCount(a: AppEntry) = rule.onAllNodesWithContentDescription(a.label).fetchSemanticsNodes().size

    // ---- screenshots (read against the owner's picture)

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun shots_wheel_no_shortcuts_portrait() { rule.mainClock.autoAdvance = false; wheel(startLetter = "C"); shoot("w28-wheel-portrait-noshortcuts") }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun shots_wheel_with_shortcuts_portrait() { rule.mainClock.autoAdvance = false; wheel(shortcuts = shortcutApps, startLetter = "C"); shoot("w28-wheel-portrait-shortcuts") }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun shots_wheel_many_apps_portrait() {
        rule.mainClock.autoAdvance = false
        val many = (0 until 60).map { i -> AppEntry("demo.m$i", ComponentName("demo.m$i", "M"), "Mm${('a' + i % 26)}$i", Process.myUserHandle()) { ColorDrawable(0xFFD9D9D9.toInt()) } }
        wheel(shortcuts = many.take(4), startLetter = "M", list = many); shoot("w28-wheel-portrait-many")
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun shots_wheel_landscape_shortcuts_and_right_handed() {
        rule.mainClock.autoAdvance = false
        wheel(shortcuts = shortcutApps, startLetter = "S"); shoot("w28-wheel-landscape-shortcuts")
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun shots_wheel_landscape_right_corner() { rule.mainClock.autoAdvance = false; wheel(anchor = Corner.BottomRight, startLetter = "B"); shoot("w28-wheel-landscape-right") }

    // ---- the home with the four squares, the shortcuts and the wheel in the three modes

    private var wheelOpen by mutableStateOf(false)
    private fun host(on: Boolean, shown: ShortcutsShown, keys: List<String> = shortcutApps.map { it.key }, corner: Corner? = Corner.BottomLeft, wheelStart: Boolean = false) {
        wheelOpen = wheelStart
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF0B0B10))) {
                MinimalHotspotLayer(
                    hotspots = listOfNotNull(corner?.let { Hotspot(it, CornerItem.Action(CornerActionId.AllApps)) }),
                    appsByKey = byKey, showHints = false, onRun = { _, _ -> wheelOpen = true }, showIcons = true,
                )
                ArcWheelHost(
                    wheelOpen = wheelOpen, wheelAnchor = corner ?: Corner.BottomLeft, restCorner = corner, restHidden = false, apps = apps, appsByKey = byKey,
                    shortcutsOn = on, shortcutKeys = keys, shortcutsShown = shown, liftDp = 0f,
                    onLaunch = { launched += it.label }, onLongPress = { menus += it.label }, onSearch = { searched++ }, onDismiss = { wheelOpen = false },
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_200)
    }

    private fun shortcutsShownNow() = shortcutApps.count { appNodeCount(it) > 0 }

    private fun tapFourSquares() {
        rule.onNode(androidx.compose.ui.test.hasContentDescription("Bottom left hotspot", substring = true)).performClick()
        rule.mainClock.advanceTimeBy(1_500)
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ARC_06_always_the_shortcuts_stay_on_the_home_and_the_four_squares_open_the_wheel_with_them() {
        host(true, ShortcutsShown.Always)
        assertEquals("all four shortcuts at rest", 4, shortcutsShownNow())
        rule.onAllNodesWithContentDescription("Alphabet wheel").assertCountEquals(0)
        tapFourSquares()
        rule.onAllNodesWithContentDescription("Alphabet wheel").assertCountEquals(1)
        assertEquals("the shortcuts stay with the wheel", 4, shortcutsShownNow())
        shoot("w28-host-always-wheel-portrait")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ARC_06_always_screenshot_at_rest_portrait() { rule.mainClock.autoAdvance = false; host(true, ShortcutsShown.Always); shoot("w28-host-always-rest-portrait") }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun ARC_06_always_screenshot_at_rest_landscape() { rule.mainClock.autoAdvance = false; host(true, ShortcutsShown.Always); shoot("w28-host-always-rest-landscape") }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ARC_07_only_with_the_wheel_nothing_extra_at_rest_and_the_shortcuts_appear_with_the_wheel_and_leave_with_it() {
        host(true, ShortcutsShown.OnlyWithWheel)
        assertEquals("nothing extra at rest", 0, shortcutsShownNow())
        tapFourSquares()
        rule.onAllNodesWithContentDescription("Alphabet wheel").assertCountEquals(1)
        assertEquals("the bottom row of the wheel", 4, shortcutsShownNow())
        rule.onNodeWithContentDescription("Back").performClick()
        rule.mainClock.advanceTimeBy(1_500)
        rule.onAllNodesWithContentDescription("Alphabet wheel").assertCountEquals(0)
        assertEquals("they close with the wheel", 0, shortcutsShownNow())
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ARC_07_only_with_the_wheel_screenshot_portrait() { rule.mainClock.autoAdvance = false; host(true, ShortcutsShown.OnlyWithWheel, wheelStart = true); shoot("w28-host-onlywheel-portrait") }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ARC_08_shortcuts_off_the_four_squares_just_open_the_wheel_and_no_shortcut_row_exists() {
        host(false, ShortcutsShown.Always)
        assertEquals(0, shortcutsShownNow())
        tapFourSquares()
        rule.onAllNodesWithContentDescription("Alphabet wheel").assertCountEquals(1)
        assertEquals("no shortcut row anywhere", 0, shortcutsShownNow())
        shoot("w28-host-off-wheel-portrait")
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun ARC_08_shortcuts_off_landscape_screenshot() { rule.mainClock.autoAdvance = false; host(false, ShortcutsShown.Always, wheelStart = true); shoot("w28-host-off-wheel-landscape") }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ARC_09_the_four_squares_label_stays_apps_for_accessibility_and_without_the_corner_hotspot_no_row_shows_at_rest() {
        host(true, ShortcutsShown.Always, corner = null)
        assertEquals("no four squares corner: nothing at rest", 0, shortcutsShownNow())
        wheelOpen = true; rule.mainClock.advanceTimeBy(1_500)
        assertEquals("but the wheel still has its row", 4, shortcutsShownNow())
    }

    // ---- the wheel

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ARC_10_keyboard_steps_round_the_looped_alphabet_x_y_z_hash_a_b() {
        wheel(startLetter = "X")
        val seen = mutableListOf(selectedLetter())
        repeat(5) { key(android.view.KeyEvent.KEYCODE_DPAD_RIGHT); seen += selectedLetter() }
        assertEquals(listOf("X", "Y", "Z", "#", "A", "B"), seen)
        repeat(5) { key(android.view.KeyEvent.KEYCODE_DPAD_LEFT) }
        assertEquals("X", selectedLetter())
        key(android.view.KeyEvent.KEYCODE_DPAD_UP); assertEquals("W", selectedLetter())
        key(android.view.KeyEvent.KEYCODE_DPAD_DOWN); assertEquals("X", selectedLetter())
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ARC_10_typing_a_letter_jumps_to_it_and_enter_launches_the_first_app() {
        wheel(startLetter = "A")
        key(android.view.KeyEvent.KEYCODE_C)
        assertEquals("C", selectedLetter())
        key(android.view.KeyEvent.KEYCODE_ENTER)
        assertEquals(listOf("Calendar"), launched)
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ARC_11_dragging_along_the_ring_turns_it_and_it_loops_past_z_hash_to_a() {
        wheel(startLetter = "Y")
        val seen = mutableListOf(selectedLetter())
        var wrapped = false
        repeat(12) {
            rule.onNodeWithContentDescription("Alphabet wheel").performTouchInput {
                val c = Offset(30.dp.toPx(), height - 30.dp.toPx())
                val r = 76.dp.toPx()
                fun at(u: Float) = Offset(c.x + r * Math.cos(Math.toRadians(u.toDouble())).toFloat(), c.y - r * Math.sin(Math.toRadians(u.toDouble())).toFloat())
                swipe(at(30f), at(50f), 400)
            }
            rule.mainClock.advanceTimeBy(2_000)
            val now = selectedLetter()
            if (AlphabetRing.indexOf(now) < AlphabetRing.indexOf(seen.last())) wrapped = true // forward past # lands on A: the index falls
            seen += now
        }
        assertTrue("the drag moved the ring: $seen", seen.toSet().size > 3)
        assertTrue("and it looped from # round to A: $seen", wrapped)
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ARC_12_tapping_a_letter_shows_its_apps_and_tapping_an_app_launches_it_long_press_opens_the_menu() {
        wheel(startLetter = "A")
        assertEquals("A apps shown", 3, appsOf('A').count { appNodeCount(it) > 0 })
        assertEquals("B apps are not", 0, appsOf('B').count { appNodeCount(it) > 0 })
        rule.onNodeWithContentDescription("Letter B, 3 apps").performClick()
        rule.mainClock.advanceTimeBy(1_500)
        assertEquals("B", selectedLetter())
        assertEquals("the B apps fan out", 3, appsOf('B').count { appNodeCount(it) > 0 })
        assertEquals("the A apps left", 0, appsOf('A').count { appNodeCount(it) > 0 })
        rule.onNodeWithContentDescription("Bank").performClick()
        assertEquals(listOf("Bank"), launched)
        rule.onNodeWithContentDescription("Books").performTouchInput { longClick() }
        assertEquals(listOf("Books"), menus)
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ARC_12_apps_are_plain_icons_not_circles_the_icon_fills_its_square_to_the_corners() {
        wheel(startLetter = "A", list = apps.filter { it.label == "Alarm" })
        rule.mainClock.advanceTimeBy(1_000)
        val node = rule.onNodeWithContentDescription("Alarm").fetchSemanticsNode()
        val b = node.boundsInRoot
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        // The demo icon is a flat colour square: its corner pixels are the icon, not the dark background a circular clip would leave.
        // 10% in from the corner: inside a rounded square, outside the circle inscribed in it.
        val px = bmp.getPixel((b.left + b.width * 0.1f).toInt(), (b.top + b.height * 0.1f).toInt())
        val blue = px and 0xFF
        assertTrue("the icon's corner is painted (not clipped to a disc): ${Integer.toHexString(px)}", blue > 0x80)
        assertEquals("the icon is 48 dp square", b.width, b.height, 1f)
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ARC_13_back_arrow_closes_the_wheel_and_the_search_wedge_opens_radar() {
        wheel()
        rule.onNodeWithContentDescription("Search apps").performClick()
        assertEquals(1, searched)
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, dismissed)
        key(android.view.KeyEvent.KEYCODE_ESCAPE)
        assertEquals(2, dismissed)
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ARC_14_talkback_gets_next_and_previous_letter_actions_and_each_letter_is_a_labelled_item() {
        wheel(startLetter = "C")
        val ring = rule.onNodeWithContentDescription("Alphabet wheel").fetchSemanticsNode()
        val actions = ring.config[SemanticsActions.CustomActions]
        rule.runOnUiThread { actions.first { it.label == "Next letter" }.action() }
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals("D", selectedLetter())
        rule.runOnUiThread { rule.onNodeWithContentDescription("Alphabet wheel").fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "Previous letter" }.action() }
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals("C", selectedLetter())
        for (l in listOf("A", "M", "Z", "#")) assertEquals(1, rule.onAllNodesWithContentDescription("Letter $l,", substring = true).fetchSemanticsNodes().size)
        assertEquals("apps are items with a collection position in rank order", 0, rule.onNodeWithContentDescription("Calendar").fetchSemanticsNode().config[SemanticsProperties.CollectionItemInfo].rowIndex)
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ARC_15_the_wheel_mirrors_to_the_right_corner_and_ignores_right_to_left_layout() {
        wheel(anchor = Corner.BottomRight)
        val w = rule.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot
        assertTrue("hub on the right", w.center.x > rule.activity.findViewById<android.view.View>(android.R.id.content).width / 2f)
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ARC_15_right_to_left_layout_does_not_move_the_physical_corner() {
        // Same corner under right to left: the corner is physical, so the hub stays where the corner is.
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.fillMaxSize()) {
                    WheelDrawer(visible = true, anchor = Corner.BottomLeft, apps = apps, onLaunch = {}, onLongPressApp = {}, onSearch = {}, onDismiss = {}, modifier = Modifier.fillMaxSize())
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_200)
        val b = rule.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot
        assertTrue("left-handed hub stays on the left in RTL", b.center.x < rule.activity.findViewById<android.view.View>(android.R.id.content).width / 2f)
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ARC_16_reduced_motion_snaps_to_the_letter_without_spring_or_inertia() {
        ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f))
        wheel(startLetter = "A")
        rule.onNodeWithContentDescription("Letter C, 4 apps").performClick()
        rule.mainClock.advanceTimeByFrame(); rule.mainClock.advanceTimeByFrame()
        assertEquals("C", selectedLetter())
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ARC_17_a_full_fan_scrolls_when_there_are_more_apps_than_slots() {
        val many = (0 until 80).map { i -> AppEntry("demo.q$i", ComponentName("demo.q$i", "M"), "Q%02d".format(i), Process.myUserHandle()) { ColorDrawable(0xFF336699.toInt()) } }
        wheel(startLetter = "Q", list = many)
        val before = many.count { appNodeCount(it) > 0 }
        assertTrue("some, not all: $before", before in 5 until 80)
        assertEquals("the first apps come first", 1, appNodeCount(many[0]))
        // Drag the fan towards the corner: the list moves on.
        rule.onNodeWithContentDescription("Alphabet wheel").performTouchInput {
            swipe(Offset(width * 0.7f, height * 0.4f), Offset(width * 0.7f - 120f, height * 0.4f + 140f), 300)
        }
        rule.mainClock.advanceTimeBy(500)
        assertEquals("scrolled: the first app is gone", 0, appNodeCount(many[0]))
    }

    // ---- bounds at the five test sizes

    private fun boundsCheck(anchor: Corner) {
        wheel(anchor = anchor, shortcuts = shortcutApps, startLetter = "S", list = apps + (0 until 30).map { i -> AppEntry("demo.s$i", ComponentName("demo.s$i", "M"), "S%02d".format(i), Process.myUserHandle()) { ColorDrawable(0xFF999999.toInt()) } })
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val w = view.width.toFloat(); val h = view.height.toFloat()
        val nodes = rule.onAllNodes(SemanticsMatcher("an app icon") { n -> n.config.getOrNull(SemanticsProperties.CollectionItemInfo) != null }).fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty())
        val rects = nodes.map { it.boundsInRoot }
        for (r in rects) assertTrue("icon inside the window: $r in ${w}x$h", r.left >= -1f && r.top >= -1f && r.right <= w + 1f && r.bottom <= h + 1f)
        for (i in rects.indices) for (j in i + 1 until rects.size) assertFalse("icons $i and $j overlap: ${rects[i]} ${rects[j]} in ${w}x$h", rects[i].overlaps(rects[j].deflate(1f)))
        val dens = rule.density.density
        val hot = 64f * dens
        val opposite = if (anchor == Corner.BottomLeft) androidx.compose.ui.geometry.Rect(w - hot, h - hot, w, h) else androidx.compose.ui.geometry.Rect(0f, h - hot, hot, h)
        for (r in rects) assertFalse("icon clear of the Radar hotspot", r.overlaps(opposite))
    }

    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-port-xhdpi") fun ARC_18_bounds_360x640() = boundsCheck(Corner.BottomLeft)
    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi") fun ARC_18_bounds_411x891() = boundsCheck(Corner.BottomRight)
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi") fun ARC_18_bounds_891x411() = boundsCheck(Corner.BottomLeft)
    @Test @Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-xhdpi") fun ARC_18_bounds_1280x800() = boundsCheck(Corner.BottomLeft)
    @Test @Config(sdk = [34], qualifiers = "w280dp-h653dp-port-xhdpi") fun ARC_18_bounds_280x653() = boundsCheck(Corner.BottomRight)

    // ---- the shortcut row at rest at the five sizes

    private fun restBounds() {
        host(true, ShortcutsShown.Always)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val w = view.width.toFloat(); val h = view.height.toFloat(); val d = rule.density.density
        val rects = shortcutApps.map { rule.onNodeWithContentDescription(it.label).fetchSemanticsNode().boundsInRoot }
        val glyph = androidx.compose.ui.geometry.Rect(0f, h - 64f * d, 64f * d, h)
        val otherHot = androidx.compose.ui.geometry.Rect(w - 64f * d, h - 64f * d, w, h)
        val indicator = androidx.compose.ui.geometry.Rect(w / 2f - 56f * d, h - 76f * d, w / 2f + 56f * d, h)
        for (r in rects) {
            assertTrue("on screen $r", r.left >= 0 && r.top >= 0 && r.right <= w && r.bottom <= h)
            assertFalse("glyph", r.overlaps(glyph)); assertFalse("hotspot", r.overlaps(otherHot)); assertFalse("indicator", r.overlaps(indicator))
        }
        for (i in rects.indices) for (j in i + 1 until rects.size) assertFalse(rects[i].overlaps(rects[j]))
    }

    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-port-xhdpi") fun ARC_19_rest_bounds_360x640() = restBounds()
    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi") fun ARC_19_rest_bounds_411x891() = restBounds()
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi") fun ARC_19_rest_bounds_891x411() = restBounds()
    @Test @Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-xhdpi") fun ARC_19_rest_bounds_1280x800() = restBounds()
    @Test @Config(sdk = [34], qualifiers = "w280dp-h653dp-port-xhdpi") fun ARC_19_rest_bounds_280x653() = restBounds()
}
