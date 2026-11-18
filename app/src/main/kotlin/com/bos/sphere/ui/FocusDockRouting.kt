package com.bos.sphere.ui

import com.bos.sphere.core.data.ChromeElement
import com.bos.sphere.core.data.ChromeSettings
import com.bos.sphere.core.data.HomeChromeRules
import com.bos.sphere.core.data.HomeLanding
import com.bos.sphere.core.input.LauncherCommand

/**
 * INP-05: what "focus dock" does when an overlay hides the rail. The home's command handler calls [focusDock] and passes its own
 * overlay flags and the dock controller; the rule lives here so a test can drive it without composing the home. With an overlay
 * open (Radar, the drawer, the puck, Launchpad, Ground Control) the overlay is closed first, then the controller gets the
 * command, which keeps the request pending for the rail that appears next.
 *
 * Wave 24 (the dock stays optional): when no rail can appear once the overlays close (the dock is off, or the landing draws no
 * dock), the overlay is left alone and [onNoDock] says why instead. Before, the command closed Radar or the drawer for a rail
 * that never came (on the default Glance landing too).
 */
internal object FocusDockRouting {
    /** Why the home would show no rail after its overlays close. */
    enum class NoDock(val message: String) {
        Off("The dock is off. Turn it on in Ground Control, Desktop & dock."),
        Landing("This home landing shows no dock. On Glance, set Ground Control, Home, Right side to Dock rail."),
    }

    /** Null when a rail shows on the home once nothing covers it (the same rules `DockLayer` and `DockHost` apply). */
    fun noDock(landing: HomeLanding, chrome: ChromeSettings, dockEnabled: Boolean): NoDock? = when {
        HomeChromeRules.forcesDock(landing, chrome) -> null
        !HomeChromeRules.shows(landing, ChromeElement.Dock, chrome) -> NoDock.Landing
        !dockEnabled -> NoDock.Off
        else -> null
    }

    fun focusDock(
        command: LauncherCommand,
        overlayOpen: Boolean,
        closeOverlays: () -> Unit,
        dock: (LauncherCommand) -> Boolean,
        noDock: NoDock? = null,
        onNoDock: (NoDock) -> Unit = {},
    ): Boolean {
        if (noDock != null) { onNoDock(noDock); return true }
        if (overlayOpen) closeOverlays()
        return dock(command)
    }
}
