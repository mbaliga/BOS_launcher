package com.bos.sphere.core.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * CFG-03 (wave 20 cleanup): preference keys that earlier builds wrote and no build reads any more, removed once from the private
 * files so nothing of a retired feature lingers on a phone that upgraded through it. The list is exactly the retired features of
 * waves 15 to 19: the old background gallery (wave 15), the Minimal landing's corner-dot hint (replaced by Glance hotspots, wave 16),
 * the typed search prefixes and their wheel mapping (wave 18) and the rapid-start counter of the crash loop guard (wave 19); wave 23 adds the `networkDots` toggle that no screen ever offered. The marker file name carries the wave, so a list that grows is purged once more.
 *
 * Tolerant by construction: a file that cannot be opened, a key of any stored type and a failing edit are all skipped, never thrown.
 * `bgPicture` is not listed for `launcher_prefs`: [DeviceBound] still moves that one key and owns its lifecycle.
 */
object RetiredPrefKeys {
    val BY_FILE: Map<String, Set<String>> = mapOf(
        "launcher_prefs" to (RetiredBackgroundKeys.KEYS - "bgPicture") + setOf("minimalHints", "networkDots"),
        "palette" to setOf("scopeMapping", "prefixes"),
        "crash_loop" to setOf("rapid"),
    )

    /** Keys an imported settings file may still carry from those builds; they are dropped, never kept as unknown fields. */
    val DROPPED_ON_IMPORT: Set<String> = RetiredBackgroundKeys.KEYS + setOf("minimalHints", "networkDots")

    /** Removes every retired key present in the files [open] returns; the number of keys removed. Never throws. */
    fun purge(open: (String) -> SharedPreferences?): Int {
        var removed = 0
        for ((file, keys) in BY_FILE) {
            runCatching {
                val sp = open(file) ?: return@runCatching
                val present = keys.filter { sp.contains(it) }
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
        return purgeOnce(File(app.noBackupFilesDir, "retired_pref_keys_w23")) { name -> app.getSharedPreferences(name, Context.MODE_PRIVATE) }
    }
}
