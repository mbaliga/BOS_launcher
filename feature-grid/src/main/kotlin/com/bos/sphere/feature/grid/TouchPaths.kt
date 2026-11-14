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
        LauncherCommand.NextWorkspace, LauncherCommand.PreviousWorkspace -> "Swipe between home pages"
        LauncherCommand.FocusDock -> "Tap a dock item"
        LauncherCommand.Dock1, LauncherCommand.Dock2, LauncherCommand.Dock3, LauncherCommand.Dock4, LauncherCommand.Dock5,
        LauncherCommand.Dock6, LauncherCommand.Dock7, LauncherCommand.Dock8, LauncherCommand.Dock9 -> "Tap the dock item"
        LauncherCommand.CornerTopLeft, LauncherCommand.CornerTopRight, LauncherCommand.CornerBottomLeft, LauncherCommand.CornerBottomRight -> "Tap the corner"
        LauncherCommand.Dismiss -> "Tap outside the surface, or the system Back gesture"
    }

    /** Every menu entry is a row in the long-press menu, which is the touch way in. */
    fun forMenu(a: MenuAction): String = when (a) {
        MenuAction.Open, MenuAction.OpenOnDisplay, MenuAction.OpenBeside, MenuAction.AppInfo, MenuAction.AddToDock,
        MenuAction.AddToWorkspace, MenuAction.Uninstall, MenuAction.Rename, MenuAction.Remove, MenuAction.MoveToFolder,
        MenuAction.Tags, MenuAction.AddToHome, MenuAction.IconColour, MenuAction.Hide, MenuAction.Unhide, MenuAction.HomeSettings ->
            "Long-press the item, then choose ${a.label}"
    }

    /** Corner actions run by tapping the corner they are assigned to; the corner editor (long-press a corner) offers each of them. */
    fun forCornerAction(a: CornerActionId): String = when (a) {
        CornerActionId.Music, CornerActionId.Camera, CornerActionId.Phone, CornerActionId.Messages, CornerActionId.Browser,
        CornerActionId.Email, CornerActionId.Maps, CornerActionId.Calendar, CornerActionId.Clock, CornerActionId.Gallery,
        CornerActionId.Contacts, CornerActionId.Calculator, CornerActionId.Assistant, CornerActionId.Flashlight,
        CornerActionId.RingerCycle, CornerActionId.RingerNormal, CornerActionId.RingerVibrate, CornerActionId.RingerSilent,
        CornerActionId.DoNotDisturb, CornerActionId.SystemSettings -> "Assign it to a corner or gesture (long-press a corner), then tap it"
        CornerActionId.Search, CornerActionId.AllApps, CornerActionId.Notifications, CornerActionId.LauncherSettings,
        CornerActionId.GridDenser, CornerActionId.GridSparser -> "Tap the corner it is assigned to, or its bound gesture"
    }
}
