package com.bos.sphere.feature.widgets

import kotlin.math.ceil
import kotlin.math.roundToInt

/** A size in dp (pure stand-in for `android.util.SizeF`). */
data class SizeDp(val width: Float, val height: Float)

/** The min/max option values the platform derives from a size list (whole dp). */
data class SizeBounds(val minWidth: Int, val minHeight: Int, val maxWidth: Int, val maxHeight: Int)

/**
 * The sizes a hosted widget is told it has, via `AppWidgetHostView.updateAppWidgetSize(Bundle,
 * List<SizeF>)` (and the bind options), so responsive widgets pick the right layout.
 */
object WidgetSizing {

    /** Lens range the equator can produce (focus 1..2, two-row shrink 0.66); clamps bad input. */
    private const val MIN_LENS = 0.5f
    private const val MAX_LENS = 3f

    /**
     * The size an equator widget's view is laid out at: the focused, lens-magnified tile. The tile is
     * then scaled down by the lens when it moves off focus, so the focused tile is drawn 1:1.
     */
    fun equatorLayoutDp(tileDp: Float, lens: Float): Float =
        wholeDp(tileDp * lens.coerceIn(MIN_LENS, MAX_LENS))

    /**
     * Sizes for an equator tile: only the size its view is actually laid out at, the focus-lens
     * size ([equatorLayoutDp]), square and whole dp. Off focus the tile is only scaled down as a
     * picture, so the provider never lays out at any other size; reporting one (the brief's "tile
     * size") would make legacy providers, which read the min width / max height options, pick a
     * layout for a shape that never appears.
     */
    fun equatorSizes(tileDp: Float, lens: Float): List<SizeDp> {
        val laidOut = equatorLayoutDp(tileDp, lens)
        return listOf(SizeDp(laidOut, laidOut))
    }

    /** Size for a hub widget: the full panel width at the chosen height preset. */
    fun hubSizes(panelWidthDp: Float, size: HubSize): List<SizeDp> =
        listOf(SizeDp(wholeDp(panelWidthDp.coerceAtLeast(1f)), size.heightDp))

    /**
     * Min/max as `updateAppWidgetSize` computes them from a size list: min/max of the widths and of
     * the heights. Used for the bind options so the provider renders the right layout from the
     * first update.
     */
    fun bounds(sizes: List<SizeDp>): SizeBounds {
        require(sizes.isNotEmpty()) { "at least one size" }
        return SizeBounds(
            minWidth = sizes.minOf { it.width }.toInt(),
            minHeight = sizes.minOf { it.height }.toInt(),
            maxWidth = ceil(sizes.maxOf { it.width }).toInt(),
            maxHeight = ceil(sizes.maxOf { it.height }).toInt(),
        )
    }

    /** Smallest hub preset tall enough for a provider's minimum height; the largest if none is. */
    fun defaultHubSize(providerMinHeightDp: Float): HubSize =
        HubSize.entries.firstOrNull { it.heightDp >= providerMinHeightDp } ?: HubSize.Large

    private fun wholeDp(v: Float): Float = v.roundToInt().toFloat()
}

/** Hub stack layout: how much height the stack may use, and whether it must scroll. */
object HubStackLayout {
    /** Height of a widget's label row (its options button is a 48 dp target). */
    const val ROW_HEADER_DP = 48f
    const val GAP_DP = 10f

    /**
     * Height the stack leaves for the hub's other content (notifications) when they share the
     * panel: about one notification row, or half the space if that is less.
     */
    const val SHARED_RESERVE_DP = 120f

    /** Hub fraction from which the stack takes touches (the hub has settled fully open). */
    const val LIVE_FROM_FRACTION = 0.99f

    /** Whether hub widgets take touches at this hub [fraction]. */
    fun isLive(fraction: Float): Boolean = fraction >= LIVE_FROM_FRACTION

    /**
     * The most height the stack may take out of [availableDp] (what the hub panel has below its
     * header). The stack is measured before the notifications, so it gets the room its widgets
     * need; when [sharesPanel] it still leaves [SHARED_RESERVE_DP] (or half) to the notifications.
     */
    fun viewportDp(availableDp: Float, sharesPanel: Boolean): Float {
        if (!availableDp.isFinite()) return availableDp
        val available = availableDp.coerceAtLeast(0f)
        if (!sharesPanel) return available
        return available - minOf(SHARED_RESERVE_DP, available / 2f)
    }

    /** Total height of a stack: the section header, then each widget's label row and body. */
    fun heightDp(sizes: List<HubSize>, sectionHeaderDp: Float = ROW_HEADER_DP): Float =
        sectionHeaderDp + sizes.sumOf { (ROW_HEADER_DP + it.heightDp + GAP_DP).toDouble() }.toFloat()

    /**
     * The stack scrolls only when it overflows. When it fits, nothing scrolls, so a drag inside a
     * hosted widget reaches the widget (its own lists can scroll).
     */
    fun needsScroll(sizes: List<HubSize>, availableDp: Float): Boolean =
        availableDp.isFinite() && heightDp(sizes) > availableDp
}
