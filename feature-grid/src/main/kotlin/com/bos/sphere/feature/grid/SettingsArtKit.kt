package com.bos.sphere.feature.grid

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Wave 54 (settingsVisual): the small drawing kit the illustrations of [SettingsArt] share. Everything is drawn in code from the ink it is given
 * (no bitmaps), in physical coordinates (a drawing never mirrors with the layout direction, so "Right to left" keeps its arrow on the left).
 */

/** A set of illustrations, one per value of [values] (an enum's entries, or a fixed list). A value without a drawing is a compile error in the provider's `when`, and a failing test. */
class ArtSet<T>(val values: List<T>, private val provider: (T) -> VisualArt) {
    fun art(v: T): VisualArt = provider(v)
}

fun DrawScope.dp(v: Float): Float = v * density

/** A rectangle in fractions of the art area. */
fun DrawScope.frac(x: Float, y: Float, w: Float, h: Float): Pair<Offset, Size> = Offset(size.width * x, size.height * y) to Size(size.width * w, size.height * h)

fun DrawScope.rect(color: Color, x: Float, y: Float, w: Float, h: Float, radius: Float = 0.12f, stroke: Float = 0f) {
    val (o, s) = frac(x, y, w, h)
    val r = CornerRadius(minOf(s.width, s.height) * radius)
    if (stroke > 0f) drawRoundRect(color, o, s, r, style = Stroke(dp(stroke), join = StrokeJoin.Round)) else drawRoundRect(color, o, s, r, style = Fill)
}

fun DrawScope.dot(color: Color, cx: Float, cy: Float, rDp: Float, stroke: Float = 0f) {
    val c = Offset(size.width * cx, size.height * cy)
    if (stroke > 0f) drawCircle(color, dp(rDp), c, style = Stroke(dp(stroke))) else drawCircle(color, dp(rDp), c)
}

fun DrawScope.line(color: Color, x1: Float, y1: Float, x2: Float, y2: Float, wDp: Float = 1.5f) {
    drawLine(color, Offset(size.width * x1, size.height * y1), Offset(size.width * x2, size.height * y2), dp(wDp), cap = androidx.compose.ui.graphics.StrokeCap.Round)
}

/** An arrow from (x1,y1) to (x2,y2) in fractions, with an open head. */
fun DrawScope.arrow(color: Color, x1: Float, y1: Float, x2: Float, y2: Float, wDp: Float = 2f) {
    val a = Offset(size.width * x1, size.height * y1); val b = Offset(size.width * x2, size.height * y2)
    drawLine(color, a, b, dp(wDp), cap = androidx.compose.ui.graphics.StrokeCap.Round)
    val dx = b.x - a.x; val dy = b.y - a.y
    val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
    val ux = dx / len; val uy = dy / len
    val h = dp(4.5f)
    val head = Path().apply {
        moveTo(b.x - ux * h - uy * h * 0.8f, b.y - uy * h + ux * h * 0.8f); lineTo(b.x, b.y); lineTo(b.x - ux * h + uy * h * 0.8f, b.y - uy * h - ux * h * 0.8f)
    }
    drawPath(head, color, style = Stroke(dp(wDp), join = StrokeJoin.Round, cap = androidx.compose.ui.graphics.StrokeCap.Round))
}

/** The phone outline, centred; returns its box in pixels. [wide] draws it lying down. */
fun DrawScope.phoneFrame(ink: VisualInk, fill: Color? = null, wide: Boolean = false): Pair<Offset, Size> {
    val h = if (wide) size.height * 0.62f else size.height * 0.88f
    val w = if (wide) h * 1.85f else h * 0.56f
    val o = Offset((size.width - w) / 2f, (size.height - h) / 2f)
    val r = CornerRadius(dp(5f))
    if (fill != null) drawRoundRect(fill, o, Size(w, h), r)
    drawRoundRect(ink.soft, o, Size(w, h), r, style = Stroke(dp(1.5f)))
    return o to Size(w, h)
}

