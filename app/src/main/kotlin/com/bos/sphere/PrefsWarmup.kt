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
        "liftoff_misc", "theming", "home_tags", "home_icons", "home_badges", "home_state", "palette", "desktop_surface")

    fun start(context: Context) {
        val app = context.applicationContext
        Thread {
            FILES.forEach { name -> runCatching { app.getSharedPreferences(name, Context.MODE_PRIVATE).all } }
        }.apply { this.name = "liftoff-prefs-warmup"; isDaemon = true }.start()
    }
}
