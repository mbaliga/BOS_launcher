package com.bos.sphere.feature.sphere

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Text
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.HyleMotion
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Base on-screen size of a tile (the prototype's 132×132 card). */
private val TileSize = 132.dp

/**
 * The home surface: the coverflow equator. Renders [apps] as magnified tiles via
 * `graphicsLayer`, scrubs on horizontal drag with integer snap, and launches on tap of the
 * focused tile (a tap on a side tile scrubs it to focus first — mirrors the prototype).
 */
@Composable
fun Coverflow(
    apps: List<AppEntry>,
    state: SphereState,
    onLaunch: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // position lives in SphereState; we animate it through this Animatable-like driver.
    val posDriver = remember { PositionDriver(state) }

    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            // Scrub: horizontal drag moves the equator; release snaps to the nearest column.
            .pointerInput(state, apps.size) {
                val dragScalePx = with(density) { state.params.dragScale.dp.toPx() }
                detectDragGestures(
                    onDragStart = { scope.launch { posDriver.stop() } },
                    onDragEnd = { scope.launch { posDriver.settleToNearest() } },
                    onDragCancel = { scope.launch { posDriver.settleToNearest() } },
                ) { change, dragAmount ->
                    change.consume()
                    val delta = -dragAmount.x / dragScalePx
                    scope.launch { posDriver.scrubBy(delta) }
                }
            }
            // Tap: hit-test transformed tiles, then select-or-launch.
            .pointerInput(state, apps.size, containerSize) {
                detectTapGestures { tap ->
                    val hit = hitTest(tap, apps.size, state, containerSize, density.density)
                    if (hit >= 0) {
                        val col = state.columnOf(hit)
                        if (abs(col - state.position) < 0.5f) {
                            onLaunch(apps[hit])
                        } else {
                            scope.launch { posDriver.animateToIndex(col) }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        apps.forEachIndexed { index, app ->
            // derivedStateOf so each tile only recomposes its layer when its transform changes.
            val transform by remember(index, state) {
                derivedStateOf { state.transformFor(index) }
            }
            if (transform.visible) {
                AppTile(
                    app = app,
                    transform = transform,
                    modifier = Modifier
                        .size(TileSize)
                        .zIndex(transform.zIndex)
                        .graphicsLayer {
                            translationX = transform.translationX.dp.toPx()
                            translationY = transform.translationY.dp.toPx()
                            scaleX = transform.scale
                            scaleY = transform.scale
                            rotationX = transform.rotationX
                            rotationY = transform.rotationY
                            cameraDistance = state.params.perspective.dp.toPx()
                        },
                )
            }
        }
    }
}

@Composable
private fun AppTile(
    app: AppEntry,
    transform: TileTransform,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (transform.focused) {
        HyleColors.Violet.copy(alpha = 0.55f)
    } else {
        HyleColors.Hairline
    }
    Box(
        modifier = modifier
            .background(HyleColors.Surface, RoundedCornerShape(22.dp))
            .border(1.dp, borderColor, RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // Icon (loaded lazily, cached per entry key).
        val icon = remember(app.key) { app.loadIcon()?.let { runCatching { it.toBitmap() }.getOrNull() } }
        if (icon != null) {
            val bmp = remember(icon) { icon.asImageBitmap() }
            Image(
                bitmap = bmp,
                contentDescription = app.label,
                modifier = Modifier.size(60.dp),
            )
        } else {
            // Fallback monogram chip if no icon is available.
            Canvas(modifier = Modifier.size(60.dp)) {
                scale(1f) {
                    drawCircle(color = HyleColors.Violet.copy(alpha = 0.25f))
                }
            }
        }
        if (transform.focused) {
            Text(
                text = app.label,
                color = HyleColors.InkBright,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { translationY = 36.dp.toPx() },
            )
        }
    }
}

/**
 * Returns the index of the top-most tile whose transformed rect contains [tap], or -1.
 * Tile centers are reconstructed from the same transforms used to paint them.
 */
private fun hitTest(
    tap: Offset,
    itemCount: Int,
    state: SphereState,
    container: IntSize,
    density: Float,
): Int {
    val cx = container.width / 2f
    val cy = container.height / 2f
    val halfBase = TileSize.value * density / 2f
    var best = -1
    var bestZ = Float.NEGATIVE_INFINITY
    for (i in 0 until itemCount) {
        val t = state.transformFor(i)
        if (!t.visible) continue
        val tileCx = cx + t.translationX * density
        val tileCy = cy + t.translationY * density
        val half = halfBase * t.scale
        if (abs(tap.x - tileCx) <= half && abs(tap.y - tileCy) <= half && t.zIndex > bestZ) {
            best = i
            bestZ = t.zIndex
        }
    }
    return best
}

/**
 * Thin driver that animates [SphereState.position]. Kept separate from gesture code so the
 * snap uses the Hyle "Settle" spec and over-scroll is rubber-banded back into bounds.
 */
private class PositionDriver(private val state: SphereState) {
    private val anim = androidx.compose.animation.core.Animatable(state.position).also {
        it.updateBounds(state.minPosition, state.maxPosition)
    }

    suspend fun stop() {
        anim.stop()
    }

    suspend fun scrubBy(deltaColumns: Float) {
        anim.snapTo((anim.value + deltaColumns).coerceIn(state.minPosition, state.maxPosition))
        state.position = anim.value
    }

    suspend fun settleToNearest() {
        animateToIndex(anim.value.roundToInt().coerceIn(0, state.maxIndex))
    }

    suspend fun animateToIndex(index: Int) {
        anim.updateBounds(state.minPosition, state.maxPosition)
        anim.animateTo(index.toFloat(), animationSpec = HyleMotion.settle()) {
            state.position = value
        }
    }
}
