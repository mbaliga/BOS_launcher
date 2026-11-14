package com.bos.sphere.feature.dock

import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.input.LauncherCommand
import com.bos.sphere.core.input.ShortcutRegistry
import com.bos.sphere.core.launch.AppGroup
import com.bos.sphere.core.launch.GroupLayout
import com.bos.sphere.core.launch.LaunchRequest
import com.bos.sphere.core.launch.LaunchTarget
import com.bos.sphere.core.launch.Session
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/** Wave 2: the wiring and pure rules behind DCK-05, DCK-07, WSP-05, WSP-06, WSP-07 and WSP-11. */
class DockExtTest {
    private val registry = ShortcutRegistry()

    private fun press(c: DockController, cmd: LauncherCommand): Boolean {
        val chord = registry.binding(cmd) ?: return false
        return c.onKey(registry, chord.keyCode, chord.ctrl, chord.alt, chord.shift, chord.meta)
    }

    // ---- DCK-05

    @Test fun DCK_05_everyRegisteredNumberShortcutLaunchesItsItemAndFocusShortcutFocusesTheDock() {
        val c = DockController()
        val launched = mutableListOf<Int>()
        c.launchNth = { launched += it; true }
        val dockCommands = LauncherCommand.entries.filter { DockCommandHandler.numberOf(it) != null }
        assertEquals(9, dockCommands.size)
        for (cmd in dockCommands) assertTrue("$cmd has no default shortcut", press(c, cmd))
        assertEquals((0..8).toList(), launched)
        val before = c.focusRequest
        assertTrue(press(c, LauncherCommand.FocusDock))
        assertEquals(before + 1, c.focusRequest)
    }

    @Test fun DCK_05_numberShortcutWithNoItemIsNotConsumedSoTheKeyIsNotSwallowed() {
        val c = DockController()
        c.launchNth = { false }
        assertFalse(c.handle(LauncherCommand.Dock7))
        assertFalse(c.handle(LauncherCommand.OpenSettings)) // not the dock's command
    }

    @Test fun DCK_05_workspaceShortcutsStepWithoutADockOnScreen() {
        val c = DockController()
        assertFalse(c.handle(LauncherCommand.NextWorkspace)) // nothing bound yet: left for others
        val steps = mutableListOf<Int>()
        c.bindWorkspaceStep { steps += it; true }
        assertTrue(c.handle(LauncherCommand.NextWorkspace)); assertTrue(c.handle(LauncherCommand.PreviousWorkspace))
        assertEquals(listOf(1, -1), steps)
    }

    // ---- DCK-07

    @Test fun DCK_07_eachDisplayHasItsOwnDockStateAndTheyShareTheSamePins() {
        var pins = listOf(DockItemRef.app("a"))
        val hub = DockHub({ pins }, { pins = it })
        val phone = hub.forDisplay(0); val desk = hub.forDisplay(7)
        phone.expanded = true; desk.focusIndex = 2
        assertFalse(desk.expanded); assertEquals(-1, phone.focusIndex)           // own instances
        desk.pin(DockItemRef.app("b"))
        assertEquals(listOf(DockItemRef.app("a"), DockItemRef.app("b")), phone.sharedPins) // same pins
        phone.reorder(0, 1)
        assertEquals(desk.sharedPins, phone.sharedPins)
        hub.release(7)
        assertEquals(setOf(0), hub.displays()); assertEquals(2, phone.sharedPins.size) // pins survive the display going away
    }

    // ---- WSP-05

    private fun group(n: String) = AppGroup(n, listOf(LaunchRequest(LaunchTarget("a.p", null, 0)), LaunchRequest(LaunchTarget("b.p", null, 0))), GroupLayout.Tile, "g$n")

    @Test fun WSP_05_groupShelfShowsSavedGroupsOnlyWhileOnAndCapsTheCount() {
        val gs = (1..9).map { group("G$it") }
        assertEquals(GroupShelfModel.MAX, GroupShelfModel.visible(gs, true).size)
        assertTrue(GroupShelfModel.visible(gs, false).isEmpty())
        assertTrue(GroupShelfModel.visible(emptyList(), true).isEmpty())
    }

    @Test fun WSP_05_aGroupDockItemAlwaysHasAVisibleTileNotABlankOne() {
        assertEquals("WL", DockInitials.of("Work layout"))
        assertEquals("Ed", DockInitials.of("edit"))
        assertEquals("?", DockInitials.of("  "))
    }

    @Test fun WSP_05_groupIsAPaletteResultThatRunsThroughTheExplicitRunIntent() = runBlocking {
        val ws = Workspace("w", "Work")
        val state = DockWorkspaceState(workspaces = listOf(Workspace("default", "Home"), ws), groups = listOf(group("Study")))
        val provider = DockSearchProvider({ state }, { t -> "run:${t.kind}:${t.id}" })
        val hits = provider.search("stu", 8)
        val hit = hits.single()
        assertEquals("Study", hit.title)
        assertEquals("run:Group:gStudy", hit.intentUri)
        assertTrue(provider.search("zzz", 8).isEmpty())
    }

