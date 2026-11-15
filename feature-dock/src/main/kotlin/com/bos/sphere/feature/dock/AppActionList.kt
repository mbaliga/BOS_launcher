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

    /** One row of Radar's chooser (PAL-08): a display to open on, or a workspace to add the app to ([already] when it holds the app). */
    sealed interface PickerEntry {
        val label: String
        data class Display(val choice: LaunchChoice.OnDisplay) : PickerEntry { override val label get() = L10n.tr("Open on %s", choice.displayName) }
        data class Workspace(val id: String, val name: String, val already: Boolean) : PickerEntry {
            override val label get() = if (already) L10n.tr("%s (added)", name) else name
        }
    }

    /**
     * PAL-08: what an action that needs a pick offers, so Radar can show its own small chooser instead of sending the person
     * to the home's app sheet. "Open on display" lists every other display that can host the app, "Add to workspace" lists
     * the workspaces (one that already holds [ref] says so and picking it changes nothing); any other action has none (null).
     */
    fun pickerEntries(action: MenuAction, choices: List<LaunchChoice>, workspaces: List<com.bos.sphere.feature.dock.Workspace>, ref: DockItemRef): List<PickerEntry>? = when (action) {
        MenuAction.OpenOnDisplay -> choices.filterIsInstance<LaunchChoice.OnDisplay>().map(PickerEntry::Display).takeIf { it.isNotEmpty() }
        MenuAction.AddToWorkspace -> workspaces.map { PickerEntry.Workspace(it.id, it.name, ref in it.dockPins) }.takeIf { it.isNotEmpty() }
        else -> null
    }

    /** [pickerEntries] bound to the launcher and the workspace manager: each entry's action does the work. */
    fun pickerChoices(action: MenuAction, launcher: AppLauncher, manager: WorkspaceManager, app: AppEntry, source: Rect? = null): List<AppAction>? {
        val current = launcher.defaultDisplayId()
        val src = source?.let { Bounds(it.left, it.top, it.right, it.bottom) }
        val ref = DockItemRef.app(app.key)
        val choices = if (action == MenuAction.OpenOnDisplay) LaunchMenus.choices(launcher.caps(), current, launcher.displayNames()) else emptyList()
        return pickerEntries(action, choices, manager.current.workspaces, ref)?.map { e ->
            AppAction(e.label) {
                when (e) {
                    is PickerEntry.Display -> {
                        SessionUse.mark(app.key)
                        launcher.launch(LaunchMenus.request(e.choice, launcher.targetOf(app), current, launcher::displaySize, src))
                    }
                    is PickerEntry.Workspace -> if (!e.already) manager.addPinTo(e.id, ref)
                }
            }
        }
    }
}
