package com.bos.sphere.feature.grid

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.TolerantPrefs
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.rememberMotionPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Test tags of the preview window's parts. */
object PreviewWindowTags {
    const val BAR = "previewBar"
    const val MINIMISE = "previewMinimise"
    const val PILL = "previewPill"
    const val RESIZE = "previewResize"
    const val CONTENT = "previewContent"
}

/** What the window needs to know about the area it floats over; refreshed on every composition. */
private data class PreviewEnv(val areaW: Float, val areaH: Float, val aspect: Float, val defaultLong: Float, val rtl: Boolean, val reduced: Boolean, val top: Float = PreviewWindowGeometry.TOP_DEFAULT)

/**
 * All the window's moves in one place, used by touch, keys and the screen reader actions alike. Everything is in dp; the
 * model is only written from gesture and key handlers (never during composition).
 */
private class PreviewController(val model: PreviewWindowModel, private val env: () -> PreviewEnv, private val scope: CoroutineScope) {
    fun rtl(): Boolean = env().rtl

    fun long(): Float {
        val e = env()
        return if (model.long.isNaN()) PreviewWindowGeometry.clampLong(e.defaultLong, e.areaW, e.areaH) else PreviewWindowGeometry.clampLong(model.long, e.areaW, e.areaH)
    }

    fun win(): PreviewWindowGeometry.Size = PreviewWindowGeometry.windowSize(long(), env().aspect, model.minimised)

    fun pos(): PreviewWindowGeometry.Pos {
        val e = env()
        val w = win()
        val raw = if (model.x.isNaN() || model.y.isNaN()) PreviewWindowGeometry.defaultPos(e.areaW, w.w, e.rtl, e.top) else PreviewWindowGeometry.Pos(model.x, model.y)
        return PreviewWindowGeometry.clamp(raw.x, raw.y, w.w, w.h, e.areaW, e.areaH)
    }

    private fun place(x: Float, y: Float) {
        val e = env()
        val w = win()
        val c = PreviewWindowGeometry.clamp(x, y, w.w, w.h, e.areaW, e.areaH)
        model.x = c.x; model.y = c.y
    }

    fun moveBy(dx: Float, dy: Float) { val p = pos(); place(p.x + dx, p.y + dy) }

    fun resizeTo(newLong: Float) {
        val e = env()
        val p = pos()
        val oldW = win().w
        model.long = PreviewWindowGeometry.clampLong(newLong, e.areaW, e.areaH)
        val newW = win().w
        place(PreviewWindowGeometry.keepAnchor(p.x, oldW, newW, e.rtl), p.y)
    }

    fun resizeByHandle(dx: Float, dy: Float) {
        val e = env()
        resizeTo(PreviewWindowGeometry.resizedLong(long(), e.aspect, dx, dy, e.rtl, e.areaW, e.areaH))
    }

    fun resizeStep(sign: Int) { resizeTo(long() + sign * PreviewWindowGeometry.KEY_STEP); model.persist() }

    fun nudge(dx: Int, dy: Int) { moveBy(dx * PreviewWindowGeometry.KEY_STEP, dy * PreviewWindowGeometry.KEY_STEP); model.persist() }

    fun toggleMinimise() {
        val e = env()
        val p = pos()
        val oldW = win().w
        model.minimised = !model.minimised
        val newW = win().w
        place(PreviewWindowGeometry.keepAnchor(p.x, oldW, newW, !e.rtl) /* the end-side edge stays put: the pill sits where the window's end was */, p.y)
        model.persist()
    }

    fun reset() { model.resetPosition() }

