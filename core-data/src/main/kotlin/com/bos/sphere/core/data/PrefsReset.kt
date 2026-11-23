package com.bos.sphere.core.data

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Wave 56 (settingsCore), CFG-30: resetting some of the launcher's preferences to their defaults, and finding out which ones differ.
 * The fields are those of [LauncherPrefs] (the same names the data class has), read with plain Java reflection over our own class, so a
 * preference added later is known here without a second list: the page reset lists in the app are checked against [names] by a test.
 *
 * Only [LauncherPrefs] is touched. The user's data (the home layout, hidden apps, corner and gesture assignments, Comms rules,
 * workspaces) lives in other stores and is never reached from here.
 */
object PrefsReset {
    private val ctor: Constructor<*> by lazy { LauncherPrefs::class.java.constructors.filter { !it.isSynthetic }.maxByOrNull { it.parameterCount }!! }

    private val fields: List<Field> by lazy {
        LauncherPrefs::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }.take(ctor.parameterCount)
            .onEach { it.isAccessible = true }
    }

    /** Every preference field of [LauncherPrefs], in declaration order. */
    val names: List<String> by lazy { fields.map { it.name } }

    /** The fields in which [a] and [b] differ. */
    fun diff(a: LauncherPrefs, b: LauncherPrefs): Set<String> = fields.filter { it.get(a) != it.get(b) }.mapTo(LinkedHashSet()) { it.name }

    /** [cur] with the fields named in [reset] back at their default values; every other field keeps its value. Unknown names are ignored. */
    fun reset(cur: LauncherPrefs, reset: Set<String>): LauncherPrefs {
        if (reset.isEmpty()) return cur
        val def = LauncherPrefs()
        val args = fields.map { f -> if (f.name in reset) f.get(def) else f.get(cur) }.toTypedArray()
        return ctor.newInstance(*args) as LauncherPrefs
    }

    /** True when every field named in [fields] is at its default value in [p]. */
    fun isDefault(p: LauncherPrefs, names: Set<String>): Boolean = diff(p, LauncherPrefs()).none { it in names }
}
