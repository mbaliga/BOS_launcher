package com.bos.sphere.feature.grid

import androidx.compose.ui.geometry.Offset
import com.bos.sphere.core.catalog.DropOntoIntent
import com.bos.sphere.core.catalog.SpanGrid
import com.bos.sphere.core.catalog.TilePacker
import com.bos.sphere.core.data.HomePage

/*
 * HOM-08 / HOM-04: the glue between GridHome's slot geometry and the shared tile model. Pages that
 * hold spanning widgets are laid out with [TilePacker]; every other page keeps the plain slot order.
 */

internal fun spanPacked(page: HomePage, geom: PageGeometry, extras: HomeExtras?): TilePacker.Packed? {
    if (extras == null || geom !is PageGeometry.Grid) return null
    if (!SpanGrid.active(page.keys, extras::spanOf)) return null
    return TilePacker.pack(page.keys, extras::spanOf, geom.g.columns, geom.g.rows)
}

/** The item under slot [slot] of [page], with widget spans taken into account. */
internal fun keyAtSlot(page: HomePage, geom: PageGeometry, slot: Int, extras: HomeExtras?): String? {
    val packed = spanPacked(page, geom, extras) ?: return page.keys.getOrNull(slot)
    return SpanGrid.keyAtCell(packed.placed, (geom as PageGeometry.Grid).g.columns, slot)
}

/** The index a dragged item takes in [restKeys] (the page without it) when dropped at ([x], [y]). */
internal fun dropIndex(geom: PageGeometry, restKeys: List<String>, dragged: String, extras: HomeExtras?, x: Float, y: Float): Int {
    val cell = geom.nearest(x, y)
    // The dragged item counts too: a spanning widget dragged alone still packs by its span (HOM-07).
    if (extras != null && geom is PageGeometry.Grid && SpanGrid.active(restKeys + dragged, extras::spanOf)) {
        return SpanGrid.insertIndexForDrag(restKeys, dragged, extras::spanOf, geom.g.columns, geom.g.rows, cell)
    }
    return cell.coerceAtMost(restKeys.size)
}

/** The item a drag ends on (drop-onto, HOM-04), or null when the drop is a plain reorder. */
internal fun dropOntoTarget(extras: HomeExtras?, base: HomePage?, geom: PageGeometry, local: Offset, dragKey: String): String? {
    if (extras == null || base == null || geom !is PageGeometry.Grid) return null
    val slot = geom.slotAt(local.x, local.y)
    if (slot < 0) return null
    val hit = keyAtSlot(base, geom, slot, extras) ?: return null
    if (extras.isWidget(hit)) return null
    val c = geom.center(slot)
    return DropOntoIntent.target(dragKey, hit, local.x - c.x, local.y - c.y, geom.iconPx, extras.state)
}

/** HOM-04: where the finger is relative to the item under it: that item, the offset from its centre and its cell size. */
internal data class DropProbe(val hitKey: String, val dx: Float, val dy: Float, val cell: Float)

internal fun dropOntoProbe(extras: HomeExtras?, base: HomePage?, geom: PageGeometry, local: Offset): DropProbe? {
    if (extras == null || base == null || geom !is PageGeometry.Grid) return null
    val slot = geom.slotAt(local.x, local.y)
    if (slot < 0) return null
    val hit = keyAtSlot(base, geom, slot, extras) ?: return null
    if (extras.isWidget(hit)) return null
    val c = geom.center(slot)
    return DropProbe(hit, local.x - c.x, local.y - c.y, geom.iconPx)
}
