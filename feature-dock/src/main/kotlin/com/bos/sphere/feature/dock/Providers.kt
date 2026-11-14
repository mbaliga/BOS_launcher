package com.bos.sphere.feature.dock

import com.bos.sphere.core.input.ContextMenus
import com.bos.sphere.core.input.MenuAction
import com.bos.sphere.core.input.MenuContext
import com.bos.sphere.core.launch.AppGroup
import com.bos.sphere.core.launch.Bounds
import com.bos.sphere.core.launch.BoundsPreset
import com.bos.sphere.core.launch.LaunchCaps
import com.bos.sphere.core.launch.LaunchRequest
import com.bos.sphere.core.launch.LaunchResolver
import com.bos.sphere.core.launch.LaunchTarget
import com.bos.sphere.core.launch.Placement
import com.bos.sphere.core.launch.Session

/** A result the palette can show. The search stream renders these; running one does the thing. */
data class PaletteHit(val id: String, val title: String, val subtitle: String, val run: () -> Unit)

/** The hook the palette calls for extra result sources (WSP-05: a group is a palette result). */
fun interface PaletteProvider { fun results(query: String): List<PaletteHit> }

object PaletteMatch {
    /** Prefix of the whole name or of any word, case-insensitive; an empty query matches nothing. */
    fun matches(query: String, name: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return false
        val n = name.lowercase()
        return n.startsWith(q) || n.split(' ', '-', '_').any { it.startsWith(q) } || q.length >= 3 && n.contains(q)
    }
}

class GroupPaletteProvider(private val groups: () -> List<AppGroup>, private val launch: (AppGroup) -> Unit) : PaletteProvider {
    override fun results(query: String): List<PaletteHit> =
        groups().filter { PaletteMatch.matches(query, it.name) }.map { g ->
            PaletteHit("group:${g.id}", g.name, "App group, ${g.members.size} apps") { launch(g) }
        }
}

/** "Switch to <workspace>" and "Restore <session>" results (WSP-07 palette route, WSP-09). */
class WorkspacePaletteProvider(
    private val state: () -> DockWorkspaceState,
    private val switchTo: (String) -> Unit,
    private val restore: (Session) -> Unit,
) : PaletteProvider {
    override fun results(query: String): List<PaletteHit> {
        val s = state()
        val ws = s.workspaces.filter { it.id != s.activeId && (PaletteMatch.matches(query, it.name) || PaletteMatch.matches(query, "workspace")) && query.isNotBlank() }
            .map { w -> PaletteHit("workspace:${w.id}", "Switch to ${w.name}", "Workspace") { switchTo(w.id) } }
        val ss = s.sessions.filter { PaletteMatch.matches(query, it.name) }
            .map { x -> PaletteHit("session:${x.id}", "Restore ${x.name}", "Session, reopens apps only") { restore(x) } }
        return ws + ss
    }
}

/** What an item's action list offers for launch control (WSP-02 to WSP-04), derived from the one shared menu model. */
sealed interface LaunchChoice {
    val label: String
    data object Plain : LaunchChoice { override val label = "Open" }
    data class OnDisplay(val displayId: Int, val displayName: String) : LaunchChoice { override val label get() = "Open on $displayName" }
    data object Beside : LaunchChoice { override val label = "Open beside" }
    data class WithBounds(val preset: BoundsPreset) : LaunchChoice {
        override val label get() = when (preset) {
            BoundsPreset.LeftHalf -> "Open in left half"; BoundsPreset.RightHalf -> "Open in right half"
            BoundsPreset.Centred -> "Open centred"; BoundsPreset.Full -> "Open full size"
        }
    }
}

object LaunchMenus {
    /** The [MenuContext] with its display and adjacency fields filled from the resolver and the Lab spike results. */
    fun context(caps: LaunchCaps, currentDisplay: Int, inDock: Boolean = false, onHome: Boolean = false, canUninstall: Boolean = true): MenuContext =
        MenuContext(
            isApp = true, canUninstall = canUninstall,
            otherDisplays = LaunchResolver.offeredDisplays(caps, currentDisplay).size,
            adjacentSupported = LaunchResolver.offersAdjacent(caps),
            onHome = onHome, inDock = inDock,
        )

