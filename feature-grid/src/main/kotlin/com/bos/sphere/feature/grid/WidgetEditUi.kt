package com.bos.sphere.feature.grid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.catalog.CellRect
import com.bos.sphere.core.catalog.EditCollision
import com.bos.sphere.core.catalog.EditVerdict
import com.bos.sphere.core.catalog.HandleSet
import com.bos.sphere.core.catalog.ProviderLimitsMath
import com.bos.sphere.core.catalog.ResizeHandle
import com.bos.sphere.core.catalog.ResizeMath
import com.bos.sphere.core.catalog.ResizeResult
import com.bos.sphere.core.catalog.TilePlacement
import com.bos.sphere.core.catalog.WidgetSizing
import com.bos.sphere.core.catalog.WidgetSpan
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.rememberMotionPrefs
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/** Wave 45 (editHandles): the measures of the selection chrome, shared by the widget frame, the icon outline and the tests. */
object EditChrome {
    /** The knob the user sees: a filled circle with a white ring. */
    val KnobVisible: Dp = 12.dp
    /** The touch target around a knob (48 dp, the Android minimum). */
    val KnobHit: Dp = 48.dp
    /** How far the knob's centre sits outside the content edge: the knob touches the content but never covers it. */
    val KnobOut: Dp = 6.dp
    /** The selection outline: 2 dp, drawn this far outside the content. */
    val OutlineStroke: Dp = 2.dp
    val OutlineGap: Dp = 3.dp
    val ChipHeight: Dp = 24.dp
    val GhostStroke: Dp = 2.dp
    val WidgetRadius: Dp = 16.dp
    val Error: Color = Color(0xFFE5484D)
}

/** Where a widget sits on its page's grid, for the frame: the cell rectangle, the grid, the pitch (px), the other items. */
data class ResizeContext(
    val start: CellRect,
    val gridCols: Int,
    val gridRows: Int,
    val pitchX: Float,
    val pitchY: Float,
    /** Right-to-left home order: logical column 0 is the physical right-most one. */
    val mirror: Boolean = false,
    /** False where items flow (lists, bands): then only the south and east handles exist. */
    val originMoves: Boolean = true,
    /** Everything else on the page, in cells: a widget cannot grow over it. */
    val placed: List<TilePlacement> = emptyList(),
    val pageKeys: List<String> = emptyList(),
    val pageIndex: Int = 0,
    val onRemove: () -> Unit = {},
) {
    companion object {
        /** For surfaces whose items flow: the widget's own size at the origin, no neighbours known. */
        fun flowing(span: WidgetSpan, grid: Pair<Int, Int>, pitchX: Float, pitchY: Float) =
            ResizeContext(CellRect(0, 0, span.cols, span.rows), grid.first, grid.second, pitchX, pitchY, originMoves = false)
    }
}

/** The 2 dp primary outline 3 dp outside a selected item (never clipped against its content, never doubled). [radius] is the item's own corner radius. */
fun Modifier.selectionOutline(selected: Boolean, radius: Dp): Modifier = if (!selected) this else drawBehind {
    val g = (EditChrome.OutlineGap + EditChrome.OutlineStroke / 2).toPx()
    drawRoundRect(
        HyleColors.Violet, Offset(-g, -g), Size(size.width + 2 * g, size.height + 2 * g),
        CornerRadius((radius + EditChrome.OutlineGap).toPx()), style = Stroke(EditChrome.OutlineStroke.toPx()),
    )
}

private data class Preview(val result: ResizeResult, val blocked: Boolean) {
    val rect: CellRect get() = result.rect
    val error: Boolean get() = blocked || result.limited
}

private class Edges(val l: Float, val t: Float, val r: Float, val b: Float)

/**
 * HOM-07 / HOM-12: the selected widget. Outline outside the content, the handles the provider allows (corners and edge middles;
 * west and north ones move the origin), the content following the finger (one graphicsLayer, no relayout per frame), a dashed ghost
 * on the cells it will take, a size chip, brighter grid dots round the ghost; the result applies on release with a spring. Limits
 * and neighbours clamp with a reject buzz and an error-coloured chip and ghost. Keyboard: Shift + arrows resize, arrows move,
 * Delete removes. One Done only: this frame has none.
 */