    /** After a drag: a gentle snap to the nearest edge margin, instant under reduced motion; then remember. */
    fun settle() {
        val e = env()
        val w = win()
        val p = pos()
        val s = PreviewWindowGeometry.snap(p.x, p.y, w.w, w.h, e.areaW, e.areaH)
        if (e.reduced || (abs(s.x - p.x) < 0.5f && abs(s.y - p.y) < 0.5f)) { place(s.x, s.y); model.persist(); return }
        scope.launch {
            animate(0f, 1f, animationSpec = tween(140)) { f, _ -> place(p.x + (s.x - p.x) * f, p.y + (s.y - p.y) * f) }
            place(s.x, s.y)
            model.persist()
        }
    }
}

/**
 * CFG-18 (wave 39): the floating preview window. A bar (grip, title, minimise button) on top of the scaled live home. It can be
 * dragged by the bar, resized by the corner handle (or a two-finger pinch on the picture), minimised to a pill, moved and resized
 * with keys and with screen reader actions. Position and size are remembered per orientation, the minimised state as well.
 */
@Composable
internal fun PreviewWindow(capture: HomeCapture, modifier: Modifier = Modifier, rtlOverride: Boolean? = null) {
    val context = LocalContext.current
    val store = remember(context) { PreviewWindowStore(TolerantPrefs.of(context.applicationContext, PreviewWindowStore.FILE)) }
    val rtl = rtlOverride ?: (LocalConfiguration.current.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL)
    val reduced = rememberMotionPrefs().reduced
    val density = LocalDensity.current.density
    val conf = LocalConfiguration.current // the default size is what the fixed dock had: from the screen's configured size
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val areaW = maxWidth.value
        val areaH = maxHeight.value
        val landscape = areaW > areaH
        val model = remember(store, landscape) { PreviewWindowModel(store, landscape) }
        val hw = capture.size.width
        val hh = capture.size.height
        val env = PreviewEnv(areaW, areaH, hw.toFloat() / hh, PreviewWindowGeometry.defaultLong(conf.screenWidthDp.toFloat(), conf.screenHeightDp.toFloat(), hw, hh), rtl, reduced, LocalPreviewTop.current)
        val envState = rememberUpdatedState(env)
        val ctl = remember(model) { PreviewController(model, { envState.value }, scope) }
        val win = ctl.win()
        val long = ctl.long()
        val content = PreviewWindowGeometry.contentSize(long, env.aspect)
        val shape = RoundedCornerShape(if (model.minimised) 20.dp else 12.dp)
        var focused by remember { mutableStateOf(false) }
        val title = L10n.tr("Preview")
        val actions = listOf(
            CustomAccessibilityAction(L10n.tr("Move up")) { ctl.nudge(0, -1); true },
            CustomAccessibilityAction(L10n.tr("Move down")) { ctl.nudge(0, 1); true },
            CustomAccessibilityAction(L10n.tr("Move left")) { ctl.nudge(-1, 0); true },
            CustomAccessibilityAction(L10n.tr("Move right")) { ctl.nudge(1, 0); true },
            CustomAccessibilityAction(L10n.tr("Larger")) { ctl.resizeStep(1); true },
            CustomAccessibilityAction(L10n.tr("Smaller")) { ctl.resizeStep(-1); true },
            CustomAccessibilityAction(L10n.tr(if (model.minimised) "Restore" else "Minimise")) { ctl.toggleMinimise(); true },
            CustomAccessibilityAction(L10n.tr("Reset position")) { ctl.reset(); true },
        )
        // Left-to-right and right-to-left follow the device here (Ground Control itself is forced left-to-right), so the grip and the handle mirror.
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Box(
                Modifier
                    .size(win.w.dp, win.h.dp)
                    .graphicsLayer { val p = ctl.pos(); translationX = p.x * density; translationY = p.y * density }
                    .shadow(6.dp, shape)
                    .clip(shape)
                    .background(HyleColors.Surface)
                    .border(if (focused) 2.dp else 1.dp, if (focused) HyleColors.Ink else HyleColors.InsetHighlight, shape)
                    // Nothing under the window receives a touch that lands on it.
                    .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume() } },
            ) {
                if (model.minimised) {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .testTag(PreviewWindowTags.PILL)
                            .barGestures(ctl, tapRestores = true)
                            .barKeys(ctl)
                            .onFocusChanged { focused = it.isFocused }
                            .focusable()
                            .semantics {
                                contentDescription = L10n.tr("Restore")
                                role = Role.Button
                                onClick { ctl.toggleMinimise(); true }
                                customActions = actions
                            }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Grip()
                        BasicText(title, Modifier.weight(1f), style = TextStyle(color = HyleColors.Ink, fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Glyph(restore = true)
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(PreviewWindowGeometry.BAR_DP.dp)
                                .testTag(PreviewWindowTags.BAR)
                                .barGestures(ctl, tapRestores = false)
                                .barKeys(ctl)
                                .onFocusChanged { focused = it.isFocused }
                                .focusable()
                                .semantics {
                                    contentDescription = L10n.tr("Preview window")
                                    customActions = actions
                                }
                                .padding(start = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Grip()
                            BasicText(title, Modifier.weight(1f), style = TextStyle(color = HyleColors.Ink, fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .testTag(PreviewWindowTags.MINIMISE)
                                    .onKeyEvent { e ->
                                        if (e.type == KeyEventType.KeyDown && (e.key == Key.Escape || e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionCenter)) { ctl.toggleMinimise(); true } else false
                                    }
                                    .focusable()
                                    .clickable(onClickLabel = null, role = Role.Button) { ctl.toggleMinimise() }
                                    .semantics { contentDescription = L10n.tr("Minimise") },
                                contentAlignment = Alignment.Center,
                            ) { Glyph(restore = false) }
                        }
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
                            PreviewContent(capture, ctl, content.w, content.h)
                        }
                    }
                    ResizeHandle(ctl, Modifier.align(Alignment.BottomEnd))
                }
            }
        }
    }
}

