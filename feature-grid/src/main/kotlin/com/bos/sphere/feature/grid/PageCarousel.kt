package com.bos.sphere.feature.grid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import kotlin.math.roundToInt

/**
 * HOM-12: the page carousel that fills the background beside the edit card. A vertically scrolling column of live page
 * thumbnails (the real page content, drawn small from the same layout model, never a screenshot) with the current page
 * highlighted. Tap (or Enter) shows the page on the card; each thumbnail carries Move up, Move down and Delete page
 * buttons, which are also the keyboard route for the long-press drag that reorders. The last page cannot vanish:
 * deleting it empties it (see [com.bos.sphere.core.data.HomePageOps.delete]).
 */
@Composable
fun PageCarousel(
    pages: List<HomePage>,
    current: Int,
    appsByKey: Map<String, AppEntry>,
    prefs: LauncherPrefs,
    isLandscape: Boolean,
    onSelect: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onMove: (index: Int, delta: Int) -> Unit,
    onMoveTo: (index: Int, target: Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    aspect: Float = 0.56f,
    compact: Boolean = false,
) {
    val list = rememberLazyListState()
    LaunchedEffect(current) { if (current in pages.indices) list.animateScrollToItem(current) }
    Column(modifier.semantics { contentDescription = L10n.tr("Page carousel") }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(L10n.tr("Pages"), color = HyleColors.InkBright, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            CarouselButton(L10n.tr("Add page"), onClick = onAdd, prominent = true, glyph = "+", description = L10n.tr("Add page"))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("page-carousel-list"), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(pages, key = { i, p -> "${p.id}:$i" }) { i, page ->
                PageThumb(
                    page = page, index = i, count = pages.size, selected = i == current, compact = compact, appsByKey = appsByKey, prefs = prefs, isLandscape = isLandscape, aspect = aspect,
                    onSelect = { onSelect(i) }, onDelete = { onDelete(i) }, onMove = { d -> onMove(i, d) }, onMoveTo = { t -> onMoveTo(i, t) },
                )
            }
        }
    }
}

@Composable
private fun PageThumb(
    page: HomePage, index: Int, count: Int, selected: Boolean, compact: Boolean, appsByKey: Map<String, AppEntry>, prefs: LauncherPrefs, isLandscape: Boolean, aspect: Float,
    onSelect: () -> Unit, onDelete: () -> Unit, onMove: (Int) -> Unit, onMoveTo: (Int) -> Unit,
) {
    var pitch by remember { mutableIntStateOf(1) }
    var drag by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    val label = L10n.tr("Page %d of %d", index + 1, count) + if (page.keys.isEmpty()) ", " + L10n.tr("empty") else ""
    Column(
        Modifier.fillMaxWidth().onSizeChanged { pitch = it.height + 40 } // the column's own spacing and the buttons sit inside the item
            .graphicsLayer { translationY = if (dragging) drag else 0f; alpha = if (dragging) 0.85f else 1f }
            .pointerInput(index, count) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragging = true; drag = 0f },
                    onDragEnd = {
                        val steps = (drag / pitch.coerceAtLeast(1)).roundToInt()
                        dragging = false; drag = 0f
                        if (steps != 0) onMoveTo((index + steps).coerceIn(0, count - 1))
                    },
                    onDragCancel = { dragging = false; drag = 0f },
                    onDrag = { change, amount -> change.consume(); drag += amount.y },
                )
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        @Composable fun Thumb(m: Modifier) = Box(
            m.aspectRatio(aspect.coerceIn(0.3f, 2.6f)).clip(shape)
                .background(HyleColors.Surface.copy(alpha = if (selected) 0.95f else 0.6f), shape)
                .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) HyleColors.Violet else HyleColors.InkDim.copy(alpha = 0.25f)), shape)
                .clickable(onClickLabel = L10n.tr("Show this page"), onClick = onSelect)
                .semantics { role = Role.Tab; this.selected = selected; contentDescription = label },
        ) {
            PagePreview(page, appsByKey, prefs, isLandscape, Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = if (compact) 8.dp else 26.dp))
            if (page.keys.isEmpty()) Text(L10n.tr("Empty page"), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
            Text("${index + 1}", color = if (selected) HyleColors.Violet else HyleColors.InkDim, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
        }
        @Composable fun Buttons(m: Modifier) = Row(m, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CarouselButton(L10n.tr("Move up"), enabled = index > 0, onClick = { onMove(-1) }, glyph = "↑", description = L10n.tr("Move page %d up", index + 1))
            CarouselButton(L10n.tr("Move down"), enabled = index < count - 1, onClick = { onMove(1) }, glyph = "↓", description = L10n.tr("Move page %d down", index + 1))
            CarouselButton(L10n.tr("Delete page"), onClick = onDelete, glyph = "×", danger = true, description = L10n.tr("Delete page %d", index + 1))
        }
        if (compact) {
            // Landscape: the thumbnail is wide and flat, so the buttons sit beside it and more pages fit in the column.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Thumb(Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CarouselButton(L10n.tr("Move up"), enabled = index > 0, onClick = { onMove(-1) }, glyph = "↑", description = L10n.tr("Move page %d up", index + 1))
                    CarouselButton(L10n.tr("Move down"), enabled = index < count - 1, onClick = { onMove(1) }, glyph = "↓", description = L10n.tr("Move page %d down", index + 1))
                    CarouselButton(L10n.tr("Delete page"), onClick = onDelete, glyph = "×", danger = true, description = L10n.tr("Delete page %d", index + 1))
                }
            }
        } else {
            Thumb(Modifier.fillMaxWidth())
            Buttons(Modifier.fillMaxWidth())
        }
    }
}

