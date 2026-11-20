package com.bos.sphere.feature.grid

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.rememberMotionPrefs
import com.bos.sphere.core.input.FocusKind
import com.bos.sphere.core.input.InputMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * One section of the all-apps disc (wave 30): what the wedge shows and does. A section with neither [glyph] nor [app] is an empty slot.
 * [available] false draws it dimmed (the tap still runs [onClick], which explains why it cannot work); [onLongClick] is the empty slot's
 * "open the editor" (a filled one shows its name instead).
 */
class PuckAction(
    val label: String,
    val glyph: ImageVector? = null,
    val app: AppEntry? = null,
    val available: Boolean = true,
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit,
) {
    val empty: Boolean get() = glyph == null && app == null
}

/** The Back arrow on the hub's centre button. */
internal val BackGlyph: ImageVector = ImageVector.Builder("back", 24.dp, 24.dp, 24f, 24f).apply {
    path(
        stroke = SolidColor(Color.White), strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(19f, 12f); lineTo(5f, 12f)
        moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
    }
}.build()

/**
 * Colours of the disc (wave 32), after the Concepts quick switcher: a solid ring with hairline dividers, glyphs in the divider colour, a hub
 * in the opposite tone. Dark themes: white ring, black glyphs and dividers, black hub with a white arrow. Light theme: a near-black ring
 * (relative luminance about 0.12) with white glyphs and dividers, a white hub with a black arrow, so it still contrasts with the light drawer.
 */
internal class PuckLook(
    val ring: Color, val pressed: Color, val divider: Color, val glyph: Color, val hub: Color, val hubPressed: Color, val hubGlyph: Color,
) {
    companion object {
        /** Share of the glyph's opacity for an empty slot's plus and for an unavailable action. */
        const val EMPTY_ALPHA = 0.40f
        const val UNAVAILABLE_ALPHA = 0.38f
        val Dark = PuckLook(
            ring = Color.White, pressed = Color(0xFFD9D9D9), divider = Color.Black, glyph = Color.Black,
            hub = Color.Black, hubPressed = Color(0xFF1C1C1C), hubGlyph = Color.White,
        )
        val Light = PuckLook(
            ring = Color(0xFF616161), pressed = Color(0xFF3A3A3A), divider = Color.White, glyph = Color.White,
            hub = Color.White, hubPressed = Color(0xFFDDDDDD), hubGlyph = Color.Black,
        )
        fun of(light: Boolean): PuckLook = if (light) Light else Dark
    }
}


/**
 * Wave 38 (drawerRing): the state of the turning ring. [p] is its position in segment units (see `PuckSectionsGeometry`): a whole number at
 * rest, so a segment sits centred. It is a plain snapshot float, so the drawing, the glyph placement and the touch nodes read it in the draw and
 * layout phases and a turning ring never recomposes. [pressed] is the segment under a finger ([HUB] for the hub, [NONE] for neither).
 */
internal class PuckRingState(initial: Float = DEFAULT_POSITION) {
    var p by mutableFloatStateOf(initial)
    var pressed by mutableIntStateOf(NONE)
    /** The segment (or [HUB]) that has keyboard focus, [NONE] for none: plain Left/Right turn the ring only while one does. */
    var focusedItem by mutableIntStateOf(NONE)
    /** The long-press name pill's segment. */
    var tip by mutableIntStateOf(NONE)
    private var job: Job? = null
    private var heading: Float? = null

    val ringFocused: Boolean get() = focusedItem != NONE

    /** A finger takes over: any turning stops where it is. */
    fun stop() { job?.cancel(); job = null; heading = null }

    /** Follows a finger. */
    fun dragBy(delta: Float) { p += delta }

