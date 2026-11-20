package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

private val STRIP_CORNER = 12.dp

/** The text a page card announces: "Page 2 of 4, empty, selected". */
internal fun pageCardLabel(index: Int, count: Int, empty: Boolean, selected: Boolean): String =
    L10n.tr("Page %d of %d", index + 1, count) + (if (empty) ", " + L10n.tr("empty") else "") + (if (selected) ", " + L10n.tr("selected") else "")

/** Wave 45 (pageEditorLayout): stable keys for the strip's pages (two chunks of one overflowed page share an id, so a repeat gets a suffix). */
internal fun pageKeys(pages: List<HomePage>): List<String> {
    val seen = HashMap<Long, Int>()
    return pages.mapIndexed { i, p -> if (p.id == 0L) "i$i" else "${p.id}:${seen.merge(p.id, 1, Int::plus)}" }
}

/**
 * The strip of page cards: a row above the live card in portrait, a column at the end side in landscape and on wide windows. The focused card is
 * centred at scale 1.0 with a 2 dp accent outline, its neighbours are 0.9 and dimmer. Glance first (when on, a house glyph, no menu), every page
 * (an overflow menu with Move earlier, Move later, Delete page; long press and drag to reorder with a ghost and an insertion line; arrows move between
 * cards, Shift with an arrow reorders; the same as TalkBack custom actions), then a dashed "New page" card.
 */
