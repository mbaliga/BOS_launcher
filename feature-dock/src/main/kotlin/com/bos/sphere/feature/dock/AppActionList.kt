package com.bos.sphere.feature.dock

import android.graphics.Rect
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.L10n
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
}
