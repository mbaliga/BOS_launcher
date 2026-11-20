package com.bos.sphere.feature.grid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.catalog.ColumnFill
import com.bos.sphere.core.catalog.WidgetSpan
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HomeOrder
import com.bos.sphere.core.data.HomeTiles
import com.bos.sphere.core.design.L10n
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Wave 26 (HOM-01, HOM-07): the SCROLL home of the pages after Glance: ONE continuous full-screen surface that scrolls horizontally, the
 * Windows 8 start-screen look. [keys] (the items of all pages, in order) fill it column by column ([ColumnFill]): as many rows as fit
 * between [topBand] (status bar and top hotspots) and [bottomBand] (bottom hotspots and gesture area), top to bottom, then the next
 * column. Widgets span cells. There is no page snapping: the scroll has inertia and the edge overscroll of the platform. With
 * [HomeOrder.RightToLeft] the columns fill from the right edge and the scroll starts there. Physical coordinates throughout: the app
 * language direction does not change anything (the host layout direction is forced left to right here).
 */
/** One request to scroll the continuous surface to [page]'s first column; each request is a new instance, so asking twice scrolls twice. */
class PageScrollRequest(val page: Int)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ColumnScrollHome(
    keys: List<String>,
    appsByKey: Map<String, AppEntry>,
    extras: HomeExtras?,
    order: HomeOrder,
    topBand: Dp,
    bottomBand: Dp,
    onLaunch: (AppEntry) -> Unit,
    onAppLongPress: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    /** The target cell edge: the number of rows is the area height divided by it (rounded down), then the cells are stretched to fill it. */
    targetCell: Dp = 92.dp,
    sidePad: Dp = 12.dp,
    iconScale: Float = 1f,
    showLabels: Boolean = true,
    editing: Boolean = false,
    onRemove: (String) -> Unit = {},
    gestures: HomeGestureSpec? = null,
    /** Reports the layout (rows and columns) for tests and the add flow. */
    onLayout: (ColumnFill.Layout) -> Unit = {},
    /** True while the scroll sits at its start edge (the left edge, or the right edge in right to left): a swipe back from there leaves the scroll. */
    onAtStart: (Boolean) -> Unit = {},
    /** Wave 37 (editorDebt): the items of each page, so the surface knows where a page starts (the page editor's tap on a card scrolls there). */
    pageKeys: List<List<String>> = emptyList(),
    /** A request to bring a page's first column to the start edge; each request is a new instance. */
    pageScroll: PageScrollRequest? = null,
    /** The page the editor has selected (-1: no editor). While the user scrolls the surface, [onPageInView] reports the page now at the start edge. */
    selectedPage: Int = -1,
    onPageInView: (Int) -> Unit = {},
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val density = LocalDensity.current
        val scroll = rememberScrollState()
        val latestExtras by rememberUpdatedState(extras)
        BoxWithConstraints(modifier.fillMaxSize().testTag("scrollhome")) {
            val metrics = ColumnFill.metrics(maxHeight.value, topBand.value, bottomBand.value, targetCell.value)
            val cell = metrics.cellPx // dp
            val spanOf: (String) -> WidgetSpan = { k -> latestExtras?.takeIf { it.owns(k) }?.spanOf(k) ?: WidgetSpan(1, 1) }
            val present = remember(keys, appsByKey, extras) { keys.filter { appsByKey[it] != null || it == HomeTiles.SETTINGS || it == HomeTiles.CLOCK || extras?.owns(it) == true } }
            val layout = remember(present, extras, metrics.rows) { ColumnFill.pack(present, spanOf, metrics.rows) }
            LaunchedEffect(layout) { onLayout(layout) }
            val contentW = maxOf(maxWidth.value, layout.columns * cell + 2 * sidePad.value)
            // The scroll starts at the start edge: the right edge in right to left.
            LaunchedEffect(order, contentW) {
                if (order.rtl) withTimeoutOrNull(600) { snapshotFlow { scroll.maxValue }.first { it > 0 } }?.let { scroll.scrollTo(order.initialScroll(it)) }
            }
            LaunchedEffect(order, scroll) { snapshotFlow { scroll.value == order.initialScroll(scroll.maxValue) }.collect { onAtStart(it) } }
            // Wave 37 (editorDebt): the page editor scrolls the surface to a page's first column, and follows the surface when the user scrolls it.
            val starts = remember(pageKeys, layout) { PageEditorGeometry.pageStartColumns(pageKeys, layout.cells, layout.columns) }
            val cellPx = cell * density.density
            val latestSelected by rememberUpdatedState(selectedPage)
            val latestInView by rememberUpdatedState(onPageInView)
            LaunchedEffect(pageScroll) {
                val r = pageScroll ?: return@LaunchedEffect
                starts.getOrNull(r.page)?.let { scroll.scrollTo(PageEditorGeometry.scrollForColumn(it, cellPx, scroll.maxValue, order.rtl)) }
            }
            LaunchedEffect(scroll, starts, order, cellPx) {
                var was = false
                snapshotFlow { scroll.value to scroll.isScrollInProgress }.collect { (v, moving) ->
                    val sel = latestSelected
                    if (sel >= 0 && (moving || was)) {
                        val now = PageEditorGeometry.pageInView(starts, v, scroll.maxValue, cellPx, order.rtl, sel)
                        if (now != sel) latestInView(now)
                    }
                    was = moving
                }
            }
            // A position is empty when no item covers its cell: the long press on empty space opens the quick sheet / edit mode.
            val isEmptyAt: (androidx.compose.ui.geometry.Offset) -> Boolean = { p ->
                val x = (p.x + scroll.value) / density.density
                val logicalX = if (order.rtl) contentW - x else x
                val col = kotlin.math.floor((logicalX - sidePad.value) / cell).toInt()
                val row = kotlin.math.floor((p.y / density.density - topBand.value) / cell).toInt()
                logicalX < sidePad.value || row < 0 || row >= metrics.rows || ColumnFill.keyAt(layout, col, row) == null
            }
            Box(Modifier.fillMaxSize().homeGestures(gestures, isEmptyAt).horizontalScroll(scroll)) {
                Box(Modifier.width(contentW.dp).fillMaxHeight()) {
                    for (c in layout.cells) {
                        val itemW = c.span.cols * cell
                        val left = order.physicalLeft(sidePad.value + c.col * cell, itemW, contentW)
                        val top = topBand.value + c.row * cell
                        key(c.key) {
                            Box(
                                Modifier.offset { IntOffset((left * density.density).roundToInt(), (top * density.density).roundToInt()) }
                                    .size(itemW.dp, (c.span.rows * cell).dp).testTag("scroll.cell.${c.key}"),
                                contentAlignment = Alignment.Center,
                            ) {
                                ScrollCellContent(c.key, c.span, cell.dp, iconScale, showLabels, appsByKey[c.key], extras, editing, onLaunch, onAppLongPress, { onRemove(c.key) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScrollCellContent(
    key: String, span: WidgetSpan, cell: Dp, iconScale: Float, showLabels: Boolean, app: AppEntry?, extras: HomeExtras?, editing: Boolean,
    onLaunch: (AppEntry) -> Unit, onAppLongPress: (AppEntry) -> Unit, onRemove: () -> Unit,
) {
    val extra = extras?.takeIf { it.owns(key) }
    if (extra != null && extra.isWidget(key)) {
        // A widget spans its cells with a small inset, like a tile.
        val inset = cell * 0.04f
        val px = androidx.compose.ui.platform.LocalDensity.current.density
        Box(Modifier.fillMaxSize().padding(inset).childrenOutOfBounds()) {
            HomeWidgetCell(key, extra, cell.value * px, cell.value * px, Modifier.fillMaxSize().launcherItem("scroll.$key", onMenu = { extra.onLongPress(key) }), editing = editing)
            if (editing) EditHandleBox(cell * span.cols, extraLabel(key, extra), onRemove) {}
        }
        return
    }
    val icon = cell * 0.62f * iconScale.coerceIn(0.5f, 1.3f)
    val label = app?.label ?: extra?.let { extraLabel(key, it) } ?: if (key == HomeTiles.SETTINGS) L10n.tr("Settings") else L10n.tr("Clock")
    val activate: () -> Unit = { if (app != null) onLaunch(app) else if (extra != null) extra.onTap(key) }
    val menu: () -> Unit = { if (app != null) onAppLongPress(app) else if (extra != null) extra.onLongPress(key) }
    Column(
        Modifier.fillMaxSize()
            .semantics(mergeDescendants = true) { contentDescription = label }
            .launcherItem("scroll.$key", onActivate = activate, onMenu = menu)
            .combinedClickable(onClick = activate, onLongClick = menu),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        val face: @Composable () -> Unit = {
            when {
                app != null -> BadgedAppIcon(app, icon, Modifier.size(icon))
                key == HomeTiles.SETTINGS -> SettingsTile(icon, Modifier.size(icon))
                extra != null -> ExtraItemFace(key, extra, icon, Modifier.size(icon))
                else -> ClockTile(icon, Modifier.size(icon))
            }
        }
        if (editing && (app != null || extra != null)) EditHandleBox(icon, label, onRemove) { face() } else face()
        if (showLabels) AppLabel(label, icon)
    }
}
