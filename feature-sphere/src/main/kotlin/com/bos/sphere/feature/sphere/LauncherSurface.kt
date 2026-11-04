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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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

    /**
     * While scrubbing: how far the finger has pulled past the equator's first (negative) or last
     * (positive) column, in columns, before the rubber-band clamp; 0 inside. Back to 0 on release.
     * The launcher's pages read it to turn the page past a threshold (`PastEnd`).
     */
    var scrubPastEnd: Float by mutableFloatStateOf(0f)
        internal set

    /**
     * True from the moment a long-press in the current gesture opened something (an app's menu, a
     * widget's menu, the widget picker) until the next gesture starts. The pages read it so a drag
     * that follows such a long-press never turns the page behind what just opened.
     */
    var longPressOpened: Boolean = false
        internal set
}

/**
 * How far a scrub has pulled past the equator's ends, in columns ([LauncherSurfaceState.scrubPastEnd]):
 * [raw] is the unclamped scrub position, [startScrub] where the scrub began. A pull counts only when
 * the scrub *began at that end* (within half a column of it), so the page turn past the ends is a
 * second, deliberate pull from the last (or first) item: a long, fast stroke from the middle of the
 * list that overshoots the end never turns the page, because the coverflow has no fling and the finger
 * travel alone would otherwise count. Pure.
 */
