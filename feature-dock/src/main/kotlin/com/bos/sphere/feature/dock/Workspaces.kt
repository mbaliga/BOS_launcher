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
) {
    val active: Workspace get() = workspaces.firstOrNull { it.id == activeId } ?: workspaces.first()

    companion object { const val DEFAULT_ID = "default" }
}

/** Where the home stream plugs in: capture the arrangement being left, apply the one being entered. */
interface WorkspaceHooks {
    fun captureHome(): String? = null
    fun applyHome(arrangement: String?) {}
    fun applyNotificationScope(allowed: Set<String>?) {}
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
    private val _state = MutableStateFlow(load())
    val state: StateFlow<DockWorkspaceState> = _state.asStateFlow()
    val current: DockWorkspaceState get() = _state.value

    private fun load(): DockWorkspaceState {
        val text = runCatching { store.read() }.getOrNull() ?: return DockWorkspaceState()
        return (WorkspaceJson.decode(text) as? WorkspaceJson.Decoded.Ok)?.state ?: DockWorkspaceState()
    }

    private fun update(f: (DockWorkspaceState) -> DockWorkspaceState) {
        val next = f(_state.value)
        if (next == _state.value) return
        _state.value = next
        val text = WorkspaceJson.encode(next)
        io { runCatching { store.write(text) } }
    }

    // ---- dock

    fun setDock(f: (DockSettings) -> DockSettings) = update { it.copy(dock = f(it.dock)) }

    fun setPins(pins: List<DockItemRef>) = updateActive { it.copy(dockPins = pins) }

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
        update { s -> s.copy(workspaces = s.workspaces.filter { it.id != id }) }
    }

    fun setNotificationApps(id: String, apps: Set<String>?) = update { s -> s.copy(workspaces = s.workspaces.map { if (it.id == id) it.copy(notificationApps = apps) else it }) }

    fun setPaletteActions(id: String, actions: List<String>) = update { s -> s.copy(workspaces = s.workspaces.map { if (it.id == id) it.copy(paletteActions = actions) else it }) }

    fun setWidgets(id: String, widgets: List<String>) = update { s -> s.copy(workspaces = s.workspaces.map { if (it.id == id) it.copy(widgets = widgets) else it }) }

    /** Saves the arrangement being left, makes [id] active and applies its arrangement, pins and notification scope. */
    fun switchTo(id: String): Boolean {
        val target = current.workspaces.firstOrNull { it.id == id } ?: return false
        if (id == current.activeId) return true
        val h = hooks()
        val leaving = h.captureHome()
        update { s ->
            s.copy(
                activeId = id,
                workspaces = s.workspaces.map { if (it.id == s.activeId && leaving != null) it.copy(homeArrangement = leaving) else it },
            )
        }
        h.applyHome(target.homeArrangement)
        h.applyNotificationScope(target.notificationApps)
        h.onSwitched(target)
        return true
    }

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
        hooks().applyHome(a.homeArrangement)
        hooks().applyNotificationScope(a.notificationApps)
    }

    // ---- groups and sessions

    fun saveGroup(group: AppGroup) = update { s -> s.copy(groups = s.groups.filter { it.id != group.id } + group) }
    fun deleteGroup(id: String) = update { s -> s.copy(groups = s.groups.filter { it.id != id }, workspaces = s.workspaces.map { w -> w.copy(dockPins = w.dockPins - DockItemRef.group(id)) }) }
    fun saveSession(session: Session) = update { s -> s.copy(sessions = s.sessions.filter { it.id != session.id } + session) }
    fun deleteSession(id: String) = update { s -> s.copy(sessions = s.sessions.filter { it.id != id }) }
    fun addSchedule(sl: ScheduledLaunch) = update { s -> s.copy(schedules = s.schedules.filter { it.id != sl.id } + sl) }
    fun removeSchedule(id: String) = update { s -> s.copy(schedules = s.schedules.filter { it.id != id }) }

    fun idFor(prefix: String): String = prefix + newId()
}
