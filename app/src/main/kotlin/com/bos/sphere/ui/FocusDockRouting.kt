package com.bos.sphere.ui

import com.bos.sphere.core.input.LauncherCommand

/**
 * INP-05: what "focus dock" does when an overlay hides the rail. The home's command handler calls [focusDock] and passes its own
 * overlay flags and the dock controller; the rule lives here so a test can drive it without composing the home. With an overlay
 * open (Radar, the drawer, the puck, Launchpad, Ground Control) the overlay is closed first, then the controller gets the
 * command, which keeps the request pending for the rail that appears next.
 */
internal object FocusDockRouting {
    fun focusDock(command: LauncherCommand, overlayOpen: Boolean, closeOverlays: () -> Unit, dock: (LauncherCommand) -> Boolean): Boolean {
        if (overlayOpen) closeOverlays()
        return dock(command)
    }
}
