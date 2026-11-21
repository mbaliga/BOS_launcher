package com.bos.sphere.feature.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * HOM-13: real app widgets on the desktop surface. The surface keeps placement (position, snap guides, non-overlap)
 * and persistence in its own per-display profile; drawing a widget needs the launcher's widget host, which lives in the
 * home module, so the app plugs it in through [DesktopWidgetSlot] (the same pattern as [DesktopDockSlot]).
 */
interface DesktopWidgetHost {
    /** The live widget view for [appWidgetId], filling [modifier]. */
    @Composable fun Content(appWidgetId: Int, modifier: Modifier)

    /**
     * Remembers the picker, bind and configure flow and returns the function that starts it. [onAdded] gets the bound
     * widget's id and the provider's minimum size in dp.
     */
    @Composable fun rememberAdder(onAdded: (appWidgetId: Int, minWidthDp: Float, minHeightDp: Float) -> Unit): () -> Unit

    /** True while [appWidgetId] is still bound to an installed provider. */
    fun isBound(appWidgetId: Int): Boolean

    /** Frees [appWidgetId] when its desktop item is removed. */
    fun release(appWidgetId: Int)

    /** What the provider allows when the widget is resized after it is placed; null when unknown (any size from one cell up). */
    fun resizeLimits(appWidgetId: Int, density: Float): ResizeLimits? = null
}

/**
 * HOM-13: a provider's resize rules in dp: the smallest size it supports (`minResizeWidth` / `minResizeHeight`, else its minimum)
 * and which axes may change at all (`resizeMode`).
 */
data class ResizeLimits(val minWidthDp: Float = 0f, val minHeightDp: Float = 0f, val horizontal: Boolean = true, val vertical: Boolean = true)

object DesktopWidgetSlot {
    @Volatile var provider: DesktopWidgetHost? = null
}

/** HOM-13: ids and placement rules for hosted widgets on the desktop. Pure, so they are tested without a display. */
object DesktopWidgets {
    private const val PREFIX = "aw:"

    fun id(appWidgetId: Int) = PREFIX + appWidgetId
    fun appWidgetId(itemId: String): Int? = itemId.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.toIntOrNull()
    fun isHosted(item: PlacedItem) = item.isWidget && appWidgetId(item.id) != null

    /** The hosted widgets of [profile] as app widget ids. */
    fun hostedIds(profile: DesktopProfile): List<Int> = profile.items.mapNotNull { if (it.isWidget) appWidgetId(it.id) else null }

    /**
     * The size a provider's minimum becomes on the desktop: whole cells (at least one, at most the work area), so a widget
     * lines up with the icon grid. All values in layout dp.
     */
    fun sizeFor(minWidthDp: Float, minHeightDp: Float, cellDp: Float, container: FRect): Pair<Float, Float> {
        val cell = cellDp.coerceAtLeast(1f)
        fun axis(minDp: Float, max: Float): Float {
            val wanted = kotlin.math.ceil((minDp / cell).toDouble()).toInt().coerceAtLeast(1)
            val fits = kotlin.math.floor((max / cell).toDouble()).toInt().coerceAtLeast(1)
            return minOf(wanted, fits) * cell
        }
        return axis(minWidthDp, container.width) to axis(minHeightDp, container.height)
    }

    /**
     * Adds [appWidgetId] to [profile] at the top-left of [container] or the nearest free spot (snapped, never overlapping another
     * widget). Null when the widget fits nowhere; the caller then releases the id.
     */
    fun add(profile: DesktopProfile, appWidgetId: Int, w: Float, h: Float, container: FRect, threshold: Float = 6f): DesktopProfile? {
        val id = id(appWidgetId)
        if (profile.items.any { it.id == id }) return profile
        val wanted = FRect(container.left + 8f, container.top + 8f, container.left + 8f + w, container.top + 8f + h)
        val others = profile.items.filter { it.isWidget }.map { it.rect() }
        val placed = FreeformPlacement.placeWidget(wanted, others, container, threshold) ?: return null
        return profile.copy(items = profile.items + PlacedItem(PlacedItem.KIND_WIDGET, id, placed.rect.left, placed.rect.top, w, h))
    }

    /** Removes the item with [itemId]; the widget's id (if hosted) is the caller's to release. */
    fun remove(profile: DesktopProfile, itemId: String): DesktopProfile = profile.copy(items = profile.items.filter { it.id != itemId })