    /** Comes to rest on [target] (a whole position): a spring, or at once with reduced motion. Wrapped into 0 until [n] at the end. */
    fun settleTo(scope: CoroutineScope, target: Float, n: Int, reduced: Boolean) {
        stop()
        heading = target
        if (reduced) { p = PuckSectionsGeometry.wrap(target, n); heading = null; return }
        job = scope.launch {
            animate(p, target, animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { v, _ -> p = v }
            p = PuckSectionsGeometry.wrap(p, n)
            heading = null
        }
    }

    /** Keyboard, TalkBack and the scroll wheel: [steps] whole segments from where the ring is heading (a second press while turning adds up). */
    fun turnBy(scope: CoroutineScope, steps: Int, n: Int, reduced: Boolean) {
        val from = heading ?: p.roundToInt().toFloat()
        settleTo(scope, from + steps, n, reduced)
    }

    companion object {
        const val NONE = -1
        const val HUB = -2
        /** At rest segment 1 is centred, so slot 0 (flashlight) sits on the thumb's side with 1 in the middle and 2 beyond. */
        const val DEFAULT_POSITION = 1f
    }
}

/** Everything the layer needs to place one segment's glyph, built once per ring size (never per frame). */
private fun angleOf(v: Offset): Float = Math.toDegrees(atan2(v.y.toDouble(), v.x.toDouble())).toFloat()

/**
 * The shortcut ring around the Back hub, drawn like the ring of the Concepts quick switcher: a full white annulus cut into [n] equal segments
 * by hairline dividers, clipped only by the window, rotatable. [corner] is the wheel's centre in px inside this box and [window] the same in dp.
 * [actions] are the slots in order (padded with empty ones to [n]); [onBack] closes the drawer.
 *
 * Touch: one circular overlay owns every touch that starts on the hub or the ring (a tap runs the segment's action or Back, a long press names it
 * or opens the editor for an empty one, a drag turns the ring about the centre and snaps to a segment on release); touches outside the ring are
 * the outer wheel's (see `WheelContent`). The segments and the hub are also accessibility nodes with the same click.
 */
@Composable
internal fun BoxScope.PuckSectionsLayer(
    anchor: Corner,
    corner: Offset,
    ring: SectionRing,
    window: RingWindow,
    n: Int,
    state: PuckRingState,
    showLabels: Boolean,
    actions: List<PuckAction>,
    interactive: Boolean = true,
    onBack: () -> Unit,
) {
    val haptics = rememberLauncherHaptics()
    val density = LocalDensity.current
    val d = density.density
    val scope = rememberCoroutineScope()
    val look = PuckLook.of(HyleTheme.isLight)
    val accent = HyleColors.Violet
    val reduced = rememberMotionPrefs().reduced
    val reducedNow by rememberUpdatedState(reduced)
    val keyboard = LocalInputMode.current.value == InputMode.Keyboard
    val hairline = DIVIDER_PX(d)
    val seg = PuckSectionsGeometry.segmentDeg(n)
    val currentActions by rememberUpdatedState(actions)
    val currentOnBack by rememberUpdatedState(onBack)
    val pressAnim = animateFloatAsState(if (state.pressed >= 0) 1f else 0f, if (reduced) snap() else tween(90), label = "puckPress")
    var lastPressed by remember { mutableIntStateOf(0) }
    if (state.pressed >= 0) lastPressed = state.pressed
    var tipToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.tip, tipToken) { if (state.tip >= 0) { delay(1800); state.tip = PuckRingState.NONE } }

    // One tick per segment that passes the middle.
    if (interactive) LaunchedEffect(state, n) {
        snapshotFlow { PuckSectionsGeometry.frontSlot(n, state.p) }.distinctUntilChanged().drop(1)
            .collect { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }

    fun label(i: Int): String {
        val a = currentActions.getOrNull(i)
        return if (a == null || a.empty) L10n.tr("Drawer shortcut %d, empty", i + 1) else a.label
    }
    fun tap(i: Int) {
        val a = currentActions.getOrNull(i) ?: return
        if (a.empty) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        a.onClick()
    }
    fun longPress(i: Int) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val a = currentActions.getOrNull(i)
        if (a == null || a.empty) a?.onLongClick?.invoke() else { state.tip = i; tipToken++ }
    }

    val ringActions = listOf(
        CustomAccessibilityAction(L10n.tr("Rotate ring next")) { state.turnBy(scope, 1, n, reducedNow); true },
        CustomAccessibilityAction(L10n.tr("Rotate ring previous")) { state.turnBy(scope, -1, n, reducedNow); true },
    )

