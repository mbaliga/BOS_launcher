package com.bos.sphere.feature.grid

import com.bos.sphere.core.catalog.CellRect
import com.bos.sphere.core.catalog.EditCollision
import com.bos.sphere.core.catalog.EditVerdict
import com.bos.sphere.core.catalog.GhostSnap
import com.bos.sphere.core.catalog.OriginFit
import com.bos.sphere.core.catalog.TilePacker
import com.bos.sphere.core.catalog.WidgetSpan
import com.bos.sphere.core.catalog.rect
import com.bos.sphere.core.data.HomePage

/**
 * Wave 45 (editHandles), HOM-12: where a dragged item lands, for the ghost and for the drop. Widgets BLOCK (a drop on another item
 * is refused), icons PUSH ASIDE (the page reflows around them, which the live preview already shows) and an icon on an icon makes a
 * folder. Pure: grid geometry comes in as numbers.
 */
internal object ItemDropMath {
    sealed interface WidgetDrop {
        /** No page to measure against (the trailing blank page): the ordinary drop applies. */
        data object Fallback : WidgetDrop
        data class Blocked(val rect: CellRect) : WidgetDrop
        data class Place(val rect: CellRect, val index: Int) : WidgetDrop
    }

    /** The cells a dragged item of [span] covers when its centre is at ([localX], [localY]) in the page area; logical columns. */
    fun snapCentre(span: WidgetSpan, localX: Float, localY: Float, originX: Float, originY: Float, pitchX: Float, pitchY: Float, cols: Int, rows: Int, mirror: Boolean): CellRect {
        val w = span.cols * pitchX; val h = span.rows * pitchY
        val phys = GhostSnap.snap(localX - w / 2f - originX, localY - h / 2f - originY, w, h, pitchX, pitchY, cols, rows)
        return if (mirror) phys.copy(col = cols - phys.cols - phys.col) else phys
    }

    fun widgetDrop(base: HomePage?, key: String, spanOf: (String) -> WidgetSpan, rect: CellRect, cols: Int, rows: Int): WidgetDrop {
        if (base == null) return WidgetDrop.Fallback
        val placed = TilePacker.pack(base.keys, spanOf, cols, rows).placed
        if (EditCollision.moveVerdict(placed, key, rect) is EditVerdict.Blocked) return WidgetDrop.Blocked(rect)
        return WidgetDrop.Place(rect, OriginFit.indexFor(base.keys, spanOf, cols, rows, key, spanOf(key), rect.col, rect.row))
    }

    /** The cell rectangle of [key] on [page] after packing (where an icon will land once the page has reflowed), or null. */
    fun landing(page: HomePage, key: String, spanOf: (String) -> WidgetSpan, cols: Int, rows: Int): CellRect? =
        TilePacker.pack(page.keys, spanOf, cols, rows).placed.firstOrNull { it.key == key }?.rect()
}
