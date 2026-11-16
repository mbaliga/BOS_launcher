package com.bos.sphere.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/** STR-01: the pure maths of sliding home aside. No Android types, so it is tested on the JVM. */
object RevealMath {
    /** Width of the left-edge zone, in dp, where a drag starts the reveal. */
    const val EDGE_DP = 28f
    /** Released with the card dragged less than this fraction of the way: it springs back (a peek). */
    const val OPEN_AT = 0.35f
    /** Dragged closed from open: it stays open until it is pulled back past this fraction. */
    const val CLOSE_AT = 0.65f
    /** A fling faster than this (px per second) decides the outcome whatever the distance. */
    const val FLING = 1200f

    fun fraction(startFraction: Float, dragPx: Float, slidePx: Float): Float =
        if (slidePx <= 0f) startFraction else (startFraction + dragPx / slidePx).coerceIn(0f, 1f)

    /** True when the card should end up open. */
    fun settle(fraction: Float, velocityPxPerSec: Float, wasOpen: Boolean): Boolean = when {
        velocityPxPerSec > FLING -> true
        velocityPxPerSec < -FLING -> false
        wasOpen -> fraction > CLOSE_AT
        else -> fraction > OPEN_AT
    }

    fun startsInEdge(x: Float, edgePx: Float) = x in 0f..edgePx
}

/**
 * STR-01 (owner redesign): opening Launchpad shrinks the home into a rounded card pushed to one side, in both orientations.
 * [slideFraction] is the drag distance (as a fraction of the window width) that completes the reveal; [homeStripFraction] is
 * the share of the window width the card still covers at rest. The pose itself comes from [LaunchpadCardGeometry].
 */
data class RevealLayout(val slideFraction: Float, val homeStripFraction: Float, val landscape: Boolean) {
    companion object {
        fun forWindow(widthDp: Float, heightDp: Float): RevealLayout {
            val g = LaunchpadCardGeometry(widthDp, heightDp, CardEdge.Right, 1f)
            return RevealLayout(g.dragTravelFraction, g.cardCoverFraction, g.landscape)
        }
    }
}

/** Open/closed plus a live 0..1 progress that follows a finger, springs back after a peek and settles after a fling. */
@Stable
class LaunchpadRevealState internal constructor(private val scope: CoroutineScope) {
    private val anim = Animatable(0f)
    internal var onOpenChange: (Boolean) -> Unit = {}
    /** NFR-14: with animations off the card settles in a short linear tween instead of a bouncing spring. */
    internal var reducedMotion: Boolean = false
    var open by mutableStateOf(false)
        internal set
    var dragging by mutableStateOf(false)
        private set

    /** 0 = home in place, 1 = fully slid aside. */
    val progress: Float get() = anim.value

    internal fun beginDrag() { dragging = true }
    internal fun dragTo(fraction: Float) { scope.launch { anim.snapTo(fraction) } }

    internal fun endDrag(velocity: Float) {
        dragging = false
        val target = RevealMath.settle(anim.value, velocity, open)
        onOpenChange(target)
        scope.launch { animateTo(target) }
    }

    internal suspend fun animateTo(open: Boolean) {
        if (reducedMotion) { anim.animateTo(if (open) 1f else 0f, androidx.compose.animation.core.tween(90)); return }
        anim.animateTo(if (open) 1f else 0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
    }
}

@Composable
fun rememberLaunchpadReveal(open: Boolean, onOpenChange: (Boolean) -> Unit): LaunchpadRevealState {
    val scope = rememberCoroutineScope()
    val state = remember { LaunchpadRevealState(scope) }
    val latest by rememberUpdatedState(onOpenChange)
    state.onOpenChange = { latest(it) }
    state.open = open
    state.reducedMotion = com.bos.sphere.core.design.rememberMotionPrefs().reduced
    LaunchedEffect(open) { if (!state.dragging) state.animateTo(open) }
    return state
}

/**
 * Drag from the screen edge the notifications sit beside (the left edge; the right edge when [mirrored], i.e. right-to-left):
 * the card follows the finger; a partial drag peeks and springs back.
 */
fun Modifier.launchpadEdgeDrag(state: LaunchpadRevealState, enabled: Boolean, slideDp: Float, mirrored: Boolean = false): Modifier =
    if (!enabled) this else this.pointerInput(state, slideDp, mirrored) { revealGestures(state, slideDp, closing = false, mirrored = mirrored) }

/** Drag the card back towards its home edge: Launchpad closes live. */
fun Modifier.launchpadCloseDrag(state: LaunchpadRevealState, slideDp: Float, mirrored: Boolean = false): Modifier =
    this.pointerInput(state, slideDp, mirrored) { revealGestures(state, slideDp, closing = true, mirrored = mirrored) }

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.revealGestures(state: LaunchpadRevealState, slideDp: Float, closing: Boolean, mirrored: Boolean) {
    val slidePx = slideDp * density
    val edgePx = RevealMath.EDGE_DP * density
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (closing != state.open) return@awaitEachGesture
        val sign = if (mirrored) -1f else 1f
        val startX = if (mirrored) size.width - down.position.x else down.position.x
        if (!closing && !RevealMath.startsInEdge(startX, edgePx)) return@awaitEachGesture
        val slop = viewConfiguration.touchSlop
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var dx = 0f; var dy = 0f; var dragging = false
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
            if (!ch.pressed) break
            val d = ch.positionChange()
            dx += d.x * sign; dy += d.y
            tracker.addPosition(ch.uptimeMillis, ch.position)
            if (!dragging) {
                if (abs(dy) > slop * 3 && abs(dy) > abs(dx)) return@awaitEachGesture
                val along = if (closing) -dx else dx
                if (along > slop * 2 && along > abs(dy) * 1.5f) { dragging = true; state.beginDrag() }
            }
            if (dragging) {
                ch.consume()
                state.dragTo(RevealMath.fraction(if (closing) 1f else 0f, dx, slidePx))
            }
        }
        if (dragging) state.endDrag(tracker.calculateVelocity().x * sign)
    }
}

/** STR-01: the keyboard route. [registry] decides which chord means "Open Launchpad", so rebinding is honoured. */
fun Modifier.launchpadShortcut(registry: com.bos.sphere.core.input.ShortcutRegistry, onOpen: () -> Unit): Modifier =
    this.then(
        androidx.compose.ui.Modifier.onPreviewKeyEvent { e ->
            if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val cmd = registry.commandFor(LaunchpadShortcuts.chordOf(e.key.nativeKeyCode, e.isCtrlPressed, e.isAltPressed, e.isShiftPressed, e.isMetaPressed))
            if (cmd == com.bos.sphere.core.input.LauncherCommand.OpenLaunchpad) { onOpen(); true } else false
        },
    )

object LaunchpadShortcuts {
    fun chordOf(keyCode: Int, ctrl: Boolean, alt: Boolean, shift: Boolean, meta: Boolean) =
        com.bos.sphere.core.input.KeyChord(keyCode, ctrl, alt, shift, meta)

    /** True when [chord] is the one bound to OpenLaunchpad in [registry]. */
    fun opensLaunchpad(registry: com.bos.sphere.core.input.ShortcutRegistry, chord: com.bos.sphere.core.input.KeyChord) =
        registry.commandFor(chord) == com.bos.sphere.core.input.LauncherCommand.OpenLaunchpad
}
