package com.bos.sphere.feature.dock

/**
 * INP-05: what Dock 1 to 9 launch while no rail is on screen (Radar, the drawer, Ground Control or the puck is open, or
 * the dock setting is off, the phone default). The numbers then count the pinned items that can be shown now, in pin
 * order; the rail's recent section is only numbered while the rail shows it, because recents come from usage data the
 * rail reads while it is composed.
 */
object DockPinLaunch {
    const val MAX = 9

    /** The pins numbers 1 to 9 stand for: those that [available] says exist now (an uninstalled app is skipped, as the rail skips it). */
    fun numbered(pins: List<DockItemRef>, available: (DockItemRef) -> Boolean): List<DockItemRef> = pins.filter(available).take(MAX)

    /** Launches the pin at zero-based [index]; false (key not consumed) when there is none. */
    fun launchNth(index: Int, pins: List<DockItemRef>, available: (DockItemRef) -> Boolean, launch: (DockItemRef) -> Unit): Boolean {
        val ref = numbered(pins, available).getOrNull(index) ?: return false
        launch(ref)
        return true
    }
}