/** A rectangle given in fractions of a pixel box. */
fun DrawScope.inBox(b: Pair<Offset, Size>, color: Color, x: Float, y: Float, w: Float, h: Float, radius: Float = 0.2f, stroke: Float = 0f) {
    val (o, s) = b
    val off = Offset(o.x + s.width * x, o.y + s.height * y); val sz = Size(s.width * w, s.height * h)
    val r = CornerRadius(minOf(sz.width, sz.height) * radius)
    if (stroke > 0f) drawRoundRect(color, off, sz, r, style = Stroke(dp(stroke), join = StrokeJoin.Round)) else drawRoundRect(color, off, sz, r)
}

fun DrawScope.dotIn(b: Pair<Offset, Size>, color: Color, x: Float, y: Float, rDp: Float, stroke: Float = 0f) {
    val (o, s) = b
    val c = Offset(o.x + s.width * x, o.y + s.height * y)
    if (stroke > 0f) drawCircle(color, dp(rDp), c, style = Stroke(dp(stroke))) else drawCircle(color, dp(rDp), c)
}

fun DrawScope.lineIn(b: Pair<Offset, Size>, color: Color, x1: Float, y1: Float, x2: Float, y2: Float, wDp: Float = 1.5f) {
    val (o, s) = b
    drawLine(color, Offset(o.x + s.width * x1, o.y + s.height * y1), Offset(o.x + s.width * x2, o.y + s.height * y2), dp(wDp), cap = androidx.compose.ui.graphics.StrokeCap.Round)
}

/** A grid of app squares in a pixel box: [cols] by [rows], leaving [pad] of the box's width at the sides. */
fun DrawScope.appGridIn(b: Pair<Offset, Size>, color: Color, cols: Int, rows: Int, top: Float = 0.14f, pad: Float = 0.14f, gap: Float = 0.1f, accentCell: Int = -1, accent: Color = color) {
    val (o, s) = b
    val cell = (s.width * (1f - 2 * pad) - s.width * gap * (cols - 1)) / cols
    var n = 0
    for (r in 0 until rows) for (c in 0 until cols) {
        drawRoundRect(if (n == accentCell) accent else color, Offset(o.x + s.width * pad + c * (cell + s.width * gap), o.y + s.height * top + r * (cell + s.width * gap)), Size(cell, cell), CornerRadius(cell * 0.3f))
        n++
    }
}

// ---- seven-segment digits for the clock illustrations -------------------------------------------------------------------------------------------

private val SEGMENTS = intArrayOf(0b1110111, 0b0010010, 0b1011101, 0b1011011, 0b0111010, 0b1101011, 0b1101111, 0b1010010, 0b1111111, 0b1111011)

/** One digit as seven segments in a (x, y, w, h) box given in pixels; [thick] is the segment thickness in pixels. */
fun DrawScope.digit(d: Int, x: Float, y: Float, w: Float, h: Float, thick: Float, color: Color) {
    val m = SEGMENTS[d.coerceIn(0, 9)]
    fun seg(bit: Int, sx: Float, sy: Float, sw: Float, sh: Float) {
        if (m and (1 shl (6 - bit)) != 0) drawRoundRect(color, Offset(sx, sy), Size(sw, sh), CornerRadius(thick / 2f))
    }
    val t = thick
    seg(0, x + t, y, w - 2 * t, t)
    seg(1, x, y + t, t, h / 2f - t * 1.25f)
    seg(2, x + w - t, y + t, t, h / 2f - t * 1.25f)
    seg(3, x + t, y + h / 2f - t / 2f, w - 2 * t, t)
    seg(4, x, y + h / 2f + t * 0.25f, t, h / 2f - t * 1.25f)
    seg(5, x + w - t, y + h / 2f + t * 0.25f, t, h / 2f - t * 1.25f)
    seg(6, x + t, y + h - t, w - 2 * t, t)
}

/** Two digits centred on [cx] with the top at [y]. */
fun DrawScope.digits2(v: Int, cx: Float, y: Float, w: Float, h: Float, thick: Float, color: Color) {
    val gap = w * 0.35f
    digit(v / 10 % 10, cx - gap / 2f - w, y, w, h, thick, color)
    digit(v % 10, cx + gap / 2f, y, w, h, thick, color)
}