@Composable
internal fun PageStrip(
    layout: PageEditorGeometry.Layout, state: LazyListState,
    pages: List<HomePage>, current: Int, glanceOn: Boolean, glanceFocus: Boolean,
    appsByKey: Map<String, AppEntry>, prefs: LauncherPrefs, isLandscape: Boolean, reduced: Boolean, hostOrigin: () -> Offset, rects: MutableMap<Int, EdgeRect>,
    onSelectPage: (Int, Float, Float) -> Unit, onSelectGlance: (Float, Float) -> Unit, onAddPage: (Float, Float) -> Unit,
    onDelete: (Int) -> Unit, onMove: (Int, Int) -> Unit, onReorder: (Int, Int) -> Unit,
    modifier: Modifier,
) {
    // The cards are a fixed size in dp, so their small labels do not grow past a modest font scale (they would overflow the card).
    val base = LocalDensity.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, LocalDensity provides androidx.compose.ui.unit.Density(base.density, base.fontScale.coerceAtMost(1.15f))) {
        val d = LocalDensity.current
        val haptic = LocalHapticFeedback.current
        val focusManager = LocalFocusManager.current
        val horizontal = layout.axis == PageEditorGeometry.Axis.Horizontal
        val entries = remember(glanceOn, pages.size) { PageEditorGeometry.entries(glanceOn, pages.size) }
        val keys = remember(pages) { pageKeys(pages) }
        val focus = PageEditorGeometry.focusPosition(glanceOn, glanceFocus, current).coerceIn(0, entries.lastIndex)
        val cw = with(d) { layout.cardW.toDp() }; val ch = with(d) { layout.cardH.toDp() }
        // The padding before the first item puts the item scrolled to the start of the strip into the middle.
        val padPx = if (horizontal) (layout.strip.width - layout.cardW) / 2f else (layout.strip.height - layout.cardH) / 2f
        val pad = with(d) { padPx.coerceAtLeast(0f).toDp() }
        val gap = PageEditorGeometry.GAP_DP.dp
        var stripOrigin by remember { mutableStateOf(Offset.Zero) }
        var dragFrom by remember { mutableIntStateOf(-1) }
        var dragStart by remember { mutableStateOf(Offset.Zero) }
        var dragDelta by remember { mutableStateOf(Offset.Zero) }
        var said by remember { mutableStateOf("") }
        fun reading(o: Offset) = if (horizontal) (if (layout.mirrored) -o.x else o.x) else o.y
        val dropTo = if (dragFrom >= 0) PageEditorGeometry.dropIndex(dragFrom, reading(dragDelta), layout.pitch, pages.size) else -1
        var lastTick by remember { mutableIntStateOf(-1) }
        LaunchedEffect(dropTo) { if (dropTo >= 0 && dropTo != lastTick) { if (lastTick >= 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); lastTick = dropTo } else if (dropTo < 0) lastTick = -1 }
        LaunchedEffect(focus, entries.size) { if (dragFrom < 0) { if (reduced) state.scrollToItem(focus) else state.animateScrollToItem(focus) } }
        // The offset of a tapped card's centre from the live card's centre, from where it really is now (the strip may have been scrolled by hand).
        fun offsetFrom(c: LayoutCoordinates?): Pair<Float, Float> {
            val p = c?.takeIf { it.isAttached }?.positionInWindow() ?: return 0f to 0f
            val o = hostOrigin()
            val cx = p.x - o.x + c.size.width / 2f; val cy = p.y - o.y + c.size.height / 2f
            return (cx - (layout.card.left + layout.card.width / 2f)) to (cy - (layout.card.top + layout.card.height / 2f))
        }
        fun Modifier.nav(reorder: ((Int) -> Unit)?): Modifier = onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val along = if (horizontal) e.key == Key.DirectionLeft || e.key == Key.DirectionRight else e.key == Key.DirectionUp || e.key == Key.DirectionDown
            if (!along) return@onPreviewKeyEvent false
            val step = PageEditorGeometry.stepOf(horizontal, layout.mirrored, right = e.key == Key.DirectionRight, down = e.key == Key.DirectionDown)
            if (e.isShiftPressed) { reorder?.invoke(step); reorder != null }
            else focusManager.moveFocus(when (e.key) { Key.DirectionLeft -> FocusDirection.Left; Key.DirectionRight -> FocusDirection.Right; Key.DirectionUp -> FocusDirection.Up; else -> FocusDirection.Down })
        }
        @Composable fun PageFace(p: Int, selected: Boolean, ghost: Boolean) {
            val page = pages[p]
            val shape = RoundedCornerShape(STRIP_CORNER)
            val tintTop = Color.hsv(PageEditorGeometry.hue(p), 0.55f, if (com.bos.sphere.core.design.HyleTheme.isLight) 0.98f else 0.75f)
            Box(
                Modifier.fillMaxSize().clip(shape).background(Brush.verticalGradient(listOf(tintTop.copy(alpha = 0.55f), HyleColors.Surface.copy(alpha = 0.95f))), shape)
                    .border(if (selected || ghost) 2.dp else 1.dp, if (selected || ghost) HyleColors.Violet else HyleColors.InkDim.copy(alpha = 0.28f), shape),
            ) {
                PagePreview(page, appsByKey, prefs, isLandscape, Modifier.fillMaxSize().padding(start = 6.dp, end = 6.dp, bottom = 30.dp, top = 18.dp))
                if (page.keys.isEmpty()) Text(L10n.tr("Empty page"), color = HyleColors.InkDim, fontSize = 10.sp, modifier = Modifier.align(Alignment.Center))
                Text("${p + 1}", color = HyleColors.InkDim, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 4.dp))
            }
        }
        @Composable fun Item(pos: Int) {
            val e = entries[pos]
            var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
            val slot = Modifier.size(cw, ch).onGloballyPositioned {
                coords = it
                if (e.kind == PageEditorGeometry.Kind.Page && it.isAttached) { val p = it.positionInWindow(); rects[e.page] = EdgeRect(p.x, p.y, p.x + it.size.width, p.y + it.size.height) }
            }
            val isFocus = when (e.kind) { PageEditorGeometry.Kind.Glance -> glanceFocus; PageEditorGeometry.Kind.Page -> !glanceFocus && e.page == current; else -> false }
            val near = if (isFocus) 1f else PageEditorGeometry.NEIGHBOUR_SCALE
            val dim = if (isFocus) 1f else PageEditorGeometry.NEIGHBOUR_ALPHA
            when (e.kind) {
                PageEditorGeometry.Kind.Glance -> Box(slot.graphicsLayer { scaleX = near; scaleY = near; alpha = dim }) {
                    val shape = RoundedCornerShape(STRIP_CORNER)
                    Box(
                        Modifier.fillMaxSize().testTag("pe.glance").clip(shape).background(Brush.verticalGradient(listOf(Color(0xFF3A3F7A), Color(0xFF14172B))), shape)
                            .border(if (isFocus) 2.dp else 1.dp, if (isFocus) HyleColors.Violet else HyleColors.InkDim.copy(alpha = 0.28f), shape)
                            .nav(null)
                            .clickable(onClickLabel = L10n.tr("Show this page")) { val (dx, dy) = offsetFrom(coords); onSelectGlance(dx, dy) }
                            .semantics { role = Role.Button; this.selected = isFocus; contentDescription = L10n.tr("Glance") + if (isFocus) ", " + L10n.tr("selected") else "" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Glyphs.Home, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                }
                PageEditorGeometry.Kind.Page -> {
                    val p = e.page
                    androidx.compose.runtime.DisposableEffect(p) { onDispose { rects.remove(p) } }
                    var menu by remember { mutableStateOf(false) }
                    val label = pageCardLabel(p, pages.size, pages[p].keys.isEmpty(), isFocus)
                    val dragging = dragFrom == p
                    Box(slot.graphicsLayer { scaleX = near; scaleY = near; alpha = if (dragging) 0.3f else dim }) {
                        Box(
                            Modifier.fillMaxSize().testTag("pe.page.$p")
                                .nav { step -> PageEditorGeometry.stepTarget(p, step, pages.size)?.let { onMove(p, step); said = L10n.tr("Page moved to position %d of %d", it + 1, pages.size) } }
                                .pointerInput(p, layout.pitch, pages.size, layout.mirrored, horizontal) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            val c = coords
                                            dragFrom = p; dragDelta = Offset.Zero
                                            dragStart = if (c != null && c.isAttached) c.positionInWindow() + Offset(c.size.width / 2f, c.size.height / 2f) else Offset.Zero
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, amount -> change.consume(); dragDelta += amount },
                                        onDragEnd = {
                                            val to = PageEditorGeometry.dropIndex(p, reading(dragDelta), layout.pitch, pages.size)
                                            dragFrom = -1; dragDelta = Offset.Zero
                                            if (to != p) { onReorder(p, to); said = L10n.tr("Page moved to position %d of %d", to + 1, pages.size) }
                                        },
                                        onDragCancel = { dragFrom = -1; dragDelta = Offset.Zero },
                                    )
                                }
                                .clickable(onClickLabel = L10n.tr("Show this page")) { val (dx, dy) = offsetFrom(coords); onSelectPage(p, dx, dy) }
                                .semantics {
                                    role = Role.Button; this.selected = isFocus; contentDescription = label
                                    customActions = buildList {
                                        if (p > 0) add(CustomAccessibilityAction(L10n.tr("Move earlier")) { onMove(p, -1); true })
                                        if (p < pages.size - 1) add(CustomAccessibilityAction(L10n.tr("Move later")) { onMove(p, 1); true })
                                        add(CustomAccessibilityAction(L10n.tr("Delete page")) { onDelete(p); true })
                                    }
                                },
                        ) { PageFace(p, isFocus, ghost = false) }
                        // The overflow menu: the only place Move earlier / later and Delete page live on screen (the floating rail is gone).
                        Box(
                            Modifier.align(Alignment.BottomCenter).size(width = 48.dp.coerceAtMost(cw), height = 32.dp).testTag("pe.more.$p")
                                .clickable(role = Role.Button) { menu = true }
                                .semantics { role = Role.Button; contentDescription = L10n.tr("Actions for page %d", p + 1) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Glyphs.More, null, tint = HyleColors.InkDim, modifier = Modifier.size(20.dp))
                            OneUiMenu(
                                menu, { menu = false },
                                listOf(
                                    OneUiMenuEntry(L10n.tr("Move earlier"), enabled = p > 0, tag = "pe.menu.earlier.$p") { onMove(p, -1) },
                                    OneUiMenuEntry(L10n.tr("Move later"), enabled = p < pages.size - 1, tag = "pe.menu.later.$p") { onMove(p, 1) },
                                    OneUiMenuEntry(L10n.tr("Delete page"), destructive = true, tag = "pe.menu.delete.$p") { onDelete(p) },
                                ),
                            )
                        }
                        // The insertion line while a card is dragged: before the slot it would take, after the last card at the very end.
                        if (dragFrom >= 0 && dropTo != dragFrom) {
                            val g = PageEditorGeometry.insertionGap(dragFrom, dropTo)
                            val at = if (g == p) 0 else if (g == pages.size && p == pages.size - 1) 1 else -1
                            if (at >= 0) InsertionLine(horizontal, layout.mirrored, atEnd = at == 1)
                        }
                    }
                }
                PageEditorGeometry.Kind.Add -> Box(slot) {
                    val color = HyleColors.InkDim.copy(alpha = 0.6f)
                    Box(
                        Modifier.fillMaxSize().testTag("pe.add").clip(RoundedCornerShape(STRIP_CORNER))
                            .drawBehind { drawRoundRect(color, cornerRadius = CornerRadius(STRIP_CORNER.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))) }
                            .nav(null)
                            .clickable(onClickLabel = L10n.tr("New page")) { val (dx, dy) = offsetFrom(coords); onAddPage(dx, dy) }
                            .semantics { role = Role.Button; contentDescription = L10n.tr("New page") },
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("+", color = HyleColors.InkDim, fontSize = 28.sp)
                            Text(L10n.tr("New page"), color = HyleColors.InkDim, fontSize = 11.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
        val stripDescription = L10n.tr("Page carousel")
        Box(modifier.onGloballyPositioned { stripOrigin = it.positionInWindow() }) {
            val listMod = Modifier.fillMaxSize().testTag("page-editor-strip").semantics { contentDescription = stripDescription }
            if (horizontal) {
                LazyRow(listMod, state = state, reverseLayout = layout.mirrored, contentPadding = PaddingValues(horizontal = pad), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                    items(entries.size, key = { i -> entries[i].let { if (it.kind == PageEditorGeometry.Kind.Page) "p" + keys[it.page] else it.kind.name } }) { Item(it) }
                }
            } else {
                LazyColumn(listMod, state = state, contentPadding = PaddingValues(vertical = pad), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
                    items(entries.size, key = { i -> entries[i].let { if (it.kind == PageEditorGeometry.Kind.Page) "p" + keys[it.page] else it.kind.name } }) { Item(it) }
                }
            }
            // The ghost of the card under the finger (along the strip only, so it stays in the strip).
            if (dragFrom in pages.indices) {
                val c = dragStart - stripOrigin + (if (horizontal) Offset(dragDelta.x, 0f) else Offset(0f, dragDelta.y))
                Box(
                    Modifier.offset { IntOffset((c.x - layout.cardW / 2f).toInt(), (c.y - layout.cardH / 2f).toInt()) }.size(cw, ch)
                        .graphicsLayer { scaleX = 1.08f; scaleY = 1.08f; alpha = 0.92f }.testTag("pe.ghost.card"),
                ) { PageFace(dragFrom, selected = false, ghost = true) }
            }
            if (said.isNotEmpty()) Box(Modifier.size(1.dp).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = said })
        }
    }
}

/** The insertion marker of a reorder drag: an accent bar across the strip's direction, in the gap before (or after) a card. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.InsertionLine(horizontal: Boolean, mirrored: Boolean, atEnd: Boolean) {
    val half = (PageEditorGeometry.GAP_DP / 2f + 1.5f).dp
    val startsLeft = if (horizontal) (!mirrored) else true
    val atLeadingEdge = if (atEnd) !startsLeft else startsLeft
    if (horizontal) {
        Box(
            Modifier.align(if (atLeadingEdge) Alignment.CenterStart else Alignment.CenterEnd).offset(x = if (atLeadingEdge) -half else half).width(3.dp).fillMaxHeight()
                .background(HyleColors.Violet, RoundedCornerShape(2.dp)).testTag("pe.insertion"),
        )
    } else {
        Box(
            Modifier.align(if (!atEnd) Alignment.TopCenter else Alignment.BottomCenter).offset(y = if (!atEnd) -half else half).height(3.dp).fillMaxWidth()
                .background(HyleColors.Violet, RoundedCornerShape(2.dp)).testTag("pe.insertion"),
        )
    }
}
