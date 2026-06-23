package com.bos.sphere.feature.sphere

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.HyleMotion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/** Drag distance (dp) for a full hub pull, and the latch fraction to commit it open. */
private const val HubPullDp = 330f
private const val HubLatch = 0.40f

/**
 * Hoisted state for the whole home surface. The two navigation axes from the brief's state
 * model: [zoom] (0 = home equator, 1 = all-apps globe) and [hub] (0..1 pull-down). [sphereState]
 * owns the coverflow scrub; [globeState] owns globe rotation. Chrome reads [zoomValue]/[hubValue]
 * to fade out of the way.
 */
@Stable
class LauncherSurfaceState(
    val sphereState: SphereState,
    val globeState: GlobeState,
    val zoom: Animatable<Float, AnimationVector1D>,
    val hub: Animatable<Float, AnimationVector1D>,
) {
    val zoomValue: Float get() = zoom.value
    val hubValue: Float get() = hub.value
}

@Composable
fun rememberLauncherSurfaceState(
    itemCount: Int,
    rows: Int,
    params: SphereParams,
): LauncherSurfaceState {
    val sphere = remember { SphereState(itemCount, rows, params) }
    sphere.itemCount = itemCount
    if (sphere.rows != rows) sphere.updateRows(rows)
    sphere.params = params
    val globe = remember { GlobeState() }
    val zoom = remember { Animatable(0f) }
    val hub = remember { Animatable(0f) }
    return remember { LauncherSurfaceState(sphere, globe, zoom, hub) }
}

private enum class Mode { Undecided, Scrub, Hub, Globe, Pinch }

/**
 * The interactive home surface: coverflow equator + all-apps globe, with unified gestures.
 *
 * - horizontal drag → scrub the equator (snap to column on release)
 * - vertical drag → pull the [hubContent] down / push it back up
 * - pinch in → all-apps globe; spread → settings (or back home from the globe)
 * - in the globe: drag → rotate, tap → launch
 * - tap a side tile → bring to focus; tap the focused tile → launch
 * - long-press a tile → [onLongPressApp] (context menu)
 */
