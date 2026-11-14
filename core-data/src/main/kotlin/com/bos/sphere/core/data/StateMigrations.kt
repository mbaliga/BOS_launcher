package com.bos.sphere.core.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * CFG-03: files from every earlier schema version import through these steps, one version at a time. Each step
 * takes the document of version N and returns version N+1, copying every field it does not own (unknown fields
 * survive). A file from a newer build than this one is not rejected: it is read as the current schema and its
 * unknown fields are kept; [Result.fromNewer] tells the UI to say so.
 */
object StateMigrations {
    class Result(val doc: StateDocument, val fromVersion: Int, val fromNewer: Boolean, val steps: List<Int>)

    private val steps: Map<Int, (JSONObject) -> JSONObject> = mapOf(
        1 to ::v1ToV2,
    )

    /** Versions that have a migration step. Tests require a fixture file for each, so a new version cannot ship without one. */
    val migratableVersions: Set<Int> get() = steps.keys

    fun toCurrent(doc: StateDocument): Result {
        val from = doc.schemaVersion
        if (from < StateSchema.OLDEST) throw StateFormatException("Unsupported schema version $from")
        if (from >= StateSchema.CURRENT) {
            return Result(doc, from, from > StateSchema.CURRENT, emptyList())
        }
        var cur = JSONObject(doc.json.toString())
        val applied = ArrayList<Int>()
        var v = from
        while (v < StateSchema.CURRENT) {
            val step = steps[v] ?: throw StateFormatException("No migration from schema version $v")
            cur = step(cur)
            cur.put(StateSchema.K_VERSION, v + 1)
            applied += v
            v++
        }
        return Result(StateDocument(cur), from, false, applied)
    }

    // v1: flat prefs at the top, layoutPages as a string, corners/gestures as maps.
    // v2: sectioned, settings as a separable document.
    private fun v1ToV2(old: JSONObject): JSONObject {
        val out = JSONObject()
        val owned = setOf("prefs", "layoutPages", "removed", "seeded", "corners", "gestures", "launchpadRules")
        old.keys().forEach { k -> if (k !in owned) out.put(k, old.get(k)) }

        out.put(
            StateSchema.K_SETTINGS,
            JSONObject()
                .put(StateSchema.K_FORMAT, StateSchema.SETTINGS_FORMAT)
                .put("settingsVersion", StateSchema.SETTINGS_CURRENT)
                .put("prefs", old.optJSONObject("prefs") ?: JSONObject()),
        )
        val pages = old.optString("layoutPages").takeIf { it.isNotEmpty() }?.let { runCatching { JSONArray(it) }.getOrNull() } ?: JSONArray()
        out.put(
            StateSchema.K_LAYOUT,
            JSONObject()
                .put("pages", pages)
                .put("removed", old.optJSONArray("removed") ?: JSONArray())
                .put("seeded", old.optBoolean("seeded", false)),
        )
        out.put(
            StateSchema.K_SHORTCUTS,
            JSONObject()
                .put("corners", old.optJSONObject("corners") ?: JSONObject())
                .put("gestures", old.optJSONObject("gestures") ?: JSONObject()),
        )
        old.optJSONArray("launchpadRules")?.let { out.put(StateSchema.K_LAUNCHPAD, JSONObject().put("rules", it)) }
        // New in v2: workspaces, sessions, palette aliases and widgets. A v1 file never carried them, so they are left
        // absent (not written as empty): importing it must not wipe the ones already on this install.
        return out
    }
}
