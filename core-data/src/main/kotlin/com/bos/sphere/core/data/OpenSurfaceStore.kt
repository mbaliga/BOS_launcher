package com.bos.sphere.core.data

import android.content.SharedPreferences

/** The home surfaces that can be reopened after a restart (NFR-11). Settings and menus are never restored: they hold edits in progress. */
enum class OpenSurface { Drawer, Search }

/**
 * NFR-11: opt-in restore of the surface that was open when the process ended. The launcher records the open drawer or
 * search screen here; at the next cold start, and only when the user switched [LauncherPrefs.restoreOpenSurfaces] on, the
 * home opens it again. Off by default (an owner decision: Home always starts on the home screen unless asked). Never applies
 * in safe mode, and applies once per process, so a configuration change does not reopen a surface the user closed.
 *
 * Stored in `liftoff_misc` (excluded from backup and export: what was open is a moment, not setup).
 */
class OpenSurfaceStore(private val sp: SharedPreferences) {
    private var consumed = false

    fun record(surface: OpenSurface?) {
        val raw = surface?.name ?: ""
        if (sp.getString(KEY, "") != raw) sp.edit().putString(KEY, raw).apply()
    }

    fun last(): OpenSurface? = sp.getString(KEY, "")?.let { n -> OpenSurface.entries.firstOrNull { it.name == n } }

    /** The surface to open at start: null when the option is off, in safe mode, already applied in this process, or nothing was open. */
    @Synchronized
    fun takeRestore(enabled: Boolean, safeMode: Boolean): OpenSurface? {
        if (consumed) return null
        consumed = true
        return if (enabled && !safeMode) last() else null
    }

    companion object {
        const val FILE = "liftoff_misc"
        private const val KEY = "openSurface"
        @Volatile private var shared: OpenSurfaceStore? = null
        fun get(context: android.content.Context): OpenSurfaceStore = shared ?: synchronized(this) {
            shared ?: OpenSurfaceStore(TolerantPrefs.of(context.applicationContext, FILE)).also { shared = it }
        }
    }
}
