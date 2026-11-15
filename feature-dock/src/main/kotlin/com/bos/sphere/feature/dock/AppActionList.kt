package com.bos.sphere.feature.dock

import android.graphics.Rect
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.input.MenuAction
import com.bos.sphere.core.launch.Bounds

class AppAction(val label: String, val run: () -> Unit)

/**
 * The extra entries an app's action list gets from this stream: "Open on <display name>", "Open beside", bounds presets
 * (each only where the device and the Lab spikes allow it), and pinning to the dock (WSP-02 to WSP-04, DCK-01).
 */
object AppActionList {
    fun build(launcher: AppLauncher, manager: WorkspaceManager, app: AppEntry, source: Rect? = null, onDone: () -> Unit = {}): List<AppAction> {
        val caps = launcher.caps()
        val current = launcher.defaultDisplayId()
        val target = launcher.targetOf(app)
        val src = source?.let { Bounds(it.left, it.top, it.right, it.bottom) }
        val out = mutableListOf<AppAction>()
        LaunchMenus.choices(caps, current, launcher.displayNames()).forEach { c ->
            out += AppAction(L10n.tr(c.label)) {
                SessionUse.mark(app.key)
                launcher.launch(LaunchMenus.request(c, target, current, launcher::displaySize, src)); onDone()
            }
        }
        val ref = DockItemRef.app(app.key)
        val pinned = ref in manager.current.active.dockPins
        out += AppAction(L10n.tr(if (pinned) "Remove from dock" else "Add to dock")) {
            manager.setPins(if (pinned) DockPins.remove(manager.current.active.dockPins, ref) else DockPins.add(manager.current.active.dockPins, ref)); onDone()
        }
        return out
    }

    /**
     * PAL-08: the launch choice a shared menu action means when it can run without another tap: "Open beside" is the one
     * beside choice; "Open on display" is the one other display when there is exactly one (with several the person must
     * pick which, so this returns null and the host shows its list).
     */
    fun directChoice(action: MenuAction, choices: List<LaunchChoice>): LaunchChoice? = when (action) {
        MenuAction.OpenBeside -> choices.firstOrNull { it == LaunchChoice.Beside }
        MenuAction.OpenOnDisplay -> choices.filterIsInstance<LaunchChoice.OnDisplay>().singleOrNull()
        else -> null
    }

    /** Runs [action] for [app] straight from Radar's action list (PAL-08); false when it needs the host's own sheet. */
    fun runDirect(action: MenuAction, launcher: AppLauncher, manager: WorkspaceManager, app: AppEntry, source: Rect? = null): Boolean {
        if (action == MenuAction.AddToDock) {
            val ref = DockItemRef.app(app.key)
            val pins = manager.current.active.dockPins
            manager.setPins(if (ref in pins) DockPins.remove(pins, ref) else DockPins.add(pins, ref))
            return true
        }
        val current = launcher.defaultDisplayId()
        val choice = directChoice(action, LaunchMenus.choices(launcher.caps(), current, launcher.displayNames())) ?: return false
        SessionUse.mark(app.key)
        launcher.launch(LaunchMenus.request(choice, launcher.targetOf(app), current, launcher::displaySize, source?.let { Bounds(it.left, it.top, it.right, it.bottom) }))
        return true
    }
}
