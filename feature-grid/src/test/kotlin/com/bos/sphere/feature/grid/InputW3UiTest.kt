package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.os.Process
import android.view.KeyEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.ComplicationKind
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.CornerSlot
import com.bos.sphere.core.input.FocusKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.reflect.Proxy

/** Wave 3 input: Radar pointer affordances (INP-02), complication and widget corner nodes (INP-17), the dialog key hook (INP-20). */
@OptIn(ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InputW3UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test @Config(sdk = [35], qualifiers = "w411dp-h700dp-port-xhdpi")
    fun INP_02_a_radar_tile_answers_a_right_click_and_not_a_left_click_and_uses_the_hand_pointer() {
        val secondary = mutableListOf<String>()
        rule.setContent { Box(Modifier.fillMaxSize()) { Box(Modifier.size(80.dp).testTag("tile").secondaryClick { secondary += "menu" }) } }
        rule.waitForIdle()
        rule.onNodeWithTag("tile").performMouseInput { click() }
        assertEquals("a primary click is not a secondary click", emptyList<String>(), secondary)
        rule.onNodeWithTag("tile").performMouseInput { rightClick() }
        rule.waitForIdle()
        assertEquals(listOf("menu"), secondary)
        assertEquals(PointerIcon.Hand, RADAR_POINTER_ICON)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h700dp-port-xhdpi")
    fun INP_02_hovering_a_radar_tile_reports_the_hand_icon_and_leaving_clears_it_and_a_finger_never_hovers() {
        val seen = mutableListOf<PointerIcon?>()
        RadarHoverProbe.observer = { seen += it }
        try {
            rule.setContent { Box(Modifier.fillMaxSize()) { Box(Modifier.size(80.dp).testTag("tile").secondaryClick {}) } }
            rule.waitForIdle()
            seen.clear() // the first report is the initial not-hovered state
            rule.onNodeWithTag("tile").performTouchInput { click() }
            rule.waitForIdle()
            assertEquals("a finger tap never hovers", emptyList<PointerIcon?>(), seen)
            rule.onNodeWithTag("tile").performMouseInput { moveTo(center) }
            rule.waitForIdle()
            assertEquals(listOf<PointerIcon?>(PointerIcon.Hand), seen)
            rule.onNodeWithTag("tile").performMouseInput { moveTo(Offset(-50f, -50f)) }
            rule.waitForIdle()
            assertEquals(listOf<PointerIcon?>(PointerIcon.Hand, null), seen)
        } finally { RadarHoverProbe.observer = null }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h700dp-port-xhdpi")
    fun INP_17_a_complication_corner_is_a_corner_node_and_enter_runs_its_action() {
        val ran = mutableListOf<CornerItem>()
        val placements = cornerPlacements(mapOf(Corner.TopLeft to CornerSlot.Complication(ComplicationKind.Date)), 0, false)
        rule.setContent {
            androidx.compose.ui.platform.LocalInputModeManager.current.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard)
            InputModeProvider {
                CornerLayer(
                    placements, com.bos.sphere.core.data.CornerStyle.Glyph, emptyMap(), onRun = { item, _ -> ran += item }, onEdit = {},
                    complication = { _, slot, m -> ComplicationView(slot.kind, 48.dp, onClick = { ran += CornerItem.Action(slot.kind.action!!) }, modifier = m.testTag("comp")) },
                    cluster = { _, _, _ -> }, widget = { _, _ -> },
                )
            }
        }
        rule.waitForIdle()
        val node = LauncherInput.focus.nodes().single { it.id == "corner.TopLeft" }
        assertEquals(FocusKind.Corner, node.kind)
        LauncherInput.focus.request("corner.TopLeft"); rule.waitForIdle()
        assertEquals("corner.TopLeft", LauncherInput.focus.currentId)
        rule.onNodeWithTag("comp").performKeyInput { keyDown(androidx.compose.ui.input.key.Key.Enter); keyUp(androidx.compose.ui.input.key.Key.Enter) }
        rule.waitForIdle()
        assertEquals(listOf<CornerItem>(CornerItem.Action(CornerActionId.Calendar)), ran)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h700dp-port-xhdpi")
    fun INP_17_the_collapsed_widget_peek_is_a_corner_node() {
        rule.setContent {
            androidx.compose.ui.platform.LocalInputModeManager.current.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard)
            InputModeProvider {
                val host = LauncherWidgetHost(androidx.compose.ui.platform.LocalContext.current)
                val expandedState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                PeekWidget(host, appWidgetId = 4242, label = "Weather", anchor = Corner.BottomRight, expanded = expandedState.value, onExpandedChange = { expandedState.value = it }, onRemove = {}, modifier = Modifier.fillMaxSize())
            }
        }
        rule.waitForIdle()
        val node = LauncherInput.focus.nodes().singleOrNull { it.id == "corner.BottomRight" }
        assertTrue("the peek registers as a corner node", node != null && node.kind == FocusKind.Corner)
    }

    private class Recorder { val keys = mutableListOf<Int>() }

    @Test @Config(sdk = [35])
    fun INP_20_the_dialog_window_hook_turns_b_a_x_and_shoulders_into_dialog_keys_and_leaves_other_keys_alone() {
        val rec = Recorder()
        val base = Proxy.newProxyInstance(Window.Callback::class.java.classLoader, arrayOf(Window.Callback::class.java)) { _, m, args ->
            if (m.name == "dispatchKeyEvent") { rec.keys += (args[0] as KeyEvent).keyCode; true } else if (m.returnType == Boolean::class.javaPrimitiveType) false else null
        } as Window.Callback
        val hook = controllerWindowCallback(base)
        fun press(code: Int) = hook.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        press(KeyEvent.KEYCODE_BUTTON_B); press(KeyEvent.KEYCODE_ESCAPE); press(KeyEvent.KEYCODE_BUTTON_A); press(KeyEvent.KEYCODE_DPAD_LEFT); press(KeyEvent.KEYCODE_Q)
        assertEquals(
            listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_Q),
            rec.keys,
        )
        // While the shortcut sheet captures a chord, Esc reaches it untranslated.
        rec.keys.clear(); DialogKeyGate.passThrough = true
        try { press(KeyEvent.KEYCODE_ESCAPE) } finally { DialogKeyGate.passThrough = false }
        assertEquals(listOf(KeyEvent.KEYCODE_ESCAPE), rec.keys)
    }

    private fun app(i: Int) = AppEntry("p$i", ComponentName("p$i", "M"), "App ${'A' + i}", Process.myUserHandle()) { null }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h700dp-port-xhdpi")
    fun INP_01_drawer_rows_are_reached_by_arrow_and_tab_and_enter_launches_the_focused_one() {
        val rows = (0 until 6).map { DrawerRow.App(app(it)) }
        val launched = mutableListOf<String>()
        rule.setContent {
            androidx.compose.ui.platform.LocalInputModeManager.current.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard)
            InputModeProvider {
                // A home item below the drawer: the drawer layer keeps arrows away from it.
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.size(40.dp).launcherItem("grid.under", FocusKind.Item))
                    FocusLayer(DRAWER_FOCUS_LAYER) {
                        DrawerGrid(androidx.compose.foundation.lazy.grid.rememberLazyGridState(), rows, androidx.compose.foundation.layout.PaddingValues(0.dp), onLaunch = { launched += it.packageName }, onLongPress = {})
                    }
                }
            }
        }
        rule.waitForIdle()
        val ids = LauncherInput.focus.nodes().map { it.id }.filter { it.startsWith("drawer.") }
        assertEquals(6, ids.size)
        LauncherInput.focus.request(ids.first()); rule.waitForIdle()
        assertEquals(ids.first(), LauncherInput.focus.currentId)
        assertTrue(LauncherInput.focus.move(com.bos.sphere.core.input.FocusDir.Right)); rule.waitForIdle()
        val second = LauncherInput.focus.currentId
        assertTrue("arrow stays inside the drawer: $second", second != null && second.startsWith("drawer.") && second != ids.first())
        assertTrue(LauncherInput.focus.tab(false)); rule.waitForIdle()
        assertTrue(LauncherInput.focus.currentId!!.startsWith("drawer."))
        val focused = LauncherInput.focus.currentId!!.removePrefix("drawer.")
        rule.onAllNodes(androidx.compose.ui.test.hasClickAction())[ids.indexOf("drawer.$focused").coerceAtLeast(0)]
            .performKeyInput { keyDown(androidx.compose.ui.input.key.Key.Enter); keyUp(androidx.compose.ui.input.key.Key.Enter) }
        rule.waitForIdle()
        assertEquals("one app launched by Enter", 1, launched.size)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h2000dp-port-xhdpi")
    fun INP_07_every_corner_action_is_a_row_the_corner_editor_offers_and_choosing_it_assigns_that_action() {
        val picked = mutableListOf<CornerSlot>()
        rule.setContent {
            CornerEditorDialog(Corner.TopLeft, CornerSlot.Single(CornerItem.Action(CornerActionId.Search)), apps = emptyList(), appsByKey = emptyMap(), onSave = { picked += it }, onPickWidget = {}, onDismiss = {})
        }
        rule.waitForIdle()
        for (a in CornerActionId.entries) {
            rule.onNodeWithText(com.bos.sphere.core.design.L10n.tr(a.label)).assertExists().performClick()
            rule.waitForIdle()
            // An action that opens an app asks "open with" first; the system default row assigns the action itself.
            if (picked.lastOrNull() != CornerSlot.Single(CornerItem.Action(a))) {
                rule.onNodeWithText(com.bos.sphere.core.design.L10n.tr("System default")).performClick(); rule.waitForIdle()
                // The real dialog closes on save; this one stays open, so step back to the list.
                rule.onNodeWithText(com.bos.sphere.core.design.L10n.tr("‹  Back")).performClick(); rule.waitForIdle()
            }
            assertEquals("choosing ${a.label} assigns it", CornerSlot.Single(CornerItem.Action(a)), picked.last())
        }
        assertEquals(CornerActionId.entries.size, picked.size)
    }

    @Test @Config(sdk = [35]) fun INP_07_commands_with_a_corner_touch_path_name_an_action_the_defaults_bind_or_the_editor_offers() {
        for (c in com.bos.sphere.core.input.LauncherCommand.entries) {
            val a = TouchPaths.cornerActionFor(c)
            // Wave 4 shrank describedOnly: a command without a corner action is either rendered by a named test or still described only.
            if (a == null) assertTrue(c in TouchPaths.describedOnly || c in TouchPaths.renderedBy) else assertTrue("$c names $a", a in CornerActionId.entries && c !in TouchPaths.describedOnly)
        }
        // Radar and all apps are also on default gestures, so a bare install reaches them by finger.
        assertTrue(com.bos.sphere.core.data.DefaultGestures.values.contains(CornerItem.Action(CornerActionId.Search)))
        assertTrue(com.bos.sphere.core.data.DefaultGestures.values.contains(CornerItem.Action(CornerActionId.AllApps)))
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h700dp-port-xhdpi")
    fun INP_09_in_edit_mode_shift_arrow_on_the_focused_app_files_it_and_on_a_widget_resizes_it_through_the_live_hook() {
        val widget = com.bos.sphere.core.catalog.ItemKeys.widget(7)
        val state = com.bos.sphere.core.catalog.HomeState(
            pages = listOf(com.bos.sphere.core.data.HomePage(listOf("a", "b", widget))),
            widgets = mapOf(widget to com.bos.sphere.core.catalog.WidgetSpan(2, 2)),
        )
        val actions = mutableListOf<ItemAction>()
        val extras = HomeExtras(state, resolveApp = { null }, shortcutIcon = { null }, widget = { _, _ -> }, onAction = { actions += it })
        rule.setContent {
            androidx.compose.ui.platform.LocalInputModeManager.current.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard)
            InputModeProvider {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.size(40.dp).testTag("a").launcherItem("grid.a"))
                    Box(Modifier.size(40.dp).testTag("w").launcherItem("grid.$widget"))
                    GridKeyboardMove(state.pages, com.bos.sphere.core.data.LauncherPrefs(), false, { 12 }, {}, extras = extras)
                }
            }
        }
        rule.waitForIdle()
        LauncherInput.editMode.value = true
        try {
            LauncherInput.focus.request("grid.a"); rule.waitForIdle()
            fun shiftArrow(code: Int) = LauncherInput.onKeyEvent(KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, code, 0, KeyEvent.META_SHIFT_ON))
            assertTrue(shiftArrow(KeyEvent.KEYCODE_DPAD_RIGHT))
            assertEquals(ItemAction.FileInto("a", "b"), actions.single())
            actions.clear()
            LauncherInput.focus.request("grid.$widget"); rule.waitForIdle()
            assertTrue(shiftArrow(KeyEvent.KEYCODE_DPAD_RIGHT))
            val resize = actions.single() as ItemAction.Resize
            assertEquals(widget, resize.key); assertEquals(1, resize.dCols)
        } finally { LauncherInput.editMode.value = false }
    }
}
