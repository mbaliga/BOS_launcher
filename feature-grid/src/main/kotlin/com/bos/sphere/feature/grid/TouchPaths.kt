package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.input.LauncherCommand
import com.bos.sphere.core.input.MenuAction

/**
 * INP-07: for every launcher action, the way to reach it with a finger alone. Nothing in the launcher may be
 * reachable only by hover, right-click or a key. [InputAuditTest] enumerates every [LauncherCommand],
 * [MenuAction] and [CornerActionId] and fails when one has no entry here, so a new action cannot ship without a
 * touch path.
 */
object TouchPaths {
    /** Exhaustive on purpose: adding a [LauncherCommand] without a touch path does not compile. */
    fun forCommand(c: LauncherCommand): String = when (c) {
        LauncherCommand.OpenPalette -> "Pinch in on home, or the Radar corner"
        LauncherCommand.OpenDrawer -> "The All apps corner, or swipe up on home"
        LauncherCommand.OpenSettings -> "Long-press empty home, then Ground Control; or the Home settings tile"
        LauncherCommand.EditMode -> "Long-press an icon on the paged home, then drag"
        LauncherCommand.OpenLaunchpad -> "Swipe right from the left edge, or the unread rail"
        LauncherCommand.OpenPuck -> "The All apps corner when the drawer style is Wheel"
        LauncherCommand.ShortcutSheet -> "Ground Control, Home, Keyboard shortcuts"
        LauncherCommand.NextWorkspace, LauncherCommand.PreviousWorkspace -> "Swipe the workspace chip on the dock, or tap it and pick a workspace"
        LauncherCommand.FocusDock -> "Tap a dock item, or the collapsed dock band to open it"
        LauncherCommand.Dock1, LauncherCommand.Dock2, LauncherCommand.Dock3, LauncherCommand.Dock4, LauncherCommand.Dock5,
        LauncherCommand.Dock6, LauncherCommand.Dock7, LauncherCommand.Dock8, LauncherCommand.Dock9 -> "Tap the dock item"
        LauncherCommand.CornerTopLeft, LauncherCommand.CornerTopRight, LauncherCommand.CornerBottomLeft, LauncherCommand.CornerBottomRight -> "Tap the corner"
        LauncherCommand.OpenRecents -> "Swipe left from the right edge, or the Open recents corner or gesture"
        LauncherCommand.Dismiss -> "Tap the close button of Radar, the drawer or Ground Control, or the system Back gesture"
    }

    /**
     * The corner action whose tap runs the same opener as [c] (the home's key handler and the corner both go through
     * the one opener), or null when the touch way in is not a corner action. A test renders the real corner editor and
     * checks the action is offered there, so these paths are proven against the UI rather than only worded.
     */
    fun cornerActionFor(c: LauncherCommand): CornerActionId? = when (c) {
        LauncherCommand.OpenPalette -> CornerActionId.Search
        LauncherCommand.OpenDrawer -> CornerActionId.AllApps
        LauncherCommand.OpenSettings -> CornerActionId.LauncherSettings
        LauncherCommand.OpenRecents -> CornerActionId.OpenRecents
        else -> null
    }

    /** A headless test that renders the real surface for a touch path and taps or swipes it: module, test class, test function. */
    data class RenderedBy(val module: String, val testClass: String, val testName: String)

    private fun grid(name: String) = RenderedBy("feature-grid", "InputTouchPathsUiTest", name)
    private fun dock(name: String) = RenderedBy("feature-dock", "W4RadarInputDockTest", name)