    // Ring: n segments of one annulus, dividers over the shared edges, the focused segment outlined. The window clips it.
    Box(Modifier.fillMaxSize().drawBehind {
        val mid = ring.mid * d; val thick = ring.thickness * d
        val tl = Offset(corner.x - mid, corner.y - mid); val sz = androidx.compose.ui.geometry.Size(mid * 2f, mid * 2f)
        val a = pressAnim.value
        for (i in 0 until n) {
            val c = if (i == lastPressed && a > 0f) lerp(look.ring, look.pressed, a) else look.ring
            drawArc(c, PuckSectionsGeometry.startAngle(anchor, n, state.p, i), seg, false, tl, sz, style = Stroke(width = thick))
        }
        for (deg in PuckSectionsGeometry.dividerAngles(anchor, n, state.p)) {
            val r = Math.toRadians(deg.toDouble())
            val cs = kotlin.math.cos(r).toFloat(); val sn = kotlin.math.sin(r).toFloat()
            drawLine(look.divider, Offset(corner.x + cs * ring.inner * d, corner.y + sn * ring.inner * d), Offset(corner.x + cs * ring.outer * d, corner.y + sn * ring.outer * d), strokeWidth = hairline)
        }
        val f = state.focusedItem
        if (f >= 0 && keyboard) {
            val s0 = PuckSectionsGeometry.startAngle(anchor, n, state.p, f)
            val inset = 2f * d
            val path = Path().apply {
                val o = ring.outer * d - inset; val i2 = ring.inner * d + inset
                arcTo(Rect(corner.x - o, corner.y - o, corner.x + o, corner.y + o), s0, seg, true)
                arcTo(Rect(corner.x - i2, corner.y - i2, corner.x + i2, corner.y + i2), s0 + seg, -seg, false)
                close()
            }
            drawPath(path, accent, style = Stroke(width = 2f * d))
        }
    })

