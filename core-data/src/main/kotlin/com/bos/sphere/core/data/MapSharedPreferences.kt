package com.bos.sphere.core.data

import android.content.SharedPreferences

/**
 * An in-memory [SharedPreferences] over a plain map. It lets the existing stores' own encode and decode logic
 * ([LauncherPrefsStore]) turn preferences into a key map and back, so the state file covers every setting without a
 * second field list that could drift. Number getters are lenient (JSON gives back Int, Long or Double for what was a
 * Float), and every key read is recorded in [accessed] so unknown keys can be told apart and preserved.
 */
class MapSharedPreferences(initial: Map<String, Any?> = emptyMap()) : SharedPreferences {
    private val data = LinkedHashMap<String, Any?>().apply { initial.forEach { (k, v) -> if (v != null) put(k, v) } }
    private val _accessed = LinkedHashSet<String>()

    /** Keys any getter or [contains] asked about. */
    val accessed: Set<String> get() = _accessed

    override fun getAll(): MutableMap<String, *> = LinkedHashMap(data)

    override fun getString(key: String, defValue: String?): String? { _accessed += key; return data[key] as? String ?: defValue }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        _accessed += key
        return (data[key] as? Set<String>)?.toMutableSet() ?: defValues
    }

    override fun getInt(key: String, defValue: Int): Int { _accessed += key; return (data[key] as? Number)?.toInt() ?: defValue }
    override fun getLong(key: String, defValue: Long): Long { _accessed += key; return (data[key] as? Number)?.toLong() ?: defValue }
    override fun getFloat(key: String, defValue: Float): Float { _accessed += key; return (data[key] as? Number)?.toFloat() ?: defValue }
    override fun getBoolean(key: String, defValue: Boolean): Boolean { _accessed += key; return data[key] as? Boolean ?: defValue }
    override fun contains(key: String): Boolean { _accessed += key; return data.containsKey(key) }

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = LinkedHashMap<String, Any?>()
        private val removed = LinkedHashSet<String>()
        private var clear = false
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removed += key }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) data.clear()
            removed.forEach { data.remove(it) }
            pending.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
        }
    }

    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}
