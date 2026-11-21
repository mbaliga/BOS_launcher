package com.bos.sphere.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the set of hidden app keys ([AppEntry.key]) so hiding survives restarts.
 * Backed by [android.content.SharedPreferences] — zero extra dependencies, fine for a small set.
 * Exposes a [hidden] [StateFlow] the surface filters against live.
 */
class HiddenAppsStore(private val context: Context) {

    private val prefs =
        TolerantPrefs.of(context.applicationContext, "hidden_apps")

    private val _hidden = MutableStateFlow(prefs.getStringSet(KEY, emptySet())!!.toSet())
    val hidden: StateFlow<Set<String>> = _hidden.asStateFlow()

    fun isHidden(key: String): Boolean = key in _hidden.value

    fun setHidden(key: String, hidden: Boolean) {
        if (hidden && key !in _hidden.value) DestructiveEdits.before("hide app")
        val next = _hidden.value.toMutableSet().apply {
            if (hidden) add(key) else remove(key)
        }
        val changed = (key in _hidden.value) != hidden
        _hidden.value = next
        // Wave 41: the Timeline shows the user's hide / unhide; the key is "component#user", its package part names the app.
        if (changed) { TimelineEvents.init(context); TimelineEvents.record(if (hidden) TimelineKind.AppHidden else TimelineKind.AppUnhidden, key.substringBefore('/').substringBefore('#')) }
        prefs.edit().putStringSet(KEY, next).apply()
    }

    private companion object {
        const val KEY = "keys"
    }
}
