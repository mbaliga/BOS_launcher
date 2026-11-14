package com.bos.sphere.feature.dock

import com.bos.sphere.core.launch.AppGroup
import com.bos.sphere.core.launch.Bounds
import com.bos.sphere.core.launch.GroupLayout
import com.bos.sphere.core.launch.LaunchRequest
import com.bos.sphere.core.launch.LaunchTarget
import com.bos.sphere.core.launch.Placement
import com.bos.sphere.core.launch.Session
import com.bos.sphere.core.launch.SessionEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serialisation for [DockWorkspaceState], kept apart from the state so the state stream's store can adopt the model and
 * replace this. The first field of the document is `schemaVersion`; a newer version is refused rather than overwritten.
 */
object WorkspaceJson {
    const val SCHEMA_VERSION = 1

    sealed interface Decoded {
        data class Ok(val state: DockWorkspaceState) : Decoded
        data class TooNew(val version: Int) : Decoded
        data object Corrupt : Decoded
    }

    fun encode(s: DockWorkspaceState): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("dock", JSONObject().put("enabled", s.dock.enabled ?: JSONObject.NULL).put("mode", s.dock.mode.name).put("edge", s.dock.edge.name).put("groupShelf", s.dock.groupShelf))
        put("activeId", s.activeId)
        put("workspaces", JSONArray(s.workspaces.map { w ->
            JSONObject().put("id", w.id).put("name", w.name).put("home", w.homeArrangement ?: JSONObject.NULL)
                .put("pins", JSONArray(w.dockPins.map { it.encode() }))
                .put("widgets", JSONArray(w.widgets)).put("actions", JSONArray(w.paletteActions))
                .put("notifyApps", w.notificationApps?.let { JSONArray(it.sorted()) } ?: JSONObject.NULL)
        }))
        put("groups", JSONArray(s.groups.map { g ->
            JSONObject().put("id", g.id).put("name", g.name).put("layout", g.layout.name).put("members", JSONArray(g.members.map(::request)))
        }))
        put("sessions", JSONArray(s.sessions.map { x ->
            JSONObject().put("id", x.id).put("name", x.name).put("layout", x.layout.name).put("savedAt", x.savedAt)
                .put("entries", JSONArray(x.entries.map { e ->
                    JSONObject().put("target", target(e.target)).put("display", e.displayId ?: JSONObject.NULL).put("displayName", e.displayName ?: JSONObject.NULL)
                        .put("bounds", e.bounds?.let(::bounds) ?: JSONObject.NULL).put("placement", e.placement.name)
                }))
        }))
        put("schedules", JSONArray(s.schedules.map { c ->
            JSONObject().put("id", c.id).put("label", c.label).put("at", c.atMillis).put("kind", c.target.kind.name).put("target", c.target.id)
        }))
    }.toString()

    fun decode(text: String): Decoded {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return Decoded.Corrupt
        val version = o.optInt("schemaVersion", -1)
        if (version < 0) return Decoded.Corrupt
        if (version > SCHEMA_VERSION) return Decoded.TooNew(version)
        return runCatching {
            val d = o.optJSONObject("dock")
            val dock = DockSettings(
                enabled = if (d == null || d.isNull("enabled")) null else d.getBoolean("enabled"),
                mode = d?.optString("mode")?.let { m -> DockMode.entries.firstOrNull { it.name == m } } ?: DockMode.PinnedPlusRecent,
                edge = d?.optString("edge")?.let { m -> DockEdge.entries.firstOrNull { it.name == m } } ?: DockEdge.Bottom,
                groupShelf = d?.optBoolean("groupShelf", true) ?: true,
            )
            val workspaces = o.optJSONArray("workspaces").objs().map { w ->
                Workspace(
                    id = w.getString("id"), name = w.getString("name"),
                    homeArrangement = w.optStringOrNull("home"),
                    dockPins = w.optJSONArray("pins").strs().mapNotNull(DockItemRef::decode),
                    widgets = w.optJSONArray("widgets").strs(), paletteActions = w.optJSONArray("actions").strs(),
                    notificationApps = if (w.isNull("notifyApps") || !w.has("notifyApps")) null else w.getJSONArray("notifyApps").strs().toSet(),
                )
            }.ifEmpty { listOf(Workspace(DockWorkspaceState.DEFAULT_ID, "Home")) }
            val groups = o.optJSONArray("groups").objs().mapNotNull { g ->
                runCatching {
                    AppGroup(g.getString("name"), g.getJSONArray("members").objs().map(::request), layoutOf(g.optString("layout")), g.getString("id"))
                }.getOrNull()
            }
            val sessions = o.optJSONArray("sessions").objs().mapNotNull { x -> runCatching {
                Session(
                    x.getString("id"), x.getString("name"),
                    x.getJSONArray("entries").objs().map { e ->
                        SessionEntry(
                            target(e.getJSONObject("target")), if (e.isNull("display")) null else e.getInt("display"), e.optStringOrNull("displayName"),
                            e.optJSONObject("bounds")?.let(::bounds), placementOf(e.optString("placement")),
                        )
                    },
                    layoutOf(x.optString("layout")), x.optLong("savedAt"),
                )
            }.getOrNull() }
            val schedules = o.optJSONArray("schedules").objs().mapNotNull { c ->
                val kind = RunKind.entries.firstOrNull { it.name == c.optString("kind") } ?: return@mapNotNull null
                ScheduledLaunch(c.getString("id"), c.optString("label"), c.getLong("at"), RunTarget(kind, c.getString("target")))
            }
            val active = o.optString("activeId", DockWorkspaceState.DEFAULT_ID).takeIf { id -> workspaces.any { it.id == id } } ?: workspaces.first().id
            Decoded.Ok(DockWorkspaceState(SCHEMA_VERSION, dock, active, workspaces, groups, sessions, schedules))
        }.getOrDefault(Decoded.Corrupt)
    }

    private fun layoutOf(s: String) = GroupLayout.entries.firstOrNull { it.name == s } ?: GroupLayout.Tile
    private fun placementOf(s: String) = Placement.entries.firstOrNull { it.name == s } ?: Placement.Default
    private fun target(t: LaunchTarget) = JSONObject().put("p", t.packageName).put("c", t.className ?: JSONObject.NULL).put("u", t.userId)
    private fun target(o: JSONObject) = LaunchTarget(o.getString("p"), o.optStringOrNull("c"), o.optInt("u"))
    private fun bounds(b: Bounds) = JSONObject().put("l", b.left).put("t", b.top).put("r", b.right).put("b", b.bottom)
    private fun bounds(o: JSONObject) = Bounds(o.getInt("l"), o.getInt("t"), o.getInt("r"), o.getInt("b"))
    private fun request(r: LaunchRequest) = JSONObject().put("target", target(r.target)).put("display", r.displayId ?: JSONObject.NULL)
        .put("bounds", r.bounds?.let(::bounds) ?: JSONObject.NULL).put("placement", r.placement.name)
    private fun request(o: JSONObject) = LaunchRequest(
        target(o.getJSONObject("target")), if (o.isNull("display")) null else o.getInt("display"),
        o.optJSONObject("bounds")?.let(::bounds), placementOf(o.optString("placement")),
    )

    private fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
    private fun JSONArray?.strs(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }
    private fun JSONObject.optStringOrNull(k: String): String? = if (!has(k) || isNull(k)) null else getString(k)
}
