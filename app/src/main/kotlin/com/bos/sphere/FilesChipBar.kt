package com.bos.sphere

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.zIndex
import com.bos.sphere.core.catalog.FileSourceIds
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.gate
import com.bos.sphere.core.design.rememberMotionPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * W54 (filesChips): the source switcher as the owner asked for it. The row runs edge to edge under the whole page width; chips fade out softly at an
 * edge that has more behind it ([horizontalEdgeFade]); a chip can be picked up with a long press and dragged to a new place (collections among
 * collections, folders among folders; All and the Add tile are fixed). A screen reader gets "Move earlier" and "Move later", a keyboard Alt+Left and Alt+Right.
 */

/** How wide the fade at a scroll edge is. */
internal val EDGE_FADE = 28.dp

/** What the row shows as faded, for tests only (not announced): `none`, `start`, `end` or `both`. Start and end are the reading direction's. */
internal val FilesChipFadeKey = SemanticsPropertyKey<String>("FilesChipFade")
private var SemanticsPropertyReceiver.chipFade by FilesChipFadeKey

/** The state of the fade for [scroll]: which logical edges have content hidden behind them. */
internal fun fadeState(scroll: ScrollState): String {
    val start = scroll.value > 0
    val end = scroll.value < scroll.maxValue
    return when { start && end -> "both"; start -> "start"; end -> "end"; else -> "none" }
}

/**
 * Chips of a horizontally scrolling row fade out towards an edge that has more content: an alpha mask ([BlendMode.DstIn]) over an offscreen layer. The
 * start fade shows only once the row has scrolled; the end fade only while more is hidden; an edge with nothing hidden is not touched. [endReserve] is
 * a stretch at the end that chips never show in (the pinned Add tile lives there): the fade finishes where it begins. Put this BEFORE the scroll
 * modifier, so it masks what the viewport shows.
 */
internal fun Modifier.horizontalEdgeFade(scroll: ScrollState, fade: Dp = EDGE_FADE, endReserve: Dp = 0.dp): Modifier =
    this.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val w = size.width; val h = size.height
            val f = minOf(fade.toPx(), w / 2f); val r = endReserve.toPx().coerceIn(0f, w / 2f)
            val rtl = layoutDirection == LayoutDirection.Rtl
            val clear = Color.Transparent; val keep = Color.Black
            fun rect(x0: Float, x1: Float, from: Color, to: Color) {
                // A gradient from [from] at x0 to [to] at x1 (x0 < x1 on screen).
                if (x1 - x0 <= 0f) return
                drawRect(Brush.horizontalGradient(listOf(from, to), startX = x0, endX = x1), Offset(x0, 0f), Size(x1 - x0, h), blendMode = BlendMode.DstIn)
            }
            if (scroll.value > 0) { if (rtl) rect(w - f, w, keep, clear) else rect(0f, f, clear, keep) }
            if (scroll.value < scroll.maxValue) {
                if (rtl) { rect(0f, r, clear, clear); rect(r, r + f, clear, keep) } else { rect(w - r, w, clear, clear); rect(w - r - f, w - r, keep, clear) }
            }
        }
        .semantics { chipFade = fadeState(scroll) }

/** The block a chip belongs to: 0 for All (fixed), 1 for collections, 2 for folders. */
private fun blockOf(c: FilesChip): Int = when { c.id == FileSourceIds.ALL -> 0; FileSourceIds.isFolder(c.id) -> 2; else -> 1 }

/** The first and last index of the chips that share [index]'s block, or null for a chip that cannot be moved. */
internal fun movableRange(chips: List<FilesChip>, index: Int): IntRange? {
    val b = blockOf(chips[index]); if (b == 0) return null
    val lo = chips.indexOfFirst { blockOf(it) == b }; val hi = chips.indexOfLast { blockOf(it) == b }
    return lo..hi
}

private class ChipDrag {
    var id by mutableStateOf<String?>(null)
    var from by mutableIntStateOf(-1)
    /** How far the chip was carried along the reading direction, in px, scrolling included. */
    var delta by mutableFloatStateOf(0f)
    /** Where the finger is in the viewport, along the reading direction. */
    var finger by mutableFloatStateOf(0f)
    var justDragged by mutableStateOf(false)
}