object ScrubPastEnd {
    fun columns(startScrub: Float, raw: Float, maxIndex: Float): Float = when {
        raw < 0f && startScrub <= 0.5f -> raw
        raw > maxIndex && startScrub >= maxIndex - 0.5f -> raw - maxIndex
        else -> 0f
    }
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

private enum class Mode { Undecided, Scrub, Hub, Globe, Pinch, PassThrough }

/**
 * The interactive home surface: coverflow equator + all-apps globe, with unified gestures.
 *
 * - horizontal drag → scrub the equator (snap to column on release)
 * - vertical drag → pull the [hubContent] down / push it back up
 * - pinch in → all-apps globe; spread → settings (or back home from the globe)
 * - in the globe: drag → rotate, tap → launch
 * - tap a side tile → bring to focus; tap the focused tile → launch
 * - long-press a tile → [onLongPressApp] (context menu)
 *
 * Hosted widgets ([equatorItems] may mix [EquatorItem.Widget]s in with apps; the globe stays
 * apps-only): an unfocused widget tile behaves like an app tile (tap focuses it, drag scrubs,
 * pinch / spread / pull keep working). The focused widget at rest gets taps itself (its own
 * buttons and list rows); a drag past touch slop is still a scrub or hub pull, and the hosted view
 * receives ACTION_CANCEL. Long-press on a widget → [onLongPressWidget]; on empty surface →
 * [onLongPressEmpty]. Gestures that start inside a [touchPassThrough] region (the hub's widget
 * stack) are left to that content.
 *
 * Pages (a parent that reads touches first): when [gestureClaimed] turns true during a gesture, the
 * parent has taken it as a page gesture, and the surface does nothing more with it (a scrub in
 * progress settles). [onScrubReleased] reports, at the end of each scrub, how far it went past the
 * equator's ends ([LauncherSurfaceState.scrubPastEnd]). Both default to the old behaviour.
 */
@Composable
fun LauncherSurface(
    apps: List<AppEntry>,
    state: LauncherSurfaceState,
    onLaunch: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    equatorItems: List<EquatorItem> = remember(apps) { apps.map { EquatorItem.App(it) } },
    onLongPressWidget: (EquatorItem.Widget) -> Unit = {},
    onLongPressEmpty: () -> Unit = {},
    touchPassThrough: TouchPassThrough? = null,
    widgetTile: @Composable (item: EquatorItem.Widget, interactive: Boolean) -> Unit = { _, _ -> },
    gestureClaimed: () -> Boolean = { false },
    onScrubReleased: (pastEndColumns: Float) -> Unit = {},
    hubContent: @Composable (fraction: Float) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var container by remember { mutableStateOf(IntSize.Zero) }
    val globePoints = remember(apps.size) { fibonacciSphere(apps.size) }
    // The gesture coroutine outlives recompositions; read the latest lists and callbacks.
    val currentApps by rememberUpdatedState(apps)
    val currentItems by rememberUpdatedState(equatorItems)
    val currentOnLaunch by rememberUpdatedState(onLaunch)
    val currentOnLongPressApp by rememberUpdatedState(onLongPressApp)
    val currentOnLongPressWidget by rememberUpdatedState(onLongPressWidget)
    val currentOnLongPressEmpty by rememberUpdatedState(onLongPressEmpty)
    val currentOnOpenSettings by rememberUpdatedState(onOpenSettings)
    val currentGestureClaimed by rememberUpdatedState(gestureClaimed)
    val currentOnScrubReleased by rememberUpdatedState(onScrubReleased)
    val surfaceCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { container = it }
            .onGloballyPositioned { surfaceCoords[0] = it }
            .pointerInput(apps.size, container) {
                val slop = viewConfiguration.touchSlop
                val longPressMs = viewConfiguration.longPressTimeoutMillis
                val dragScalePx = state.sphereState.params.dragScale.dp.toPx()
                val pullPx = HubPullDp.dp.toPx()
                val dpi = density

                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    state.longPressOpened = false
                    val downPos = first.position
                    val startScrub = state.sphereState.position
                    val startHub = state.hub.value
                    val hubOpen = state.hub.value > 0.5f
                    val passThrough =
                        touchPassThrough?.contains(surfaceCoords[0], downPos) == true
                    var mode = when {
                        passThrough -> Mode.PassThrough
                        state.zoom.value > 0.5f -> Mode.Globe
                        else -> Mode.Undecided
                    }
                    var pinchStart = 0f
                    var pinchHandled = false
                    var lastRot = downPos
                    var longPressFired = false
                    // True only when the long-press actually opened something (a menu or the picker).
                    var longPressClaimed = false
                    // Whether this gesture moved the coverflow or the hub before a page gesture took
                    // it: the surface switches to Pinch as soon as a second finger lands, so the
                    // mode alone can't tell (a two-finger page swipe often starts with one finger).
                    var scrubbed = false
                    var hubPulled = false

                    fun settleScrub() {
                        val target = state.sphereState.position
                            .roundToInt().coerceIn(0, state.sphereState.maxIndex)
                        scope.launch {
                            Animatable(state.sphereState.position)
                                .animateTo(target.toFloat(), HyleMotion.settle()) {
                                    state.sphereState.position = value
                                }
                        }
                    }

                    while (true) {
                        val useTimeout = mode == Mode.Undecided && !longPressFired
                        val event = when {
                            useTimeout -> withTimeoutOrNull(longPressMs) { awaitPointerEvent() }
                            // After a long-press that opened something, the rest of the gesture
                            // belongs to the launcher: read it in the Initial pass and consume it,
                            // so a hosted widget under the finger gets ACTION_CANCEL instead of a
                            // click when it lifts. A long-press that opened nothing (e.g. on the
                            // open hub) leaves the gesture to whatever is under the finger.
                            longPressClaimed -> awaitPointerEvent(PointerEventPass.Initial)
                                .also { e -> e.changes.forEach { it.consume() } }
                            else -> awaitPointerEvent()
                        }
                        if (event == null) {
                            // long-press: no movement within the timeout
                            longPressFired = true
                            val items = currentItems
                            val idx = coverflowHitTest(downPos, items.size, state.sphereState, container, dpi)
                            when (val item = items.getOrNull(idx)) {
                                is EquatorItem.App -> {
                                    currentOnLongPressApp(item.entry)
                                    longPressClaimed = true
                                }
                                is EquatorItem.Widget -> {
                                    currentOnLongPressWidget(item)
                                    longPressClaimed = true
                                }
                                null -> if (!hubOpen) {
                                    currentOnLongPressEmpty()
                                    longPressClaimed = true
                                }
                            }
                            state.longPressOpened = longPressClaimed
                            continue
                        }

                        // A page gesture took over (the pages read touches before the surface).
                        if (mode != Mode.PassThrough && currentGestureClaimed()) {
                            if (scrubbed) settleScrub()
                            if (hubPulled) {
                                val target = if (state.hub.value > HubLatch) 1f else 0f
                                scope.launch { state.hub.animateTo(target, HyleMotion.settle()) }
                            }
                            state.scrubPastEnd = 0f
                            mode = Mode.PassThrough
                        }

                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        // Started inside a pass-through region: that content owns the gesture.
                        if (mode == Mode.PassThrough) continue

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
                                        currentOnOpenSettings()
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
                            scrubbed = true
                            val raw = startScrub - totalDx / dragScalePx
                            state.scrubPastEnd =
                                ScrubPastEnd.columns(startScrub, raw, state.sphereState.maxIndex.toFloat())
                            state.sphereState.position =
                                raw.coerceIn(
                                    state.sphereState.minPosition,
                                    state.sphereState.maxPosition,
                                )
                            ch.consume()
                        } else if (mode == Mode.Hub) {
                            hubPulled = true
                            val f = (startHub + totalDy / pullPx).coerceIn(0f, 1f)
                            scope.launch { state.hub.snapTo(f) }
                            ch.consume()
                        }
                    }

                    // --- gesture end: settle ---
                    val pastEnd = state.scrubPastEnd
                    state.scrubPastEnd = 0f
                    when (mode) {
                        Mode.Scrub -> {
                            settleScrub()
                            currentOnScrubReleased(pastEnd)
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
                                    val globeApps = currentApps
                                    val gi = globeHitTest(downPos, globePoints, dpi)
                                    if (gi in globeApps.indices) {
                                        currentOnLaunch(globeApps[gi])
                                        scope.launch { state.zoom.animateTo(0f, HyleMotion.give()) }
                                    }
                                }

                                else -> {
                                    val items = currentItems
                                    val idx = coverflowHitTest(
                                        downPos, items.size, state.sphereState, container, dpi,
                                    )
                                    if (idx in items.indices) {
                                        val col = state.sphereState.columnOf(idx)
                                        if (abs(col - state.sphereState.position) < 0.5f) {
                                            // A focused widget already received this tap itself.
                                            (items[idx] as? EquatorItem.App)?.let { currentOnLaunch(it.entry) }
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
        // Equator (fades out as the globe comes in). Hosted widgets only take touches while
        // nothing (hub, globe) is in front of the equator.
        CoverflowLayer(
            items = equatorItems,
            state = state.sphereState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (1f - smoothstep01(state.zoom.value)) },
            widgetsInteractive = state.hub.value < 0.01f && state.zoom.value < 0.01f,
            widgetTile = widgetTile,
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
