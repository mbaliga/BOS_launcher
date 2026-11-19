package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.CornerSlot
import com.bos.sphere.core.data.CornerStyle
import com.bos.sphere.core.data.DrawerSort
import com.bos.sphere.core.data.DrawerStyle
import com.bos.sphere.core.data.DrawerView
import com.bos.sphere.core.data.HomeGesture
import com.bos.sphere.core.data.HomeLayoutMath
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.RailSide
import com.bos.sphere.core.data.WheelGrouping
import com.bos.sphere.core.input.LauncherCommand
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

/**
 * Wave 4 input: the touch paths that were only described are rendered and touched here (INP-07), plus the finger drag out of a
 * row and the drop on a folder tile (INP-11) and Launchpad as a layer of the shared graph (INP-01). Nothing here is a device run.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InputTouchPathsUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @org.junit.Before fun english() { LauncherInput.tracker.onTouch(); com.bos.sphere.core.design.L10n.setLanguage("en") } // other tests switch the language; the labels looked up here are English

    @After fun reset() { LauncherInput.sheetOpen.value = false; LauncherDrops.onTouchDragStart = null }

    private val labels = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot")
    private val apps = labels.mapIndexed { i, n ->
        AppEntry("demo.t$i", ComponentName("demo.t$i", "demo.t$i.Main"), n, Process.myUserHandle()) { ColorDrawable(0xFF3355AA.toInt() + i * 0x101010) }
    }

    // ---- EditMode: long-press an icon on the paged home, then drag

    @Composable
    private fun Home(changes: MutableList<List<HomePage>>) {
        val prefs = LauncherPrefs()
        val state = rememberGridHomeState()
        val pages = HomeLayoutMath.visualPages(com.bos.sphere.core.data.HomeLayout(pages = listOf(com.bos.sphere.core.data.HomePage(apps.map { it.key }, id = 1L)), seeded = true), apps.map { it.key }, capacityOf = { it.capacity(false, prefs) })
        Box(Modifier.fillMaxSize()) {
            GridHome(
                pages = pages, appsByKey = apps.associateBy { it.key }, prefs = prefs, isLandscape = false, state = state, suggestion = null,
                gesturesBound = setOf(HomeGesture.PinchIn), capacityOf = { it.capacity(false, prefs) },
                onLaunch = {}, onAppLongPress = {}, onPagesChange = { changes += it },
                onEmptyLongPress = {}, onGesture = {}, onClockTap = {}, onTileLongPress = { _, _ -> },
            )
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun INP_07_a_long_press_on_a_paged_home_icon_then_a_drag_rearranges_the_home() {
        rule.mainClock.autoAdvance = false
        val changes = mutableListOf<List<HomePage>>()
        rule.setContent { Home(changes) }
        rule.mainClock.advanceTimeBy(500)
        val from = rule.onNodeWithContentDescription("Alpha").fetchSemanticsNode().boundsInRoot.center
        val to = rule.onNodeWithContentDescription("Foxtrot").fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput {
            down(from)
            advanceEventTime(900) // the long press
            moveTo(Offset(from.x + 40f, from.y + 40f)); advanceEventTime(30)
            moveTo(to); advanceEventTime(30)
            up()
        }
        rule.mainClock.advanceTimeBy(400)
        assertTrue("a long press then a drag ends in a new arrangement", changes.isNotEmpty())
        assertNotEquals(apps[0].key, changes.last().first().keys.first())
        assertEquals(apps.map { it.key }.toSet(), changes.last().flatMap { it.keys }.toSet())
    }

    // ---- Corner taps

    @Test @Config(sdk = [35], qualifiers = "w411dp-h700dp-port-xhdpi")
    fun INP_07_tapping_each_corner_runs_what_it_holds() {
        val ran = mutableListOf<Pair<CornerItem, Corner>>()
        val held = mapOf(
            Corner.TopLeft to CornerActionId.Search, Corner.TopRight to CornerActionId.AllApps,
            Corner.BottomLeft to CornerActionId.Notifications, Corner.BottomRight to CornerActionId.LauncherSettings,
        )
        val placements = cornerPlacements(held.mapValues { CornerSlot.Single(CornerItem.Action(it.value)) }, 0, false)
        rule.setContent {
            CornerLayer(placements, CornerStyle.Glyph, emptyMap(), onRun = { item, screen -> ran += item to screen }, onEdit = {}, complication = { _, _, _ -> }, cluster = { _, _, _ -> }, widget = { _, _ -> })
        }
        rule.waitForIdle()
        for ((corner, id) in held) rule.onNodeWithContentDescription(id.label).performClick()
        rule.waitForIdle()
        assertEquals(held.map { CornerItem.Action(it.value) to it.key }.toSet(), ran.toSet())
        // the four corner commands are the keyboard way to the same four taps (hot corners, INP-16); the touch path is the tap itself
        assertTrue(listOf(LauncherCommand.CornerTopLeft, LauncherCommand.CornerTopRight, LauncherCommand.CornerBottomLeft, LauncherCommand.CornerBottomRight).all { it in TouchPaths.renderedBy })
    }

    // ---- Dismiss: close button and Back

    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() } }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun INP_07_the_close_button_and_back_dismiss_radar() {
        rule.mainClock.autoAdvance = false
        var dismissed = 0
        rule.setContent {
            SearchOverlay(visible = true, apps = apps, recent = apps.take(3), prefs = LauncherPrefs(), contacts = null, onLaunch = {}, onLongPressApp = {}, onOpenIntent = {}, onRequestContactsPermission = {}, onDismiss = { dismissed++ })
        }
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithContentDescription("Close").performClick()
        assertEquals(1, dismissed)
        back()
        rule.mainClock.advanceTimeBy(100)
        assertEquals(2, dismissed)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun INP_07_the_close_button_and_back_dismiss_the_drawer() {
        rule.mainClock.autoAdvance = false
        var dismissed = 0
        rule.setContent {
            AppDrawerSpace(
                visible = true, apps = apps, usage = emptyMap(), view = DrawerView.Grid, sort = DrawerSort.Alphabetical, rail = RailSide.Right,
                onViewChange = {}, onSortChange = {}, onLaunch = {}, onLongPress = {}, onDismiss = { dismissed++ }, modifier = Modifier.fillMaxSize(),
            )
        }
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithContentDescription("Close apps").performClick()
        assertEquals(1, dismissed)
        back()
        rule.mainClock.advanceTimeBy(100)
        assertEquals(2, dismissed)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun INP_07_back_dismisses_ground_control() {
        rule.mainClock.autoAdvance = false
        var closed = 0
        rule.setContent {
            SettingsSpace(visible = true, categories = listOf(SettingCategory("Home", Glyphs.Apps, listOf(keyboardShortcutsRow()))), initialCategory = 0, sounds = null, onClose = { closed++ }, modifier = Modifier.fillMaxSize())
        }
        rule.mainClock.advanceTimeBy(600)
        back()
        rule.mainClock.advanceTimeBy(100)
        assertEquals(1, closed)
    }

    // ---- ShortcutSheet row

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun INP_07_the_keyboard_shortcuts_row_in_home_settings_opens_the_shortcut_sheet() {
        rule.mainClock.autoAdvance = false
        val noop: (com.bos.sphere.core.input.LauncherCommand) -> Boolean = { false }
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                SettingsSpace(visible = true, categories = listOf(SettingCategory("Home", Glyphs.Apps, listOf(keyboardShortcutsRow()))), initialCategory = 0, sounds = null, onClose = {}, modifier = Modifier.fillMaxSize())
                LauncherInputWiring(overlayOpen = true, onCommand = noop, onEmptyMenu = {}, hotCornerDwellMs = 0L, cornerLabel = { null }, onCornerActivate = {})
            }
        }
        rule.mainClock.advanceTimeBy(600)
        assertFalse(LauncherInput.sheetOpen.value)
        rule.onNodeWithText("Keyboard shortcuts").performClick()
        rule.mainClock.advanceTimeBy(300)
        assertTrue("the row opens the sheet", LauncherInput.sheetOpen.value)
        // and the sheet itself is drawn (a dialog window; "Moving around" is a heading only the sheet has)
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Moving around").assertExists()
    }

    // ---- Puck

    @Test @Config(sdk = [35]) fun INP_07_the_drawer_style_decides_between_the_puck_and_the_drawer_for_all_apps() {
        assertEquals(AllAppsSurface.Puck, AllAppsOpening.surfaceFor(DrawerStyle.Wheel))
        for (s in DrawerStyle.entries.filter { it != DrawerStyle.Wheel }) assertEquals(AllAppsSurface.Drawer, AllAppsOpening.surfaceFor(s))
    }

    @Test @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun INP_07_the_all_apps_corner_opens_the_puck_for_the_wheel_style_and_the_puck_is_operable_by_touch() {
        assertEquals(AllAppsSurface.Puck, AllAppsOpening.surfaceFor(LauncherPrefs().drawerStyle)) // the shipped default
        assertEquals(com.bos.sphere.core.data.Corner.entries.size, 4)
        rule.mainClock.autoAdvance = false
        var searched = 0; var dismissed = 0
        val launched = mutableListOf<String>()
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                WheelDrawer(
                    visible = true, anchor = Corner.BottomRight, apps = apps, grouping = WheelGrouping.Letter, onGroupingChange = {}, recentKeys = emptyList(),
                    onLaunch = { launched += it.label }, onLongPressApp = {}, onSearch = { searched++ }, onDismiss = { dismissed++ }, modifier = Modifier.fillMaxSize(),
                )
            }
        }
        rule.mainClock.advanceTimeBy(1200)
        rule.onNodeWithContentDescription("Search apps").performClick()
        assertEquals("the puck's centre runs Radar", 1, searched)
        back()
        rule.mainClock.advanceTimeBy(100)
        assertEquals("Back closes the puck", 1, dismissed)
    }

    // ---- INP-11: a finger drags an app after a long press

    @Test @Config(sdk = [35], qualifiers = "w411dp-h700dp-port-xhdpi")
    fun INP_11_a_finger_drags_an_app_out_of_a_row_after_a_long_press_and_not_on_a_plain_swipe() {
        var started = 0; var menuClosed = 0
        LauncherDrops.onTouchDragStart = { menuClosed++ }
        rule.setContent { Box(Modifier.fillMaxSize()) { Box(Modifier.size(120.dp).testTag("row").appDragSource("demo.t0", onStart = { started++ })) } }
        rule.waitForIdle()
        // a quick swipe is a scroll: nothing starts
        rule.onNodeWithTag("row").performTouchInput { down(center); advanceEventTime(40); moveBy(Offset(60f, 0f)); advanceEventTime(40); up() }
        assertEquals(0, started)
        // a long press that stays put keeps the item's menu: nothing starts either
        rule.onNodeWithTag("row").performTouchInput { down(center); advanceEventTime(900); up() }
        assertEquals(0, started)
        // a long press, then movement: the drag starts and the menu that came with the long press is closed
        rule.onNodeWithTag("row").performTouchInput { down(center); advanceEventTime(900); moveBy(Offset(80f, 0f)); advanceEventTime(40); moveBy(Offset(40f, 0f)); advanceEventTime(40); up() }
        assertEquals(1, started)
        assertEquals(1, menuClosed)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h700dp-port-xhdpi")
    fun INP_11_a_mouse_still_drags_at_once_unless_the_host_keeps_the_mouse_for_itself() {
        var started = 0; var orbitStarted = 0
        rule.setContent {
            androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
                Box(Modifier.size(100.dp).testTag("mouse").appDragSource("demo.t0", onStart = { started++ }))
                Box(Modifier.size(100.dp).testTag("orbit").appDragSource("demo.t1", onStart = { orbitStarted++ }, pointer = false))
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("mouse").performMouseInput { moveTo(center); press(); moveBy(Offset(80f, 0f)); moveBy(Offset(40f, 0f)); release() }
        rule.onNodeWithTag("orbit").performMouseInput { moveTo(center); press(); moveBy(Offset(80f, 0f)); moveBy(Offset(40f, 0f)); release() }
        assertEquals(1, started)
        assertEquals("the Orbit turns its rings with the mouse", 0, orbitStarted)
    }

    // ---- INP-11: the drop on a folder tile

    @Test @Config(sdk = [35]) fun INP_11_a_drop_on_a_folder_tile_files_every_app_and_item_through_the_hosts_edit_path() {
        val dropped = mutableListOf<Pair<String, String>>(); val items = mutableListOf<Pair<String, Int>>()
        val extras = HomeExtras(
            com.bos.sphere.core.catalog.HomeState(), resolveApp = { null }, shortcutIcon = { null }, widget = { _, _ -> },
            onDropOnto = { drag, target -> dropped += drag to target }, onDropItems = { folder, defs -> items += folder to defs.size },
        )
        val link = com.bos.sphere.core.catalog.ItemDef(com.bos.sphere.core.catalog.ItemKeys.link("https://a.example"), com.bos.sphere.core.catalog.ItemKind.Link, "a", "https://a.example")
        val folder = com.bos.sphere.core.catalog.ItemKeys.folder("f")
        assertEquals(3, FolderDrop.file(extras, folder, DropResult(listOf(link), listOf("k1", "k2"), 0)))
        assertEquals(listOf("k1" to folder, "k2" to folder), dropped)
        assertEquals(listOf(folder to 1), items)
        assertEquals("nothing dropped, nothing filed", 0, FolderDrop.file(extras, folder, DropResult(emptyList(), emptyList(), 2)))
        assertEquals(1, items.size)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h700dp-port-xhdpi")
    fun INP_11_the_folder_tile_on_the_home_carries_the_drop_target_and_still_draws() {
        val state = com.bos.sphere.core.catalog.HomeState(folders = mapOf("f" to com.bos.sphere.core.catalog.Folder("f", "F", listOf(apps[0].key, apps[1].key))))
        val extras = HomeExtras(state, resolveApp = { k -> apps.firstOrNull { it.key == k } }, shortcutIcon = { null }, widget = { _, _ -> })
        rule.setContent { Box(Modifier.fillMaxSize()) { ExtraItemFace(com.bos.sphere.core.catalog.ItemKeys.folder("f"), extras, 72.dp, Modifier.testTag("folder")) } }
        rule.waitForIdle()
        rule.onNodeWithTag("folder").assertExists()
    }

    // ---- INP-01: Launchpad is a layer of the shared graph while open

    @Test @Config(sdk = [35], qualifiers = "w411dp-h300dp-port-xhdpi")
    fun INP_01_while_launchpad_is_open_the_home_below_it_gives_no_arrow_or_tab_target() {
        var open by androidx.compose.runtime.mutableStateOf(false)
        rule.setContent {
            InputModeProvider {
                androidx.compose.foundation.layout.Row {
                    for (id in listOf("a", "b")) Box(Modifier.size(80.dp).launcherItem("w4.$id"))
                }
                FocusLayer(layer = 4, active = open) {}
            }
        }
        rule.waitForIdle()
        LauncherInput.focus.request("w4.a"); rule.waitForIdle()
        assertTrue("with Comms closed an arrow moves along the home", LauncherInput.focus.move(com.bos.sphere.core.input.FocusDir.Right))
        LauncherInput.focus.request("w4.a"); rule.waitForIdle()
        rule.runOnUiThread { open = true }
        rule.waitForIdle()
        assertTrue(LauncherInput.focus.anyLayerActive)
        assertFalse("with it open the graph leaves the arrows to the panel (STR-08)", LauncherInput.focus.move(com.bos.sphere.core.input.FocusDir.Right))
        assertFalse(LauncherInput.focus.tab(false))
        rule.runOnUiThread { open = false }
        rule.waitForIdle()
        assertFalse(LauncherInput.focus.anyLayerActive)
    }

    // ---- INP-07 audit: every rendered path names a test that exists

    @Test @Config(sdk = [35]) fun INP_07_every_touch_path_named_as_rendered_has_a_test_that_exists_and_describedOnly_is_what_is_left() {
        val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        for ((cmd, tests) in TouchPaths.renderedBy) {
            assertTrue("$cmd lists no test", tests.isNotEmpty())
            for (t in tests) {
                val file = root.resolve(t.module).walkTopDown().onEnter { it.name != "build" }.firstOrNull { it.isFile && it.name == "${t.testClass}.kt" && "/src/test/" in it.path.replace('\\', '/') }
                assertTrue("$cmd: ${t.module}/${t.testClass}.kt does not exist", file != null)
                assertTrue("$cmd: ${t.testClass} has no test named ${t.testName}", file!!.readText().contains("fun ${t.testName}("))
            }
        }
        val rendered = TouchPaths.renderedBy.keys
        assertTrue("no command is both rendered and described only", rendered.none { it in TouchPaths.describedOnly })
        assertEquals("every command is a corner action, rendered or described", LauncherCommand.entries.toSet(), LauncherCommand.entries.filter { TouchPaths.cornerActionFor(it) != null }.toSet() + rendered + TouchPaths.describedOnly)
        assertEquals("what is described only is nothing now", emptySet<LauncherCommand>(), TouchPaths.describedOnly)
    }
}