/** The source switcher: edge to edge chips that fade, can be reordered, and the pinned Add tile. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FilesSourceBar(
    chips: List<FilesChip>, selected: String, bright: Color, dim: Color, accent: Color, onSelect: (String) -> Unit, onAdd: () -> Unit,
    inset: Dp = 0.dp, onMove: (id: String, toIndex: Int) -> Unit = { _, _ -> },
) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val dir = if (rtl) -1f else 1f
    val haptic = LocalHapticFeedback.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val gap = 8.dp
    var addW by remember { mutableIntStateOf(0) }
    var viewportW by remember { mutableIntStateOf(0) }
    var contentW by remember { mutableFloatStateOf(0f) }
    val xs = remember { HashMap<String, Float>() }   // physical x in the row content, by chip id
    val ws = remember { HashMap<String, Float>() }
    val drag = remember { ChipDrag() }
    val chipsNow by rememberUpdatedState(chips)
    val onMoveNow by rememberUpdatedState(onMove)
    val gapPx = with(density) { gap.toPx() }
    val insetPx = with(density) { inset.toPx() }
    val reserve = with(density) { addW.toDp() } + inset + gap

    fun logicalStarts(): Pair<List<Float>, List<Float>> {
        val list = chipsNow
        val w = list.map { ws[it.id] ?: 0f }
        val s = list.mapIndexed { i, c -> val x = xs[c.id] ?: 0f; if (rtl) contentW - x - w[i] else x }
        return s to w
    }
    fun clampedDelta(): Float {
        val i = drag.from; val list = chipsNow; if (i !in list.indices) return 0f
        val r = movableRange(list, i) ?: return 0f
        val (s, w) = logicalStarts()
        return FilesChipReorder.clampDelta(s, w, i, drag.delta, r.first, r.last)
    }
    val target by remember {
        derivedStateOf {
            val i = drag.from; val list = chipsNow
            if (drag.id == null || i !in list.indices) -1 else {
                val r = movableRange(list, i)
                if (r == null) i else { val (s, w) = logicalStarts(); FilesChipReorder.targetIndex(s, w, i, drag.delta, r.first, r.last) }
            }
        }
    }
    fun end(commit: Boolean) {
        val id = drag.id; val to = target; val from = drag.from
        drag.id = null; drag.from = -1; drag.delta = 0f
        if (id != null) {
            drag.justDragged = true
            scope.launch { delay(350); drag.justDragged = false }
            if (commit && to >= 0 && to != from) onMoveNow(id, to)
        }
    }
    // While a chip is held near an edge, the row scrolls under it and the chip keeps its place under the finger.
    LaunchedEffect(drag.id) {
        if (drag.id == null) return@LaunchedEffect
        val zone = with(density) { FilesChipReorder.EDGE_ZONE_DP.dp.toPx() }; val max = with(density) { FilesChipReorder.MAX_STEP_DP.dp.toPx() }
        // Frames are asked for only while the finger is inside an edge zone, so a held chip in the middle costs nothing.
        snapshotFlow { FilesChipReorder.autoScrollStep(drag.finger, viewportW.toFloat(), zone, max) }.collectLatest { first ->
            if (first == 0f) return@collectLatest
            while (isActive && drag.id != null) {
                androidx.compose.runtime.withFrameNanos { }
                val step = FilesChipReorder.autoScrollStep(drag.finger, viewportW.toFloat(), zone, max)
                if (step == 0f) break
                val before = scroll.value
                scroll.scrollBy(step)
                if (scroll.value == before) break   // at the end of the row: nothing more to scroll, stop asking for frames
                drag.delta += (scroll.value - before)
            }
        }
    }

    Box(Modifier.fillMaxWidth().onSizeChanged { viewportW = it.width }) {
        Row(
            Modifier.fillMaxWidth().horizontalEdgeFade(scroll, EDGE_FADE, reserve).horizontalScroll(scroll).testTag("files.sources")
                .padding(start = inset, end = reserve).onSizeChanged { contentW = it.width.toFloat() },
            horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically,
        ) {
            chips.forEachIndexed { index, c ->
                androidx.compose.runtime.key(c.id) {
                    val on = c.id == selected
                    val shape = RoundedCornerShape(50)
                    val inSight = remember { BringIntoViewRequester() }
                    // Keep the selected chip in sight, beyond the stretch the Add tile and its fade take at the end.
                    LaunchedEffect(on, selected, chips.size) {
                        if (!on) return@LaunchedEffect
                        val w = ws[c.id] ?: 0f; val h = with(density) { 48.dp.toPx() }; val res = with(density) { reserve.toPx() }
                        if (w <= 0f) inSight.bringIntoView() else inSight.bringIntoView(androidx.compose.ui.geometry.Rect(if (rtl) -res else 0f, 0f, if (rtl) w else w + res, h))
                    }
                    val range = movableRange(chips, index)
                    val lifted = drag.id == c.id
                    val slideTarget = if (drag.id == null || lifted || target < 0) 0f else {
                        val (_, w) = logicalStarts(); FilesChipReorder.slide(drag.from, target, w, gapPx, index) * dir
                    }
                    val slide by animateFloatAsState(slideTarget, rememberMotionPrefs().gate(tween(120)), label = "chipSlide")
                    val earlier = L10n.tr("Move earlier"); val later = L10n.tr("Move later")
                    Box(
                        Modifier.heightIn(min = 48.dp).zIndex(if (lifted) 2f else 0f)
                            .onGloballyPositioned { xs[c.id] = it.positionInParent().x; ws[c.id] = it.size.width.toFloat() }
                            .testTag("files.source." + c.id).filesFocus("files.src." + c.id) { onSelect(c.id) }
                            .onKeyEvent { e ->
                                if (range == null || e.type != KeyEventType.KeyDown || !e.isAltPressed) return@onKeyEvent false
                                // Alt+Left moves the chip towards the screen's left, whichever way the language reads.
                                val step = FilesChipReorder.keyStep(true, when (e.key) { Key.DirectionLeft -> true; Key.DirectionRight -> false; else -> null }, rtl)
                                if (step == 0) return@onKeyEvent false
                                val to = index + step
                                if (to in range) { onMove(c.id, to); true } else true
                            }
                            .semantics {
                                if (range != null) {
                                    val acts = ArrayList<CustomAccessibilityAction>()
                                    if (index > range.first) acts += CustomAccessibilityAction(earlier) { onMove(c.id, index - 1); true }
                                    if (index < range.last) acts += CustomAccessibilityAction(later) { onMove(c.id, index + 1); true }
                                    customActions = acts
                                }
                            }
                            .selectable(selected = on, role = Role.RadioButton, onClick = { if (!drag.justDragged) onSelect(c.id) })
                            .then(
                                if (range == null) Modifier else Modifier.pointerInput(c.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { local ->
                                            val i = chipsNow.indexOfFirst { it.id == c.id }
                                            if (i >= 0) {
                                                val (s, w) = logicalStarts()
                                                val localL = if (rtl) (ws[c.id] ?: 0f) - local.x else local.x
                                                drag.from = i; drag.delta = 0f; drag.id = c.id
                                                drag.finger = s[i] + insetPx - scroll.value + localL
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            if (drag.id == c.id) {
                                                drag.delta += amount.x * dir
                                                drag.finger += amount.x * dir
                                                drag.delta = clampedDelta()
                                            }
                                        },
                                        onDragEnd = { end(true) },
                                        onDragCancel = { end(true) },
                                    )
                                },
                            )
                            .graphicsLayer {
                                if (lifted) {
                                    translationX = clampedDelta() * dir
                                    scaleX = 1.06f; scaleY = 1.06f
                                    shadowElevation = 10.dp.toPx(); this.shape = shape; clip = false
                                } else translationX = slide
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            Modifier.testTag("files.pill." + c.id).height(38.dp).background(if (on) accent.copy(alpha = .30f) else Color(0x14FFFFFF), shape).border(1.dp, if (on) accent else Color.Transparent, shape).padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            GlyphIcon(c.glyph, if (on) bright else dim, Modifier.size(16.dp))
                            Text(c.label, color = if (on) bright else dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
                        }
                    }
                }
            }
        }
        val addLabel = L10n.tr("Add a folder or a collection")
        Box(
            Modifier.align(Alignment.CenterEnd).padding(end = inset).heightIn(min = 48.dp).onSizeChanged { addW = it.width }.testTag("files.add").filesFocus("files.add", onAdd)
                .semantics { contentDescription = addLabel }.clickable(role = Role.Button, onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier.height(38.dp).border(1.dp, accent.copy(alpha = .7f), RoundedCornerShape(50)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GlyphIcon(Glyph.Plus, accent, Modifier.size(16.dp))
                Text(L10n.tr("Add"), color = accent, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}