    /**
     * INP-07: the commands whose touch path is proven by a test that renders the real surface and touches it (not a corner
     * action, which [cornerActionFor] ties to the corner editor). [InputAuditTest] checks that every test named here exists.
     */
    val renderedBy: Map<LauncherCommand, List<RenderedBy>> = buildMap {
        put(LauncherCommand.EditMode, listOf(grid("INP_07_a_long_press_on_a_paged_home_icon_then_a_drag_rearranges_the_home")))
        put(LauncherCommand.OpenLaunchpad, listOf(
            RenderedBy("feature-launchpad", "LaunchpadTouchPathTest", "INP_07_tapping_the_unread_rail_opens_launchpad"),
            RenderedBy("feature-launchpad", "LaunchpadInteractionTest", "STR_01_longDragFromTheEdgeOpens"),
        ))
        put(LauncherCommand.OpenPuck, listOf(grid("INP_07_the_all_apps_corner_opens_the_puck_for_the_wheel_style_and_the_puck_is_operable_by_touch")))
        put(LauncherCommand.ShortcutSheet, listOf(grid("INP_07_the_keyboard_shortcuts_row_in_home_settings_opens_the_shortcut_sheet")))
        for (c in listOf(LauncherCommand.NextWorkspace, LauncherCommand.PreviousWorkspace)) put(c, listOf(dock("INP_07_theWorkspaceChipSwitchesWorkspaceBySwipeAndOpensThePanelOnTap")))
        put(LauncherCommand.FocusDock, listOf(dock("INP_07_theCollapsedBandExpandsOnTapSoFocusDockHasATouchPath"), dock("INP_07_tappingADockItemLaunchesItAndItsNumberIsItsPosition")))
        for (c in LauncherCommand.entries.filter { it.name.startsWith("Dock") && it.name.length == 5 }) put(c, listOf(dock("INP_07_tappingADockItemLaunchesItAndItsNumberIsItsPosition")))
        for (c in listOf(LauncherCommand.CornerTopLeft, LauncherCommand.CornerTopRight, LauncherCommand.CornerBottomLeft, LauncherCommand.CornerBottomRight)) put(c, listOf(grid("INP_07_tapping_each_corner_runs_what_it_holds")))
        put(LauncherCommand.Dismiss, listOf(
            grid("INP_07_the_close_button_and_back_dismiss_radar"),
            grid("INP_07_the_close_button_and_back_dismiss_the_drawer"),
            grid("INP_07_back_dismisses_ground_control"),
        ))
    }

    /**
     * Commands whose touch path no headless test renders. Listed so the audit states what it does not prove; a new command lands
     * here, in [renderedBy] or in [cornerActionFor], never silently. Empty: every touch path of a [LauncherCommand] is now rendered
     * and touched by a test. What still needs a device is the system's own Back gesture and edge gestures (the platform delivers
     * them), which no test can send; the surfaces behind them are tested through their back handler.
     */
    val describedOnly: Set<LauncherCommand> = LauncherCommand.entries.filter { cornerActionFor(it) == null && it !in renderedBy }.toSet()

    /** Every menu entry is a row in the long-press menu, which is the touch way in. */
    fun forMenu(a: MenuAction): String = when (a) {
        MenuAction.Open, MenuAction.OpenOnDisplay, MenuAction.OpenBeside, MenuAction.AppInfo, MenuAction.AddToDock,
        MenuAction.AddToWorkspace, MenuAction.Uninstall, MenuAction.Rename, MenuAction.Remove, MenuAction.MoveToFolder ->
            "Long-press the item, then choose ${a.label}"
    }

    /** The home-only rows of the app menu are rows of the same long-press menu. */
    fun forHomeMenu(a: com.bos.sphere.core.input.HomeMenuAction): String = "Long-press the app, then choose ${a.label}"

    /** Corner actions run by tapping the corner they are assigned to; the corner editor (long-press a corner) offers each of them. */
    fun forCornerAction(a: CornerActionId): String = when (a) {
        CornerActionId.Music, CornerActionId.Camera, CornerActionId.Phone, CornerActionId.Messages, CornerActionId.Browser,
        CornerActionId.Email, CornerActionId.Maps, CornerActionId.Calendar, CornerActionId.Clock, CornerActionId.Gallery,
        CornerActionId.Contacts, CornerActionId.Calculator, CornerActionId.Assistant, CornerActionId.Flashlight,
        CornerActionId.RingerCycle, CornerActionId.RingerNormal, CornerActionId.RingerVibrate, CornerActionId.RingerSilent,
        CornerActionId.DoNotDisturb, CornerActionId.SystemSettings -> "Assign it to a corner or gesture (long-press a corner), then tap it"
        CornerActionId.Search, CornerActionId.AllApps, CornerActionId.Notifications, CornerActionId.LauncherSettings,
        CornerActionId.GridDenser, CornerActionId.GridSparser, CornerActionId.OpenLaunchpad, CornerActionId.OpenRecents -> "Tap the corner it is assigned to, or its bound gesture"
    }
}
