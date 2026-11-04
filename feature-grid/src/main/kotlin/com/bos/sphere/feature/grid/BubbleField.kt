package com.bos.sphere.feature.grid

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.HyleColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Apple-Watch-style bubble field: a hexagonal honeycomb of round app icons that you pan with a
 * finger. Bubbles near the viewport centre are full size, those toward the edges shrink and fade
 * (fisheye); on release the field settles so the nearest bubble sits at the centre.
 *
 * Pan state lives in one [androidx.compose.runtime.MutableState] that is only read inside
 * graphicsLayer / snapshotFlow, never in composition, so a drag does not recompose bubbles.
 * Only bubbles within the visible window are composed (the window is re-derived when the pan
 * crosses a coarse cell).
 */
@Composable
fun BubbleField(
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    bubbleSize: Dp = 68.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    showLabels: Boolean = true,
    sounds: UiSounds? = null,
) {
    // Physical coordinates: the lattice and gestures must not mirror under RTL.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BubbleFieldImpl(apps, onLaunch, onLongPress, modifier, bubbleSize, contentPadding, showLabels, sounds)
    }
}

/** Everything the gesture / layer lambdas need, rebuilt on change and read through a State. */
private class BubbleGeo(
    val xy: FloatArray,
    val bounds: FloatArray,
    val cx: Float,
    val cy: Float,
    val radius: Float,
    val plateau: Float,
    val bubblePx: Float,
    val pitch: Float,
)

