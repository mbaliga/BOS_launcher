package com.bos.sphere.core.data

import android.content.SharedPreferences

/**
 * NFR-18: app widget ids held inside the home arrangement. A home item key `widget:<id>` names an id the system host gave
 * on this device; on another install the id means nothing or, worse, another widget. The arrangement files (`home_layout`,
 * `home_state`, `home_extras`, `home_layout_by_class`) are backed up and exported, so they must not carry the ids.
 *
 * The id is replaced by a marker: `widget:<id>` becomes `widget:-<id+1>`. A negative id is never handed out by the host, so
 * the home treats it as a widget that lost its binding and HOM-07 rebinds it from the provider recorded in the item (the
 * stack members inside a stack target are rewritten the same way). The real value goes to the device file
 * ([DeviceBound.FILE], excluded from backup and transfer) beside it, under `<file>:<key>`.
 *
 * Reading uses the device copy only while it still matches the main value (its marker form is equal to it); after an import,
 * a restore or an edit by an older build the main value wins. Older installs that stored the real ids in the main file are
 * moved across the first time a value is read ([getString], [getStringSet]).
 *
 * Stated limit: the rewrite is a text rewrite of the stored value, so a label that literally contains `widget:<digits>`
 * is rewritten in the backed-up copy too (the device copy keeps it).
 */
object WidgetIdSplit {
    private val real = Regex("""widget:(\d{1,9})(?!\d)""")

    fun hasIds(s: String): Boolean = real.containsMatchIn(s)

    /** The backup and export form of [s]: every `widget:<id>` becomes `widget:-<id+1>`. */
    fun sanitize(s: String): String = if (!s.contains("widget:")) s else real.replace(s) { "widget:-" + (it.groupValues[1].toLong() + 1) }

    fun sanitize(s: Set<String>): Set<String> = if (s.none { hasIds(it) }) s else s.mapTo(LinkedHashSet()) { sanitize(it) }

    /** True for a marker key (`widget:-<n>`), which stands for a widget that still has to be re-added. */
    fun isMarker(key: String): Boolean = key.startsWith("widget:-")

    private fun dk(file: String, key: String) = "$file:$key"

    fun getString(sp: SharedPreferences, device: SharedPreferences?, file: String, key: String): String? {
        val v = sp.getString(key, null) ?: return null
        if (device == null) return v
        if (hasIds(v)) { // written by a build that kept the ids here: move them across
            device.edit().putString(dk(file, key), v).apply()
            sp.edit().putString(key, sanitize(v)).apply()
            return v
        }
        val d = device.getString(dk(file, key), null)
        return if (d != null && sanitize(d) == v) d else v
    }

    fun putString(sp: SharedPreferences, device: SharedPreferences?, file: String, key: String, value: String) {
        if (device == null) { sp.edit().putString(key, value).apply(); return }
        val clean = sanitize(value)
        sp.edit().putString(key, clean).apply()
        if (clean != value) device.edit().putString(dk(file, key), value).apply()
        else if (device.contains(dk(file, key))) device.edit().remove(dk(file, key)).apply()
    }

    fun getStringSet(sp: SharedPreferences, device: SharedPreferences?, file: String, key: String): Set<String> {
        val v = sp.getStringSet(key, null)?.toSet() ?: return emptySet()
        if (device == null) return v
        if (v.any { hasIds(it) }) {
            device.edit().putStringSet(dk(file, key), v).apply()
            sp.edit().putStringSet(key, sanitize(v)).apply()
            return v
        }
        val d = device.getStringSet(dk(file, key), null)?.toSet()
        return if (d != null && sanitize(d) == v) d else v
    }

    fun putStringSet(sp: SharedPreferences, device: SharedPreferences?, file: String, key: String, value: Set<String>) {
        if (device == null) { sp.edit().putStringSet(key, value).apply(); return }
        val clean = sanitize(value)
        sp.edit().putStringSet(key, clean).apply()
        if (clean != value) device.edit().putStringSet(dk(file, key), value).apply()
        else if (device.contains(dk(file, key))) device.edit().remove(dk(file, key)).apply()
    }

    /** Files whose values hold home item keys, and so are rewritten on export and import ([PrefFilesSource]). */
    val ARRANGEMENT_FILES: Set<String> = setOf("home_state", "home_extras", "home_layout_by_class")
}
