package com.bos.sphere.feature.sphere

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.HyleColors
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** Diameter of a globe app dot, dp (prototype `.gapp` = 52px). */
private val GlobeDotSize = 52.dp

/** Rotation state for the all-apps globe (yaw/pitch in radians). */
@Stable
class GlobeState {
    var yaw by mutableFloatStateOf(0f)
    var pitch by mutableFloatStateOf(0f)

    fun rotateBy(dxRad: Float, dyRad: Float) {
        yaw += dxRad
        pitch = (pitch + dyRad).coerceIn(-1.45f, 1.45f)
    }
}

/** A globe point on the unit sphere plus its projected screen state. */
internal class GlobePoint(val x: Float, val y: Float, val z: Float) {
    var screenX = 0f
    var screenY = 0f
    var pz = 0f
    var scale = 1f
    var alpha = 0f
}

/** Fibonacci-sphere distribution of [count] points (golden-angle spiral). */
internal fun fibonacciSphere(count: Int): List<GlobePoint> {
    if (count <= 0) return emptyList()
    val ga = (Math.PI * (3 - sqrt(5.0))).toFloat()
    return List(count) { i ->
        val y = 1f - (i / (count - 1f).coerceAtLeast(1f)) * 2f
        val r = sqrt((1f - y * y).coerceAtLeast(0f))
        val th = i * ga
        GlobePoint(x = cos(th) * r, y = y, z = sin(th) * r)
    }
}

/**
 * Projects every point for the current [yaw]/[pitch]/[zoom] into [container] pixel space,
 * mutating each [GlobePoint]. Returns the front-most point index (largest pz) for the label.
 * Ported from the prototype `renderGlobe`.
 */
internal fun projectGlobe(
    points: List<GlobePoint>,
    yaw: Float,
    pitch: Float,
    zoom: Float,
    focus: Float,
    container: IntSize,
): Int {
    val az = smoothstep(zoom)
    val cyy = cos(yaw); val syy = sin(yaw)
    val cpp = cos(pitch); val spp = sin(pitch)
    val cx = container.width / 2f
    val cyc = container.height * 0.46f
    val spread = (minOf(container.width * 0.32f, container.height * 0.42f)) * (0.7f + 0.3f * az)
    var frontIdx = -1
    var frontPz = -2f
    for (i in points.indices) {
        val g = points[i]
        val x1 = g.x * cyy + g.z * syy
        val z1 = -g.x * syy + g.z * cyy
        val y1 = g.y
        val y2 = y1 * cpp - z1 * spp
        val z2 = y1 * spp + z1 * cpp
        val pz = z2
        g.screenX = cx + x1 * spread
        g.screenY = cyc - y2 * spread
        g.pz = pz
        val ang = acos(pz.coerceIn(-1f, 1f))
        val lensFall = smoothstep((1f - ang / 0.95f).coerceAtLeast(0f))
        g.scale = (0.42f + 0.58f * ((pz + 1f) / 2f)) * (1f + (focus - 1f) * lensFall)
        g.alpha = ((pz + 0.25f) / 0.55f).coerceIn(0f, 1f)
        if (pz > frontPz) { frontPz = pz; frontIdx = i }
    }
    return frontIdx
}

private fun smoothstep(t: Float): Float = (t * t * (3 - 2 * t)).coerceIn(0f, 1f)

/**
 * Renders the all-apps globe. Visible only while [zoom] > 0. Rotation is driven by
 * [LauncherSurface]'s gestures through [state]; taps are hit-tested via [globeHitTest].
 */
@Composable
internal fun AllAppsGlobeLayer(
    apps: List<AppEntry>,
    points: List<GlobePoint>,
    state: GlobeState,
    zoom: Float,
    focus: Float,
    container: IntSize,
    modifier: Modifier = Modifier,
) {
    if (zoom <= 0.001f || apps.isEmpty() || points.size != apps.size) return
    val az = smoothstep(zoom)

    // Recompute projection each frame (cheap for tens–hundreds of points).
    val frontIdx = projectGlobe(points, state.yaw, state.pitch, zoom, focus, container)

    Box(modifier = modifier.fillMaxSize().graphicsLayer { alpha = az }) {
        apps.forEachIndexed { i, app ->
            val g = points[i]
            if (g.alpha <= 0.02f) return@forEachIndexed
            val bmp = remember(app.key) {
                app.loadIcon()?.let { runCatching { it.toBitmap().asImageBitmap() }.getOrNull() }
            }
            Box(
                modifier = Modifier
                    .size(GlobeDotSize)
                    .graphicsLayer {
                        translationX = g.screenX - (GlobeDotSize.toPx() / 2f)
                        translationY = g.screenY - (GlobeDotSize.toPx() / 2f)
                        scaleX = g.scale
                        scaleY = g.scale
                        alpha = g.alpha
                    }
                    .background(HyleColors.Surface, CircleShape),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                if (bmp != null) {
                    Image(bitmap = bmp, contentDescription = app.label, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

/** Hit-tests a tap against the projected (front-facing) globe dots. Returns app index or -1. */
internal fun globeHitTest(
    tap: Offset,
    points: List<GlobePoint>,
    density: Float,
): Int {
    var best = -1
    var bestD = Float.MAX_VALUE
    val radiusPx = GlobeDotSize.value * density / 2f
    for (i in points.indices) {
        val g = points[i]
        if (g.pz < 0.2f) continue
        val d = hypot(g.screenX - tap.x, g.screenY - tap.y)
        if (d < bestD && d < radiusPx * 0.8f * g.scale) { bestD = d; best = i }
    }
    return best
}
