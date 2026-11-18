package com.bos.sphere.feature.dock

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.input.LauncherCommand
import com.bos.sphere.core.input.MenuAction
import com.bos.sphere.core.launch.LaunchCaps
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Wave 4: Radar's chooser entries (PAL-08), number keys without a rail (INP-05), the shared input graph and dialog hook (INP-01, INP-20), dock touch paths (INP-07). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
class W4RadarInputDockTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @org.junit.Before fun english() { com.bos.sphere.core.design.L10n.setLanguage("en") } // other tests switch the language; the labels looked up here are English

    @After fun reset() { DockInput.bridge = null; DockInput.dialogHook = {} }

    private val rich = LaunchCaps(setOf(0, 2, 3), setOf(0, 2, 3), boundsHonoured = true, adjacentSupported = true)
    private val one = LaunchCaps(setOf(0, 2), setOf(0, 2), boundsHonoured = false, adjacentSupported = true)
    private val names = mapOf(2 to "Desk monitor", 3 to "TV")
    private val appRef = DockItemRef.app("a")

    // ---- PAL-08

    @Test fun PAL_08_openOnDisplayWithSeveralDisplaysOffersEachByNameInRadarsChooser() {
        val e = AppActionList.pickerEntries(MenuAction.OpenOnDisplay, LaunchMenus.choices(rich, 0, names), emptyList(), appRef)!!
        assertEquals(listOf("Open on Desk monitor", "Open on TV"), e.map { it.label })
        assertEquals(listOf(2, 3), e.map { (it as AppActionList.PickerEntry.Display).choice.displayId })
        // Beside and the other actions never get a chooser, and no displays means nothing to choose.
        assertNull(AppActionList.pickerEntries(MenuAction.OpenBeside, LaunchMenus.choices(rich, 0, names), emptyList(), appRef))
        assertNull(AppActionList.pickerEntries(MenuAction.OpenOnDisplay, LaunchMenus.choices(LaunchCaps(setOf(0), setOf(0), false, null), 0, names), emptyList(), appRef))
        assertEquals(1, AppActionList.pickerEntries(MenuAction.OpenOnDisplay, LaunchMenus.choices(one, 0, names), emptyList(), appRef)!!.size)
    }

    @Test fun PAL_08_addToWorkspaceListsEveryWorkspaceAndMarksTheOnesThatHoldTheApp() {
        var n = 0
        val m = WorkspaceManager(InMemoryWorkspaceStore(), newId = { "id${n++}" })
        val work = m.create("Work")
        m.addPinTo(work, appRef)
        val e = AppActionList.pickerEntries(MenuAction.AddToWorkspace, emptyList(), m.current.workspaces, appRef)!!.map { it as AppActionList.PickerEntry.Workspace }
        assertEquals(m.current.workspaces.size, e.size)
        assertEquals(listOf(false, true), e.map { it.already })
        assertTrue(e.last().label.endsWith("(added)"))
        assertEquals(m.current.workspaces.first().name, e.first().label)
    }

    @Test fun PAL_08_addPinToPinsTheAppToThatWorkspaceWithoutSwitchingAndNeverTwice() {
        var n = 0
        val m = WorkspaceManager(InMemoryWorkspaceStore(), newId = { "id${n++}" })
        val work = m.create("Work")
        val active = m.current.activeId
        m.addPinTo(work, appRef); m.addPinTo(work, appRef)
        assertEquals(active, m.current.activeId)
        assertEquals(listOf(appRef), m.current.workspaces.first { it.id == work }.dockPins)
        assertTrue(m.current.active.dockPins.isEmpty())
    }

    // ---- INP-05

    @Test fun INP_05_numberKeysLaunchThePinListWhenNoRailIsOnScreen() {
        val c = DockController()
        assertFalse("nothing bound yet: the key is left for others", c.handle(LauncherCommand.Dock1))
        val launched = mutableListOf<Int>()
        c.bindPinLaunch { launched += it; it < 2 }
        assertTrue(c.handle(LauncherCommand.Dock1)); assertTrue(c.handle(LauncherCommand.Dock2))
        assertFalse(c.handle(LauncherCommand.Dock3))
        assertEquals(listOf(0, 1, 2), launched)
    }

    @Test fun INP_05_aComposedRailTakesOverTheNumbersAndGivesThemBackWhenItLeaves() {
        val c = DockController()
        val pins = mutableListOf<Int>(); val rail = mutableListOf<Int>()
        c.bindPinLaunch { pins += it; true }
        c.launchNth = { rail += it; true }
        c.handle(LauncherCommand.Dock4)
        c.releaseRail()
        c.handle(LauncherCommand.Dock5)
        assertEquals(listOf(3), rail); assertEquals(listOf(4), pins)
    }

    @Test fun INP_05_withoutARailTheNumbersCountTheShowablePinsInOrderUpToNine() {
        val refs = (1..12).map { DockItemRef.app("a$it") }
        val gone = setOf(DockItemRef.app("a2"), DockItemRef.app("a3"))
        val numbered = DockPinLaunch.numbered(refs) { it !in gone }
        assertEquals(9, numbered.size)
        assertEquals(listOf("a1", "a4", "a5"), numbered.take(3).map { it.id })
        val launched = mutableListOf<DockItemRef>()
        assertTrue(DockPinLaunch.launchNth(1, refs, { it !in gone }, { launched += it }))
        assertEquals("a4", launched.single().id)
        assertFalse("past the last pin the key is not consumed", DockPinLaunch.launchNth(9, refs, { it !in gone }, { launched += it }))
        assertFalse(DockPinLaunch.launchNth(0, emptyList(), { true }, { launched += it }))
    }

    @Test fun INP_05_workspaceStepKeysActWithNoRailAndFocusDockWaitsForTheRailThatComesBack() {
        val c = DockController()
        val steps = mutableListOf<Int>()
        c.bindWorkspaceStep { steps += it; true }
        assertTrue(c.handle(LauncherCommand.NextWorkspace)); assertTrue(c.handle(LauncherCommand.PreviousWorkspace))
        assertEquals(listOf(1, -1), steps)
        assertFalse(c.railShown)
        assertTrue(c.handle(LauncherCommand.FocusDock))
        assertTrue("an overlay hid the rail: the request waits for it", c.consumePendingFocus())
        assertFalse("and is used once", c.consumePendingFocus())
    }

    // ---- the rail on screen: INP-01, INP-07 taps and swipes

    private class RecordingBridge : DockInput.FocusBridge {
        val live = linkedMapOf<String, FocusRequester>(); val focus = mutableListOf<Pair<String, Boolean>>()
        override fun register(id: String, left: Float, top: Float, right: Float, bottom: Float, requester: FocusRequester) { live[id] = requester }
        override fun unregister(id: String) { live.remove(id) }
        override fun onFocus(id: String, focused: Boolean) { focus += id to focused }
    }

    private fun content(vararg labels: String) = DockContent(
        pinned = labels.mapIndexed { i, l -> DockItem(DockItemRef.app("k$i"), l) }, recent = emptyList(), showEnableRow = false, showRecentSection = false, collapsed = false,
    )

    @Composable private fun Rail(c: DockContent, actions: DockActions, bridge: DockInput.FocusBridge?) {
        Box(Modifier.fillMaxSize()) {
            DockRail(c, DockEdge.Bottom, "Home", expanded = true, focusRequest = 0, showNumbers = true, actions = actions, graphBridge = bridge) { _, s -> Box(Modifier.size(s)) }
        }
    }

    @Test fun INP_01_everyRailItemAndControlIsANodeOfTheSharedFocusGraphAndFocusReachesIt() {
        val bridge = RecordingBridge()
        rule.setContent { Rail(content("Alpha", "Bravo"), DockActions(), bridge) }
        rule.waitForIdle()
        assertEquals(setOf("dock.app:k0", "dock.app:k1", "dock.workspaces"), bridge.live.keys.toSet())
        rule.runOnUiThread { bridge.live.getValue("dock.app:k1").requestFocus() }
        rule.waitForIdle()
        assertTrue(bridge.focus.contains("dock.app:k1" to true))
    }

    @Test fun INP_01_aRailWithoutABridgeStaysOffTheGraph() {
        val bridge = RecordingBridge()
        DockInput.bridge = bridge // installed globally, but this rail (the desktop surface's) passes none
        rule.setContent { Rail(content("Alpha"), DockActions(), null) }
        rule.waitForIdle()
        assertTrue(bridge.live.isEmpty())
    }

    @Test fun INP_01_aPinThatLeavesTheRailLeavesTheGraph() {
        val bridge = RecordingBridge()
        var two by mutableStateOf(true)
        rule.setContent { Rail(if (two) content("Alpha", "Bravo") else content("Alpha"), DockActions(), bridge) }
        rule.waitForIdle()
        assertTrue("dock.app:k1" in bridge.live)
        rule.runOnUiThread { two = false }
        rule.waitForIdle()
        assertFalse("dock.app:k1" in bridge.live)
        assertTrue("dock.app:k0" in bridge.live)
    }

    @Test fun INP_07_tappingADockItemLaunchesItAndItsNumberIsItsPosition() {
        val launched = mutableListOf<Pair<String, Int>>()
        rule.setContent { Rail(content("Alpha", "Bravo", "Charlie"), DockActions(onLaunch = { item, i -> launched += item.label to i }), null) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Charlie").performClick()
        rule.onNodeWithContentDescription("Alpha").performClick()
        assertEquals(listOf("Charlie" to 2, "Alpha" to 0), launched)
        // the number keys name the same position the rail shows on the item
        assertEquals(2, DockCommandHandler.numberOf(LauncherCommand.Dock3)!! - 1)
        rule.onNodeWithText("3").assertExists()
    }

    @Test fun INP_07_theWorkspaceChipSwitchesWorkspaceBySwipeAndOpensThePanelOnTap() {
        val steps = mutableListOf<Int>(); var panels = 0
        rule.setContent { Rail(content("Alpha"), DockActions(onSwitchWorkspace = { steps += it }, onOpenPanel = { panels++ }), null) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Workspace Home").performTouchInput { swipeLeft() }
        rule.onNodeWithContentDescription("Workspace Home").performTouchInput { swipeRight() }
        rule.onNodeWithContentDescription("Workspace Home").performClick()
        assertEquals(listOf(1, -1), steps)
        assertEquals(1, panels)
    }

    @Test fun INP_07_theCollapsedBandExpandsOnTapSoFocusDockHasATouchPath() {
        var toggled = 0
        val c = content("Alpha").copy(collapsed = true)
        rule.setContent { Box(Modifier.fillMaxSize()) { DockRail(c, DockEdge.Bottom, "Home", expanded = false, focusRequest = 0, showNumbers = false, actions = DockActions(onToggleExpanded = { toggled++ }), graphBridge = null) { _, s -> Box(Modifier.size(s)) } } }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Dock, collapsed").performClick()
        assertEquals(1, toggled)
    }

    // ---- INP-20: every dock dialog installs the window hook

    private fun hooked(show: @Composable () -> Unit): Int {
        var installed = 0
        DockInput.dialogHook = { DisposableEffect(Unit) { installed++; onDispose { installed-- } } }
        rule.mainClock.autoAdvance = false // text fields blink their cursor forever: the clock is stepped by hand
        rule.setContent { show() }
        rule.mainClock.advanceTimeBy(500)
        return installed
    }

    @Test fun INP_20_theScheduleDialogInstallsTheDialogHook() {
        assertEquals(1, hooked { ScheduleDialog("Study", 0L, {}, {}) })
    }

    @Test fun INP_20_theAppPickerInstallsTheDialogHook() {
        val apps = listOf(PanelApp("k", "Alpha", "a.p", com.bos.sphere.core.launch.LaunchTarget("a.p", null, 0)))
        assertEquals(1, hooked { AppPicker("Pick", apps, emptySet(), 3, 1, {}, {}) })
    }

    /**
     * The group editor, the session dialog and the panel hold text fields; under the Compose test rule a text field inside a dialog window
     * never lets the rule idle (wave 24 found the cause: the rule's Robolectric idling keeps forcing a layout of the dialog's root, and each
     * measure republishes the field's layout result; it is not the cursor). They are rendered without the rule in the app module's
     * `DockDialogsW24Test`. Every dialog window of the module is also audited in source: each `Dialog(` call is followed at once by the
     * hook call, so a new dialog cannot ship without it.
     */
    @Test fun INP_20_everyDialogWindowInTheDockModuleStartsWithTheDialogHook() {
        val dir = java.io.File(System.getProperty("user.dir"), "src/main/kotlin/com/bos/sphere/feature/dock")
        val files = dir.listFiles { f -> f.extension == "kt" }!!.toList()
        var dialogs = 0
        for (f in files) {
            val lines = f.readLines()
            lines.forEachIndexed { i, l ->
                if (l.trim().startsWith("Dialog(onDismissRequest")) {
                    dialogs++
                    assertTrue("${f.name}:${i + 1} opens a dialog without DockDialogInput()", lines.getOrNull(i + 1)?.trim()?.startsWith("DockDialogInput()") == true)
                }
            }
        }
        assertEquals("the seven dialog windows of the dock (panel, group editor, app picker, session, schedule, rail help, edit dock)", 7, dialogs)
    }

    @Test fun INP_20_theRailsHelpDialogInstallsTheDialogHook() {
        var installed = 0
        DockInput.dialogHook = { DisposableEffect(Unit) { installed++; onDispose { installed-- } } }
        rule.setContent { Rail(content("Alpha"), DockActions(), null) }
        rule.waitForIdle()
        assertEquals(0, installed)
        // W25: the "i" button is gone; About is the last entry of the dock's long-press menu.
        rule.onNodeWithContentDescription("Dock").performTouchInput { longClick(androidx.compose.ui.geometry.Offset(width - 4f, height / 2f)) }
        rule.waitForIdle()
        rule.onNodeWithText("About the dock").performClick()
        rule.waitForIdle()
        assertEquals(1, installed)
    }
}