@Composable
private fun BubbleFieldImpl(
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier,
    bubbleSize: Dp,
    contentPadding: PaddingValues,
    showLabels: Boolean,
    sounds: UiSounds?,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(IntSize.Zero) }

    val bubblePx = with(density) { bubbleSize.toPx() }
    val pitch = bubblePx * BubbleMath.PITCH_FACTOR
    val xy = remember(apps.size, pitch) { BubbleMath.positions(apps.size, pitch) }
    val bounds = remember(xy) { BubbleMath.bounds(xy) }

    val padL = with(density) { contentPadding.calculateLeftPadding(LayoutDirection.Ltr).toPx() }
    val padR = with(density) { contentPadding.calculateRightPadding(LayoutDirection.Ltr).toPx() }
    val padT = with(density) { contentPadding.calculateTopPadding().toPx() }
    val padB = with(density) { contentPadding.calculateBottomPadding().toPx() }
    val innerW = max(1f, size.width - padL - padR)
    val innerH = max(1f, size.height - padT - padB)
    val geo = remember(xy, bounds, size, padL, padR, padT, padB, bubblePx) {
        val plateau = pitch * 0.75f
        BubbleGeo(
            xy = xy, bounds = bounds,
            cx = padL + innerW / 2f, cy = padT + innerH / 2f,
            radius = max(BubbleMath.fisheyeRadius(innerW, innerH), plateau + pitch),
            plateau = plateau, bubblePx = bubblePx, pitch = pitch,
        )
    }
    val geoRef = rememberUpdatedState(geo)
    val appsRef = rememberUpdatedState(apps)
    val onLaunchRef = rememberUpdatedState(onLaunch)
    val onLongPressRef = rememberUpdatedState(onLongPress)
    val soundsRef = rememberUpdatedState(sounds)

    // World point currently under the viewport centre. Never read in composition.
    val pan = remember { mutableStateOf(Offset.Zero) }
    val focus = remember { mutableIntStateOf(if (apps.isEmpty()) -1 else 0) }
    val popIdx = remember { mutableIntStateOf(-1) }
    val pop = remember { Animatable(1f) }
    val jobHolder = remember { arrayOfNulls<Job>(1) }

    // Keep the pan inside the (possibly shrunk) world after the app list changes.
    LaunchedEffect(bounds) {
        val p = pan.value
        pan.value = Offset(BubbleMath.clamp(p.x, bounds[0], bounds[2]), BubbleMath.clamp(p.y, bounds[1], bounds[3]))
    }

    // Coarse window origin: composition only reacts when the pan crosses a cell.
    var winPan by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(pitch) {
        val cell = pitch * 0.5f
        snapshotFlow {
            val p = pan.value
            Offset((p.x / cell).roundToInt() * cell, (p.y / cell).roundToInt() * cell)
        }.distinctUntilChanged().collect { winPan = it }
    }

    // Centre-most bubble: label + tick on change.
    LaunchedEffect(xy) {
        var first = true
        snapshotFlow { BubbleMath.nearest(xy, pan.value.x, pan.value.y) }
            .distinctUntilChanged()
            .collect {
                focus.intValue = it
                if (first) first = false else {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    soundsRef.value?.play(UiSound.Tick)
                }
            }
    }

    val visible = remember(winPan, geo) {
        if (size == IntSize.Zero) IntArray(0)
        else BubbleMath.visible(
            xy, winPan.x, winPan.y,
            BubbleMath.cullDistance(geo.radius, geo.plateau) + bubblePx / 2f + pitch,
        )
    }

    suspend fun flingAndSettle(v: Offset) {
        val g = geoRef.value
        val b = g.bounds
        if (v.getDistance() > 150f) {
            AnimationState(Offset.VectorConverter, pan.value, v)
                .animateDecay(exponentialDecay<Offset>(frictionMultiplier = 1.5f)) {
                    val c = Offset(
                        BubbleMath.clamp(value.x, b[0], b[2]),
                        BubbleMath.clamp(value.y, b[1], b[3]),
                    )
                    pan.value = c
                    if (c != value) cancelAnimation()
                }
        }
        val from = pan.value
        val n = BubbleMath.nearest(g.xy, from.x, from.y)
        if (n >= 0) {
            val target = Offset(g.xy[2 * n], g.xy[2 * n + 1])
            animate(
                Offset.VectorConverter, from, target,
                animationSpec = spring(
                    dampingRatio = 0.8f, stiffness = Spring.StiffnessLow,
                    visibilityThreshold = Offset.VisibilityThreshold,
                ),
            ) { value, _ -> pan.value = value }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    jobHolder[0]?.cancel()
                    jobHolder[0] = null
                    val slop = viewConfiguration.touchSlop
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)

                    // Phase 1: wait for slop (pan), release (tap) or timeout (long press).
                    var slopHit = false
                    var releasedEarly = false
                    val timeoutHit = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(ch.uptimeMillis, ch.position)
                            if (ch.changedToUpIgnoreConsumed()) { releasedEarly = true; break }
                            if ((ch.position - down.position).getDistance() > slop) { slopHit = true; break }
                        }
                        true
                    } == null

                    if (releasedEarly) {
                        val g = geoRef.value
                        val hit = BubbleMath.hitTest(
                            g.xy, pan.value.x, pan.value.y, g.cx, g.cy, down.position.x, down.position.y,
                            g.bubblePx, g.radius, g.plateau,
                        )
                        val app = appsRef.value.getOrNull(hit)
                        if (app != null) {
                            soundsRef.value?.play(UiSound.Select)
                            scope.launch {
                                popIdx.intValue = hit
                                pop.snapTo(1f)
                                pop.animateTo(1.16f, tween(70))
                                onLaunchRef.value(app)
                                pop.animateTo(1f, spring(dampingRatio = 0.6f))
                                popIdx.intValue = -1
                            }
                        }
                        jobHolder[0] = scope.launch { flingAndSettle(Offset.Zero) }
                        return@awaitEachGesture
                    }

                    if (timeoutHit) {
                        val g = geoRef.value
                        val hit = BubbleMath.hitTest(
                            g.xy, pan.value.x, pan.value.y, g.cx, g.cy, down.position.x, down.position.y,
                            g.bubblePx, g.radius, g.plateau,
                        )
                        val app = appsRef.value.getOrNull(hit)
                        if (app != null) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPressRef.value(app)
                            while (true) {
                                val ev = awaitPointerEvent()
                                ev.changes.forEach { it.consume() }
                                if (ev.changes.none { it.pressed }) break
                            }
                            jobHolder[0] = scope.launch { flingAndSettle(Offset.Zero) }
                            return@awaitEachGesture
                        }
                    }

                    // Phase 2: pan.
                    var dragging = slopHit
                    var lastPos = down.position
                    var velocity = Offset.Zero
                    fun move(ch: PointerInputChange) {
                        tracker.addPosition(ch.uptimeMillis, ch.position)
                        if (!dragging && (ch.position - down.position).getDistance() > slop) dragging = true
                        if (dragging) {
                            val g = geoRef.value
                            val b = g.bounds
                            val maxOver = g.radius * 0.6f
                            val d = lastPos - ch.position // pan moves opposite to the finger
                            val p = pan.value
                            val fx = BubbleMath.dragResistance(p.x, d.x, b[0], b[2], maxOver)
                            val fy = BubbleMath.dragResistance(p.y, d.y, b[1], b[3], maxOver)
                            pan.value = Offset(p.x + d.x * fx, p.y + d.y * fy)
                            lastPos = ch.position
                            ch.consume()
                        }
                    }
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id }
                        if (ch == null) break
                        move(ch)
                        if (ch.changedToUpIgnoreConsumed() || !ch.pressed) {
                            val v = tracker.calculateVelocity()
                            velocity = Offset(-v.x, -v.y)
                            break
                        }
                    }
                    val v = velocity
                    jobHolder[0] = scope.launch { flingAndSettle(v) }
                }
            },
    ) {
        if (size != IntSize.Zero) {
            for (i in visible) {
                val app = apps.getOrNull(i) ?: continue
                key(app.key) {
                    Bubble(
                        app = app,
                        index = i,
                        bubbleSize = bubbleSize,
                        geoRef = geoRef,
                        pan = pan,
                        popIdx = popIdx,
                        pop = pop,
                        onLaunch = { onLaunchRef.value(it) },
                        onOptions = { onLongPressRef.value(it) },
                    )
                }
            }
            if (showLabels) {
                FocusLabel(
                    apps = apps, focus = focus,
                    startPx = padL, endPx = padR,
                    topPx = geo.cy + bubblePx / 2f + with(density) { 8.dp.toPx() },
                )
            }
        }
    }
}

