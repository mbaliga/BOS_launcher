package com.bos.sphere.feature.grid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors

/** Pure colour maths for the picker. Colours are 0xAARRGGBB longs; alpha is always forced to FF. */
internal object ColorMath {
    fun argbToRgb(argb: Long): IntArray =
        intArrayOf(((argb shr 16) and 0xFF).toInt(), ((argb shr 8) and 0xFF).toInt(), (argb and 0xFF).toInt())

    /** Returns [hue 0..360, saturation 0..1, value 0..1]. */
    fun rgbToHsv(r: Int, g: Int, b: Int): FloatArray {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val d = max - min
        val h = when {
            d == 0f -> 0f
            max == rf -> 60f * (((gf - bf) / d) % 6f)
            max == gf -> 60f * ((bf - rf) / d + 2f)
            else -> 60f * ((rf - gf) / d + 4f)
        }
        return floatArrayOf(if (h < 0f) h + 360f else h, if (max == 0f) 0f else d / max, max)
    }

    fun argbToHsv(argb: Long): FloatArray = argbToRgb(argb).let { rgbToHsv(it[0], it[1], it[2]) }

    fun hsvToArgb(h: Float, s: Float, v: Float): Long {
        val hh = ((h % 360f) + 360f) % 360f
        val ss = s.coerceIn(0f, 1f)
        val vv = v.coerceIn(0f, 1f)
        val c = vv * ss
        val x = c * (1f - kotlin.math.abs((hh / 60f) % 2f - 1f))
        val m = vv - c
        val (r, g, b) = when ((hh / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255).toLong()
        return 0xFF000000L or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    /** Accepts `#RRGGBB`, `RRGGBB`, `#RGB`, `RGB`; returns an opaque ARGB long or null. */
    fun parseHex(text: String): Long? {
        var s = text.trim().removePrefix("#")
        if (s.length == 3) s = s.map { "$it$it" }.joinToString("")
        if (s.length != 6 || !s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return 0xFF000000L or s.toLong(16)
    }

    fun formatHex(argb: Long): String = "#%06X".format(argb and 0xFFFFFFL)
}

private val Presets = listOf(
    0xFF8E7BFFL, 0xFF5B8CFFL, 0xFF42E8FFL, 0xFF3DDC97L, 0xFF8CFF6AL,
    0xFFFFD166L, 0xFFFF9F43L, 0xFFFF5C7AL, 0xFFE070FFL, 0xFFC9CDD6L,
)

private fun Long.toColor() = Color(this.toInt())

/** A small filled colour swatch. Pass [onClick] to make it tappable. */
@Composable
fun ColorDot(argb: Long, size: Dp, onClick: (() -> Unit)? = null, selected: Boolean = false) {
    Box(
        Modifier
            .size(size)
            .background(argb.toColor(), CircleShape)
            .border(if (selected) 2.dp else 1.dp, if (selected) HyleColors.InkBright else HyleColors.Hairline, CircleShape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    )
}

/** HSV colour picker: saturation/value square, hue bar, hex field, presets and a live preview. */
@Composable
fun ColorPicker(argb: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    var hsv by remember { mutableStateOf(ColorMath.argbToHsv(argb)) }
    var lastEmitted by remember { mutableLongStateOf(argb or 0xFF000000L) }
    var hexText by remember { mutableStateOf(ColorMath.formatHex(argb)) }
    var hexError by remember { mutableStateOf(false) }

    // Follow external changes (e.g. a preset chosen elsewhere) without fighting our own emits.
    LaunchedEffect(argb) {
        val opaque = argb or 0xFF000000L
        if (opaque != lastEmitted) {
            lastEmitted = opaque
            hsv = ColorMath.argbToHsv(opaque)
            hexText = ColorMath.formatHex(opaque)
            hexError = false
        }
    }

    fun commit(h: Float, s: Float, v: Float) {
        hsv = floatArrayOf(h, s, v)
        val out = ColorMath.hsvToArgb(h, s, v)
        lastEmitted = out
        hexText = ColorMath.formatHex(out)
        hexError = false
        onChange(out)
    }

    val current = ColorMath.hsvToArgb(hsv[0], hsv[1], hsv[2])
    val density = LocalDensity.current

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Saturation / value square.
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .border(1.dp, HyleColors.Hairline, RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        commit(hsv[0], (p.x / w).coerceIn(0f, 1f), 1f - (p.y / h).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { p ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            commit(hsv[0], (p.x / w).coerceIn(0f, 1f), 1f - (p.y / h).coerceIn(0f, 1f))
                        },
                    ) { change, _ ->
                        change.consume()
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val p = change.position
                        commit(hsv[0], (p.x / w).coerceIn(0f, 1f), 1f - (p.y / h).coerceIn(0f, 1f))
                    }
                },
        ) {
            val hueColor = ColorMath.hsvToArgb(hsv[0], 1f, 1f).toColor()
            drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val c = Offset(hsv[1] * size.width, (1f - hsv[2]) * size.height)
            val r = with(density) { 8.dp.toPx() }
            drawCircle(Color.Black.copy(alpha = 0.5f), r + 1f, c, style = Stroke(width = 3f))
            drawCircle(Color.White, r, c, style = Stroke(width = 2.5f))
        }

        // Hue bar.
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .border(1.dp, HyleColors.Hairline, RoundedCornerShape(11.dp))
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        commit(((p.x / size.width.coerceAtLeast(1)) * 360f).coerceIn(0f, 360f), hsv[1], hsv[2])
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { p ->
                            commit(((p.x / size.width.coerceAtLeast(1)) * 360f).coerceIn(0f, 360f), hsv[1], hsv[2])
                        },
                    ) { change, _ ->
                        change.consume()
                        commit(((change.position.x / size.width.coerceAtLeast(1)) * 360f).coerceIn(0f, 360f), hsv[1], hsv[2])
                    }
                },
        ) {
            val stops = (0..6).map { ColorMath.hsvToArgb(it * 60f, 1f, 1f).toColor() }
            drawRect(Brush.horizontalGradient(stops), size = Size(size.width, size.height))
            val x = (hsv[0] / 360f) * size.width
            val r = size.height / 2f
            drawCircle(Color.White, r - 1f, Offset(x.coerceIn(r, size.width - r), size.height / 2f), style = Stroke(width = 3f))
        }

