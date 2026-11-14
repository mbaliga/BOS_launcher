package com.bos.sphere.feature.grid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.bos.sphere.core.catalog.Box
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.ItemKind
import com.bos.sphere.core.catalog.WidgetBand
import com.bos.sphere.core.design.HyleColors

/**
 * HOM-08 / HOM-01: where the widget band and the item strip sit on the scrolling homes (List, XMB,
 * DSi, continuous). The band is carved out of the frame's app area first, so the tile list, the
 * item strip and every widget are disjoint; the scrolling content takes [padFor] as extra padding.
 */
object ScrollExtrasLayout {
    data class Result(val tiles: Box, val items: Box?, val widgets: List<Box>, val thickness: Float, val edge: WidgetBand.Edge)

    fun compute(widthDp: Float, heightDp: Float, widgetCount: Int, itemCount: Int): Result {
        val a = HomeFrame.appArea(widthDp, heightDp)
        val area = Box(a.left, a.top, a.right, a.bottom)
        val edge = widgetBandEdge(widthDp.toInt(), heightDp.toInt())
        if (widgetCount <= 0 && itemCount <= 0) return Result(area, null, emptyList(), 0f, edge)
        val frac = when { widgetCount > 0 && itemCount > 0 -> 0.42f; widgetCount > 0 -> 0.30f; else -> 0.16f }
        val bottom = edge == WidgetBand.Edge.Bottom
        val thick = (if (bottom) area.height else area.width) * frac
        val itemsThick = when { itemCount > 0 && widgetCount > 0 -> thick * 0.32f; itemCount > 0 -> thick; else -> 0f }
        val tiles = if (bottom) Box(area.left, area.top, area.right, area.bottom - thick) else Box(area.left, area.top, area.right - thick, area.bottom)
        val band = if (bottom) Box(area.left, area.bottom - thick, area.right, area.bottom) else Box(area.right - thick, area.top, area.right, area.bottom)
        val items = if (itemCount > 0) {
            if (bottom) Box(band.left, band.top, band.right, band.top + itemsThick) else Box(band.left, band.top, band.left + itemsThick, band.bottom)
        } else null
        val widgets = if (widgetCount > 0) {
            val region = if (bottom) Box(band.left, band.top + itemsThick, band.right, band.bottom) else Box(band.left + itemsThick, band.top, band.right, band.bottom)
            // WidgetBand carves its band from the far side of the area; extend the area so the band is exactly [region].
            val big = if (bottom) Box(region.left, region.bottom - region.height / 0.5f, region.right, region.bottom) else Box(region.right - region.width / 0.5f, region.top, region.right, region.bottom)
            WidgetBand.layout(big, widgetCount, edge, 0.5f).widgets
        } else emptyList()
        return Result(tiles, items, widgets, thick, edge)
    }

    /** The scrolling content's padding with the band's thickness added on its edge. */
    fun padFor(base: PaddingValues, widthDp: Float, heightDp: Float, widgetCount: Int, itemCount: Int): PaddingValues {
        val r = compute(widthDp, heightDp, widgetCount, itemCount)
        if (r.thickness <= 0f) return base
        val extra = r.thickness.dp
        return if (r.edge == WidgetBand.Edge.Bottom) {
            PaddingValues(base.calculateLeftPadding(LayoutDirection.Ltr), base.calculateTopPadding(), base.calculateRightPadding(LayoutDirection.Ltr), base.calculateBottomPadding() + extra)
        } else {
            PaddingValues(base.calculateLeftPadding(LayoutDirection.Ltr), base.calculateTopPadding(), base.calculateRightPadding(LayoutDirection.Ltr) + extra, base.calculateBottomPadding())
        }
    }
}

/**
 * The keys of the item strip and the widget band, taken from the surface contract's plan for
 * [style] (HOM-01): widgets are the plan's band widgets, items are the non-app, non-widget cells.
 * The continuous grid uses the list plan (it has no cell grid of its own to hold widgets).
 */
fun bandKeys(extras: HomeExtras, topLevel: List<String>, style: com.bos.sphere.core.data.HomeStyle, widthDp: Int, heightDp: Int): Pair<List<String>, List<String>> {
    val s = if (style == com.bos.sphere.core.data.HomeStyle.Grid) com.bos.sphere.core.data.HomeStyle.List else style
    val plan = HomeSurfaceContract.forStyle(s).plan(SurfaceRequest(extras.state, widthDp, heightDp, keys = topLevel))
    val items = plan.cells.map { it.key }.filter { extras.owns(it) && ItemKeys.kind(it) != ItemKind.Widget }
    return items to plan.bandWidgets.filter { extras.owns(it) }
}

/** Draws the item strip and widget band of [ScrollExtrasLayout] over a scrolling home. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScrollExtrasBand(extras: HomeExtras, items: List<String>, widgets: List<String>, widthDp: Float, heightDp: Float, modifier: Modifier = Modifier) {
    if (items.isEmpty() && widgets.isEmpty()) return
    val r = ScrollExtrasLayout.compute(widthDp, heightDp, widgets.size, items.size)
    val bottom = r.edge == WidgetBand.Edge.Bottom
    BoxWithConstraints(modifier.fillMaxSize().safeDrawingPadding()) {
        val ow = maxWidth.value
        val oh = maxHeight.value
        fun x(b: Box) = if (bottom) b.left else ow - (widthDp - b.left)
        fun y(b: Box) = if (bottom) oh - (heightDp - b.top) else b.top
        r.items?.let { b ->
            val tile = minOf(56f, (if (bottom) b.height else b.width) - 18f).coerceAtLeast(28f).dp
            val mod = Modifier.offset(x(b).dp, y(b).dp).width(b.width.dp).height(b.height.dp)
            if (bottom) LazyRow(mod, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                items(items, key = { it }) { k -> BandItem(k, extras, tile) }
            } else LazyColumn(mod, verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                items(items, key = { it }) { k -> BandItem(k, extras, tile) }
            }
        }
        widgets.zip(r.widgets).forEach { (k, b) ->
            HomeWidgetCell(
                k, extras, b.width, b.height,
                Modifier.offset(x(b).dp, y(b).dp).size(b.width.dp, b.height.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BandItem(key: String, extras: HomeExtras, tile: Dp) {
    Column(
        Modifier.launcherItem("band.$key", focusable = false, onActivate = { extras.onTap(key) }, onMenu = { extras.onLongPress(key) })
            .combinedClickable(onClick = { extras.onTap(key) }, onLongClick = { extras.onLongPress(key) }),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ExtraItemFace(key, extras, tile)
        Text(extraLabel(key, extras), color = HyleColors.Ink, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(tile + 12.dp))
    }
}
