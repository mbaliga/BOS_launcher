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
 * Wave 26: the wallpaper per surface keys (wpGlance*, wpPages*) live in launcher_prefs, which is exported and backed up like the rest; no version bump (additive).
 * Wave 27: the launcher haptic strength lives in its own exported pref file "launcher_haptics" (PrefFilesSource.ALLOWED), see [LauncherHapticSettings]; additive, no version bump.
 *
 * Wave 28: the Comms content scale (75 to 125 percent) lives in its own exported pref file "comms_scale" (PrefFilesSource.ALLOWED), see [CommsScaleSettings]; additive, no version bump.
 *
 * Wave 39: the floating Settings preview window (position, size and minimised state per orientation) lives in its own device-local pref file "preview_window", excluded from backup and transfer (BACKUP_EXCLUDED_PREF_FILES) and never exported.
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
    /** 3 (wave 15): the old background gallery settings are retired; see [StateMigrations] and docs/decisions/backgroundsReset.md. */
    const val CURRENT = 3
    const val OLDEST = 1

    /** The separable settings document (CFG-11) has its own format name and version. */
    const val SETTINGS_FORMAT = "liftoff-settings"
    const val SETTINGS_CURRENT = 2

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

    /**
     * Wave 34 (wallpaper): the gallery picture of a surface is a copy in the app's private files/wallpapers folder, which is not carried by the export file or by system
     * backup (a user's photos stay on the device). The prefs that name or describe that file therefore stay out of export and import too; the fit, dim and animation mode are
     * kept with the rest of the settings and apply to whatever picture is chosen next. A restored install whose kind is Picture but has no file draws the solid colour.
     */
    val WALLPAPER_PICTURE_KEYS: Set<String> = listOf("wpGlance", "wpPages").flatMap { p -> listOf("Pic", "PicAnim", "PicVid", "PicMs", "PicOk", "PicBounce").map { p + it } }.toSet()

    val DEVICE_BOUND_PREF_KEYS: Set<String> = setOf("leftWidgetId", "bgPicture", "onboardingDone") + WALLPAPER_PICTURE_KEYS

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
    val BACKUP_EXCLUDED_PREF_FILES: Set<String> = setOf("palette", "liftoff_spikes", "desktop_surface", "liftoff_misc", "device_checks", DeviceBound.FILE, GroupsSettings.FILE, "launchpad_hub", "preview_window")
    val BACKUP_EXCLUDED_FILES: Set<String> = setOf("palette-files.idx", "crash-reports", "usage", "wallpapers", LegacyHomeBackup.FILE)

    /**
     * Files (relative to the app's files directory) that hold usage history and are never exported (HOM-18): the timeline
     * log of up to 5000 opens, and the recents list's removals (HOM-22). Each lives under a directory in [BACKUP_EXCLUDED_FILES], so system backup skips it too.
     */
    val NEVER_EXPORTED_FILES: Set<String> = setOf(TimelineLogStore.RELATIVE_PATH, RecentsStateStore.RELATIVE_PATH, NotificationHistoryStore.RELATIVE_PATH)

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
        /**
         * NFR-17: the platform's JSON parser puts the whole input after its position ("Unterminated object at character 25 of {...}").
         * The file holds names, people links and the user's last name, and this text is shown in a dialog, so only the part before the
         * input is kept.
         */
        fun describe(e: JSONException): String =
            POSITION.find(e.message ?: "")?.value ?: "Invalid JSON" // "Value Sharma of type ..." and the like quote the input: dropped

        private val POSITION = Regex("^[A-Za-z' :]{1,60} at character \\d+")

        fun parse(text: String): StateDocument {
            val o = try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw StateFormatException("Not a Liftoff state file: ${describe(e)}", e)
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