/** The page's own content drawn small: the apps' real icons in the page's grid, a quiet tile for everything else. */
@Composable
fun PagePreview(page: HomePage, appsByKey: Map<String, AppEntry>, prefs: LauncherPrefs, isLandscape: Boolean, modifier: Modifier = Modifier) {
    val grid = page.layout.gridFor(isLandscape, prefs)
    BoxWithConstraints(modifier) {
        val cell = minOf(maxWidth / grid.columns, maxHeight / grid.rows)
        val icon = cell * 0.62f
        val originX = (maxWidth - cell * grid.columns) / 2
        val originY = 0.dp
        page.keys.take(grid.capacity).forEachIndexed { i, key ->
            val x = originX + cell * (i % grid.columns) + (cell - icon) / 2
            val y = originY + cell * (i / grid.columns) + (cell - icon) / 2
            val app = appsByKey[key]
            if (app != null) Box(Modifier.absoluteOffset(x, y)) { AppIcon(app, icon) }
            else Box(Modifier.absoluteOffset(x, y).size(icon).background(HyleColors.Violet.copy(alpha = 0.35f), RoundedCornerShape(icon / 4)))
        }
    }
}

@Composable
internal fun CarouselButton(
    label: String, onClick: () -> Unit, enabled: Boolean = true, prominent: Boolean = false, danger: Boolean = false, glyph: String? = null, description: String? = null,
) {
    val shape = RoundedCornerShape(50)
    val fg = when {
        !enabled -> HyleColors.InkDim.copy(alpha = 0.45f)
        danger -> Color(0xFFE5484D)
        prominent -> HyleColors.OnAccent
        else -> HyleColors.InkBright
    }
    Text(
        glyph ?: label, color = fg, fontSize = 13.sp, fontWeight = if (prominent) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .background(if (prominent && enabled) HyleColors.Violet else HyleColors.InkDim.copy(alpha = 0.14f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (glyph != null) 14.dp else 14.dp, vertical = 8.dp)
            .semantics { role = Role.Button; contentDescription = description ?: label; if (!enabled) disabled() },
    )
}