@Composable
private fun Bubble(
    app: AppEntry,
    index: Int,
    bubbleSize: Dp,
    geoRef: State<BubbleGeo>,
    pan: State<Offset>,
    popIdx: State<Int>,
    pop: Animatable<Float, *>,
    onLaunch: (AppEntry) -> Unit,
    onOptions: (AppEntry) -> Unit,
) {
    Box(
        Modifier
            .size(bubbleSize)
            .graphicsLayer {
                val g = geoRef.value
                val p = pan.value
                val wx = g.xy.getOrElse(2 * index) { 0f }
                val wy = g.xy.getOrElse(2 * index + 1) { 0f }
                val dx = wx - p.x
                val dy = wy - p.y
                val d = hypot(dx, dy)
                var s = BubbleMath.scale(d, g.radius, g.plateau)
                if (popIdx.value == index) s *= pop.value
                translationX = g.cx - g.bubblePx / 2f + dx
                translationY = g.cy - g.bubblePx / 2f + dy
                scaleX = s
                scaleY = s
                alpha = if (s < BubbleMath.CULL_SCALE) 0f else BubbleMath.alpha(d, g.radius, g.plateau)
            }
            .semantics(mergeDescendants = true) {
                contentDescription = app.label
                onClick(label = app.label) { onLaunch(app); true }
                customActions = listOf(CustomAccessibilityAction("Options") { onOptions(app); true })
            }
            .clip(CircleShape),
    ) {
        AppIcon(app, bubbleSize)
    }
}

@Composable
private fun FocusLabel(
    apps: List<AppEntry>,
    focus: State<Int>,
    startPx: Float,
    endPx: Float,
    topPx: Float,
) {
    val label = apps.getOrNull(focus.value)?.label.orEmpty()
    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = with(density) { startPx.toDp() }, end = with(density) { endPx.toDp() })
            .offset { IntOffset(0, topPx.roundToInt()) },
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedContent(
            targetState = label,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
            label = "bubbleLabel",
        ) { text ->
            Text(
                text = text,
                color = HyleColors.InkBright,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.55f), blurRadius = 6f)),
            )
        }
    }
}

