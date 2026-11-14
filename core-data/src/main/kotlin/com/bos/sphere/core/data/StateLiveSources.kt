package com.bos.sphere.core.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * CFG-01: the sections that live in another work stream's own storage (palette aliases, workspaces, sessions, dock
 * shell). The state file reads them from there on export and writes them back on import, so the export is the real
 * data and not a copy that nothing keeps current.
 */
interface LiveSections {
    /** The current values of the sections this source owns, by section name. Absent keys are not owned or have nothing. */
    fun read(): Map<String, JSONArray>
    /** The object sections ([StateSchema.OBJECT_SECTIONS]) this source owns. */
    fun readObjects(): Map<String, JSONObject>
    /** Writes the sections that [present] names. */
    fun write(opaque: Map<String, JSONArray>, objects: Map<String, JSONObject>, present: Set<String>)
}

/**
 * Wraps [StateExtrasStore] so reads see the live sections and writes push them back. Everything else stays in the
 * extras file. Pass [read] and [write] to [StateApplier].
 */
class LiveExtras(private val store: StateExtrasStore, private val live: List<LiveSections>) {
    fun read(): StateExtras {
        val base = store.read()
        var opaque = base.opaque
        var objects = base.objects
        for (l in live) {
            opaque = opaque + l.read()
            objects = objects + l.readObjects()
        }
        return base.copy(opaque = opaque, objects = objects)
    }

    fun write(e: StateExtras) {
        store.write(e)
        val present = HashSet<String>().apply {
            addAll(e.opaque.keys)
            addAll(e.objects.keys)
        }
        for (l in live) l.write(e.opaque, e.objects, present)
    }
}

/** Palette aliases (PAL-02) as `[{"package": ..., "alias": ...}]`. Stored as tab-separated lines, the format of `PalettePrefs`. */
object AliasCodec {
    fun toJson(raw: String?): JSONArray = JSONArray().also { a ->
        raw.orEmpty().lineSequence().mapNotNull { l -> l.split('\t', limit = 2).takeIf { it.size == 2 && it[0].isNotBlank() && it[1].isNotBlank() } }
            .forEach { a.put(JSONObject().put("package", it[0]).put("alias", it[1])) }
    }

    fun fromJson(a: JSONArray?): String {
        if (a == null) return ""
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val p = o.optString("package").trim()
            val al = o.optString("alias").replace('\n', ' ').replace('\t', ' ').trim()
            if (p.isEmpty() || al.isEmpty()) null else "$p\t$al"
        }.joinToString("\n")
    }
}

/** Reads and writes the one string preference that holds the aliases (`palette` / `aliases`). */
class PaletteAliasSource(private val get: () -> String?, private val put: (String) -> Unit) : LiveSections {
    override fun read(): Map<String, JSONArray> = mapOf(StateSchema.K_ALIASES to AliasCodec.toJson(get()))
    override fun readObjects(): Map<String, JSONObject> = emptyMap()
    override fun write(opaque: Map<String, JSONArray>, objects: Map<String, JSONObject>, present: Set<String>) {
        if (StateSchema.K_ALIASES in present) put(AliasCodec.fromJson(opaque[StateSchema.K_ALIASES]))
    }
}

/**
 * Workspaces, sessions and the dock shell, read from and written into the dock stream's JSON file
 * (`liftoff_workspaces.json`, its own `schemaVersion` 1). Only the keys this source owns are replaced; schedules and
 * any field it does not know stay as they are. A file from a newer version is never overwritten.
 *
 * Not carried: per-workspace widget ids (they are meaningless on another install) and scheduled reminders (they are
 * tied to a moment in time).
 */
class WorkspaceFileSource(private val file: File) : LiveSections {
    private fun load(): JSONObject? = runCatching { JSONObject(file.readText()) }.getOrNull()