    // The segments that have any part inside the window are the nodes (they join and leave as the ring turns).
    val visible by remember(anchor, n, ring, window) {
        derivedStateOf { PuckSectionsGeometry.visibleSegments(anchor, n, state.p, ring, window).map { it.index } }
    }
    val arcMid = PuckSectionsGeometry.arcAt(n, ring.mid)
    val minDim = minOf(arcMid, ring.thickness)
    val glyphDp = (PuckSectionsGeometry.APP_ICON_SHARE * 0.7f * minDim).coerceIn(PuckSectionsGeometry.MIN_GLYPH_DP, 32f)
    val appDp = (PuckSectionsGeometry.APP_ICON_SHARE * minDim).coerceIn(PuckSectionsGeometry.MIN_GLYPH_DP, 40f)
    for (i in visible) {
        val a = actions.getOrNull(i) ?: PuckAction(L10n.tr("Drawer shortcut %d, empty", i + 1)) {}
        val empty = a.empty
        val name = label(i)
        Box(
            Modifier
                .layout { m, _ ->
                    val v = PuckSectionsGeometry.visibleSegment(anchor, n, state.p, i, ring, window)
                    val bw = if (v == null) 1 else (v.bounds.width * d).roundToInt().coerceAtLeast(1)
                    val bh = if (v == null) 1 else (v.bounds.height * d).roundToInt().coerceAtLeast(1)
                    val pl = m.measure(Constraints.fixed(bw, bh))
                    layout(bw, bh) { pl.place(if (v == null) 0 else (v.bounds.left * d).roundToInt(), if (v == null) 0 else (v.bounds.top * d).roundToInt()) }
                }
                .semantics {
                    contentDescription = name
                    role = Role.Button
                    traversalIndex = (i + 1).toFloat()
                    customActions = ringActions
                    if (!a.available && !empty) stateDescription = L10n.tr("Not available on this device")
                    onClick(label = null) { tap(i); true }
                    onLongClick(label = if (empty) L10n.tr("Edit drawer shortcuts") else L10n.tr("Show name")) { longPress(i); true }
                }
                .launcherItem(
                    "puck.section.$i", FocusKind.PuckRing, order = i + 1, layer = 3, focusable = true, ringRadiusDp = 14f,
                    drawRing = false, onActivate = { tap(i) },
                    onFocusState = { f -> if (f) state.focusedItem = i else if (state.focusedItem == i) state.focusedItem = PuckRingState.NONE },
                ),
        ) {
            val alpha = when { empty -> PuckLook.EMPTY_ALPHA; !a.available -> PuckLook.UNAVAILABLE_ALPHA; else -> 1f }
            val boxDp = if (a.app != null) appDp else glyphDp
            val glyphPx = boxDp * d
            val labelShift = if (showLabels) 7f * d else 0f
            val v0 = { PuckSectionsGeometry.visibleSegment(anchor, n, state.p, i, ring, window) }
            // Glyph and label sit on the part of the segment that shows, whole inside the window; one with no room for them shows only its (tappable) area.
            val placed = { PuckSectionsGeometry.glyphPlacement(anchor, n, state.p, i, ring, window, boxDp + if (showLabels) 14f else 0f) }
            Box(
                Modifier
                    .offset {
                        val v = v0() ?: return@offset IntOffset.Zero
                        val g = placed() ?: return@offset IntOffset.Zero
                        IntOffset((window.cx * d + g.first * d - v.bounds.left * d - glyphPx / 2f).roundToInt(), (window.cy * d + g.second * d - v.bounds.top * d - glyphPx / 2f - labelShift).roundToInt())
                    }
                    .size(boxDp.dp)
                    .graphicsLayer { this.alpha = if (placed() != null) 1f else 0f },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    empty -> Icon(Glyphs.Plus, null, tint = look.glyph.copy(alpha = alpha), modifier = Modifier.size((glyphDp * 0.7f).dp))
                    // Apps stay app icons: no tile, no tint, no extra clip (the icon theme's own shape, e.g. a circle, still applies).
                    a.app != null -> AppIcon(a.app, boxDp.dp, Modifier.graphicsLayer { this.alpha = alpha })
                    a.glyph != null -> Icon(a.glyph, null, tint = look.glyph.copy(alpha = alpha), modifier = Modifier.size(boxDp.dp))
                }
            }
            if (showLabels && !empty) {
                Box(
                    Modifier
                        .offset {
                            val v = v0() ?: return@offset IntOffset.Zero
                            val g = placed() ?: return@offset IntOffset.Zero
                            IntOffset((window.cx * d + g.first * d - v.bounds.left * d - 28f * d).roundToInt(), (window.cy * d + g.second * d - v.bounds.top * d + glyphPx / 2f - labelShift + 1f * d).roundToInt())
                        }
                        .width(56.dp)
                        .graphicsLayer { this.alpha = if (placed() != null) alpha else 0f },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(a.label, color = look.glyph, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }

    // The long-press name, over the segment it belongs to.
    val tipped = state.tip
    if (tipped >= 0) {
        val v = PuckSectionsGeometry.visibleSegment(anchor, n, state.p, tipped, ring, window)
        if (v != null) {
            val (gx, gy) = PuckSectionsGeometry.glyphOffset(v.midAngleDeg, ring)
            Text(
                label(tipped), color = HyleColors.InkBright, fontSize = 12.sp,
                modifier = Modifier
                    .offset { IntOffset((corner.x + gx * d).roundToInt(), (corner.y + gy * d).roundToInt()) }
                    .offset(x = (-40).dp, y = (-34).dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }

    // The Back button: the round hub at the corner, hard against the ring (no gap), opposite tone to the ring. It reaches the nearest window edge
    // (wave 38); its arrow sits at the middle of the part of it that is inside the window, upright.
    val backDp = ring.backRadius * 2f
    var backFocused by remember { mutableStateOf(false) }
    val arrow = PuckSectionsGeometry.hubArrowOffset(ring, window)
    Box(
        Modifier
            .offset { IntOffset((corner.x - ring.backRadius * d).roundToInt(), (corner.y - ring.backRadius * d).roundToInt()) }
            .size(backDp.dp)
            .semantics {
                contentDescription = L10n.tr("Back"); role = Role.Button; traversalIndex = 0f
                customActions = ringActions
                onClick(label = null) { currentOnBack(); true }
            }
            .clip(CircleShape)
            .drawBehind {
                drawCircle(if (state.pressed == PuckRingState.HUB) look.hubPressed else look.hub)
                if (backFocused && keyboard) drawCircle(accent, radius = size.minDimension / 2f - 1f * d, style = Stroke(width = 2f * d))
            }
            .launcherItem(
                "puck.back", FocusKind.PuckHub, order = 0, layer = 3, focusable = true, ringRadiusDp = ring.backRadius,
                drawRing = false, onActivate = { currentOnBack() },
                onFocusState = { f -> backFocused = f; if (f) state.focusedItem = PuckRingState.HUB else if (state.focusedItem == PuckRingState.HUB) state.focusedItem = PuckRingState.NONE },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(BackGlyph, null, tint = look.hubGlyph, modifier = Modifier.offset(arrow.first.dp, arrow.second.dp).size(40.dp))
    }

    // The touch overlay: exactly the disc of the ring (hit testing follows the circular clip), above everything else of the disc.
    val outerPx = ring.outer * d
    Box(
        Modifier
            .offset { IntOffset((corner.x - outerPx).roundToInt(), (corner.y - outerPx).roundToInt()) }
            .size((ring.outer * 2f).dp)
            .clip(CircleShape)
            .pointerInput(anchor, n, ring, window, interactive) {
                if (!interactive) return@pointerInput
                val centre = Offset(outerPx, outerPx)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val rel0 = (down.position - centre) / d
                    val startHit = PuckSectionsGeometry.hit(anchor, n, state.p, ring, rel0.x, rel0.y, window)
                    if (startHit is PuckSectionsGeometry.Hit.None) return@awaitEachGesture
                    down.consume()
                    state.stop()
                    state.pressed = when (startHit) { is PuckSectionsGeometry.Hit.Slot -> startHit.index; else -> PuckRingState.HUB }
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var lastAngle = angleOf(down.position - centre)
                    var dragging = false
                    var longFired = false
                    var upChange: PointerInputChange? = null
                    var lastTime = down.uptimeMillis
                    val slop = viewConfiguration.touchSlop
                    val longMs = viewConfiguration.longPressTimeoutMillis
                    val minRadius = 12f * d
                    while (true) {
                        val canLong = !dragging && !longFired && startHit is PuckSectionsGeometry.Hit.Slot
                        val ev = if (canLong) withTimeoutOrNull((longMs - (lastTime - down.uptimeMillis)).coerceAtLeast(1L)) { awaitPointerEvent() } else awaitPointerEvent()
                        if (ev == null) {
                            longFired = true
                            state.pressed = PuckRingState.NONE
                            longPress((startHit as PuckSectionsGeometry.Hit.Slot).index)
                            continue
                        }
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        lastTime = ch.uptimeMillis
                        if (!ch.pressed) { upChange = ch; break }
                        tracker.addPosition(ch.uptimeMillis, ch.position)
                        if (!dragging && !longFired && (ch.position - down.position).getDistance() > slop) {
                            // The ring follows the whole movement from the touch-down, so it does not lag the finger by the slop.
                            dragging = true
                            state.pressed = PuckRingState.NONE
                        }
                        if (dragging) {
                            val rel = ch.position - centre
                            if (rel.getDistance() > minRadius) {
                                val a = angleOf(rel)
                                var dA = a - lastAngle
                                dA = ((dA + 540f) % 360f) - 180f
                                state.dragBy(PuckSectionsGeometry.positionDelta(anchor, n, dA))
                                lastAngle = a
                            }
                            ch.consume()
                        }
                    }
                    state.pressed = PuckRingState.NONE
                    val up = upChange
                    if (dragging) {
                        val v = tracker.calculateVelocity()
                        val rel = (up?.position ?: down.position) - centre
                        val omega = WheelGeometry.angularVelocityDeg(rel.x, rel.y, v.x, v.y).coerceIn(-1500f, 1500f)
                        val target = PuckSectionsGeometry.settleTarget(
                            state.p, PuckSectionsGeometry.positionVelocity(anchor, n, omega), if (reducedNow) 0f else 0.18f,
                        )
                        state.settleTo(scope, target, n, reducedNow)
                    } else if (up != null && !longFired) {
                        up.consume()
                        val rel = (up.position - centre) / d
                        when (val h = PuckSectionsGeometry.hit(anchor, n, state.p, ring, rel.x, rel.y, window)) {
                            is PuckSectionsGeometry.Hit.Back -> { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); currentOnBack() }
                            is PuckSectionsGeometry.Hit.Slot -> tap(h.index)
                            else -> Unit
                        }
                    }
                }
            },
    )
}

private fun DIVIDER_PX(d: Float) = PuckSectionsGeometry.DIVIDER_DP * d
