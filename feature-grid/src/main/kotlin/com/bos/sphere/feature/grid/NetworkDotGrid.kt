package com.bos.sphere.feature.grid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.NetworkKind
import com.bos.sphere.core.data.NetworkStatus

/** Dot colour for a status: red (weak) → green (strong); grey in airplane mode; black offline. */
fun networkDotColor(status: NetworkStatus): Color = when (status.kind) {
    NetworkKind.Good, NetworkKind.Poor -> {
        val red = Color(0xFFFF4D4D)
        val amber = Color(0xFFFFB347)
        val green = Color(0xFF6BFF8F)
        val s = status.strength.coerceIn(0f, 1f)
        if (s < 0.5f) lerp(red, amber, s / 0.5f) else lerp(amber, green, (s - 0.5f) / 0.5f)
    }
    NetworkKind.Airplane -> Color(0xFF8A8F99)
    NetworkKind.None -> Color.Black
}

/**
 * The faint substrate dot grid, colour-coded by network: it breathes (a slow, steady glow) in
 * green when the signal is good and red when it is poor, sits solid grey in airplane mode, and
 * goes dark with no connection. Drawn as a single point batch so the breathing redraw is cheap.
 */
@Composable
fun NetworkDotGrid(status: NetworkStatus, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val target = if (enabled) networkDotColor(status) else Color(0xFF3A3F4A)
    val color by animateColorAsState(target, tween(900), label = "dotColor")
    val breathes = enabled && (status.kind == NetworkKind.Good || status.kind == NetworkKind.Poor)
    val transition = rememberInfiniteTransition(label = "breathe")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val points = remember { mutableListOf<Offset>() }
    Canvas(modifier) {
        val spacing = 22.dp.toPx()
        // Rebuild the lattice only when the size changes.
        val cols = (size.width / spacing).toInt() + 1
        val rows = (size.height / spacing).toInt() + 1
        if (points.size != cols * rows) {
            points.clear()
            val ox = (size.width - (cols - 1) * spacing) / 2f
            val oy = (size.height - (rows - 1) * spacing) / 2f
            for (r in 0 until rows) for (c in 0 until cols) points += Offset(ox + c * spacing, oy + r * spacing)
        }
        val alpha = when {
            !enabled -> 0.22f
            status.kind == NetworkKind.None -> 0f
            status.kind == NetworkKind.Airplane -> 0.32f
            else -> {
                // Smooth inhale/exhale: 0.14 … 0.42.
                val b = 0.5f - 0.5f * kotlin.math.cos(phase * 2f * Math.PI.toFloat())
                0.14f + 0.28f * (if (breathes) b else 0.5f)
            }
        }
        if (alpha <= 0f) return@Canvas
        drawPoints(
            points = points,
            pointMode = PointMode.Points,
            color = color.copy(alpha = alpha),
            strokeWidth = 2.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
