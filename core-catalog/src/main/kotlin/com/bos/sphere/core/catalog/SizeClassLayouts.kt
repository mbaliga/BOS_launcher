package com.bos.sphere.core.catalog

import android.content.Context
import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomeLayoutCodec

/**
 * HOM-11: one stored page arrangement ([HomeLayout]) per window size class, so rotating, folding
 * or resizing never rewrites another size's layout, and a class seen for the first time starts
 * from a copy of the nearest known one (positions are kept, nothing is lost).
 */
data class LayoutsBySizeClass(val byClass: Map<String, HomeLayout> = emptyMap(), val classes: Map<String, SizeClass> = emptyMap()) {

    /** Records [layout] as the arrangement of [sc] only. */
    fun with(sc: SizeClass, layout: HomeLayout) = copy(byClass = byClass + (sc.key to layout), classes = classes + (sc.key to sc))

    /**
     * Switches to [sc]: [current] (what is on screen, belonging to [previous] when given) is stored
     * under the previous class, then the arrangement for [sc] is returned, seeded from the nearest
     * class (or [current]) when [sc] has none yet.
     */
    fun switchTo(sc: SizeClass, current: HomeLayout, previous: SizeClass?): Pair<HomeLayout, LayoutsBySizeClass> {
        var next = if (previous != null && previous != sc) with(previous, current) else this
        next.byClass[sc.key]?.let { return it to next }
        val seed = next.classes.values.minByOrNull { it.distanceTo(sc) }?.let { next.byClass[it.key] } ?: current
        next = next.with(sc, seed)
        return seed to next
    }
}

/** Persistence of [LayoutsBySizeClass] (SharedPreferences, one entry per class). */
class SizeClassLayoutStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("home_layout_by_class", Context.MODE_PRIVATE)
    /** NFR-18: real widget ids live in the device file; the backed-up file holds re-add markers ([com.bos.sphere.core.data.WidgetIdSplit]). */
    private val device = context.applicationContext.getSharedPreferences(com.bos.sphere.core.data.DeviceBound.FILE, Context.MODE_PRIVATE)
    private var data: LayoutsBySizeClass = read()

    /** Makes [sc] active: saves [current] under [previous], returns the layout to show. */
    fun activate(sc: SizeClass, current: HomeLayout, previous: SizeClass?): HomeLayout {
        val (layout, next) = data.switchTo(sc, current, previous)
        data = next
        persist(sc.key); previous?.let { persist(it.key) }
        return layout
    }

    /** Keeps the stored copy of the active class current while the user edits. */
    fun record(sc: SizeClass, layout: HomeLayout) {
        data = data.with(sc, layout)
        persist(sc.key)
    }

    private fun persist(key: String) {
        val l = data.byClass[key] ?: return
        com.bos.sphere.core.data.WidgetIdSplit.putString(sp, device, "home_layout_by_class", "pages:$key", HomeLayoutCodec.encode(l.pages))
        com.bos.sphere.core.data.WidgetIdSplit.putStringSet(sp, device, "home_layout_by_class", "removed:$key", l.removed)
    }

    private fun read(): LayoutsBySizeClass {
        var a = LayoutsBySizeClass()
        for ((k, v) in sp.all.toMap()) {
            if (!k.startsWith("pages:") || v !is String) continue
            val key = k.removePrefix("pages:")
            val parts = key.split('-')
            val w = SizeBucket.entries.firstOrNull { it.name.lowercase() == parts.getOrNull(0) } ?: continue
            val h = SizeBucket.entries.firstOrNull { it.name.lowercase() == parts.getOrNull(1) } ?: continue
            val removed = com.bos.sphere.core.data.WidgetIdSplit.getStringSet(sp, device, "home_layout_by_class", "removed:$key")
            val pages = com.bos.sphere.core.data.WidgetIdSplit.getString(sp, device, "home_layout_by_class", k)
            a = a.with(SizeClass(w, h), HomeLayout(HomeLayoutCodec.decode(pages), removed, seeded = true))
        }
        return a
    }
}
