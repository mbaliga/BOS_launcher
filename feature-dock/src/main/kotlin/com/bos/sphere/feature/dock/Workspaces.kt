package com.bos.sphere.feature.dock

import com.bos.sphere.core.launch.AppGroup
import com.bos.sphere.core.launch.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A named context (WSP-07). [homeArrangement] is an opaque snapshot of the home layout that the home stream produces and
 * applies through [WorkspaceHooks]; widgets and palette actions are likewise opaque ids owned by their streams.
 */
data class Workspace(
    val id: String,
    val name: String,
    val homeArrangement: String? = null,
    val dockPins: List<DockItemRef> = emptyList(),
    val widgets: List<String> = emptyList(),
    val paletteActions: List<String> = emptyList(),
    /** Apps that may surface in the calm centre while this workspace is active; null shows all (WSP-08). */
    val notificationApps: Set<String>? = null,
)

/** What to run when a notification Open action or an automation intent fires (WSP-11, WSP-12). */
enum class RunKind { App, Group, Workspace, Session }

data class RunTarget(val kind: RunKind, val id: String)

data class ScheduledLaunch(val id: String, val label: String, val atMillis: Long, val target: RunTarget)

/** Everything this stream persists. The state stream's versioned model may adopt it; [schemaVersion] says which shape this is. */
data class DockWorkspaceState(
    val schemaVersion: Int = WorkspaceJson.SCHEMA_VERSION,
    val dock: DockSettings = DockSettings(),
    val activeId: String = DEFAULT_ID,
    val workspaces: List<Workspace> = listOf(Workspace(DEFAULT_ID, "Home")),
    val groups: List<AppGroup> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val schedules: List<ScheduledLaunch> = emptyList(),
    /**
     * Set when the active workspace was changed while no home could be captured (a broadcast with the launcher screen gone):
     * the live home still belongs to this workspace. [WorkspaceManager.applyActive] saves it there before applying the
     * active one. Stored, so a process death in between loses nothing (WSP-12).
     */
    val captureDue: String? = null,
) {
    val active: Workspace get() = workspaces.firstOrNull { it.id == activeId } ?: workspaces.first()

    companion object { const val DEFAULT_ID = "default" }
}

/** Where the home stream plugs in: capture the arrangement being left, apply the one being entered. */
interface WorkspaceHooks {
    /**
     * False while nothing can capture or apply a home (the launcher screen is not composed). A switch asked for then is held
     * and carried out when the hooks are attached, so the arrangement being left is never lost (WSP-07, WSP-12).
     */
    val ready: Boolean get() = true
    fun captureHome(): String? = null
    fun applyHome(arrangement: String?) {}
    fun applyNotificationScope(allowed: Set<String>?) {}
    /** The widget slots of the home being left, as opaque ids (WSP-07); null when this host has none to save. */
    fun captureWidgets(): List<String>? = null
    /** Puts the entered workspace's widget slots on the home. Called only when that workspace has a saved snapshot. */
    fun applyWidgets(widgets: List<String>) {}
    fun onSwitched(workspace: Workspace) {}
}

interface WorkspaceStore {
    fun read(): String?
    fun write(text: String)
}

class InMemoryWorkspaceStore(var text: String? = null) : WorkspaceStore {
    override fun read() = text
    override fun write(text: String) { this.text = text }
}

/**
 * Holds the state in memory and writes it behind [store]. Switching is a state change plus the hooks, so it does not wait on
 * storage (WSP-07: under 150 ms); the write runs through [io], which the app points at a background executor.
 */