        // Preview + hex.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(width = 56.dp, height = 32.dp)
                    .background(current.toColor(), RoundedCornerShape(8.dp))
                    .border(1.dp, HyleColors.Hairline, RoundedCornerShape(8.dp)),
            )
            BasicTextField(
                value = hexText,
                onValueChange = { raw ->
                    hexText = raw.take(7)
                    val parsed = ColorMath.parseHex(hexText)
                    if (parsed != null) {
                        hexError = false
                        hsv = ColorMath.argbToHsv(parsed)
                        lastEmitted = parsed
                        onChange(parsed)
                    } else {
                        hexError = true
                    }
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = if (hexError) Color(0xFFFF6B7A) else HyleColors.InkBright,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(HyleColors.Violet),
                modifier = Modifier
                    .weight(1f)
                    .background(HyleColors.Surface, RoundedCornerShape(8.dp))
                    .border(1.dp, if (hexError) Color(0xFFFF6B7A) else HyleColors.Hairline, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            Text(
                if (hexError) "#RRGGBB" else "",
                color = HyleColors.InkDim,
                fontSize = 11.sp,
            )
        }

        // Presets.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Presets.forEach { p ->
                ColorDot(p, 24.dp, onClick = {
                    hsv = ColorMath.argbToHsv(p)
                    lastEmitted = p
                    hexText = ColorMath.formatHex(p)
                    hexError = false
                    onChange(p)
                }, selected = p == current)
            }
        }
    }
}