    // ---- WSP-06

    @Test fun WSP_06_theItemUnderThePressIsTheAnimationSourceAndBigContainersAreIgnored() {
        val icon = com.bos.sphere.core.input.FRect(100f, 200f, 220f, 320f)
        val page = com.bos.sphere.core.input.FRect(0f, 0f, 1080f, 2000f)
        val other = com.bos.sphere.core.input.FRect(400f, 200f, 520f, 320f)
        assertEquals(icon, SourcePick.itemUnder(150f, 250f, listOf(page, other, icon)))
        assertNull(SourcePick.itemUnder(700f, 900f, listOf(page, other, icon)))        // empty space: only the page contains it
        assertEquals(icon, SourcePick.itemUnder(100f, 200f, listOf(icon)))             // edges count
    }

    @Test fun WSP_06_lastTouchUsesTheRegisteredItemRectangleElseASquareAroundThePress() {
        val icon = com.bos.sphere.core.input.FRect(100f, 200f, 220f, 320f)
        val withItem = LastTouch(origin = { 10 to 20 }, itemRects = { listOf(icon) })
        withItem.set(androidx.compose.ui.geometry.Offset(150f, 250f))
        assertEquals(listOf(110, 220, 230, 340), withItem.sourceLtrb()!!.toList())
        val bare = LastTouch(origin = { 0 to 0 })
        bare.set(androidx.compose.ui.geometry.Offset(500f, 500f))
        assertEquals(listOf(452, 452, 548, 548), bare.sourceLtrb(48)!!.toList())
        assertNull(LastTouch().sourceLtrb())
    }

    // ---- WSP-07

    private class WidgetHooks : WorkspaceHooks {
        var widgets: List<String> = WorkspaceWidgets.encode(emptyList()); val applied = mutableListOf<List<String>>()
        override fun captureWidgets() = widgets
        override fun applyWidgets(widgets: List<String>) { applied += widgets; this.widgets = widgets }
    }

    @Test fun WSP_07_eachWorkspaceKeepsItsOwnWidgetsAndTheyComeBackOnSwitch() {
        val h = WidgetHooks()
        val m = WorkspaceManager(InMemoryWorkspaceStore(), { h })
        val work = m.create("Work")
        h.widgets = WorkspaceWidgets.encode(listOf(WorkspaceWidgets.Slot.Left(41), WorkspaceWidgets.Slot.Corner("TopLeft", 42, "Weather")))
        m.switchTo(work)                       // leaving Home saves its two widgets
        assertTrue(h.applied.isEmpty())        // Work has no snapshot yet: the home is left as it is
        h.widgets = WorkspaceWidgets.encode(listOf(WorkspaceWidgets.Slot.Corner("BottomRight", 7, "Notes")))
        m.switchTo(DockWorkspaceState.DEFAULT_ID)
        assertEquals(2, WorkspaceWidgets.decode(h.applied.single()).size)
        m.switchTo(work)
        val back = WorkspaceWidgets.decode(h.applied.last()).single() as WorkspaceWidgets.Slot.Corner
        assertEquals("Notes", back.label); assertEquals(7, back.appWidgetId)
    }

    @Test fun WSP_07_aSnapshotWithNoWidgetsIsToldApartFromNothingSaved() {
        assertTrue(WorkspaceWidgets.isSnapshot(WorkspaceWidgets.encode(emptyList())))
        assertFalse(WorkspaceWidgets.isSnapshot(emptyList()))
        assertEquals(listOf(WorkspaceWidgets.Slot.Corner("TopLeft", 1, "A:B")), WorkspaceWidgets.decode(WorkspaceWidgets.encode(listOf(WorkspaceWidgets.Slot.Corner("TopLeft", 1, "A:B")))))
    }

    @Test fun WSP_07_pinnedRadarActionsBelongToTheActiveWorkspaceAndShowBeforeTyping() = runBlocking {
        val home = Workspace("default", "Home", paletteActions = listOf("OpenSettings", "NextWorkspace"))
        val work = Workspace("w", "Work", paletteActions = listOf("OpenDrawer"))
        var state = DockWorkspaceState(workspaces = listOf(home, work), activeId = "default")
        val p = DockSearchProvider({ state }, { t -> "run:${t.id}" })
        assertEquals(listOf("Open launcher settings", "Switch to next workspace"), p.search("", 8).map { it.title })
        assertEquals(CornerActionId.LauncherSettings, p.search("", 8).first().actionId)
        assertEquals("run:@next", p.search("", 8).last().intentUri)
        assertEquals(listOf("Open launcher settings"), p.search("sett", 8).map { it.title })
        state = state.copy(activeId = "w")
        assertEquals(listOf("Open all apps"), p.search("", 8).map { it.title })
    }

