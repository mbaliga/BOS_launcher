package com.bos.sphere.core.catalog

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User tags per app (HOM-05), kept in SharedPreferences. */
class TagStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("home_tags", Context.MODE_PRIVATE)
    private val _tags = MutableStateFlow(TagCodec.decode(sp.getString("tags", "") ?: ""))
    val tags: StateFlow<Map<String, Set<String>>> = _tags.asStateFlow()

    fun toggle(appKey: String, tag: String) {
        val t = tag.trim()
        if (t.isEmpty()) return
        _tags.value = TagCodec.toggle(_tags.value, appKey, t)
        sp.edit().putString("tags", TagCodec.encode(_tags.value)).apply()
    }
}

/** Per-item icon overrides and the chosen icon pack (HOM-03). */
class IconOverrideStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("home_icons", Context.MODE_PRIVATE)
    private val _overrides = MutableStateFlow(IconOverrideCodec.decode(sp.getString("overrides", "") ?: ""))
    val overrides: StateFlow<Map<String, String>> = _overrides.asStateFlow()
    private val _pack = MutableStateFlow(sp.getString("pack", null))
    val pack: StateFlow<String?> = _pack.asStateFlow()

    fun setOverride(itemKey: String, spec: String?) {
        _overrides.value = if (spec == null) _overrides.value - itemKey else _overrides.value + (itemKey to spec)
        sp.edit().putString("overrides", IconOverrideCodec.encode(_overrides.value)).apply()
    }

    private val _themed = MutableStateFlow(sp.getBoolean("themed", false))
    /** Themed (monochrome) icons on or off (HOM-03). */
    val themed: StateFlow<Boolean> = _themed.asStateFlow()
    fun setThemed(on: Boolean) {
        _themed.value = on
        sp.edit().putBoolean("themed", on).apply()
    }

    fun setPack(packageName: String?) {
        _pack.value = packageName
        sp.edit().putString("pack", packageName).apply()
    }
}

/** Badge display options (HOM-09): dots only, or dots with a count. */
class BadgeSettings(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("home_badges", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(v) { sp.edit().putBoolean("enabled", v).apply() }
    var showCount: Boolean
        get() = sp.getBoolean("count", true)
        set(v) { sp.edit().putBoolean("count", v).apply() }
}

/**
 * The home arrangement beyond the plain page list (folders, shortcuts, widgets, dock), one entry
 * per size class (HOM-11). Survives process death through [HomeStateCodec].
 */
class HomeStateStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("home_state", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val arrangements: StateFlow<SizeClassArrangements> = _state.asStateFlow()

    fun save(sc: SizeClass, state: HomeState) {
        _state.value = _state.value.with(sc, state)
        sp.edit().putString(PREFIX + sc.key, HomeStateCodec.encode(state)).apply()
    }

    private fun read(): SizeClassArrangements {
        var a = SizeClassArrangements()
        for ((k, v) in sp.all) {
            if (!k.startsWith(PREFIX) || v !is String) continue
            val parts = k.removePrefix(PREFIX).split('-')
            val w = SizeBucket.entries.firstOrNull { it.name.lowercase() == parts.getOrNull(0) } ?: continue
            val h = SizeBucket.entries.firstOrNull { it.name.lowercase() == parts.getOrNull(1) } ?: continue
            a = a.with(SizeClass(w, h), HomeStateCodec.decode(v))
        }
        return a
    }

    private companion object { const val PREFIX = "state:" }
}
