package com.bos.sphere.feature.grid

import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.ItemKind

/**
 * HOM-01: non-app home items (folders, shortcuts, links, files) are rows of the same list as the
 * apps on List, XMB, DSi and the continuous grid, not a side strip. Widgets stay in the widget band.
 * Pure placement rules; the composables only draw what these return.
 */
sealed interface HomeRow<out A> {
    val key: String
    data class App<A>(val app: A, override val key: String) : HomeRow<A>
    data class Extra(override val key: String) : HomeRow<Nothing>
}

object HomeInterleave {
    /** The non-app, non-widget keys in [topLevel] order that [owns] says belong to the home extras. */
    fun extraKeys(topLevel: List<String>, owns: (String) -> Boolean): List<String> =
        topLevel.filter { owns(it) && ItemKeys.kind(it) != ItemKind.Widget }.distinct()

    /**
     * Apps keep their given order. Each extra goes in front of the first app that follows it in
     * [topLevel] (the user's arrangement); an extra with no app after it goes to the end, in order.
     */
    fun <A> merge(apps: List<A>, keyOf: (A) -> String, extras: List<String>, topLevel: List<String>): List<HomeRow<A>> {
        if (extras.isEmpty()) return apps.map { HomeRow.App(it, keyOf(it)) }
        val appKeys = apps.map(keyOf).toHashSet()
        val extraSet = extras.toHashSet()
        val before = LinkedHashMap<String, MutableList<String>>()
        val tail = ArrayList<String>()
        val pending = ArrayList<String>()
        for (k in topLevel) {
            when {
                k in extraSet && k !in pending -> pending += k
                k in appKeys -> if (pending.isNotEmpty()) { before.getOrPut(k) { ArrayList() }.addAll(pending); pending.clear() }
            }
        }
        tail += pending
        // Extras that are not in topLevel at all still show (end of the list).
        tail += extras.filter { it !in topLevel }
        val out = ArrayList<HomeRow<A>>(apps.size + extras.size)
        for (a in apps) {
            before[keyOf(a)]?.forEach { out += HomeRow.Extra(it) }
            out += HomeRow.App(a, keyOf(a))
        }
        tail.forEach { out += HomeRow.Extra(it) }
        return out
    }

    /**
     * List home: A to Z. Apps and extras sort together by label, each under its letter header.
     * [labelOf] gives an extra's label.
     */
    fun <A> alphabetical(apps: List<A>, keyOf: (A) -> String, labelOf: (A) -> String, extras: List<String>, extraLabel: (String) -> String): List<HomeRow<A>> {
        val all = apps.map { Triple(labelOf(it).lowercase(), 0, HomeRow.App(it, keyOf(it)) as HomeRow<A>) } +
            extras.map { Triple(extraLabel(it).lowercase(), 1, HomeRow.Extra(it) as HomeRow<A>) }
        return all.sortedWith(compareBy<Triple<String, Int, HomeRow<A>>> { it.first }.thenBy { it.second }).map { it.third }
    }
}
