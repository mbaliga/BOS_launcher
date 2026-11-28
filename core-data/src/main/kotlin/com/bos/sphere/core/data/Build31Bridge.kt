package com.bos.sphere.core.data

import org.json.JSONObject

/**
 * Reads the preference files build 31 wrote (names and keys as in [LauncherStores] and [LaunchpadSettings]) into
 * [StateParts], reusing the stores' own decoders so the result is exactly what build 31 would have shown. It
 * only ever reads: the bridge is one way.
 */
object Build31Bridge {
    const val PREFS = "launcher_prefs"
    const val LAYOUT = "home_layout"
    const val SHORTCUTS = "shortcuts"
    const val LAUNCHPAD = "launchpad_rules"
    val FILES = listOf(PREFS, LAYOUT, SHORTCUTS, LAUNCHPAD)

    /** [files] maps a preference file name to its `getAll()` contents. */
    fun fromPrefFiles(files: Map<String, Map<String, Any?>>): StateParts {
        fun sp(name: String) = MapSharedPreferences(files[name].orEmpty())
        val prefsSp = sp(PREFS)
        val prefs = LauncherPrefsStore(prefsSp).prefs.value
        // Keys this build does not read are kept, so a newer or older file loses nothing.
        val unknown = JSONObject().also { o ->
            files[PREFS].orEmpty().forEach { (k, v) -> if (k !in prefsSp.accessed && k !in StateSchema.DEVICE_BOUND_PREF_KEYS && v != null) o.put(k, v) }
        }
        val layout = HomeLayoutStore(sp(LAYOUT)).layout.value
        val sc = ShortcutStore(sp(SHORTCUTS))
        val lp = files[LAUNCHPAD].orEmpty()
        val g = sc.gestures.value
        return StateParts(
            prefs = prefs,
            layout = layout,
            corners = sc.corners.value,
            gestures = HomeGesture.entries.associateWith { g[it] },
            launchpadRules = LaunchpadJson.decodeRules(lp["rules"] as? String),
            launchpadPrefs = LaunchpadJson.decodePrefs(lp["prefs"] as? String),
            launchpadGestures = LaunchpadGestures.decode(lp["gestures"] as? String),
            extras = StateExtras(unknownPrefs = unknown),
        )
    }
}