/** Pure bubble-field geometry (no Android types) so it can be unit tested on the JVM. */
internal object BubbleMath {
    const val PITCH_FACTOR = 1.12f
    const val MIN_SCALE = 0.30f
    const val MIN_ALPHA = 0.35f
    const val CULL_SCALE = 0.12f
    private const val TAIL = 0.9f // beyond the radius, scale falls to zero over TAIL * radius
    private val SQRT3 = sqrt(3.0).toFloat()

    data class Hex(val q: Int, val r: Int)

    /** Axial directions, redblobgames order. */
    private val DIRS = arrayOf(
        Hex(1, 0), Hex(1, -1), Hex(0, -1), Hex(-1, 0), Hex(-1, 1), Hex(0, 1),
    )

    /** Index of the first cell of ring [k] (ring 0 is the single centre cell). */
    fun ringStart(k: Int): Int = if (k <= 0) 0 else 3 * k * (k - 1) + 1

    fun ringOf(i: Int): Int {
        if (i <= 0) return 0
        var k = 1
        while (ringStart(k + 1) <= i) k++
        return k
    }

    /** Hex-spiral axial coordinate of index [i]: centre, then ring 1 (6), ring 2 (12), ... */
    fun hexCell(i: Int): Hex {
        if (i <= 0) return Hex(0, 0)
        val k = ringOf(i)
        var off = i - ringStart(k)
        var q = -k
        var r = k
        for (side in 0 until 6) {
            if (off < k) {
                return Hex(q + DIRS[side].q * off, r + DIRS[side].r * off)
            }
            q += DIRS[side].q * k
            r += DIRS[side].r * k
            off -= k
        }
        return Hex(q, r)
    }

    fun hexDistance(a: Hex, b: Hex): Int {
        val dq = a.q - b.q
        val dr = a.r - b.r
        return maxOf(kotlin.math.abs(dq), kotlin.math.abs(dr), kotlin.math.abs(dq + dr))
    }

    fun pixelX(q: Int, r: Int, pitch: Float): Float = pitch * (q + r / 2f)
    fun pixelY(r: Int, pitch: Float): Float = pitch * (SQRT3 / 2f) * r

    /** Interleaved x,y world positions (relative to bubble 0) for [n] bubbles. */
    fun positions(n: Int, pitch: Float): FloatArray {
        val out = FloatArray(max(0, n) * 2)
        for (i in 0 until n) {
            val h = hexCell(i)
            out[2 * i] = pixelX(h.q, h.r, pitch)
            out[2 * i + 1] = pixelY(h.r, pitch)
        }
        return out
    }

    fun fisheyeRadius(w: Float, h: Float): Float = 0.48f * min(w, h)