    /**
     * Launch entries for an app's action list. "Open on <display name>" appears once per display that can host tasks,
     * "Open beside" only where SPK-02 proved it, and bounds presets only where bounds are honoured.
     */
    fun choices(caps: LaunchCaps, currentDisplay: Int, displayNames: Map<Int, String>): List<LaunchChoice> {
        val menu = ContextMenus.forItem(context(caps, currentDisplay))
        val out = mutableListOf<LaunchChoice>()
        for (e in menu) when (e.action) {
            MenuAction.OpenOnDisplay -> LaunchResolver.offeredDisplays(caps, currentDisplay).forEach { out += LaunchChoice.OnDisplay(it, displayNames[it] ?: "display $it") }
            MenuAction.OpenBeside -> out += LaunchChoice.Beside
            else -> Unit
        }
        if (caps.boundsHonoured) BoundsPreset.entries.forEach { out += LaunchChoice.WithBounds(it) }
        return out
    }

    fun request(choice: LaunchChoice, target: LaunchTarget, currentDisplay: Int, size: (Int) -> Pair<Int, Int>, source: Bounds? = null): LaunchRequest = when (choice) {
        LaunchChoice.Plain -> LaunchRequest(target, source = source)
        is LaunchChoice.OnDisplay -> LaunchRequest(target, displayId = choice.displayId, source = source)
        LaunchChoice.Beside -> LaunchRequest(target, placement = Placement.Adjacent, source = source)
        is LaunchChoice.WithBounds -> { val (w, h) = size(currentDisplay); LaunchRequest(target, bounds = choice.preset.on(w, h), source = source) }
    }
}

/** Automation intents (WSP-12): the actions other tools may send, and how they map to a [RunTarget]. */
object AutomationIntents {
    const val ACTION_SWITCH_WORKSPACE = "com.bos.sphere.action.SWITCH_WORKSPACE"
    const val ACTION_RESTORE_SESSION = "com.bos.sphere.action.RESTORE_SESSION"
    const val ACTION_LAUNCH_GROUP = "com.bos.sphere.action.LAUNCH_GROUP"
    const val ACTION_RUN = "com.bos.sphere.action.RUN"
    const val EXTRA_NAME = "com.bos.sphere.extra.NAME"
    const val EXTRA_KIND = "com.bos.sphere.extra.KIND"
    const val EXTRA_ID = "com.bos.sphere.extra.ID"

    fun parse(action: String?, name: String?, kind: String?, id: String?): RunTarget? {
        val key = (id ?: name)?.takeIf { it.isNotBlank() } ?: return null
        return when (action) {
            ACTION_SWITCH_WORKSPACE -> RunTarget(RunKind.Workspace, key)
            ACTION_RESTORE_SESSION -> RunTarget(RunKind.Session, key)
            ACTION_LAUNCH_GROUP -> RunTarget(RunKind.Group, key)
            ACTION_RUN -> RunKind.entries.firstOrNull { it.name.equals(kind, ignoreCase = true) }?.let { RunTarget(it, key) }
            else -> null
        }
    }

    /** Resolves a target given by id or by name against the saved state; null when nothing matches. */
    fun resolve(t: RunTarget, s: DockWorkspaceState): RunTarget? = when (t.kind) {
        RunKind.Workspace -> if (t.id == WorkspaceRefs.NEXT || t.id == WorkspaceRefs.PREVIOUS) t else s.workspaces.firstOrNull { it.id == t.id || it.name.equals(t.id, true) }?.let { RunTarget(t.kind, it.id) }
        RunKind.Group -> s.groups.firstOrNull { it.id == t.id || it.name.equals(t.id, true) }?.let { RunTarget(t.kind, it.id) }
        RunKind.Session -> s.sessions.firstOrNull { it.id == t.id || it.name.equals(t.id, true) }?.let { RunTarget(t.kind, it.id) }
        RunKind.App -> t
    }
}

/** Parses an app key (`component#userHash`) into a launch target; the hash is the same one the app list uses. */
object AppKeys {
    fun target(key: String): LaunchTarget? {
        val hashAt = key.lastIndexOf('#')
        if (hashAt <= 0) return null
        val comp = key.substring(0, hashAt)
        val slash = comp.indexOf('/')
        if (slash <= 0) return null
        val pkg = comp.substring(0, slash)
        val cls = comp.substring(slash + 1).let { if (it.startsWith(".")) pkg + it else it }
        return LaunchTarget(pkg, cls, key.substring(hashAt + 1).toIntOrNull() ?: return null)
    }
}