@Composable
fun LauncherSurface(
    apps: List<AppEntry>,
    state: LauncherSurfaceState,
    onLaunch: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    hubContent: @Composable (fraction: Float) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var container by remember { mutableStateOf(IntSize.Zero) }
    val globePoints = remember(apps.size) { fibonacciSphere(apps.size) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { container = it }
            .pointerInput(apps.size, container) {
                val slop = viewConfiguration.touchSlop
                val longPressMs = viewConfiguration.longPressTimeoutMillis
                val dragScalePx = state.sphereState.params.dragScale.dp.toPx()
                val pullPx = HubPullDp.dp.toPx()
                val dpi = density

                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    val downPos = first.position
                    val startScrub = state.sphereState.position
                    val startHub = state.hub.value
                    val hubOpen = state.hub.value > 0.5f
                    var mode = if (state.zoom.value > 0.5f) Mode.Globe else Mode.Undecided
                    var pinchStart = 0f
                    var pinchHandled = false
                    var lastRot = downPos
                    var longPressFired = false

                    while (true) {
                        val useTimeout = mode == Mode.Undecided && !longPressFired
                        val event = if (useTimeout) {
                            withTimeoutOrNull(longPressMs) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }
                        if (event == null) {
                            // long-press: no movement within the timeout
                            longPressFired = true
                            val idx = coverflowHitTest(downPos, apps.size, state.sphereState, container, dpi)
                            if (idx in apps.indices) onLongPressApp(apps[idx])
                            continue
                        }

                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        // --- pinch (two fingers, any mode) ---
                        if (pressed.size >= 2) {
                            val a = pressed[0].position
                            val b = pressed[1].position
                            val dist = (a - b).getDistance()
                            if (mode != Mode.Pinch) {
                                mode = Mode.Pinch
                                pinchStart = if (dist > 0f) dist else 1f
                                pinchHandled = false
                            }
                            if (!pinchHandled) {
                                val ratio = dist / pinchStart
                                if (ratio < 0.78f) {
                                    pinchHandled = true
                                    scope.launch { state.zoom.animateTo(1f, HyleMotion.reach()) }
                                } else if (ratio > 1.28f) {
                                    pinchHandled = true
                                    if (state.zoom.value > 0.5f) {
                                        scope.launch { state.zoom.animateTo(0f, HyleMotion.give()) }
                                    } else {
                                        onOpenSettings()
                                    }
                                }
                            }
                            pressed.forEach { it.consume() }
                            continue
                        }
                        if (mode == Mode.Pinch) {
                            pressed.forEach { it.consume() }
                            continue
                        }

                        val ch = pressed.firstOrNull { it.id == first.id } ?: pressed.first()
                        val pos = ch.position
                        val totalDx = pos.x - downPos.x
                        val totalDy = pos.y - downPos.y

                        if (mode == Mode.Globe) {
                            val dx = pos.x - lastRot.x
                            val dy = pos.y - lastRot.y
                            state.globeState.rotateBy(dx * 0.006f, dy * 0.006f)
                            lastRot = pos
                            if (ch.positionChanged()) ch.consume()
                            continue
                        }

                        if (mode == Mode.Undecided) {
                            if (abs(totalDx) > slop || abs(totalDy) > slop) {
                                mode = if (abs(totalDx) > abs(totalDy)) Mode.Scrub else Mode.Hub
                            } else {
                                continue
                            }
                        }

                        if (mode == Mode.Scrub) {
                            state.sphereState.position =
                                (startScrub - totalDx / dragScalePx).coerceIn(
                                    state.sphereState.minPosition,
                                    state.sphereState.maxPosition,
                                )
                            ch.consume()
                        } else if (mode == Mode.Hub) {
                            val f = (startHub + totalDy / pullPx).coerceIn(0f, 1f)
                            scope.launch { state.hub.snapTo(f) }
                            ch.consume()
                        }
                    }

                    // --- gesture end: settle ---
                    when (mode) {
                        Mode.Scrub -> {
                            val target = state.sphereState.position
                                .roundToInt().coerceIn(0, state.sphereState.maxIndex)
                            scope.launch {
                                Animatable(state.sphereState.position)
                                    .animateTo(target.toFloat(), HyleMotion.settle()) {
                                        state.sphereState.position = value
                                    }
                            }
                        }

                        Mode.Hub -> {
                            val target = if (state.hub.value > HubLatch) 1f else 0f
                            scope.launch { state.hub.animateTo(target, HyleMotion.settle()) }
                        }

                        Mode.Undecided -> if (!longPressFired) {
                            when {
                                hubOpen ->
                                    scope.launch { state.hub.animateTo(0f, HyleMotion.give()) }

                                state.zoom.value > 0.5f -> {
                                    projectGlobe(
                                        globePoints,
                                        state.globeState.yaw,
                                        state.globeState.pitch,
                                        state.zoom.value,
                                        state.sphereState.params.focus,
                                        container,
                                    )
                                    val gi = globeHitTest(downPos, globePoints, dpi)
                                    if (gi in apps.indices) {
                                        onLaunch(apps[gi])
                                        scope.launch { state.zoom.animateTo(0f, HyleMotion.give()) }
                                    }
                                }

                                else -> {
                                    val idx = coverflowHitTest(
                                        downPos, apps.size, state.sphereState, container, dpi,
                                    )
                                    if (idx in apps.indices) {
                                        val col = state.sphereState.columnOf(idx)
                                        if (abs(col - state.sphereState.position) < 0.5f) {
                                            onLaunch(apps[idx])
                                        } else {
                                            scope.launch {
                                                Animatable(state.sphereState.position)
                                                    .animateTo(col.toFloat(), HyleMotion.settle()) {
                                                        state.sphereState.position = value
                                                    }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> Unit
                    }
                }
            },
    ) {
        // Equator (fades out as the globe comes in).
        CoverflowLayer(
            apps = apps,
            state = state.sphereState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (1f - smoothstep01(state.zoom.value)) },
        )

        // All-apps globe (fades in with zoom).
        AllAppsGlobeLayer(
            apps = apps,
            points = globePoints,
            state = state.globeState,
            zoom = state.zoom.value,
            focus = state.sphereState.params.focus,
            container = container,
        )

        // Hub overlay (the composable positions itself by the fraction).
        hubContent(state.hub.value)
    }
}

private fun smoothstep01(t: Float): Float = (t * t * (3 - 2 * t)).coerceIn(0f, 1f)
