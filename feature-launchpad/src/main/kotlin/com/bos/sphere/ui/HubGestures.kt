package com.bos.sphere.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.CommsScale
import kotlin.math.abs

/**
 * Wave 25 (hubData), wave 28 (commsLayout): the Comms list's two-finger gestures. One finger is never touched, so scrolling and swipes on rows work as
 * before. From the moment a second finger is down the pair belongs to the list (their changes are consumed, so the list does not scroll and the
 * screen's one-finger close drag does not fire):
 *  - fingers apart or together by more than ~8 percent: a pinch, which sets the Comms size live: the size the pinch started at times the spread ratio,
 *    snapped to a 5 percent detent ([CommsScale.fromPinch]); [onScale] is called each time the detent changes, and the pair stays a pinch until released;
 *  - both fingers moving the same way by about 56 dp with the spread unchanged: two-finger swipe left, right or down, once per gesture ([onSwipe], null = none).
 */
internal fun Modifier.hubGestures(
    currentScale: () -> Int, onScale: (Int) -> Unit, onSwipe: ((HubSwipe) -> Unit)? = null,
): Modifier = this.pointerInput(Unit) {
    val swipeDistance = 56.dp.toPx()
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var startSpread = 0f
        var startCenter = Offset.Zero
        var startPercent = CommsScale.DEFAULT
        var pinching = false
        var swiped = false
        var applied = -1
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val down = ev.changes.filter { it.pressed }.sortedBy { it.id.value }
            if (down.isEmpty()) break
            if (down.size < 2) continue
            val a = down[0].position; val b = down[1].position
            val spread = (a - b).getDistance()
            val center = (a + b) / 2f
            if (startSpread == 0f) { startSpread = spread; startCenter = center; startPercent = currentScale() }
            else if (startSpread > 1f) {
                val ratio = spread / startSpread
                val d = center - startCenter
                if (!pinching && !swiped && abs(ratio - 1f) > PINCH_START) pinching = true
                if (pinching) {
                    val target = CommsScale.fromPinch(startPercent, ratio)
                    if (target != applied) { applied = target; if (target != currentScale()) onScale(target) }
                } else if (!swiped && onSwipe != null && d.getDistance() > swipeDistance) {
                    if (d.y > abs(d.x)) { swiped = true; onSwipe(HubSwipe.Down) }
                    else if (abs(d.x) > abs(d.y) * 1.3f) { swiped = true; onSwipe(if (d.x < 0) HubSwipe.Left else HubSwipe.Right) }
                }
            }
            down.forEach { it.consume() }
        }
    }
}

/** How far the finger spread must change before a two-finger move counts as a pinch (and no longer as a swipe). */
internal const val PINCH_START = 0.08f

/**
 * Hardware keyboard equivalents, in the panel's own key handler (the shortcut registry holds the global launcher commands; in-panel keys
 * live with the panel, as the list's own keys do): Ctrl+Right and Ctrl+Left next and previous view, Ctrl+Plus and Ctrl+Minus
 * bigger and smaller Comms size (one 5 percent detent), Ctrl+Shift+M mark the visible items read.
 */
internal fun Modifier.hubKeys(
    onView: (delta: Int) -> Unit, onScale: (bigger: Boolean) -> Unit, onMarkRead: () -> Unit,
): Modifier = this.onPreviewKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown || !e.isCtrlPressed) return@onPreviewKeyEvent false
    when (e.key) {
        Key.DirectionRight -> { onView(+1); true }
        Key.DirectionLeft -> { onView(-1); true }
        Key.Equals, Key.Plus, Key.NumPadAdd -> { onScale(true); true }
        Key.Minus, Key.NumPadSubtract -> { onScale(false); true }
        Key.M -> if (e.isShiftPressed) { onMarkRead(); true } else false
        else -> false
    }
}

/**
 * A tap that only counts when the finger stayed put. A plain `clickable` also fires when a drag that nothing consumes (a horizontal swipe
 * across a row) is released inside the row; here a movement beyond the touch slop is not a tap. The tap is also an accessibility click action.
 */
internal fun Modifier.hubTap(onTap: () -> Unit): Modifier = this
    .semantics { onClick { onTap(); true } }
    .pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val up = waitForUpOrCancellation() ?: return@awaitEachGesture
            if ((up.position - down.position).getDistance() <= viewConfiguration.touchSlop) onTap()
        }
    }
