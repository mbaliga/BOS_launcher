package com.bos.sphere.feature.dock

import com.bos.sphere.core.launch.AppGroup
import com.bos.sphere.core.launch.Session
import com.bos.sphere.core.data.ProfileSettings
import com.bos.sphere.core.data.ProfileSwitch
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
    /**
     * Wave 57 (profiles, CFG-46): the settings groups ([com.bos.sphere.core.data.ProfileGroup] ids) this profile carries. Empty for a
     * workspace from before profiles: it then carries no settings and switching behaves as it always did.
     */
    val carries: Set<String> = emptySet(),
    /** CFG-46: the carried settings, key (a Studio schema key) to the JSON text of its value; see [com.bos.sphere.core.data.ProfileSettings]. */
    val settings: Map<String, String> = emptyMap(),
    /** CFG-47: the app groups linked to this profile (ids of [DockWorkspaceState.groups]). */
    val groupLinks: List<String> = emptyList(),
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
    /**
     * CFG-48: the global value of each key the active profile carries, kept while the profile holds its own value (the precedence rule,
     * docs/decisions/profiles.md). Empty while the active profile carries nothing.
     */
    val globalSettings: Map<String, String> = emptyMap(),
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
    /** Wave 57 (profiles, CFG-48): where the carried settings live; the app points it at its stores ([StoresProfileSettings]). */
    private val settingsPort: () -> ProfileSettingsPort = { NoProfileSettings },
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

    fun create(name: String): String = create(name, null)

    /**
     * Wave 41: a new workspace, empty or a copy of [copyFrom]. A copy takes the home arrangement (the live one when [copyFrom] is the
     * active workspace and the host can capture it), the dock, the pinned Radar actions and the calm-centre apps. It does not take the
     * widget slots: those are hosted widget ids that two workspaces cannot share. It is not made active.
     */
    fun create(name: String, copyFrom: String?): String {
        val id = newId()
        val src = copyFrom?.let { f -> current.workspaces.firstOrNull { it.id == f } }
        val live = if (src != null && src.id == current.activeId) liveHome() else null
        val fresh = if (src == null) Workspace(id, name.trim().ifEmpty { "Profile" })
        else src.copy(
            id = id, name = name.trim().ifEmpty { src.name }, homeArrangement = live ?: src.homeArrangement, widgets = emptyList(),
            // Wave 57: a copy of the active profile takes the carried settings as they are on screen now.
            settings = if (src.id == current.activeId && src.carries.isNotEmpty()) ProfileSwitch.capture(readLive(ProfileSettings.keysOfIds(src.carries)), ProfileSettings.keysOfIds(src.carries)) else src.settings,
        )
        update { it.copy(workspaces = it.workspaces + fresh) }
        return id
    }

    /** The live home arrangement when the host can capture one now, else null. */
    fun liveHome(): String? = hooks().takeIf { it.ready }?.captureHome()

    fun rename(id: String, name: String) = update { s -> s.copy(workspaces = s.workspaces.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it }) }

    /** The last workspace cannot be deleted; deleting the active one switches to the first remaining. */
    fun delete(id: String) {
        if (current.workspaces.size <= 1 || current.workspaces.none { it.id == id }) return
        if (current.activeId == id) switchTo(current.workspaces.first { it.id != id }.id)
        update { s -> s.copy(workspaces = s.workspaces.filter { it.id != id }, captureDue = s.captureDue.takeIf { it != id }) }
        _undo.value?.let { u -> if (u.fromId == id || u.toId == id) _undo.value = null }
    }

    fun setNotificationApps(id: String, apps: Set<String>?) = update { s -> s.copy(workspaces = s.workspaces.map { if (it.id == id) it.copy(notificationApps = apps) else it }) }

    fun setPaletteActions(id: String, actions: List<String>) = update { s -> s.copy(workspaces = s.workspaces.map { if (it.id == id) it.copy(paletteActions = actions) else it }) }

    fun setWidgets(id: String, widgets: List<String>) = update { s -> s.copy(workspaces = s.workspaces.map { if (it.id == id) it.copy(widgets = widgets) else it }) }

    /** Saves the arrangement being left, makes [id] active and applies its arrangement, pins and notification scope. */
    fun switchTo(id: String): Boolean {
        val target = current.workspaces.firstOrNull { it.id == id } ?: return false
        val h = hooks()
        if (id == current.activeId) return true
        val from = current.active
        // CFG-48: the settings first, in one step: the profile being left keeps its carried keys as they are now, the entering one's are written.
        val lk = ProfileSettings.keysOfIds(from.carries)
        val ek = ProfileSettings.keysOfIds(target.carries)
        val plan = if (lk.isEmpty() && ek.isEmpty()) null else ProfileSwitch.plan(readLive(lk + ek), lk, current.globalSettings, ek, target.settings)
        _undo.value = ProfileSwitchUndo(from.id, from.name, id)
        if (!h.ready) {
            update { s -> s.copy(activeId = id, captureDue = s.captureDue ?: s.activeId, globalSettings = plan?.stash ?: s.globalSettings, workspaces = s.workspaces.map { if (plan != null && it.id == s.activeId && lk.isNotEmpty()) it.copy(settings = plan.leaving) else it }) }
            plan?.let { writeLive(it.writes) }
            return true
        }
        val leaving = h.captureHome()
        val leavingWidgets = h.captureWidgets()
        update { s ->
            s.copy(
                activeId = id,
                globalSettings = plan?.stash ?: s.globalSettings,
                workspaces = s.workspaces.map {
                    if (it.id != s.activeId) it
                    else it.copy(homeArrangement = leaving ?: it.homeArrangement, widgets = leavingWidgets ?: it.widgets, settings = if (plan != null && lk.isNotEmpty()) plan.leaving else it.settings)
                },
            )
        }
        plan?.let { writeLive(it.writes) }
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

    // ---- profiles (wave 57, CFG-45 to CFG-49): a profile is a workspace plus the settings it carries and its linked app groups

    private val _undo = MutableStateFlow<ProfileSwitchUndo?>(null)
    /** The last switch while it can still be undone ("Switch back to Home"); cleared by [switchBack] and by deleting either profile. */
    val undo: StateFlow<ProfileSwitchUndo?> = _undo.asStateFlow()

    /** True while a switch writes the settings, so the write-through of [syncActiveSettings] does not see half a switch. */
    @Volatile private var writing = false

    private fun readLive(keys: Set<String>): Map<String, String> {
        if (keys.isEmpty()) return emptyMap()
        val out = LinkedHashMap(runCatching { settingsPort().read(keys.filterTo(HashSet()) { !DockProfileKeys.isDock(it) }) }.getOrDefault(emptyMap()))
        if (keys.any(DockProfileKeys::isDock)) out += DockProfileKeys.read(current.dock).filterKeys { it in keys }
        return out
    }

    private fun writeLive(values: Map<String, String>) {
        if (values.isEmpty()) return
        writing = true
        try {
            val dock = values.filterKeys(DockProfileKeys::isDock)
            if (dock.isNotEmpty()) setDock { DockProfileKeys.apply(it, dock) }
            val rest = values.filterKeys { !DockProfileKeys.isDock(it) }
            if (rest.isNotEmpty()) runCatching { settingsPort().write(rest) }
        } finally { writing = false }
    }

    /** The live value of every key a profile can carry, as the user sees it now. */
    fun liveSettings(): Map<String, String> = readLive(ProfileSettings.ALL_KEYS.toSet())

    /** The global values (what a profile that carries nothing sees): the live ones, except the keys the active profile holds for itself. */
    fun globalSettings(): Map<String, String> = ProfileSwitch.global(liveSettings(), ProfileSettings.keysOfIds(current.active.carries), current.globalSettings)

    /**
     * CFG-48 precedence, write-through: a setting changed while a profile is active is saved to that profile when the profile carries
     * its group; every other change stays global (nothing to do). The app calls this when a store it watches changes.
     */
    fun syncActiveSettings() {
        if (writing) return
        val a = current.active
        val keys = ProfileSettings.keysOfIds(a.carries)
        if (keys.isEmpty()) return
        val cap = ProfileSwitch.capture(readLive(keys), keys)
        if (cap != a.settings) update { s -> s.copy(workspaces = s.workspaces.map { if (it.id == a.id) it.copy(settings = cap) else it }) }
    }

    /**
     * Changes which settings groups profile [id] carries. For the active profile the live values of a group being added become the
     * profile's (and their global value is kept aside), a group being dropped goes back to its global value. For another profile a
     * group being added starts from the global values.
     */
    fun setCarries(id: String, groups: Set<String>) {
        val w = current.workspaces.firstOrNull { it.id == id } ?: return
        val ids = groups.filterTo(LinkedHashSet()) { com.bos.sphere.core.data.ProfileGroup.forId(it) != null }
        if (ids == w.carries) return
        val oldK = ProfileSettings.keysOfIds(w.carries)
        val newK = ProfileSettings.keysOfIds(ids)
        if (id == current.activeId) {
            val live = readLive(oldK + newK)
            val g = ProfileSwitch.global(live, oldK, current.globalSettings)
            val added = newK - oldK
            val stash = current.globalSettings.filterKeys { it in newK } + g.filterKeys { it in added }
            val back = g.filter { (k, v) -> k in oldK && k !in newK && live[k] != v }
            update { s -> s.copy(globalSettings = stash, workspaces = s.workspaces.map { if (it.id == id) it.copy(carries = ids, settings = ProfileSwitch.capture(live, newK)) else it }) }
            writeLive(back)
        } else {
            val g = globalSettings()
            val kept = w.settings.filterKeys { it in newK }
            val added = ProfileSwitch.capture(g, newK - oldK)
            update { s -> s.copy(workspaces = s.workspaces.map { if (it.id == id) it.copy(carries = ids, settings = kept + added) else it }) }
        }
    }

    /**
     * CFG-45: a new profile. [copyFrom] (the "Current setup" start) copies that profile's contents as [create] does; otherwise the
     * profile starts from [homeArrangement] (null keeps the old "inherit the home" behaviour). [settings] is its snapshot of the
     * carried keys; it is not made active.
     */
    fun createProfile(
        name: String,
        copyFrom: String?,
        carries: Set<String>,
        settings: Map<String, String>,
        homeArrangement: String? = null,
        notificationApps: Set<String>? = null,
        groupLinks: List<String> = emptyList(),
    ): String {
        val id = create(name, copyFrom)
        val ids = carries.filterTo(LinkedHashSet()) { com.bos.sphere.core.data.ProfileGroup.forId(it) != null }
        val keys = ProfileSettings.keysOfIds(ids)
        update { s ->
            s.copy(workspaces = s.workspaces.map { w ->
                if (w.id != id) w
                else w.copy(
                    carries = ids, settings = settings.filterKeys { it in keys }, groupLinks = groupLinks,
                    homeArrangement = if (copyFrom == null) homeArrangement else w.homeArrangement,
                    dockPins = if (copyFrom == null) emptyList() else w.dockPins,
                    notificationApps = if (copyFrom == null) notificationApps else w.notificationApps,
                )
            })
        }
        return id
    }

    /** "Switch back": undoes the last switch (the profile that was left gets its home, dock, widgets and settings back). */
    fun switchBack(): Boolean {
        val u = _undo.value ?: return false
        if (current.activeId != u.toId || current.workspaces.none { it.id == u.fromId }) { _undo.value = null; return false }
        val ok = switchTo(u.fromId)
        _undo.value = null
        return ok
    }

    /**
     * CFG-47: links app group [groupId] to profile [id]; with [pin] it is also pinned to that profile's dock (the active profile's dock
     * is the live one; another profile's is written into its saved arrangement).
     */
    fun linkGroup(id: String, groupId: String, pin: Boolean) {
        val ref = DockItemRef.group(groupId)
        if (id == current.activeId && pin) setPins(DockPins.add(current.active.dockPins, ref))
        update { s ->
            s.copy(workspaces = s.workspaces.map { w ->
                if (w.id != id) w
                else {
                    val links = if (groupId in w.groupLinks) w.groupLinks else w.groupLinks + groupId
                    if (!pin || id == s.activeId) w.copy(groupLinks = links)
                    else w.copy(groupLinks = links, dockPins = DockPins.add(w.dockPins, ref), homeArrangement = ProfileArrangements.withPin(w.homeArrangement, ref))
                }
            })
        }
    }

    fun unlinkGroup(id: String, groupId: String) = update { s -> s.copy(workspaces = s.workspaces.map { if (it.id == id) it.copy(groupLinks = it.groupLinks - groupId) else it }) }

    // ---- groups and sessions

    fun saveGroup(group: AppGroup) = update { s -> s.copy(groups = s.groups.filter { it.id != group.id } + group) }
    fun deleteGroup(id: String) {
        update { s -> s.copy(groups = s.groups.filter { it.id != id }, workspaces = s.workspaces.map { w -> w.copy(dockPins = w.dockPins - DockItemRef.group(id), groupLinks = w.groupLinks - id) }) }
        // HOM-02: with the shared dock list in use, the group's pin leaves it too.
        pinSink?.invoke(current.active.dockPins)
    }
    fun saveSession(session: Session) = update { s -> s.copy(sessions = s.sessions.filter { it.id != session.id } + session) }
    fun deleteSession(id: String) = update { s -> s.copy(sessions = s.sessions.filter { it.id != id }) }
    fun addSchedule(sl: ScheduledLaunch) = update { s -> s.copy(schedules = s.schedules.filter { it.id != sl.id } + sl) }
    fun removeSchedule(id: String) = update { s -> s.copy(schedules = s.schedules.filter { it.id != id }) }

    fun idFor(prefix: String): String = prefix + newId()
}
