package com.bos.sphere.feature.grid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Static mini-phone drawings of the new home styles (sample apps, no state). */

@Composable
fun XmbPreview() {
    MiniPhone(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            // Soft wave ribbon.
            for (k in 0 until 2) {
                val y0 = h * (0.62f + 0.1f * k)
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, y0)
                    for (i in 1..16) lineTo(w * i / 16, y0 + (kotlin.math.sin(i * 0.5 + k * 2).toFloat()) * h * 0.04f)
                }
                drawPath(p, HyleVioletish.copy(alpha = 0.35f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f))
            }
            // Category bar: selected dot at the anchor (1/4), dimmer neighbours.
            val barY = h * 0.2f; val ax = w * 0.27f; val gap = w * 0.2f
            for (i in -1..3) {
                val sel = i == 0
                val r = if (sel) w * 0.075f else w * 0.05f
                drawCircle(Color.White.copy(alpha = if (sel) 0.95f else 0.4f), r, Offset(ax + i * gap, barY))
            }
            drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(ax - w * 0.06f, barY + w * 0.11f), Size(w * 0.12f, h * 0.012f), CornerRadius(3f))
            // List under the anchor: focused row bigger and brighter.
            val rowY0 = h * 0.40f
            for (r in 0 until 4) {
                val focus = r == 0
                val s = if (focus) w * 0.17f else w * 0.12f
                val y = rowY0 + r * h * 0.14f + (if (r > 0) h * 0.03f else 0f)
                val cx = ax - s / 2
                drawRoundRect(SampleApps.color(r), Offset(cx, y), Size(s, s), CornerRadius(s * 0.28f), alpha = if (focus) 1f else 0.65f)
                drawRoundRect(Color.White.copy(alpha = if (focus) 0.9f else 0.45f), Offset(cx + s + w * 0.04f, y + s * 0.3f), Size(w * (if (focus) 0.36f else 0.28f), s * 0.34f), CornerRadius(3f))
            }
        }
    }
}

private val HyleVioletish = Color(0xFF9B8CFF)

@Composable
fun DsiPreview() {
    MiniPhone(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val cols = 3; val rows = 3
            val cell = size.width / cols
            val plate = cell * 0.86f
            for (r in 0 until rows) for (c in 0 until cols) {
                val x = c * cell + (cell - plate) / 2
                val y = r * cell * 1.2f + (cell - plate) / 2
                val rr = CornerRadius(plate * 0.22f)
                drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(x, y + plate * 0.06f), Size(plate, plate), rr)
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFAFBFD), Color(0xFFCDD3DD)), y, y + plate), Offset(x, y), Size(plate, plate), rr)
                val ic = plate * 0.6f
                drawRoundRect(SampleApps.color(r * cols + c), Offset(x + (plate - ic) / 2, y + (plate - ic) / 2), Size(ic, ic), CornerRadius(ic * 0.25f))
                drawRoundRect(Color.White.copy(alpha = 0.85f), Offset(x + plate * 0.2f, y + plate + cell * 0.03f), Size(plate * 0.6f, cell * 0.1f), CornerRadius(cell * 0.05f))
            }
        }
    }
}

@Composable
fun ContinuousPreview() {
    MiniPhone(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)) {
            val cols = 4; val rows = 6
            val cell = (size.width - 8f) / cols
            val icon = cell * 0.7f
            for (r in 0 until rows) for (c in 0 until cols) {
                val a = (1f - (if (r == 0) 0.55f else 0f) - (if (r == rows - 1) 0.55f else 0f)).coerceIn(0.15f, 1f)
                drawRoundRect(SampleApps.color(r * cols + c), Offset(c * cell + (cell - icon) / 2, r * cell * 1.15f + 2f), Size(icon, icon), CornerRadius(icon * 0.26f), alpha = a)
            }
            // Scroll bar on the trailing edge.
            val x = size.width - 2f
            drawRoundRect(Color.White.copy(alpha = 0.2f), Offset(x, 4f), Size(2.5f, size.height - 8f), CornerRadius(2f))
            drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(x, 4f + (size.height - 8f) * 0.15f), Size(2.5f, (size.height - 8f) * 0.35f), CornerRadius(2f))
        }
    }
}