    override fun read(): Map<String, JSONArray> {
        val o = load() ?: return emptyMap()
        val ws = JSONArray()
        o.optJSONArray("workspaces")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { w -> ws.put(JSONObject(w.toString()).put("widgets", JSONArray())) }
        }
        return mapOf(
            StateSchema.K_WORKSPACES to ws,
            StateSchema.K_SESSIONS to JSONArray(o.optJSONArray("sessions")?.toString() ?: "[]"),
        )
    }

    override fun readObjects(): Map<String, JSONObject> {
        val o = load() ?: return emptyMap()
        return mapOf(
            StateSchema.K_DOCK to JSONObject()
                .put("settings", JSONObject(o.optJSONObject("dock")?.toString() ?: "{}"))
                .put("activeId", o.optString("activeId"))
                .put("groups", JSONArray(o.optJSONArray("groups")?.toString() ?: "[]")),
        )
    }

    override fun write(opaque: Map<String, JSONArray>, objects: Map<String, JSONObject>, present: Set<String>) {
        val dock = objects[StateSchema.K_DOCK]
        val writes = StateSchema.K_WORKSPACES in present || StateSchema.K_SESSIONS in present || StateSchema.K_DOCK in present
        if (!writes) return
        val existing = if (file.exists()) load() else JSONObject().put("schemaVersion", SCHEMA_VERSION)
        // Unreadable or newer than this build understands: leave it alone, as the dock stream's own store does.
        if (existing == null || existing.optInt("schemaVersion", -1) > SCHEMA_VERSION) return
        if (StateSchema.K_WORKSPACES in present) {
            val incoming = opaque[StateSchema.K_WORKSPACES] ?: JSONArray()
            val old = existing.optJSONArray("workspaces")
            val merged = JSONArray()
            for (i in 0 until incoming.length()) {
                val w = incoming.optJSONObject(i) ?: continue
                val keepWidgets = (0 until (old?.length() ?: 0)).firstNotNullOfOrNull { j -> old?.optJSONObject(j)?.takeIf { it.optString("id") == w.optString("id") } }?.optJSONArray("widgets")
                merged.put(JSONObject(w.toString()).put("widgets", keepWidgets ?: JSONArray()))
            }
            if (merged.length() > 0) existing.put("workspaces", merged)
        }
        if (StateSchema.K_SESSIONS in present) existing.put("sessions", opaque[StateSchema.K_SESSIONS] ?: JSONArray())
        if (StateSchema.K_DOCK in present && dock != null) {
            dock.optJSONObject("settings")?.takeIf { it.length() > 0 }?.let { existing.put("dock", it) }
            dock.optJSONArray("groups")?.let { existing.put("groups", it) }
        }
        val ids = existing.optJSONArray("workspaces")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("id") } }.orEmpty()
        val wanted = dock?.optString("activeId").orEmpty()
        val active = existing.optString("activeId")
        if (ids.isNotEmpty() && active !in ids) existing.put("activeId", if (wanted in ids) wanted else ids.first())
        else if (wanted in ids && StateSchema.K_DOCK in present) existing.put("activeId", wanted)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(existing.toString())
        if (!tmp.renameTo(file)) { file.writeText(existing.toString()); tmp.delete() }
    }

    companion object {
        /** The dock stream's `WorkspaceJson.SCHEMA_VERSION`; a test in the app module keeps the two equal. */
        const val SCHEMA_VERSION = 1
    }
}


/**
 * Settings kept in preference files other than `launcher_prefs`: an allow-list of files that hold only setup (never
 * history, indexes or device-bound values; see [StateSchema.NEVER_EXPORTED_STORES]). Each file becomes
 * `{key: {"t": type, "v": value}}`. Import replaces the keys of the listed files. The in-memory stores that read these files
 * pick the values up when the activity is recreated.
 */
class PrefFilesSource(
    private val open: (String) -> android.content.SharedPreferences,
    private val files: List<String> = ALLOWED,
) : LiveSections {
    override fun read(): Map<String, JSONArray> = emptyMap()

    override fun readObjects(): Map<String, JSONObject> {
        val out = JSONObject()
        for (f in files) {
            val all = open(f).all
            if (all.isEmpty()) continue
            val o = JSONObject()
            all.toSortedMap().forEach { (k, v) -> encode(v)?.let { o.put(k, it) } }
            out.put(f, o)
        }
        return mapOf(StateSchema.K_PREF_FILES to out)
    }

    override fun write(opaque: Map<String, JSONArray>, objects: Map<String, JSONObject>, present: Set<String>) {
        if (StateSchema.K_PREF_FILES !in present) return
        val src = objects[StateSchema.K_PREF_FILES] ?: return
        for (f in files) {
            val o = src.optJSONObject(f) ?: continue
            val ed = open(f).edit().clear()
            o.keys().forEach { k -> decode(o.optJSONObject(k))?.let { v -> put(ed, k, v) } }
            ed.apply()
        }
    }

    private fun encode(v: Any?): JSONObject? = when (v) {
        is String -> JSONObject().put("t", "s").put("v", v)
        is Boolean -> JSONObject().put("t", "b").put("v", v)
        is Int -> JSONObject().put("t", "i").put("v", v)
        is Long -> JSONObject().put("t", "l").put("v", v)
        is Float -> JSONObject().put("t", "f").put("v", v.toString().toDouble())
        is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(v.map { it.toString() }))
        else -> null
    }

    private fun decode(o: JSONObject?): Any? = o?.let {
        when (it.optString("t")) {
            "s" -> it.optString("v")
            "b" -> it.optBoolean("v")
            "i" -> it.optInt("v")
            "l" -> it.optLong("v")
            "f" -> it.optDouble("v").toFloat()
            "set" -> it.optJSONArray("v")?.let { a -> List(a.length()) { i -> a.getString(i) }.toSet() }
            else -> null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun put(ed: android.content.SharedPreferences.Editor, k: String, v: Any) {
        when (v) {
            is String -> ed.putString(k, v)
            is Boolean -> ed.putBoolean(k, v)
            is Int -> ed.putInt(k, v)
            is Long -> ed.putLong(k, v)
            is Float -> ed.putFloat(k, v)
            is Set<*> -> ed.putStringSet(k, v as Set<String>)
        }
    }

    companion object {
        val ALLOWED = listOf("home_state", "home_tags", "home_icons", "home_badges", "action_apps", "radar_ui", "orbit_feel", "puck_actions", "home_layout_by_class", "home_extras")
    }
}
