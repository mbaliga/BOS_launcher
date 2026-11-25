package com.bos.sphere.ui

import android.content.Context
import com.bos.sphere.core.data.OpenSurfaceStore
import com.bos.sphere.core.data.TolerantPrefs
import com.bos.sphere.feature.grid.SettingsTabMemory

/**
 * Wave 57 (settingsTabs): the Settings section shown last, kept in the launcher's small state file (`liftoff_misc`, key `settingsTab`, the English
 * tab key). It is a convenience, not a setting: the file is already outside the backup. The process copy answers first, so only the first
 * opening of Settings after a start reads the (pre-warmed) file.
 */
class PrefsTabMemory(context: Context) : SettingsTabMemory {
    private val prefs = TolerantPrefs.of(context.applicationContext, OpenSurfaceStore.FILE)

    override fun get(): String? = SettingsTabMemory.Process.get() ?: runCatching { prefs.getString(KEY, null) }.getOrNull()?.also { SettingsTabMemory.Process.set(it) }

    override fun set(key: String) {
        if (SettingsTabMemory.Process.get() == key) return
        SettingsTabMemory.Process.set(key)
        prefs.edit().putString(KEY, key).apply()
    }

    companion object { const val KEY = "settingsTab" }
}