/** The scaled live home. No clicks, no focus; a two-finger pinch resizes the window. */
@Composable
private fun PreviewContent(capture: HomeCapture, ctl: PreviewController, w: Float, h: Float) {
    val label = L10n.tr("Live preview of your home")
    Box(
        Modifier
            .size(w.dp, h.dp)
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    var prev = 0f
                    var pinched = false
                    do {
                        val ev = awaitPointerEvent()
                        val down = ev.changes.filter { it.pressed }
                        if (down.size >= 2) {
                            val span = (down[0].position - down[1].position).getDistance()
                            if (prev > 0f && span > 0f) ctl.resizeTo(ctl.long() * span / prev)
                            prev = span
                            pinched = true
                            ev.changes.forEach { it.consume() }
                        } else prev = 0f
                    } while (ev.changes.any { it.pressed })
                    if (pinched) ctl.model.persist()
                }
            }
            .semantics { contentDescription = label }
            .testTag(PreviewWindowTags.CONTENT),
    ) {
        Canvas(Modifier.size(w.dp, h.dp)) {
            val k = DockGeometry.scale(capture.size.width, size.width)
            clipRect { scale(k, k, pivot = Offset.Zero) { drawLayer(capture.wallpaperLayer); drawLayer(capture.layer) } }
        }
    }
}

