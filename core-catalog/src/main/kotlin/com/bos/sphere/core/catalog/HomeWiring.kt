package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.PageLayout
import com.bos.sphere.core.data.PageShape

/*
 * Wave 2 home wiring: the pure pieces the live composables and the app host call. Everything here
 * is plain Kotlin so it is covered by JVM tests (HOM-01 to HOM-08).
 */

/** A per-item icon override spec: a drawable of an installed icon pack. */
object IconSpec {
    private const val PREFIX = "pack:"
    fun encode(packPackage: String, drawable: String) = "$PREFIX$packPackage/$drawable"
    fun decode(spec: String): Pair<String, String>? {
        if (!spec.startsWith(PREFIX)) return null
        val body = spec.removePrefix(PREFIX)
        val i = body.indexOf('/')
        if (i <= 0 || i == body.lastIndex) return null
        return body.substring(0, i) to body.substring(i + 1)
    }

    /** A signature that changes whenever what an item's icon shows could change (used in the bitmap cache key). */
    fun signature(source: IconSource): String = when (source) {
        is IconSource.Override -> "o:" + source.spec
        is IconSource.Pack -> "p:" + source.packName + "/" + source.drawable
        IconSource.Themed -> "t"
        IconSource.Adaptive -> "a"
        IconSource.Legacy -> "l"
    }
}

/** HOM-08: the span-aware slot model of one grid page (widgets share the tile layout with app tiles). */
object SpanGrid {
    private val ONE = WidgetSpan(1, 1)

    fun active(keys: List<String>, spanOf: (String) -> WidgetSpan): Boolean = keys.any { spanOf(it) != ONE }

    fun cellOf(p: TilePlacement, cols: Int): Int = p.row * cols + p.col

    /** The item covering [cell] (row-major index), or null when it is empty. */
    fun keyAtCell(placed: List<TilePlacement>, cols: Int, cell: Int): String? {
        if (cols < 1 || cell < 0) return null
        val c = cell % cols
        val r = cell / cols
        return placed.firstOrNull { c >= it.col && c < it.col + it.span.cols && r >= it.row && r < it.row + it.span.rows }?.key
    }

    /**
     * The insertion index in [keys] for a drop that lands on [cell]: the number of the other items
     * that start before it, so the dragged item takes the place the packing would give it.
     */
    fun insertIndex(keys: List<String>, dragged: String, spanOf: (String) -> WidgetSpan, cols: Int, rows: Int, cell: Int): Int {
        val rest = keys.filter { it != dragged }
        val packed = TilePacker.pack(rest, spanOf, cols, rows)
        val before = packed.placed.count { cellOf(it, cols) < cell }
        return before.coerceIn(0, rest.size)
    }
}

/**
 * HOM-08: a page that holds spanning widgets can hold fewer items than its slot count. This splits
 * such a grid page into as many visual pages (same layout, same id, so they merge back on save) as
 * the packing needs, so no item is ever packed off the page and hidden.
 */
object SpanReflow {
    fun reflow(pages: List<HomePage>, spanOf: (String) -> WidgetSpan, dimsOf: (PageLayout) -> Pair<Int, Int>): List<HomePage> =
        pages.flatMap { page ->
            if (page.layout.shape != PageShape.Grid || !SpanGrid.active(page.keys, spanOf)) return@flatMap listOf(page)
            val (cols, rows) = dimsOf(page.layout)
            val out = ArrayList<HomePage>()
            var cur = ArrayList<String>()
            for (k in page.keys) {
                if (cur.isNotEmpty() && TilePacker.pack(cur + k, spanOf, cols, rows).overflow.isNotEmpty()) {
                    out += page.copy(keys = cur)
                    cur = ArrayList()
                }
                cur += k
            }
            if (cur.isNotEmpty() || out.isEmpty()) out += page.copy(keys = cur)
            out
        }
}

/** HOM-04: where a drag ends decides between "reorder" and "drop onto an item". */
object DropOntoIntent {
    /**
     * [dx], [dy] are the finger's offset from the centre of the item under it, [cell] the cell's
     * shorter edge. Within [ZONE] of the edge length from the centre the drop means "onto".
     */
    const val ZONE = 0.28f

    fun isOnto(dx: Float, dy: Float, cell: Float): Boolean = kotlin.math.hypot(dx, dy) <= cell * ZONE

    /** Which key (if any) the drop is on: never itself, and only items that can receive a drop. */
    fun target(dragKey: String, hitKey: String?, dx: Float, dy: Float, cell: Float, state: HomeState): String? {
        val k = hitKey ?: return null
        if (k == dragKey || !isOnto(dx, dy, cell)) return null
        return when (ItemKeys.kind(k)) {
            ItemKind.App, ItemKind.Shortcut, ItemKind.Link, ItemKind.Folder -> k
            else -> null
        }.takeIf { HomeEdit.dropOnto(state, dragKey, k, "probe", "x") { 1_000 } != state }
    }
}

/** HOM-07 / HOM-02: re-identify a widget after the provider was rebound to a new id. */
object HomeRekey {
    fun widget(state: HomeState, oldKey: String, newKey: String): HomeState {
        if (oldKey == newKey) return state
        fun sw(k: String) = if (k == oldKey) newKey else k
        return state.copy(
            pages = state.pages.map { p -> p.copy(keys = p.keys.map(::sw)) },
            dock = state.dock.map(::sw),
            folders = state.folders.mapValues { (_, f) -> f.copy(keys = f.keys.map(::sw)) },
            items = state.items.mapKeys { (k, _) -> sw(k) }.mapValues { (_, d) -> if (d.key == oldKey) d.copy(key = newKey) else d },
            widgets = state.widgets.mapKeys { (k, _) -> sw(k) },
        )
    }
}

/**
 * The arrangement a page list resolves against: non-app items are available keys, and apps held
 * in folders or the dock must not be auto-placed back onto a page.
 */
object HomeResolve {
    fun available(appKeys: List<String>, extras: HomeState): List<String> = appKeys + extras.nonAppKeys()

    fun layoutFor(layout: HomeLayout, extras: HomeState): HomeLayout {
        val held = extras.folders.values.flatMap { it.keys } + extras.dock
        return if (held.isEmpty()) layout else layout.copy(removed = layout.removed + held)
    }

    /** Stored keys the save must keep although they are not drawn: apps are decided by the caller, non-app items never. */
    fun preserve(base: Set<String>, extras: HomeState, keep: Set<String> = emptySet()): Set<String> = (base - extras.nonAppKeys().toSet()) + keep
}

/** Restore at startup (HOM-07): what to do for each widget saved in the home state. */
object WidgetStartup {
    fun saved(state: HomeState): List<SavedWidget> = state.items.values
        .filter { it.kind == ItemKind.Widget }
        .mapNotNull { d -> ItemKeys.widgetId(d.key)?.let { SavedWidget(d.key, it, d.target) } }

    fun plan(state: HomeState, hostedIds: Set<Int>, installedProviders: Set<String>): List<RestoreAction> =
        WidgetRestore.plan(saved(state), hostedIds, installedProviders)
}

/** HOM-05: the hidden apps view lists what is hidden, in name order. */
object HiddenAppsView {
    fun <T> hiddenOnly(all: List<T>, keyOf: (T) -> String, labelOf: (T) -> String, hidden: Set<String>): List<T> =
        all.filter { keyOf(it) in hidden }.sortedBy { labelOf(it).lowercase() }
}
