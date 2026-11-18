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
import kotlin.math.abs

/**
 * Wave 25 (hubData): the hub's two-finger gestures. One finger is never touched, so scrolling and swipes on rows work as before. From
 * the moment a second finger is down the pair belongs to the hub (their changes are consumed, so the list does not scroll and the
 * Launchpad's one-finger close drag does not fire):
 *  - fingers apart or together by more than ~18 percent: pinch ([onPinch], true = apart), once per gesture;
 *  - both fingers moving the same way by about 56 dp with the spread unchanged: two-finger swipe left, right or down, once per gesture.
 */
internal fun Modifier.hubGestures(onPinch: (zoomIn: Boolean) -> Unit, onSwipe: (HubSwipe) -> Unit): Modifier = this.pointerInput(Unit) {
    val swipeDistance = 56.dp.toPx()
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var startSpread = 0f
        var startCenter = Offset.Zero
        var fired = false
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val down = ev.changes.filter { it.pressed }.sortedBy { it.id.value }
            if (down.isEmpty()) break
            if (down.size < 2) continue
            val a = down[0].position; val b = down[1].position
            val spread = (a - b).getDistance()
            val center = (a + b) / 2f
            if (startSpread == 0f) { startSpread = spread; startCenter = center }
            else if (!fired && startSpread > 1f) {
                val ratio = spread / startSpread
                val d = center - startCenter
                if (abs(ratio - 1f) > 0.18f) { fired = true; onPinch(ratio > 1f) }
                else if (abs(ratio - 1f) < 0.15f && d.getDistance() > swipeDistance) {
                    if (d.y > abs(d.x)) { fired = true; onSwipe(HubSwipe.Down) }
                    else if (abs(d.x) > abs(d.y) * 1.3f) { fired = true; onSwipe(if (d.x < 0) HubSwipe.Left else HubSwipe.Right) }
                }
            }
            down.forEach { it.consume() }
        }
    }
}

/**
 * Hardware keyboard equivalents, in the panel's own key handler (the shortcut registry holds the global launcher commands; in-panel keys
 * live with the panel, as the list's own keys do): Ctrl+Right and Ctrl+Left next and previous view, Ctrl+Plus and Ctrl+Minus looser and
 * denser rows, Ctrl+Shift+M mark the visible items read.
 */
internal fun Modifier.hubKeys(
    onView: (delta: Int) -> Unit, onDensity: (looser: Boolean) -> Unit, onMarkRead: () -> Unit,
): Modifier = this.onPreviewKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown || !e.isCtrlPressed) return@onPreviewKeyEvent false
    when (e.key) {
        Key.DirectionRight -> { onView(+1); true }
        Key.DirectionLeft -> { onView(-1); true }
        Key.Equals, Key.Plus, Key.NumPadAdd -> { onDensity(true); true }
        Key.Minus, Key.NumPadSubtract -> { onDensity(false); true }
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
