package com.bos.sphere

import android.content.Context

/**
 * NFR-02 / NFR-12: SharedPreferences files load on first touch, and a main-thread read that arrives before the load
 * finishes blocks on the disk (StrictMode counts it). Touching the files that home needs on a background thread from
 * `Application.onCreate` starts those loads before the activity asks, so by then they are normally in memory.
 * Files are only read here, never written, and the thread is a daemon that ends when done.
 */
object PrefsWarmup {
    /** Preference files read while home is built (see LauncherStores, HiddenAppsStore, CrashLoopGuard). */
    val FILES = listOf("launcher_prefs", "home_layout", "shortcuts", "launch_history", "icon_colors", "hidden_apps", "crash_loop",
        "liftoff_misc", "theming", "home_tags", "home_icons", "home_badges", "home_state", "palette", "desktop_surface",
        // The left-edge waiting indicator is composed with the first home frame and reads the Launchpad rules and groups (STR-09, STR-13).
        "launchpad_rules", "launchpad_groups",
        // Wave 58: LauncherStores reads the kept theme undo at construction.
        "theme_undo")

    fun start(context: Context) {
        val app = context.applicationContext
        Thread {
            FILES.forEach { name -> runCatching { app.getSharedPreferences(name, Context.MODE_PRIVATE).all } }
            // CFG-03: retired keys of waves 15 to 19 are removed once, off the main thread, after the files are loaded.
            runCatching { com.bos.sphere.core.data.RetiredPrefKeys.purgeOnce(app) }
        }.apply { this.name = "liftoff-prefs-warmup"; isDaemon = true }.start()
    }
}
