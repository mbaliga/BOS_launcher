package com.bos.sphere.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.design.HyleColors

/**
 * The Hyle "dot-grid substrate" (§4): a faint network of dots under the surface, masked toward
 * the focal centre so the equator stands clear. A flat fallback for the AGSL material that will
 * replace it later.
 */
@Composable
fun DotGridBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val spacing = 26.dp.toPx()
        val dot = 1.2.dp.toPx()
        val cx = size.width / 2f
        val cy = size.height * 0.46f
        val maxR = maxOf(size.width, size.height) * 0.62f
        var y = spacing / 2f
        while (y < size.height) {
            var x = spacing / 2f
            while (x < size.width) {
                val d = Offset(x - cx, y - cy).getDistance() / maxR
                // Fade in from the centre hole outward, then back down at the edges.
                val a = (d * 1.4f).coerceIn(0f, 1f) * (1f - (d - 0.7f).coerceAtLeast(0f) * 2f)
                if (a > 0.02f) {
                    drawCircle(
                        color = HyleColors.Violet.copy(alpha = 0.10f * a),
                        radius = dot,
                        center = Offset(x, y),
                    )
                }
                x += spacing
            }
            y += spacing
        }
    }
}