/** The 48 dp diagonal grip at the bottom-end corner. */
@Composable
private fun ResizeHandle(ctl: PreviewController, modifier: Modifier) {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current.density
    val label = L10n.tr("Resize preview")
    Box(
        modifier
            .size(48.dp)
            .testTag(PreviewWindowTags.RESIZE)
            .onGloballyPositioned { coords = it }
            .rootDrag({ coords }, onDelta = { d -> ctl.resizeByHandle(d.x / density, d.y / density) }, onEnd = { ctl.model.persist() })
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.BottomEnd,
    ) {
        val ink = HyleColors.InkDim
        Canvas(Modifier.padding(6.dp).size(16.dp)) {
            val rtl = layoutDirection == LayoutDirection.Rtl
            fun line(f: Float) {
                // diagonals parallel to the corner's diagonal, nearest the corner at f = 0
                val a = if (rtl) Offset(0f + size.width * f, size.height) else Offset(size.width - size.width * f, size.height)
                val b = if (rtl) Offset(0f, size.height - size.height * f) else Offset(size.width, size.height - size.height * f)
                drawLine(ink, a, b, strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
            }
            line(0.35f); line(0.7f); line(1f)
        }
    }
}

@Composable
private fun Grip() {
    val ink = HyleColors.InkDim
    Canvas(Modifier.size(12.dp, 16.dp)) {
        for (row in 0..2) for (col in 0..1) drawCircle(ink, 1.3.dp.toPx(), Offset(size.width * (0.25f + col * 0.5f), size.height * (0.15f + row * 0.35f)))
    }
}

/** The minimise line, or the restore square. */
@Composable
private fun Glyph(restore: Boolean) {
    val ink = HyleColors.Ink
    Canvas(Modifier.size(16.dp)) {
        val sw = 1.8.dp.toPx()
        if (restore) drawRect(ink, Offset(sw, sw), androidx.compose.ui.geometry.Size(size.width - 2 * sw, size.height - 2 * sw), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
        else drawLine(ink, Offset(size.width * 0.15f, size.height * 0.75f), Offset(size.width * 0.85f, size.height * 0.75f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

/** Drag the window by this node, double tap resets the position; on the pill a single tap restores. */
@Composable
private fun Modifier.barGestures(ctl: PreviewController, tapRestores: Boolean): Modifier {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current.density
    return this
        .onGloballyPositioned { coords = it }
        .pointerInput(tapRestores) {
            // the bar and the pill carry the semantics (name and the eight custom actions); these gestures are the touch route to the same actions
            detectTapGestures(
                onDoubleTap = { ctl.reset() },
                onTap = { if (tapRestores) ctl.toggleMinimise() },
            )
        }
        .rootDrag({ coords }, onDelta = { d -> ctl.moveBy(d.x / density, d.y / density) }, onEnd = { ctl.settle() })
}

/** Arrow keys move by 16 dp, Shift+arrows resize, Enter or Escape on the pill restores it. */
private fun Modifier.barKeys(ctl: PreviewController): Modifier = onKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
    val dir = when (e.key) {
        Key.DirectionLeft -> -1 to 0
        Key.DirectionRight -> 1 to 0
        Key.DirectionUp -> 0 to -1
        Key.DirectionDown -> 0 to 1
        else -> null
    }
    when {
        dir != null && e.isShiftPressed -> { ctl.resizeStep(if ((if (ctl.rtl()) -dir.first else dir.first) + dir.second > 0) 1 else -1); true }
        dir != null -> { ctl.nudge(dir.first, dir.second); true }
        ctl.model.minimised && (e.key == Key.Enter || e.key == Key.Escape || e.key == Key.NumPadEnter) -> { ctl.toggleMinimise(); true }
        else -> false
    }
}

/**
 * A drag measured in the window's root coordinates, so a node that moves or grows under the finger does not distort the deltas.
 * Starts after the touch slop; ordinary taps pass through. Consumes only once it is a drag.
 */
private fun Modifier.rootDrag(coords: () -> LayoutCoordinates?, onDelta: (Offset) -> Unit, onEnd: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val c0 = coords() ?: return@awaitEachGesture
        var prev = c0.localToRoot(down.position)
        val start = prev
        var dragging = false
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Main)
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
            if (!ch.pressed) break
            val c = coords() ?: break
            val now = c.localToRoot(ch.position)
            if (!dragging && (now - start).getDistance() > viewConfiguration.touchSlop) dragging = true
            if (dragging) {
                val d = now - prev
                if (d != Offset.Zero) onDelta(d)
                ch.consume()
            }
            prev = now
        }
        if (dragging) onEnd()
    }
}
