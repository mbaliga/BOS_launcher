package com.bos.sphere.core.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * CFG-01 / CFG-03 / CFG-11: the versioned, JSON-serialisable state model.
 *
 * A state file is one JSON object. Its shape is described by [StateSchema]; the object itself is kept
 * as a [JSONObject] so fields this build does not know (written by a newer build, or by another work
 * stream) survive a round trip untouched.
 *
 * Schema history (every version listed here has a fixture under `core-data/src/test/resources` and a
 * migration step in [StateMigrations]):
 *  - 1: first export format. Flat `prefs` map at the top, `layoutPages` as an embedded JSON string,
 *       `corners` / `gestures` as maps of the stored strings. No workspaces, sessions or aliases.
 *  - 2: current. Sections: `layout`, `settings` (a separable document, CFG-11), `shortcuts`,
 *       `launchpad`, `paletteAliases`, `workspaces`, `sessions`, `widgets`.
 */
object StateSchema {
    const val FORMAT = "liftoff-state"
    const val CURRENT = 2
    const val OLDEST = 1

    /** The separable settings document (CFG-11) has its own format name and version. */
    const val SETTINGS_FORMAT = "liftoff-settings"
    const val SETTINGS_CURRENT = 1

    const val K_FORMAT = "format"
    const val K_VERSION = "schemaVersion"
    const val K_EXPORTED_AT = "exportedAt"
    const val K_APP = "app"
    const val K_LAYOUT = "layout"
    const val K_SETTINGS = "settings"
    const val K_SHORTCUTS = "shortcuts"
    const val K_LAUNCHPAD = "launchpad"
    const val K_ALIASES = "paletteAliases"
    const val K_WORKSPACES = "workspaces"
    const val K_SESSIONS = "sessions"
    const val K_WIDGETS = "widgets"
    /** Dock settings, active workspace and app groups (added within v2; older v2 files lack it and are left untouched on import). */
    const val K_DOCK = "dock"
    /** Settings kept in other preference files (home state, tags, icon overrides, badges, action apps, Radar and Orbit options); an allow-list, see [PrefFilesSource]. */
    const val K_PREF_FILES = "prefFiles"

    /** Top-level keys this build understands in the current schema. Anything else is preserved, never dropped. */
    val KNOWN_TOP: Set<String> = setOf(
        K_FORMAT, K_VERSION, K_EXPORTED_AT, K_APP, K_LAYOUT, K_SETTINGS, K_SHORTCUTS, K_LAUNCHPAD,
        K_ALIASES, K_WORKSPACES, K_SESSIONS, K_WIDGETS, K_DOCK, K_PREF_FILES,
    )

    /** Sections other work streams fill in later; this build stores and round-trips them verbatim. */
    val OPAQUE_SECTIONS: List<String> = listOf(K_ALIASES, K_WORKSPACES, K_SESSIONS)

    /** Sections that are one JSON object (not an array), filled from other work streams' storage. */
    val OBJECT_SECTIONS: List<String> = listOf(K_DOCK, K_PREF_FILES)

    /**
     * Preference keys that are bound to this device and so never leave it: a widget id is meaningless on
     * another install (widgets re-bind, CFG-02), a picture path points into this app's private storage, and
     * onboarding state belongs to the install.
     */
    val DEVICE_BOUND_PREF_KEYS: Set<String> = setOf("leftWidgetId", "bgPicture", "onboardingDone")

    /**
     * What an export never contains (CFG-01, NFR-18). This is documentation the tests enforce: no SharedPreferences
     * file in this list is read by the exporter, so the data cannot reach a file.
     */
    /**
     * Stores and files that must stay out of system backup and device transfer (NFR-18) beyond [NEVER_EXPORTED_STORES]: the
     * palette file mixes aliases with learned selection habits, the file index lists the user's files, spike results and
     * the desktop profile table describe this device, and crash reports hold stack traces. The user's own export file is
     * the portable copy of the part of this that is setup (aliases).
     */
    val BACKUP_EXCLUDED_PREF_FILES: Set<String> = setOf("palette", "liftoff_spikes", "desktop_surface", "liftoff_misc")
    val BACKUP_EXCLUDED_FILES: Set<String> = setOf("palette-files.idx", "crash-reports")

    val NEVER_EXPORTED_STORES: Set<String> = setOf(
        "launch_history", "search_habits", "steps", "weather", "crash_loop", "icon_colors", "liftoff_misc",
    )
}

class StateFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A state document of any version; use [StateMigrations.toCurrent] before reading sections. */
class StateDocument(val json: JSONObject) {
    val schemaVersion: Int get() = json.optInt(StateSchema.K_VERSION, 0)

    fun section(name: String): JSONObject? = json.optJSONObject(name)
    fun array(name: String): JSONArray? = json.optJSONArray(name)

    /** Top-level fields this build does not know. */
    fun unknownTop(): JSONObject = JSONObject().also { out ->
        json.keys().forEach { k -> if (k !in StateSchema.KNOWN_TOP) out.put(k, json.get(k)) }
    }

    fun encode(pretty: Boolean = true): String = if (pretty) json.toString(2) else json.toString()

    /**
     * CFG-11: the settings portion on its own, as a document that can travel without the layout, workspaces or
     * sessions. Null when this document has no settings section.
     */
    fun settingsDocument(): JSONObject? = section(StateSchema.K_SETTINGS)?.let { JSONObject(it.toString()) }

    companion object {
        fun parse(text: String): StateDocument {
            val o = try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw StateFormatException("Not a Liftoff state file: ${e.message}", e)
            }
            // A bare settings document is accepted and wrapped, so a settings-only file can be imported (CFG-11).
            if (o.optString(StateSchema.K_FORMAT) == StateSchema.SETTINGS_FORMAT) {
                return StateDocument(
                    JSONObject()
                        .put(StateSchema.K_FORMAT, StateSchema.FORMAT)
                        .put(StateSchema.K_VERSION, StateSchema.CURRENT)
                        .put(StateSchema.K_SETTINGS, o),
                )
            }
            if (o.optString(StateSchema.K_FORMAT) != StateSchema.FORMAT) {
                throw StateFormatException("Not a Liftoff state file (format is \"${o.optString(StateSchema.K_FORMAT)}\")")
            }
            if (!o.has(StateSchema.K_VERSION)) throw StateFormatException("The file has no schema version")
            return StateDocument(o)
        }
    }
}
