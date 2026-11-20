package com.bos.sphere.core.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * CFG-03 (wave 20 cleanup): preference keys that earlier builds wrote and no build reads any more, removed once from the private
 * files so nothing of a retired feature lingers on a phone that upgraded through it. The list is exactly the retired features of
 * waves 15 to 19: the old background gallery (wave 15), the Minimal landing's corner-dot hint (replaced by Glance hotspots, wave 16),
 * the typed search prefixes and their wheel mapping (wave 18) and the rapid-start counter of the crash loop guard (wave 19); wave 23 adds the `networkDots` toggle that no screen ever offered; wave 38 adds the flat app drawer's `drawerStyle`, `drawerView`, `drawerSort`, `drawerRail`, `drawerRailVisible` and `drawerViewSwitch` (the app drawer is always the disc, see docs/decisions/drawerRing.md); wave 29 adds the Timeline's `panel.timeline.depth` and `panel.timeline.scheduled` of `home_state` (the filter row is gone, the Timeline has one fixed behaviour) and `switcherStyle`, `drawerShortcutsOn`, `drawerShortcuts` and `drawerShortcutsShown` (the wave 26 and 28 arc drawer settings, which the restored disc does not have). The marker file name carries the wave, so a list that grows is purged once more.
 *
 * Tolerant by construction: a file that cannot be opened, a key of any stored type and a failing edit are all skipped, never thrown.
 * `bgPicture` is not listed for `launcher_prefs`: [DeviceBound] still moves that one key and owns its lifecycle.
 */
object RetiredPrefKeys {
    val BY_FILE: Map<String, Set<String>> = mapOf(
        "launcher_prefs" to (RetiredBackgroundKeys.KEYS - "bgPicture") + setOf("minimalHints", "networkDots", "switcherStyle", "drawerShortcutsOn", "drawerShortcuts", "drawerShortcutsShown", "indInvert", "drawerStyle", "drawerView", "drawerSort", "drawerRail", "drawerRailVisible", "drawerViewSwitch"),
        "palette" to setOf("scopeMapping", "prefixes"),
        "crash_loop" to setOf("rapid"),
        "home_state" to setOf("panel.timeline.depth", "panel.timeline.scheduled"),
    )

    /**
     * Wave 35: `indEffects` (the old effect set) is retired too, but it is the source of the one-time migration to `indCalm` / `indAlert`; it is only removed once
     * those exist (the store migrates and removes it itself at start), so a purge that runs first never loses the user's choice.
     */
    val AFTER_MIGRATION: Map<String, Map<String, String>> = mapOf("launcher_prefs" to mapOf("indEffects" to "indCalm"))

    /** Keys an imported settings file may still carry from those builds; they are dropped, never kept as unknown fields. */
    val DROPPED_ON_IMPORT: Set<String> = RetiredBackgroundKeys.KEYS + setOf("minimalHints", "networkDots", "switcherStyle", "drawerShortcutsOn", "drawerShortcuts", "drawerShortcutsShown", "indInvert", "drawerStyle", "drawerView", "drawerSort", "drawerRail", "drawerRailVisible", "drawerViewSwitch")

    /** Removes every retired key present in the files [open] returns; the number of keys removed. Never throws. */
    fun purge(open: (String) -> SharedPreferences?): Int {
        var removed = 0
        for ((file, keys) in BY_FILE) {
            runCatching {
                val sp = open(file) ?: return@runCatching
                val after = AFTER_MIGRATION[file].orEmpty()
                val present = keys.filter { sp.contains(it) } + after.filter { (old, proof) -> sp.contains(old) && sp.contains(proof) }.keys
                if (present.isEmpty()) return@runCatching
                sp.edit().also { e -> present.forEach { e.remove(it) } }.apply()
                removed += present.size
            }
        }
        return removed
    }

    /** One time per install: a marker file in the no-backup directory (never exported) says the purge ran. Call off the main thread. */
    fun purgeOnce(marker: File, open: (String) -> SharedPreferences?): Int {
        if (runCatching { marker.exists() }.getOrDefault(false)) return 0
        val n = purge(open)
        runCatching { marker.parentFile?.mkdirs(); marker.writeText("1") }
        return n
    }

    fun purgeOnce(context: Context): Int {
        val app = context.applicationContext
        return purgeOnce(File(app.noBackupFilesDir, "retired_pref_keys_w38")) { name -> app.getSharedPreferences(name, Context.MODE_PRIVATE) }
    }
}
