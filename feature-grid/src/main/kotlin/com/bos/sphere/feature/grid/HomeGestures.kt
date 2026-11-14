package com.bos.sphere.feature.grid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.HomeGesture
import com.bos.sphere.core.data.HomeSurface
import com.bos.sphere.core.data.SurfaceScroll
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * What the shared gesture layer ([homeGestures]) fires. [bound] are the gestures the user gave
 * an action to; the others are left alone so the content can still use them.
 */
class HomeGestureSpec(
    val surface: HomeSurface,
    val bound: Set<HomeGesture>,
    val onGesture: (HomeGesture) -> Unit,
    /** Long press on empty space (the paged home opens page settings; scrolling homes open Home settings). */
    val onEmptyLongPress: (() -> Unit)? = null,
)

/** Pure decisions behind [homeGestures] (unit tested). Distances in px. */
internal object HomeGestureMath {
    /** A swipe needs to travel this far along the free axis. */
    val SwipeMin = 56.dp
    /** Pinch span ratios that count as in / out. */
    const val PinchInRatio = 0.7f
    const val PinchOutRatio = 1.4f

    /**
     * The axis a one-finger swipe may use on [scroll]: the one the home does not scroll along.
     * null = none (the home takes both). True = vertical swipes are free, false = horizontal.
     */
    fun freeAxisVertical(scroll: SurfaceScroll): Boolean? = when (scroll) {
        SurfaceScroll.Horizontal -> true
        SurfaceScroll.Vertical -> false
        SurfaceScroll.Both -> null
    }

    /**
     * Once the finger has left touch slop: is this movement clearly across the free axis (and so
     * ours), or along the scroll axis (the content's)? Diagonals go to the content — it is far
     * worse to steal a scroll than to miss a swipe.
     */
    fun isCrossSwipe(dx: Float, dy: Float, scroll: SurfaceScroll): Boolean {
        val vertical = freeAxisVertical(scroll) ?: return false
        val across = if (vertical) abs(dy) else abs(dx)
        val along = if (vertical) abs(dx) else abs(dy)
        return across > along * 1.5f
    }

    /** The swipe gesture a total displacement amounts to on [scroll], or null if too short. */
    fun swipeFor(dx: Float, dy: Float, scroll: SurfaceScroll, minPx: Float): HomeGesture? {
        val vertical = freeAxisVertical(scroll) ?: return null
        return if (vertical) {
            if (abs(dy) < minPx) null else if (dy < 0) HomeGesture.SwipeUp else HomeGesture.SwipeDown
        } else {
            if (abs(dx) < minPx) null else if (dx < 0) HomeGesture.SwipeLeft else HomeGesture.SwipeRight
        }
    }

    /** The pinch gesture a span change amounts to, or null when the fingers barely moved. */
    fun pinchFor(startSpan: Float, endSpan: Float): HomeGesture? {
        if (startSpan <= 0f || endSpan <= 0f) return null
        val ratio = endSpan / startSpan
        return when {
            ratio < PinchInRatio -> HomeGesture.PinchIn
            ratio > PinchOutRatio -> HomeGesture.PinchOut
            else -> null
        }
    }
}

private enum class Phase { Up, Cancel, Swipe, Pinch }

/**
 * The shared touch-gesture layer for the homes that scroll instead of paging — Continuous Pages,
 * DSi, List and PSP. Put it on the home's root box, *under* the scrolling content.
 *
 * The rule: this layer never takes a one-finger drag along an axis the home scrolls on, so the
 * list / grid keeps every scroll and fling. It only acts on what cannot be a scroll:
 * - **two fingers** → pinch in / out. Claimed from the first two-finger event (consumed on the
 *   Initial pass) so the content does not jump under the pinch;
 * - **a one-finger swipe across the free axis** — the axis the home does not scroll along — that is
 *   clearly across (1.5× more across than along) and at least [HomeGestureMath.SwipeMin] long. On
 *   PSP both axes are taken, so no swipes;
 * - **a double tap on empty space** → [HomeGesture.DoubleTap];
 * - **a long press on empty space** → [HomeGestureSpec.onEmptyLongPress].
 * Taps, long presses on items and drags along the scroll axis pass through untouched: nothing is
 * consumed until a gesture is recognised. Events are watched on [PointerEventPass.Initial]; a
 * change already consumed there by an ancestor (the Launchpad edge swipe) ends our interest.
 *
 * "Empty space" is [isEmptyAt] when the home can tell (hit-test its visible items); when it is
 * null, a tap counts as empty if no child consumed its release, and long press is off.
 */