    @Test fun WSP_07_switchToAnotherWorkspaceIsAPaletteResultOnlyForOthers() = runBlocking {
        val state = DockWorkspaceState(workspaces = listOf(Workspace("default", "Home"), Workspace("w", "Work")), sessions = listOf(Session("s1", "Morning", emptyList(), GroupLayout.Tile, 0L)))
        val p = DockSearchProvider({ state }, { t -> "run:${t.kind}:${t.id}" })
        assertEquals(listOf("Switch to Work"), p.search("wor", 8).map { it.title })
        assertEquals("run:Workspace:w", p.search("wor", 8).single().intentUri)
        assertEquals("run:Session:s1", p.search("morn", 8).single().intentUri)
    }

    @Test fun WSP_07_widgetsActionsAndTheGroupShelfSurviveTheStateFile() {
        val ws = Workspace("w", "Work", widgets = WorkspaceWidgets.encode(listOf(WorkspaceWidgets.Slot.Left(5))), paletteActions = listOf("OpenDrawer"))
        val s = DockWorkspaceState(dock = DockSettings(groupShelf = false), workspaces = listOf(Workspace("default", "Home"), ws), activeId = "w")
        val back = (WorkspaceJson.decode(WorkspaceJson.encode(s)) as WorkspaceJson.Decoded.Ok).state
        assertFalse(back.dock.groupShelf)
        assertEquals(ws.widgets, back.workspaces.last().widgets); assertEquals(ws.paletteActions, back.workspaces.last().paletteActions)
        // an older file without the field keeps the shelf on
        val older = WorkspaceJson.encode(s).replace(",\"groupShelf\":false", "")
        assertTrue((WorkspaceJson.decode(older) as WorkspaceJson.Decoded.Ok).state.dock.groupShelf)
    }

    // ---- WSP-11

    @Test fun WSP_11_nextOccurrenceIsTodayIfAheadElseTomorrowInTheUsersZone() {
        val zone = TimeZone.getTimeZone("Europe/Berlin")
        val cal = java.util.Calendar.getInstance(zone).apply { clear(); set(2026, java.util.Calendar.MARCH, 10, 9, 30, 15) }
        val now = cal.timeInMillis
        val ahead = ScheduleTime.nextOccurrence(now, 18, 0, zone)
        assertEquals(now + (8 * 60 + 29) * 60_000L + 45_000L, ahead)
        val past = ScheduleTime.nextOccurrence(now, 9, 30, zone) // 9:30:00 already passed by 15 s
        assertEquals(now - 15_000L + 24 * 3_600_000L, past)
        assertEquals("07:05", ScheduleTime.clock(7, 5)); assertEquals("23:59", ScheduleTime.clock(99, 99))
        assertEquals(now + 3_600_000L, ScheduleTime.inMinutes(now, 60)); assertEquals(now + 60_000L, ScheduleTime.inMinutes(now, 0))
        assertEquals(23, ScheduleTime.wrap(0, -1, 24)); assertEquals(0, ScheduleTime.wrap(23, 1, 24)); assertEquals(55, ScheduleTime.wrap(0, -5, 60))
    }

    @Test fun WSP_11_aScheduleCanTargetAnAppOrAGroupAndRoundTripsThroughTheStateFile() {
        val s = DockWorkspaceState(schedules = listOf(
            ScheduledLaunch("a", "Open Maps", 1_000L, RunTarget(RunKind.App, "pkg/.Main#0")),
            ScheduledLaunch("b", "Open Study", 2_000L, RunTarget(RunKind.Group, "gStudy")),
        ))
        val back = (WorkspaceJson.decode(WorkspaceJson.encode(s)) as WorkspaceJson.Decoded.Ok).state
        assertEquals(s.schedules, back.schedules)
        assertNotNull(AppKeys.target("pkg/.Main#0"))
    }

    // ---- EXT-07

    @Test fun EXT_07_fromEitherDisplayTheOtherOneIsOfferedByName() {
        val caps = com.bos.sphere.core.launch.LaunchCaps(setOf(0, 2), setOf(0, 2), boundsHonoured = false, adjacentSupported = null)
        val names = mapOf(0 to "Phone", 2 to "Desk monitor")
        assertEquals(listOf("Open on Desk monitor"), LaunchMenus.choices(caps, 0, names).map { it.label })   // phone home to the desktop
        assertEquals(listOf("Open on Phone"), LaunchMenus.choices(caps, 2, names).map { it.label })          // desktop surface to the phone
        val target = LaunchTarget("a.p", "a.p.Main", 0)
        val req = LaunchMenus.request(LaunchChoice.OnDisplay(2, "Desk monitor"), target, 0, { 1080 to 1920 })
        assertEquals(2, req.displayId)
    }

    @Test fun EXT_07_aDisplayThatCannotHostTasksIsNotOffered() {
        val caps = com.bos.sphere.core.launch.LaunchCaps(setOf(0, 3), setOf(0), boundsHonoured = false, adjacentSupported = null)
        assertTrue(LaunchMenus.choices(caps, 0, mapOf(3 to "Cast")).isEmpty())
    }
}
