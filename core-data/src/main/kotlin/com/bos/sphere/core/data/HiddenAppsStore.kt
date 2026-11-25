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
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    companion object {
        /**
         * The SharedPreferences file. Sphere's home-screen widgets watch it for changes (each store
         * instance has its own [hidden] flow, so the file is the one signal every reader shares).
         */
        const val PREFS_NAME = "hidden_apps"
        private const val KEY = "keys"
    }
}