    private fun smoothstep(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    /** Bubble scale at distance [d] from the viewport centre: 1 on the plateau, [MIN_SCALE] at [radius], then to 0. */
    fun scale(d: Float, radius: Float, plateau: Float): Float {
        if (d <= plateau) return 1f
        if (d <= radius) {
            val s = smoothstep((d - plateau) / max(1e-3f, radius - plateau))
            return 1f - (1f - MIN_SCALE) * s
        }
        return MIN_SCALE * (1f - (d - radius) / (TAIL * radius)).coerceIn(0f, 1f)
    }

    /** Opacity at distance [d]: 1 on the plateau, [MIN_ALPHA] at [radius], then fading out. */
    fun alpha(d: Float, radius: Float, plateau: Float): Float {
        if (d <= plateau) return 1f
        if (d <= radius) {
            val s = smoothstep((d - plateau) / max(1e-3f, radius - plateau))
            return 1f - (1f - MIN_ALPHA) * s
        }
        return MIN_ALPHA * (1f - (d - radius) / (TAIL * radius)).coerceIn(0f, 1f)
    }

    /** Distance beyond which [scale] drops below [CULL_SCALE]. */
    fun cullDistance(radius: Float, plateau: Float): Float =
        radius + TAIL * radius * (1f - CULL_SCALE / MIN_SCALE)

    fun clamp(v: Float, lo: Float, hi: Float): Float = if (v < lo) lo else if (v > hi) hi else v

    /** Index of the bubble closest to (px, py), or -1 when empty. */
    fun nearest(xy: FloatArray, px: Float, py: Float): Int {
        var best = -1
        var bd = Float.MAX_VALUE
        for (i in 0 until xy.size / 2) {
            val dx = xy[2 * i] - px
            val dy = xy[2 * i + 1] - py
            val d = dx * dx + dy * dy
            if (d < bd) { bd = d; best = i }
        }
        return best
    }

    /** [minX, minY, maxX, maxY] of all centres; zeros when empty. */
    fun bounds(xy: FloatArray): FloatArray {
        if (xy.isEmpty()) return FloatArray(4)
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE
        var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        for (i in 0 until xy.size / 2) {
            x0 = min(x0, xy[2 * i]); x1 = max(x1, xy[2 * i])
            y0 = min(y0, xy[2 * i + 1]); y1 = max(y1, xy[2 * i + 1])
        }
        return floatArrayOf(x0, y0, x1, y1)
    }

    /** Indices of bubbles whose centre is within [radius] of the world point (px, py). */
    fun visible(xy: FloatArray, px: Float, py: Float, radius: Float): IntArray {
        val r2 = radius * radius
        var count = 0
        val tmp = IntArray(xy.size / 2)
        for (i in tmp.indices) {
            val dx = xy[2 * i] - px
            val dy = xy[2 * i + 1] - py
            if (dx * dx + dy * dy <= r2) tmp[count++] = i
        }
        return tmp.copyOf(count)
    }

    /**
     * Factor (0..1) applied to a pan delta [delta] at position [pos] on one axis: 1 inside
     * [lo]..[hi] or when moving back toward it, shrinking quadratically to ~0 at [maxOver] beyond.
     */
    fun dragResistance(pos: Float, delta: Float, lo: Float, hi: Float, maxOver: Float): Float {
        val over = when {
            pos < lo && delta < 0f -> lo - pos
            pos > hi && delta > 0f -> pos - hi
            else -> return 1f
        }
        val f = (1f - over / max(1f, maxOver)).coerceIn(0f, 1f)
        return max(0.02f, f * f)
    }

    /** Smooth rubber-band of an unconstrained [v] into [lo]..[hi] with at most [maxOver] overshoot. */
    fun rubberBand(v: Float, lo: Float, hi: Float, maxOver: Float): Float {
        if (v in lo..hi) return v
        val over = if (v < lo) lo - v else v - hi
        val soft = maxOver * (1f - exp(-over / max(1f, maxOver)))
        return if (v < lo) lo - soft else hi + soft
    }

    /**
     * Topmost bubble under the touch at screen (tx, ty), or -1. A bubble's screen centre is
     * (cx, cy) + world - pan; its hit radius follows its fisheye scale (with a small minimum).
     */
    fun hitTest(
        xy: FloatArray, panX: Float, panY: Float, cx: Float, cy: Float,
        tx: Float, ty: Float, bubblePx: Float, radius: Float, plateau: Float,
    ): Int {
        var best = -1
        var bestRatio = Float.MAX_VALUE
        for (i in 0 until xy.size / 2) {
            val dx = xy[2 * i] - panX
            val dy = xy[2 * i + 1] - panY
            val s = scale(hypot(dx, dy), radius, plateau)
            if (s < CULL_SCALE) continue
            val hitR = max(s, 0.4f) * bubblePx / 2f
            val dist = hypot(cx + dx - tx, cy + dy - ty)
            if (dist <= hitR) {
                val ratio = dist / hitR
                if (ratio < bestRatio) { bestRatio = ratio; best = i }
            }
        }
        return best
    }
}