fun Modifier.homeGestures(spec: HomeGestureSpec?, isEmptyAt: ((Offset) -> Boolean)? = null): Modifier =
    if (spec == null) this else composed {
        val latest by rememberUpdatedState(spec)
        val latestEmpty by rememberUpdatedState(isEmptyAt)
        pointerInput(Unit) {
            val slop = viewConfiguration.touchSlop
            val swipeMin = HomeGestureMath.SwipeMin.toPx()
            val doubleTapRadius = 48.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val s = latest
                val scroll = s.surface.scroll
                val emptyFn = latestEmpty
                val startEmpty = emptyFn?.invoke(down.position)
                var last: PointerInputChange = down

                val early: Phase? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        if (ev.changes.count { it.pressed } >= 2) return@withTimeoutOrNull Phase.Pinch
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull Phase.Cancel
                        if (c.isConsumed) return@withTimeoutOrNull Phase.Cancel
                        last = c
                        if (!c.pressed) return@withTimeoutOrNull Phase.Up
                        val d = c.position - down.position
                        if (d.getDistance() > slop) {
                            return@withTimeoutOrNull if (HomeGestureMath.isCrossSwipe(d.x, d.y, scroll)) Phase.Swipe else Phase.Cancel
                        }
                    }
                    @Suppress("UNREACHABLE_CODE") Phase.Cancel
                }

                when (early) {
                    Phase.Cancel -> Unit // the content (or an ancestor) owns it

                    Phase.Up -> {
                        if (HomeGesture.DoubleTap !in s.bound) return@awaitEachGesture
                        // Empty? Ask the home; otherwise see whether a child claimed the release.
                        if (startEmpty == false) return@awaitEachGesture
                        if (startEmpty == null) {
                            val fin = awaitPointerEvent(PointerEventPass.Final)
                            if (fin.changes.firstOrNull { it.id == down.id }?.isConsumed == true) return@awaitEachGesture
                        }
                        val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        } ?: return@awaitEachGesture
                        if ((second.position - down.position).getDistance() > doubleTapRadius) return@awaitEachGesture
                        if (emptyFn != null && !emptyFn(second.position)) return@awaitEachGesture
                        // A clean release, close to the first tap: a double tap. Claim it so the
                        // content does not also treat the second tap as a click.
                        val tapped = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                val c = ev.changes.firstOrNull { it.id == second.id } ?: return@withTimeoutOrNull false
                                if (c.isConsumed) return@withTimeoutOrNull false
                                if ((c.position - second.position).getDistance() > slop) return@withTimeoutOrNull false
                                if (!c.pressed) { c.consume(); return@withTimeoutOrNull true }
                            }
                            @Suppress("UNREACHABLE_CODE") false
                        } ?: false
                        if (tapped) s.onGesture(HomeGesture.DoubleTap)
                    }

                    Phase.Swipe -> {
                        var total = last.position - down.position
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            if (ev.changes.count { it.pressed } >= 2) return@awaitEachGesture
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (c.isConsumed) return@awaitEachGesture
                            total = c.position - down.position
                            // Ours now: keep it from the content for the rest of the stroke.
                            c.consume()
                            if (!c.pressed) break
                        }
                        val g = HomeGestureMath.swipeFor(total.x, total.y, scroll, swipeMin)
                        if (g != null && g in s.bound && !swipeStartsInSystemZone(down.position, size, g)) s.onGesture(g)
                    }

                    Phase.Pinch -> {
                        if (HomeGesture.PinchIn !in s.bound && HomeGesture.PinchOut !in s.bound) return@awaitEachGesture
                        var startSpan = -1f
                        var span = -1f
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = ev.changes.filter { it.pressed }
                            ev.changes.forEach { it.consume() }
                            if (pressed.size >= 2) {
                                span = (pressed[0].position - pressed[1].position).getDistance()
                                if (startSpan < 0f) startSpan = span
                            }
                            if (pressed.isEmpty()) break
                        }
                        val g = HomeGestureMath.pinchFor(startSpan, span)
                        if (g != null && g in s.bound) s.onGesture(g)
                    }

                    null -> {
                        // Long press: only on empty space the home can vouch for.
                        val onLong = s.onEmptyLongPress ?: return@awaitEachGesture
                        if (startEmpty != true) return@awaitEachGesture
                        onLong()
                        do {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            ev.changes.forEach { it.consume() }
                        } while (ev.changes.any { it.pressed })
                    }
                }
            }
        }
    }