class WorkspaceManager(
    private val store: WorkspaceStore,
    private val hooks: () -> WorkspaceHooks = { object : WorkspaceHooks {} },
    private val io: (() -> Unit) -> Unit = { it() },
    private val newId: () -> String = { java.util.UUID.randomUUID().toString().take(8) },
) {
    /** False when the stored document is from a newer version or unreadable: it is left untouched on disk (WSP-07). */
    private var persist = true

    /** False while the stored document could not be read: groups then look empty and must not be taken as deleted (WSP-05). */
    val groupsReliable: Boolean get() = persist
    private val _state = MutableStateFlow(load())
    val state: StateFlow<DockWorkspaceState> = _state.asStateFlow()
    val current: DockWorkspaceState get() = _state.value

    private fun load(): DockWorkspaceState {
        val text = runCatching { store.read() }.getOrNull() ?: return DockWorkspaceState()
        val decoded = WorkspaceJson.decode(text)
        if (decoded !is WorkspaceJson.Decoded.Ok) persist = false
        return (decoded as? WorkspaceJson.Decoded.Ok)?.state ?: DockWorkspaceState()
    }

    /** Re-reads the stored document after another writer replaced it (a state import, CFG-02). */
    fun reload() {
        persist = true
        _state.value = load()
    }

    private fun update(f: (DockWorkspaceState) -> DockWorkspaceState) {
        val next = f(_state.value)
        if (next == _state.value) return
        _state.value = next
        if (!persist) return
        val text = WorkspaceJson.encode(next)
        io { runCatching { store.write(text) } }
    }

    // ---- dock

    fun setDock(f: (DockSettings) -> DockSettings) = update { it.copy(dock = f(it.dock)) }

    /**
     * HOM-02: where the pins really live. When set (the app points it at `HomeState.dock`), [setPins] writes there and the
     * workspace's own list only mirrors it through [mirrorPins]; unset, the workspace list is the store (tests, other hosts).
     */
    @Volatile var pinSink: ((List<DockItemRef>) -> Unit)? = null

    fun setPins(pins: List<DockItemRef>) {
        val sink = pinSink
        if (sink != null) sink(pins) else updateActive { it.copy(dockPins = pins) }
    }

    /** Follows the shared dock list without writing it back. */
    fun mirrorPins(pins: List<DockItemRef>) = updateActive { if (it.dockPins == pins) it else it.copy(dockPins = pins) }

    /** PAL-08: adds [ref] to the dock pins of workspace [id] (not necessarily the active one); no change when it is already there. */
    fun addPinTo(id: String, ref: DockItemRef) = update { s -> s.copy(workspaces = s.workspaces.map { if (it.id == id) it.copy(dockPins = DockPins.add(it.dockPins, ref)) else it }) }

    private fun updateActive(f: (Workspace) -> Workspace) = update { s -> s.copy(workspaces = s.workspaces.map { if (it.id == s.activeId) f(it) else it }) }

    // ---- workspaces (WSP-07)

    fun create(name: String): String {
        val id = newId()
        update { it.copy(workspaces = it.workspaces + Workspace(id, name.trim().ifEmpty { "Workspace" })) }
        return id
    }

    fun rename(id: String, name: String) = update { s -> s.copy(workspaces = s.workspaces.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it }) }

    /** The last workspace cannot be deleted; deleting the active one switches to the first remaining. */
    fun delete(id: String) {
        if (current.workspaces.size <= 1 || current.workspaces.none { it.id == id }) return
        if (current.activeId == id) switchTo(current.workspaces.first { it.id != id }.id)
        update { s -> s.copy(workspaces = s.workspaces.filter { it.id != id }, captureDue = s.captureDue.takeIf { it != id }) }
    }

    fun setNotificationApps(id: String, apps: Set<String>?) = update { s -> s.copy(workspaces = s.workspaces.map { if (it.id == id) it.copy(notificationApps = apps) else it }) }

    fun setPaletteActions(id: String, actions: List<String>) = update { s -> s.copy(workspaces = s.workspaces.map { if (it.id == id) it.copy(paletteActions = actions) else it }) }

    fun setWidgets(id: String, widgets: List<String>) = update { s -> s.copy(workspaces = s.workspaces.map { if (it.id == id) it.copy(widgets = widgets) else it }) }

    /** Saves the arrangement being left, makes [id] active and applies its arrangement, pins and notification scope. */
    fun switchTo(id: String): Boolean {
        val target = current.workspaces.firstOrNull { it.id == id } ?: return false
        val h = hooks()
        if (id == current.activeId) return true
        if (!h.ready) {
            update { s -> s.copy(activeId = id, captureDue = s.captureDue ?: s.activeId) }
            return true
        }
        val leaving = h.captureHome()
        val leavingWidgets = h.captureWidgets()
        update { s ->
            s.copy(
                activeId = id,
                workspaces = s.workspaces.map {
                    if (it.id != s.activeId) it
                    else it.copy(homeArrangement = leaving ?: it.homeArrangement, widgets = leavingWidgets ?: it.widgets)
                },
            )
        }
        h.applyHome(target.homeArrangement)
        if (target.widgets.isNotEmpty()) h.applyWidgets(target.widgets)
        h.applyNotificationScope(target.notificationApps)
        h.onSwitched(target)
        return true
    }

    /** PAL-03: the Radar result "Switch to workspace <name>" carries the workspace's position in [DockWorkspaceState.workspaces]. */
    fun switchToIndex(index: Int): Boolean = current.workspaces.getOrNull(index)?.let { switchTo(it.id) } ?: false

    fun names(): List<String> = current.workspaces.map { it.name }

    fun next(): Boolean = step(+1)
    fun previous(): Boolean = step(-1)
    private fun step(delta: Int): Boolean {
        val list = current.workspaces
        if (list.size < 2) return false
        val i = list.indexOfFirst { it.id == current.activeId }.coerceAtLeast(0)
        return switchTo(list[(i + delta + list.size) % list.size].id)
    }

    /** Re-applies the active workspace's hooks, for process start. */
    fun applyActive() {
        val a = current.active
        val due = current.captureDue
        if (due != null && hooks().ready) {
            // The live home still holds the workspace that was left without a capture: save it there first (WSP-12).
            val live = hooks().captureHome()
            val liveWidgets = hooks().captureWidgets()
            update { s -> s.copy(captureDue = null, workspaces = s.workspaces.map { if (it.id == due) it.copy(homeArrangement = live ?: it.homeArrangement, widgets = liveWidgets ?: it.widgets) else it }) }
            // Read the active workspace again: when it is the one that was just saved (a switch away and back), the saved home is the current one.
            val now = current.active
            hooks().applyHome(now.homeArrangement)
            if (now.widgets.isNotEmpty()) hooks().applyWidgets(now.widgets)
            hooks().applyNotificationScope(now.notificationApps)
            hooks().onSwitched(now)
            return
        }
        hooks().applyHome(a.homeArrangement)
        if (a.widgets.isNotEmpty()) hooks().applyWidgets(a.widgets)
        hooks().applyNotificationScope(a.notificationApps)
    }

    // ---- groups and sessions

    fun saveGroup(group: AppGroup) = update { s -> s.copy(groups = s.groups.filter { it.id != group.id } + group) }
    fun deleteGroup(id: String) {
        update { s -> s.copy(groups = s.groups.filter { it.id != id }, workspaces = s.workspaces.map { w -> w.copy(dockPins = w.dockPins - DockItemRef.group(id)) }) }
        // HOM-02: with the shared dock list in use, the group's pin leaves it too.
        pinSink?.invoke(current.active.dockPins)
    }
    fun saveSession(session: Session) = update { s -> s.copy(sessions = s.sessions.filter { it.id != session.id } + session) }
    fun deleteSession(id: String) = update { s -> s.copy(sessions = s.sessions.filter { it.id != id }) }
    fun addSchedule(sl: ScheduledLaunch) = update { s -> s.copy(schedules = s.schedules.filter { it.id != sl.id } + sl) }
    fun removeSchedule(id: String) = update { s -> s.copy(schedules = s.schedules.filter { it.id != id }) }

    fun idFor(prefix: String): String = prefix + newId()
}