@Composable
fun SelectedWidgetFrame(
    key: String,
    extras: HomeExtras,
    ctx: ResizeContext,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val d = density.density
    val haptics = rememberLauncherHaptics()
    val reduced = rememberMotionPrefs().reduced
    val scope = rememberCoroutineScope()
    val extrasNow by rememberUpdatedState(extras)
    val ctxNow by rememberUpdatedState(ctx)
    val primary = HyleColors.Violet

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val limits = remember(key, ctx.start, ctx.gridCols, ctx.gridRows, ctx.pitchX, ctx.pitchY, extras.limitsOf(key)) {
        ProviderLimitsMath.toCells(extras.limitsOf(key), ctx.pitchX / d, ctx.pitchY / d, ctx.gridCols, ctx.gridRows, ctx.start.span)
    }
    val limitsNow by rememberUpdatedState(limits)
    val handles = remember(limits, boxSize, ctx.originMoves) { HandleSet.of(limits, boxSize.width / d, boxSize.height / d, ctx.originMoves) }

    var active by remember { mutableStateOf<ResizeHandle?>(null) }
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    val settle = remember { Animatable(0f) }
    var fromL by remember { mutableFloatStateOf(0f) }
    var fromT by remember { mutableFloatStateOf(0f) }
    var fromR by remember { mutableFloatStateOf(0f) }
    var fromB by remember { mutableFloatStateOf(0f) }

    fun previewOf(h: ResizeHandle, ddx: Float, ddy: Float): Preview {
        val c = ctxNow
        val lh = if (c.mirror) h.mirrored(true, false) else h
        val r = ResizeMath.resizePx(c.start, lh, if (c.mirror) -ddx else ddx, ddy, c.pitchX, c.pitchY, limitsNow, c.gridCols, c.gridRows)
        return Preview(r, EditCollision.resizeVerdict(c.placed, key, r.rect) is EditVerdict.Blocked)
    }

    val preview by remember { derivedStateOf { active?.let { previewOf(it, dx, dy) } } }

    /** The snapped cell rectangle as pixel edges of the widget's own box (the cells' padding is the widget's own). */
    fun ghostEdges(p: Preview, w: Float, h: Float): Edges {
        val c = ctxNow
        val dL = (p.rect.col - c.start.col) * c.pitchX
        val dR = (p.rect.right - c.start.right) * c.pitchX
        val dT = (p.rect.row - c.start.row) * c.pitchY
        val dB = (p.rect.bottom - c.start.bottom) * c.pitchY
        return if (c.mirror) Edges(-dR, dT, w - dL, h + dB) else Edges(dL, dT, w + dR, h + dB)
    }

    /** What the content shows right now: the finger while dragging (rubber-banded to the ghost), the spring afterwards, else its own box. */
    fun edges(): Edges {
        val w = boxSize.width.toFloat(); val h = boxSize.height.toFloat()
        val a = active
        val p = preview
        if (a != null && p != null) {
            val g = ghostEdges(p, w, h)
            val c = ctxNow
            val rx = c.pitchX * 0.5f; val ry = c.pitchY * 0.5f
            val minW = c.pitchX * 0.4f; val minH = c.pitchY * 0.4f
            var l = 0f; var r = w; var t = 0f; var b = h
            if (a.west) l = dx.coerceIn(g.l - rx, g.l + rx) else if (a.east) r = (w + dx).coerceIn(g.r - rx, g.r + rx)
            if (a.north) t = dy.coerceIn(g.t - ry, g.t + ry) else if (a.south) b = (h + dy).coerceIn(g.b - ry, g.b + ry)
            if (r - l < minW) { if (a.west) l = r - minW else r = l + minW }
            if (b - t < minH) { if (a.north) t = b - minH else b = t + minH }
            return Edges(l, t, r, b)
        }
        val s = settle.value
        if (s <= 0f) return Edges(0f, 0f, w, h)
        return Edges(fromL * s, fromT * s, w + (fromR - w) * s, h + (fromB - h) * s)
    }

    fun release() {
        val a = active ?: return
        val p = preview
        val w = boxSize.width.toFloat(); val h = boxSize.height.toFloat()
        val live = edges()
        active = null
        if (p == null) { dx = 0f; dy = 0f; return }
        val c = ctxNow
        val accepted = !p.blocked && p.rect != c.start
        if (!accepted && p.blocked) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val g = if (accepted) ghostEdges(p, w, h) else Edges(0f, 0f, w, h)
        fromL = live.l - g.l; fromT = live.t - g.t; fromR = live.r - g.l; fromB = live.b - g.t
        dx = 0f; dy = 0f
        if (accepted) {
            extrasNow.onResizeTo(key, p.rect, c.originMoves && (p.rect.col != c.start.col || p.rect.row != c.start.row))
            extrasNow.selection.announce(L10n.tr("Resized to %d by %d", p.rect.cols, p.rect.rows))
        } else if (p.blocked) extrasNow.selection.announce(L10n.tr("Can't resize: another item is in the way"))
        else if (p.result.limited) extrasNow.selection.announce(
            if (p.result.reason == com.bos.sphere.core.catalog.LimitReason.AtMinimum) L10n.tr("Can't resize: at the minimum") else L10n.tr("Can't resize: at the maximum"),
        )
        scope.launch {
            settle.snapTo(1f)
            if (reduced) settle.snapTo(0f) else settle.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 500f))
        }
    }

    val fr = remember { FocusRequester() }
    LaunchedEffect(key) { runCatching { fr.requestFocus() } }

    fun editor() = ItemEditor(key, extrasNow, ctxNow.pageKeys.ifEmpty { listOf(key) }, ctxNow.pageIndex, ctxNow.gridCols, ctxNow.gridRows, ctxNow.mirror, ctxNow.originMoves, ctxNow.onRemove)

    Box(
        modifier
            .childrenOutOfBounds()
            .onSizeChanged { boxSize = it }
            .onGloballyPositioned { extrasNow.selection.report(key, it.boundsInRoot()) }
            .semantics { selected = true }
            .focusRequester(fr)
            .onKeyEvent { e ->
                val arrow = when (e.key) {
                    Key.DirectionRight -> WidgetSizing.Arrow.Right
                    Key.DirectionLeft -> WidgetSizing.Arrow.Left
                    Key.DirectionDown -> WidgetSizing.Arrow.Down
                    Key.DirectionUp -> WidgetSizing.Arrow.Up
                    else -> null
                }
                when {
                    arrow != null -> {
                        if (e.type == KeyEventType.KeyUp) {
                            val (c, r) = WidgetSizing.deltaForKey(arrow)
                            if (e.isShiftPressed) editor().resize(c, r) else editor().move(c, r)
                        }
                        true
                    }
                    e.key == Key.Delete || e.key == Key.Backspace -> { if (e.type == KeyEventType.KeyUp) editor().remove(); true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        // The content, following the finger; clipped to the widget's own rounded shape.
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    val e = edges()
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = e.l; translationY = e.t
                    scaleX = ((e.r - e.l) / max(1, boxSize.width)).coerceAtLeast(0.05f)
                    scaleY = ((e.b - e.t) / max(1, boxSize.height)).coerceAtLeast(0.05f)
                    shape = RoundedCornerShape(EditChrome.WidgetRadius); clip = true
                },
        ) { content(); Box(Modifier.fillMaxSize().editTouchShield()) }

        // The selection outline, 3 dp outside the content edges (which move with the finger).
        Box(
            Modifier.matchParentSizeCompat().drawBehind {
                val e = edges()
                val g = (EditChrome.OutlineGap + EditChrome.OutlineStroke / 2).toPx()
                val col = if (preview?.error == true) EditChrome.Error else primary
                drawRoundRect(
                    col, Offset(e.l - g, e.t - g), Size(e.r - e.l + 2 * g, e.b - e.t + 2 * g),
                    CornerRadius((EditChrome.WidgetRadius + EditChrome.OutlineGap).toPx()), style = Stroke(EditChrome.OutlineStroke.toPx()),
                )
            },
        )

        // The ghost: the snapped cells the widget will take, dashed, with the grid dots around it brightened.
        val p = preview
        if (p != null && boxSize.width > 0) {
            val g = ghostEdges(p, boxSize.width.toFloat(), boxSize.height.toFloat())
            val col = if (p.error) EditChrome.Error else primary
            val padX = (ctx.start.cols * ctx.pitchX - boxSize.width) / 2f
            val padY = (ctx.start.rows * ctx.pitchY - boxSize.height) / 2f
            // The ghost may be larger than the widget's own box, which would constrain its children: measure it unbounded, from the top start.
            Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)) {
            Box(
                Modifier.offset { IntOffset(g.l.toInt(), g.t.toInt()) }
                    .size(with(density) { (g.r - g.l).toDp() }, with(density) { (g.b - g.t).toDp() })
                    .clearAndSetSemantics { testTag = "resize-ghost" }
                    .drawBehind {
                        val r = (EditChrome.WidgetRadius).toPx()
                        drawRoundRect(col.copy(alpha = 0.15f), cornerRadius = CornerRadius(r))
                        drawRoundRect(col, cornerRadius = CornerRadius(r), style = Stroke(EditChrome.GhostStroke.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))))
                        // Grid dots at the cell corners within one cell of the ghost.
                        val c = ctxNow
                        val x0 = -padX; val y0 = -padY
                        val cw = size.width + 2 * padX; val ch = size.height + 2 * padY
                        val nx = ((cw / c.pitchX) + 0.5f).toInt(); val ny = ((ch / c.pitchY) + 0.5f).toInt()
                        for (i in -1..nx + 1) for (j in -1..ny + 1) {
                            drawCircle(Color.White.copy(alpha = 0.55f), 1.5.dp.toPx(), Offset(x0 + i * c.pitchX, y0 + j * c.pitchY))
                        }
                    },
            )
            }
        }

        // The handles: edges first, corners on top where hit areas meet.
        val e = edges()
        val w = boxSize.width.toFloat(); val h = boxSize.height.toFloat()
        val out = with(density) { EditChrome.KnobOut.toPx() }
        val hit = with(density) { EditChrome.KnobHit.toPx() }
        handles.sortedBy { if (it.isCorner) 1 else 0 }.forEach { handle ->
            val restX = when { handle.west -> -out; handle.east -> w + out; else -> w / 2f }
            val restY = when { handle.north -> -out; handle.south -> h + out; else -> h / 2f }
            Box(
                Modifier
                    .offset { IntOffset((restX - hit / 2f).toInt(), (restY - hit / 2f).toInt()) }
                    .size(EditChrome.KnobHit)
                    .graphicsLayer {
                        val cur = edges()
                        val nx = when { handle.west -> cur.l - out; handle.east -> cur.r + out; else -> (cur.l + cur.r) / 2f }
                        val ny = when { handle.north -> cur.t - out; handle.south -> cur.b + out; else -> (cur.t + cur.b) / 2f }
                        translationX = nx - restX; translationY = ny - restY
                        alpha = if (active != null && active != handle) 0f else 1f
                    }
                    .clearAndSetSemantics { testTag = "resize-handle-${handle.name}" }
                    .pointerInput(handle) {
                        // Custom drag (not detectDragGestures): the press itself is consumed at once, so the page's own gesture arbiter never
                        // takes a press on a knob (half of it lies over a neighbouring icon) for a grab of that icon.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            active = handle; dx = 0f; dy = 0f
                            var lastRect: CellRect? = ctxNow.start
                            var lastError = false
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) { c.consume(); release(); return@awaitEachGesture }
                                val d = c.positionChange()
                                c.consume()
                                dx += d.x; dy += d.y
                                val pv = previewOf(handle, dx, dy)
                                if (pv.error && !lastError) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                else if (!pv.error && pv.rect != lastRect) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastError = pv.error; lastRect = pv.rect
                            }
                            active = null; dx = 0f; dy = 0f
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(EditChrome.KnobVisible).background(primary, CircleShape).border(2.dp, Color.White, CircleShape).testTag("resize-knob"))
            }
        }

        // The size chip, near the active handle.
        val a = active
        if (p != null && a != null) {
            val chipW = with(density) { 60.dp.toPx() }
            val cx = when { a.west -> e.l; a.east -> e.r; else -> (e.l + e.r) / 2f }
            val cy = when { a.north -> e.t; a.south -> e.b; else -> (e.t + e.b) / 2f }
            val above = cy > with(density) { 48.dp.toPx() }
            Box(
                Modifier
                    .offset { IntOffset((cx - chipW / 2f).toInt(), (if (above) cy - 24.dp.toPx() - 16.dp.toPx() else cy + 18.dp.toPx()).toInt()) }
                    .height(EditChrome.ChipHeight)
                    .testTag("resize-chip")
                    .background(if (p.error) EditChrome.Error else primary, RoundedCornerShape(EditChrome.ChipHeight / 2))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("${p.rect.cols} x ${p.rect.rows}", color = if (p.error) Color.White else HyleColors.OnAccent, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxSize()

/**
 * The handles sit half outside the widget's box, and Compose only hit-tests a child inside its parent's bounds: every box above the
 * handles up to the page's own area must let its children's touches through from outside it. Put this on the widget's slot.
 */
fun Modifier.childrenOutOfBounds(): Modifier = this then OutOfBoundsElement

private object OutOfBoundsElement : androidx.compose.ui.node.ModifierNodeElement<OutOfBoundsNode>() {
    override fun create() = OutOfBoundsNode()
    override fun update(node: OutOfBoundsNode) {}
    override fun hashCode() = 7
    override fun equals(other: Any?) = other === this
}

private class OutOfBoundsNode : Modifier.Node(), androidx.compose.ui.node.PointerInputModifierNode {
    override fun onPointerEvent(pointerEvent: androidx.compose.ui.input.pointer.PointerEvent, pass: androidx.compose.ui.input.pointer.PointerEventPass, bounds: IntSize) {}
    override fun onCancelPointerInput() {}
    override fun interceptOutOfBoundsChildEvents() = true
    override fun sharePointerInputWithSiblings() = true
}

/**
 * Edit mode: the hosted widget view must not get the touch (an AppWidgetHostView swallows taps and drags, so the item could be neither
 * selected nor moved). This sits above it, takes the events so the view beneath is not hit, and consumes nothing, so the page's own
 * gesture arbiter still sees the tap, the long press and the drag.
 */
fun Modifier.editTouchShield(): Modifier = pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