    /** Moves widget [itemId] to [rect]'s corner with snapping and non-overlap against the other widgets; unchanged when it cannot be placed. */
    fun move(profile: DesktopProfile, itemId: String, rect: FRect, container: FRect, threshold: Float = 6f): Pair<DesktopProfile, List<Guide>> {
        val others = profile.items.filter { it.isWidget && it.id != itemId }.map { it.rect() }
        val res = FreeformPlacement.placeWidget(rect, others, container, threshold) ?: return profile to emptyList()
        return profile.copy(items = profile.items.map { if (it.id == itemId) it.copy(x = res.rect.left, y = res.rect.top) else it }) to res.guides
    }

    /** Widget item [itemId] of [profile], if there is one. */
    fun widgetItem(profile: DesktopProfile, itemId: String): PlacedItem? = profile.items.firstOrNull { it.id == itemId && it.isWidget }

    /**
     * HOM-13, resize after placement. The new box keeps the widget's top-left corner and snaps to whole cells: it is as close to
     * [wantedW] x [wantedH] as the rules allow. A change is limited to what the provider supports ([limits]: smallest size, axes),
     * at least one cell, inside [container], and never over another widget; when the wanted size would overlap a neighbour or leave
     * the work area, the largest size that does not is used (shrunk a cell at a time). Null when nothing different is possible.
     */
    fun resizedRect(profile: DesktopProfile, itemId: String, wantedW: Float, wantedH: Float, cell: Float, container: FRect, limits: ResizeLimits? = null): FRect? {
        val item = widgetItem(profile, itemId) ?: return null
        val c = cell.coerceAtLeast(1f)
        val others = profile.items.filter { it.isWidget && it.id != itemId }.map { it.rect() }
        val cur = item.rect()
        fun cells(dp: Float) = kotlin.math.round(dp / c).toInt().coerceAtLeast(1)
        fun floorCells(dp: Float) = kotlin.math.floor(dp / c).toInt().coerceAtLeast(1)
        val minCols = if (limits == null) 1 else kotlin.math.ceil(limits.minWidthDp / c).toInt().coerceAtLeast(1)
        val minRows = if (limits == null) 1 else kotlin.math.ceil(limits.minHeightDp / c).toInt().coerceAtLeast(1)
        val maxCols = floorCells(container.right - cur.left + FRect.EPS)
        val maxRows = floorCells(container.bottom - cur.top + FRect.EPS)
        val horizontal = limits?.horizontal ?: true
        val vertical = limits?.vertical ?: true
        val wantCols = cells(wantedW).coerceIn(minCols.coerceAtMost(maxCols), maxCols)
        val wantRows = cells(wantedH).coerceIn(minRows.coerceAtMost(maxRows), maxRows)
        val widths = if (horizontal) (wantCols downTo minCols.coerceAtMost(wantCols)).map { it * c } else listOf(cur.width)
        val heights = if (vertical) (wantRows downTo minRows.coerceAtMost(wantRows)).map { it * c } else listOf(cur.height)
        var best: FRect? = null
        var bestArea = -1f
        var bestDist = Float.MAX_VALUE
        for (w in widths) for (h in heights) {
            val r = FRect(cur.left, cur.top, cur.left + w, cur.top + h)
            if (!r.inside(container) || others.any { it.overlaps(r) }) continue
            val area = w * h
            val dist = kotlin.math.abs(w - wantedW) + kotlin.math.abs(h - wantedH)
            if (area > bestArea + 0.001f || (kotlin.math.abs(area - bestArea) <= 0.001f && dist < bestDist)) { best = r; bestArea = area; bestDist = dist }
        }
        return best?.takeIf { kotlin.math.abs(it.width - cur.width) > 0.001f || kotlin.math.abs(it.height - cur.height) > 0.001f }
    }

    /** [resizedRect] applied: the profile with widget [itemId] at its new size (unchanged when no different size is possible). */
    fun resize(profile: DesktopProfile, itemId: String, wantedW: Float, wantedH: Float, cell: Float, container: FRect, limits: ResizeLimits? = null): DesktopProfile {
        val r = resizedRect(profile, itemId, wantedW, wantedH, cell, container, limits) ?: return profile
        return profile.copy(items = profile.items.map { if (it.id == itemId) it.copy(w = r.width, h = r.height) else it })
    }

    /** The size menu's step: [dCols] and [dRows] whole cells wider or taller (negative: smaller) than the widget is now. */
    fun resizeByCells(profile: DesktopProfile, itemId: String, dCols: Int, dRows: Int, cell: Float, container: FRect, limits: ResizeLimits? = null): DesktopProfile {
        val item = widgetItem(profile, itemId) ?: return profile
        val c = cell.coerceAtLeast(1f)
        return resize(profile, itemId, (kotlin.math.round(item.w / c).toInt() + dCols) * c, (kotlin.math.round(item.h / c).toInt() + dRows) * c, c, container, limits)
    }
}
