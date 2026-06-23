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
class HiddenAppsStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("hidden_apps", Context.MODE_PRIVATE)

    private val _hidden = MutableStateFlow(prefs.getStringSet(KEY, emptySet())!!.toSet())
    val hidden: StateFlow<Set<String>> = _hidden.asStateFlow()

    fun isHidden(key: String): Boolean = key in _hidden.value

    fun setHidden(key: String, hidden: Boolean) {
        val next = _hidden.value.toMutableSet().apply {
            if (hidden) add(key) else remove(key)
        }
        _hidden.value = next
        prefs.edit().putStringSet(KEY, next).apply()
    }

    private companion object {
        const val KEY = "keys"
    }
}
