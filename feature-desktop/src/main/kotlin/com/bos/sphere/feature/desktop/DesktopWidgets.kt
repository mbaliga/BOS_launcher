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
}

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
}
